import React, { useState, useEffect } from 'react';
import api from '../../api/axios';
import { 
  Car, Bike, Footprints, Clock, Route, CheckCircle, 
  AlertTriangle, Sparkles, Navigation, Layers 
} from 'lucide-react';

/**
 * RouteAlternativesCard:
 * Provides multi-mode navigation alternatives (Car, Two-Wheeler, Walking).
 * Queries /api/edges/route-alternatives to fetch up to 3 genuine road routes with
 * provider geometry, distance, and duration metrics.
 */
export default function RouteAlternativesCard({
  sourceId,
  destId,
  onSelectAlternative,
  selectedAlternativeIndex = 0,
}) {
  const [mode, setMode] = useState('CAR');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [routingData, setRoutingData] = useState(null);
  const [selectedIndex, setSelectedIndex] = useState(selectedAlternativeIndex);

  const fetchAlternatives = async (selectedMode) => {
    if (!sourceId || !destId || sourceId === destId) {
      setRoutingData(null);
      setError('');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const res = await api.get('/edges/route-alternatives', {
        params: {
          sourceId,
          destinationId: destId,
          mode: selectedMode,
        },
      });

      setRoutingData(res.data);
      const initialIdx = 0;
      setSelectedIndex(initialIdx);

      if (res.data?.routes && res.data.routes.length > 0 && onSelectAlternative) {
        onSelectAlternative(res.data.routes[initialIdx], res.data);
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Routing calculation failed';
      setError(msg);
      setRoutingData(null);
      if (onSelectAlternative) {
        onSelectAlternative(null, null);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (sourceId && destId && sourceId !== destId) {
      fetchAlternatives(mode);
    } else {
      setRoutingData(null);
      setError('');
    }
  }, [sourceId, destId, mode]);

  const handleModeChange = (newMode) => {
    setMode(newMode);
    fetchAlternatives(newMode);
  };

  const handleCardClick = (idx, route) => {
    setSelectedIndex(idx);
    if (onSelectAlternative) {
      onSelectAlternative(route, routingData);
    }
  };

  if (!sourceId || !destId || sourceId === destId) {
    return null;
  }

  return (
    <div className="mt-4 p-4 rounded-xl bg-slate-900/90 border border-slate-800 shadow-xl">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4 pb-3 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <Navigation size={18} className="text-cyan-400" />
          <h4 className="text-sm font-bold text-white tracking-wide">
            ROAD NAVIGATION ALTERNATIVES
          </h4>
          <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-cyan-950 text-cyan-300 border border-cyan-800/60">
            OSRM Engine
          </span>
        </div>

        {/* Transport Mode Switcher */}
        <div className="flex items-center gap-1.5 p-1 rounded-lg bg-slate-950/80 border border-slate-800">
          <button
            type="button"
            onClick={() => handleModeChange('CAR')}
            className={`flex items-center gap-1 px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
              mode === 'CAR'
                ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/20'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Car size={14} />
            <span>Car</span>
          </button>

          <button
            type="button"
            onClick={() => handleModeChange('TWO_WHEELER')}
            className={`flex items-center gap-1 px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
              mode === 'TWO_WHEELER'
                ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/20'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Bike size={14} />
            <span>Two-Wheeler</span>
          </button>

          <button
            type="button"
            onClick={() => handleModeChange('WALKING')}
            className={`flex items-center gap-1 px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
              mode === 'WALKING'
                ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/20'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Footprints size={14} />
            <span>Walking</span>
          </button>
        </div>
      </div>

      {loading && (
        <div className="py-6 text-center text-xs text-slate-400 flex items-center justify-center gap-2">
          <div className="w-4 h-4 rounded-full border-2 border-cyan-400 border-t-transparent animate-spin" />
          <span>Evaluating real Bengaluru road network alternatives via OSRM...</span>
        </div>
      )}

      {error && (
        <div className="p-3 rounded-lg bg-red-950/40 border border-red-800/50 text-red-300 text-xs flex items-center gap-2">
          <AlertTriangle size={16} className="text-red-400 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {!loading && !error && routingData?.routes && (
        <div className="space-y-2.5">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {routingData.routes.map((route, idx) => {
              const isSelected = selectedIndex === idx;
              return (
                <div
                  key={idx}
                  onClick={() => handleCardClick(idx, route)}
                  className={`cursor-pointer p-3.5 rounded-xl border transition-all duration-200 relative ${
                    isSelected
                      ? 'bg-cyan-950/40 border-cyan-400 shadow-lg shadow-cyan-500/10'
                      : 'bg-slate-950/60 border-slate-800 hover:border-slate-700 hover:bg-slate-900/60'
                  }`}
                >
                  {/* Recommended Badge */}
                  {route.recommended && (
                    <span className="absolute -top-2.5 right-3 px-2 py-0.5 rounded-full bg-emerald-500 text-slate-950 text-[10px] font-bold uppercase tracking-wider shadow-sm flex items-center gap-1">
                      <Sparkles size={11} />
                      Recommended
                    </span>
                  )}

                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-xs font-bold text-white tracking-wide">
                      {route.label}
                    </span>
                    {isSelected && <CheckCircle size={15} className="text-cyan-400" />}
                  </div>

                  <div className="flex items-baseline gap-3 my-2">
                    <div>
                      <span className="text-lg font-mono font-extrabold text-cyan-300">
                        {route.distanceKm.toFixed(1)}
                      </span>
                      <span className="text-xs text-slate-400 ml-0.5">km</span>
                    </div>
                    <div className="text-slate-600 font-mono">•</div>
                    <div>
                      <span className="text-lg font-mono font-extrabold text-white">
                        {Math.round(route.durationMin)}
                      </span>
                      <span className="text-xs text-slate-400 ml-0.5">min</span>
                    </div>
                  </div>

                  <div className="text-[11px] text-slate-400 font-mono truncate">
                    Criterion: {route.criterion}
                  </div>
                </div>
              );
            })}
          </div>

          <div className="flex items-center justify-between pt-2 text-[11px] text-slate-500 font-mono">
            <span>Source: {routingData.sourceLocationName} → Dest: {routingData.destinationLocationName}</span>
            <span>Attribution: © OpenStreetMap contributors, OSRM Project</span>
          </div>
        </div>
      )}
    </div>
  );
}
