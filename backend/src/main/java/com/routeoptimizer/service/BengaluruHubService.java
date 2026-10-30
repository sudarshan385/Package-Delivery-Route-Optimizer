package com.routeoptimizer.service;

import com.routeoptimizer.dto.LocationResponse;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BengaluruHubService {

    private final LocationRepository locationRepository;
    private final DeliveryRepository deliveryRepository;

    public record CandidateHub(String zoneName, String canonicalName, String address, double lat, double lng) {}

    public static final List<CandidateHub> CANDIDATE_HUBS = List.of(
            new CandidateHub("Yelahanka", "Yelahanka Hub (Virtual)", "Yelahanka New Town, Bengaluru", 13.1007, 77.5963),
            new CandidateHub("Hebbal", "Hebbal Hub (Virtual)", "Hebbal Outer Ring Road, Bengaluru", 13.0358, 77.5970),
            new CandidateHub("Jakkur", "Jakkur Hub (Virtual)", "Jakkur Main Road, Bengaluru", 13.0784, 77.6070),
            new CandidateHub("Peenya", "Peenya Industrial Hub (Virtual)", "Peenya 1st Stage, Industrial Area, Bengaluru", 13.0285, 77.5197),
            new CandidateHub("Yeshwanthpur", "Yeshwanthpur Hub (Virtual)", "Yeshwanthpur APMC Yard, Bengaluru", 13.0227, 77.5487),
            new CandidateHub("Malleshwaram", "Malleshwaram Hub (Virtual)", "Malleshwaram 8th Cross, Bengaluru", 13.0031, 77.5643),
            new CandidateHub("Rajajinagar", "Rajajinagar Hub (Virtual)", "Rajajinagar 1st Block, Bengaluru", 12.9982, 77.5530),
            new CandidateHub("Majestic", "Majestic Central Hub", "Majestic Interchange, Bengaluru", 12.97791, 77.57239),
            new CandidateHub("Vijayanagar", "Vijayanagar Hub (Virtual)", "Vijayanagar Club Road, Bengaluru", 12.9719, 77.5305),
            new CandidateHub("Kengeri", "Kengeri Satellite Town Hub (Virtual)", "Kengeri Satellite Town, Bengaluru", 12.9177, 77.4838),
            new CandidateHub("Banashankari", "Banashankari Hub (Virtual)", "Banashankari 2nd Stage, Bengaluru", 12.9255, 77.5468),
            new CandidateHub("Jayanagar", "Jayanagar Hub (Virtual)", "Jayanagar 4th Block Complex, Bengaluru", 12.9308, 77.5838),
            new CandidateHub("Koramangala", "Koramangala Hub (Virtual)", "Koramangala 5th Block, Bengaluru", 12.9352, 77.6245),
            new CandidateHub("HSR Layout", "HSR Layout Hub (Virtual)", "HSR Layout Sector 1, Bengaluru", 12.9121, 77.6446),
            new CandidateHub("Bommanahalli", "Bommanahalli Hub (Virtual)", "Hosur Main Road, Bommanahalli, Bengaluru", 12.9030, 77.6242),
            new CandidateHub("Electronic City", "Electronic City Phase 1 Hub (Virtual)", "Electronic City Phase 1, Bengaluru", 12.8452, 77.6602),
            new CandidateHub("Marathahalli", "Marathahalli Bridge Hub (Virtual)", "Marathahalli Outer Ring Road, Bengaluru", 12.9591, 77.6974),
            new CandidateHub("Whitefield", "Whitefield ITPL Hub (Virtual)", "ITPL Main Road, Whitefield, Bengaluru", 12.9698, 77.7500),
            new CandidateHub("KR Puram", "KR Puram Hanging Bridge Hub (Virtual)", "KR Puram Market, Bengaluru", 13.0075, 77.6959),
            new CandidateHub("Indiranagar", "Indiranagar 100ft Road Hub (Virtual)", "100 Feet Road, Indiranagar, Bengaluru", 12.9784, 77.6408)
    );

    @Transactional
    public Map<String, Object> initializeOrSyncBengaluruHubs() {
        List<Location> allLocations = locationRepository.findAll();
        int createdCount = 0;
        int reusedCount = 0;
        List<LocationResponse> activeHubResponses = new ArrayList<>();

        for (CandidateHub candidate : CANDIDATE_HUBS) {
            // Check if existing location matches by zone name, canonical name, or close coordinates
            Location existing = allLocations.stream()
                    .filter(loc -> loc.getName().equalsIgnoreCase(candidate.canonicalName()) ||
                            loc.getName().toLowerCase().contains(candidate.zoneName().toLowerCase()) ||
                            (Math.abs(loc.getLatitude() - candidate.lat()) < 0.005 && Math.abs(loc.getLongitude() - candidate.lng()) < 0.005))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                // Ensure proper classification as HUB without mutating historical keys
                if (!"HUB".equalsIgnoreCase(existing.getLocationType())) {
                    existing.setLocationType("HUB");
                }
                if (existing.getCity() == null) {
                    existing.setCity("Bengaluru");
                }
                existing.setActive(true);
                locationRepository.save(existing);
                reusedCount++;
                activeHubResponses.add(LocationResponse.fromEntity(existing));
            } else {
                Location newHub = Location.builder()
                        .name(candidate.canonicalName())
                        .address(candidate.address())
                        .city("Bengaluru")
                        .latitude(candidate.lat())
                        .longitude(candidate.lng())
                        .locationType("HUB")
                        .build();
                newHub.setActive(true);
                newHub.setVersion(0L);
                Location saved = locationRepository.save(newHub);
                createdCount++;
                activeHubResponses.add(LocationResponse.fromEntity(saved));
            }
        }

        long totalHubsInDb = locationRepository.findAll().stream()
                .filter(l -> Boolean.TRUE.equals(l.getActive()) && "HUB".equalsIgnoreCase(l.getLocationType()))
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("createdCount", createdCount);
        result.put("reusedCount", reusedCount);
        result.put("totalCandidateZones", CANDIDATE_HUBS.size());
        result.put("totalActiveHubsInDb", totalHubsInDb);
        result.put("hubs", activeHubResponses);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllActiveHubsWithWorkloads() {
        List<Location> rawHubs = locationRepository.findAll().stream()
                .filter(l -> Boolean.TRUE.equals(l.getActive()) && 
                        l.getLatitude() != null && l.getLongitude() != null &&
                        l.getName() != null && !l.getName().toUpperCase().startsWith("PKG-") &&
                        ("HUB".equalsIgnoreCase(l.getLocationType()) || "DEPOT".equalsIgnoreCase(l.getLocationType()) || "hub".equalsIgnoreCase(l.getLocationType())))
                .sorted(Comparator.comparing(Location::getId))
                .toList();

        // Deduplicate identical coordinates at service layer to eliminate duplicate seed nodes
        Map<String, Location> canonicalMap = new LinkedHashMap<>();
        for (Location h : rawHubs) {
            String coordKey = String.format(java.util.Locale.US, "%.4f,%.4f", h.getLatitude(), h.getLongitude());
            if (!canonicalMap.containsKey(coordKey)) {
                canonicalMap.put(coordKey, h);
            } else {
                // If existing has timestamp in name and current does not, prefer current
                Location existing = canonicalMap.get(coordKey);
                boolean existingHasTimestamp = existing.getName().matches(".*\\d{9,11}(\\.\\d+)?$");
                boolean currentHasTimestamp = h.getName().matches(".*\\d{9,11}(\\.\\d+)?$");
                if (existingHasTimestamp && !currentHasTimestamp) {
                    canonicalMap.put(coordKey, h);
                }
            }
        }

        List<Location> hubs = new ArrayList<>(canonicalMap.values());
        hubs.sort(Comparator.comparing(l -> l.getName().replaceAll("\\s+\\d{9,11}(\\.\\d+)?$", "").trim()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Location hub : hubs) {
            String cleanName = hub.getName().replaceAll("\\s+\\d{9,11}(\\.\\d+)?$", "").trim();
            Map<String, Object> hubMap = new LinkedHashMap<>();
            hubMap.put("id", hub.getId());
            hubMap.put("name", cleanName);
            hubMap.put("address", hub.getAddress());
            hubMap.put("city", hub.getCity() != null ? hub.getCity() : "Bengaluru");
            hubMap.put("latitude", hub.getLatitude());
            hubMap.put("longitude", hub.getLongitude());
            hubMap.put("locationType", hub.getLocationType());
            hubMap.put("active", hub.getActive());

            // Compute pending work at this hub
            long unassignedPickups = deliveryRepository.findAll().stream()
                    .filter(d -> d.getHub() != null && d.getHub().getId().equals(hub.getId()) &&
                            "REQUEST_CREATED".equalsIgnoreCase(d.getCurrentStage()))
                    .count();

            long shipmentsAtHub = deliveryRepository.findAll().stream()
                    .filter(d -> d.getHub() != null && d.getHub().getId().equals(hub.getId()) &&
                            "HUB_RECEIVED".equalsIgnoreCase(d.getCurrentStage()))
                    .count();

            hubMap.put("pendingPickupsCount", unassignedPickups);
            hubMap.put("packagesAtHubCount", shipmentsAtHub);
            result.add(hubMap);
        }
        return result;
    }

    @Transactional
    public LocationResponse updateHubStatus(Long id, boolean active) {
        Location hub = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hub not found with id: " + id));
        hub.setActive(active);
        return LocationResponse.fromEntity(locationRepository.save(hub));
    }
}
