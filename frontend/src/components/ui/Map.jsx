import React, { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';

// Fix Leaflet's default icon path issues in React
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

// Create custom icons based on location type
const getIconForType = (type) => {
  let color = 'var(--color-primary)'; // default brand primary
  const t = type?.toLowerCase() || '';
  if (t.includes('hub')) color = '#8b5cf6'; // purple
  else if (t.includes('depot')) color = '#f59e0b'; // orange
  else if (t.includes('customer')) color = '#10b981'; // emerald green

  const markerHtml = `
    <div style="
      background-color: ${color};
      width: 16px;
      height: 16px;
      border-radius: 50%;
      border: 2px solid white;
      box-shadow: 0 2px 4px rgba(0,0,0,0.4);
    "></div>
  `;

  return L.divIcon({
    html: markerHtml,
    className: 'custom-leaflet-icon',
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  });
};

const getTourStopIcon = (stop) => {
  if (stop.stopType === 'START_HUB') {
    return L.divIcon({
      html: `<div style="background-color: #6366f1; color: white; width: 32px; height: 32px; border-radius: 50%; border: 3px solid white; box-shadow: 0 3px 8px rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 11px; letter-spacing: -0.5px;">HUB</div>`,
      className: 'custom-hub-icon',
      iconSize: [32, 32],
      iconAnchor: [16, 16],
    });
  }
  if (stop.stopType === 'HUB_RETURN') {
    return L.divIcon({
      html: `<div style="background-color: #8b5cf6; color: white; width: 28px; height: 28px; border-radius: 50%; border: 3px solid white; box-shadow: 0 3px 8px rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 10px;">RET</div>`,
      className: 'custom-ret-icon',
      iconSize: [28, 28],
      iconAnchor: [14, 14],
    });
  }
  return L.divIcon({
    html: `<div style="background-color: #10b981; color: white; width: 28px; height: 28px; border-radius: 50%; border: 3px solid white; box-shadow: 0 3px 8px rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 13px;">${stop.customerStopNumber || ''}</div>`,
    className: 'custom-stop-icon',
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
};

// Auto-fit bounds component
const FitBounds = ({ bounds }) => {
  const map = useMap();
  useEffect(() => {
    if (bounds && bounds.length > 0) {
      map.fitBounds(bounds, { padding: [40, 40] });
    }
  }, [bounds, map]);
  return null;
};

// Helper: parse GeoJSON geometry to array of [lat, lng]
function parseGeoJsonToLatLngs(geometryInput) {
  if (!geometryInput) return null;
  let parsed = geometryInput;
  if (typeof geometryInput === 'string') {
    try {
      parsed = JSON.parse(geometryInput);
    } catch {
      return null;
    }
  }
  let rawCoords = [];
  if (parsed.type === 'LineString' && Array.isArray(parsed.coordinates)) {
    rawCoords = parsed.coordinates;
  } else if (parsed.type === 'MultiLineString' && Array.isArray(parsed.coordinates)) {
    rawCoords = parsed.coordinates.flat();
  } else if (Array.isArray(parsed)) {
    rawCoords = parsed;
  }
  if (!rawCoords || rawCoords.length === 0) return null;

  return rawCoords.map(coord => [coord[1], coord[0]]); // convert [lon, lat] -> [lat, lon]
}

export default function MapComponent({ 
  locations = [], 
  edges = [], 
  routePathNames = null, 
  routeEdges = [], 
  customPolylinePositions = null,
  tourStops = null,
  height = '400px' 
}) {
  // Filter for valid coordinates
  const validLocations = locations.filter(loc => 
    loc.latitude != null && loc.longitude != null &&
    !isNaN(loc.latitude) && !isNaN(loc.longitude)
  );

  if (locations.length > 0 && validLocations.length === 0 && (!tourStops || tourStops.length === 0)) {
    return (
      <div style={{ height, width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: 'var(--theme-elevated)', borderRadius: 'var(--radius-card)', border: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>
        Locations lack valid coordinates for map rendering.
      </div>
    );
  }

  if (validLocations.length === 0 && (!tourStops || tourStops.length === 0)) {
    return (
      <div style={{ height, width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: 'var(--theme-elevated)', borderRadius: 'var(--radius-card)', border: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>
        No locations to display on map.
      </div>
    );
  }

  // Subdued background network edges
  const networkEdgeLines = edges.map(edge => {
    const src = validLocations.find(l => l.id === edge.sourceLocationId);
    const dest = validLocations.find(l => l.id === edge.destinationLocationId);
    if (!src || !dest) return null;

    const latLngs = parseGeoJsonToLatLngs(edge.geometryGeojson);
    return {
      id: edge.id,
      positions: latLngs || [[src.latitude, src.longitude], [dest.latitude, dest.longitude]],
      hasRoadGeometry: !!latLngs,
    };
  }).filter(Boolean);

  // Active highlighted route computation
  let activeSegments = [];
  let isRouteActive = false;

  if (customPolylinePositions && customPolylinePositions.length > 1) {
    isRouteActive = true;
    activeSegments.push({
      key: 'custom-tour-polyline',
      positions: customPolylinePositions,
      hasRoadGeometry: true,
      fromName: 'Tour Origin',
      toName: 'Tour Destination',
    });
  } else if (routeEdges && routeEdges.length > 0) {
    isRouteActive = true;
    activeSegments = routeEdges.map((re, idx) => {
      const src = validLocations.find(l => l.id === re.sourceLocationId);
      const dest = validLocations.find(l => l.id === re.destinationLocationId);
      if (!src || !dest) return null;

      // 1. Direct geometry on routeEdge DTO
      let latLngs = parseGeoJsonToLatLngs(re.geometryGeojson);

      // 2. Fallback to matching edge in edges list (directed: src -> dest)
      if (!latLngs && edges && edges.length > 0) {
        const matchingEdge = edges.find(e => 
          e.sourceLocationId === re.sourceLocationId && 
          e.destinationLocationId === re.destinationLocationId
        );
        if (matchingEdge && matchingEdge.geometryGeojson) {
          latLngs = parseGeoJsonToLatLngs(matchingEdge.geometryGeojson);
        }
      }

      const hasRoad = !!latLngs;
      return {
        key: `re-${idx}`,
        positions: latLngs || [[src.latitude, src.longitude], [dest.latitude, dest.longitude]],
        hasRoadGeometry: hasRoad,
        fromName: src.name,
        toName: dest.name,
      };
    }).filter(Boolean);
  }
  // Mode B: routePathNames provided (fallback / legacy)
  else if (routePathNames && routePathNames.length > 1) {
    isRouteActive = true;
    for (let i = 0; i < routePathNames.length - 1; i++) {
      const srcName = routePathNames[i];
      const destName = routePathNames[i+1];
      const src = validLocations.find(l => l.name === srcName);
      const dest = validLocations.find(l => l.name === destName);
      if (src && dest) {
        let latLngs = null;
        if (edges && edges.length > 0) {
          const matchingEdge = edges.find(e => 
            e.sourceLocationId === src.id && e.destinationLocationId === dest.id
          );
          if (matchingEdge && matchingEdge.geometryGeojson) {
            latLngs = parseGeoJsonToLatLngs(matchingEdge.geometryGeojson);
          }
        }

        const hasRoad = !!latLngs;
        activeSegments.push({
          key: `rpn-${i}`,
          positions: latLngs || [[src.latitude, src.longitude], [dest.latitude, dest.longitude]],
          hasRoadGeometry: hasRoad,
          fromName: src.name,
          toName: dest.name,
        });
      }
    }
  }

  // Count geometries for user status indicator
  const segmentsWithoutRoad = activeSegments.filter(s => !s.hasRoadGeometry);
  const segmentsWithRoad = activeSegments.filter(s => s.hasRoadGeometry);

  // Compute map bounding box (expand with road coordinates for optimal fit)
  let allBoundsPoints = validLocations.map(l => [l.latitude, l.longitude]);
  if (isRouteActive) {
    const routePoints = activeSegments.flatMap(s => s.positions);
    if (routePoints.length > 0) {
      allBoundsPoints = routePoints;
    }
  }

  return (
    <div style={{ height, width: '100%', borderRadius: 'var(--radius-card)', overflow: 'hidden', border: '1px solid var(--theme-border)', position: 'relative', zIndex: 0 }}>
      <MapContainer 
        bounds={allBoundsPoints} 
        style={{ height: '100%', width: '100%' }}
        scrollWheelZoom={true}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        
        {/* Subdued background network graph edges */}
        {networkEdgeLines.map((line, i) => (
          <Polyline 
            key={`net-edge-${i}`} 
            positions={line.positions} 
            color="var(--theme-muted)" 
            weight={isRouteActive ? 1.5 : 2.5}
            dashArray={line.hasRoadGeometry ? undefined : "4"}
            opacity={isRouteActive ? 0.35 : 0.65}
          />
        ))}

        {/* Highlighted active route segments */}
        {isRouteActive && activeSegments.map((segment) => (
          <Polyline 
            key={segment.key} 
            positions={segment.positions} 
            color={segment.hasRoadGeometry ? "var(--color-primary)" : "#f59e0b"} 
            weight={segment.hasRoadGeometry ? 5 : 3.5}
            dashArray={segment.hasRoadGeometry ? undefined : "6, 6"}
            opacity={0.9}
          />
        ))}

        {/* Markers: Multi-Stop Tour stops OR default Locations */}
        {tourStops && tourStops.length > 0 ? (
          tourStops
            .filter(s => s.latitude != null && s.longitude != null)
            .map((stop, idx) => (
              <Marker
                key={`tour-stop-${stop.stopId || idx}`}
                position={[stop.latitude, stop.longitude]}
                icon={getTourStopIcon(stop)}
                zIndexOffset={stop.stopType === 'START_HUB' ? 1000 : 500 - (stop.customerStopNumber || 0)}
              >
                <Popup>
                  <div style={{ fontSize: '13px', lineHeight: '1.4', minWidth: '180px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                      <span style={{ 
                        backgroundColor: stop.stopType === 'START_HUB' ? '#6366f1' : stop.stopType === 'HUB_RETURN' ? '#8b5cf6' : '#10b981',
                        color: 'white',
                        fontWeight: 'bold',
                        padding: '2px 6px',
                        borderRadius: '4px',
                        fontSize: '11px'
                      }}>
                        {stop.stopType === 'START_HUB' ? 'Central Hub' : stop.stopType === 'HUB_RETURN' ? 'Return to Hub' : `Customer Stop #${stop.customerStopNumber}`}
                      </span>
                      {stop.deliveryStatus && (
                        <span style={{ fontSize: '11px', fontWeight: 'bold', color: 'var(--color-primary)' }}>
                          [{stop.deliveryStatus}]
                        </span>
                      )}
                    </div>
                    <strong style={{ display: 'block', fontSize: '14px', marginBottom: '2px', color: 'var(--theme-text)' }}>
                      {stop.locationName || stop.address}
                    </strong>
                    {stop.address && stop.locationName && (
                      <div style={{ color: 'var(--theme-muted)', fontSize: '12px' }}>{stop.address}</div>
                    )}
                    {stop.receiverName && (
                      <div style={{ marginTop: '4px', fontSize: '12px' }}>
                        <strong>Recipient:</strong> {stop.receiverName}
                      </div>
                    )}
                    {stop.trackingNumber && (
                      <div style={{ fontSize: '12px' }}>
                        <strong>Tracking:</strong> {stop.trackingNumber}
                      </div>
                    )}
                    {stop.packageWeightKg != null && (
                      <div style={{ fontSize: '12px' }}>
                        <strong>Weight:</strong> {stop.packageWeightKg.toFixed(1)} kg
                      </div>
                    )}
                    {stop.estimatedArrivalTime && (
                      <div style={{ marginTop: '6px', padding: '4px 6px', backgroundColor: 'var(--theme-elevated)', borderRadius: '4px', border: '1px solid var(--theme-border)', fontSize: '12px' }}>
                        <strong>ETA:</strong> {new Date(stop.estimatedArrivalTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                      </div>
                    )}
                  </div>
                </Popup>
              </Marker>
            ))
        ) : (
          validLocations.map(loc => {
            let isRouteNode = true;
            if (isRouteActive) {
              if (routePathNames) {
                isRouteNode = routePathNames.includes(loc.name);
              } else if (routeEdges && routeEdges.length > 0) {
                isRouteNode = routeEdges.some(re => re.sourceLocationId === loc.id || re.destinationLocationId === loc.id);
              }
            }
            const opacity = isRouteNode ? 1 : 0.35;
            
            return (
              <Marker 
                key={loc.id} 
                position={[loc.latitude, loc.longitude]}
                icon={getIconForType(loc.locationType)}
                opacity={opacity}
              >
                <Popup>
                  <div style={{ fontSize: '13px', lineHeight: '1.4' }}>
                    <strong style={{ display: 'block', fontSize: '14px', marginBottom: '4px', color: 'var(--theme-text)' }}>
                      {loc.name}
                    </strong>
                    {loc.address && <div style={{ color: 'var(--theme-text)' }}>{loc.address}</div>}
                    {loc.locationType && <div style={{ marginTop: '2px', color: 'var(--theme-muted)' }}>Type: {loc.locationType}</div>}
                    <div style={{ color: 'var(--theme-muted)', marginTop: '6px', fontSize: '11px', fontFamily: 'monospace' }}>
                      {loc.latitude.toFixed(5)}, {loc.longitude.toFixed(5)}
                    </div>
                  </div>
                </Popup>
              </Marker>
            );
          })
        )}
        
        <FitBounds bounds={allBoundsPoints} />
      </MapContainer>
      
      {/* Route Road Geometry Status Badge */}
      {isRouteActive && (
        <div style={{ 
          position: 'absolute', 
          bottom: 12, 
          right: 12, 
          zIndex: 999, 
          backgroundColor: 'rgba(255, 255, 255, 0.95)', 
          padding: '6px 10px', 
          borderRadius: '6px', 
          fontSize: '11px', 
          border: '1px solid #d1d5db',
          boxShadow: '0 2px 6px rgba(0,0,0,0.15)',
          maxWidth: '300px',
          color: '#1f2937'
        }}>
          {segmentsWithoutRoad.length === 0 ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#047857', fontWeight: 600 }}>
              <span>🛣️</span> Real OSRM Road Geometry ({segmentsWithRoad.length} {segmentsWithRoad.length === 1 ? 'edge' : 'edges'})
            </div>
          ) : segmentsWithRoad.length === 0 ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#b45309', fontWeight: 600 }}>
              <span>⚠️</span> Straight graph connections (no stored road geometry)
            </div>
          ) : (
            <div>
              <div style={{ color: '#047857', fontWeight: 600, marginBottom: '2px' }}>
                🛣️ {segmentsWithRoad.length} road {segmentsWithRoad.length === 1 ? 'segment' : 'segments'}
              </div>
              <div style={{ color: '#b45309', fontSize: '10px' }}>
                ⚠️ {segmentsWithoutRoad.length} {segmentsWithoutRoad.length === 1 ? 'segment lacks' : 'segments lack'} stored road geometry (straight line)
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
