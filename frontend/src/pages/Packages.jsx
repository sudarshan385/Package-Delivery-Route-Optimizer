import { useState, useEffect } from 'react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { Package, Plus, Search, Edit2, Trash2, Filter } from 'lucide-react';
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
import ErrorState from '../components/ui/ErrorState';
import Pagination from '../components/ui/Pagination';
import CustomerShipmentModal from '../components/ui/CustomerShipmentModal';

export const SUPPORTED_CITIES = ['Bengaluru', 'Pune', 'Mumbai'];

export default function Packages() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const isUser = user?.role === 'USER';

  const [packages, setPackages] = useState([]);
  const [hubLocations, setHubLocations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [showCustomerModal, setShowCustomerModal] = useState(false);
  const [editing, setEditing] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  
  // Feedback & New Package Pinning
  const [successMessage, setSuccessMessage] = useState('');
  const [newlyCreatedId, setNewlyCreatedId] = useState(null);

  // Pagination
  const [currentPage, setCurrentPage] = useState(0);
  const pageSize = 15;
  
  // Toolbar states: Search, Status, City
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [cityFilter, setCityFilter] = useState('');
  const [error, setError] = useState('');
  const [fetchError, setFetchError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});

  const [form, setForm] = useState({
    fulfillmentMode: 'HUB_STAGED',
    sourceLocationId: '',
    city: '',
    pickupLocation: '',
    deliveryLocation: '',
    senderName: '',
    senderPhone: '',
    receiverName: '',
    receiverPhone: '',
    weight: '',
    priority: 'MEDIUM',
    description: '',
  });

  const fetchPackages = () => {
    setLoading(true);
    setFetchError('');
    api.get('/packages', { params: { size: 200 } })
      .then((res) => {
        setPackages(res.data.content || []);
      })
      .catch((err) => {
        setFetchError(err.response?.data?.message || 'Failed to load packages');
      })
      .finally(() => setLoading(false));
  };

  const fetchHubLocations = () => {
    api.get('/locations/hubs')
      .then((res) => {
        const hubs = Array.isArray(res.data) ? res.data : [];
        if (hubs.length > 0) {
          setHubLocations(hubs);
        } else {
          fallbackHubLocations();
        }
      })
      .catch(() => {
        fallbackHubLocations();
      });
  };

  const fallbackHubLocations = () => {
    api.get('/locations', { params: { size: 200 } })
      .then((res) => {
        const all = res.data.content || [];
        const hubs = all.filter(
          (l) => l.active && l.locationType && ['HUB', 'DEPOT', 'WAREHOUSE'].includes(l.locationType.toUpperCase())
        );
        setHubLocations(hubs);
      })
      .catch((err) => {
        console.error('Failed to load active hubs', err);
      });
  };

  useEffect(() => {
    fetchPackages();
    fetchHubLocations();
  }, []);

  const resetForm = () => {
    const defaultHub = hubLocations[0];
    setForm({
      fulfillmentMode: 'HUB_STAGED',
      sourceLocationId: defaultHub ? String(defaultHub.id) : '',
      city: defaultHub ? defaultHub.city : '',
      pickupLocation: defaultHub ? defaultHub.name : '',
      deliveryLocation: '',
      senderName: user?.fullName || user?.username || '',
      senderPhone: '',
      receiverName: '',
      receiverPhone: '',
      weight: '',
      priority: 'MEDIUM',
      description: '',
    });
    setFieldErrors({});
    setEditing(null);
    setError('');
  };

  const openCreate = () => {
    if (isUser) {
      setShowCustomerModal(true);
      return;
    }
    resetForm();
    setSuccessMessage('');
    setShowModal(true);
  };

  const openEdit = (pkg) => {
    const isHub = pkg.sourceLocationId && hubLocations.some(h => h.id === pkg.sourceLocationId);
    setForm({
      fulfillmentMode: isHub ? 'HUB_STAGED' : (pkg.fulfillmentMode || 'CUSTOMER_PICKUP'),
      sourceLocationId: pkg.sourceLocationId ? String(pkg.sourceLocationId) : '',
      city: pkg.city || '',
      pickupLocation: pkg.pickupLocation || pkg.sourceLocationName || '',
      deliveryLocation: pkg.deliveryLocation || pkg.destinationLocationName || '',
      senderName: pkg.senderName || '',
      senderPhone: pkg.senderPhone || '',
      receiverName: pkg.receiverName || '',
      receiverPhone: pkg.receiverPhone || '',
      weight: pkg.weight != null ? pkg.weight : '',
      priority: pkg.priority || 'MEDIUM',
      description: pkg.description || '',
    });
    setFieldErrors({});
    setEditing(pkg);
    setError('');
    setSuccessMessage('');
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});

    const errors = {};
    const trimmedCity = form.city?.trim() || '';
    const trimmedDelivery = form.deliveryLocation?.trim() || '';
    const trimmedSender = form.senderName?.trim() || '';
    const trimmedReceiver = form.receiverName?.trim() || '';
    const trimmedSenderPhone = form.senderPhone?.trim() || '';
    const trimmedReceiverPhone = form.receiverPhone?.trim() || '';
    const phoneRegex = /^[0-9+\-\s()]{7,20}$/;

    if (!trimmedCity) {
      errors.city = 'City is required.';
    }

    if (form.fulfillmentMode === 'HUB_STAGED') {
      if (!form.sourceLocationId) {
        errors.sourceLocationId = 'Please select an active Central Hub location.';
      }
    } else {
      const trimmedPickup = form.pickupLocation?.trim() || '';
      if (!trimmedPickup) {
        errors.pickupLocation = 'Pickup location is required.';
      } else if (trimmedPickup.length < 2) {
        errors.pickupLocation = 'Pickup location must be at least 2 characters.';
      }
      if (trimmedPickup && trimmedDelivery && trimmedPickup.toLowerCase() === trimmedDelivery.toLowerCase()) {
        errors.deliveryLocation = 'Pickup location and delivery location must be distinct.';
      }
    }

    if (!trimmedDelivery) {
      errors.deliveryLocation = 'Delivery destination is required.';
    } else if (trimmedDelivery.length < 2) {
      errors.deliveryLocation = 'Delivery destination must be at least 2 characters.';
    }

    if (!trimmedSender) {
      errors.senderName = 'Sender name is required.';
    }
    if (trimmedSenderPhone && !phoneRegex.test(trimmedSenderPhone)) {
      errors.senderPhone = 'Invalid phone format (allowed: 7-20 digits or symbols +, -, ()).';
    }

    if (!trimmedReceiver) {
      errors.receiverName = 'Receiver name is required.';
    }
    if (trimmedReceiverPhone && !phoneRegex.test(trimmedReceiverPhone)) {
      errors.receiverPhone = 'Invalid phone format (allowed: 7-20 digits or symbols +, -, ()).';
    }

    const parsedWeight = Number(form.weight);
    if (!form.weight || isNaN(parsedWeight) || parsedWeight <= 0) {
      errors.weight = 'Weight must be greater than zero.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setError('Please resolve the highlighted field errors before submitting.');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        fulfillmentMode: form.fulfillmentMode,
        city: trimmedCity,
        deliveryLocation: trimmedDelivery,
        senderName: trimmedSender,
        senderPhone: trimmedSenderPhone || null,
        receiverName: trimmedReceiver,
        receiverPhone: trimmedReceiverPhone || null,
        weight: parsedWeight,
        priority: form.priority || 'MEDIUM',
        description: form.description?.trim() || null,
      };

      if (form.fulfillmentMode === 'HUB_STAGED') {
        payload.sourceLocationId = Number(form.sourceLocationId);
        const selectedHub = hubLocations.find(h => String(h.id) === form.sourceLocationId);
        payload.pickupLocation = selectedHub ? selectedHub.name : (form.pickupLocation || 'Central Hub');
      } else {
        payload.pickupLocation = form.pickupLocation?.trim();
      }

      let savedPkg;
      if (editing) {
        const res = await api.put(`/packages/${editing.id}`, payload);
        savedPkg = res.data;
        setSuccessMessage(`Package ${savedPkg.trackingNumber || `PKG-${savedPkg.id}`} updated successfully.`);
      } else {
        const res = await api.post('/packages', payload);
        savedPkg = res.data;
        setNewlyCreatedId(savedPkg.id);
        setCurrentPage(0);
        setSuccessMessage(`Package ${savedPkg.trackingNumber || `PKG-${savedPkg.id}`} created successfully.`);
      }

      if (savedPkg && savedPkg.id) {
        setPackages((prev) => {
          const exists = prev.some((p) => p.id === savedPkg.id);
          if (exists) {
            return prev.map((p) => (p.id === savedPkg.id ? savedPkg : p));
          }
          return [savedPkg, ...prev];
        });
      }

      setShowModal(false);
      resetForm();
      fetchPackages();
    } catch (err) {
      const respData = err.response?.data;
      const errorMsg = respData?.detail || respData?.message || 'Failed to save package';
      
      const newFieldErrors = {};
      if (respData?.errors) {
        Object.entries(respData.errors).forEach(([field, msg]) => {
          newFieldErrors[field] = msg;
        });
      }
      if (errorMsg.toLowerCase().includes('geocode delivery') || errorMsg.toLowerCase().includes('delivery destination') || errorMsg.toLowerCase().includes('resolve address to coordinates')) {
        newFieldErrors.deliveryLocation = `Geocoding failed: ${errorMsg}. Please enter a valid address.`;
      } else if (errorMsg.toLowerCase().includes('geocode pickup') || errorMsg.toLowerCase().includes('pickup location')) {
        newFieldErrors.pickupLocation = `Geocoding failed: ${errorMsg}. Please enter a valid address.`;
      } else if (errorMsg.toLowerCase().includes('hub')) {
        newFieldErrors.sourceLocationId = errorMsg;
      }
      if (Object.keys(newFieldErrors).length > 0) {
        setFieldErrors(prev => ({ ...prev, ...newFieldErrors }));
      }
      setError(errorMsg);
    } finally {
      setSubmitting(false);
    }
  };

  const confirmDelete = async () => {
    if (!deletingId) return;
    setDeleteLoading(true);
    try {
      await api.delete(`/packages/${deletingId}`);
      setDeletingId(null);
      fetchPackages();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to deactivate package');
    } finally {
      setDeleteLoading(false);
    }
  };

  // Filter packages based on Search, Status, and City
  const filtered = packages.filter((p) => {
    // A newly created package is always visible so it is not hidden simply because the current filter excludes its initial status
    if (newlyCreatedId && p.id === newlyCreatedId) return true;
    if (statusFilter && p.status !== statusFilter) return false;
    if (cityFilter && p.city !== cityFilter) return false;
    if (search) {
      const q = search.toLowerCase();
      const match =
        p.trackingNumber?.toLowerCase().includes(q) ||
        p.city?.toLowerCase().includes(q) ||
        p.senderName?.toLowerCase().includes(q) ||
        p.receiverName?.toLowerCase().includes(q) ||
        p.pickupLocation?.toLowerCase().includes(q) ||
        p.deliveryLocation?.toLowerCase().includes(q) ||
        p.sourceLocationName?.toLowerCase().includes(q) ||
        p.destinationLocationName?.toLowerCase().includes(q);
      if (!match) return false;
    }
    return true;
  });

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const paginatedPackages = filtered.slice(currentPage * pageSize, (currentPage + 1) * pageSize);

  return (
    <div>
      <PageHeader
        title={isUser ? "My Packages" : "Package Management"}
        subtitle={isUser ? "Manage your delivery requests and view tracking status" : "Manage customer shipments, user-entered locations, and delivery workflows"}
        action={
          isUser ? (
            <Button
              variant="primary"
              icon={Plus}
              onClick={() => setShowCustomerModal(true)}
              id="add-package-btn"
            >
              New Package
            </Button>
          ) : isAdmin ? (
            <div style={{ display: 'flex', gap: 10 }}>
              <Button
                variant="secondary"
                icon={Plus}
                onClick={openCreate}
                id="admin-hub-package-btn"
              >
                Stock at Hub (Internal)
              </Button>
              <Button
                variant="primary"
                icon={Plus}
                onClick={() => setShowCustomerModal(true)}
                id="add-package-btn"
              >
                New Smart Shipment
              </Button>
            </div>
          ) : null
        }
      />

      {successMessage && (
        <div
          id="package-success-alert"
          className="alert alert-success mb-4"
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '12px 16px',
            borderRadius: '8px',
            backgroundColor: 'var(--color-success-bg, rgba(16, 185, 129, 0.12))',
            border: '1px solid var(--color-success, #10b981)',
            color: 'var(--color-success-text, #065f46)',
            fontWeight: 500,
          }}
        >
          <span>✓ {successMessage}</span>
          <button
            type="button"
            onClick={() => setSuccessMessage('')}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              fontWeight: 'bold',
              color: 'inherit',
              fontSize: '14px',
            }}
          >
            ✕
          </button>
        </div>
      )}

      {fetchError && (
        <ErrorState
          error={fetchError}
          onRetry={fetchPackages}
          className="mb-4"
        />
      )}

      {/* Toolbar: [Search] [Status] [City] [New Package] */}
      <Card padding={16} className="mb-4">
        <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ flex: '1 1 260px', minWidth: 200 }}>
            <Input
              icon={Search}
              placeholder="Search by tracking #, city, sender, receiver, location..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setCurrentPage(0);
              }}
              id="package-search"
            />
          </div>

          <div style={{ width: 160 }}>
            <Select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setCurrentPage(0);
              }}
              id="package-status-filter"
            >
              <option value="">All Statuses</option>
              <option value="PENDING">Pending</option>
              <option value="IN_DELIVERY">In Delivery</option>
              <option value="DELIVERED">Delivered</option>
              <option value="FAILED">Failed</option>
            </Select>
          </div>

          <div style={{ width: 160 }}>
            <Select
              value={cityFilter}
              onChange={(e) => {
                setCityFilter(e.target.value);
                setCurrentPage(0);
              }}
              id="package-city-filter"
            >
              <option value="">All Cities</option>
              {SUPPORTED_CITIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </Select>
          </div>

          {(search || statusFilter || cityFilter) && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setSearch('');
                setStatusFilter('');
                setCityFilter('');
                setNewlyCreatedId(null);
                setCurrentPage(0);
              }}
            >
              Reset Filters
            </Button>
          )}
        </div>
      </Card>

      {/* Package List / Table */}
      {loading ? (
        <LoadingState message="Loading package records..." />
      ) : fetchError ? null : filtered.length === 0 ? (
        <Card>
          <EmptyState
            icon={Package}
            title={packages.length === 0 ? 'No packages found' : 'No matching packages'}
            description={
              packages.length === 0
                ? 'Use the New Package button in the page header above to intake and register your first delivery package.'
                : 'Try adjusting your search query, status, or city filters.'
            }
          />
        </Card>
      ) : (
        <>
          <Table>
            <TableHeader>
              <tr>
                <TableHead>Tracking #</TableHead>
                <TableHead>City</TableHead>
                <TableHead>Sender</TableHead>
                <TableHead>Receiver</TableHead>
                <TableHead>Pickup Location</TableHead>
                <TableHead>Delivery Location</TableHead>
                <TableHead>Nearest Hub</TableHead>
                <TableHead>Weight</TableHead>
                <TableHead>Priority</TableHead>
                <TableHead>Status</TableHead>
                {isAdmin && <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>}
              </tr>
            </TableHeader>
            <TableBody>
              {paginatedPackages.map((pkg) => (
                <TableRow
                  key={pkg.id}
                  style={pkg.id === newlyCreatedId ? { backgroundColor: 'var(--color-primary-light, rgba(59, 130, 246, 0.08))' } : undefined}
                >
                  <TableCell style={{ fontFamily: 'monospace', fontWeight: 600 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span>{pkg.trackingNumber || `PKG-${pkg.id}`}</span>
                      {pkg.id === newlyCreatedId && (
                        <span
                          style={{
                            fontSize: 10,
                            fontWeight: 700,
                            padding: '1px 5px',
                            borderRadius: 4,
                            backgroundColor: 'var(--color-primary, #3b82f6)',
                            color: '#fff',
                            textTransform: 'uppercase',
                          }}
                        >
                          New
                        </span>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    {pkg.city ? (
                      <span
                        style={{
                          padding: '2px 6px',
                          borderRadius: 4,
                          border: '1px solid var(--theme-border)',
                          backgroundColor: 'var(--theme-elevated)',
                          fontSize: 11,
                          fontWeight: 600,
                        }}
                      >
                        {pkg.city}
                      </span>
                    ) : (
                      '—'
                    )}
                  </TableCell>
                  <TableCell>
                    <div>
                      <strong style={{ display: 'block' }}>{pkg.senderName}</strong>
                      {pkg.senderPhone && (
                        <span style={{ fontSize: 11, color: 'var(--theme-muted)' }}>
                          {pkg.senderPhone}
                        </span>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div>
                      <strong style={{ display: 'block' }}>{pkg.receiverName}</strong>
                      {pkg.receiverPhone && (
                        <span style={{ fontSize: 11, color: 'var(--theme-muted)' }}>
                          {pkg.receiverPhone}
                        </span>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>{pkg.pickupLocation || pkg.sourceLocationName || '—'}</TableCell>
                  <TableCell>{pkg.deliveryLocation || pkg.destinationLocationName || '—'}</TableCell>
                  <TableCell>
                    {pkg.nearestHubName ? (
                      <div>
                        <span style={{ fontWeight: 600, color: 'var(--theme-text)' }}>
                          {pkg.nearestHubName}
                        </span>
                        {pkg.nearestHubDistance != null && (
                          <span style={{ fontSize: 11, color: 'var(--theme-muted)', display: 'block' }}>
                            {pkg.nearestHubDistance.toFixed(1)} km
                          </span>
                        )}
                      </div>
                    ) : (
                      <span style={{ color: 'var(--theme-muted)', fontStyle: 'italic', fontSize: 12 }}>
                        No reachable hub
                      </span>
                    )}
                  </TableCell>
                  <TableCell>{pkg.weight != null ? `${pkg.weight} kg` : '—'}</TableCell>
                  <TableCell>
                    <StatusBadge status={pkg.priority} />
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={pkg.status} />
                  </TableCell>
                  {isAdmin && (
                    <TableCell style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: 6 }}>
                        <Button
                          variant="secondary"
                          size="sm"
                          icon={Edit2}
                          onClick={() => openEdit(pkg)}
                          title="Edit package"
                        />
                        <Button
                          variant="danger"
                          size="sm"
                          icon={Trash2}
                          onClick={() => setDeletingId(pkg.id)}
                          title="Deactivate package"
                        />
                      </div>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {filtered.length > pageSize && (
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              totalItems={filtered.length}
              pageSize={pageSize}
              onPageChange={(p) => setCurrentPage(p)}
            />
          )}
        </>
      )}

      {/* New / Edit Package Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editing ? 'Edit Package' : 'New Package'}
        maxWidth={580}
        footer={
          <div className="modal-footer">
            <Button variant="secondary" onClick={() => setShowModal(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={handleSubmit}
              id="package-submit-btn"
              disabled={submitting}
            >
              {submitting ? 'Saving...' : (editing ? 'Update Package' : 'Create Package')}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleSubmit}>
          {error && <div className="alert alert-error mb-4">{error}</div>}

          {/* Fulfillment Mode Toggle */}
          <div className="form-group mb-4">
            <label className="block text-sm font-medium mb-1">Fulfillment Mode</label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              <button
                type="button"
                id="fulfillment-mode-hub"
                className={`btn ${form.fulfillmentMode === 'HUB_STAGED' ? 'btn-primary' : 'btn-secondary'}`}
                style={{
                  padding: '8px 12px',
                  fontSize: '13px',
                  fontWeight: 600,
                  borderRadius: '6px',
                  border: form.fulfillmentMode === 'HUB_STAGED' ? '2px solid var(--color-primary)' : '1px solid var(--theme-border)',
                  cursor: 'pointer',
                }}
                onClick={() => {
                  const defaultHub = hubLocations.find(h => String(h.id) === form.sourceLocationId) || hubLocations[0];
                  setForm(prev => ({
                    ...prev,
                    fulfillmentMode: 'HUB_STAGED',
                    sourceLocationId: defaultHub ? String(defaultHub.id) : prev.sourceLocationId,
                    city: defaultHub ? defaultHub.city : prev.city,
                  }));
                  setFieldErrors(prev => ({ ...prev, pickupLocation: undefined, sourceLocationId: undefined }));
                }}
              >
                🏢 Stocked at Central Hub
              </button>
              <button
                type="button"
                id="fulfillment-mode-pickup"
                className={`btn ${form.fulfillmentMode === 'CUSTOMER_PICKUP' ? 'btn-primary' : 'btn-secondary'}`}
                style={{
                  padding: '8px 12px',
                  fontSize: '13px',
                  fontWeight: 600,
                  borderRadius: '6px',
                  border: form.fulfillmentMode === 'CUSTOMER_PICKUP' ? '2px solid var(--color-primary)' : '1px solid var(--theme-border)',
                  cursor: 'pointer',
                }}
                onClick={() => {
                  setForm(prev => ({
                    ...prev,
                    fulfillmentMode: 'CUSTOMER_PICKUP',
                    sourceLocationId: '',
                  }));
                  setFieldErrors(prev => ({ ...prev, sourceLocationId: undefined }));
                }}
              >
                🚚 Customer Pickup (Door-to-Door)
              </button>
            </div>
            <span style={{ fontSize: 11, color: 'var(--theme-muted)', marginTop: 4, display: 'block' }}>
              {form.fulfillmentMode === 'HUB_STAGED'
                ? 'Package is already staged at an active Central Hub. Driver dispatches directly to customer destination.'
                : 'Package will be collected at customer pickup location before delivery to customer destination.'}
            </span>
          </div>

          {/* Central Hub Fulfillment Mode Fields */}
          {form.fulfillmentMode === 'HUB_STAGED' ? (
            <>
              <Select
                label="Departure Central Hub"
                id="package-source-hub"
                value={form.sourceLocationId}
                error={fieldErrors.sourceLocationId}
                onChange={(e) => {
                  const hubId = e.target.value;
                  const selectedHub = hubLocations.find((h) => String(h.id) === hubId);
                  setForm((prev) => ({
                    ...prev,
                    sourceLocationId: hubId,
                    city: selectedHub ? selectedHub.city : prev.city,
                  }));
                  setFieldErrors((prev) => ({ ...prev, sourceLocationId: undefined }));
                }}
                helperText="Select the active central distribution hub where this package is stocked."
                required
              >
                <option value="">Select Central Hub</option>
                {hubLocations.map((hub) => (
                  <option key={hub.id} value={hub.id}>
                    {hub.name} ({hub.city}) — {hub.address || 'Central Depot'}
                  </option>
                ))}
              </Select>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
                <Select
                  label="Delivery City"
                  id="package-city"
                  value={form.city}
                  error={fieldErrors.city}
                  onChange={(e) => {
                    setForm({ ...form, city: e.target.value });
                    setFieldErrors((prev) => ({ ...prev, city: undefined }));
                  }}
                  required
                >
                  <option value="">Select City</option>
                  {SUPPORTED_CITIES.map((city) => (
                    <option key={city} value={city}>
                      {city}
                    </option>
                  ))}
                </Select>
                <Input
                  label="Customer Delivery Destination"
                  id="package-delivery-location"
                  placeholder="Enter customer destination address"
                  value={form.deliveryLocation}
                  error={fieldErrors.deliveryLocation}
                  helperText="Real customer street address to be geocoded."
                  onChange={(e) => {
                    setForm({ ...form, deliveryLocation: e.target.value });
                    setFieldErrors((prev) => ({ ...prev, deliveryLocation: undefined }));
                  }}
                  required
                />
              </div>
            </>
          ) : (
            <>
              {/* Customer Pickup Mode Fields */}
              <Select
                label="City"
                id="package-city"
                value={form.city}
                error={fieldErrors.city}
                onChange={(e) => {
                  setForm({ ...form, city: e.target.value });
                  setFieldErrors((prev) => ({ ...prev, city: undefined }));
                }}
                required
              >
                <option value="">Select City</option>
                {SUPPORTED_CITIES.map((city) => (
                  <option key={city} value={city}>
                    {city}
                  </option>
                ))}
              </Select>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
                <Input
                  label="Customer Pickup Location"
                  id="package-pickup-location"
                  placeholder="Enter customer pickup address"
                  value={form.pickupLocation}
                  error={fieldErrors.pickupLocation}
                  onChange={(e) => {
                    setForm({ ...form, pickupLocation: e.target.value });
                    setFieldErrors((prev) => ({ ...prev, pickupLocation: undefined }));
                  }}
                  required
                />
                <Input
                  label="Customer Delivery Location"
                  id="package-delivery-location"
                  placeholder="Enter customer delivery address"
                  value={form.deliveryLocation}
                  error={fieldErrors.deliveryLocation}
                  onChange={(e) => {
                    setForm({ ...form, deliveryLocation: e.target.value });
                    setFieldErrors((prev) => ({ ...prev, deliveryLocation: undefined }));
                  }}
                  required
                />
              </div>
            </>
          )}

          {/* Sender & Receiver Info */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
            <Input
              label="Sender Name"
              id="package-sender-name"
              value={form.senderName}
              error={fieldErrors.senderName}
              onChange={(e) => {
                setForm({ ...form, senderName: e.target.value });
                setFieldErrors((prev) => ({ ...prev, senderName: undefined }));
              }}
              required
            />
            <Input
              label="Sender Phone"
              id="package-sender-phone"
              placeholder="e.g. +91 98765 43210"
              value={form.senderPhone}
              error={fieldErrors.senderPhone}
              onChange={(e) => {
                setForm({ ...form, senderPhone: e.target.value });
                setFieldErrors((prev) => ({ ...prev, senderPhone: undefined }));
              }}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
            <Input
              label="Receiver Name"
              id="package-receiver-name"
              value={form.receiverName}
              error={fieldErrors.receiverName}
              onChange={(e) => {
                setForm({ ...form, receiverName: e.target.value });
                setFieldErrors((prev) => ({ ...prev, receiverName: undefined }));
              }}
              required
            />
            <Input
              label="Receiver Phone"
              id="package-receiver-phone"
              placeholder="e.g. +91 98765 43211"
              value={form.receiverPhone}
              error={fieldErrors.receiverPhone}
              onChange={(e) => {
                setForm({ ...form, receiverPhone: e.target.value });
                setFieldErrors((prev) => ({ ...prev, receiverPhone: undefined }));
              }}
            />
          </div>

          {/* Weight & Priority */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
            <Input
              label="Weight (kg)"
              id="package-weight"
              type="number"
              step="0.1"
              min="0.1"
              value={form.weight}
              error={fieldErrors.weight}
              onChange={(e) => {
                setForm({ ...form, weight: e.target.value });
                setFieldErrors((prev) => ({ ...prev, weight: undefined }));
              }}
              required
            />
            <Select
              label="Priority"
              id="package-priority"
              value={form.priority}
              onChange={(e) => setForm({ ...form, priority: e.target.value })}
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="URGENT">Urgent</option>
            </Select>
          </div>

          {/* Description */}
          <div className="form-group">
            <label htmlFor="package-description">Description</label>
            <textarea
              id="package-description"
              className="form-input"
              rows={3}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              placeholder="Package description, handling instructions..."
            />
          </div>
        </form>
      </Modal>

      {/* Confirm Deactivation Dialog */}
      <ConfirmDialog
        isOpen={Boolean(deletingId)}
        onClose={() => setDeletingId(null)}
        onConfirm={confirmDelete}
        title="Deactivate Package"
        message="Are you sure you want to deactivate this package? This package will no longer be eligible for dispatch."
        confirmLabel="Deactivate"
        loading={deleteLoading}
      />

      {/* Customer Smart Shipment Wizard */}
      <CustomerShipmentModal
        isOpen={showCustomerModal}
        onClose={() => setShowCustomerModal(false)}
        onCreated={(created) => {
          setSuccessMessage(`Shipment ${created?.trackingNumber || ''} created successfully! Nearest service hub automatically assigned.`);
          fetchPackages();
        }}
      />
    </div>
  );
}
