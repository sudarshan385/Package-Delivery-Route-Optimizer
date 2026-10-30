package com.routeoptimizer.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeoptimizer.dto.GeocodeResponse;
import com.routeoptimizer.exception.GeocodingUpstreamException;
import com.routeoptimizer.exception.RateLimitExceededException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import jakarta.annotation.PreDestroy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enterprise Geocoding Service with bounded concurrency, background rate pacing,
 * duplicate-request coalescing, and zero synthetic fallbacks.
 *
 * Hardened against:
 * - D2 (Synthetic Coordinates): Never fabricates or returns fixed/mock coordinates on upstream failures.
 *   Throws typed GeocodingUpstreamException / RateLimitExceededException so callers fail safely without corrupting data.
 * - D3 (Request Blocking & Timeout Mismatch):
 *   1. Work queue capacity bounded to 4 (aligned with 5s timeout at 1 req/sec pacing).
 *   2. Global waiter permits bounded to 10 via Semaphore across all waiting callers (including coalesced callers).
 *   3. Cancels and skips unneeded queued work if caller deadline expires.
 *   4. Supports non-blocking geocodeAddressAsync for asynchronous controller execution.
 */
@Service
public class GeocodingService {

    public static final int MAX_CACHE_ENTRIES = 500;
    public static final long CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L; // 24 hours
    public static final int MAX_PENDING_QUEUE = 4; // 4 slots * 1s pacing fits within 5s deadline
    public static final long MIN_OUTBOUND_INTERVAL_MILLIS = 1000L; // 1 req/sec Nominatim pacing
    public static final long REQUEST_TIMEOUT_SECONDS = 5L;
    public static final int MAX_TOTAL_WAITERS = 10; // Strict global cap on concurrent waiting callers

    private final RestClient restClient;

    // Bounded in-memory cache for normalized addresses (Single authoritative cache, 500 entries)
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // In-flight deduplication / coalescing
    private final ConcurrentHashMap<String, CompletableFuture<GeocodeResponse>> inFlight = new ConcurrentHashMap<>();

    // Bounded pending queue for outbound calls
    private final BlockingQueue<GeocodeTask> workQueue = new ArrayBlockingQueue<>(MAX_PENDING_QUEUE);

    // Global waiter limit protecting Tomcat worker threads from exhaustion
    private final Semaphore waiterPermits = new Semaphore(MAX_TOTAL_WAITERS);

    // Dedicated background executor for paced outbound dispatching (servlet threads never sleep)
    private final ExecutorService dispatcherExecutor;
    private final AtomicLong lastOutboundRequestTime = new AtomicLong(0);

    public GeocodingService() {
        this(createDefaultRestClient());
    }

    public GeocodingService(RestClient restClient) {
        this.restClient = restClient;
        this.dispatcherExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "nominatim-outbound-dispatcher");
            t.setDaemon(true);
            return t;
        });
        startDispatcher();
    }

    private static RestClient createDefaultRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(4000);
        requestFactory.setReadTimeout(4000);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "RouteOptimizerApp/2.0 (routeopt@localhost)")
                .defaultHeader("Accept-Language", "en-US,en;q=0.9")
                .build();
    }

    public static String normalizeAddress(String address) {
        if (address == null) return "";
        return address.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * Non-blocking asynchronous geocoding lookup supporting Spring MVC async controllers.
     */
    public CompletableFuture<GeocodeResponse> geocodeAddressAsync(String address) {
        if (address == null || address.trim().isEmpty()) {
            CompletableFuture<GeocodeResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Address cannot be empty"));
            return failed;
        }

        String normalized = normalizeAddress(address);

        // 1. Check bounded in-memory cache
        CacheEntry cached = cache.get(normalized);
        if (cached != null && !cached.isExpired(System.currentTimeMillis())) {
            return CompletableFuture.completedFuture(cached.response);
        }

        // 2. Bound total waiting callers globally across all callers
        if (!waiterPermits.tryAcquire()) {
            CompletableFuture<GeocodeResponse> rejected = new CompletableFuture<>();
            rejected.completeExceptionally(new RateLimitExceededException(
                    "Geocoding service busy. Too many concurrent requests.", 2));
            return rejected;
        }

        // 3. In-flight request coalescing
        CompletableFuture<GeocodeResponse> promise = new CompletableFuture<>();
        CompletableFuture<GeocodeResponse> activePromise = inFlight.putIfAbsent(normalized, promise);

        if (activePromise != null) {
            // Another thread is already dispatching this address; share existing future and release waiter permit when done
            activePromise.whenComplete((res, ex) -> waiterPermits.release());
            return activePromise;
        }

        // 4. Submit to bounded work queue
        GeocodeTask task = new GeocodeTask(address, normalized, promise);
        boolean enqueued = workQueue.offer(task);
        if (!enqueued) {
            inFlight.remove(normalized);
            waiterPermits.release();
            promise.completeExceptionally(new RateLimitExceededException(
                    "Geocoding service queue is full. Please retry in 2 seconds.", 2));
            return promise;
        }

        // Ensure waiter permit and in-flight tracker are cleanly released upon completion or cancellation
        promise.whenComplete((res, ex) -> {
            inFlight.remove(normalized);
            waiterPermits.release();
        });

        return promise;
    }

    /**
     * Synchronous geocoding lookup with strict deadline and bounded waiter semaphore.
     * Used by transactional workflows such as package creation.
     */
    public GeocodeResponse geocodeAddress(String address) {
        CompletableFuture<GeocodeResponse> future = geocodeAddressAsync(address);
        try {
            return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new GeocodingUpstreamException("Geocoding failed: " + cause.getMessage(), 500);
        } catch (TimeoutException e) {
            future.cancel(true); // Signal cancellation so background dispatcher skips unneeded work
            throw new RateLimitExceededException("Geocoding request timed out. Please retry.", 2);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RateLimitExceededException("Geocoding request was interrupted.", 2);
        }
    }

    private void startDispatcher() {
        dispatcherExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    GeocodeTask task = workQueue.take();

                    // Skip cancelled or already-completed requests to avoid wasting outbound capacity
                    if (task.future.isDone() || task.future.isCancelled()) {
                        continue;
                    }

                    // Enforce >= 1000ms outbound rate pacing to Nominatim
                    long now = System.currentTimeMillis();
                    long timeSinceLast = now - lastOutboundRequestTime.get();
                    if (timeSinceLast < MIN_OUTBOUND_INTERVAL_MILLIS) {
                        long sleepTime = MIN_OUTBOUND_INTERVAL_MILLIS - timeSinceLast;
                        Thread.sleep(sleepTime);
                    }
                    lastOutboundRequestTime.set(System.currentTimeMillis());

                    // Double-check cancellation after pacing delay
                    if (task.future.isDone() || task.future.isCancelled()) {
                        continue;
                    }

                    // Execute outbound HTTP query
                    try {
                        GeocodeResponse response = executeNominatimQuery(task.rawAddress);
                        putCache(task.normalizedAddress, response);
                        task.future.complete(response);
                    } catch (Throwable t) {
                        task.future.completeExceptionally(t);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private GeocodeResponse executeNominatimQuery(String address) {
        List<NominatimResponse> response = null;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", address)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimResponse>>() {});
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // Strictly fail without returning synthetic or fabricated coordinates
            if (e.getStatusCode().value() == 429) {
                throw new RateLimitExceededException("Upstream geocoding provider rate limit exceeded. Please retry in 2 seconds.", 2);
            }
            throw new GeocodingUpstreamException("Geocoding provider returned error: " + e.getStatusCode().value(), e.getStatusCode().value());
        } catch (Exception e) {
            throw new GeocodingUpstreamException("Failed to reach geocoding provider: " + e.getMessage(), 503);
        }

        if (response == null || response.isEmpty()) {
            throw new ResourceNotFoundException("Could not resolve address to coordinates: " + address);
        }

        NominatimResponse result = response.get(0);
        return GeocodeResponse.builder()
                .displayName(result.getDisplayName())
                .latitude(Double.parseDouble(result.getLat()))
                .longitude(Double.parseDouble(result.getLon()))
                .build();
    }

    private void putCache(String normalized, GeocodeResponse response) {
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
        }
        cache.put(normalized, new CacheEntry(response, System.currentTimeMillis() + CACHE_TTL_MILLIS));
    }

    public void putCacheForTesting(String normalized, GeocodeResponse response) {
        cache.put(normalized, new CacheEntry(response, System.currentTimeMillis() + CACHE_TTL_MILLIS));
    }

    public void clearCache() {
        cache.clear();
        inFlight.clear();
        workQueue.clear();
    }

    public int getCacheSize() {
        return cache.size();
    }

    public int getQueueSize() {
        return workQueue.size();
    }

    public int getAvailableWaiterPermits() {
        return waiterPermits.availablePermits();
    }

    @PreDestroy
    public void shutdown() {
        dispatcherExecutor.shutdownNow();
    }

    private static class GeocodeTask {
        final String rawAddress;
        final String normalizedAddress;
        final CompletableFuture<GeocodeResponse> future;

        GeocodeTask(String rawAddress, String normalizedAddress, CompletableFuture<GeocodeResponse> future) {
            this.rawAddress = rawAddress;
            this.normalizedAddress = normalizedAddress;
            this.future = future;
        }
    }

    private static class CacheEntry {
        final GeocodeResponse response;
        final long expiresAtMillis;

        CacheEntry(GeocodeResponse response, long expiresAtMillis) {
            this.response = response;
            this.expiresAtMillis = expiresAtMillis;
        }

        boolean isExpired(long now) {
            return now >= expiresAtMillis;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NominatimResponse {
        @JsonProperty("display_name")
        private String displayName;
        private String lat;
        private String lon;

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getLat() { return lat; }
        public void setLat(String lat) { this.lat = lat; }
        public String getLon() { return lon; }
        public void setLon(String lon) { this.lon = lon; }
    }
}
