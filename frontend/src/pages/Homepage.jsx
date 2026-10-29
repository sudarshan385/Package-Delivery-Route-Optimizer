import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import { 
  Search, ArrowRight, Route, ShieldCheck, Zap, Truck, 
  MapPin, CheckCircle2, Navigation, Layers, Sparkles, Building2
} from 'lucide-react';
import Modal from '../components/ui/Modal';
import ShipmentTimelineView from '../components/ui/ShipmentTimelineView';

export default function Homepage() {
  const { user } = useAuth();
  const [trackingNumber, setTrackingNumber] = useState('');
  const [trackingLoading, setTrackingLoading] = useState(false);
  const [trackingError, setTrackingError] = useState('');
  const [trackedShipment, setTrackedShipment] = useState(null);

  const handleTrackSubmit = async (e) => {
    e.preventDefault();
    const query = trackingNumber.trim();
    if (!query) return;

    setTrackingLoading(true);
    setTrackingError('');
    try {
      const res = await api.get(`/shipments/track/${encodeURIComponent(query)}`);
      setTrackedShipment(res.data);
    } catch (err) {
      setTrackingError(
        err.response?.data?.message || 
        'No active shipment found matching this tracking identifier. Please check and try again.'
      );
    } finally {
      setTrackingLoading(false);
    }
  };

  return (
    <div className="relative min-h-screen w-full bg-[#070b14] text-white selection:bg-cyan-500 selection:text-slate-950 overflow-x-hidden font-sans">
      {/* Background Futuristic Grid & Subtle Particle Glows */}
      <div 
        className="fixed inset-0 pointer-events-none opacity-20 z-0"
        style={{
          backgroundImage: `
            linear-gradient(to right, rgba(6, 182, 212, 0.08) 1px, transparent 1px),
            linear-gradient(to bottom, rgba(6, 182, 212, 0.08) 1px, transparent 1px)
          `,
          backgroundSize: '48px 48px',
        }}
      />
      <div className="fixed top-[-10%] right-[-10%] w-[650px] h-[650px] bg-cyan-500/10 rounded-full blur-[140px] pointer-events-none z-0" />
      <div className="fixed bottom-[-10%] left-[-10%] w-[650px] h-[650px] bg-emerald-500/10 rounded-full blur-[140px] pointer-events-none z-0" />

      {/* Top Navigation Bar */}
      <header className="relative z-20 border-b border-cyan-900/30 backdrop-blur-xl bg-[#070b14]/80">
        <div className="max-w-7xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-cyan-600 to-emerald-400 p-[1px] shadow-lg shadow-cyan-500/20">
              <div className="w-full h-full bg-[#070b14] rounded-xl flex items-center justify-center">
                <Route className="text-cyan-400" size={22} />
              </div>
            </div>
            <div>
              <div className="font-extrabold text-base tracking-wider text-white flex items-center gap-2">
                <span>BENGALURU SMART DELIVERY</span>
                <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded-full bg-cyan-950 text-cyan-300 border border-cyan-800">
                  2.0 HUB NETWORK
                </span>
              </div>
              <div className="text-[11px] text-slate-400 font-mono tracking-tight">
                Graph-Theoretic 2-Leg Autonomous Logistics Engine
              </div>
            </div>
          </div>

          <nav className="flex items-center gap-3 md:gap-4">
            {user ? (
              <Link
                to="/deliveries"
                className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-emerald-400 text-slate-950 font-bold text-xs uppercase tracking-wider shadow-lg shadow-cyan-500/20 hover:scale-[1.02] active:scale-[0.98] transition-all"
              >
                <span>Console Dashboard</span>
                <ArrowRight size={14} />
              </Link>
            ) : (
              <>
                <Link
                  to="/register"
                  className="px-5 py-2.5 rounded-xl border border-cyan-500/30 hover:border-cyan-400/80 bg-cyan-950/20 text-cyan-300 text-xs font-bold uppercase tracking-wider backdrop-blur-md transition-all hover:bg-cyan-950/40"
                >
                  Join
                </Link>
                <Link
                  to="/login"
                  className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-emerald-400 text-slate-950 text-xs font-bold uppercase tracking-wider shadow-md shadow-cyan-500/20 hover:scale-[1.02] active:scale-[0.98] transition-all"
                >
                  Login
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>

      {/* Main Hero Container */}
      <main className="relative z-10 max-w-7xl mx-auto px-6 pt-16 pb-24">
        {/* Hero Section */}
        <div className="text-center max-w-3xl mx-auto mb-14">
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-cyan-950/60 border border-cyan-500/30 text-cyan-300 text-xs font-mono mb-6 shadow-inner">
            <Sparkles size={14} className="text-cyan-400" />
            <span>Autonomous First-Mile to Final-Mile Parcel Optimization</span>
          </div>

          <h1 className="text-4xl md:text-5xl lg:text-6xl font-black tracking-tight leading-[1.1] text-white">
            Bengaluru’s Intelligent{' '}
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 via-emerald-300 to-teal-200">
              Two-Leg Delivery Network
            </span>
          </h1>

          <p className="mt-6 text-base md:text-lg text-slate-300 leading-relaxed max-w-2xl mx-auto">
            Dynamic pickup assignment, 20 virtual central service hubs, Dijkstra shortest-path evaluation, and automated road navigation across real Bengaluru geography.
          </p>

          {/* Real-time Tracking Input Box */}
          <div className="mt-10 max-w-xl mx-auto">
            <form onSubmit={handleTrackSubmit} className="relative flex items-center">
              <div className="absolute left-4 pointer-events-none text-slate-400">
                <Search size={18} />
              </div>
              <input
                type="text"
                value={trackingNumber}
                onChange={(e) => {
                  setTrackingNumber(e.target.value);
                  setTrackingError('');
                }}
                placeholder="Enter Tracking Identifier (e.g. PKG-1793268790172-EA754C)..."
                className="w-full pl-11 pr-32 py-4 rounded-2xl bg-[#0c1424] border border-cyan-500/40 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-cyan-400 focus:ring-2 focus:ring-cyan-400/20 shadow-2xl transition-all font-mono"
              />
              <button
                type="submit"
                disabled={trackingLoading || !trackingNumber.trim()}
                className="absolute right-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-emerald-400 text-slate-950 text-xs font-bold uppercase tracking-wider shadow-md hover:scale-[1.02] active:scale-[0.98] transition-all disabled:opacity-50"
              >
                {trackingLoading ? 'Searching...' : 'Track Live'}
              </button>
            </form>

            {trackingError && (
              <div className="mt-3 p-3 rounded-xl bg-red-950/60 border border-red-800 text-red-300 text-xs text-left flex items-center gap-2">
                <span>⚠️ {trackingError}</span>
              </div>
            )}
          </div>
        </div>

        {/* 3-Step Interactive Workflow Illustration */}
        <div className="mt-12 p-8 md:p-10 rounded-3xl bg-[#0a101d]/90 border border-cyan-900/40 shadow-2xl backdrop-blur-xl relative overflow-hidden">
          <div className="flex items-center justify-between pb-6 mb-8 border-b border-slate-800">
            <div>
              <div className="text-xs font-mono font-bold text-cyan-400 tracking-wider uppercase">
                Autonomous Architecture
              </div>
              <h2 className="text-xl font-bold text-white mt-1">
                The 3-Step Operational Pipeline
              </h2>
            </div>
            <div className="flex items-center gap-2 text-xs font-mono text-slate-400">
              <div className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
              <span>Real-Time Dispatch Engine</span>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 relative">
            {/* Step 1 */}
            <div className="p-6 rounded-2xl bg-gradient-to-b from-[#0f172a] to-[#090d16] border border-cyan-500/30 relative group hover:border-cyan-400 transition-all duration-300">
              <div className="w-12 h-12 rounded-xl bg-cyan-950 border border-cyan-500/40 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <MapPin className="text-cyan-400" size={24} />
              </div>
              <div className="text-[11px] font-mono font-bold text-cyan-400 mb-1">STAGE 01</div>
              <h3 className="text-base font-bold text-white mb-2">Delivery Request</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                Customer places a pickup pin and destination on the interactive Bengaluru map. The system runs real road matrix calculation to assign the nearest of 20 candidate service hubs.
              </p>
            </div>

            {/* Step 2 */}
            <div className="p-6 rounded-2xl bg-gradient-to-b from-[#0f172a] to-[#090d16] border border-emerald-500/30 relative group hover:border-emerald-400 transition-all duration-300">
              <div className="w-12 h-12 rounded-xl bg-emerald-950 border border-emerald-500/40 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <Truck className="text-emerald-400" size={24} />
              </div>
              <div className="text-[11px] font-mono font-bold text-emerald-400 mb-1">STAGE 02</div>
              <h3 className="text-base font-bold text-white mb-2">Two-Leg Partner Assignment</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                First-Mile driver receives the pickup task to transport the parcel from the sender to the Central Hub. Upon hub arrival and intake, the Final-Mile partner is dispatched to the customer.
              </p>
            </div>

            {/* Step 3 */}
            <div className="p-6 rounded-2xl bg-gradient-to-b from-[#0f172a] to-[#090d16] border border-indigo-500/30 relative group hover:border-indigo-400 transition-all duration-300">
              <div className="w-12 h-12 rounded-xl bg-indigo-950 border border-indigo-500/40 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <Route className="text-indigo-400" size={24} />
              </div>
              <div className="text-[11px] font-mono font-bold text-indigo-400 mb-1">STAGE 03</div>
              <h3 className="text-base font-bold text-white mb-2">Dijkstra & Multi-Mode Routing</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                Evaluates shortest road distance and fastest travel time across graph vertices with real OSRM road geometry, supporting Car, Motorized Two-Wheeler, and Walking modes.
              </p>
            </div>
          </div>

          {/* Network Metrics Bar */}
          <div className="mt-8 pt-6 border-t border-slate-800/80 grid grid-cols-2 md:grid-cols-4 gap-4 text-center">
            <div>
              <div className="text-2xl font-black font-mono text-cyan-400">20</div>
              <div className="text-[11px] text-slate-400 uppercase font-mono mt-0.5">Bengaluru Virtual Hubs</div>
            </div>
            <div>
              <div className="text-2xl font-black font-mono text-emerald-400">2-Leg</div>
              <div className="text-[11px] text-slate-400 uppercase font-mono mt-0.5">Normalized Architecture</div>
            </div>
            <div>
              <div className="text-2xl font-black font-mono text-white">6 Stages</div>
              <div className="text-[11px] text-slate-400 uppercase font-mono mt-0.5">Persisted Milestones</div>
            </div>
            <div>
              <div className="text-2xl font-black font-mono text-indigo-400">OSRM + TSP</div>
              <div className="text-[11px] text-slate-400 uppercase font-mono mt-0.5">Real Road Geometry</div>
            </div>
          </div>
        </div>
      </main>

      {/* Public Tracking Result Modal */}
      <Modal
        isOpen={!!trackedShipment}
        onClose={() => setTrackedShipment(null)}
        title={trackedShipment ? `Live Tracking • ${trackedShipment.trackingNumber}` : 'Shipment Tracking'}
        maxWidth="1050px"
      >
        {trackedShipment && (
          <ShipmentTimelineView
            shipmentData={trackedShipment}
            onRefresh={async () => {
              const res = await api.get(`/shipments/track/${trackedShipment.trackingNumber}`);
              setTrackedShipment(res.data);
            }}
          />
        )}
      </Modal>
    </div>
  );
}
