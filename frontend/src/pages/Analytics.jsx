import { useState, useEffect } from 'react';
import api from '../api/axios';
import { BarChart3, TrendingUp, Package, Send, Users, Truck, Route } from 'lucide-react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
} from 'recharts';
import PageHeader from '../components/ui/PageHeader';
import Card from '../components/ui/Card';
import EmptyState from '../components/ui/EmptyState';
import LoadingState from '../components/ui/LoadingState';
import ErrorState, { parseApiError } from '../components/ui/ErrorState';

// Curated theme palette (accessible, high-contrast)
const FLAT_COLORS = ['#10B981', '#059669', '#3B82F6', '#F59E0B', '#EF4444', '#8B5CF6', '#06B6D4'];

export default function Analytics() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState('');

  const fetchAnalytics = () => {
    setLoading(true);
    setFetchError('');
    api.get('/analytics')
      .then((res) => setData(res.data))
      .catch((err) => setFetchError(parseApiError(err, 'Failed to load telemetry analytics')))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchAnalytics();
  }, []);

  if (loading) {
    return <LoadingState message="Loading operational analytics..." />;
  }

  if (fetchError) {
    return (
      <div>
        <PageHeader
          title="Operational Analytics"
          subtitle="Real-time telemetry and throughput metrics from backend delivery events"
        />
        <ErrorState error={fetchError} onRetry={fetchAnalytics} />
      </div>
    );
  }

  // Strict check on real backend metrics
  const hasData =
    data &&
    (Object.values(data.packageStats?.packagesByStatus || {}).some((v) => v > 0) ||
      Object.values(data.deliveryStats?.deliveriesByStatus || {}).some((v) => v > 0) ||
      (data.packageStats?.totalPackages ?? 0) > 0 ||
      (data.deliveryStats?.totalDeliveries ?? 0) > 0);

  if (!hasData) {
    return (
      <div>
        <PageHeader
          title="Operational Analytics"
          subtitle="Real-time telemetry and throughput metrics from backend delivery events"
        />
        <Card>
          <EmptyState
            icon={BarChart3}
            title="No analytics data available"
            description="Operational charts and metrics will populate once packages, dispatches, and routes are recorded in the database."
          />
        </Card>
      </div>
    );
  }

  const toChartData = (map) =>
    Object.entries(map || {})
      .filter(([_, v]) => v > 0)
      .map(([name, value]) => ({ name: name.replace(/_/g, ' '), value }));

  const packageStatusData = toChartData(data.packageStats?.packagesByStatus);
  const deliveryStatusData = toChartData(data.deliveryStats?.deliveriesByStatus);
  const algorithmData = toChartData(data.routeStats?.algorithmsUsed);

  const driverData = [];
  if (data.driverUtilization) {
    const du = data.driverUtilization;
    if (du.availableDrivers > 0) driverData.push({ name: 'Available', value: du.availableDrivers });
    if (du.onDeliveryDrivers > 0) driverData.push({ name: 'On Delivery', value: du.onDeliveryDrivers });
    if (du.offDutyDrivers > 0) driverData.push({ name: 'Off Duty', value: du.offDutyDrivers });
  }

  const vehicleData = [];
  if (data.vehicleUtilization) {
    const vu = data.vehicleUtilization;
    if (vu.availableVehicles > 0) vehicleData.push({ name: 'Available', value: vu.availableVehicles });
    if (vu.inUseVehicles > 0) vehicleData.push({ name: 'In Use', value: vu.inUseVehicles });
    if (vu.maintenanceVehicles > 0) vehicleData.push({ name: 'Maintenance', value: vu.maintenanceVehicles });
    if (vu.offlineVehicles > 0) vehicleData.push({ name: 'Offline', value: vu.offlineVehicles });
  }

  const customTooltipStyle = {
    backgroundColor: 'var(--theme-surface)',
    border: '1px solid var(--theme-border)',
    borderRadius: 'var(--radius-input)',
    color: 'var(--theme-text)',
    fontSize: '11px',
    boxShadow: 'none',
  };

  return (
    <div>
      <PageHeader
        title="Operational Analytics"
        subtitle="Real-time telemetry and throughput metrics from backend delivery events"
      />

      {/* Top Level Metric KPIs */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-card-header">
            <span className="stat-card-title">Total Packages</span>
            <div className="stat-card-icon">
              <Package size={16} />
            </div>
          </div>
          <div className="stat-card-value">{data.packageStats?.totalPackages ?? 0}</div>
          <div className="stat-card-subtitle">
            {data.packageStats?.inDeliveryPackages ?? 0} in delivery • {data.packageStats?.pendingPackages ?? 0} pending
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-card-header">
            <span className="stat-card-title">Total Deliveries</span>
            <div className="stat-card-icon">
              <Send size={16} />
            </div>
          </div>
          <div className="stat-card-value">{data.deliveryStats?.totalDeliveries ?? 0}</div>
          <div className="stat-card-subtitle">
            {data.deliveryStats?.completedDeliveries ?? 0} completed
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-card-header">
            <span className="stat-card-title">Completion Rate</span>
            <div className="stat-card-icon">
              <TrendingUp size={16} />
            </div>
          </div>
          <div className="stat-card-value">
            {data.deliveryStats?.completionRate != null
              ? `${(data.deliveryStats.completionRate * 100).toFixed(1)}%`
              : '—'}
          </div>
          <div className="stat-card-subtitle">Completed / Total ratio</div>
        </div>

        <div className="stat-card">
          <div className="stat-card-header">
            <span className="stat-card-title">Optimized Routes</span>
            <div className="stat-card-icon">
              <Route size={16} />
            </div>
          </div>
          <div className="stat-card-value">{data.routeStats?.totalRoutes ?? 0}</div>
          <div className="stat-card-subtitle">
            {data.routeStats?.totalDistanceKm != null
              ? `${data.routeStats.totalDistanceKm.toFixed(1)} km total network`
              : 'Dijkstra shortest paths'}
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-card-header">
            <span className="stat-card-title">Driver Utilization</span>
            <div className="stat-card-icon">
              <Users size={16} />
            </div>
          </div>
          <div className="stat-card-value">
            {data.driverUtilization?.utilizationRate != null
              ? `${(data.driverUtilization.utilizationRate * 100).toFixed(1)}%`
              : '—'}
          </div>
          <div className="stat-card-subtitle">
            {data.driverUtilization?.availableDrivers ?? 0} available drivers
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-card-header">
            <span className="stat-card-title">Vehicle Fleet</span>
            <div className="stat-card-icon">
              <Truck size={16} />
            </div>
          </div>
          <div className="stat-card-value">
            {data.vehicleUtilization?.totalVehicles ?? 0}
          </div>
          <div className="stat-card-subtitle">
            {data.vehicleUtilization?.availableVehicles ?? 0} available • {data.vehicleUtilization?.inUseVehicles ?? 0} in transit
          </div>
        </div>
      </div>

      {/* Charts Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(380px, 1fr))', gap: 16 }}>
        {/* Packages by Status */}
        {packageStatusData.length > 0 && (
          <Card title="Package Distribution by Status">
            <div style={{ height: 260, width: '100%' }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={packageStatusData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--theme-border)" />
                  <XAxis dataKey="name" stroke="var(--theme-muted)" fontSize={11} />
                  <YAxis stroke="var(--theme-muted)" fontSize={11} />
                  <Tooltip contentStyle={customTooltipStyle} />
                  <Bar dataKey="value" fill="var(--color-primary)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Card>
        )}

        {/* Deliveries by Status */}
        {deliveryStatusData.length > 0 && (
          <Card title="Delivery Operations Breakdown">
            <div style={{ height: 260, width: '100%' }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={deliveryStatusData}
                    cx="50%"
                    cy="50%"
                    outerRadius={80}
                    dataKey="value"
                    label={({ name, percent }) => `${name} (${(percent * 100).toFixed(0)}%)`}
                    fontSize={11}
                  >
                    {deliveryStatusData.map((_, i) => (
                      <Cell key={i} fill={FLAT_COLORS[i % FLAT_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={customTooltipStyle} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </Card>
        )}

        {/* Driver Availability */}
        {driverData.length > 0 && (
          <Card title="Driver Workforce Status">
            <div style={{ height: 260, width: '100%' }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={driverData}
                    cx="50%"
                    cy="50%"
                    outerRadius={80}
                    dataKey="value"
                    label={({ name, value }) => `${name}: ${value}`}
                    fontSize={11}
                  >
                    {driverData.map((_, i) => (
                      <Cell key={i} fill={FLAT_COLORS[(i + 2) % FLAT_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={customTooltipStyle} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </Card>
        )}

        {/* Vehicle Utilization */}
        {vehicleData.length > 0 && (
          <Card title="Fleet Vehicle Allocation">
            <div style={{ height: 260, width: '100%' }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={vehicleData}
                    cx="50%"
                    cy="50%"
                    outerRadius={80}
                    dataKey="value"
                    label={({ name, value }) => `${name}: ${value}`}
                    fontSize={11}
                  >
                    {vehicleData.map((_, i) => (
                      <Cell key={i} fill={FLAT_COLORS[(i + 4) % FLAT_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={customTooltipStyle} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </Card>
        )}

        {/* Algorithms Used */}
        {algorithmData.length > 0 && (
          <Card title="Route Optimization Algorithms">
            <div style={{ height: 260, width: '100%' }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={algorithmData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--theme-border)" />
                  <XAxis dataKey="name" stroke="var(--theme-muted)" fontSize={11} />
                  <YAxis stroke="var(--theme-muted)" fontSize={11} />
                  <Tooltip contentStyle={customTooltipStyle} />
                  <Bar dataKey="value" fill="var(--color-accent)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Card>
        )}
      </div>
    </div>
  );
}
