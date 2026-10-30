import React, { useState, useEffect } from 'react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { 
  Send, Plus, UserPlus, ArrowRight, Ban, CheckCircle2, 
  Truck, Package, MapPin, Navigation, Clock, AlertTriangle, 
  Layers, RefreshCw, Eye, ShieldCheck, Flag, Sparkles, Building2, Sliders
} from 'lucide-react';
import PageHeader from '../components/ui/PageHeader';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Select from '../components/ui/Select';
import { StatusBadge } from '../components/ui/Badge';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '../components/ui/Table';
import Modal from '../components/ui/Modal';
import EmptyState from '../components/ui/EmptyState';
import LoadingState from '../components/ui/LoadingState';
import ErrorState, { parseApiError } from '../components/ui/ErrorState';
import CustomerShipmentModal from '../components/ui/CustomerShipmentModal';
import ShipmentTimelineView from '../components/ui/ShipmentTimelineView';
import MapComponent from '../components/ui/Map';
import BatchDispatchBoard from '../components/ui/BatchDispatchBoard';
import DriverManifestBoard from '../components/ui/DriverManifestBoard';

export default function Deliveries() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const isDriver = user?.role === 'DRIVER';
  const isUser = user?.role === 'USER';

  // Shared Data
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState('');
  const [deliveries, setDeliveries] = useState([]);
  const [shipments, setShipments] = useState([]);
  const [drivers, setDrivers] = useState([]);
  const [vehicles, setVehicles] = useState([]);
  const [locations, setLocations] = useState([]);
  const [packages, setPackages] = useState([]);
  const [driverLegs, setDriverLegs] = useState([]);

  // Admin Tab selection: 'AWAITING_PICKUP', 'BATCH_PICKUP', 'IN_TRANSIT_HUB', 'AWAITING_FINAL_MILE', 'BATCH_DELIVERY', 'DELIVERED', 'LEGACY'
  const [adminTab, setAdminTab] = useState('AWAITING_PICKUP');

  // Driver View Mode: 'MANIFEST' (Multi-stop batch manifest) | 'ALL_LEGS' (Individual task cards)
  const [driverViewMode, setDriverViewMode] = useState('MANIFEST');

  // Modals
  const [showCustomerModal, setShowCustomerModal] = useState(false);
  const [inspectShipment, setInspectShipment] = useState(null);
  const [showAssignLegModal, setShowAssignLegModal] = useState(false);
  const [targetLeg, setTargetLeg] = useState(null);
  const [targetShipment, setTargetShipment] = useState(null);
  const [assignForm, setAssignForm] = useState({ driverId: '', vehicleId: '', notes: '' });
  const [assignError, setAssignError] = useState('');
  const [assigning, setAssigning] = useState(false);

  // Hub Override Modal
  const [showOverrideModal, setShowOverrideModal] = useState(false);
  const [overrideShipmentId, setOverrideShipmentId] = useState(null);
  const [overrideHubId, setOverrideHubId] = useState('');
  const [overrideReason, setOverrideReason] = useState('');
  const [overrideError, setOverrideError] = useState('');
  const [overriding, setOverriding] = useState(false);

  // Legacy Create/Assign state
  const [showLegacyCreateModal, setShowLegacyCreateModal] = useState(false);
  const [showLegacyAssignModal, setShowLegacyAssignModal] = useState(false);
  const [legacyAssignDeliveryId, setLegacyAssignDeliveryId] = useState(null);
  const [legacyCreateForm, setLegacyCreateForm] = useState({ packageId: '', sourceLocationId: '', notes: '' });
  const [legacyAssignForm, setLegacyAssignForm] = useState({ driverId: '', vehicleId: '', notes: '' });
  const [legacyError, setLegacyError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [confirmingDeliveryId, setConfirmingDeliveryId] = useState(null);

  const handleConfirmDeliveryReturn = async (deliveryId) => {
    setConfirmingDeliveryId(deliveryId);
    try {
      await api.post(`/deliveries/${deliveryId}/confirm-return`);
      setSuccessMessage(`Delivery #${deliveryId} return to hub confirmed! Driver and vehicle released to AVAILABLE.`);
      fetchData();
    } catch (err) {
      alert(parseApiError(err, 'Failed to confirm return to hub'));
    } finally {
      setConfirmingDeliveryId(null);
    }
  };

  const fetchData = async () => {
    setLoading(true);
    setFetchError('');

    try {
      if (isAdmin) {
        const [dRes, sRes, drRes, vRes, lRes, pRes] = await Promise.all([
          api.get('/deliveries'),
          api.get('/shipments'),
          api.get('/drivers', { params: { size: 200 } }),
          api.get('/vehicles', { params: { size: 200 } }),
          api.get('/locations', { params: { size: 200 } }),
          api.get('/packages', { params: { size: 200 } }).catch(() => ({ data: { content: [] } })),
        ]);

        setDeliveries(Array.isArray(dRes.data) ? dRes.data : dRes.data.content || []);
        setShipments(Array.isArray(sRes.data) ? sRes.data : []);
        setDrivers(drRes.data.content || []);
        setVehicles(vRes.data.content || []);
        setLocations(lRes.data.content || []);
        setPackages(pRes.data.content || []);
      } else if (isDriver) {
        const [legsRes, dRes] = await Promise.all([
          api.get('/shipments/driver/assigned-legs'),
          api.get('/deliveries').catch(() => ({ data: [] })),
        ]);
        setDriverLegs(Array.isArray(legsRes.data) ? legsRes.data : []);
        setDeliveries(Array.isArray(dRes.data) ? dRes.data : dRes.data.content || []);
      } else {
        // Customer view
        const [myRes, dRes] = await Promise.all([
          api.get('/shipments/my'),
          api.get('/deliveries').catch(() => ({ data: [] })),
        ]);
        setShipments(Array.isArray(myRes.data) ? myRes.data : []);
        setDeliveries(Array.isArray(dRes.data) ? dRes.data : dRes.data.content || []);
      }
    } catch (err) {
      setFetchError(parseApiError(err, 'Failed to synchronize dispatch operations'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  // Filter Shipments by Dispatch Stage
  const awaitingPickupShipments = shipments.filter((s) => {
    const firstLeg = s.legs?.find((l) => l.legType === 'FIRST_MILE');
    return firstLeg && firstLeg.status === 'PENDING';
  });

  const inTransitHubShipments = shipments.filter((s) => {
    const firstLeg = s.legs?.find((l) => l.legType === 'FIRST_MILE');
    return firstLeg && (firstLeg.status === 'ASSIGNED' || firstLeg.status === 'IN_TRANSIT');
  });

  const awaitingFinalMileShipments = shipments.filter((s) => {
    const firstLeg = s.legs?.find((l) => l.legType === 'FIRST_MILE');
    const finalLeg = s.legs?.find((l) => l.legType === 'FINAL_MILE');
    return firstLeg?.status === 'COMPLETED' && finalLeg?.status === 'PENDING';
  });

  const activeAndDeliveredShipments = shipments.filter((s) => {
    const finalLeg = s.legs?.find((l) => l.legType === 'FINAL_MILE');
    return finalLeg && (finalLeg.status === 'ASSIGNED' || finalLeg.status === 'IN_TRANSIT' || finalLeg.status === 'COMPLETED');
  });

  // Open Leg Assignment Modal
  const openAssignLegModal = (shipment, leg) => {
    setTargetShipment(shipment);
    setTargetLeg(leg);
    setAssignForm({ driverId: '', vehicleId: '', notes: '' });
    setAssignError('');
    setShowAssignLegModal(true);
  };

  // Submit Leg Assignment
  const handleAssignLegSubmit = async (e) => {
    e.preventDefault();
    if (!assignForm.driverId || !assignForm.vehicleId) {
      setAssignError('Please select both an available driver and vehicle.');
      return;
    }

    setAssigning(true);
    setAssignError('');

    try {
      await api.post(`/shipments/legs/${targetLeg.id}/assign`, {
        driverId: Number(assignForm.driverId),
        vehicleId: Number(assignForm.vehicleId),
        notes: assignForm.notes?.trim() || null,
      });

      setShowAssignLegModal(false);
      fetchData();
    } catch (err) {
      setAssignError(parseApiError(err, 'Failed to assign personnel to delivery leg'));
    } finally {
      setAssigning(false);
    }
  };

  // Open Hub Override Modal
  const openOverrideHubModal = (shipment) => {
    setOverrideShipmentId(shipment.id || shipment.deliveryId);
    setOverrideHubId(shipment.hubId || shipment.selectedHubId ? String(shipment.hubId || shipment.selectedHubId) : '');
    setOverrideReason('');
    setOverrideError('');
    setShowOverrideModal(true);
  };

  // Submit Hub Override
  const handleOverrideSubmit = async (e) => {
    e.preventDefault();
    if (!overrideHubId || !overrideReason.trim()) {
      setOverrideError('Please select a destination hub and provide an auditable override reason.');
      return;
    }

    setOverriding(true);
    setOverrideError('');

    try {
      await api.put(`/shipments/${overrideShipmentId}/override-hub`, {
        newHubId: Number(overrideHubId),
        reason: overrideReason.trim(),
      });
      setShowOverrideModal(false);
      fetchData();
    } catch (err) {
      setOverrideError(parseApiError(err, 'Failed to override hub'));
    } finally {
      setOverriding(false);
    }
  };

  // Driver Action: Update Leg Status
  const handleDriverLegTransition = async (legId, nextStatus) => {
    try {
      await api.patch(`/shipments/legs/${legId}/status`, {
        status: nextStatus,
        reason: null,
      });
      fetchData();
    } catch (err) {
      alert(parseApiError(err, 'Failed to update leg progress'));
    }
  };

  // Helper for vehicle capacity calculation
  const selectedVehicleObj = vehicles.find((v) => String(v.id) === String(assignForm.vehicleId));
  const pkgWeight = targetShipment?.packageDetails?.weight ?? targetShipment?.packageWeight ?? 1.0;
  const vehicleCap = selectedVehicleObj ? (selectedVehicleObj.capacity ?? selectedVehicleObj.capacityKg ?? 50) : 50;
  const isOverweight = Boolean(
    selectedVehicleObj &&
    vehicleCap &&
    pkgWeight > vehicleCap
  );

  return (
    <div style={{ maxWidth: 1380, margin: '0 auto', paddingBottom: 60 }}>
      <PageHeader
        title={
          isUser
            ? 'My Parcel Deliveries'
            : isDriver
            ? 'Driver Delivery Console'
            : 'Central Dispatch & Multi-Leg Hub Network'
        }
        subtitle={
          isUser
            ? 'Track real-time shipment progress, Bangalore hub dispatch, and delivery milestones'
            : isDriver
            ? 'Sequential first-mile sender pickups, hub arrivals, and final-mile recipient deliveries'
            : 'Manage First-Mile (Sender → Hub) and Final-Mile (Hub → Recipient) dispatch across 20 Bengaluru Service Hubs'
        }
        action={
          isUser ? (
            <Button
              variant="primary"
              icon={Plus}
              onClick={() => setShowCustomerModal(true)}
              id="schedule-shipment-btn"
            >
              Send New Parcel
            </Button>
          ) : isAdmin ? (
            <div style={{ display: 'flex', gap: 10 }}>
              <Button
                variant="secondary"
                icon={RefreshCw}
                onClick={fetchData}
                loading={loading}
                size="sm"
              >
                Refresh
              </Button>
              <Button
                variant="primary"
                icon={Plus}
                onClick={() => setShowCustomerModal(true)}
                id="admin-create-shipment-btn"
              >
                New Shipment
              </Button>
            </div>
          ) : (
            <Button
              variant="secondary"
              icon={RefreshCw}
              onClick={fetchData}
              loading={loading}
              size="sm"
            >
              Refresh Tasks
            </Button>
          )
        }
      />

      {successMessage && (
        <div id="shipment-success-alert" className="alert alert-success mb-6" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>{successMessage}</span>
          <button 
            onClick={() => setSuccessMessage('')} 
            style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', fontWeight: 'bold' }}
          >
            ✕
          </button>
        </div>
      )}

      {fetchError && <ErrorState error={fetchError} onRetry={fetchData} className="mb-6" />}

      {/* ===================================================================== */}
      {/* 1. CUSTOMER / USER VIEW */}
      {/* ===================================================================== */}
      {isUser && (
        <div>
          {loading ? (
            <LoadingState message="Loading your shipments and tracking data..." />
          ) : shipments.length === 0 ? (
            <Card>
              <EmptyState
                icon={Package}
                title="No Shipments Yet"
                description="You haven't scheduled any parcel shipments yet. Click 'Send New Parcel' to drop a pin on the Bengaluru map and book a pickup."
                action={
                  <Button
                    variant="primary"
                    icon={Plus}
                    onClick={() => setShowCustomerModal(true)}
                  >
                    Schedule First Delivery
                  </Button>
                }
              />
            </Card>
          ) : (
            <div className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-800">
                  <div className="text-xs text-slate-400 uppercase font-mono">Total Shipments</div>
                  <div className="text-2xl font-bold text-white mt-1">{shipments.length}</div>
                </div>
                <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-800">
                  <div className="text-xs text-slate-400 uppercase font-mono">Active In Transit</div>
                  <div className="text-2xl font-bold text-cyan-400 mt-1">
                    {shipments.filter(s => s.currentStage !== 'DELIVERED').length}
                  </div>
                </div>
                <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-800">
                  <div className="text-xs text-slate-400 uppercase font-mono">Delivered</div>
                  <div className="text-2xl font-bold text-emerald-400 mt-1">
                    {shipments.filter(s => s.currentStage === 'DELIVERED').length}
                  </div>
                </div>
              </div>

              <Table>
                <TableHeader>
                  <tr>
                    <TableHead>Tracking #</TableHead>
                    <TableHead>Item Details</TableHead>
                    <TableHead>Pickup Location</TableHead>
                    <TableHead>Destination</TableHead>
                    <TableHead>Assigned Hub</TableHead>
                    <TableHead>Current Milestone</TableHead>
                    <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                  </tr>
                </TableHeader>
                <TableBody>
                  {shipments.map((s) => (
                    <TableRow key={s.id || s.deliveryId}>
                      <TableCell>
                        <span className="font-mono font-bold text-cyan-400">
                          {s.trackingNumber}
                        </span>
                      </TableCell>
                      <TableCell>
                        <div className="font-semibold text-white">{s.packageDetails?.itemName || s.productName || 'Parcel'}</div>
                        <div className="text-xs text-slate-400">
                          {(s.packageDetails?.weight ?? s.packageWeight ?? 1.0).toFixed(1)} kg • {s.packageDetails?.itemCategory || s.category || 'General'}
                          {(s.packageDetails?.fragile ?? s.fragile) && <span className="ml-1 text-amber-400 font-bold">[Fragile]</span>}
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="text-xs text-slate-300 max-w-[180px] truncate" title={s.packageDetails?.pickupLocation || s.senderAddress || s.sourceLocationName}>
                          {s.packageDetails?.pickupLocation || s.senderAddress || s.sourceLocationName || '—'}
                        </div>
                        <div className="text-[10px] text-slate-400">{s.packageDetails?.senderName || s.senderName}</div>
                      </TableCell>
                      <TableCell>
                        <div className="text-xs text-slate-300 max-w-[180px] truncate" title={s.packageDetails?.deliveryLocation || s.recipientAddress || s.destinationLocationName}>
                          {s.packageDetails?.deliveryLocation || s.recipientAddress || s.destinationLocationName || '—'}
                        </div>
                        <div className="text-[10px] text-slate-400">{s.packageDetails?.receiverName || s.recipientName}</div>
                      </TableCell>
                      <TableCell>
                        <span className="text-xs font-medium text-indigo-300">
                          {s.hubName || s.selectedHubName || 'Evaluating...'}
                        </span>
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={s.currentStage || s.status} />
                      </TableCell>
                      <TableCell style={{ textAlign: 'right' }}>
                        <Button
                          variant="secondary"
                          size="sm"
                          icon={Eye}
                          onClick={() => setInspectShipment(s)}
                        >
                          View Timeline
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </div>
      )}

      {/* ===================================================================== */}
      {/* 2. DRIVER VIEW */}
      {/* ===================================================================== */}
      {isDriver && (
        <div className="space-y-6">
          {/* Driver Mode Toggle */}
          <div className="flex items-center gap-2 p-1.5 rounded-xl bg-themed-surface border border-themed w-fit">
            <button
              onClick={() => setDriverViewMode('MANIFEST')}
              className={`px-4 py-2 rounded-lg text-xs font-bold transition flex items-center gap-2 ${
                driverViewMode === 'MANIFEST'
                  ? 'bg-emerald-500 text-slate-950 shadow-md shadow-emerald-500/20'
                  : 'text-themed-secondary hover:text-themed-primary'
              }`}
              id="driver-manifest-tab-btn"
            >
              <Navigation className="w-3.5 h-3.5" />
              Active Route Manifest
            </button>
            <button
              onClick={() => setDriverViewMode('ALL_LEGS')}
              className={`px-4 py-2 rounded-lg text-xs font-bold transition flex items-center gap-2 ${
                driverViewMode === 'ALL_LEGS'
                  ? 'bg-emerald-500 text-slate-950 shadow-md shadow-emerald-500/20'
                  : 'text-themed-secondary hover:text-themed-primary'
              }`}
              id="driver-all-legs-tab-btn"
            >
              <Truck className="w-3.5 h-3.5" />
              All Assigned Legs ({driverLegs.length})
            </button>
          </div>

          {driverViewMode === 'MANIFEST' ? (
            <DriverManifestBoard onStatusUpdated={fetchData} />
          ) : loading ? (
            <LoadingState message="Loading your assigned delivery tasks..." />
          ) : driverLegs.length === 0 ? (
            <Card>
              <EmptyState
                icon={Truck}
                title="No Active Leg Tasks Assigned"
                description="You currently have no assigned first-mile pickups or final-mile deliveries. As dispatch assigns you tasks, they will appear here in real time."
              />
            </Card>
          ) : (
            <div className="space-y-6">
              {driverLegs.map((leg) => {
                const isFirstMile = leg.legType === 'FIRST_MILE';
                const canPickup = isFirstMile && leg.status === 'ASSIGNED';
                const canArriveHub = isFirstMile && leg.status === 'IN_TRANSIT';
                const canDepartHub = !isFirstMile && leg.status === 'ASSIGNED';
                const canDeliver = !isFirstMile && leg.status === 'IN_TRANSIT';

                return (
                  <Card
                    key={leg.id}
                    title={
                      <div className="flex items-center gap-2">
                        <span className={`px-2.5 py-1 rounded text-xs font-bold uppercase tracking-wider ${
                          isFirstMile ? 'bg-cyan-950 text-cyan-300 border border-cyan-800' : 'bg-emerald-950 text-emerald-300 border border-emerald-800'
                        }`}>
                          {isFirstMile ? 'Leg 1: First-Mile Pickup' : 'Leg 2: Final-Mile Delivery'}
                        </span>
                        <span className="font-mono text-sm text-slate-300">
                          Task #{leg.id} • {leg.trackingNumber}
                        </span>
                      </div>
                    }
                    actions={<StatusBadge status={leg.status} />}
                  >
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                      {/* Left Details */}
                      <div className="space-y-3">
                        <div className="p-3.5 rounded-xl bg-slate-900 border border-slate-800 space-y-2">
                          <div className="text-xs font-bold text-slate-400 uppercase font-mono">
                            {isFirstMile ? 'Sender / Pickup Contact' : 'Pickup Hub Origin'}
                          </div>
                          <div className="text-sm font-semibold text-white">
                            {isFirstMile ? (leg.senderName || leg.originLocationName || leg.sourceLocationName) : (leg.originLocationName || leg.sourceLocationName)}
                          </div>
                          {isFirstMile && leg.senderPhone && (
                            <div className="text-xs text-cyan-400 font-mono">📞 {leg.senderPhone}</div>
                          )}
                          <div className="text-xs text-slate-300">{leg.originAddress || leg.sourceAddress}</div>
                        </div>

                        <div className="p-3.5 rounded-xl bg-slate-900 border border-slate-800 space-y-2">
                          <div className="text-xs font-bold text-slate-400 uppercase font-mono">
                            {isFirstMile ? 'Destination Central Hub' : 'Recipient Delivery Address'}
                          </div>
                          <div className="text-sm font-semibold text-white">
                            {isFirstMile ? leg.destinationLocationName : (leg.recipientName || leg.destinationLocationName)}
                          </div>
                          {!isFirstMile && leg.recipientPhone && (
                            <div className="text-xs text-emerald-400 font-mono">📞 {leg.recipientPhone}</div>
                          )}
                          <div className="text-xs text-slate-300">{leg.destinationAddress}</div>
                        </div>

                        <div className="flex items-center justify-between p-3 rounded-lg bg-slate-950 border border-slate-800 text-xs text-slate-400 font-mono">
                          <span>Vehicle: <strong className="text-white">{leg.vehicleRegistrationNumber || 'Assigned'}</strong></span>
                          <span>Weight: <strong className="text-cyan-300">{(leg.packageWeight ?? 1.0).toFixed(1)} kg</strong></span>
                          {leg.fragile && <span className="text-amber-400 font-bold">FRAGILE</span>}
                        </div>

                        {/* Driver Action Buttons */}
                        <div className="pt-2 flex flex-wrap gap-3">
                          {canPickup && (
                            <Button
                              variant="primary"
                              icon={CheckCircle2}
                              onClick={() => handleDriverLegTransition(leg.id, 'IN_TRANSIT')}
                            >
                              Confirm Pickup from Sender
                            </Button>
                          )}

                          {canArriveHub && (
                            <Button
                              variant="primary"
                              icon={Building2}
                              onClick={() => handleDriverLegTransition(leg.id, 'COMPLETED')}
                            >
                              Confirm Central Hub Arrival
                            </Button>
                          )}

                          {canDepartHub && (
                            <Button
                              variant="primary"
                              icon={Truck}
                              onClick={() => handleDriverLegTransition(leg.id, 'IN_TRANSIT')}
                            >
                              Depart Hub (Out for Delivery)
                            </Button>
                          )}

                          {canDeliver && (
                            <Button
                              variant="primary"
                              icon={CheckCircle2}
                              onClick={() => handleDriverLegTransition(leg.id, 'COMPLETED')}
                            >
                              Confirm Delivery to Recipient
                            </Button>
                          )}

                          <Button
                            variant="secondary"
                            size="sm"
                            icon={Eye}
                            onClick={() => {
                              // Find shipment details or fetch
                              api.get(`/shipments/track/${leg.trackingNumber}`).then((res) => {
                                setInspectShipment(res.data);
                              });
                            }}
                          >
                            Timeline & Route
                          </Button>
                        </div>
                      </div>

                      {/* Right Mini Map Preview */}
                      <div className="rounded-xl overflow-hidden border border-slate-800 min-h-[260px]">
                        {(leg.originLatitude || leg.sourceLatitude) && leg.destinationLatitude ? (
                          <MapComponent
                            locations={[
                              { id: 1, name: leg.originLocationName || leg.sourceLocationName || 'Origin', latitude: leg.originLatitude || leg.sourceLatitude, longitude: leg.originLongitude || leg.sourceLongitude, locationType: isFirstMile ? 'CUSTOMER' : 'HUB' },
                              { id: 2, name: leg.destinationLocationName || 'Destination', latitude: leg.destinationLatitude, longitude: leg.destinationLongitude, locationType: isFirstMile ? 'HUB' : 'CUSTOMER' },
                            ]}
                            edges={[]}
                            customPolylinePositions={
                              leg.geometryCoordinates?.map((c) => [c[1], c[0]]) ||
                              (leg.routeGeometry ? (() => {
                                try {
                                  const parsed = typeof leg.routeGeometry === 'string' ? JSON.parse(leg.routeGeometry) : leg.routeGeometry;
                                  return parsed.coordinates?.map((c) => [c[1], c[0]]);
                                } catch { return null; }
                              })() : null) || [
                                [leg.originLatitude || leg.sourceLatitude, leg.originLongitude || leg.sourceLongitude],
                                [leg.destinationLatitude, leg.destinationLongitude],
                              ]
                            }
                            height="100%"
                          />
                        ) : (
                          <div className="h-full flex items-center justify-center bg-slate-950 text-xs text-slate-500">
                            Road map geometry available upon GPS lock
                          </div>
                        )}
                      </div>
                    </div>
                  </Card>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* ===================================================================== */}
      {/* 3. ADMIN DISPATCH DASHBOARD */}
      {/* ===================================================================== */}
      {isAdmin && (
        <div>
          {/* Top Stage Navigation Tabs */}
          <div className="flex flex-wrap items-center gap-2 mb-6 p-1.5 rounded-xl bg-slate-900 border border-slate-800">
            <button
              onClick={() => setAdminTab('AWAITING_PICKUP')}
              className={`px-4 py-2 rounded-lg text-xs font-bold transition-all flex items-center gap-2 ${
                adminTab === 'AWAITING_PICKUP'
                  ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/20'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <span>1. Single Pickup</span>
              <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-slate-950/60 font-mono">
                {awaitingPickupShipments.length}
              </span>
            </button>

            <button
              onClick={() => setAdminTab('BATCH_PICKUP')}
              className={`px-4 py-2 rounded-lg text-xs font-bold transition-all flex items-center gap-2 ${
                adminTab === 'BATCH_PICKUP'
                  ? 'bg-emerald-500 text-slate-950 shadow-md shadow-emerald-500/20'
                  : 'text-slate-400 hover:text-white'
              }`}
              id="admin-batch-pickup-tab-btn"
            >
              <Sliders size={14} />
              <span>Batch Pickup (10km)</span>
            </button>

            <button
              onClick={() => setAdminTab('IN_TRANSIT_HUB')}
              className={`px-4 py-2 rounded-lg text-xs font-bold transition-all flex items-center gap-2 ${
                adminTab === 'IN_TRANSIT_HUB'
                  ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/20'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <span>2. In Transit to Hub</span>
              <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-slate-950/60 font-mono">
                {inTransitHubShipments.length}
              </span>
            </button>

            <button
              onClick={() => setAdminTab('AWAITING_FINAL_MILE')}
              className={`px-4 py-2 rounded-lg text-xs font-bold transition-all flex items-center gap-2 ${
                adminTab === 'AWAITING_FINAL_MILE'
                  ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/20'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <span>3. Single Final-Mile</span>
              <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-slate-950/60 font-mono">
                {awaitingFinalMileShipments.length}
              </span>
            </button>

            <button
              onClick={() => setAdminTab('BATCH_DELIVERY')}
              className={`px-4 py-2 rounded-lg text-xs font-bold transition-all flex items-center gap-2 ${
                adminTab === 'BATCH_DELIVERY'
                  ? 'bg-emerald-500 text-slate-950 shadow-md shadow-emerald-500/20'
                  : 'text-slate-400 hover:text-white'
              }`}
              id="admin-batch-delivery-tab-btn"
            >
              <Sliders size={14} />
              <span>Batch Delivery (10km)</span>
            </button>

            <button
              onClick={() => setAdminTab('DELIVERED')}
              className={`px-4 py-2 rounded-lg text-xs font-bold transition-all flex items-center gap-2 ${
                adminTab === 'DELIVERED'
                  ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/20'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <span>4. Active Out / Delivered</span>
              <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-slate-950/60 font-mono">
                {activeAndDeliveredShipments.length}
              </span>
            </button>

            <button
              onClick={() => setAdminTab('LEGACY')}
              className={`px-4 py-2 rounded-lg text-xs font-bold transition-all flex items-center gap-2 ${
                adminTab === 'LEGACY'
                  ? 'bg-indigo-600 text-white'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <Layers size={14} />
              <span>Legacy Operations ({deliveries.length})</span>
            </button>
          </div>

          {/* TAB: BATCH PICKUP DISPATCH */}
          {adminTab === 'BATCH_PICKUP' && (
            <BatchDispatchBoard
              mode="PICKUP"
              hubs={locations}
              drivers={drivers}
              vehicles={vehicles}
              onDispatchSuccess={fetchData}
            />
          )}

          {/* TAB: BATCH DELIVERY DISPATCH */}
          {adminTab === 'BATCH_DELIVERY' && (
            <BatchDispatchBoard
              mode="DELIVERY"
              hubs={locations}
              drivers={drivers}
              vehicles={vehicles}
              onDispatchSuccess={fetchData}
            />
          )}

          {/* TAB 1: AWAITING PICKUP ASSIGNMENT */}
          {adminTab === 'AWAITING_PICKUP' && (
            <div>
              {awaitingPickupShipments.length === 0 ? (
                <Card>
                  <EmptyState
                    icon={CheckCircle2}
                    title="No Shipments Awaiting Pickup Assignment"
                    description="All customer shipment requests have assigned pickup partners or no new requests have been submitted."
                  />
                </Card>
              ) : (
                <Table>
                  <TableHeader>
                    <tr>
                      <TableHead>Tracking #</TableHead>
                      <TableHead>Item / Spec</TableHead>
                      <TableHead>Sender & Pickup Address</TableHead>
                      <TableHead>Nearest Hub & Selection Rationale</TableHead>
                      <TableHead>Destination</TableHead>
                      <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                    </tr>
                  </TableHeader>
                  <TableBody>
                    {awaitingPickupShipments.map((s) => {
                      const firstLeg = s.legs?.find((l) => l.legType === 'FIRST_MILE');
                      return (
                        <TableRow key={s.id || s.deliveryId}>
                          <TableCell>
                            <span className="font-mono font-bold text-cyan-400">
                              {s.trackingNumber}
                            </span>
                          </TableCell>
                          <TableCell>
                            <div className="font-semibold text-white">{s.packageDetails?.itemName || s.productName || 'Package'}</div>
                            <div className="text-xs text-slate-400">
                              {(s.packageDetails?.weight ?? s.packageWeight ?? 1.0).toFixed(1)} kg • {s.packageDetails?.itemCategory || s.category || 'General'}
                              {(s.packageDetails?.fragile ?? s.fragile) && <span className="ml-1 text-amber-400 font-bold">[Fragile]</span>}
                            </div>
                          </TableCell>
                          <TableCell>
                            <div className="text-xs text-slate-300 max-w-[200px] truncate" title={s.packageDetails?.pickupLocation || s.senderAddress || s.sourceLocationName}>
                              {s.packageDetails?.pickupLocation || s.senderAddress || s.sourceLocationName || '—'}
                            </div>
                            <div className="text-[10px] text-slate-400">{s.packageDetails?.senderName || s.senderName} ({s.packageDetails?.senderPhone || s.senderPhone})</div>
                          </TableCell>
                          <TableCell>
                            <div className="text-xs font-bold text-indigo-300">
                              {s.hubName || s.selectedHubName}
                            </div>
                            <div className="text-[11px] text-slate-400 mt-0.5">
                              {s.hubSelectionBasis || s.hubSelectionRationale}
                            </div>
                          </TableCell>
                          <TableCell>
                            <div className="text-xs text-slate-300 max-w-[180px] truncate" title={s.packageDetails?.deliveryLocation || s.recipientAddress || s.destinationLocationName}>
                              {s.packageDetails?.deliveryLocation || s.recipientAddress || s.destinationLocationName || '—'}
                            </div>
                            <div className="text-[10px] text-slate-400">{s.packageDetails?.receiverName || s.recipientName}</div>
                          </TableCell>
                          <TableCell style={{ textAlign: 'right' }}>
                            <div className="inline-flex gap-2">
                              <Button
                                variant="primary"
                                size="sm"
                                icon={UserPlus}
                                onClick={() => openAssignLegModal(s, firstLeg)}
                              >
                                Assign Pickup
                              </Button>
                              <Button
                                variant="secondary"
                                size="sm"
                                onClick={() => openOverrideHubModal(s)}
                              >
                                Override Hub
                              </Button>
                              <Button
                                variant="secondary"
                                size="sm"
                                icon={Eye}
                                onClick={() => setInspectShipment(s)}
                              >
                                Timeline
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              )}
            </div>
          )}

          {/* TAB 2: IN TRANSIT TO HUB */}
          {adminTab === 'IN_TRANSIT_HUB' && (
            <div>
              {inTransitHubShipments.length === 0 ? (
                <Card>
                  <EmptyState
                    icon={Building2}
                    title="No First-Mile Legs Currently in Transit"
                    description="No packages are currently traveling from senders to central hubs."
                  />
                </Card>
              ) : (
                <Table>
                  <TableHeader>
                    <tr>
                      <TableHead>Tracking #</TableHead>
                      <TableHead>Pickup Partner</TableHead>
                      <TableHead>Vehicle</TableHead>
                      <TableHead>Transit Stage</TableHead>
                      <TableHead>Target Hub</TableHead>
                      <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                    </tr>
                  </TableHeader>
                  <TableBody>
                    {inTransitHubShipments.map((s) => {
                      const firstLeg = s.legs?.find((l) => l.legType === 'FIRST_MILE');
                      return (
                        <TableRow key={s.id || s.deliveryId}>
                          <TableCell>
                            <span className="font-mono font-bold text-cyan-400">
                              {s.trackingNumber}
                            </span>
                          </TableCell>
                          <TableCell>
                            <div className="font-semibold text-white">{firstLeg?.driverName || 'Assigned'}</div>
                            <div className="text-xs text-slate-400">{firstLeg?.driverPhone}</div>
                          </TableCell>
                          <TableCell>
                            <span className="font-mono text-xs">{firstLeg?.vehicleRegistrationNumber || '—'}</span>
                          </TableCell>
                          <TableCell>
                            <StatusBadge status={firstLeg?.status} />
                          </TableCell>
                          <TableCell>
                            <span className="text-xs font-medium text-indigo-300">{s.hubName || s.selectedHubName}</span>
                          </TableCell>
                          <TableCell style={{ textAlign: 'right' }}>
                            <div className="inline-flex gap-2">
                              {firstLeg?.status === 'IN_TRANSIT' && (
                                <Button
                                  variant="primary"
                                  size="sm"
                                  icon={Building2}
                                  onClick={async () => {
                                    try {
                                      await api.patch(`/shipments/legs/${firstLeg.id}/status`, { status: 'COMPLETED' });
                                      fetchData();
                                    } catch (err) {
                                      alert(parseApiError(err, 'Failed to confirm hub intake'));
                                    }
                                  }}
                                >
                                  Confirm Hub Intake
                                </Button>
                              )}
                              <Button
                                variant="secondary"
                                size="sm"
                                icon={Eye}
                                onClick={() => setInspectShipment(s)}
                              >
                                Timeline
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              )}
            </div>
          )}

          {/* TAB 3: AWAITING FINAL-MILE ASSIGNMENT */}
          {adminTab === 'AWAITING_FINAL_MILE' && (
            <div>
              {awaitingFinalMileShipments.length === 0 ? (
                <Card>
                  <EmptyState
                    icon={Building2}
                    title="No Packages Awaiting Final-Mile Assignment"
                    description="When a first-mile driver delivers a parcel to the central hub, it will appear here ready for final-mile dispatch."
                  />
                </Card>
              ) : (
                <Table>
                  <TableHeader>
                    <tr>
                      <TableHead>Tracking #</TableHead>
                      <TableHead>Item Details</TableHead>
                      <TableHead>Current Location</TableHead>
                      <TableHead>Final Destination</TableHead>
                      <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                    </tr>
                  </TableHeader>
                  <TableBody>
                    {awaitingFinalMileShipments.map((s) => {
                      const finalLeg = s.legs?.find((l) => l.legType === 'FINAL_MILE');
                      return (
                        <TableRow key={s.id || s.deliveryId}>
                          <TableCell>
                            <span className="font-mono font-bold text-cyan-400">
                              {s.trackingNumber}
                            </span>
                          </TableCell>
                          <TableCell>
                            <div className="font-semibold text-white">{s.packageDetails?.itemName || s.productName || 'Package'}</div>
                            <div className="text-xs text-slate-400">{(s.packageDetails?.weight ?? s.packageWeight ?? 1.0).toFixed(1)} kg</div>
                          </TableCell>
                          <TableCell>
                            <span className="text-xs font-bold text-indigo-300">
                              Stored at {s.hubName || s.selectedHubName}
                            </span>
                          </TableCell>
                          <TableCell>
                            <div className="text-xs text-slate-300 max-w-[200px] truncate" title={s.packageDetails?.deliveryLocation || s.recipientAddress || s.destinationLocationName}>
                              {s.packageDetails?.deliveryLocation || s.recipientAddress || s.destinationLocationName || '—'}
                            </div>
                            <div className="text-[10px] text-slate-400">{s.packageDetails?.receiverName || s.recipientName} ({s.packageDetails?.receiverPhone || s.recipientPhone})</div>
                          </TableCell>
                          <TableCell style={{ textAlign: 'right' }}>
                            <div className="inline-flex gap-2">
                              <Button
                                variant="primary"
                                size="sm"
                                icon={UserPlus}
                                onClick={() => openAssignLegModal(s, finalLeg)}
                              >
                                Assign Final-Mile
                              </Button>
                              <Button
                                variant="secondary"
                                size="sm"
                                icon={Eye}
                                onClick={() => setInspectShipment(s)}
                              >
                                Timeline
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              )}
            </div>
          )}

          {/* TAB 4: OUT FOR DELIVERY & COMPLETED */}
          {adminTab === 'DELIVERED' && (
            <div>
              {activeAndDeliveredShipments.length === 0 ? (
                <Card>
                  <EmptyState
                    icon={Truck}
                    title="No Active Final-Mile or Completed Deliveries"
                    description="Dispatched final-mile deliveries and completed drop-offs will appear here."
                  />
                </Card>
              ) : (
                <Table>
                  <TableHeader>
                    <tr>
                      <TableHead>Tracking #</TableHead>
                      <TableHead>Final-Mile Partner</TableHead>
                      <TableHead>Recipient</TableHead>
                      <TableHead>Delivery Status</TableHead>
                      <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                    </tr>
                  </TableHeader>
                  <TableBody>
                    {activeAndDeliveredShipments.map((s) => {
                      const finalLeg = s.legs?.find((l) => l.legType === 'FINAL_MILE');
                      return (
                        <TableRow key={s.id || s.deliveryId}>
                          <TableCell>
                            <span className="font-mono font-bold text-cyan-400">
                              {s.trackingNumber}
                            </span>
                          </TableCell>
                          <TableCell>
                            <div className="font-semibold text-white">{finalLeg?.driverName || 'Assigned'}</div>
                            <div className="text-xs font-mono text-slate-400">{finalLeg?.vehicleRegistrationNumber || '—'}</div>
                          </TableCell>
                          <TableCell>
                            <div className="text-xs text-slate-300">{s.packageDetails?.receiverName || s.recipientName}</div>
                            <div className="text-[10px] text-slate-400 truncate max-w-[200px]">{s.packageDetails?.deliveryLocation || s.recipientAddress || s.destinationLocationName}</div>
                          </TableCell>
                          <TableCell>
                            <StatusBadge status={s.currentStage || finalLeg?.status} />
                          </TableCell>
                          <TableCell style={{ textAlign: 'right' }}>
                            <Button
                              variant="secondary"
                              size="sm"
                              icon={Eye}
                              onClick={() => setInspectShipment(s)}
                            >
                              Timeline
                            </Button>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              )}
            </div>
          )}

          {/* TAB 5: LEGACY DELIVERY OPERATIONS (PRESERVED FROM SCREENSHOT 3) */}
          {adminTab === 'LEGACY' && (
            <div>
              <Table>
                <TableHeader>
                  <tr>
                    <TableHead>Delivery ID</TableHead>
                    <TableHead>Package Details</TableHead>
                    <TableHead>Pickup Location</TableHead>
                    <TableHead>Delivery Location</TableHead>
                    <TableHead>Assigned Driver</TableHead>
                    <TableHead>Assigned Vehicle</TableHead>
                    <TableHead>Lifecycle Status</TableHead>
                    <TableHead>Updated</TableHead>
                    <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                  </tr>
                </TableHeader>
                <TableBody>
                  {deliveries.map((d) => (
                    <TableRow key={d.id}>
                      <TableCell style={{ fontWeight: 700 }}>#{d.id}</TableCell>
                      <TableCell>
                        <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>
                          {d.trackingNumber || `PKG-${d.packageId}`}
                        </span>
                      </TableCell>
                      <TableCell>{d.sourceLocationName || '—'}</TableCell>
                      <TableCell>{d.destinationLocationName || '—'}</TableCell>
                      <TableCell>{d.driverName || <span style={{ color: 'var(--theme-muted)' }}>Unassigned</span>}</TableCell>
                      <TableCell>
                        {d.vehicleRegistrationNumber ? (
                          <span style={{ fontFamily: 'monospace' }}>{d.vehicleRegistrationNumber}</span>
                        ) : (
                          <span style={{ color: 'var(--theme-muted)' }}>Unassigned</span>
                        )}
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={d.status} />
                      </TableCell>
                      <TableCell style={{ fontSize: 11, color: 'var(--theme-muted)' }}>
                        {d.updatedAt ? new Date(d.updatedAt).toLocaleString() : '—'}
                      </TableCell>
                      <TableCell style={{ textAlign: 'right' }}>
                        {(d.canConfirmReturn || d.routeStatus === 'RETURNING_TO_HUB') && (
                          <Button
                            variant="primary"
                            size="sm"
                            icon={CheckCircle2}
                            onClick={() => handleConfirmDeliveryReturn(d.id)}
                            loading={confirmingDeliveryId === d.id}
                            style={{ backgroundColor: '#6366f1', borderColor: '#6366f1' }}
                            id={`confirm-return-del-${d.id}`}
                          >
                            Confirm Return
                          </Button>
                        )}
                        {d.returnConfirmedAt && (
                          <span style={{ color: '#10b981', fontSize: 11, fontWeight: 600 }}>
                            Returned ✓
                          </span>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </div>
      )}

      {/* ===================================================================== */}
      {/* MODALS */}
      {/* ===================================================================== */}

      {/* 1. Customer Shipment Creation Modal */}
      <CustomerShipmentModal
        isOpen={showCustomerModal}
        onClose={() => setShowCustomerModal(false)}
        onCreated={(created) => {
          setSuccessMessage(`Shipment ${created?.trackingNumber || ''} created successfully! Automatically scheduled with nearest hub.`);
          fetchData();
        }}
      />

      {/* 2. Full 6-Stage Timeline Modal (Screenshot 4 Inspection) */}
      <Modal
        isOpen={!!inspectShipment}
        onClose={() => setInspectShipment(null)}
        title={
          inspectShipment
            ? `Shipment Timeline • ${inspectShipment.trackingNumber}`
            : 'Shipment Details'
        }
        maxWidth="1050px"
      >
        {inspectShipment && (
          <ShipmentTimelineView
            delivery={inspectShipment}
            shipmentData={inspectShipment}
            onRefresh={async () => {
              const res = await api.get(`/shipments/track/${inspectShipment.trackingNumber}`);
              setInspectShipment(res.data);
            }}
          />
        )}
      </Modal>

      {/* 3. Assign Driver & Vehicle to Leg Modal */}
      <Modal
        isOpen={showAssignLegModal}
        onClose={() => setShowAssignLegModal(false)}
        title={`Assign Personnel: ${targetLeg?.legType === 'FIRST_MILE' ? 'First-Mile Pickup' : 'Final-Mile Delivery'}`}
        footer={
          <div className="modal-footer">
            <Button variant="secondary" onClick={() => setShowAssignLegModal(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={handleAssignLegSubmit}
              loading={assigning}
              disabled={assigning || !assignForm.driverId || !assignForm.vehicleId || isOverweight}
            >
              Confirm Assignment
            </Button>
          </div>
        }
      >
        <form onSubmit={handleAssignLegSubmit} className="space-y-4">
          {assignError && <div className="alert alert-error">{assignError}</div>}

          <div className="p-3 rounded-lg bg-slate-900 border border-slate-800 text-xs text-slate-300">
            <div>Tracking: <strong className="text-cyan-400 font-mono">{targetShipment?.trackingNumber}</strong></div>
            <div>Item: <strong className="text-white">{targetShipment?.packageDetails?.itemName || targetShipment?.productName || 'Package'}</strong> ({pkgWeight.toFixed(1)} kg)</div>
            {(targetShipment?.packageDetails?.fragile ?? targetShipment?.fragile) && <div className="text-amber-400 font-bold mt-1">⚠️ SPECIAL HANDLING: FRAGILE ITEM</div>}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Select
              label="Select Available Driver"
              id="leg-driver-select"
              value={assignForm.driverId}
              onChange={(e) => setAssignForm({ ...assignForm, driverId: e.target.value })}
              required
            >
              <option value="">Choose Driver</option>
              {drivers
                .filter((d) => d.status === 'AVAILABLE' && d.active)
                .map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name} {d.phone ? `(${d.phone})` : ''}
                  </option>
                ))}
            </Select>

            <Select
              label="Select Available Vehicle"
              id="leg-vehicle-select"
              value={assignForm.vehicleId}
              onChange={(e) => setAssignForm({ ...assignForm, vehicleId: e.target.value })}
              required
            >
              <option value="">Choose Vehicle</option>
              {vehicles
                .filter((v) => v.status === 'AVAILABLE' && v.active)
                .map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.registrationNumber} — {v.vehicleType} (Cap: {v.capacity ?? v.capacityKg ?? 50} kg)
                  </option>
                ))}
            </Select>
          </div>

          {isOverweight && (
            <div className="p-3 rounded-lg bg-red-950/60 border border-red-800 text-red-300 text-xs flex items-center gap-2">
              <AlertTriangle size={16} className="text-red-400 shrink-0" />
              <span>
                <strong>Capacity Exceeded:</strong> Package weight ({pkgWeight.toFixed(1)} kg) exceeds vehicle payload capacity ({selectedVehicleObj?.capacityKg} kg).
              </span>
            </div>
          )}

          <div className="form-group">
            <label htmlFor="leg-notes">Dispatch Notes (Optional)</label>
            <textarea
              id="leg-notes"
              className="form-input"
              rows={2}
              value={assignForm.notes}
              onChange={(e) => setAssignForm({ ...assignForm, notes: e.target.value })}
              placeholder="Instructions for driver, landmark hints, gate codes..."
            />
          </div>
        </form>
      </Modal>

      {/* 4. Admin Override Hub Modal */}
      <Modal
        isOpen={showOverrideModal}
        onClose={() => setShowOverrideModal(false)}
        title="Admin Override Service Hub"
        footer={
          <div className="modal-footer">
            <Button variant="secondary" onClick={() => setShowOverrideModal(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={handleOverrideSubmit}
              loading={overriding}
              disabled={overriding || !overrideHubId || !overrideReason.trim()}
            >
              Commit Hub Override
            </Button>
          </div>
        }
      >
        <form onSubmit={handleOverrideSubmit} className="space-y-4">
          {overrideError && <div className="alert alert-error">{overrideError}</div>}

          <Select
            label="Select Alternative Service Hub"
            id="override-hub-select"
            value={overrideHubId}
            onChange={(e) => setOverrideHubId(e.target.value)}
            required
            helperText="Select a reachable alternative Bengaluru central service hub."
          >
            <option value="">Select Hub</option>
            {locations
              .filter((l) => l.locationType === 'HUB' && l.active)
              .map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name} — {l.address || ''}
                </option>
              ))}
          </Select>

          <div className="form-group">
            <label htmlFor="override-reason">Auditable Override Reason *</label>
            <textarea
              id="override-reason"
              className="form-input"
              rows={3}
              value={overrideReason}
              onChange={(e) => setOverrideReason(e.target.value)}
              placeholder="Explain why the nearest hub was overridden (e.g., temporary road closure, capacity rebalancing, warehouse sorting rule)..."
              required
            />
          </div>
        </form>
      </Modal>
    </div>
  );
}
