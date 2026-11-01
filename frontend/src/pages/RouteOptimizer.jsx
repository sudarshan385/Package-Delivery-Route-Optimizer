import { useState, useEffect } from 'react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { 
  Route, Navigation, Compass, MapPin, Flag, ArrowRight, Truck, 
  CheckCircle2, AlertTriangle, Clock, Layers, Send, RefreshCw, 
  UserCheck, CheckSquare, Square, Package, Weight, AlertCircle, Sparkles,
  Activity, Globe, Zap
} from 'lucide-react';
import PageHeader from '../components/ui/PageHeader';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Select from '../components/ui/Select';
import { StatusBadge } from '../components/ui/Badge';
import EmptyState from '../components/ui/EmptyState';
import LoadingState from '../components/ui/LoadingState';
import ErrorState, { parseApiError } from '../components/ui/ErrorState';
import MapComponent from '../components/ui/Map';
import NetworkGraphVisualizer from '../components/ui/NetworkGraphVisualizer';
import RouteAlternativesCard from '../components/ui/RouteAlternativesCard';

export default function RouteOptimizer() {
  const { user } = useAuth();
  const isDriver = user?.role === 'DRIVER';
  const isAdmin = user?.role === 'ADMIN';

  // Navigation mode for Admin: 'MULTI_STOP' (Dispatch) vs 'POINT_TO_POINT' (Legacy Dijkstra)
  const [activeTab, setActiveTab] = useState('MULTI_STOP');

  // Shared network data
  const [locations, setLocations] = useState([]);
  const [edges, setEdges] = useState([]);
  const [deliveries, setDeliveries] = useState([]);
  const [pageLoading, setPageLoading] = useState(true);
  const [fetchError, setFetchError] = useState('');

  // --- ADMIN MULTI-STOP DISPATCH STATE ---
  const [selectedHubId, setSelectedHubId] = useState('');
  const [unassignedDeliveries, setUnassignedDeliveries] = useState([]);
  const [loadingUnassigned, setLoadingUnassigned] = useState(false);
  const [selectedDeliveryIds, setSelectedDeliveryIds] = useState([]);
  const [multiCriterion, setMultiCriterion] = useState('SHORTEST_DISTANCE');
  const [returnToHub, setReturnToHub] = useState(true);
  const [optimizingMulti, setOptimizingMulti] = useState(false);
  const [multiError, setMultiError] = useState('');
  const [multiTourResult, setMultiTourResult] = useState(null);

  // Available resources for batch assignment
  const [availableDrivers, setAvailableDrivers] = useState([]);
  const [availableVehicles, setAvailableVehicles] = useState([]);
  const [selectedDriverId, setSelectedDriverId] = useState('');
  const [selectedVehicleId, setSelectedVehicleId] = useState('');
  const [assigningBatch, setAssigningBatch] = useState(false);
  const [assignSuccess, setAssignSuccess] = useState('');
  const [assignError, setAssignError] = useState('');

  // --- DRIVER MANIFEST STATE ---
  const [driverManifest, setDriverManifest] = useState(null);
  const [loadingManifest, setLoadingManifest] = useState(false);
  const [manifestError, setManifestError] = useState('');
  const [statusUpdatingId, setStatusUpdatingId] = useState(null);
  const [statusActionMessage, setStatusActionMessage] = useState('');
  const [confirmingReturn, setConfirmingReturn] = useState(false);

  // --- LEGACY SINGLE ROUTE / POINT-TO-POINT STATE ---
  const [sourceId, setSourceId] = useState('');
  const [destId, setDestId] = useState('');
  const [deliveryId, setDeliveryId] = useState('');
  const [singleCriterion, setSingleCriterion] = useState('SHORTEST_DISTANCE');
  const [singleResult, setSingleResult] = useState(null);
  const [singleLoading, setSingleLoading] = useState(false);
  const [singleError, setSingleError] = useState('');
  const [p2pViewMode, setP2pViewMode] = useState('MAP'); // 'MAP' or 'GRAPH'
  const [selectedAlternative, setSelectedAlternative] = useState(null);

  // Initial data loading
  useEffect(() => {
    loadNetworkData();
    if (isDriver) {
      loadDriverManifest();
    }
  }, [isDriver]);

  const loadNetworkData = () => {
    setPageLoading(true);
    Promise.all([
      api.get('/locations', { params: { size: 200 } }),
      api.get('/edges', { params: { size: 200 } }),
      api.get('/deliveries'),
    ])
      .then(([lRes, eRes, dRes]) => {
        const locs = lRes.data.content || [];
        setLocations(locs);
        setEdges(eRes.data.content || []);
        const deliveriesData = Array.isArray(dRes.data) ? dRes.data : dRes.data.content || [];
        setDeliveries(deliveriesData);

        // Auto-select first valid Hub if available
        const firstHub = locs.find(l => {
          const t = l.locationType?.toUpperCase();
          return (t === 'HUB' || t === 'DEPOT') && !l.name?.toUpperCase().startsWith('PKG-') && l.active !== false;
        });
        if (firstHub && !selectedHubId) {
          setSelectedHubId(String(firstHub.id));
          loadUnassignedDeliveries(firstHub.id);
        }
      })
      .catch((err) => {
        setFetchError(parseApiError(err, 'Failed to load route network topology'));
      })
      .finally(() => setPageLoading(false));
  };

  // Load unassigned deliveries when Hub changes
  const handleHubChange = (hubId) => {
    setSelectedHubId(hubId);
    setSelectedDeliveryIds([]);
    setMultiTourResult(null);
    setMultiError('');
    setAssignSuccess('');
    setAssignError('');
    if (hubId) {
      loadUnassignedDeliveries(hubId);
    } else {
      setUnassignedDeliveries([]);
    }
  };

  const loadUnassignedDeliveries = (hubId) => {
    setLoadingUnassigned(true);
    api.get('/deliveries/unassigned', { params: { hubId } })
      .then((res) => {
        setUnassignedDeliveries(res.data || []);
      })
      .catch((err) => {
        setMultiError(parseApiError(err, 'Failed to load unassigned deliveries for this hub'));
        setUnassignedDeliveries([]);
      })
      .finally(() => setLoadingUnassigned(false));
  };

  // Toggle delivery selection
  const toggleDeliverySelection = (id) => {
    setSelectedDeliveryIds(prev => 
      prev.includes(id) ? prev.filter(x => x !== id) : Array.from(new Set([...prev, id]))
    );
  };

  const selectAllDeliveries = () => {
    if (selectedDeliveryIds.length === unassignedDeliveries.length) {
      setSelectedDeliveryIds([]);
    } else {
      setSelectedDeliveryIds(unassignedDeliveries.map(d => d.id));
    }
  };

  // Load available drivers and vehicles
  const loadAvailableResources = () => {
    Promise.all([
      api.get('/drivers', { params: { status: 'AVAILABLE', size: 100 } }),
      api.get('/vehicles', { params: { status: 'AVAILABLE', size: 100 } }),
    ])
      .then(([dRes, vRes]) => {
        setAvailableDrivers(dRes.data.content || dRes.data || []);
        setAvailableVehicles(vRes.data.content || vRes.data || []);
      })
      .catch((err) => {
        console.error('Failed to load dispatch resources:', err);
      });
  };

  // Calculate selected payload weight from real database weights
  const totalSelectedPayload = unassignedDeliveries
    .filter(d => selectedDeliveryIds.includes(d.id))
    .reduce((sum, d) => sum + (d.packageWeight || d.weight || d.packageDetails?.weight || 0), 0);

  // --- MULTI-STOP TOUR OPTIMIZATION (ADMIN) ---
  const handleOptimizeMultiStop = async () => {
    if (!selectedHubId) {
      setMultiError('Please select a Central Hub origin');
      return;
    }
    if (selectedDeliveryIds.length === 0) {
      setMultiError('Please select at least one delivery destination for the tour');
      return;
    }

    setOptimizingMulti(true);
    setMultiError('');
    setAssignSuccess('');
    setAssignError('');

    try {
      const payload = {
        hubLocationId: Number(selectedHubId),
        deliveryIds: selectedDeliveryIds,
        criterion: multiCriterion,
        returnToHub: returnToHub,
      };

      const res = await api.post('/optimization/multi-stop', payload);
      setMultiTourResult(res.data);
      // Fetch available drivers and vehicles for assignment
      loadAvailableResources();
    } catch (err) {
      setMultiError(parseApiError(err, 'Multi-stop tour optimization failed'));
      setMultiTourResult(null);
    } finally {
      setOptimizingMulti(false);
    }
  };

  // Selected vehicle details and capacity validation
  const currentVehicle = availableVehicles.find(v => String(v.id) === String(selectedVehicleId));
  const vehicleCapacity = currentVehicle?.capacity ?? currentVehicle?.capacityKg ?? 0;
  const tourTotalWeight = multiTourResult?.totalWeightKg ?? totalSelectedPayload;
  const isOverloaded = Boolean(currentVehicle && vehicleCapacity > 0 && tourTotalWeight > vehicleCapacity);
  const capacityPercent = currentVehicle && vehicleCapacity > 0 ? Math.min(100, Math.round((tourTotalWeight / vehicleCapacity) * 100)) : 0;

  // --- BATCH ASSIGNMENT DISPATCH (ADMIN) ---
  const handleBatchAssign = async () => {
    if (!multiTourResult?.routeId) {
      setAssignError('Please calculate an optimized tour first');
      return;
    }
    if (!selectedDriverId) {
      setAssignError('Please select an available driver');
      return;
    }
    if (!selectedVehicleId) {
      setAssignError('Please select an available vehicle');
      return;
    }
    if (isOverloaded) {
      setAssignError(`Cannot dispatch: Payload (${tourTotalWeight.toFixed(1)} kg) exceeds vehicle capacity (${vehicleCapacity.toFixed(1)} kg)`);
      return;
    }

    setAssigningBatch(true);
    setAssignError('');
    setAssignSuccess('');

    try {
      const payload = {
        routeId: multiTourResult.routeId,
        driverId: Number(selectedDriverId),
        vehicleId: Number(selectedVehicleId),
        deliveryIds: selectedDeliveryIds,
      };

      const res = await api.post('/deliveries/batch-assign', payload);
      const assignedCount = Array.isArray(res.data?.assignedDeliveryIds)
        ? res.data.assignedDeliveryIds.length
        : (res.data?.assignedDeliveryCount ?? selectedDeliveryIds.length);
      const vehicleReg = res.data?.vehicleRegistrationNumber || res.data?.vehicleRegistration || currentVehicle?.registrationNumber || 'N/A';
      const driver = res.data?.driverName || 'assigned driver';
      setAssignSuccess(`Manifest successfully dispatched! Assigned ${assignedCount} deliveries to Driver ${driver} with vehicle ${vehicleReg}.`);
      
      // Reset selections and refresh
      setSelectedDeliveryIds([]);
      setMultiTourResult(null);
      setSelectedDriverId('');
      setSelectedVehicleId('');
      if (selectedHubId) {
        loadUnassignedDeliveries(selectedHubId);
      }
    } catch (err) {
      setAssignError(parseApiError(err, 'Batch assignment failed'));
    } finally {
      setAssigningBatch(false);
    }
  };

  // --- DRIVER WORKFLOW METHODS ---
  const loadDriverManifest = () => {
    setLoadingManifest(true);
    setManifestError('');
    api.get('/deliveries/manifest/driver')
      .then((res) => {
        setDriverManifest(res.data);
      })
      .catch((err) => {
        if (err.response?.status === 404) {
          setDriverManifest(null);
        } else {
          setManifestError(parseApiError(err, 'Failed to fetch active driver manifest'));
        }
      })
      .finally(() => setLoadingManifest(false));
  };

  const handleUpdateDeliveryStatus = async (deliveryId, nextStatus) => {
    setStatusUpdatingId(deliveryId);
    setStatusActionMessage('');
    try {
      await api.patch(`/deliveries/${deliveryId}/status`, { status: nextStatus });
      setStatusActionMessage(`Delivery #${deliveryId} updated to ${nextStatus}`);
      // Re-fetch manifest to update stop states and ETAs
      loadDriverManifest();
    } catch (err) {
      setManifestError(parseApiError(err, `Failed to update delivery to ${nextStatus}`));
    } finally {
      setStatusUpdatingId(null);
    }
  };

  const handleConfirmReturnToHub = async () => {
    if (!driverManifest?.routeId) return;
    setConfirmingReturn(true);
    setStatusActionMessage('');
    setManifestError('');
    try {
      await api.post(`/deliveries/routes/${driverManifest.routeId}/confirm-return`);
      setStatusActionMessage('Vehicle safely returned to Central Hub! Driver and vehicle released to AVAILABLE.');
      loadDriverManifest();
    } catch (err) {
      setManifestError(parseApiError(err, 'Failed to confirm return to hub'));
    } finally {
      setConfirmingReturn(false);
    }
  };

  // --- POINT-TO-POINT LEGACY SOLVER METHODS ---
  const handleSourceChange = (val) => {
    setSourceId(val);
    if (val && destId && String(val) === String(destId)) {
      setSingleError('Source and destination locations must be distinct. Please select different origin and destination locations.');
    } else if (singleError === 'Source and destination locations must be distinct. Please select different origin and destination locations.') {
      setSingleError('');
    }
  };

  const handleDestChange = (val) => {
    setDestId(val);
    if (val && sourceId && String(val) === String(sourceId)) {
      setSingleError('Source and destination locations must be distinct. Please select different origin and destination locations.');
    } else if (singleError === 'Source and destination locations must be distinct. Please select different origin and destination locations.') {
      setSingleError('');
    }
  };
  const handleSingleOptimize = async () => {
    if (!deliveryId && (!sourceId || !destId)) {
      setSingleError('Please select both source and destination');
      return;
    }
    if (!deliveryId && sourceId === destId) {
      setSingleError('Source and destination cannot be identical');
      return;
    }

    setSingleLoading(true);
    setSingleError('');
    setSingleResult(null);

    try {
      const payload = { criterion: singleCriterion };
      if (deliveryId) {
        payload.deliveryId = Number(deliveryId);
      } else {
        payload.startLocationId = Number(sourceId);
        payload.destinationLocationId = Number(destId);
      }

      const res = await api.post('/optimization', payload);
      setSingleResult(res.data);
    } catch (err) {
      setSingleError(parseApiError(err, 'Dijkstra route optimization failed'));
    } finally {
      setSingleLoading(false);
    }
  };

  // Format timestamp
  const formatTime = (iso) => {
    if (!iso) return '—';
    try {
      return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', month: 'short', day: 'numeric' });
    } catch {
      return iso;
    }
  };

  if (pageLoading) {
    return <LoadingState message="Loading network topology for route optimizer..." />;
  }

  // ==========================================
  // DRIVER VIEW
  // ==========================================
  if (isDriver) {
    return (
      <div style={{ maxWidth: '1280px', margin: '0 auto' }}>
        <PageHeader
          title="Assigned Tour Manifest"
          subtitle="Sequential delivery tour navigation, customer stops, and lifecycle tracking"
          action={
            <Button
              variant="secondary"
              icon={RefreshCw}
              onClick={loadDriverManifest}
              loading={loadingManifest}
              size="sm"
              id="refresh-manifest-btn"
            >
              Refresh Manifest
            </Button>
          }
        />

        {manifestError && <ErrorState error={manifestError} className="mb-4" />}
        {statusActionMessage && (
          <div className="alert alert-success mb-4" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <CheckCircle2 size={18} />
            <span>{statusActionMessage}</span>
          </div>
        )}

        {loadingManifest && !driverManifest ? (
          <LoadingState message="Loading assigned route manifest..." />
        ) : !driverManifest || !driverManifest.stops || driverManifest.stops.length === 0 ? (
          <Card className="mb-6">
            <EmptyState
              icon={Truck}
              title="No Active Multi-Stop Tour Assigned"
              description="You do not currently have an active multi-stop delivery manifest. Once dispatch assigns you a tour, your stops and route will appear here automatically."
            />
          </Card>
        ) : (
          <div>
            {/* Manifest Overview Bar */}
            <Card
              title={`Manifest #${driverManifest.routeId} • Multi-Stop Delivery Tour`}
              subtitle={`Origin: ${driverManifest.hubName || 'Central Hub'} • Vehicle: ${driverManifest.vehicleRegistration || 'Assigned Vehicle'}`}
              actions={
                <StatusBadge 
                  status={driverManifest.isCompleted ? 'COMPLETED' : (driverManifest.canConfirmReturn || driverManifest.routeStatus === 'RETURNING_TO_HUB') ? 'IN_PROGRESS' : 'IN_PROGRESS'} 
                  label={driverManifest.isCompleted ? 'TOUR COMPLETED' : (driverManifest.canConfirmReturn || driverManifest.routeStatus === 'RETURNING_TO_HUB') ? 'RETURNING TO HUB' : 'IN TRANSIT'} 
                />
              }
              className="mb-6"
            >
              {driverManifest.canConfirmReturn && (
                <div 
                  style={{ 
                    marginBottom: 16, 
                    padding: '16px 20px', 
                    borderRadius: 'var(--radius-card)', 
                    backgroundColor: 'rgba(99, 102, 241, 0.12)', 
                    border: '2px solid #6366f1', 
                    display: 'flex', 
                    justifyContent: 'space-between', 
                    alignItems: 'center', 
                    gap: 16,
                    flexWrap: 'wrap'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <Truck size={28} color="#6366f1" />
                    <div>
                      <strong style={{ color: '#6366f1', fontSize: '15px' }}>All Deliveries Completed • Returning to Dispatch Hub</strong>
                      <div style={{ fontSize: '13px', color: 'var(--theme-text)', marginTop: 2 }}>
                        Return Hub: <strong>{driverManifest.hubName || 'Central Hub'}</strong>
                        {driverManifest.returnLegDistance != null && (
                          <span> • Return Leg: <strong>{driverManifest.returnLegDistance.toFixed(1)} km</strong> ({driverManifest.returnLegDuration?.toFixed(0)} min)</span>
                        )}
                        <br />
                        Driver remains <strong>ON_DELIVERY</strong> and vehicle remains <strong>IN_USE</strong> until return arrival is confirmed.
                      </div>
                    </div>
                  </div>
                  <Button
                    variant="primary"
                    icon={CheckCircle2}
                    onClick={handleConfirmReturnToHub}
                    loading={confirmingReturn}
                    style={{ backgroundColor: '#6366f1', borderColor: '#6366f1' }}
                    id="confirm-return-to-hub-btn"
                  >
                    Confirm Return to Hub
                  </Button>
                </div>
              )}

              {driverManifest.isCompleted && (
                <div 
                  style={{ 
                    marginBottom: 16, 
                    padding: '14px 18px', 
                    borderRadius: 'var(--radius-card)', 
                    backgroundColor: 'rgba(16, 185, 129, 0.1)', 
                    border: '1px solid #10b981', 
                    display: 'flex', 
                    alignItems: 'center', 
                    gap: 12 
                  }}
                >
                  <CheckCircle2 size={24} color="#10b981" />
                  <div>
                    <strong style={{ color: '#10b981', fontSize: '15px' }}>Tour Completed Successfully!</strong>
                    <div style={{ fontSize: '13px', color: 'var(--theme-text)', marginTop: 2 }}>
                      All stops in this manifest have been completed and return to hub confirmed. Driver and vehicle have been automatically released to AVAILABLE.
                    </div>
                  </div>
                </div>
              )}

              {/* Metric stats grid */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
                  gap: 12,
                  padding: 16,
                  borderRadius: 'var(--radius-card)',
                  backgroundColor: 'var(--theme-elevated)',
                  border: '1px solid var(--theme-border)',
                  marginBottom: 20
                }}
              >
                <div>
                  <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                    Total Distance
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                    {driverManifest.totalDistance != null ? `${driverManifest.totalDistance.toFixed(1)} km` : '—'}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                    Est. Tour Time
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                    {driverManifest.totalEstimatedTime != null ? `${driverManifest.totalEstimatedTime.toFixed(1)} min` : '—'}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                    Total Payload
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--color-primary)', marginTop: 2 }}>
                    {driverManifest.totalWeightKg != null ? `${driverManifest.totalWeightKg.toFixed(1)} kg` : '—'}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                    Vehicle Capacity
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                    {driverManifest.vehicleCapacityKg != null ? `${driverManifest.vehicleCapacityKg.toFixed(1)} kg` : '—'}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                    Customer Stops
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                    {driverManifest.stops.filter(s => s.stopType === 'CUSTOMER_STOP').length}
                  </div>
                </div>
              </div>

              {/* Interactive Tour Map */}
              <div style={{ marginBottom: 24, borderRadius: 'var(--radius-card)', overflow: 'hidden', border: '1px solid var(--theme-border)' }}>
                <MapComponent
                  locations={locations}
                  edges={edges}
                  customPolylinePositions={driverManifest.geometryCoordinates}
                  tourStops={driverManifest.stops}
                  height="420px"
                />
              </div>

              {/* Sequential Stops Timeline */}
              <div style={{ marginBottom: 12 }}>
                <h3 style={{ fontSize: 15, fontWeight: 700, color: 'var(--theme-text)', display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Navigation size={18} color="var(--color-primary)" />
                  Sequential Delivery Tour Sequence
                </h3>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                {driverManifest.stops.map((stop, idx) => {
                  const isStartHub = stop.stopType === 'START_HUB';
                  const isReturnHub = stop.stopType === 'HUB_RETURN';
                  const isCustomer = stop.stopType === 'CUSTOMER_STOP';
                  const isCurrentUpdating = statusUpdatingId === stop.deliveryId;

                  return (
                    <div
                      key={`manifest-stop-${stop.stopId || idx}`}
                      style={{
                        padding: '16px 20px',
                        borderRadius: 'var(--radius-card)',
                        backgroundColor: 'var(--theme-elevated)',
                        border: isCustomer && stop.deliveryStatus === 'IN_PROGRESS' 
                          ? '2px solid var(--color-primary)' 
                          : '1px solid var(--theme-border)',
                        boxShadow: isCustomer && stop.deliveryStatus === 'IN_PROGRESS'
                          ? '0 4px 14px rgba(99, 102, 241, 0.15)'
                          : 'none',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 12
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 8 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                          {/* Number badge */}
                          <div
                            style={{
                              width: 34,
                              height: 34,
                              borderRadius: '50%',
                              backgroundColor: isStartHub ? '#6366f1' : isReturnHub ? '#8b5cf6' : '#10b981',
                              color: 'white',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontWeight: 800,
                              fontSize: isCustomer ? 15 : 11,
                              boxShadow: '0 2px 6px rgba(0,0,0,0.2)'
                            }}
                          >
                            {isStartHub ? 'HUB' : isReturnHub ? 'RET' : stop.customerStopNumber}
                          </div>

                          <div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                              <strong style={{ fontSize: 15, color: 'var(--theme-text)' }}>
                                {isStartHub ? `Origin: ${stop.locationName}` : isReturnHub ? `Return to Hub: ${stop.locationName}` : stop.locationName}
                              </strong>
                              {isCustomer && stop.deliveryStatus && (
                                <StatusBadge status={stop.deliveryStatus} />
                              )}
                            </div>
                            <div style={{ fontSize: 13, color: 'var(--theme-muted)', marginTop: 2 }}>
                              <MapPin size={13} style={{ display: 'inline', marginRight: 4 }} />
                              {stop.address || 'Address registered on file'}
                            </div>
                          </div>
                        </div>

                        {/* Timing details */}
                        <div style={{ textAlign: 'right', fontSize: 12, color: 'var(--theme-muted)' }}>
                          {stop.estimatedArrivalTime && (
                            <div>
                              <Clock size={12} style={{ display: 'inline', marginRight: 4 }} />
                              ETA: <strong style={{ color: 'var(--theme-text)' }}>{formatTime(stop.estimatedArrivalTime)}</strong>
                            </div>
                          )}
                          {stop.legDistance != null && (
                            <div style={{ marginTop: 2 }}>
                              Leg: {stop.legDistance.toFixed(1)} km ({stop.legDuration?.toFixed(0)} min)
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Customer Delivery Details and Actions */}
                      {isCustomer && (
                        <div 
                          style={{ 
                            padding: '12px 14px', 
                            borderRadius: 'var(--radius-input)', 
                            backgroundColor: 'var(--theme-surface)', 
                            display: 'flex', 
                            justifyContent: 'space-between', 
                            alignItems: 'center', 
                            flexWrap: 'wrap', 
                            gap: 12 
                          }}
                        >
                          <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap', fontSize: 13 }}>
                            <div>
                              <span style={{ color: 'var(--theme-muted)' }}>Recipient: </span>
                              <strong>{stop.receiverName || 'Customer'}</strong>
                            </div>
                            <div>
                              <span style={{ color: 'var(--theme-muted)' }}>Tracking: </span>
                              <code style={{ color: 'var(--color-primary)' }}>{stop.trackingNumber || `DEL-${stop.deliveryId}`}</code>
                            </div>
                            <div>
                              <span style={{ color: 'var(--theme-muted)' }}>Weight: </span>
                              <strong>{stop.packageWeightKg != null ? `${stop.packageWeightKg.toFixed(1)} kg` : '—'}</strong>
                            </div>
                          </div>

                          {/* Action Buttons for Driver */}
                          <div style={{ display: 'flex', gap: 8 }}>
                            {stop.deliveryStatus === 'ASSIGNED' && (
                              <Button
                                variant="primary"
                                size="sm"
                                icon={Navigation}
                                onClick={() => handleUpdateDeliveryStatus(stop.deliveryId, 'IN_PROGRESS')}
                                loading={isCurrentUpdating}
                                disabled={isCurrentUpdating}
                                id={`start-del-${stop.deliveryId}`}
                              >
                                Start Delivery
                              </Button>
                            )}

                            {stop.deliveryStatus === 'IN_PROGRESS' && (
                              <>
                                <Button
                                  variant="primary"
                                  size="sm"
                                  icon={CheckCircle2}
                                  onClick={() => handleUpdateDeliveryStatus(stop.deliveryId, 'COMPLETED')}
                                  loading={isCurrentUpdating}
                                  disabled={isCurrentUpdating}
                                  style={{ backgroundColor: '#10b981', borderColor: '#10b981' }}
                                  id={`complete-del-${stop.deliveryId}`}
                                >
                                  Mark Delivered
                                </Button>
                                <Button
                                  variant="secondary"
                                  size="sm"
                                  icon={AlertTriangle}
                                  onClick={() => handleUpdateDeliveryStatus(stop.deliveryId, 'FAILED')}
                                  loading={isCurrentUpdating}
                                  disabled={isCurrentUpdating}
                                  style={{ color: '#ef4444', borderColor: '#ef4444' }}
                                  id={`fail-del-${stop.deliveryId}`}
                                >
                                  Report Failed
                                </Button>
                              </>
                            )}

                            {stop.deliveryStatus === 'COMPLETED' && (
                              <span style={{ color: '#10b981', fontWeight: 600, fontSize: 13, display: 'flex', alignItems: 'center', gap: 4 }}>
                                <CheckCircle2 size={16} /> Delivered
                              </span>
                            )}

                            {stop.deliveryStatus === 'FAILED' && (
                              <span style={{ color: '#ef4444', fontWeight: 600, fontSize: 13, display: 'flex', alignItems: 'center', gap: 4 }}>
                                <AlertTriangle size={16} /> Delivery Failed
                              </span>
                            )}
                          </div>
                        </div>
                      )}

                      {isReturnHub && driverManifest.canConfirmReturn && (
                        <div 
                          style={{ 
                            padding: '12px 14px', 
                            borderRadius: 'var(--radius-input)', 
                            backgroundColor: 'var(--theme-surface)', 
                            display: 'flex', 
                            justifyContent: 'space-between', 
                            alignItems: 'center', 
                            flexWrap: 'wrap', 
                            gap: 12 
                          }}
                        >
                          <span style={{ fontSize: 13, color: 'var(--theme-text)' }}>
                            All customer stops completed. Confirm arrival at dispatch hub to complete tour and release driver & vehicle.
                          </span>
                          <Button
                            variant="primary"
                            size="sm"
                            icon={CheckCircle2}
                            onClick={handleConfirmReturnToHub}
                            loading={confirmingReturn}
                            style={{ backgroundColor: '#8b5cf6', borderColor: '#8b5cf6' }}
                            id="confirm-return-stop-btn"
                          >
                            Confirm Return to Hub
                          </Button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </Card>
          </div>
        )}
      </div>
    );
  }

  // ==========================================
  // ADMIN & DISPATCH VIEW
  // ==========================================
  // Clean location display name: strip trailing epoch timestamps e.g. "Admin Node 1 1791636709.2307775" -> "Admin Node 1"
  const cleanLocationName = (name) => {
    if (!name) return '';
    return name.replace(/\s+\d{9,12}(\.\d+)?$/, '').trim();
  };

  // Valid, deduplicated central hubs (strictly HUB/DEPOT types, no customer packages, deduplicated by coordinates)
  const hubLocations = (() => {
    const list = [];
    const seen = new Set();
    for (const l of locations) {
      if (!l || l.active === false) continue;
      const type = l.locationType?.toUpperCase();
      const isHub = (type === 'HUB' || type === 'DEPOT') && !l.name?.toUpperCase().startsWith('PKG-');
      if (isHub) {
        const lat = Number(l.latitude);
        const lon = Number(l.longitude);
        const key = !isNaN(lat) && !isNaN(lon) ? `${lat.toFixed(4)},${lon.toFixed(4)}` : l.name;
        if (!seen.has(key)) {
          seen.add(key);
          list.push({
            ...l,
            cleanName: cleanLocationName(l.name)
          });
        }
      }
    }
    return list;
  })();

  // All valid user-entered routable locations (Central hubs, customer pickup addresses, customer delivery destinations)
  const routableLocations = (() => {
    const list = [];
    const seen = new Set();
    for (const l of locations) {
      if (!l || l.active === false) continue;
      const lat = Number(l.latitude);
      const lon = Number(l.longitude);
      const key = !isNaN(lat) && !isNaN(lon) ? `${lat.toFixed(4)},${lon.toFixed(4)}` : `${l.id}-${l.name}`;
      if (!seen.has(key)) {
        seen.add(key);
        const cleaned = cleanLocationName(l.name);
        const typeBadge = l.locationType ? `[${l.locationType}]` : '';
        const addressText = l.address ? ` — ${l.address}` : '';
        list.push({
          ...l,
          cleanName: cleaned,
          displayLabel: `${cleaned} ${typeBadge}${addressText}`.trim()
        });
      }
    }
    return list;
  })();

  return (
    <div style={{ maxWidth: '1280px', margin: '0 auto' }}>
      <PageHeader
        title="Central-Hub Multi-Stop Route Optimizer"
        subtitle="Graph-theoretic 2-Opt TSP tour optimization and batch dispatch workflow"
      />

      {fetchError && <ErrorState error={fetchError} className="mb-4" />}

      {/* Mode Selector Tabs */}
      <div 
        style={{ 
          display: 'flex', 
          gap: 12, 
          marginBottom: 20, 
          borderBottom: '1px solid var(--theme-border)', 
          paddingBottom: 12 
        }}
      >
        <button
          onClick={() => setActiveTab('MULTI_STOP')}
          id="tab-multi-stop"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '10px 18px',
            borderRadius: 'var(--radius-button)',
            border: 'none',
            cursor: 'pointer',
            fontWeight: 600,
            fontSize: '14px',
            backgroundColor: activeTab === 'MULTI_STOP' ? 'var(--color-primary)' : 'var(--theme-elevated)',
            color: activeTab === 'MULTI_STOP' ? 'white' : 'var(--theme-text)',
            transition: 'all 0.2s ease',
          }}
        >
          <Layers size={16} />
          Multi-Stop Tour Dispatch (Admin)
        </button>

        <button
          onClick={() => setActiveTab('POINT_TO_POINT')}
          id="tab-point-to-point"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '10px 18px',
            borderRadius: 'var(--radius-button)',
            border: 'none',
            cursor: 'pointer',
            fontWeight: 600,
            fontSize: '14px',
            backgroundColor: activeTab === 'POINT_TO_POINT' ? 'var(--color-primary)' : 'var(--theme-elevated)',
            color: activeTab === 'POINT_TO_POINT' ? 'white' : 'var(--theme-text)',
            transition: 'all 0.2s ease',
          }}
        >
          <Route size={16} />
          Single Delivery / Point-to-Point (Dijkstra)
        </button>
      </div>

      {/* ========================================================================= */}
      {/* TAB 1: MULTI-STOP TOUR DISPATCH (PRIMARY PHASE 3 WORKFLOW) */}
      {/* ========================================================================= */}
      {activeTab === 'MULTI_STOP' && (
        <div>
          {/* Step 1: Tour Setup Card */}
          <Card
            title="Step 1: Central Hub & Unassigned Deliveries"
            subtitle="Select the origin central hub and select multiple customer delivery destinations to batch into an optimized tour"
            className="mb-6"
          >
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16 }}>
              <Select
                label="Central Hub Origin"
                id="hub-select"
                value={selectedHubId}
                onChange={(e) => handleHubChange(e.target.value)}
                required
                helperText="All deliveries on this tour will depart from this distribution center."
              >
                <option value="">Select a Central Hub</option>
                {hubLocations.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.cleanName || l.name} {l.locationType ? `(${l.locationType})` : ''} {l.address ? `— ${l.address}` : ''}
                  </option>
                ))}
              </Select>

              <Select
                label="Optimization Metric"
                id="multi-criterion-select"
                value={multiCriterion}
                onChange={(e) => setMultiCriterion(e.target.value)}
                required
              >
                <option value="SHORTEST_DISTANCE">Shortest Road Distance (km)</option>
                <option value="FASTEST_ROUTE">Fastest Travel Time (min)</option>
              </Select>
            </div>

            {/* Return to hub toggle */}
            <div style={{ marginTop: 14, display: 'flex', alignItems: 'center', gap: 10 }}>
              <input
                type="checkbox"
                id="return-to-hub-toggle"
                checked={returnToHub}
                onChange={(e) => setReturnToHub(e.target.checked)}
                style={{ width: 18, height: 18, accentColor: 'var(--color-primary)', cursor: 'pointer' }}
              />
              <label htmlFor="return-to-hub-toggle" style={{ fontSize: 14, fontWeight: 500, color: 'var(--theme-text)', cursor: 'pointer' }}>
                Return vehicle to Central Hub at conclusion of tour (Closed-loop TSP)
              </label>
            </div>

            {/* Deliveries Picker Section */}
            <div style={{ marginTop: 24 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <div>
                  <h4 style={{ fontSize: 14, fontWeight: 700, color: 'var(--theme-text)', margin: 0 }}>
                    Eligible Unassigned Deliveries (Within 10 km Radius) ({unassignedDeliveries.length})
                  </h4>
                  <span style={{ fontSize: 12, color: 'var(--theme-muted)' }}>
                    Selected: <strong>{selectedDeliveryIds.length}</strong> / {unassignedDeliveries.length} • Total Payload Weight: <strong style={{ color: 'var(--color-primary)' }}>{totalSelectedPayload.toFixed(1)} kg</strong>
                  </span>
                </div>

                {unassignedDeliveries.length > 0 && (
                  <Button
                    variant="secondary"
                    size="sm"
                    icon={selectedDeliveryIds.length === unassignedDeliveries.length ? Square : CheckSquare}
                    onClick={selectAllDeliveries}
                    id="select-all-deliveries-btn"
                  >
                    {selectedDeliveryIds.length === unassignedDeliveries.length ? 'Deselect All' : 'Select All'}
                  </Button>
                )}
              </div>

              {loadingUnassigned ? (
                <LoadingState message="Loading eligible unassigned packages from database..." />
              ) : unassignedDeliveries.length === 0 ? (
                <div style={{ padding: 24, textAlign: 'center', backgroundColor: 'var(--theme-elevated)', borderRadius: 'var(--radius-card)', border: '1px dashed var(--theme-border)' }}>
                  <Package size={28} color="var(--theme-muted)" style={{ margin: '0 auto 8px' }} />
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--theme-text)' }}>No eligible unassigned deliveries found within 10 km radius</div>
                  <div style={{ fontSize: 12, color: 'var(--theme-muted)', marginTop: 4 }}>
                    No unassigned customer deliveries with destinations within 10 km of this Central Hub. Select another hub or register new shipments.
                  </div>
                </div>
              ) : (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 12, maxHeight: '340px', overflowY: 'auto', padding: 4 }}>
                  {unassignedDeliveries.map((del) => {
                    const isSelected = selectedDeliveryIds.includes(del.id);
                    return (
                      <div
                        key={del.id}
                        onClick={() => toggleDeliverySelection(del.id)}
                        id={`delivery-item-${del.id}`}
                        style={{
                          padding: '12px 16px',
                          borderRadius: 'var(--radius-input)',
                          backgroundColor: isSelected ? 'rgba(99, 102, 241, 0.08)' : 'var(--theme-elevated)',
                          border: isSelected ? '2px solid var(--color-primary)' : '1px solid var(--theme-border)',
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 12,
                          transition: 'all 0.15s ease'
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={(e) => {
                            e.stopPropagation();
                            toggleDeliverySelection(del.id);
                          }}
                          onClick={(e) => e.stopPropagation()}
                          style={{ width: 18, height: 18, accentColor: 'var(--color-primary)', cursor: 'pointer' }}
                        />

                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <strong style={{ fontSize: 13, color: 'var(--theme-text)' }}>
                              #{del.id} • {del.trackingNumber || `PKG-${del.packageId}`}
                            </strong>
                            <span 
                              style={{ 
                                fontSize: 11, 
                                fontWeight: 700, 
                                padding: '2px 6px', 
                                borderRadius: 4, 
                                backgroundColor: 'var(--theme-surface)', 
                                color: 'var(--color-primary)' 
                              }}
                            >
                              {(del.packageWeight || del.weight || del.packageDetails?.weight || 1.0).toFixed(1)} kg
                            </span>
                          </div>

                          <div style={{ fontSize: 12, color: 'var(--theme-muted)', marginTop: 4, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            <MapPin size={11} style={{ display: 'inline', marginRight: 4 }} />
                            To: <strong style={{ color: 'var(--theme-text)' }}>{del.destinationLocationName}</strong>
                          </div>

                          {del.receiverName && (
                            <div style={{ fontSize: 11, color: 'var(--theme-muted)', marginTop: 2 }}>
                              Recipient: {del.receiverName}
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {multiError && (
              <div className="alert alert-error mt-4" style={{ marginBottom: 16 }}>
                {multiError}
              </div>
            )}

            <div style={{ marginTop: 20, display: 'flex', alignItems: 'center', gap: 12 }}>
              <Button
                variant="primary"
                icon={Sparkles}
                onClick={handleOptimizeMultiStop}
                disabled={optimizingMulti || selectedDeliveryIds.length === 0}
                loading={optimizingMulti}
                id="optimize-multi-btn"
              >
                {optimizingMulti ? 'Computing 2-Opt Multi-Stop Tour...' : `Calculate Optimized Tour (${selectedDeliveryIds.length} stops)`}
              </Button>
            </div>
          </Card>

          {/* Step 2: Tour Preview & Map */}
          {multiTourResult && (
            <Card
              title="Step 2: Optimized Tour Review"
              subtitle={`Algorithm: ${multiTourResult.algorithm || '2-Opt Dijkstra'} • Persisted Route #${multiTourResult.routeId}`}
              actions={<StatusBadge status="COMPLETED" label="TOUR CALCULATED" />}
              className="mb-6"
            >
              {multiTourResult.explanation && (
                <div 
                  style={{ 
                    marginBottom: 16, 
                    padding: '12px 16px', 
                    backgroundColor: 'var(--theme-surface)', 
                    borderLeft: '4px solid var(--color-primary)', 
                    borderRadius: '0 var(--radius-card) var(--radius-card) 0', 
                    fontSize: 13, 
                    color: 'var(--theme-text)' 
                  }}
                >
                  <strong>Optimization Strategy:</strong> {multiTourResult.explanation}
                </div>
              )}

              {/* Metrics Strip */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                  gap: 12,
                  padding: 16,
                  borderRadius: 'var(--radius-card)',
                  backgroundColor: 'var(--theme-elevated)',
                  border: '1px solid var(--theme-border)',
                  marginBottom: 20
                }}
              >
                <div>
                  <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                    Total Distance
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                    {multiTourResult.totalDistance != null ? `${multiTourResult.totalDistance.toFixed(1)} km` : '—'}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                    Estimated Duration
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                    {multiTourResult.totalEstimatedTime != null ? `${multiTourResult.totalEstimatedTime.toFixed(1)} min` : '—'}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                    Customer Stops
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                    {multiTourResult.customerStops?.length || 0} Deliveries
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                    Combined Payload
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--color-primary)', marginTop: 2 }}>
                    {(multiTourResult.totalWeightKg || totalSelectedPayload).toFixed(1)} kg
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                    Return to Hub
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                    {multiTourResult.returnToHub ? 'Yes' : 'No'}
                  </div>
                </div>
              </div>

              {/* Map View */}
              <div style={{ marginBottom: 20, borderRadius: 'var(--radius-card)', overflow: 'hidden', border: '1px solid var(--theme-border)' }}>
                {(() => {
                  const hubLoc = locations.find(l => l.id === multiTourResult.hubLocationId);
                  const previewStops = [
                    {
                      stopType: 'START_HUB',
                      locationId: multiTourResult.hubLocationId,
                      locationName: multiTourResult.hubLocationName || hubLoc?.name || 'Central Hub',
                      latitude: hubLoc?.latitude,
                      longitude: hubLoc?.longitude,
                    },
                    ...(multiTourResult.customerStops || []).map(s => ({
                      stopType: 'CUSTOMER_STOP',
                      customerStopNumber: s.customerStopNumber,
                      deliveryId: s.deliveryId,
                      trackingNumber: s.trackingNumber,
                      locationName: s.locationName,
                      latitude: s.latitude,
                      longitude: s.longitude,
                      estimatedArrivalTime: s.estimatedArrivalTime,
                      packageWeight: s.packageWeight,
                    })),
                    ...(multiTourResult.returnToHub ? [{
                      stopType: 'HUB_RETURN',
                      locationId: multiTourResult.hubLocationId,
                      locationName: multiTourResult.hubLocationName || hubLoc?.name || 'Central Hub',
                      latitude: hubLoc?.latitude,
                      longitude: hubLoc?.longitude,
                    }] : [])
                  ];

                  return (
                    <MapComponent
                      locations={locations}
                      edges={edges}
                      routeEdges={multiTourResult.orderedEdges || []}
                      tourStops={previewStops}
                      height="400px"
                    />
                  );
                })()}
              </div>

              {/* Ordered Stops Pipeline Flow */}
              <div style={{ marginBottom: 12 }}>
                <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                  Optimized Tour Sequence ({multiTourResult.customerStops?.length} Delivery Stops)
                </span>
              </div>

              <div
                style={{
                  display: 'flex',
                  alignItems: 'stretch',
                  gap: 8,
                  overflowX: 'auto',
                  paddingBottom: 8,
                  marginBottom: 24
                }}
              >
                {/* Origin Hub Card */}
                <div style={{ display: 'flex', alignItems: 'stretch' }}>
                  <div
                    style={{
                      backgroundColor: 'var(--theme-elevated)',
                      border: '1px solid var(--theme-border)',
                      borderLeft: '4px solid #6366f1',
                      borderRadius: 'var(--radius-input)',
                      padding: '12px 14px',
                      minWidth: 170,
                      display: 'flex',
                      flexDirection: 'column',
                      justifyContent: 'space-between'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                      <span style={{ fontSize: 9, fontWeight: 700, color: '#6366f1', textTransform: 'uppercase' }}>
                        CENTRAL HUB ORIGIN
                      </span>
                      <MapPin size={14} color="#6366f1" />
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--theme-text)' }}>
                      {multiTourResult.hubLocationName || 'Central Hub'}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--theme-muted)', marginTop: 4 }}>
                      Departure: {formatTime(multiTourResult.departureTime || new Date().toISOString())}
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', padding: '0 6px', color: 'var(--theme-muted)' }}>
                    <ArrowRight size={18} />
                  </div>
                </div>

                {/* Customer Stops Cards */}
                {multiTourResult.customerStops?.map((stop, idx) => {
                  const isLast = idx === multiTourResult.customerStops.length - 1;
                  return (
                    <div key={`stop-card-${stop.customerStopNumber}`} style={{ display: 'flex', alignItems: 'stretch' }}>
                      <div
                        style={{
                          backgroundColor: 'var(--theme-elevated)',
                          border: '1px solid var(--theme-border)',
                          borderLeft: '4px solid #10b981',
                          borderRadius: 'var(--radius-input)',
                          padding: '12px 14px',
                          minWidth: 180,
                          display: 'flex',
                          flexDirection: 'column',
                          justifyContent: 'space-between'
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
                          <span style={{ fontSize: 10, fontWeight: 800, color: '#10b981' }}>
                            STOP #{stop.customerStopNumber}
                          </span>
                          <span style={{ fontSize: 10, color: 'var(--theme-muted)' }}>
                            {stop.packageWeight ? `${stop.packageWeight.toFixed(1)} kg` : ''}
                          </span>
                        </div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--theme-text)' }}>
                          {stop.locationName}
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--theme-muted)', marginTop: 4 }}>
                          ETA: <strong>{formatTime(stop.estimatedArrivalTime)}</strong>
                        </div>
                        <div style={{ fontSize: 10, color: 'var(--color-primary)', marginTop: 2 }}>
                          {stop.trackingNumber || `DEL #${stop.deliveryId}`}
                        </div>
                      </div>

                      {(!isLast || multiTourResult.returnToHub) && (
                        <div style={{ display: 'flex', alignItems: 'center', padding: '0 6px', color: 'var(--theme-muted)' }}>
                          <ArrowRight size={18} />
                        </div>
                      )}
                    </div>
                  );
                })}

                {/* Return to Hub Card */}
                {multiTourResult.returnToHub && (
                  <div
                    style={{
                      backgroundColor: 'var(--theme-elevated)',
                      border: '1px solid var(--theme-border)',
                      borderLeft: '4px solid #8b5cf6',
                      borderRadius: 'var(--radius-input)',
                      padding: '12px 14px',
                      minWidth: 170,
                      display: 'flex',
                      flexDirection: 'column',
                      justifyContent: 'space-between'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                      <span style={{ fontSize: 9, fontWeight: 700, color: '#8b5cf6', textTransform: 'uppercase' }}>
                        HUB RETURN
                      </span>
                      <Flag size={14} color="#8b5cf6" />
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--theme-text)' }}>
                      {multiTourResult.hubLocationName || 'Central Hub'}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--theme-muted)', marginTop: 4 }}>
                      Tour End: {formatTime(multiTourResult.estimatedCompletionTime)}
                    </div>
                  </div>
                )}
              </div>

              {/* Step 3: Resource Assignment & Dispatch */}
              <div 
                style={{ 
                  marginTop: 24, 
                  padding: 20, 
                  backgroundColor: 'var(--theme-elevated)', 
                  borderRadius: 'var(--radius-card)', 
                  border: '1px solid var(--theme-border)' 
                }}
              >
                <h4 style={{ fontSize: 15, fontWeight: 700, color: 'var(--theme-text)', marginBottom: 4, display: 'flex', alignItems: 'center', gap: 8 }}>
                  <UserCheck size={18} color="var(--color-primary)" />
                  Step 3: Driver & Vehicle Batch Assignment
                </h4>
                <p style={{ fontSize: 12, color: 'var(--theme-muted)', marginBottom: 16 }}>
                  Select an available driver and vehicle. The system validates vehicle capacity against the combined tour payload before dispatching.
                </p>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
                  <Select
                    label="Assign Driver"
                    id="assign-driver-select"
                    value={selectedDriverId}
                    onChange={(e) => setSelectedDriverId(e.target.value)}
                    required
                  >
                    <option value="">Select Available Driver</option>
                    {availableDrivers.map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.name} {d.phone ? `(${d.phone})` : ''} — Available
                      </option>
                    ))}
                  </Select>

                  <Select
                    label="Assign Vehicle"
                    id="assign-vehicle-select"
                    value={selectedVehicleId}
                    onChange={(e) => setSelectedVehicleId(e.target.value)}
                    required
                  >
                    <option value="">Select Available Vehicle</option>
                    {availableVehicles.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.registrationNumber} — {v.model || v.vehicleType} (Cap: {v.capacity ?? v.capacityKg} kg)
                      </option>
                    ))}
                  </Select>
                </div>

                {/* Capacity Visualizer */}
                {currentVehicle && (
                  <div style={{ marginTop: 16, padding: '14px 16px', borderRadius: 'var(--radius-input)', backgroundColor: 'var(--theme-surface)', border: '1px solid var(--theme-border)' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--theme-text)' }}>
                        Vehicle Capacity Check ({currentVehicle.registrationNumber})
                      </span>
                      <span style={{ fontSize: 13, fontWeight: 700, color: isOverloaded ? '#ef4444' : '#10b981' }}>
                        {tourTotalWeight.toFixed(1)} kg / {vehicleCapacity.toFixed(1)} kg ({capacityPercent}%)
                      </span>
                    </div>

                    {/* Progress gauge */}
                    <div style={{ height: 8, borderRadius: 4, backgroundColor: 'var(--theme-border)', overflow: 'hidden' }}>
                      <div
                        style={{
                          width: `${capacityPercent}%`,
                          height: '100%',
                          backgroundColor: isOverloaded ? '#ef4444' : '#10b981',
                          transition: 'width 0.3s ease'
                        }}
                      />
                    </div>

                    {isOverloaded && (
                      <div style={{ marginTop: 8, fontSize: 12, color: '#ef4444', display: 'flex', alignItems: 'center', gap: 6 }}>
                        <AlertTriangle size={14} />
                        <strong>OVERLOAD DETECTED:</strong> Combined payload exceeds vehicle capacity by {(tourTotalWeight - vehicleCapacity).toFixed(1)} kg. Select a higher capacity vehicle or remove packages.
                      </div>
                    )}
                  </div>
                )}

                {assignError && (
                  <div className="alert alert-error mt-4" style={{ marginBottom: 12 }}>
                    {assignError}
                  </div>
                )}

                {assignSuccess && (
                  <div className="alert alert-success mt-4" style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
                    <CheckCircle2 size={18} />
                    <span>{assignSuccess}</span>
                  </div>
                )}

                <div style={{ marginTop: 18 }}>
                  <Button
                    variant="primary"
                    icon={Send}
                    onClick={handleBatchAssign}
                    loading={assigningBatch}
                    disabled={assigningBatch || !selectedDriverId || !selectedVehicleId || isOverloaded}
                    id="dispatch-assign-btn"
                  >
                    {assigningBatch ? 'Dispatching Manifest...' : 'Dispatch & Assign Multi-Stop Tour'}
                  </Button>
                </div>
              </div>
            </Card>
          )}
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: SINGLE DELIVERY / POINT-TO-POINT (DIJKSTRA SOLVER) */}
      {/* ========================================================================= */}
      {activeTab === 'POINT_TO_POINT' && (
        <div>
          <Card
            title="Dijkstra Route Computation"
            subtitle="Point-to-point shortest path evaluation across graph edges via Dijkstra's Algorithm"
            className="mb-6"
          >
            {locations.length < 2 ? (
              <EmptyState
                icon={Route}
                title="Insufficient network nodes"
                description="At least 2 connected locations are required to compute Dijkstra paths. Configure locations in the Locations module."
              />
            ) : (
              <div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 16 }}>
                  <Select
                    label="Source / Origin Node"
                    id="route-source"
                    value={sourceId}
                    onChange={(e) => handleSourceChange(e.target.value)}
                    required={!deliveryId}
                    disabled={!!deliveryId}
                  >
                    <option value="">Select origin location</option>
                    {routableLocations.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.displayLabel || `${l.cleanName || l.name} ${l.locationType ? `(${l.locationType})` : ''}`}
                      </option>
                    ))}
                  </Select>

                  <Select
                    label="Destination Node"
                    id="route-destination"
                    value={destId}
                    onChange={(e) => handleDestChange(e.target.value)}
                    required={!deliveryId}
                    disabled={!!deliveryId}
                  >
                    <option value="">Select destination location</option>
                    {routableLocations.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.displayLabel || `${l.cleanName || l.name} ${l.locationType ? `(${l.locationType})` : ''}`}
                      </option>
                    ))}
                  </Select>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 16, marginTop: 16 }}>
                  <Select
                    label="Optimization Criterion"
                    id="route-criterion"
                    value={singleCriterion}
                    onChange={(e) => setSingleCriterion(e.target.value)}
                    required
                  >
                    <option value="SHORTEST_DISTANCE">Shortest Distance (km)</option>
                    <option value="FASTEST_ROUTE">Fastest Route (min)</option>
                  </Select>
                </div>

                <div style={{ marginTop: 12 }}>
                  <Select
                    label="Target Single Delivery (Optional)"
                    id="route-delivery"
                    value={deliveryId}
                    onChange={(e) => {
                      setDeliveryId(e.target.value);
                      setSingleError('');
                    }}
                    helperText="Select a legacy single delivery to auto-link and persist this Dijkstra route."
                  >
                    <option value="">None (Standalone Point-to-Point Graph Query)</option>
                    {deliveries
                      .filter((d) => d.status === 'ASSIGNED' || d.status === 'IN_PROGRESS' || d.status === 'CREATED')
                      .map((d) => (
                        <option key={d.id} value={d.id}>
                          #{d.id} — {d.trackingNumber || `Pkg #${d.packageId}`} ({d.status})
                        </option>
                      ))}
                  </Select>
                </div>

                {singleError && (
                  <div className="alert alert-error mt-4" style={{ marginBottom: 16 }}>
                    {singleError}
                  </div>
                )}

                <div style={{ marginTop: 16, display: 'flex', alignItems: 'center', gap: 12 }}>
                  <Button
                    variant="primary"
                    icon={Navigation}
                    onClick={handleSingleOptimize}
                    disabled={singleLoading || (!deliveryId && (!sourceId || !destId || String(sourceId) === String(destId)))}
                    loading={singleLoading}
                    id="optimize-single-btn"
                  >
                    {singleLoading ? 'Computing Dijkstra Path...' : 'Find Optimal Route'}
                  </Button>
                </div>

                {/* Real-time Multi-mode Road Navigation Alternatives */}
                <RouteAlternativesCard
                  sourceId={sourceId}
                  destId={destId}
                  onSelectAlternative={(alt) => setSelectedAlternative(alt)}
                />
              </div>
            )}
          </Card>

          {/* Single Result Card */}
          {singleResult && (
            <Card
              title="Optimized Dijkstra Route Result"
              subtitle={`Algorithm: ${singleResult.algorithm || 'DIJKSTRA'} • Path verified by network topology`}
              actions={<StatusBadge status={singleResult.pathExists ? 'COMPLETED' : 'FAILED'} />}
              className="mb-6"
            >
              {singleResult.pathExists ? (
                <div>
                  {singleResult.explanation && (
                    <div style={{ marginBottom: 16, padding: '10px 14px', backgroundColor: 'var(--theme-surface)', borderLeft: '3px solid var(--color-primary)', borderRadius: '0 var(--radius-card) var(--radius-card) 0', fontSize: 13, color: 'var(--theme-text)' }}>
                      <strong>{singleResult.criterion === 'FASTEST_ROUTE' ? 'Fastest Route' : 'Shortest Distance'}:</strong> {singleResult.explanation}
                    </div>
                  )}

                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                      gap: 12,
                      marginBottom: 20,
                      padding: 16,
                      borderRadius: 'var(--radius-card)',
                      backgroundColor: 'var(--theme-elevated)',
                      border: '1px solid var(--theme-border)',
                    }}
                  >
                    <div>
                      <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                        Total Distance
                      </div>
                      <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                        {singleResult.totalDistance != null ? `${singleResult.totalDistance.toFixed(1)} km` : '—'}
                      </div>
                    </div>

                    <div>
                      <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                        Estimated Time
                      </div>
                      <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                        {singleResult.totalEstimatedTime != null ? `${singleResult.totalEstimatedTime.toFixed(1)} min` : '—'}
                      </div>
                    </div>

                    <div>
                      <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                        Total Nodes / Stops
                      </div>
                      <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                        {singleResult.orderedLocationNames?.length || 0}
                      </div>
                    </div>

                    <div>
                      <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                        Production Engine
                      </div>
                      <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--color-primary)', marginTop: 2 }}>
                        {singleResult.algorithm || 'DIJKSTRA'}
                      </div>
                    </div>

                    {singleResult.routeId && (
                      <div>
                        <div style={{ fontSize: 10, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                          Persisted Route ID
                        </div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--theme-text)', marginTop: 2 }}>
                          #{singleResult.routeId} (v{singleResult.versionNumber || 1})
                        </div>
                      </div>
                    )}
                  </div>

                  {/* View Mode Toggle Bar (Geographic Map vs Topology Graph) */}
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12, flexWrap: 'wrap', gap: 8 }}>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <Button
                        variant={p2pViewMode === 'MAP' ? 'primary' : 'secondary'}
                        size="sm"
                        icon={Globe}
                        onClick={() => setP2pViewMode('MAP')}
                      >
                        Geographic Street Map
                      </Button>
                      <Button
                        variant={p2pViewMode === 'GRAPH' ? 'primary' : 'secondary'}
                        size="sm"
                        icon={Activity}
                        onClick={() => setP2pViewMode('GRAPH')}
                      >
                        Futuristic Topology Graph
                      </Button>
                    </div>

                    {selectedAlternative && (
                      <span style={{ fontSize: 12, color: 'var(--color-primary)', fontWeight: 600 }}>
                        Active Road Alternative: {selectedAlternative.label} ({selectedAlternative.distanceKm?.toFixed(1)} km)
                      </span>
                    )}
                  </div>

                  <div style={{ marginBottom: 20 }}>
                    {p2pViewMode === 'MAP' ? (
                      <MapComponent 
                        locations={locations} 
                        edges={edges}
                        routeEdges={singleResult.orderedEdges || []}
                        routePathNames={singleResult.orderedLocationNames} 
                        customPolylinePositions={selectedAlternative?.geometryCoordinates?.map(c => [c[1], c[0]]) || null}
                        height="420px" 
                      />
                    ) : (
                      <NetworkGraphVisualizer
                        locations={locations}
                        edges={edges}
                        activePathNames={singleResult.orderedLocationNames || []}
                        sourceId={sourceId}
                        destId={destId}
                        onSelectNode={(node) => {
                          if (!sourceId) setSourceId(String(node.id));
                          else if (!destId && String(node.id) !== sourceId) setDestId(String(node.id));
                          else setSourceId(String(node.id));
                        }}
                        height="420px"
                      />
                    )}
                  </div>

                  <div style={{ marginBottom: 12 }}>
                    <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                      Transit Path Nodes ({singleResult.orderedLocationNames?.length} Vertices)
                    </span>
                  </div>

                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'stretch',
                      gap: 8,
                      overflowX: 'auto',
                      paddingBottom: 8,
                    }}
                  >
                    {singleResult.orderedLocationNames?.map((name, idx) => {
                      const isFirst = idx === 0;
                      const isLast = idx === singleResult.orderedLocationNames.length - 1;

                      return (
                        <div key={idx} style={{ display: 'flex', alignItems: 'stretch' }}>
                          <div
                            style={{
                              backgroundColor: 'var(--theme-elevated)',
                              border: '1px solid var(--theme-border)',
                              borderLeft: isFirst
                                ? '3px solid var(--color-primary)'
                                : isLast
                                ? '3px solid var(--color-danger)'
                                : '3px solid var(--theme-border)',
                              borderRadius: 'var(--radius-input)',
                              padding: '12px 14px',
                              minWidth: 160,
                              display: 'flex',
                              flexDirection: 'column',
                              justifyContent: 'space-between',
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                              <span style={{ fontSize: 9, fontWeight: 700, color: 'var(--theme-muted)', textTransform: 'uppercase' }}>
                                {isFirst ? 'ORIGIN' : isLast ? 'DESTINATION' : `STOP #${idx + 1}`}
                              </span>
                              {isFirst ? (
                                <MapPin size={14} color="var(--color-primary)" />
                              ) : isLast ? (
                                <Flag size={14} color="var(--color-danger)" />
                              ) : (
                                <Navigation size={12} color="var(--theme-muted)" />
                              )}
                            </div>
                            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--theme-text)' }}>
                              {name}
                            </div>
                            <div style={{ fontSize: 10, color: 'var(--theme-muted)', marginTop: 4 }}>
                              Step {idx + 1} of {singleResult.orderedLocationNames.length}
                            </div>
                          </div>

                          {!isLast && (
                            <div
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                padding: '0 6px',
                                color: 'var(--theme-muted)',
                              }}
                            >
                              <ArrowRight size={18} />
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              ) : (
                <EmptyState
                  icon={Route}
                  title="No traversable path exists"
                  description="No road segments connect the selected origin and destination in the graph network."
                />
              )}
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
