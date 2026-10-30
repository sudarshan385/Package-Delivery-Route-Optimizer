package com.routeoptimizer.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.RouteAlternativeOption;
import com.routeoptimizer.dto.RoutingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoutingService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;

    public RoutingService(@Value("${routing.osrm.base-url:http://router.project-osrm.org}") String configuredBaseUrl) {
        this.baseUrl = configuredBaseUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(this.baseUrl)
                .defaultHeader("User-Agent", "RouteOptimizerApp/2.0")
                .build();
    }

    private final java.util.concurrent.ConcurrentHashMap<String, RoutingResponse> routeCache = new java.util.concurrent.ConcurrentHashMap<>();

    public RoutingResponse calculateRoute(Double lat1, Double lon1, Double lat2, Double lon2) {
        return calculateRouteWithAlternatives(lat1, lon1, lat2, lon2, "CAR");
    }

    public RoutingResponse calculateRouteWithAlternatives(Double lat1, Double lon1, Double lat2, Double lon2, String transportMode) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }

        String mode = (transportMode != null && !transportMode.trim().isEmpty()) 
                ? transportMode.trim().toUpperCase() 
                : "CAR";

        String cacheKey = String.format(java.util.Locale.US, "%.4f,%.4f;%.4f,%.4f;%s", lat1, lon1, lat2, lon2, mode);
        RoutingResponse cached = routeCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String coords = String.format(java.util.Locale.US, "%f,%f;%f,%f", lon1, lat1, lon2, lat2);

        OsrmResponse response = null;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/route/v1/driving/{coords}")
                            .queryParam("overview", "full")
                            .queryParam("geometries", "geojson")
                            .queryParam("alternatives", "true")
                            .build(coords))
                    .retrieve()
                    .body(OsrmResponse.class);
        } catch (Exception e) {
            // Safe resilient road estimation fallback
            double haversine = calculateHaversineKm(lat1, lon1, lat2, lon2);
            double estimatedRoadKm = Math.round(haversine * 1.3 * 10.0) / 10.0;
            double estimatedDurationMin = Math.round(estimatedRoadKm * 2.5 * 10.0) / 10.0;
            String lineGeoJson = String.format(java.util.Locale.US,
                    "{\"type\":\"LineString\",\"coordinates\":[[%f,%f],[%f,%f]]}",
                    lon1, lat1, lon2, lat2);

            RoutingResponse fallback = RoutingResponse.builder()
                    .distanceKm(estimatedRoadKm)
                    .durationMin(estimatedDurationMin)
                    .geometryGeoJson(lineGeoJson)
                    .transportMode(mode)
                    .provider("Road Network Approximation (Deterministic Circuity 1.3x)")
                    .alternatives(List.of(RouteAlternativeOption.builder()
                            .alternativeIndex(0)
                            .label("Primary Road Connection")
                            .distanceKm(estimatedRoadKm)
                            .durationMin(estimatedDurationMin)
                            .geometryGeoJson(lineGeoJson)
                            .transportMode(mode)
                            .recommended(true)
                            .summary(String.format("%.1f km • %.0f min", estimatedRoadKm, estimatedDurationMin))
                            .build()))
                    .build();
            routeCache.put(cacheKey, fallback);
            return fallback;
        }

        if (response == null || !"Ok".equalsIgnoreCase(response.getCode()) || response.getRoutes() == null || response.getRoutes().isEmpty()) {
            double haversine = calculateHaversineKm(lat1, lon1, lat2, lon2);
            double estimatedRoadKm = Math.round(haversine * 1.3 * 10.0) / 10.0;
            double estimatedDurationMin = Math.round(estimatedRoadKm * 2.5 * 10.0) / 10.0;
            String lineGeoJson = String.format(java.util.Locale.US,
                    "{\"type\":\"LineString\",\"coordinates\":[[%f,%f],[%f,%f]]}",
                    lon1, lat1, lon2, lat2);

            RoutingResponse fallback = RoutingResponse.builder()
                    .distanceKm(estimatedRoadKm)
                    .durationMin(estimatedDurationMin)
                    .geometryGeoJson(lineGeoJson)
                    .transportMode(mode)
                    .provider("Road Network Approximation (Deterministic Circuity 1.3x)")
                    .alternatives(List.of(RouteAlternativeOption.builder()
                            .alternativeIndex(0)
                            .label("Primary Road Connection")
                            .distanceKm(estimatedRoadKm)
                            .durationMin(estimatedDurationMin)
                            .geometryGeoJson(lineGeoJson)
                            .transportMode(mode)
                            .recommended(true)
                            .summary(String.format("%.1f km • %.0f min", estimatedRoadKm, estimatedDurationMin))
                            .build()))
                    .build();
            routeCache.put(cacheKey, fallback);
            return fallback;
        }

        List<RouteAlternativeOption> alternativesList = new ArrayList<>();
        int index = 0;

        for (OsrmRoute r : response.getRoutes()) {
            if (r.getDistance() == null || r.getDistance() <= 0) continue;

            double distKm = r.getDistance() / 1000.0;
            double baseDurationMin = (r.getDuration() != null ? r.getDuration() : 0.0) / 60.0;
            double adjustedDurationMin = baseDurationMin;

            String summaryLabel;
            if ("TWO_WHEELER".equals(mode)) {
                adjustedDurationMin = Math.max(1.0, baseDurationMin * 0.85);
                summaryLabel = (index == 0) ? "Recommended Motorized Two-Wheeler Route" : "Alternative Two-Wheeler Path " + index;
            } else if ("WALKING".equals(mode)) {
                adjustedDurationMin = distKm * 12.0;
                summaryLabel = (index == 0) ? "Recommended Pedestrian Pathway" : "Alternative Walking Route " + index;
            } else {
                summaryLabel = (index == 0) ? "Recommended Primary Route (Fastest)" : "Alternative Arterial Route " + index;
            }

            String routeGeoJson = null;
            if (r.getGeometry() != null) {
                try {
                    routeGeoJson = objectMapper.writeValueAsString(r.getGeometry());
                } catch (Exception ignored) {
                }
            }

            alternativesList.add(RouteAlternativeOption.builder()
                    .alternativeIndex(index)
                    .label(summaryLabel)
                    .distanceKm(distKm)
                    .durationMin(adjustedDurationMin)
                    .geometryGeoJson(routeGeoJson)
                    .transportMode(mode)
                    .recommended(index == 0)
                    .summary(String.format("%.1f km • %.0f min", distKm, adjustedDurationMin))
                    .build());

            index++;
        }

        if (alternativesList.isEmpty()) {
            double haversine = calculateHaversineKm(lat1, lon1, lat2, lon2);
            double estimatedRoadKm = Math.round(haversine * 1.3 * 10.0) / 10.0;
            double estimatedDurationMin = Math.round(estimatedRoadKm * 2.5 * 10.0) / 10.0;
            String lineGeoJson = String.format(java.util.Locale.US,
                    "{\"type\":\"LineString\",\"coordinates\":[[%f,%f],[%f,%f]]}",
                    lon1, lat1, lon2, lat2);

            RoutingResponse fallback = RoutingResponse.builder()
                    .distanceKm(estimatedRoadKm)
                    .durationMin(estimatedDurationMin)
                    .geometryGeoJson(lineGeoJson)
                    .transportMode(mode)
                    .provider("Road Network Approximation (Deterministic Circuity 1.3x)")
                    .alternatives(List.of(RouteAlternativeOption.builder()
                            .alternativeIndex(0)
                            .label("Primary Road Connection")
                            .distanceKm(estimatedRoadKm)
                            .durationMin(estimatedDurationMin)
                            .geometryGeoJson(lineGeoJson)
                            .transportMode(mode)
                            .recommended(true)
                            .summary(String.format("%.1f km • %.0f min", estimatedRoadKm, estimatedDurationMin))
                            .build()))
                    .build();
            routeCache.put(cacheKey, fallback);
            return fallback;
        }

        RouteAlternativeOption primary = alternativesList.get(0);

        RoutingResponse res = RoutingResponse.builder()
                .distanceKm(primary.getDistanceKm())
                .durationMin(primary.getDurationMin())
                .geometryGeoJson(primary.getGeometryGeoJson())
                .transportMode(mode)
                .provider("OSRM (OpenStreetMap Road Network)")
                .alternatives(alternativesList)
                .build();
        routeCache.put(cacheKey, res);
        return res;
    }

    public static double calculateHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OsrmResponse {
        private String code;
        private List<OsrmRoute> routes;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public List<OsrmRoute> getRoutes() { return routes; }
        public void setRoutes(List<OsrmRoute> routes) { this.routes = routes; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OsrmRoute {
        private Double distance;
        private Double duration;
        private JsonNode geometry;

        public Double getDistance() { return distance; }
        public void setDistance(Double distance) { this.distance = distance; }
        public Double getDuration() { return duration; }
        public void setDuration(Double duration) { this.duration = duration; }
        public JsonNode getGeometry() { return geometry; }
        public void setGeometry(JsonNode geometry) { this.geometry = geometry; }
    }
}
