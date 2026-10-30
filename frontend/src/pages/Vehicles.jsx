import { useState, useEffect } from 'react';
import api from '../api/axios';
import { Truck, Plus, Edit2, Trash2 } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Select from '../components/ui/Select';
import { StatusBadge } from '../components/ui/Badge';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '../components/ui/Table';
import Modal from '../components/ui/Modal';
import ConfirmDialog from '../components/ui/ConfirmDialog';
import EmptyState from '../components/ui/EmptyState';
import LoadingState from '../components/ui/LoadingState';
import ErrorState, { parseApiError } from '../components/ui/ErrorState';

export default function Vehicles() {
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [error, setError] = useState('');
  const [fetchError, setFetchError] = useState('');

  const [form, setForm] = useState({
    registrationNumber: '',
    vehicleType: 'VAN',
    capacity: '',
    status: 'AVAILABLE',
  });

  const fetchVehicles = () => {
    setLoading(true);
    setFetchError('');
    api.get('/vehicles', { params: { size: 200 } })
      .then((res) => setVehicles(res.data.content || []))
      .catch((err) => setFetchError(parseApiError(err, 'Failed to load vehicles')))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchVehicles();
  }, []);

  const openCreate = () => {
    setForm({ registrationNumber: '', vehicleType: 'VAN', capacity: '', status: 'AVAILABLE' });
    setEditing(null);
    setError('');
    setShowModal(true);
  };

  const openEdit = (v) => {
    setForm({
      registrationNumber: v.registrationNumber,
      vehicleType: v.vehicleType || 'VAN',
      capacity: v.capacity != null ? v.capacity : '',
      status: v.status || 'AVAILABLE',
    });
    setEditing(v);
    setError('');
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    const reg = form.registrationNumber.trim();
    if (!reg) {
      setError('Registration number is required.');
      return;
    }

    const cap = Number(form.capacity);
    if (!form.capacity || isNaN(cap) || cap <= 0) {
      setError('Vehicle payload capacity must be greater than zero.');
      return;
    }

    if (editing && (editing.status === 'IN_USE' || editing.inActiveDelivery) && form.status !== 'IN_USE') {
      setError('Cannot change vehicle status while vehicle is in active delivery.');
      return;
    }

    try {
      const payload = {
        registrationNumber: reg,
        vehicleType: form.vehicleType,
        capacity: cap,
        status: form.status || 'AVAILABLE',
      };

      if (editing) {
        await api.put(`/vehicles/${editing.id}`, payload);
      } else {
        await api.post('/vehicles', payload);
      }
      setShowModal(false);
      fetchVehicles();
    } catch (err) {
      setError(parseApiError(err, 'Failed to save vehicle'));
    }
  };

  const confirmDelete = async () => {
    if (!deletingId) return;
    setDeleteLoading(true);
    try {
      await api.delete(`/vehicles/${deletingId}`);
      setDeletingId(null);
      fetchVehicles();
    } catch (err) {
      alert(parseApiError(err, 'Failed to deactivate vehicle. It may be currently allocated to an active delivery.'));
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Vehicle Fleet"
        subtitle="Manage fleet transportation assets, payload capacities, and maintenance status"
        action={
          <Button
            variant="primary"
            icon={Plus}
            onClick={openCreate}
            id="add-vehicle-btn"
          >
            Add Vehicle
          </Button>
        }
      />

      {fetchError && (
        <ErrorState
          error={fetchError}
          onRetry={fetchVehicles}
          className="mb-4"
        />
      )}

      {loading ? (
        <LoadingState message="Loading vehicle fleet data..." />
      ) : vehicles.length === 0 ? (
        <Card>
          <EmptyState
            icon={Truck}
            title="No vehicles available"
            description="Use the Add Vehicle button in the page header above to register fleet vehicles for deliveries."
          />
        </Card>
      ) : (
        <Table>
          <TableHeader>
            <tr>
              <TableHead>Registration Plate</TableHead>
              <TableHead>Vehicle Type</TableHead>
              <TableHead>Max Payload</TableHead>
              <TableHead>Operational Status</TableHead>
              <TableHead>Fleet Status</TableHead>
              <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
            </tr>
          </TableHeader>
          <TableBody>
            {vehicles.map((v) => (
              <TableRow key={v.id}>
                <TableCell style={{ fontWeight: 600, fontFamily: 'monospace' }}>
                  {v.registrationNumber}
                </TableCell>
                <TableCell>
                  <span
                    style={{
                      padding: '2px 6px',
                      borderRadius: 4,
                      border: '1px solid var(--theme-border)',
                      backgroundColor: 'var(--theme-elevated)',
                      fontSize: 11,
                    }}
                  >
                    {v.vehicleType}
                  </span>
                </TableCell>
                <TableCell>{v.capacity != null ? `${v.capacity} kg` : '—'}</TableCell>
                <TableCell>
                  <StatusBadge status={v.status} />
                </TableCell>
                <TableCell>
                  <StatusBadge status={v.active ? 'ACTIVE' : 'INACTIVE'} />
                </TableCell>
                <TableCell style={{ textAlign: 'right' }}>
                  <div style={{ display: 'inline-flex', gap: 6 }}>
                    <Button
                      variant="secondary"
                      size="sm"
                      icon={Edit2}
                      onClick={() => openEdit(v)}
                      title="Edit vehicle"
                    />
                    <Button
                      variant="danger"
                      size="sm"
                      icon={Trash2}
                      onClick={() => setDeletingId(v.id)}
                      title="Deactivate vehicle"
                    />
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {/* Vehicle Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editing ? 'Edit Vehicle' : 'New Vehicle'}
        footer={
          <div className="modal-footer">
            <Button variant="secondary" onClick={() => setShowModal(false)}>
              Cancel
            </Button>
            <Button variant="primary" onClick={handleSubmit}>
              {editing ? 'Update Vehicle' : 'Create Vehicle'}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleSubmit}>
          {error && <div className="alert alert-error mb-4">{error}</div>}
          <Input
            label="Registration Number"
            id="vehicle-reg"
            placeholder="e.g. KA-01-AB-1234"
            value={form.registrationNumber}
            onChange={(e) => setForm({ ...form, registrationNumber: e.target.value })}
            required
          />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Select
              label="Vehicle Type"
              id="vehicle-type"
              value={form.vehicleType}
              onChange={(e) => setForm({ ...form, vehicleType: e.target.value })}
            >
              <option value="BIKE">Bike</option>
              <option value="CAR">Car</option>
              <option value="VAN">Van</option>
              <option value="TRUCK">Truck</option>
            </Select>
            <Input
              label="Capacity (kg)"
              id="vehicle-capacity"
              type="number"
              step="0.1"
              min="0.1"
              value={form.capacity}
              onChange={(e) => setForm({ ...form, capacity: e.target.value })}
              required
            />
          </div>
          <Select
            label="Status"
            id="vehicle-status"
            value={form.status}
            onChange={(e) => setForm({ ...form, status: e.target.value })}
            disabled={editing && (editing.status === 'IN_USE' || editing.inActiveDelivery)}
            helperText={
              editing && (editing.status === 'IN_USE' || editing.inActiveDelivery)
                ? 'Cannot change vehicle status to OFFLINE or MAINTENANCE while vehicle is in active delivery.'
                : undefined
            }
          >
            <option value="AVAILABLE">Available</option>
            <option value="IN_USE">In Use</option>
            <option value="MAINTENANCE">Maintenance</option>
            <option value="OFFLINE">Offline</option>
          </Select>
        </form>
      </Modal>

      {/* Deactivate Dialog */}
      <ConfirmDialog
        isOpen={Boolean(deletingId)}
        onClose={() => setDeletingId(null)}
        onConfirm={confirmDelete}
        title="Deactivate Vehicle"
        message="Are you sure you want to deactivate this vehicle? It will not be assigned to future deliveries."
        confirmLabel="Deactivate"
        loading={deleteLoading}
      />
    </div>
  );
}
