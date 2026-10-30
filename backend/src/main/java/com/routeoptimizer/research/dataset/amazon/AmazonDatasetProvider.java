package com.routeoptimizer.research.dataset.amazon;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class AmazonDatasetProvider {

    private final ObjectMapper mapper;
    private final JsonFactory factory;
    public AmazonDatasetProvider() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature());
        this.factory = mapper.getFactory();
    }

    public List<AmazonRouteRecord> loadDataset(Path routeData, Path packageData, Path actualSequences) throws IOException {
        return loadDataset(routeData, packageData, actualSequences, null);
    }
    
    public List<AmazonRouteRecord> loadDataset(Path routeData, Path packageData, Path actualSequences, Integer routeLimit) throws IOException {
        Map<String, AmazonRouteRecord.AmazonRouteRecordBuilder> routeBuilders = new HashMap<>();
        Map<String, Map<String, AmazonStopRecord.AmazonStopRecordBuilder>> stopBuilders = new HashMap<>();
        Map<String, Map<String, List<AmazonPackageRecord>>> packageMap = new HashMap<>();

        // 1. Parse route_data.json
        try (java.io.InputStream is = Files.newInputStream(routeData);
             JsonParser parser = factory.createParser(is)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected data to start with an Object");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String routeId = parser.currentName();
                parser.nextToken(); // START_OBJECT for the route object

                AmazonRouteRecord.AmazonRouteRecordBuilder builder = AmazonRouteRecord.builder().routeId(routeId);
                Map<String, AmazonStopRecord.AmazonStopRecordBuilder> currentStops = new HashMap<>();
                Map<String, List<AmazonPackageRecord>> currentPackages = new HashMap<>();

                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.currentName();
                    parser.nextToken();
                    switch (fieldName) {
                        case "station_code":
                            builder.stationCode(parser.getText());
                            break;
                        case "date_YYYY_MM_DD":
                            builder.routeDate(LocalDate.parse(parser.getText(), DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                            break;
                        case "departure_time_utc":
                            builder.departureTimeUtc(LocalTime.parse(parser.getText()));
                            break;
                        case "executor_capacity_cm3":
                            builder.executorCapacityCm3(parser.getValueAsDouble());
                            break;
                        case "route_score":
                            builder.routeScore(parser.getText());
                            break;
                        case "stops":
                            if (parser.currentToken() == JsonToken.START_OBJECT) {
                                while (parser.nextToken() != JsonToken.END_OBJECT) {
                                    String stopId = parser.currentName();
                                    parser.nextToken(); // START_OBJECT for stop

                                    AmazonStopRecord.AmazonStopRecordBuilder stopBuilder = AmazonStopRecord.builder().stopId(stopId);
                                    currentPackages.put(stopId, new ArrayList<>());
                                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                                        String stopField = parser.currentName();
                                        parser.nextToken();
                                        switch (stopField) {
                                            case "lat":
                                                stopBuilder.latitude(parser.getValueAsDouble());
                                                break;
                                            case "lng":
                                                stopBuilder.longitude(parser.getValueAsDouble());
                                                break;
                                            case "type":
                                                stopBuilder.type(parser.getText());
                                                break;
                                            case "zone_id":
                                                stopBuilder.zoneId(parser.getText());
                                                break;
                                            default:
                                                skipCurrentChildren(parser);
                                        }
                                    }
                                    currentStops.put(stopId, stopBuilder);
                                }
                            } else {
                                skipCurrentChildren(parser);
                            }
                            break;
                        default:
                            skipCurrentChildren(parser);
                    }
                }
                routeBuilders.put(routeId, builder);
                stopBuilders.put(routeId, currentStops);
                packageMap.put(routeId, currentPackages);
            }
        }

        // 2. Parse package_data.json
        try (java.io.InputStream is = Files.newInputStream(packageData);
             JsonParser parser = factory.createParser(is)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected package data to start with an Object");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String routeId = parser.currentName();
                parser.nextToken(); // START_OBJECT for route packages

                Map<String, List<AmazonPackageRecord>> routePackages = packageMap.get(routeId);
                if (routePackages == null) {
                    // Route in package_data but not in route_data. Record mismatched.
                    skipCurrentChildren(parser);
                    continue;
                }

                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String stopId = parser.currentName();
                    parser.nextToken(); // START_OBJECT for stop packages

                    List<AmazonPackageRecord> stopPackages = routePackages.get(stopId);
                    if (stopPackages == null) {
                        // Stop in package_data but not in route_data
                        skipCurrentChildren(parser);
                        continue;
                    }

                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String packageId = parser.currentName();
                        parser.nextToken(); // START_OBJECT for package

                        AmazonPackageRecord.AmazonPackageRecordBuilder packageBuilder = AmazonPackageRecord.builder().packageId(packageId);

                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            String packageField = parser.currentName();
                            parser.nextToken();
                            switch (packageField) {
                                case "scan_status":
                                    packageBuilder.scanStatus(parser.getText());
                                    break;
                                case "time_window":
                                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                                        String twField = parser.currentName();
                                        parser.nextToken();
                                        if (parser.currentToken() == JsonToken.VALUE_NULL) continue;
                                        String textVal = parser.getText();
                                        if ("NaN".equals(textVal)) continue;
                                        
                                        try {
                                            if ("start_time_utc".equals(twField)) {
                                                packageBuilder.timeWindowStartUtc(OffsetDateTime.parse(textVal));
                                            } else if ("end_time_utc".equals(twField)) {
                                                packageBuilder.timeWindowEndUtc(OffsetDateTime.parse(textVal));
                                            }
                                        } catch (DateTimeParseException ignored) {
                                        }
                                    }
                                    break;
                                case "planned_service_time_seconds":
                                    if (parser.currentToken() != JsonToken.VALUE_NULL && !"NaN".equals(parser.getText())) {
                                        packageBuilder.plannedServiceTimeSeconds(parser.getValueAsDouble());
                                    }
                                    break;
                                case "dimensions":
                                    Map<String, Double> dimensions = new HashMap<>();
                                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                                        String dimKey = parser.currentName();
                                        parser.nextToken();
                                        if (parser.currentToken() != JsonToken.VALUE_NULL && !"NaN".equals(parser.getText())) {
                                            dimensions.put(dimKey, parser.getValueAsDouble());
                                        }
                                    }
                                    packageBuilder.dimensions(dimensions);
                                    break;
                                default:
                                    skipCurrentChildren(parser);
                            }
                        }
                        stopPackages.add(packageBuilder.build());
                    }
                }
            }
        }

        // 3. Parse actual_sequences.json
        Map<String, Map<String, Integer>> sequences = new HashMap<>();
        try (java.io.InputStream is = Files.newInputStream(actualSequences);
             JsonParser parser = factory.createParser(is)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected sequence data to start with an Object");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String routeId = parser.currentName();
                parser.nextToken(); // START_OBJECT for route sequence
                
                Map<String, Integer> routeSeq = new HashMap<>();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String seqField = parser.currentName();
                    parser.nextToken();
                    if ("actual".equals(seqField)) {
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            String stopId = parser.currentName();
                            parser.nextToken();
                            routeSeq.put(stopId, parser.getValueAsInt());
                        }
                    } else {
                        skipCurrentChildren(parser);
                    }
                }
                sequences.put(routeId, routeSeq);
            }
        }

        // 4. Assemble Routes
        List<AmazonRouteRecord> finalRoutes = new ArrayList<>();
        for (Map.Entry<String, AmazonRouteRecord.AmazonRouteRecordBuilder> routeEntry : routeBuilders.entrySet()) {
            String routeId = routeEntry.getKey();
            AmazonRouteRecord.AmazonRouteRecordBuilder routeBuilder = routeEntry.getValue();
            
            Map<String, AmazonStopRecord.AmazonStopRecordBuilder> routeStopsMap = stopBuilders.get(routeId);
            Map<String, List<AmazonPackageRecord>> routePackageMap = packageMap.get(routeId);
            Map<String, Integer> routeSequenceMap = sequences.get(routeId);

            if (routeSequenceMap == null) {
                // Route missing in actual_sequences
                continue;
            }

            List<AmazonStopRecord> assembledStops = new ArrayList<>();
            
            for (Map.Entry<String, AmazonStopRecord.AmazonStopRecordBuilder> stopEntry : routeStopsMap.entrySet()) {
                String stopId = stopEntry.getKey();
                AmazonStopRecord.AmazonStopRecordBuilder stopBuilder = stopEntry.getValue();
                
                List<AmazonPackageRecord> pkgs = routePackageMap.getOrDefault(stopId, Collections.emptyList());
                stopBuilder.packages(pkgs);
                
                Integer actualSeq = routeSequenceMap.get(stopId);
                if (actualSeq != null) {
                    stopBuilder.actualSequence(actualSeq);
                }
                
                assembledStops.add(stopBuilder.build());
            }

            // Sort stops by actualSequence
            assembledStops.sort((s1, s2) -> {
                if (s1.getActualSequence() == null && s2.getActualSequence() == null) return 0;
                if (s1.getActualSequence() == null) return 1;
                if (s2.getActualSequence() == null) return -1;
                return s1.getActualSequence().compareTo(s2.getActualSequence());
            });

            routeBuilder.stops(assembledStops);
            finalRoutes.add(routeBuilder.build());
        }

        return finalRoutes;
    }

    private void skipCurrentChildren(JsonParser parser) throws IOException {
        JsonToken current = parser.currentToken();
        if (current != JsonToken.START_OBJECT && current != JsonToken.START_ARRAY) {
            return;
        }
        int open = 1;
        while (open > 0) {
            JsonToken t = parser.nextToken();
            if (t == null) {
                break;
            }
            if (t == JsonToken.START_OBJECT || t == JsonToken.START_ARRAY) {
                open++;
            } else if (t == JsonToken.END_OBJECT || t == JsonToken.END_ARRAY) {
                open--;
            }
        }
    }
}
