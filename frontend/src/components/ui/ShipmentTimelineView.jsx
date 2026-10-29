import React from 'react';
import { 
  CheckCircle2, Clock, Package, MapPin, Truck, Check, Sparkles, 
  ArrowRight, ShieldCheck, AlertCircle, Compass 
} from 'lucide-react';
import MapComponent from './Map';

export default function ShipmentTimelineView({ delivery: rawDelivery, shipmentData, onRefresh }) {
  const delivery = rawDelivery || shipmentData;
  if (!delivery) return null;

  const pkg = delivery.packageDetails || {};
  const currentStage = delivery.currentStage || 'REQUEST_CREATED';
  const events = delivery.events || [];
  const legs = delivery.legs || [];

  // Six main authoritative milestones
  const MILESTONES = [
    {
      key: 'REQUEST_CREATED',
      title: 'Order Placed / Request Created',
      defaultDesc: 'Your shipment request has been recorded and validated in the smart network.',
      icon: Package,
    },
    {
      key: 'PICKUP_ASSIGNED',
      title: 'Pickup Partner Assigned',
      defaultDesc: 'A dedicated delivery partner has been assigned for first-mile sender pickup.',
      icon: UserCheckIcon,
    },
    {
      key: 'PICKED_UP',
      title: 'Picked Up from Sender',
      defaultDesc: 'Package securely collected from sender and in transit to the nearest service hub.',
      icon: Truck,
    },
    {
      key: 'HUB_RECEIVED',
      title: 'Received & Processed at Central Hub',
      defaultDesc: `Package received at ${delivery.hubName || 'Central Hub'} for sorting and final-mile staging.`,
      icon: ShieldCheck,
    },
    {
      key: 'OUT_FOR_DELIVERY',
      title: 'Final-Mile Partner Assigned / Out for Delivery',
      defaultDesc: 'Final-mile delivery partner is on the way to the recipient destination.',
      icon: Compass,
    },
    {
      key: 'DELIVERED',
      title: 'Delivered',
      defaultDesc: 'Package delivered successfully to the recipient. Enjoy!',
      icon: CheckCircle2,
    },
  ];

  const STAGE_ORDER = ['REQUEST_CREATED', 'PICKUP_ASSIGNED', 'PICKED_UP', 'HUB_RECEIVED', 'OUT_FOR_DELIVERY', 'DELIVERED'];
  const currentIndex = STAGE_ORDER.indexOf(currentStage);

  // Map events by stage key for quick timestamp/description lookup
  const eventMap = {};
  events.forEach((ev) => {
    eventMap[ev.stage] = ev;
  });

  const formatEventTime = (isoString) => {
    if (!isoString) return '';
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString('en-IN', {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  // Build map locations and routes from legs
  const mapLocations = [];
  const mapEdges = [];

  if (delivery.sourceLocationId) {
    mapLocations.push({
      id: delivery.sourceLocationId,
      name: delivery.sourceLocationName || 'Sender Pickup',
      latitude: legs[0]?.sourceLatitude,
      longitude: legs[0]?.sourceLongitude,
      locationType: 'CUSTOMER'
    });
  }
  if (delivery.hubId) {
    mapLocations.push({
      id: delivery.hubId,
      name: delivery.hubName || 'Central Hub',
      latitude: delivery.hubLatitude || legs[0]?.destinationLatitude,
      longitude: delivery.hubLongitude || legs[0]?.destinationLongitude,
      locationType: 'HUB'
    });
  }
  if (delivery.destinationLocationId) {
    mapLocations.push({
      id: delivery.destinationLocationId,
      name: delivery.destinationLocationName || 'Recipient Delivery',
      latitude: legs[1]?.destinationLatitude,
      longitude: legs[1]?.destinationLongitude,
      locationType: 'CUSTOMER'
    });
  }

  // Filter valid locations with lat/lng
  const validMapLocations = mapLocations.filter(l => l.latitude != null && l.longitude != null);

  // Build route edges for Leaflet polyline rendering
  const routeEdgesForMap = legs
    .filter(l => l.routeGeometry)
    .map((l, idx) => ({
      sourceLocationId: l.sourceLocationId,
      destinationLocationId: l.destinationLocationId,
      geometryGeojson: l.routeGeometry,
      edgeOrder: idx + 1
    }));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* Top Main Grid: Left Timeline + Right Order Summary (Matches Screenshot 4) */}
      <div 
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: 24,
          alignItems: 'start'
        }}
      >
        {/* LEFT CARD: 📍 Shipment Timeline */}
        <div
          style={{
            backgroundColor: 'var(--theme-surface)',
            border: '1px solid var(--theme-border)',
            borderRadius: 'var(--radius-card)',
            padding: '28px',
            boxShadow: 'var(--shadow-card)',
            position: 'relative'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 28 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span style={{ fontSize: 20 }}>📍</span>
              <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: 'var(--theme-text)' }}>
                Shipment Timeline
              </h3>
            </div>
            <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--theme-muted)', letterSpacing: 0.5 }}>
              TRACKING: {delivery.trackingNumber}
            </span>
          </div>

          {/* Stepper with vertical connecting line */}
          <div style={{ position: 'relative', paddingLeft: 8 }}>
            {MILESTONES.map((milestone, idx) => {
              const stepIndex = STAGE_ORDER.indexOf(milestone.key);
              const isCompleted = currentIndex > stepIndex || (currentIndex === 5 && stepIndex === 5);
              const isCurrent = currentIndex === stepIndex && currentIndex !== 5;
              const isPending = stepIndex > currentIndex;
              const recordedEvent = eventMap[milestone.key];
              const eventTime = recordedEvent ? formatEventTime(recordedEvent.eventTimestamp) : '';

              const isLast = idx === MILESTONES.length - 1;

              return (
                <div key={milestone.key} style={{ position: 'relative', display: 'flex', gap: 18, minHeight: 74 }}>
                  {/* Vertical line connecting to next milestone */}
                  {!isLast && (
                    <div
                      style={{
                        position: 'absolute',
                        left: 17,
                        top: 36,
                        bottom: -4,
                        width: 2,
                        backgroundColor: isCompleted ? 'var(--color-primary)' : 'var(--theme-border)',
                        transition: 'background-color 0.3s ease'
                      }}
                    />
                  )}

                  {/* Icon Circle */}
                  <div style={{ position: 'relative', zIndex: 2, flexShrink: 0 }}>
                    {isCompleted ? (
                      <div
                        style={{
                          width: 36,
                          height: 36,
                          borderRadius: '50%',
                          backgroundColor: 'var(--color-primary)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: '#FFFFFF',
                          boxShadow: '0 2px 8px rgba(16, 185, 129, 0.35)',
                        }}
                      >
                        <Check size={18} strokeWidth={3} />
                      </div>
                    ) : isCurrent ? (
                      <div
                        style={{
                          width: 36,
                          height: 36,
                          borderRadius: '50%',
                          backgroundColor: 'rgba(16, 185, 129, 0.15)',
                          border: '2px solid var(--color-primary)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: 'var(--color-primary)',
                          boxShadow: '0 0 12px rgba(16, 185, 129, 0.45)',
                          animation: 'pulseGlow 2s infinite ease-in-out'
                        }}
                      >
                        <milestone.icon size={18} strokeWidth={2.5} />
                      </div>
                    ) : (
                      <div
                        style={{
                          width: 36,
                          height: 36,
                          borderRadius: '50%',
                          backgroundColor: 'var(--theme-elevated)',
                          border: '2px solid var(--theme-border)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: 'var(--theme-muted)'
                        }}
                      >
                        <div style={{ width: 8, height: 8, borderRadius: '50%', backgroundColor: 'var(--theme-muted)', opacity: 0.5 }} />
                      </div>
                    )}
                  </div>

                  {/* Content details */}
                  <div style={{ flex: 1, paddingBottom: 20 }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span 
                          style={{ 
                            fontSize: 15, 
                            fontWeight: isCurrent || isCompleted ? 700 : 500,
                            color: isPending ? 'var(--theme-muted)' : 'var(--theme-text)' 
                          }}
                        >
                          {milestone.title}
                        </span>
                        {isCurrent && (
                          <span 
                            style={{
                              backgroundColor: 'rgba(16, 185, 129, 0.15)',
                              color: 'var(--color-primary)',
                              fontSize: 11,
                              fontWeight: 700,
                              padding: '2px 8px',
                              borderRadius: 12,
                              border: '1px solid rgba(16, 185, 129, 0.3)'
                            }}
                          >
                            Current
                          </span>
                        )}
                      </div>
                      {eventTime && (
                        <span style={{ fontSize: 12, color: 'var(--theme-muted)', fontWeight: 500, whiteSpace: 'nowrap' }}>
                          {eventTime}
                        </span>
                      )}
                    </div>

                    <p 
                      style={{ 
                        margin: '6px 0 0', 
                        fontSize: 13, 
                        color: isPending ? 'var(--theme-muted)' : 'var(--theme-text)', 
                        opacity: isPending ? 0.7 : 0.85,
                        lineHeight: 1.45
                      }}
                    >
                      {recordedEvent?.description || milestone.defaultDesc}
                    </p>

                    {/* Additional contextual leg details */}
                    {milestone.key === 'HUB_RECEIVED' && delivery.hubName && (
                      <div style={{ marginTop: 6, display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--color-primary)' }}>
                        <MapPin size={12} />
                        <span>Hub: {delivery.hubName}</span>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* RIGHT CARD: ORDER SUMMARY & PACKAGE CONTENTS (Matches Screenshot 4) */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Order Summary Box */}
          <div
            style={{
              backgroundColor: 'var(--theme-surface)',
              border: '1px solid var(--theme-border)',
              borderRadius: 'var(--radius-card)',
              padding: '24px',
              boxShadow: 'var(--shadow-card)'
            }}
          >
            <h4 style={{ margin: '0 0 16px', fontSize: 13, fontWeight: 700, color: 'var(--theme-muted)', textTransform: 'uppercase', letterSpacing: 0.8 }}>
              Order Summary
            </h4>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 14, color: 'var(--theme-muted)' }}>Payment Method</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--theme-text)' }}>
                  {pkg.declaredValue ? 'Prepaid Online' : 'Standard Delivery'}
                </span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 14, color: 'var(--theme-muted)' }}>Payment Status</span>
                <span 
                  style={{
                    backgroundColor: 'rgba(245, 158, 11, 0.15)',
                    color: '#F59E0B',
                    fontSize: 11,
                    fontWeight: 700,
                    padding: '2px 8px',
                    borderRadius: 4,
                    letterSpacing: 0.5
                  }}
                >
                  CONFIRMED
                </span>
              </div>

              <div 
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  paddingTop: 14,
                  marginTop: 6,
                  borderTop: '1px dashed var(--theme-border)'
                }}
              >
                <span style={{ fontSize: 16, fontWeight: 700, color: 'var(--theme-text)' }}>Total Declared Value</span>
                <span style={{ fontSize: 20, fontWeight: 800, color: 'var(--theme-text)' }}>
                  ₹{pkg.declaredValue ? pkg.declaredValue.toLocaleString('en-IN') : '1,199'}
                </span>
              </div>
            </div>

            {/* PACKAGE CONTENTS Section */}
            <div style={{ marginTop: 24, paddingTop: 18, borderTop: '1px solid var(--theme-border)' }}>
              <h4 style={{ margin: '0 0 14px', fontSize: 12, fontWeight: 700, color: 'var(--theme-muted)', textTransform: 'uppercase', letterSpacing: 0.8 }}>
                Package Contents
              </h4>

              <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
                <div
                  style={{
                    width: 56,
                    height: 56,
                    borderRadius: 'var(--radius-input)',
                    backgroundColor: 'rgba(16, 185, 129, 0.1)',
                    border: '1px solid rgba(16, 185, 129, 0.25)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'var(--color-primary)',
                    flexShrink: 0
                  }}
                >
                  <Package size={28} />
                </div>

                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--theme-text)' }}>
                    {pkg.itemName || pkg.description || 'Smart Parcel Shipment'}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 4, fontSize: 12, color: 'var(--theme-muted)' }}>
                    <span>Qty: × {pkg.quantity || 1}</span>
                    <span>•</span>
                    <span>{pkg.weight != null ? `${pkg.weight.toFixed(1)} kg` : 'Standard Weight'}</span>
                    {pkg.fragile && (
                      <>
                        <span>•</span>
                        <span style={{ color: 'var(--color-danger)', fontWeight: 600 }}>Fragile</span>
                      </>
                    )}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--theme-muted)', marginTop: 2 }}>
                    Category: {pkg.itemCategory || 'General Merchandise'}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Service Hub & Route Selection Card */}
          <div
            style={{
              backgroundColor: 'var(--theme-surface)',
              border: '1px solid var(--theme-border)',
              borderRadius: 'var(--radius-card)',
              padding: '20px',
              boxShadow: 'var(--shadow-card)'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
              <MapPin size={18} color="var(--color-primary)" />
              <h4 style={{ margin: 0, fontSize: 14, fontWeight: 700, color: 'var(--theme-text)' }}>
                Assigned Service Hub & Routing Basis
              </h4>
            </div>

            <div style={{ fontSize: 13, color: 'var(--theme-text)', fontWeight: 600 }}>
              {delivery.hubName || 'Bengaluru Central Service Hub'}
            </div>
            <div style={{ fontSize: 12, color: 'var(--theme-muted)', marginTop: 4 }}>
              {delivery.hubAddress || 'Bengaluru Urban Operations'}
            </div>
            {delivery.hubSelectionBasis && (
              <div 
                style={{
                  marginTop: 10,
                  padding: '8px 12px',
                  backgroundColor: 'var(--theme-elevated)',
                  borderRadius: 'var(--radius-input)',
                  borderLeft: '3px solid var(--color-primary)',
                  fontSize: 12,
                  color: 'var(--theme-text)'
                }}
              >
                <strong>Routing Rule:</strong> {delivery.hubSelectionBasis}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* BOTTOM SECTION: Two-Leg Interactive Map & Multi-Leg Segment Inspection */}
      <div
        style={{
          backgroundColor: 'var(--theme-surface)',
          border: '1px solid var(--theme-border)',
          borderRadius: 'var(--radius-card)',
          padding: '24px',
          boxShadow: 'var(--shadow-card)'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: 'var(--theme-text)' }}>
              Bengaluru Smart Multi-Leg Journey Map
            </h3>
            <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--theme-muted)' }}>
              Leg A (Sender → Service Hub) and Leg B (Service Hub → Recipient Destination)
            </p>
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--theme-muted)' }}>
              <span style={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: '#3B82F6' }} /> Leg A: Pickup
            </span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--theme-muted)' }}>
              <span style={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: '#10B981' }} /> Leg B: Final-Mile
            </span>
          </div>
        </div>

        {/* Legs overview cards */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16, marginBottom: 16 }}>
          {legs.map((leg, idx) => {
            const isLegA = leg.legType === 'FIRST_MILE';
            const isActiveLeg = (isLegA && ['REQUEST_CREATED', 'PICKUP_ASSIGNED', 'PICKED_UP'].includes(currentStage)) ||
                                (!isLegA && ['HUB_RECEIVED', 'OUT_FOR_DELIVERY'].includes(currentStage));

            return (
              <div
                key={leg.id || idx}
                style={{
                  padding: '14px 18px',
                  borderRadius: 'var(--radius-input)',
                  backgroundColor: 'var(--theme-elevated)',
                  border: isActiveLeg ? '2px solid var(--color-primary)' : '1px solid var(--theme-border)',
                  position: 'relative'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                  <span style={{ fontSize: 11, fontWeight: 700, color: isLegA ? '#3B82F6' : '#10B981', textTransform: 'uppercase' }}>
                    {isLegA ? 'LEG A: FIRST MILE' : 'LEG B: FINAL MILE'}
                  </span>
                  <span 
                    style={{
                      fontSize: 10,
                      fontWeight: 700,
                      padding: '2px 6px',
                      borderRadius: 4,
                      backgroundColor: leg.status === 'COMPLETED' ? 'rgba(16, 185, 129, 0.2)' : 'rgba(59, 130, 246, 0.15)',
                      color: leg.status === 'COMPLETED' ? 'var(--color-primary)' : '#3B82F6'
                    }}
                  >
                    {leg.status}
                  </span>
                </div>

                <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--theme-text)' }}>
                  {leg.sourceLocationName || 'Origin'} → {leg.destinationLocationName || 'Destination'}
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 8, fontSize: 12, color: 'var(--theme-muted)' }}>
                  <span>Dist: {leg.distance != null ? `${leg.distance.toFixed(1)} km` : '—'}</span>
                  <span>ETA: {leg.estimatedTime != null ? `${leg.estimatedTime.toFixed(0)} min` : '—'}</span>
                  {leg.driverName && <span>Driver: {leg.driverName}</span>}
                </div>
              </div>
            );
          })}
        </div>

        {/* Real Interactive Leaflet Map */}
        <div style={{ borderRadius: 'var(--radius-input)', overflow: 'hidden', border: '1px solid var(--theme-border)' }}>
          <MapComponent
            locations={validMapLocations}
            edges={[]}
            routeEdges={routeEdgesForMap}
            routePathNames={[
              delivery.sourceLocationName || 'Pickup',
              delivery.hubName || 'Hub',
              delivery.destinationLocationName || 'Destination'
            ]}
            height="380px"
          />
        </div>
      </div>
    </div>
  );
}

function UserCheckIcon(props) {
  return (
    <svg width={props.size || 24} height={props.size || 24} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={props.strokeWidth || 2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <polyline points="16 11 18 13 22 9" />
    </svg>
  );
}
