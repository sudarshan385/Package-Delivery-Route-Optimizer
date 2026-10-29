import { useState, useEffect } from 'react';
import api from '../api/axios';
import { FileText, Download, Route, Send } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import { StatusBadge } from '../components/ui/Badge';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '../components/ui/Table';
import EmptyState from '../components/ui/EmptyState';
import LoadingState from '../components/ui/LoadingState';
import ErrorState, { parseApiError } from '../components/ui/ErrorState';

export default function Reports() {
  const [deliveries, setDeliveries] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [downloadingType, setDownloadingType] = useState(null);
  const [fetchError, setFetchError] = useState('');

  const fetchReportsData = () => {
    setLoading(true);
    setFetchError('');
    Promise.all([api.get('/deliveries'), api.get('/dashboard')])
      .then(([dRes, sRes]) => {
        setDeliveries(Array.isArray(dRes.data) ? dRes.data : dRes.data.content || []);
        setStats(sRes.data);
      })
      .catch((err) => {
        setFetchError(parseApiError(err, 'Failed to load report operational data'));
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchReportsData();
  }, []);

  const downloadCSV = async (type) => {
    setDownloadingType(type);
    try {
      const url = type === 'deliveries' ? '/reports/deliveries/csv' : '/reports/routes/csv';
      const filename = type === 'deliveries' ? 'deliveries_report.csv' : 'routes_report.csv';
      const res = await api.get(url, { responseType: 'blob' });
      const blob = new Blob([res.data], { type: 'text/csv' });
      const downloadUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = downloadUrl;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(downloadUrl);
    } catch (err) {
      alert(parseApiError(err, 'Failed to download server-generated CSV report'));
    } finally {
      setDownloadingType(null);
    }
  };

  if (loading) {
    return <LoadingState message="Loading reports and summary telemetry..." />;
  }

  const hasData = deliveries.length > 0 || (stats?.totalRoutes ?? 0) > 0 || (stats?.totalPackages ?? 0) > 0;

  return (
    <div>
      <PageHeader
        title="System Reports & Data Exports"
        subtitle="Export cryptographically safe CSV records generated directly from PostgreSQL telemetry"
      />

      {fetchError && (
        <ErrorState
          error={fetchError}
          onRetry={fetchReportsData}
          className="mb-4"
        />
      )}

      {!hasData ? (
        <Card>
          <EmptyState
            icon={FileText}
            title="No report data available"
            description="Reports will be generated from real delivery dispatches and Dijkstra route records once transactions are recorded."
          />
        </Card>
      ) : (
        <>
          {/* Export Action Card */}
          <Card
            title="Download CSV Exports"
            subtitle="Server-generated operational dumps with formula injection sanitization"
            className="mb-6"
          >
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <Button
                variant="primary"
                icon={Download}
                onClick={() => downloadCSV('deliveries')}
                disabled={Boolean(downloadingType)}
                loading={downloadingType === 'deliveries'}
                id="export-deliveries"
              >
                Download Delivery Report (CSV)
              </Button>
              <Button
                variant="secondary"
                icon={Download}
                onClick={() => downloadCSV('routes')}
                disabled={Boolean(downloadingType)}
                loading={downloadingType === 'routes'}
                id="export-routes"
              >
                Download Route Report (CSV)
              </Button>
            </div>
          </Card>

          {/* Operational Metrics Strip */}
          {stats && (
            <div className="stats-grid mb-6">
              <div className="stat-card">
                <div className="stat-card-title">Packages Reconciled</div>
                <div className="stat-card-value">{stats.totalPackages ?? 0}</div>
              </div>
              <div className="stat-card">
                <div className="stat-card-title">Total Dispatches</div>
                <div className="stat-card-value">{stats.totalDeliveries ?? 0}</div>
              </div>
              <div className="stat-card">
                <div className="stat-card-title">Completed Orders</div>
                <div className="stat-card-value">{stats.completedDeliveries ?? 0}</div>
              </div>
              <div className="stat-card">
                <div className="stat-card-title">Routes Computed</div>
                <div className="stat-card-value">{stats.totalRoutes ?? 0}</div>
              </div>
            </div>
          )}

          {/* Delivery Record Preview Table */}
          <Card
            title={`Delivery Audit Preview (${deliveries.length} Records)`}
            subtitle="First 20 operational dispatch records eligible for export"
          >
            {deliveries.length === 0 ? (
              <EmptyState
                icon={Send}
                title="No delivery records"
                description="Create dispatches in the Deliveries module to populate report rows."
              />
            ) : (
              <Table>
                <TableHeader>
                  <tr>
                    <TableHead>ID</TableHead>
                    <TableHead>Tracking #</TableHead>
                    <TableHead>Pickup Node</TableHead>
                    <TableHead>Delivery Node</TableHead>
                    <TableHead>Assigned Driver</TableHead>
                    <TableHead>Assigned Vehicle</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Recorded Date</TableHead>
                  </tr>
                </TableHeader>
                <TableBody>
                  {deliveries.slice(0, 20).map((d) => (
                    <TableRow key={d.id}>
                      <TableCell style={{ fontWeight: 700 }}>#{d.id}</TableCell>
                      <TableCell style={{ fontFamily: 'monospace', fontWeight: 600 }}>
                        {d.trackingNumber || `PKG-${d.packageId}`}
                      </TableCell>
                      <TableCell>{d.sourceLocationName || '—'}</TableCell>
                      <TableCell>{d.destinationLocationName || '—'}</TableCell>
                      <TableCell>{d.driverName || '—'}</TableCell>
                      <TableCell style={{ fontFamily: 'monospace' }}>
                        {d.vehicleRegistrationNumber || '—'}
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={d.status} />
                      </TableCell>
                      <TableCell style={{ fontSize: 11, color: 'var(--theme-muted)' }}>
                        {d.createdAt ? new Date(d.createdAt).toLocaleDateString() : '—'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
