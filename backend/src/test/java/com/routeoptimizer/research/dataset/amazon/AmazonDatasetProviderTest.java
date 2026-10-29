package com.routeoptimizer.research.dataset.amazon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AmazonDatasetProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void testRouteStopAndPackageParsingWithExactStrings() throws Exception {
        Path routeFile = tempDir.resolve("route_data.json");
        Path packageFile = tempDir.resolve("package_data.json");
        Path seqFile = tempDir.resolve("actual_sequences.json");

        String routeId = "RouteID_00143bdd-0a6b-49ec-bb35-36593d303e77";
        String stopId1 = "AD";
        String stopId2 = "VE";
        String packageId = "PackageID_9d7fdd03-f2cf-4c6f-9128-028258fc09ea";

        Files.writeString(routeFile, String.format("""
            {
              "%s": {
                "station_code": "ST01",
                "date_YYYY_MM_DD": "2021-08-01",
                "departure_time_utc": "12:00:00",
                "executor_capacity_cm3": 100.5,
                "route_score": "High",
                "stops": {
                  "%s": {
                    "lat": 1.1,
                    "lng": 2.2,
                    "type": "Dropoff",
                    "zone_id": "Z1"
                  },
                  "%s": {
                    "lat": 3.3,
                    "lng": 4.4,
                    "type": "Dropoff",
                    "zone_id": "Z2"
                  }
                }
              }
            }
            """, routeId, stopId1, stopId2));

        Files.writeString(packageFile, String.format("""
            {
              "%s": {
                "%s": {
                  "%s": {
                    "scan_status": "DELIVERED",
                    "time_window": {
                      "start_time_utc": "2021-08-01T14:00:00Z",
                      "end_time_utc": "NaN"
                    },
                    "planned_service_time_seconds": 120.0,
                    "dimensions": {
                      "depth_cm": 10.0,
                      "height_cm": 20.0,
                      "weight_cm": "NaN"
                    }
                  }
                },
                "%s": {
                }
              }
            }
            """, routeId, stopId1, packageId, stopId2));

        Files.writeString(seqFile, String.format("""
            {
              "%s": {
                "actual": {
                  "%s": 1,
                  "%s": 0
                }
              }
            }
            """, routeId, stopId1, stopId2));

        AmazonDatasetProvider provider = new AmazonDatasetProvider();
        List<AmazonRouteRecord> routes = provider.loadDataset(routeFile, packageFile, seqFile);

        assertEquals(1, routes.size());
        AmazonRouteRecord route = routes.get(0);
        
        // 1 & 8. ROUTE PARSING & STRING IDENTIFIERS
        assertEquals(routeId, route.getRouteId());
        assertEquals("ST01", route.getStationCode());
        assertEquals("2021-08-01", route.getRouteDate().toString());
        assertEquals("12:00", route.getDepartureTimeUtc().toString());
        assertEquals(100.5, route.getExecutorCapacityCm3());
        assertEquals("High", route.getRouteScore());

        // 5. ACTUAL SEQUENCE
        List<AmazonStopRecord> stops = route.getStops();
        assertEquals(2, stops.size());
        
        // Order must be stopId2 (0) then stopId1 (1)
        AmazonStopRecord firstStop = stops.get(0);
        AmazonStopRecord secondStop = stops.get(1);
        
        assertEquals(stopId2, firstStop.getStopId());
        assertEquals(0, firstStop.getActualSequence());
        assertEquals(stopId1, secondStop.getStopId());
        assertEquals(1, secondStop.getActualSequence());

        // 2. STOP PARSING
        assertEquals(3.3, firstStop.getLatitude());
        assertEquals(4.4, firstStop.getLongitude());
        assertEquals("Dropoff", firstStop.getType());
        assertEquals("Z2", firstStop.getZoneId());

        // 3. PACKAGE PARSING & CROSS FILE
        List<AmazonPackageRecord> pkgs = secondStop.getPackages();
        assertEquals(1, pkgs.size());
        AmazonPackageRecord pkg = pkgs.get(0);
        assertEquals(packageId, pkg.getPackageId());
        assertEquals("DELIVERED", pkg.getScanStatus());
        assertEquals(120.0, pkg.getPlannedServiceTimeSeconds());
        assertEquals(10.0, pkg.getDimensions().get("depth_cm"));
        assertEquals(20.0, pkg.getDimensions().get("height_cm"));
        assertNull(pkg.getDimensions().get("weight_cm"));

        // 4. TIME WINDOWS
        assertNotNull(pkg.getTimeWindowStartUtc());
        assertEquals("2021-08-01T14:00Z", pkg.getTimeWindowStartUtc().toString());
        assertNull(pkg.getTimeWindowEndUtc());
    }

    @Test
    void testInvalidInputsMissingFiles() {
        AmazonDatasetProvider provider = new AmazonDatasetProvider();
        
        Path missingRoute = tempDir.resolve("missing_route.json");
        Path packageFile = tempDir.resolve("pkg.json");
        Path seqFile = tempDir.resolve("seq.json");
        
        assertThrows(IOException.class, () -> provider.loadDataset(missingRoute, packageFile, seqFile));
    }
    
    @Test
    void testInvalidInputsMalformedJson() throws IOException {
        Path routeFile = tempDir.resolve("malformed_route.json");
        Files.writeString(routeFile, "{ malformed: json ");
        
        Path packageFile = tempDir.resolve("pkg2.json");
        Files.writeString(packageFile, "{}");
        Path seqFile = tempDir.resolve("seq2.json");
        Files.writeString(seqFile, "{}");
        
        AmazonDatasetProvider provider = new AmazonDatasetProvider();
        assertThrows(IOException.class, () -> provider.loadDataset(routeFile, packageFile, seqFile));
    }
    
    @Test
    void testCrossFileConsistencyMissingRouteInActual() throws IOException {
        Path routeFile = tempDir.resolve("r_route.json");
        Path packageFile = tempDir.resolve("r_pkg.json");
        Path seqFile = tempDir.resolve("r_seq.json");
        
        Files.writeString(routeFile, "{\"ROUTE_1\": {\"stops\": {}}}");
        Files.writeString(packageFile, "{\"ROUTE_1\": {}}");
        // missing in actual_sequences
        Files.writeString(seqFile, "{}");
        
        AmazonDatasetProvider provider = new AmazonDatasetProvider();
        List<AmazonRouteRecord> routes = provider.loadDataset(routeFile, packageFile, seqFile);
        
        // It skips routes that are missing in actual_sequences
        assertEquals(0, routes.size());
    }

    @Test
    void testUnknownStopIdIsHandledSafely() throws IOException {
        Path routeFile = tempDir.resolve("u_route.json");
        Path packageFile = tempDir.resolve("u_pkg.json");
        Path seqFile = tempDir.resolve("u_seq.json");

        // Valid Route_1 with Stop_A
        Files.writeString(routeFile, """
            {
              "ROUTE_1": {
                "stops": {
                  "STOP_A": {
                    "lat": 1.1,
                    "lng": 2.2
                  }
                }
              }
            }
            """);

        // Package data contains STOP_A and an unknown STOP_UNKNOWN
        Files.writeString(packageFile, """
            {
              "ROUTE_1": {
                "STOP_UNKNOWN": {
                  "PKG_1": {
                    "scan_status": "DELIVERED"
                  }
                },
                "STOP_A": {
                  "PKG_2": {
                    "scan_status": "DELIVERED"
                  }
                }
              }
            }
            """);

        // Sequence data for STOP_A
        Files.writeString(seqFile, """
            {
              "ROUTE_1": {
                "actual": {
                  "STOP_A": 0
                }
              }
            }
            """);

        AmazonDatasetProvider provider = new AmazonDatasetProvider();
        List<AmazonRouteRecord> routes = provider.loadDataset(routeFile, packageFile, seqFile);

        assertEquals(1, routes.size());
        AmazonRouteRecord route = routes.get(0);
        
        List<AmazonStopRecord> stops = route.getStops();
        assertEquals(1, stops.size());
        
        AmazonStopRecord stopA = stops.get(0);
        assertEquals("STOP_A", stopA.getStopId());
        
        List<AmazonPackageRecord> pkgs = stopA.getPackages();
        assertEquals(1, pkgs.size());
        assertEquals("PKG_2", pkgs.get(0).getPackageId());
    }
}
