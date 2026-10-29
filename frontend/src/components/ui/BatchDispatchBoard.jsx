import React, { useState, useEffect } from 'react';
import api from '../../api/axios';
import { 
  Truck, Package, MapPin, Navigation, Clock, CheckCircle2, 
  AlertCircle, ShieldCheck, ArrowRight, Layers, Sliders, RefreshCw, Box
} from 'lucide-react';
import Card from './Card';
import Button from './Button';
import Select from './Select';
import Input from './Input';
import MapComponent from './Map';
import { parseApiError } from './ErrorState';

export default function BatchDispatchBoard({
  mode = 'PICKUP', // 'PICKUP' | 'DELIVERY'
  hubs = [],
  drivers = [],
  vehicles = [],
  onDispatchSuccess,
}) {
  const isPickup = mode === 'PICKUP';
  const [configuredRadius, setConfiguredRadius] = useState(10.0);
  const [selectedHubId, setSelectedHubId] = useState('');
  const [selectedVehicleId, setSelectedVehicleId] = useState('');
  const [selectedDriverId, setSelectedDriverId] = useState('');
  const [customRadius, setCustomRadius] = useState(10.0);
  const [dispatchNotes, setDispatchNotes] = useState('');

  // Proposal State
  const [loadingProposal, setLoadingProposal] = useState(false);
  const [proposal, setProposal] = useState(null);
  const [proposalError, setProposalError] = useState('');
  const [selectedBatchIndex, setSelectedBatchIndex] = useState(0);

  // Dispatch State
  const [dispatching, setDispatching] = useState(false);
  const [dispatchError, setDispatchError] = useState('');
  const [dispatchSuccessMsg, setDispatchSuccessMsg] = useState('');

  // 1. Fetch Backend Batch Configuration
  useEffect(() => {
    api.get('/shipments/batch/config')
      .then((res) => {
        if (res.data) {
          const r = isPickup ? res.data.pickupRadiusKm : res.data.deliveryRadiusKm;
          if (r && r > 0) {
            setConfiguredRadius(r);
            setCustomRadius(r);
          }
        }
      })
      .catch(() => {
        // Fallback default
        setConfiguredRadius(10.0);
        setCustomRadius(10.0);
      });
  }, [isPickup]);

  // Set default hub if available
  useEffect(() => {
    if (hubs.length > 0 && !selectedHubId) {
      const activeHub = hubs.find(h => h.active && h.locationType === 'HUB') || hubs[0];
      if (activeHub) setSelectedHubId(String(activeHub.id));
    }
  }, [hubs, selectedHubId]);

  // 2. Propose Batch
  const handleProposeBatch = async () => {
    if (!selectedHubId) {
      setProposalError('Please select a Central Hub to generate batch routes.');
      return;
    }

    setLoadingProposal(true);
    setProposalError('');
    setDispatchError('');
    setDispatchSuccessMsg('');

    try {
      const endpoint = isPickup ? '/shipments/batch/propose-pickup' : '/shipments/batch/propose-delivery';
      const payload = {
        hubId: Number(selectedHubId),
        vehicleId: selectedVehicleId ? Number(selectedVehicleId) : null,
        radiusKm: customRadius > 0 ? Number(customRadius) : configuredRadius,
      };

      const res = await api.post(endpoint, payload);
      setProposal(res.data);
      setSelectedBatchIndex(0);
    } catch (err) {
      setProposalError(parseApiError(err, 'Failed to compute batch corridor routes'));
      setProposal(null);
    } finally {
      setLoadingProposal(false);
    }
  };

  // 3. Dispatch Selected Batch
  const handleDispatchBatch = async () => {
    if (!proposal || !proposal.proposedBatches || proposal.proposedBatches.length === 0) {
      setDispatchError('No proposed batch available to dispatch.');
      return;
    }

    const currentBatch = proposal.proposedBatches[selectedBatchIndex];
    if (!currentBatch || !currentBatch.legIds || currentBatch.legIds.length === 0) {
      setDispatchError('Selected batch has no assigned delivery legs.');
      return;
    }

    if (!selectedDriverId) {
      setDispatchError('Please assign an authorized driver.');
      return;
    }

    if (!selectedVehicleId) {
      setDispatchError('Please assign a suitable vehicle.');
      return;
    }

    setDispatching(true);
    setDispatchError('');
    setDispatchSuccessMsg('');

    try {
      const payload = {
        legIds: currentBatch.legIds,
        driverId: Number(selectedDriverId),
        vehicleId: Number(selectedVehicleId),
        notes: dispatchNotes.trim() || `Batch ${mode} Corridor (${currentBatch.legIds.length} stops)`,
      };

      const res = await api.post('/shipments/batch/dispatch', payload);
      setDispatchSuccessMsg(res.data.message || 'Batch dispatched successfully!');

      if (onDispatchSuccess) {
        onDispatchSuccess(res.data);
      }

      // Refresh proposal after 1s
      setTimeout(() => {
        handleProposeBatch();
      }, 1200);
    } catch (err) {
      setDispatchError(parseApiError(err, 'Failed to dispatch batch route'));
    } finally {
      setDispatching(false);
    }
  };

  const currentBatch = proposal?.proposedBatches?.[selectedBatchIndex] || null;

  // Extract polyline for map
  const routePolyline = React.useMemo(() => {
    if (!currentBatch?.routeGeometriesJson) return [];
    try {
      const parsed = typeof currentBatch.routeGeometriesJson === 'string'
        ? JSON.parse(currentBatch.routeGeometriesJson)
        : currentBatch.routeGeometriesJson;
      if (parsed.coordinates && Array.isArray(parsed.coordinates)) {
        return parsed.coordinates.map(c => [c[1], c[0]]);
      }
    } catch {
      return [];
    }
    return [];
  }, [currentBatch]);

  // Extract map pins
  const mapLocations = React.useMemo(() => {
    if (!currentBatch?.stopSequence) return [];
    return currentBatch.stopSequence.map(s => ({
      id: s.sequence,
      name: `#${s.sequence} ${s.locationName || s.contactName || 'Stop'}`,
      latitude: s.latitude,
      longitude: s.longitude,
      locationType: s.stopType === 'HUB' ? 'HUB' : 'CUSTOMER',
    }));
  }, [currentBatch]);

  return (
    <div className="space-y-6">
      {/* Configuration Header Card */}
      <Card
        title={isPickup ? "First-Mile Batch Pickup Dispatch" : "Final-Mile Batch Delivery Dispatch"}
        subtitle={`Corridor-constrained multi-stop tour optimization (Backend Corridor Limit: ${configuredRadius.toFixed(1)} km)`}
      >
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 items-end">
          {/* Hub Selector */}
          <div>
            <label className="block text-xs font-semibold text-themed-secondary mb-1">
              Central Hub *
            </label>
            <select
              value={selectedHubId}
              onChange={(e) => setSelectedHubId(e.target.value)}
              className="w-full px-3 py-2 text-sm rounded-lg bg-themed-surface border border-themed text-themed-primary focus:outline-none focus:ring-2 focus:ring-emerald-500/30"
              id="batch-hub-select"
            >
              <option value="">Select Central Hub</option>
              {hubs.filter(h => h.active && h.locationType === 'HUB').map(h => (
                <option key={h.id} value={h.id}>{h.name}</option>
              ))}
            </select>
          </div>

          {/* Corridor Radius Input */}
          <div>
            <label className="block text-xs font-semibold text-themed-secondary mb-1 flex items-center justify-between">
              <span>Route Corridor Limit</span>
              <span className="font-mono text-emerald-400">{Number(customRadius).toFixed(1)} km</span>
            </label>
            <div className="flex items-center gap-2">
              <input
                type="number"
                min="1.0"
                max="50.0"
                step="0.5"
                value={customRadius}
                onChange={(e) => setCustomRadius(Math.max(0.5, parseFloat(e.target.value) || 1.0))}
                className="w-full px-3 py-2 text-sm rounded-lg bg-themed-surface border border-themed text-themed-primary focus:outline-none focus:ring-2 focus:ring-emerald-500/30 font-mono"
                id="batch-radius-input"
              />
            </div>
          </div>

          {/* Vehicle Selector (For Capacity Check) */}
          <div>
            <label className="block text-xs font-semibold text-themed-secondary mb-1">
              Vehicle (Capacity Model)
            </label>
            <select
              value={selectedVehicleId}
              onChange={(e) => setSelectedVehicleId(e.target.value)}
              className="w-full px-3 py-2 text-sm rounded-lg bg-themed-surface border border-themed text-themed-primary focus:outline-none focus:ring-2 focus:ring-emerald-500/30"
              id="batch-vehicle-select"
            >
              <option value="">Auto (Default 500 kg)</option>
              {vehicles.filter(v => v.status === 'AVAILABLE' || v.status === 'IDLE').map(v => (
                <option key={v.id} value={v.id}>
                  {v.model || v.plateNumber} ({v.capacityKg || 500} kg cap)
                </option>
              ))}
            </select>
          </div>

          {/* Optimize Button */}
          <div>
            <Button
              variant="primary"
              onClick={handleProposeBatch}
              loading={loadingProposal}
              disabled={!selectedHubId || loadingProposal}
              className="w-full flex items-center justify-center gap-2"
              id="propose-batch-btn"
            >
              <Sliders className="w-4 h-4" />
              Compute Optimal Batch
            </Button>
          </div>
        </div>

        {proposalError && (
          <div className="mt-4 p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{proposalError}</span>
          </div>
        )}

        {dispatchSuccessMsg && (
          <div className="mt-4 p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>{dispatchSuccessMsg}</span>
          </div>
        )}
      </Card>

      {/* Batch Proposal Results */}
      {proposal && (
        <div className="space-y-6">
          {/* Summary Metric Badges */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <div className="p-4 rounded-xl bg-themed-surface border border-themed">
              <span className="text-xs text-themed-muted font-medium">Eligible Candidates</span>
              <div className="text-2xl font-bold font-mono text-themed-primary mt-1">
                {proposal.totalEligibleCandidates}
              </div>
            </div>

            <div className="p-4 rounded-xl bg-themed-surface border border-themed">
              <span className="text-xs text-themed-muted font-medium">Included in Batch</span>
              <div className="text-2xl font-bold font-mono text-emerald-400 mt-1">
                {proposal.includedCandidates}
              </div>
            </div>

            <div className="p-4 rounded-xl bg-themed-surface border border-themed">
              <span className="text-xs text-themed-muted font-medium">Excluded Candidates</span>
              <div className="text-2xl font-bold font-mono text-amber-400 mt-1">
                {proposal.excludedCandidates}
              </div>
            </div>

            <div className="p-4 rounded-xl bg-themed-surface border border-themed">
              <span className="text-xs text-themed-muted font-medium">Proposed Batch Groups</span>
              <div className="text-2xl font-bold font-mono text-cyan-400 mt-1">
                {proposal.proposedBatches?.length || 0}
              </div>
            </div>
          </div>

          {/* Batch Groups Selector if multiple */}
          {proposal.proposedBatches?.length > 1 && (
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-themed-secondary">Batch Group:</span>
              {proposal.proposedBatches.map((b, idx) => (
                <button
                  key={idx}
                  onClick={() => setSelectedBatchIndex(idx)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                    selectedBatchIndex === idx
                      ? 'bg-emerald-500 text-black shadow-md'
                      : 'bg-themed-surface text-themed-secondary hover:text-white border border-themed'
                  }`}
                >
                  Group #{b.batchNumber} ({b.legIds.length} stops • {b.totalWeightKg.toFixed(1)} kg)
                </button>
              ))}
            </div>
          )}

          {/* Selected Batch Details */}
          {currentBatch ? (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* Left 2 Cols: Route Metrics & Stop Sequence */}
              <div className="lg:col-span-2 space-y-6">
                <Card
                  title={`Batch #${currentBatch.batchNumber} Execution Plan`}
                  subtitle={`Optimized 2-Opt Tour: ${currentBatch.totalDistanceKm.toFixed(1)} km road distance (~${Math.round(currentBatch.totalDurationMin)} mins)`}
                >
                  {/* Capacity Bar */}
                  <div className="mb-6 p-4 rounded-xl bg-themed-elevated border border-themed">
                    <div className="flex justify-between items-center text-xs mb-2">
                      <span className="font-semibold text-themed-secondary flex items-center gap-1.5">
                        <Box className="w-4 h-4 text-emerald-400" />
                        Vehicle Weight Load
                      </span>
                      <span className="font-mono text-themed-primary font-bold">
                        {currentBatch.totalWeightKg.toFixed(1)} kg / {currentBatch.vehicleCapacityKg.toFixed(1)} kg ({Math.round((currentBatch.totalWeightKg / currentBatch.vehicleCapacityKg) * 100)}%)
                      </span>
                    </div>
                    <div className="w-full bg-slate-800 rounded-full h-2.5 overflow-hidden">
                      <div
                        className={`h-2.5 rounded-full ${
                          currentBatch.capacityCompliant ? 'bg-emerald-500' : 'bg-rose-500'
                        }`}
                        style={{ width: `${Math.min(100, (currentBatch.totalWeightKg / currentBatch.vehicleCapacityKg) * 100)}%` }}
                      />
                    </div>
                  </div>

                  {/* Stop Sequence List */}
                  <div className="space-y-3">
                    <h4 className="text-xs font-bold uppercase tracking-wider text-themed-secondary">
                      Optimized Multi-Stop Order ({currentBatch.stopSequence?.length || 0} stops)
                    </h4>

                    <div className="divide-y divide-themed border border-themed rounded-xl overflow-hidden">
                      {currentBatch.stopSequence?.map((stop, sIdx) => (
                        <div key={sIdx} className="p-3 bg-themed-surface flex items-center justify-between gap-3 text-xs">
                          <div className="flex items-center gap-3">
                            <span className="w-6 h-6 rounded-full bg-emerald-500/20 text-emerald-400 font-bold flex items-center justify-center shrink-0 font-mono text-[11px] border border-emerald-500/40">
                              {stop.sequence}
                            </span>
                            <div>
                              <div className="font-semibold text-themed-primary">
                                {stop.locationName || stop.address}
                              </div>
                              <div className="text-[11px] text-themed-muted mt-0.5">
                                {stop.contactName && <span>Contact: {stop.contactName} ({stop.contactPhone}) • </span>}
                                {stop.trackingNumber && <span className="font-mono text-cyan-400">{stop.trackingNumber}</span>}
                              </div>
                            </div>
                          </div>

                          <div className="text-right shrink-0">
                            <span className="inline-block px-2 py-0.5 rounded text-[10px] font-bold bg-slate-800 text-slate-300 font-mono">
                              {stop.weightKg ? `${stop.weightKg.toFixed(1)} kg` : 'Hub'}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </Card>

                {/* Dispatch Action Card */}
                <Card title="Assign Personnel & Dispatch Batch">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-4">
                    <div>
                      <label className="block text-xs font-semibold text-themed-secondary mb-1">
                        Select Authorized Driver *
                      </label>
                      <select
                        value={selectedDriverId}
                        onChange={(e) => setSelectedDriverId(e.target.value)}
                        className="w-full px-3 py-2 text-sm rounded-lg bg-themed-surface border border-themed text-themed-primary focus:outline-none focus:ring-2 focus:ring-emerald-500/30"
                        id="batch-driver-select"
                      >
                        <option value="">Select Driver</option>
                        {drivers.filter(d => d.status === 'AVAILABLE' || d.status === 'IDLE').map(d => (
                          <option key={d.id} value={d.id}>
                            {d.name || d.fullName} ({d.phoneNumber || 'Available'})
                          </option>
                        ))}
                      </select>
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-themed-secondary mb-1">
                        Confirm Vehicle *
                      </label>
                      <select
                        value={selectedVehicleId}
                        onChange={(e) => setSelectedVehicleId(e.target.value)}
                        className="w-full px-3 py-2 text-sm rounded-lg bg-themed-surface border border-themed text-themed-primary focus:outline-none focus:ring-2 focus:ring-emerald-500/30"
                      >
                        <option value="">Select Vehicle</option>
                        {vehicles.filter(v => v.status === 'AVAILABLE' || v.status === 'IDLE').map(v => (
                          <option key={v.id} value={v.id}>
                            {v.model || v.plateNumber} (Cap: {v.capacityKg} kg)
                          </option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div className="mb-4">
                    <label className="block text-xs font-semibold text-themed-secondary mb-1">
                      Dispatch Notes (Optional)
                    </label>
                    <input
                      type="text"
                      value={dispatchNotes}
                      onChange={(e) => setDispatchNotes(e.target.value)}
                      placeholder="e.g. Morning 10km batch corridor, priority handling"
                      className="w-full px-3 py-2 text-sm rounded-lg bg-themed-surface border border-themed text-themed-primary focus:outline-none focus:ring-2 focus:ring-emerald-500/30"
                    />
                  </div>

                  {dispatchError && (
                    <div className="mb-4 p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-center gap-2">
                      <AlertCircle className="w-4 h-4 shrink-0" />
                      <span>{dispatchError}</span>
                    </div>
                  )}

                  <Button
                    variant="primary"
                    onClick={handleDispatchBatch}
                    loading={dispatching}
                    disabled={!selectedDriverId || !selectedVehicleId || dispatching}
                    className="w-full flex items-center justify-center gap-2 py-3"
                    id="confirm-batch-dispatch-btn"
                  >
                    <Truck className="w-4 h-4" />
                    Dispatch Entire Batch ({currentBatch.legIds.length} Packages)
                  </Button>
                </Card>
              </div>

              {/* Right 1 Col: Route Map & Exclusions */}
              <div className="space-y-6">
                {/* Route Map Preview */}
                <Card title="Batch Corridor Route Map">
                  <div className="h-72 rounded-xl overflow-hidden border border-themed">
                    <MapComponent
                      locations={mapLocations}
                      edges={[]}
                      customPolylinePositions={routePolyline}
                      height="100%"
                    />
                  </div>
                </Card>

                {/* Exclusions Summary */}
                <Card
                  title={`Excluded Candidates (${proposal.excludedCandidates})`}
                  subtitle="Packages outside corridor limit or incompatible"
                >
                  {proposal.excludedDetails?.length === 0 ? (
                    <p className="text-xs text-themed-muted">No candidates were excluded. All nearby shipments fit within corridor limits.</p>
                  ) : (
                    <div className="space-y-2.5 max-h-60 overflow-y-auto pr-1">
                      {proposal.excludedDetails?.map((ex, exIdx) => (
                        <div key={exIdx} className="p-2.5 rounded-lg bg-slate-900/60 border border-slate-800 text-xs">
                          <div className="flex justify-between items-center font-mono text-[11px] text-themed-secondary mb-1">
                            <span>{ex.trackingNumber || `Leg #${ex.legId}`}</span>
                            <span className="text-amber-400 font-bold">{ex.directDistanceToHubKm?.toFixed(1)} km</span>
                          </div>
                          <div className="text-themed-muted text-[11px] line-clamp-1 mb-1">
                            {ex.originName}
                          </div>
                          <div className="text-rose-400 text-[10px] bg-rose-500/10 px-2 py-0.5 rounded border border-rose-500/20">
                            {ex.exclusionReason}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </Card>
              </div>
            </div>
          ) : (
            <Card>
              <div className="text-center py-8 text-themed-muted text-sm">
                No batches could be formed for this hub with the current corridor limits.
              </div>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
