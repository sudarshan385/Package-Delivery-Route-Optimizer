import React, { useState, useEffect } from 'react';
import api from '../../api/axios';
import { 
  Truck, Package, MapPin, Navigation, Phone, CheckCircle2, 
  Clock, AlertCircle, ShieldCheck, ArrowRight, RefreshCw, Box, ExternalLink
} from 'lucide-react';
import Card from './Card';
import Button from './Button';
import MapComponent from './Map';
import { parseApiError } from './ErrorState';

export default function DriverManifestBoard({ onStatusUpdated }) {
  const [loading, setLoading] = useState(true);
  const [manifest, setManifest] = useState(null);
  const [fetchError, setFetchError] = useState('');
  const [transitioning, setTransitioning] = useState(false);
  const [actionError, setActionError] = useState('');

  const fetchManifest = async () => {
    setLoading(true);
    setFetchError('');
    try {
      const res = await api.get('/shipments/driver/manifest');
      setManifest(res.data);
    } catch (err) {
      setFetchError(parseApiError(err, 'Could not fetch driver route manifest'));
      setManifest(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchManifest();
  }, []);

  const handleUpdateStatus = async (legId, nextStatus) => {
    setTransitioning(true);
    setActionError('');
    try {
      await api.patch(`/shipments/legs/${legId}/status`, {
        status: nextStatus,
        reason: null,
      });
      await fetchManifest();
      if (onStatusUpdated) onStatusUpdated();
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to update delivery stop status'));
    } finally {
      setTransitioning(false);
    }
  };

  if (loading) {
    return (
      <Card>
        <div className="py-12 flex flex-col items-center justify-center text-center">
          <RefreshCw className="w-8 h-8 text-emerald-400 animate-spin mb-3" />
          <p className="text-sm font-medium text-themed-secondary">Synchronizing driver trip manifest...</p>
        </div>
      </Card>
    );
  }

  if (fetchError) {
    return (
      <Card>
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{fetchError}</span>
          </div>
          <Button variant="secondary" size="sm" onClick={fetchManifest}>
            Retry
          </Button>
        </div>
      </Card>
    );
  }

  if (!manifest || !manifest.hasActiveTrip || !manifest.stops || manifest.stops.length === 0) {
    return (
      <Card>
        <div className="py-12 text-center">
          <Truck className="w-12 h-12 mx-auto text-themed-muted mb-3 opacity-60" />
          <h3 className="text-base font-bold text-themed-primary mb-1">No Active Batch Trip Assigned</h3>
          <p className="text-xs text-themed-muted max-w-md mx-auto mb-4">
            You do not currently have any active multi-stop batch routes. Check back once dispatch assigns your next pickup or delivery manifest.
          </p>
          <Button variant="secondary" size="sm" icon={RefreshCw} onClick={fetchManifest}>
            Refresh Status
          </Button>
        </div>
      </Card>
    );
  }

  const currentStop = manifest.currentStop;
  const isPickup = currentStop?.stopType === 'PICKUP';

  // Map locations for active trip
  const mapLocations = manifest.stops.map(s => ({
    id: s.sequence,
    name: `#${s.sequence} ${s.locationName || s.contactName || 'Stop'}`,
    latitude: s.latitude,
    longitude: s.longitude,
    locationType: s.stopType === 'HUB' ? 'HUB' : 'CUSTOMER',
  }));

  return (
    <div className="space-y-6">
      {/* Manifest Overview Header */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        {/* Vehicle & Capacity */}
        <div className="p-4 rounded-xl bg-themed-surface border border-themed">
          <div className="flex items-center justify-between text-xs text-themed-muted mb-1">
            <span>Vehicle Load</span>
            <span className="font-mono text-emerald-400 font-bold">{manifest.vehicleModel || 'Assigned Vehicle'}</span>
          </div>
          <div className="text-xl font-bold font-mono text-themed-primary mb-2">
            {manifest.totalLoadedWeightKg?.toFixed(1) || 0} / {manifest.vehicleCapacityKg?.toFixed(1) || 500} kg
          </div>
          <div className="w-full bg-slate-800 rounded-full h-2 overflow-hidden">
            <div
              className="h-2 rounded-full bg-emerald-500"
              style={{ width: `${Math.min(100, manifest.capacityUtilizationPercent || 0)}%` }}
            />
          </div>
        </div>

        {/* Total Stops */}
        <div className="p-4 rounded-xl bg-themed-surface border border-themed">
          <span className="text-xs text-themed-muted font-medium">Total Manifest Stops</span>
          <div className="text-2xl font-bold font-mono text-themed-primary mt-1">
            {manifest.totalStops}
          </div>
        </div>

        {/* Completed Stops */}
        <div className="p-4 rounded-xl bg-themed-surface border border-themed">
          <span className="text-xs text-themed-muted font-medium">Completed Stops</span>
          <div className="text-2xl font-bold font-mono text-emerald-400 mt-1">
            {manifest.completedStops}
          </div>
        </div>

        {/* Remaining Stops */}
        <div className="p-4 rounded-xl bg-themed-surface border border-themed">
          <span className="text-xs text-themed-muted font-medium">Remaining Stops</span>
          <div className="text-2xl font-bold font-mono text-amber-400 mt-1">
            {manifest.remainingStops}
          </div>
        </div>
      </div>

      {actionError && (
        <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{actionError}</span>
        </div>
      )}

      {/* Hero: Current Active Stop */}
      {currentStop ? (
        <Card
          title="Current Active Destination Stop"
          subtitle={`Stop #${currentStop.sequence} of ${manifest.totalStops} • Immediate Priority`}
          className="border-2 border-emerald-500/40 shadow-lg shadow-emerald-500/5"
        >
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 items-center">
            <div className="md:col-span-2 space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className={`px-2.5 py-1 rounded-md text-xs font-bold uppercase tracking-wider ${
                  isPickup ? 'bg-blue-500/20 text-blue-400 border border-blue-500/30' : 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                }`}>
                  {currentStop.stopType}
                </span>

                <span className="px-2.5 py-1 rounded-md text-xs font-mono bg-slate-800 text-cyan-400 border border-slate-700 font-bold">
                  {currentStop.trackingNumber}
                </span>

                <span className="text-xs text-themed-muted">
                  Package Weight: <strong className="text-themed-primary font-mono">{currentStop.weightKg} kg</strong>
                </span>
              </div>

              <div>
                <h3 className="text-lg font-bold text-themed-primary">
                  {currentStop.address || currentStop.locationName}
                </h3>
                {currentStop.contactName && (
                  <div className="flex items-center gap-4 mt-2 text-xs text-themed-secondary">
                    <span className="font-semibold">{currentStop.contactName}</span>
                    {currentStop.contactPhone && (
                      <a
                        href={`tel:${currentStop.contactPhone}`}
                        className="flex items-center gap-1 text-emerald-400 hover:underline font-mono"
                      >
                        <Phone className="w-3.5 h-3.5" />
                        {currentStop.contactPhone}
                      </a>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex flex-col gap-2.5">
              {currentStop.status === 'ASSIGNED' && (
                <Button
                  variant="primary"
                  onClick={() => handleUpdateStatus(currentStop.legId, 'IN_TRANSIT')}
                  loading={transitioning}
                  className="w-full flex items-center justify-center gap-2 py-3"
                  id="driver-start-leg-btn"
                >
                  <Navigation className="w-4 h-4" />
                  Start Trip to Stop (In Transit)
                </Button>
              )}

              {currentStop.status === 'IN_TRANSIT' && (
                <Button
                  variant="primary"
                  onClick={() => handleUpdateStatus(currentStop.legId, 'COMPLETED')}
                  loading={transitioning}
                  className="w-full flex items-center justify-center gap-2 py-3 bg-emerald-600 hover:bg-emerald-500 text-white"
                  id="driver-complete-stop-btn"
                >
                  <CheckCircle2 className="w-4 h-4" />
                  Confirm Stop Completed ({isPickup ? 'Collected' : 'Delivered'})
                </Button>
              )}
            </div>
          </div>
        </Card>
      ) : (
        <Card>
          <div className="p-4 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>All stops in this batch have been successfully completed! Trip is finalized.</span>
          </div>
        </Card>
      )}

      {/* Manifest Sequence Table & Map */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Table of all stops */}
        <div className="lg:col-span-2">
          <Card title="Trip Stop Manifest Sequence" subtitle="Ordered optimal stop sequence">
            <div className="divide-y divide-themed border border-themed rounded-xl overflow-hidden">
              {manifest.stops.map((stop) => {
                const isCurrent = stop.sequence === currentStop?.sequence;
                return (
                  <div
                    key={stop.sequence}
                    className={`p-3.5 flex items-center justify-between gap-3 text-xs transition ${
                      isCurrent ? 'bg-emerald-500/10' : stop.completed ? 'bg-themed-surface opacity-75' : 'bg-themed-surface'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <span className={`w-7 h-7 rounded-full font-bold flex items-center justify-center shrink-0 font-mono text-xs border ${
                        stop.completed
                          ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40'
                          : isCurrent
                          ? 'bg-cyan-500 text-slate-950 border-cyan-400 font-extrabold shadow-sm'
                          : 'bg-slate-800 text-slate-400 border-slate-700'
                      }`}>
                        {stop.completed ? '✓' : stop.sequence}
                      </span>

                      <div>
                        <div className="font-semibold text-themed-primary flex items-center gap-2">
                          <span>{stop.locationName || stop.address}</span>
                          <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                            stop.stopType === 'PICKUP' ? 'bg-blue-500/20 text-blue-400' : 'bg-emerald-500/20 text-emerald-400'
                          }`}>
                            {stop.stopType}
                          </span>
                        </div>
                        <div className="text-[11px] text-themed-muted mt-0.5">
                          {stop.contactName && <span>{stop.contactName} ({stop.contactPhone}) • </span>}
                          <span className="font-mono text-cyan-400">{stop.trackingNumber}</span>
                        </div>
                      </div>
                    </div>

                    <div className="text-right shrink-0">
                      <span className={`inline-block px-2.5 py-1 rounded text-[11px] font-bold font-mono ${
                        stop.completed
                          ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                          : stop.status === 'IN_TRANSIT'
                          ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30 animate-pulse'
                          : 'bg-slate-800 text-slate-400 border border-slate-700'
                      }`}>
                        {stop.status}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </Card>
        </div>

        {/* Map */}
        <div>
          <Card title="Manifest Route Preview">
            <div className="h-80 rounded-xl overflow-hidden border border-themed">
              <MapComponent
                locations={mapLocations}
                edges={[]}
                height="100%"
              />
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
