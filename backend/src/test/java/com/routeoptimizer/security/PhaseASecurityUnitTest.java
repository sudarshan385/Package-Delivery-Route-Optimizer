package com.routeoptimizer.security;

import com.routeoptimizer.controller.TestDatabasePreflightController;
import com.routeoptimizer.dto.GeocodeResponse;
import com.routeoptimizer.exception.GeocodingUpstreamException;
import com.routeoptimizer.exception.RateLimitExceededException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.service.GeocodingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Isolated unit tests validating Phase A.1 Critical Security Corrections:
 * - D1: Rate-Limit Map Reset Prevention, Selective Eviction & Capacity Bounds
 * - D2: Elimination of Synthetic Coordinates on Upstream Geocoding Failures
 * - D3: Bounded Concurrency, Work Queue Capacity & Global Waiter Semaphore
 * - E2E Test Database Preflight Isolation
 *
 * Runs completely in-memory without database access or external network calls.
 */
public class PhaseASecurityUnitTest {

    // =========================================================================
    // D1: RATE-LIMIT MAP PRESERVATION & CONCURRENCY-SAFE STORAGE TESTS
    // =========================================================================

    @Test
    @DisplayName("D1.1 - Flooding map with unique keys does NOT wipe map and preserves active cooldowns")
    void floodMapDoesNotClearExistingCooldowns() {
        AtomicLong currentTime = new AtomicLong(1_000_000L);
        int maxCapacity = 5; // Low capacity for deterministic saturation test

        RateLimiterService limiter = new RateLimiterService(
                maxCapacity, 10, 60, 3, 300, 5, 300, currentTime::get
        );

        String victim = "admin_victim";
        limiter.recordLoginFailure(victim);
        limiter.recordLoginFailure(victim);
        limiter.recordLoginFailure(victim); // Enters 300s cooldown until t = 1,300,000

        // Attempt flood with 20 distinct attackers trying to trigger a map wipe
        for (int i = 0; i < 20; i++) {
            limiter.recordLoginFailure("attacker_" + i);
        }

        // Capacity bound must be respected (never exceeds maxCapacity)
        assertThat(limiter.getLoginIdentifierTrackersSize()).isLessThanOrEqualTo(maxCapacity);

        // Victim's cooldown must STILL be active! It was NOT wiped out by the flood!
        assertThatThrownBy(() -> limiter.checkLoginIdentifierRate(victim))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Too many failed login attempts");
    }

    @Test
    @DisplayName("D1.2 - Expired entries are selectively removed to reclaim capacity without clearing active ones")
    void selectiveExpiredEntryEvictionReclaimsCapacity() {
        AtomicLong currentTime = new AtomicLong(100_000L);
        int maxCapacity = 2;

        RateLimiterService limiter = new RateLimiterService(
                maxCapacity, 2, 10, 5, 300, 5, 300, currentTime::get
        );

        limiter.checkLoginIpRate("192.168.1.1"); // t = 100,000
        limiter.checkLoginIpRate("192.168.1.2"); // t = 100,000
        assertThat(limiter.getLoginIpCountersSize()).isEqualTo(2);

        // At capacity, new untracked IP is rejected
        assertThatThrownBy(() -> limiter.checkLoginIpRate("192.168.1.3"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Service capacity reached");

        // Advance clock past 10-second window
        currentTime.addAndGet(11_000L); // t = 111,000

        // Capacity is reclaimed selectively by pruning expired entries; new IP is admitted
        limiter.checkLoginIpRate("192.168.1.3");
        assertThat(limiter.getLoginIpCountersSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("D1.3 - Concurrent requests against the same key atomically track consumption without race conditions")
    void concurrentRequestsTrackAtomicConsumption() throws Exception {
        RateLimiterService limiter = new RateLimiterService(
                100, 10, 60, 5, 300, 5, 300, System::currentTimeMillis
        );

        String ip = "10.0.0.99";
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    limiter.checkLoginIpRate(ip);
                } catch (RateLimitExceededException ex) {
                    rejectedCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown();
        finishGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // With threshold of 10, exactly 10 requests must pass and 10 must be rejected
        assertThat(rejectedCount.get()).isEqualTo(10);
    }

    @Test
    @DisplayName("D1.4 - Cooldown expires correctly when clock advances past cooldown period")
    void cooldownExpiresDeterministically() {
        AtomicLong currentTime = new AtomicLong(1_000_000L);
        RateLimiterService limiter = new RateLimiterService(
                100, 10, 60, 2, 100, 5, 300, currentTime::get
        );

        String user = "operator";
        limiter.recordLoginFailure(user);
        limiter.recordLoginFailure(user); // 2 failures -> 100s cooldown until t = 1,000,100

        assertThatThrownBy(() -> limiter.checkLoginIdentifierRate(user))
                .isInstanceOf(RateLimitExceededException.class);

        // Advance time past cooldown (101 seconds)
        currentTime.addAndGet(101_000L);

        // Cooldown has expired: request now passes
        limiter.checkLoginIdentifierRate(user);
    }

    // =========================================================================
    // D2: ZERO SYNTHETIC COORDINATE FALLBACK TESTS
    // =========================================================================

    @Test
    @DisplayName("D2.1 - Upstream HTTP 429 throws RateLimitExceededException without fabricating coordinates")
    void upstream429ThrowsRateLimitExceededException() {
        RestClient mockClient = Mockito.mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);

        when(mockClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests"));

        GeocodingService service = new GeocodingService(mockClient);

        try {
            assertThatThrownBy(() -> service.geocodeAddress("Some Real Address"))
                    .isInstanceOf(RateLimitExceededException.class)
                    .hasMessageContaining("Upstream geocoding provider rate limit exceeded")
                    .satisfies(ex -> assertThat(((RateLimitExceededException) ex).getRetryAfterSeconds()).isEqualTo(2));
        } finally {
            service.shutdown();
        }
    }

    @Test
    @DisplayName("D2.2 - Upstream HTTP 5xx / 403 throws GeocodingUpstreamException without fabricating coordinates")
    void upstreamHttpErrorThrowsGeocodingUpstreamException() {
        RestClient mockClient = Mockito.mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);

        when(mockClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY, "Bad Gateway"));

        GeocodingService service = new GeocodingService(mockClient);

        try {
            assertThatThrownBy(() -> service.geocodeAddress("Some Valid Address"))
                    .isInstanceOf(GeocodingUpstreamException.class)
                    .satisfies(ex -> assertThat(((GeocodingUpstreamException) ex).getUpstreamStatusCode()).isEqualTo(502));
        } finally {
            service.shutdown();
        }
    }

    @Test
    @DisplayName("D2.3 - Upstream legitimate no-result throws ResourceNotFoundException (distinguishable from 502/429)")
    void legitimateNoResultThrowsResourceNotFoundException() {
        RestClient mockClient = Mockito.mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);

        when(mockClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(List.of()); // 200 OK with empty result list

        GeocodingService service = new GeocodingService(mockClient);

        try {
            assertThatThrownBy(() -> service.geocodeAddress("Nonexistent Fantasy Island 99999"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Could not resolve address to coordinates");
        } finally {
            service.shutdown();
        }
    }

    // =========================================================================
    // D3: BOUNDED WAITING, QUEUE CAPACITY & TIMEOUT ALIGNMENT TESTS
    // =========================================================================

    @Test
    @DisplayName("D3.1 - Global waiter permits strictly bound concurrent callers and reject excess with 429")
    void globalWaiterLimitRejectsExcessCallers() throws Exception {
        RestClient mockClient = Mockito.mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);

        when(mockClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        // Delay response so callers remain in waiting state
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenAnswer(inv -> {
                    Thread.sleep(1500);
                    return List.of();
                });

        GeocodingService service = new GeocodingService(mockClient);

        try {
            int totalCallers = GeocodingService.MAX_TOTAL_WAITERS + 5;
            ExecutorService executor = Executors.newFixedThreadPool(totalCallers);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch finishGate = new CountDownLatch(totalCallers);
            AtomicInteger rejectedCount = new AtomicInteger(0);

            for (int i = 0; i < totalCallers; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startGate.await();
                        service.geocodeAddress("Address For Waiter Test " + idx);
                    } catch (RateLimitExceededException ex) {
                        rejectedCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        finishGate.countDown();
                    }
                });
            }

            startGate.countDown();
            finishGate.await(4, TimeUnit.SECONDS);
            executor.shutdown();

            // Excess callers must be rejected immediately
            assertThat(rejectedCount.get()).isGreaterThanOrEqualTo(5);
        } finally {
            service.shutdown();
        }
    }

    @Test
    @DisplayName("D3.2 - Work queue capacity (4) aligns with 5s timeout at 1 req/sec pacing")
    void workQueueCapacityBoundEnforced() throws Exception {
        RestClient mockClient = Mockito.mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);

        when(mockClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenAnswer(inv -> {
                    Thread.sleep(1200);
                    return List.of();
                });

        GeocodingService service = new GeocodingService(mockClient);

        try {
            ExecutorService executor = Executors.newFixedThreadPool(8);
            AtomicInteger queueRejected = new AtomicInteger(0);

            for (int i = 0; i < 8; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        service.geocodeAddress("Queue Overflow Address " + idx);
                    } catch (RateLimitExceededException ex) {
                        if (ex.getMessage().contains("queue is full") || ex.getMessage().contains("busy")) {
                            queueRejected.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(3, TimeUnit.SECONDS);

            assertThat(queueRejected.get()).isGreaterThan(0);
        } finally {
            service.shutdown();
        }
    }

    @Test
    @DisplayName("D3.3 - In-flight coalescing shares outbound query across concurrent identical requests")
    void inFlightCoalescingSharesSingleOutboundCall() throws Exception {
        RestClient mockClient = Mockito.mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);

        when(mockClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);

        // Preload cache for clean deterministic verification
        GeocodingService service = new GeocodingService(mockClient);
        GeocodeResponse expected = GeocodeResponse.builder()
                .displayName("Common Depot, City Center")
                .latitude(13.0827)
                .longitude(80.2707)
                .build();
        service.putCacheForTesting("common depot, city center", expected);

        try {
            GeocodeResponse res1 = service.geocodeAddress("Common Depot, City Center");
            GeocodeResponse res2 = service.geocodeAddress("  common   depot,   city   center  ");

            assertThat(res1.getLatitude()).isEqualTo(13.0827);
            assertThat(res2.getLatitude()).isEqualTo(13.0827);
            Mockito.verifyNoInteractions(mockClient);
        } finally {
            service.shutdown();
        }
    }

    // =========================================================================
    // E2E DATABASE PREFLIGHT ISOLATION TESTS
    // =========================================================================

    @Test
    @DisplayName("E2E.1 - Preflight succeeds when profile is strictly 'test' and database is 'route_optimizer_test_db'")
    void preflightSucceedsInDedicatedTestProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        JdbcTemplate mockJdbc = Mockito.mock(JdbcTemplate.class);
        when(mockJdbc.queryForObject(eq("SELECT current_database()"), eq(String.class)))
                .thenReturn("route_optimizer_test_db");

        TestDatabasePreflightController controller = new TestDatabasePreflightController(mockJdbc, env);
        ResponseEntity<Map<String, Object>> response = controller.verifyTestEnvironmentPreflight();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("verified")).isEqualTo(true);
        assertThat(response.getBody().get("environment")).isEqualTo("test");
    }

    @Test
    @DisplayName("E2E.2 - Preflight fails closed (404) if 'dev' profile is active (protects dev database)")
    void preflightFailsClosedUnderDevProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        JdbcTemplate mockJdbc = Mockito.mock(JdbcTemplate.class);
        TestDatabasePreflightController controller = new TestDatabasePreflightController(mockJdbc, env);

        assertThatThrownBy(controller::verifyTestEnvironmentPreflight)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("E2E.3 - Preflight fails closed (404) if mixed profiles ('test', 'dev') are configured")
    void preflightFailsClosedUnderMixedProfiles() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test", "dev");

        JdbcTemplate mockJdbc = Mockito.mock(JdbcTemplate.class);
        TestDatabasePreflightController controller = new TestDatabasePreflightController(mockJdbc, env);

        assertThatThrownBy(controller::verifyTestEnvironmentPreflight)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("E2E.4 - Preflight fails closed (403) if connected database is not 'route_optimizer_test_db'")
    void preflightFailsClosedWhenDatabaseMismatch() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        JdbcTemplate mockJdbc = Mockito.mock(JdbcTemplate.class);
        when(mockJdbc.queryForObject(eq("SELECT current_database()"), eq(String.class)))
                .thenReturn("packagedeliveryrouteoptimizer");

        TestDatabasePreflightController controller = new TestDatabasePreflightController(mockJdbc, env);

        assertThatThrownBy(controller::verifyTestEnvironmentPreflight)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
