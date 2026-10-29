import { useState, useEffect } from 'react';
import api from '../api/axios';
import { Users, Plus, Edit2, Trash2, Phone, Award } from 'lucide-react';
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

export default function Drivers() {
  const [drivers, setDrivers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [error, setError] = useState('');
  const [fetchError, setFetchError] = useState('');

  const [form, setForm] = useState({
    name: '',
    phone: '',
    licenseNumber: '',
    status: 'AVAILABLE',
  });

  const fetchDrivers = () => {
    setLoading(true);
    setFetchError('');
    api.get('/drivers', { params: { size: 200 } })
      .then((res) => setDrivers(res.data.content || []))
      .catch((err) => setFetchError(parseApiError(err, 'Failed to load drivers')))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchDrivers();
  }, []);

  const openCreate = () => {
    setForm({ name: '', phone: '', licenseNumber: '', status: 'AVAILABLE' });
    setEditing(null);
    setError('');
    setShowModal(true);
  };

  const openEdit = (d) => {
    setForm({
      name: d.name,
      phone: d.phone || '',
      licenseNumber: d.licenseNumber || '',
      status: d.status || 'AVAILABLE',
    });
    setEditing(d);
    setError('');
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    const trimmedName = form.name.trim();
    const trimmedLicense = form.licenseNumber.trim();

    if (!trimmedName) {
      setError('Driver name is required.');
      return;
    }
    if (!trimmedLicense) {
      setError('License number is required.');
      return;
    }

    try {
      const payload = {
        name: trimmedName,
        phone: form.phone?.trim() || null,
        licenseNumber: trimmedLicense,
        status: form.status || 'AVAILABLE',
      };

      if (editing) {
        await api.put(`/drivers/${editing.id}`, payload);
      } else {
        await api.post('/drivers', payload);
      }
      setShowModal(false);
      fetchDrivers();
    } catch (err) {
      setError(parseApiError(err, 'Failed to save driver'));
    }
  };

  const confirmDelete = async () => {
    if (!deletingId) return;
    setDeleteLoading(true);
    try {
      await api.delete(`/drivers/${deletingId}`);
      setDeletingId(null);
      fetchDrivers();
    } catch (err) {
      alert(parseApiError(err, 'Failed to deactivate driver. Active deliveries may depend on this driver.'));
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Driver Management"
        subtitle="Manage fleet personnel, licensing details, and operational availability"
        action={
          <Button
            variant="primary"
            icon={Plus}
            onClick={openCreate}
            id="add-driver-btn"
          >
            Add Driver
          </Button>
        }
      />

      {fetchError && (
        <ErrorState
          error={fetchError}
          onRetry={fetchDrivers}
          className="mb-4"
        />
      )}

      {loading ? (
        <LoadingState message="Loading driver records..." />
      ) : drivers.length === 0 ? (
        <Card>
          <EmptyState
            icon={Users}
            title="No drivers available"
            description="Use the Add Driver button in the page header above to register qualified drivers for dispatches."
          />
        </Card>
      ) : (
        <Table>
          <TableHeader>
            <tr>
              <TableHead>Driver Name</TableHead>
              <TableHead>Contact Phone</TableHead>
              <TableHead>License Number</TableHead>
              <TableHead>Availability Status</TableHead>
              <TableHead>Account Status</TableHead>
              <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
            </tr>
          </TableHeader>
          <TableBody>
            {drivers.map((d) => (
              <TableRow key={d.id}>
                <TableCell style={{ fontWeight: 600 }}>{d.name}</TableCell>
                <TableCell>{d.phone || '—'}</TableCell>
                <TableCell style={{ fontFamily: 'monospace' }}>
                  {d.licenseNumber || '—'}
                </TableCell>
                <TableCell>
                  <StatusBadge status={d.status} />
                </TableCell>
                <TableCell>
                  <StatusBadge status={d.active ? 'ACTIVE' : 'INACTIVE'} />
                </TableCell>
                <TableCell style={{ textAlign: 'right' }}>
                  <div style={{ display: 'inline-flex', gap: 6 }}>
                    <Button
                      variant="secondary"
                      size="sm"
                      icon={Edit2}
                      onClick={() => openEdit(d)}
                      title="Edit driver"
                    />
                    <Button
                      variant="danger"
                      size="sm"
                      icon={Trash2}
                      onClick={() => setDeletingId(d.id)}
                      title="Deactivate driver"
                    />
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {/* Driver Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editing ? 'Edit Driver' : 'New Driver'}
        footer={
          <div className="modal-footer">
            <Button variant="secondary" onClick={() => setShowModal(false)}>
              Cancel
            </Button>
            <Button variant="primary" onClick={handleSubmit}>
              {editing ? 'Update Driver' : 'Create Driver'}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleSubmit}>
          {error && <div className="alert alert-error mb-4">{error}</div>}
          <Input
            label="Name"
            id="driver-name"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            required
          />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Input
              label="Phone"
              id="driver-phone"
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
            />
            <Input
              label="License Number"
              id="driver-license"
              value={form.licenseNumber}
              onChange={(e) => setForm({ ...form, licenseNumber: e.target.value })}
              required
            />
          </div>
          <Select
            label="Operational Status"
            id="driver-status"
            value={form.status}
            onChange={(e) => setForm({ ...form, status: e.target.value })}
          >
            <option value="AVAILABLE">Available</option>
            <option value="UNAVAILABLE">Unavailable</option>
            <option value="ON_DELIVERY">On Delivery</option>
          </Select>
        </form>
      </Modal>

      {/* Deactivate Dialog */}
      <ConfirmDialog
        isOpen={Boolean(deletingId)}
        onClose={() => setDeletingId(null)}
        onConfirm={confirmDelete}
        title="Deactivate Driver"
        message="Are you sure you want to deactivate this driver? They will no longer be eligible for future deliveries."
        confirmLabel="Deactivate"
        loading={deleteLoading}
      />
    </div>
  );
}
