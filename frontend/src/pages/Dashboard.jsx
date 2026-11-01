import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import {
  Package, Send, Users, Truck, Route, AlertCircle,
  RefreshCw, CheckCircle2, Clock, XCircle, AlertTriangle,
  ArrowRight, MapPin, Navigation, Flag, ChevronRight,
  Shield, Compass, User
} from 'lucide-react';

export default function Dashboard() {
  const { user } = useAuth();

  // Primary data states
  const [stats, setStats] = useState(null);
  const [deliveries, setDeliveries] = useState([]);
  const [userPackages, setUserPackages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [lastRefreshed, setLastRefreshed] = useState(null);

  // Route inspection states
  const [selectedDeliveryId, setSelectedDeliveryId] = useState(null);
  const [routeData, setRouteData] = useState(null);
  const [routeLoading, setRouteLoading] = useState(false);
  const [routeError, setRouteError] = useState('');

  // Fetch persisted route for a delivery
  const fetchRouteForDelivery = useCallback(async (deliveryId) => {
    if (!deliveryId) {
      setRouteData(null);
      setRouteError('');
      return;
    }
    setRouteLoading(true);
    setRouteError('');
    try {
      const res = await api.get(`/deliveries/${deliveryId}/routes/current`);
      if (res.data && res.data.id) {
        setRouteData(res.data);
      } else {
        setRouteData(null);
        setRouteError(`No active persisted route found for Delivery #${deliveryId}. Route has not been calculated yet.`);
      }
    } catch (err) {
      setRouteData(null);
      if (err.response?.status === 404) {
        setRouteError(`No active persisted route found for Delivery #${deliveryId}. Route has not been calculated yet.`);
      } else {
        setRouteError(err.response?.data?.message || err.response?.data?.detail || 'Failed to fetch persisted route');
      }
    } finally {
      setRouteLoading(false);
    }
  }, []);

  // Main dashboard fetch
  const fetchDashboardData = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError('');

    try {
      const requests = [
        api.get('/dashboard'),
        api.get('/deliveries')
      ];

      if (user?.role === 'USER') {
        requests.push(api.get('/packages?size=5&sort=createdAt,desc'));
      }

      const results = await Promise.all(requests);
      const dashRes = results[0];
      const delivRes = results[1];
      const pkgRes = results[2];

      const dashData = dashRes.data || {};
      const delivData = Array.isArray(delivRes.data)
        ? delivRes.data
        : (delivRes.data?.content || []);

      setStats(dashData);
      setDeliveries(delivData);
      if (pkgRes) {
        setUserPackages(pkgRes.data?.content || pkgRes.data || []);
      }
      setLastRefreshed(new Date());

      // Find an active or suitable delivery for route display
      if (delivData.length > 0) {
        // Prioritize in-progress or assigned, otherwise first
        const activeDeliv = delivData.find(d => d.status === 'IN_PROGRESS') ||
                            delivData.find(d => d.status === 'ASSIGNED') ||
                            delivData[0];
        setSelectedDeliveryId(activeDeliv.id);
        fetchRouteForDelivery(activeDeliv.id);
      } else {
        setSelectedDeliveryId(null);
        setRouteData(null);
        setRouteError('');
      }
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.detail || 'Failed to connect to backend telemetry service');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [fetchRouteForDelivery, user?.role]);

  useEffect(() => {
    fetchDashboardData(false);
  }, [fetchDashboardData]);

  // Handle user changing delivery selection in Route panel
  const handleSelectDelivery = (deliveryId) => {
    const id = Number(deliveryId);
    setSelectedDeliveryId(id);
    fetchRouteForDelivery(id);
  };

  // Check if database is empty per test and business contract
  const isDashboardEmpty =
    (stats?.totalPackages || 0) === 0 &&
    (stats?.totalDeliveries || 0) === 0 &&
    (stats?.availableDrivers || 0) === 0 &&
    (stats?.availableVehicles || 0) === 0 &&
    (stats?.totalRoutes || 0) === 0;

  // Real delivery counts by status
  const deliveriesByStatus = stats?.deliveriesByStatus || {};
  const createdCount = deliveriesByStatus.CREATED ?? 0;
  const assignedCount = deliveriesByStatus.ASSIGNED ?? 0;
  const inProgressCount = deliveriesByStatus.IN_PROGRESS ?? 0;
  const completedCount = deliveriesByStatus.COMPLETED ?? (stats?.completedDeliveries ?? 0);
  const cancelledCount = deliveriesByStatus.CANCELLED ?? (stats?.cancelledDeliveries ?? 0);
  const failedCount = deliveriesByStatus.FAILED ?? (stats?.failedDeliveries ?? 0);

  const totalDeliveries = stats?.totalDeliveries ?? (
    createdCount + assignedCount + inProgressCount + completedCount + cancelledCount + failedCount
  );

  const activeDeliveries = inProgressCount + assignedCount;

  // Real packages counts
  const packagesByStatus = stats?.packagesByStatus || {};
  const pendingPackages = packagesByStatus.PENDING ?? (packagesByStatus.CREATED ?? 0);
  const inDeliveryPackages = packagesByStatus.IN_DELIVERY ?? 0;

  // Top summary metric cards (strict backend data only)
  const summaryCards = [
    {
      id: 'stat-total-packages',
      title: 'Total Packages',
      value: stats?.totalPackages ?? 0,
      subtitle: `${inDeliveryPackages} in transit • ${pendingPackages} pending`,
      icon: Package,
      color: 'indigo',
      to: '/packages',
      tooltip: 'View and manage package records in the logistics ledger'
    },
    {
      id: 'stat-active-deliveries',
      title: 'Active Deliveries',
      value: activeDeliveries,
      subtitle: `${inProgressCount} in progress • ${assignedCount} assigned`,
      icon: Send,
      color: 'cyan',
      to: '/deliveries',
      tooltip: 'Inspect in-transit and assigned deliveries across the network'
    },
    {
      id: 'stat-available-drivers',
      title: 'Available Drivers',
      value: stats?.availableDrivers ?? 0,
      subtitle: `${stats?.driversOnDelivery ?? 0} on duty • ${(stats?.availableDrivers ?? 0) + (stats?.driversOnDelivery ?? 0)} total`,
      icon: Users,
      color: 'purple',
      to: '/drivers',
      tooltip: 'View available drivers and duty assignments'
    },
    {
      id: 'stat-available-vehicles',
      title: 'Available Vehicles',
      value: stats?.availableVehicles ?? 0,
      subtitle: `${stats?.vehiclesInUse ?? 0} in use • ${(stats?.availableVehicles ?? 0) + (stats?.vehiclesInUse ?? 0)} total`,
      icon: Truck,
      color: 'amber',
      to: '/vehicles',
      tooltip: 'Inspect available fleet transport units and maintenance statuses'
    },
    {
      id: 'stat-optimized-routes',
      title: 'Optimized Routes',
      value: stats?.totalRoutes ?? 0,
      subtitle: stats?.totalRouteDistance
        ? `${stats.totalRouteDistance.toFixed(1)} km total network`
        : 'Dijkstra optimized',
      icon: Route,
      color: 'green',
      to: '/routes',
      tooltip: 'Open the deterministic Dijkstra route optimization engine'
    }
  ];

  // 6 standard operational statuses
  const operationalStatuses = [
    {
      key: 'CREATED',
      label: 'Created',
      count: createdCount,
      color: '#f59e0b',
      badgeClass: 'badge-created',
      desc: 'Awaiting assignment',
      icon: Clock
    },
    {
      key: 'ASSIGNED',
      label: 'Assigned',
      count: assignedCount,
      color: '#6366f1',
      badgeClass: 'badge-assigned',
      desc: 'Driver & vehicle assigned',
      icon: Send
    },
    {
      key: 'IN_PROGRESS',
      label: 'In Progress',
      count: inProgressCount,
      color: '#06b6d4',
      badgeClass: 'badge-in_progress',
      desc: 'In transit on route',
      icon: Navigation,
      pulsing: inProgressCount > 0
    },
    {
      key: 'COMPLETED',
      label: 'Completed',
      count: completedCount,
      color: '#10b981',
      badgeClass: 'badge-completed',
      desc: 'Successfully delivered',
      icon: CheckCircle2
    },
    {
      key: 'CANCELLED',
      label: 'Cancelled',
      count: cancelledCount,
      color: '#ef4444',
      badgeClass: 'badge-cancelled',
      desc: 'Order voided',
      icon: XCircle
    },
    {
      key: 'FAILED',
      label: 'Failed',
      count: failedCount,
      color: '#ef4444',
      badgeClass: 'badge-failed',
      desc: 'Delivery issue',
      icon: AlertTriangle
    }
  ];

  // Helper for subtitle operational state description
  const getOperationalStatusSubtitle = () => {
    if (loading) return 'Connecting to logistics telemetry service...';
    if (error) return 'Telemetry connection interrupted — offline mode';
    if (isDashboardEmpty) return 'Network Standby • 0 registered dispatch records in database';
    if (activeDeliveries > 0) {
      return `Operational Active • ${activeDeliveries} ${activeDeliveries === 1 ? 'delivery' : 'deliveries'} underway across ${stats?.totalRoutes ?? 0} optimized route paths`;
    }
    return `Operational Idle • All ${totalDeliveries} historical deliveries reconciled • 0 active dispatches`;
  };

  if (loading) {
    return (
      <div className="loading-spinner" style={{ minHeight: '60vh' }}>
        <div className="spinner" />
      </div>
    );
  }

  // --- DRIVER DASHBOARD OVERRIDE ---
  if (user?.role === 'DRIVER') {
    const currentDelivery = deliveries.find(d => d.status === 'IN_PROGRESS' || d.status === 'ASSIGNED');

    // Resolve real driver profile information gracefully from auth & assignment telemetry
    const driverFullName =
      user?.name ||
      user?.fullName ||
      deliveries.find(d => d.driverName)?.driverName ||
      user?.username ||
      'Driver';

    const driverId =
      deliveries.find(d => d.driverId)?.driverId ||
      user?.userId ||
      user?.id ||
      null;

    const profileImageUrl =
      user?.profileImage ||
      user?.avatar ||
      user?.picture ||
      user?.avatarUrl ||
      user?.imageUrl ||
      null;

    const getInitials = (name) => {
      if (!name) return 'DR';
      const parts = name.trim().split(/\s+/).filter(Boolean);
      if (parts.length >= 2) {
        return (parts[0][0] + parts[1][0]).toUpperCase();
      }
      return name.slice(0, 2).toUpperCase();
    };

    // Determine availability status based on real delivery data
    let driverStatusText = 'Available';
    let driverStatusClass = 'available';

    if (inProgressCount > 0) {
      driverStatusText = 'On Delivery';
      driverStatusClass = 'on-delivery';
    } else if (assignedCount > 0) {
      driverStatusText = 'Assigned';
      driverStatusClass = 'assigned';
    }

    const driverKpiCards = [
      {
        id: 'stat-assigned-deliveries',
        label: 'ASSIGNED DELIVERIES',
        value: assignedCount + inProgressCount,
        desc: 'Currently active',
        icon: Send,
        statusType: 'assigned',
      },
      {
        id: 'stat-in-progress',
        label: 'IN PROGRESS',
        value: inProgressCount,
        desc: 'On route',
        icon: Navigation,
        statusType: 'in-progress',
      },
      {
        id: 'stat-completed-today',
        label: 'COMPLETED TODAY',
        value: completedCount,
        desc: 'Successfully delivered',
        icon: CheckCircle2,
        statusType: 'completed',
      },
      {
        id: 'stat-failed',
        label: 'FAILED',
        value: failedCount,
        desc: 'Issues reported',
        icon: AlertTriangle,
        statusType: 'failed',
      },
    ];

    return (
      <div id="dashboard-view" className="driver-dashboard" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>


        {error && (
          <div className="alert alert-error" role="alert" style={{ marginBottom: 0, borderRadius: '10px' }}>
            <AlertCircle size={18} />
            <div style={{ flex: 1 }}>
              <strong>Error:</strong> {error}
            </div>
          </div>
        )}

        {/* 4 Summary KPI Metric Cards in a single row */}
        <section className="driver-kpi-grid" aria-label="Driver Operational Metrics">
          {driverKpiCards.map((card) => (
            <Link
              to="/deliveries"
              key={card.id}
              id={card.id}
              className="driver-kpi-card driver-kpi-card-interactive"
              title={`View ${card.label} in Deliveries`}
              aria-label={`${card.label}: ${card.value}. ${card.desc}. Click to open deliveries.`}
            >
              <div className="driver-kpi-header">
                <span className="driver-kpi-label">{card.label}</span>
                <div className={`driver-kpi-icon-wrap ${card.statusType}`}>
                  <card.icon size={17} />
                </div>
              </div>
              <div className="driver-kpi-body">
                <div className="driver-kpi-value">{card.value}</div>
                <div className="driver-kpi-desc">{card.desc}</div>
              </div>
            </Link>
          ))}
        </section>

        {/* Current Active Delivery Banner (if assigned or in progress) */}
        {currentDelivery && (
          <section className="card driver-current-delivery-card" style={{ marginBottom: 0, borderRadius: '14px', padding: '22px' }}>
            <div className="card-header" style={{ marginBottom: '16px', paddingBottom: '12px', borderBottom: '1px solid var(--theme-border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <h2 className="section-title" style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', fontWeight: 700 }}>
                <Navigation size={16} color="var(--color-primary)" />
                <span>Current Active Delivery</span>
              </h2>
              <span className={`badge badge-${currentDelivery.status?.toLowerCase()}`}>
                {currentDelivery.status}
              </span>
            </div>
            <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: '18px', alignItems: 'center' }}>
              <div>
                <span style={{ fontSize: '11px', color: 'var(--theme-muted)', textTransform: 'uppercase', display: 'block', marginBottom: '3px', fontWeight: 600 }}>Delivery & Package</span>
                <strong>#{currentDelivery.id}</strong> — {currentDelivery.trackingNumber || `Pkg #${currentDelivery.packageId}`}
              </div>
              <div>
                <span style={{ fontSize: '11px', color: 'var(--theme-muted)', textTransform: 'uppercase', display: 'block', marginBottom: '3px', fontWeight: 600 }}>Pickup Location</span>
                <span>{currentDelivery.sourceLocationName || 'Depot'}</span>
              </div>
              <div>
                <span style={{ fontSize: '11px', color: 'var(--theme-muted)', textTransform: 'uppercase', display: 'block', marginBottom: '3px', fontWeight: 600 }}>Destination Location</span>
                <span>{currentDelivery.destinationLocationName || 'Destination'}</span>
              </div>
              <div>
                <span style={{ fontSize: '11px', color: 'var(--theme-muted)', textTransform: 'uppercase', display: 'block', marginBottom: '3px', fontWeight: 600 }}>Vehicle Registration</span>
                <span>{currentDelivery.vehicleRegistrationNumber || '—'}</span>
              </div>
              <div style={{ display: 'flex', gap: '10px', alignItems: 'center', justifyContent: 'flex-start', flexWrap: 'wrap' }}>
                <Link to="/routes" className="btn btn-sm btn-secondary">
                  View Route
                </Link>
                <Link to="/deliveries" className="btn btn-sm btn-primary">
                  Manage Status
                </Link>
              </div>
            </div>
          </section>
        )}

        {/* Recent Assigned Deliveries Panel */}
        <section className="deliveries-panel" style={{ borderRadius: '14px', padding: '24px', backgroundColor: 'var(--theme-surface)', border: '1px solid var(--theme-border)', marginTop: '24px' }}>
          <div className="deliveries-panel-header" style={{ marginBottom: '20px', paddingBottom: '16px', borderBottom: '1px solid var(--theme-border)' }}>
            <div>
              <h2 className="section-title" style={{ margin: '0 0 4px 0', display: 'flex', alignItems: 'center', gap: '10px', fontSize: '16px', fontWeight: 700, color: 'var(--theme-text)' }}>
                <Truck size={20} color="var(--color-primary)" />
                <span>Recent Assigned Deliveries</span>
              </h2>
              <p style={{ margin: 0, fontSize: '13px', color: 'var(--theme-muted)' }}>
                View your recently assigned delivery tasks and their current status.
              </p>
            </div>
          </div>
          
          {deliveries.length === 0 ? (
            <div className="empty-state" style={{ padding: '64px 24px', backgroundColor: 'var(--theme-bg)', borderRadius: '10px', border: '1px dashed var(--theme-border)' }}>
              <div className="empty-state-icon" style={{ borderRadius: '12px', marginBottom: '16px' }}>
                <Truck size={28} />
              </div>
              <h3 style={{ fontSize: '15px', color: 'var(--theme-text)', marginBottom: '8px' }}>No recent assigned deliveries</h3>
              <p style={{ fontSize: '13px' }}>Your assigned deliveries will appear here when an administrator assigns them.</p>
            </div>
          ) : (
            <div className="table-container" style={{ borderRadius: '10px', border: '1px solid var(--theme-border)', overflowX: 'auto' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th style={{ padding: '14px 16px' }}>Delivery ID</th>
                    <th style={{ padding: '14px 16px' }}>Package / Tracking ID</th>
                    <th style={{ padding: '14px 16px' }}>Pickup Location</th>
                    <th style={{ padding: '14px 16px' }}>Destination</th>
                    <th style={{ padding: '14px 16px' }}>Status</th>
                    <th style={{ padding: '14px 16px' }}>Date</th>
                  </tr>
                </thead>
                <tbody>
                  {deliveries.slice(0, 10).map(d => (
                    <tr key={d.id} style={{ transition: 'background-color 0.2s ease' }} className="hover:bg-theme-elevated">
                      <td style={{ padding: '14px 16px' }}><strong>#{d.id}</strong></td>
                      <td style={{ padding: '14px 16px', fontFamily: 'monospace', fontSize: '13px' }}>{d.trackingNumber || `Pkg #${d.packageId}`}</td>
                      <td style={{ padding: '14px 16px' }}>{d.sourceLocationName || '—'}</td>
                      <td style={{ padding: '14px 16px' }}>{d.destinationLocationName || '—'}</td>
                      <td style={{ padding: '14px 16px' }}><span className={`badge badge-${d.status?.toLowerCase()}`}>{d.status}</span></td>
                      <td style={{ padding: '14px 16px', color: 'var(--theme-muted)' }}>{new Date(d.createdAt || d.updatedAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    );
  }

  // --- USER DASHBOARD OVERRIDE ---
  if (user?.role === 'USER') {
    return (
      <div id="dashboard-view" className="user-dashboard">

        {error && (
          <div className="alert alert-error mb-4" role="alert">
            <AlertCircle size={18} />
            <div style={{ flex: 1 }}>
              <strong>Error:</strong> {error}
            </div>
          </div>
        )}

        {/* 2. PERSONAL SUMMARY CARDS */}
        <section className="stats-grid mb-4">
          <div className="stat-card indigo">
            <div className="stat-card-header">
              <span className="stat-card-title">My Packages</span>
              <div className="stat-card-icon indigo"><Package size={20} /></div>
            </div>
            <div className="stat-card-value">{stats?.totalPackages || 0}</div>
            <div className="stat-card-subtitle">Total registered packages</div>
          </div>
          
          <div className="stat-card cyan">
            <div className="stat-card-header">
              <span className="stat-card-title">Pending Packages</span>
              <div className="stat-card-icon cyan"><Clock size={20} /></div>
            </div>
            <div className="stat-card-value">{pendingPackages}</div>
            <div className="stat-card-subtitle">Awaiting dispatch</div>
          </div>

          <div className="stat-card amber">
            <div className="stat-card-header">
              <span className="stat-card-title">Active Deliveries</span>
              <div className="stat-card-icon amber"><Navigation size={20} /></div>
            </div>
            <div className="stat-card-value">{activeDeliveries}</div>
            <div className="stat-card-subtitle">Currently in transit</div>
          </div>

          <div className="stat-card green">
            <div className="stat-card-header">
              <span className="stat-card-title">Delivered</span>
              <div className="stat-card-icon green"><CheckCircle2 size={20} /></div>
            </div>
            <div className="stat-card-value">{completedCount}</div>
            <div className="stat-card-subtitle">Successfully completed</div>
          </div>
        </section>

        {/* 4. DELIVERY STATUS */}
        <section className="operational-section mb-4">
          <h2 className="section-title"><Navigation size={18} /> Delivery Lifecycle</h2>
          <div className="status-pipeline-grid">
            {[
              { label: 'Created', count: createdCount, color: '#f59e0b', icon: Clock },
              { label: 'Assigned', count: assignedCount, color: '#6366f1', icon: Send },
              { label: 'In Progress', count: inProgressCount, color: '#06b6d4', icon: Navigation },
              { label: 'Completed', count: completedCount, color: '#10b981', icon: CheckCircle2 }
            ].map(st => (
              <div className="status-pipeline-card" key={st.label}>
                <div className="status-card-top">
                  <span className="badge" style={{ backgroundColor: st.color + '20', color: st.color, border: '1px solid ' + st.color }}>{st.label}</span>
                  <st.icon size={14} color={st.color} />
                </div>
                <div className="status-count">{st.count}</div>
              </div>
            ))}
          </div>
        </section>

        {/* 3. RECENT DELIVERIES */}
        <section className="deliveries-panel">
          <div className="deliveries-panel-header">
            <div>
              <h2 className="section-title"><Package size={18} /> Recent Deliveries</h2>
            </div>
          </div>
          
          {userPackages.length === 0 ? (
            <div className="empty-state" style={{ padding: '40px 20px' }}>
              <div className="empty-state-icon"><Package size={32} /></div>
              <h3>No recent deliveries</h3>
              <p>Your delivery requests will appear here once you create a package.</p>
            </div>
          ) : (
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Tracking ID</th>
                    <th>Pickup</th>
                    <th>Destination</th>
                    <th>Status</th>
                    <th>Date</th>
                  </tr>
                </thead>
                <tbody>
                  {userPackages.slice(0, 5).map(pkg => (
                    <tr key={pkg.id}>
                      <td style={{ fontFamily: 'monospace', fontSize: '13px' }}><strong>{pkg.trackingNumber}</strong></td>
                      <td>{pkg.sourceLocationName || pkg.pickupLocation || '—'}</td>
                      <td>{pkg.destinationLocationName || pkg.deliveryLocation || '—'}</td>
                      <td><span className={`badge badge-${pkg.status?.toLowerCase()}`}>{pkg.status}</span></td>
                      <td>{new Date(pkg.createdAt || pkg.updatedAt).toLocaleDateString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    );
  }

  return (
    <div id="dashboard-view">
      {/* 2. TOP HEADER */}
      <header className="dashboard-hero">
        <div className="dashboard-hero-content">
          <h1 className="dashboard-hero-title">
            Route Optimization Dashboard
          </h1>
          <div className="dashboard-hero-subtitle">
            <span
              className={`telemetry-status-pill ${
                error ? 'error' : isDashboardEmpty ? 'idle' : 'active'
              }`}
            >
              <span className="status-pulse-dot" />
              {error ? 'Telemetry Error' : isDashboardEmpty ? 'System Standby' : 'Live Telemetry'}
            </span>
            <span>{getOperationalStatusSubtitle()}</span>
          </div>
        </div>

        <div className="dashboard-hero-meta">
          <div className="user-role-badge">
            <Shield size={16} color="var(--accent-primary)" />
            <span>{user?.username || 'Operator'}</span>
            <span className="user-role-tag">{user?.role || 'User'}</span>
          </div>

          <button
            className="btn-refresh-telemetry"
            onClick={() => fetchDashboardData(true)}
            disabled={refreshing}
            id="refresh-dashboard-btn"
            title="Refresh operational telemetry"
          >
            <RefreshCw size={14} className={refreshing ? 'spinning' : ''} />
            <span>{refreshing ? 'Syncing...' : 'Refresh'}</span>
          </button>
        </div>
      </header>

      {/* Global Error Banner (No mock fallback) */}
      {error && (
        <div className="alert alert-error mb-4" role="alert">
          <AlertCircle size={18} />
          <div style={{ flex: 1 }}>
            <strong>Operational Error:</strong> {error}
          </div>
          <button
            className="btn btn-sm btn-secondary"
            onClick={() => fetchDashboardData(true)}
            style={{ marginLeft: 12 }}
          >
            Retry Connection
          </button>
        </div>
      )}

      {/* 3. SUMMARY CARDS */}
      <section className="stats-grid" aria-label="System Metrics">
        {summaryCards.map((card) => (
          <Link
            to={card.to}
            className={`stat-card stat-card-interactive ${card.color}`}
            key={card.id}
            id={card.id}
            title={card.tooltip}
            aria-label={`${card.title}: ${card.value}. ${card.subtitle}. Click to open ${card.title}.`}
          >
            <div className="stat-card-header">
              <span className="stat-card-title">{card.title}</span>
              <div className={`stat-card-icon ${card.color}`}>
                <card.icon size={20} />
              </div>
            </div>
            <div className="stat-card-value">{card.value}</div>
            <div className="stat-card-footer">
              <span className="stat-card-subtitle">{card.subtitle}</span>
              <span className="stat-card-action-indicator" aria-hidden="true">
                <ChevronRight size={14} />
              </span>
            </div>
          </Link>
        ))}
      </section>

      {/* 8. EMPTY DATABASE ONBOARDING BANNER */}
      {isDashboardEmpty && !error && (
        <section className="card mb-4" id="dashboard-empty-banner">
          <div className="empty-state">
            <div className="empty-state-icon">
              <Package size={36} />
            </div>
            <h3>Welcome to Package Delivery Route Optimizer</h3>
            <p>
              Your database has zero records. To begin operations and generate Dijkstra-optimized routes, configure your network below:
            </p>
            <div className="workflow-steps-grid w-full">
              <div className="workflow-step-card">
                <div>
                  <div className="workflow-step-num">1</div>
                  <h4>Network Infrastructure</h4>
                  <p className="text-sm text-muted">Create Locations & Connect Road Edges with distance values.</p>
                </div>
                <Link to="/locations" className="btn btn-sm btn-secondary mt-4">
                  Manage Locations <ChevronRight size={14} />
                </Link>
              </div>

              <div className="workflow-step-card">
                <div>
                  <div className="workflow-step-num">2</div>
                  <h4>Fleet Registration</h4>
                  <p className="text-sm text-muted">Register Available Drivers and Transport Vehicles.</p>
                </div>
                <div className="flex gap-2 mt-4">
                  <Link to="/drivers" className="btn btn-sm btn-secondary" style={{ flex: 1 }}>
                    Drivers
                  </Link>
                  <Link to="/vehicles" className="btn btn-sm btn-secondary" style={{ flex: 1 }}>
                    Vehicles
                  </Link>
                </div>
              </div>

              <div className="workflow-step-card">
                <div>
                  <div className="workflow-step-num">3</div>
                  <h4>Package Dispatch</h4>
                  <p className="text-sm text-muted">Register customer packages and trigger delivery workflow.</p>
                </div>
                <Link to="/packages" className="btn btn-sm btn-secondary mt-4">
                  Packages <ChevronRight size={14} />
                </Link>
              </div>

              <div className="workflow-step-card">
                <div>
                  <div className="workflow-step-num">4</div>
                  <h4>Dijkstra Optimization</h4>
                  <p className="text-sm text-muted">Compute mathematically shortest transit stops between points.</p>
                </div>
                <Link to="/routes" className="btn btn-sm btn-primary mt-4">
                  Optimizer <ChevronRight size={14} />
                </Link>
              </div>
            </div>
          </div>
        </section>
      )}

      {/* 4. DELIVERY / OPERATIONAL STATUS BREAKDOWN */}
      <section className="operational-section" aria-label="Operational Statuses">
        <div className="section-heading-row">
          <div>
            <h2 className="section-title">
              <Navigation size={18} color="var(--accent-secondary)" />
              Operational Delivery Pipeline
            </h2>
            <p className="section-subtitle">Real-time status breakdown across all lifecycle dispatches</p>
          </div>
          <span className="text-sm text-muted">
            Total Dispatches: <strong>{totalDeliveries}</strong>
          </span>
        </div>

        <div className="status-pipeline-grid">
          {operationalStatuses.map((st) => {
            const pct = totalDeliveries > 0
              ? Math.round((st.count / totalDeliveries) * 100)
              : 0;

            return (
              <div className="status-pipeline-card" key={st.key} id={`status-card-${st.key.toLowerCase()}`}>
                <div className="status-card-top">
                  <span className={`badge ${st.badgeClass}`}>
                    {st.label}
                  </span>
                  {st.pulsing ? (
                    <span className="status-pulse-dot" />
                  ) : (
                    <st.icon size={14} color="var(--text-muted)" />
                  )}
                </div>
                <div className="status-count">{st.count}</div>
                <div className="status-progress-track">
                  <div
                    className="status-progress-bar"
                    style={{
                      width: `${pct}%`,
                      backgroundColor: st.color
                    }}
                  />
                </div>
                <div className="status-percentage-caption">
                  <span>{st.desc}</span>
                  <span>{pct}%</span>
                </div>
              </div>
            );
          })}
        </div>
      </section>

      {/* 5. ROUTE OPTIMIZATION SECTION & 6. MAP/ROUTE VISUAL AREA */}
      <section className="route-optimizer-panel" aria-label="Route Optimization Engine">
        <div className="route-optimizer-panel-header">
          <div className="flex items-center gap-3">
            <Compass size={22} color="var(--accent-primary)" />
            <div>
              <h2 className="section-title" style={{ margin: 0 }}>
                Persisted Route Optimization
              </h2>
              <span className="text-sm text-muted">
                Mathematical shortest path computed via graph edges
              </span>
            </div>
          </div>

          <div className="flex items-center gap-3 flex-wrap">
            {/* Clearly display Algorithm: DIJKSTRA */}
            <div className="route-algo-pill" id="dashboard-algo-pill">
              <Route size={15} />
              <span>Algorithm: DIJKSTRA</span>
            </div>

            {/* Delivery Route Selector if deliveries exist */}
            {deliveries.length > 0 && (
              <div className="flex items-center gap-2">
                <label htmlFor="route-delivery-select" className="text-sm text-muted" style={{ whiteSpace: 'nowrap' }}>
                  Delivery:
                </label>
                <select
                  id="route-delivery-select"
                  className="route-delivery-select"
                  value={selectedDeliveryId || ''}
                  onChange={(e) => handleSelectDelivery(e.target.value)}
                  disabled={routeLoading}
                >
                  {deliveries.map((d) => (
                    <option key={d.id} value={d.id}>
                      #{d.id} — {d.trackingNumber || `Pkg #${d.packageId}`} ({d.status})
                    </option>
                  ))}
                </select>
              </div>
            )}

            <Link to="/routes" className="btn btn-sm btn-secondary" id="open-optimizer-link">
              Open Optimizer <ChevronRight size={14} />
            </Link>
          </div>
        </div>

        {/* Route metadata strip (Real backend fields only) */}
        {routeData && (
          <div className="route-meta-strip">
            <div className="route-meta-box">
              <div className="route-meta-box-label">Origin Location</div>
              <div className="route-meta-box-val">{routeData.sourceLocationName || '—'}</div>
            </div>

            <div className="route-meta-box">
              <div className="route-meta-box-label">Destination</div>
              <div className="route-meta-box-val">{routeData.destinationLocationName || '—'}</div>
            </div>

            <div className="route-meta-box">
              <div className="route-meta-box-label">Calculated Distance</div>
              <div className="route-meta-box-val">
                {routeData.totalDistance != null ? `${routeData.totalDistance.toFixed(1)} km` : '—'}
              </div>
            </div>

            <div className="route-meta-box">
              <div className="route-meta-box-label">Estimated Transit</div>
              <div className="route-meta-box-val">
                {routeData.estimatedTime != null ? `${routeData.estimatedTime.toFixed(1)} min` : '—'}
              </div>
            </div>

            <div className="route-meta-box">
              <div className="route-meta-box-label">Route Version</div>
              <div className="route-meta-box-val">v{routeData.versionNumber || 1}</div>
            </div>

            <div className="route-meta-box">
              <div className="route-meta-box-label">Lifecycle Status</div>
              <div className="route-meta-box-val">
                {routeData.isCurrent ? 'Current Active' : 'Archived'}
              </div>
            </div>
          </div>
        )}

        {/* Visual Route Stops Area */}
        <div className="route-stops-container">
          {routeLoading ? (
            <div className="loading-spinner" style={{ padding: '30px' }}>
              <div className="spinner" />
            </div>
          ) : routeData && Array.isArray(routeData.stops) && routeData.stops.length > 0 ? (
            <div>
              <div className="flex items-center justify-between mb-4">
                <span className="text-sm text-muted">
                  Stop Sequence Pipeline ({routeData.stops.length} Total Nodes)
                </span>
                <span className="text-sm" style={{ color: 'var(--accent-secondary)' }}>
                  Source → Intermediate Stops → Destination
                </span>
              </div>

              {/* Clean Horizontal/Responsive Stops Track */}
              <div className="route-pipeline-track" tabIndex={0} aria-label="Route stop nodes">
                {routeData.stops.map((stop, idx) => {
                  const isFirst = idx === 0;
                  const isLast = idx === routeData.stops.length - 1;
                  const stopTypeClass = isFirst
                    ? 'source'
                    : isLast
                    ? 'destination'
                    : 'intermediate';

                  const badgeLabel = isFirst
                    ? 'SOURCE ORIGIN'
                    : isLast
                    ? 'DESTINATION'
                    : `INTERMEDIATE STOP #${stop.stopOrder || idx + 1}`;

                  return (
                    <div key={stop.id || idx} style={{ display: 'flex', alignItems: 'stretch' }}>
                      <div className={`route-stop-card ${stopTypeClass}`}>
                        <div className="flex items-center justify-between">
                          <span className="route-stop-type-badge">{badgeLabel}</span>
                          {isFirst ? (
                            <MapPin size={16} color="var(--accent-success)" />
                          ) : isLast ? (
                            <Flag size={16} color="var(--accent-danger)" />
                          ) : (
                            <Navigation size={14} color="var(--accent-secondary)" />
                          )}
                        </div>

                        <div className="route-stop-name">{stop.locationName || `Location #${stop.locationId}`}</div>

                        {(stop.latitude != null || stop.longitude != null) && (
                          <div className="route-stop-coord">
                            GPS: {stop.latitude?.toFixed(4)}, {stop.longitude?.toFixed(4)}
                          </div>
                        )}

                        {stop.estimatedArrivalTime && (
                          <div className="text-sm text-muted mt-4">
                            ETA: {new Date(stop.estimatedArrivalTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </div>
                        )}
                      </div>

                      {!isLast && (
                        <div className="route-stop-connector-arrow">
                          <ArrowRight size={20} />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          ) : routeError ? (
            <div className="empty-state" style={{ padding: '30px 20px' }}>
              <div className="empty-state-icon">
                <Route size={28} />
              </div>
              <h3 style={{ fontSize: '1rem' }}>No Route Persisted For Selected Delivery</h3>
              <p style={{ maxWidth: '480px' }}>{routeError}</p>
              <Link to="/routes" className="btn btn-sm btn-primary mt-4">
                Calculate in Route Optimizer
              </Link>
            </div>
          ) : deliveries.length === 0 ? (
            <div className="empty-state" style={{ padding: '30px 20px' }}>
              <div className="empty-state-icon">
                <Route size={28} />
              </div>
              <h3 style={{ fontSize: '1rem' }}>No Delivery Routes Available</h3>
              <p>
                There are currently no deliveries in the database. When deliveries are scheduled and routes optimized with Dijkstra, stops will appear here.
              </p>
            </div>
          ) : (
            <div className="empty-state" style={{ padding: '30px 20px' }}>
              <div className="empty-state-icon">
                <Route size={28} />
              </div>
              <h3 style={{ fontSize: '1rem' }}>Select a Delivery to View Route Stops</h3>
              <p>Choose any delivery from the dropdown above to inspect its persisted Dijkstra path.</p>
            </div>
          )}
        </div>
      </section>

      {/* 7. RECENT DELIVERIES SECTION */}
      <section className="deliveries-panel" aria-label="Recent Deliveries">
        <div className="deliveries-panel-header">
          <div>
            <h2 className="section-title" style={{ margin: 0 }}>
              <Send size={18} color="var(--accent-primary)" />
              Recent Deliveries
            </h2>
            <span className="section-subtitle">Real-time status of scheduled dispatches</span>
          </div>

          {deliveries.length > 0 && (
            <Link to="/deliveries" className="btn btn-sm btn-secondary" id="view-all-deliveries-link">
              View All ({deliveries.length}) <ChevronRight size={14} />
            </Link>
          )}
        </div>

        {deliveries.length === 0 ? (
          <div className="empty-state" style={{ padding: '40px 20px' }}>
            <div className="empty-state-icon">
              <Send size={32} />
            </div>
            <h3>No deliveries available.</h3>
            <p>
              Create a delivery in the Deliveries module to begin tracking dispatches and calculating routes.
            </p>
            <Link to="/deliveries" className="btn btn-sm btn-primary mt-4">
              Go to Deliveries
            </Link>
          </div>
        ) : (
          <div className="table-container">
            <table className="data-table" aria-label="Deliveries table">
              <thead>
                <tr>
                  <th scope="col">ID / Package</th>
                  <th scope="col">Source</th>
                  <th scope="col">Destination</th>
                  <th scope="col">Driver</th>
                  <th scope="col">Vehicle</th>
                  <th scope="col">Status</th>
                  <th scope="col">Timestamp</th>
                  <th scope="col" style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {deliveries.slice(0, 8).map((d) => {
                  const badgeClass = `badge-${d.status?.toLowerCase() || 'pending'}`;
                  const relevantTime = d.completedAt
                    ? new Date(d.completedAt).toLocaleString()
                    : d.updatedAt
                    ? new Date(d.updatedAt).toLocaleString()
                    : d.createdAt
                    ? new Date(d.createdAt).toLocaleString()
                    : '—';

                  const isSelected = selectedDeliveryId === d.id;

                  return (
                    <tr key={d.id}>
                      <td>
                        <strong>#{d.id}</strong>
                        <div className="text-sm text-muted">
                          {d.trackingNumber || `Pkg #${d.packageId}`}
                        </div>
                      </td>
                      <td>{d.sourceLocationName || '—'}</td>
                      <td>{d.destinationLocationName || '—'}</td>
                      <td>
                        {d.driverName ? (
                          <span>{d.driverName}</span>
                        ) : (
                          <span className="text-muted">Unassigned</span>
                        )}
                      </td>
                      <td>
                        {d.vehicleRegistrationNumber ? (
                          <span style={{ fontFamily: 'monospace' }}>{d.vehicleRegistrationNumber}</span>
                        ) : (
                          <span className="text-muted">Unassigned</span>
                        )}
                      </td>
                      <td>
                        <span className={`badge ${badgeClass}`}>{d.status}</span>
                      </td>
                      <td className="text-sm text-muted">{relevantTime}</td>
                      <td style={{ textAlign: 'right' }}>
                        <button
                          className={`btn-inspect-route ${isSelected ? 'active' : ''}`}
                          onClick={() => handleSelectDelivery(d.id)}
                          title="Inspect persisted route for this delivery"
                        >
                          {isSelected ? 'Viewing Route' : 'Inspect Route'}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
