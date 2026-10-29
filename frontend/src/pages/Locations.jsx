import { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { MapPin, Plus, Edit2, Trash2, Network, Building2, RefreshCw, CheckCircle2, Power } from 'lucide-react';
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
import MapComponent from '../components/ui/Map';
import LocationPicker from '../components/ui/LocationPicker';

export default function Locations() {
  const [locations, setLocations] = useState([]);
  const [edges, setEdges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState('');
  
  // Modals & Forms
  const [showLocModal, setShowLocModal] = useState(false);
  const [showEdgeModal, setShowEdgeModal] = useState(false);
  const [editingLoc, setEditingLoc] = useState(null);
  const [locFormError, setLocFormError] = useState('');
  const [edgeFormError, setEdgeFormError] = useState('');

  // Bengaluru Hubs Setup State
  const [syncingHubs, setSyncingHubs] = useState(false);
  const [syncMessage, setSyncMessage] = useState('');

  // Delete confirmations
  const [deletingLocId, setDeletingLocId] = useState(null);
  const [deletingEdgeId, setDeletingEdgeId] = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  // Approval flow
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [showApproveModal, setShowApproveModal] = useState(false);
  const [approvingLoc, setApprovingLoc] = useState(null);
  const [approveFormError, setApproveFormError] = useState('');
  const [approveForm, setApproveForm] = useState({
    connectToLocationId: '',
    createIncomingEdge: true,
    createOutgoingEdge: true,
    incomingDistance: '',
    incomingEstimatedTime: '',
    outgoingDistance: '',
    outgoingEstimatedTime: ''
  });

  const [locForm, setLocForm] = useState({
    name: '',
    city: '',
    address: '',
    latitude: '',
    longitude: '',
    locationType: '',
  });

  const [geocoding, setGeocoding] = useState(false);

  const [edgeForm, setEdgeForm] = useState({
    sourceLocationId: '',
    destinationLocationId: '',
    distance: '',
    estimatedTime: '',
    geometryGeojson: null,
  });

  const [calculatingEdge, setCalculatingEdge] = useState(false);
  const [edgeCalcError, setEdgeCalcError] = useState('');
  const [calculatingApprove, setCalculatingApprove] = useState(false);

  const handleCalculateEdgeRoute = async () => {
    if (!edgeForm.sourceLocationId || !edgeForm.destinationLocationId) return;
    setCalculatingEdge(true);
    setEdgeCalcError('');
    try {
      const res = await api.get('/edges/calculate-route', {
        params: { sourceId: edgeForm.sourceLocationId, destinationId: edgeForm.destinationLocationId }
      });
      setEdgeForm(prev => ({
        ...prev,
        distance: res.data.distanceKm.toFixed(2),
        estimatedTime: Math.round(res.data.durationMin).toString(),
        geometryGeojson: res.data.geometryGeoJson || null
      }));
    } catch (err) {
      setEdgeCalcError(parseApiError(err, 'Could not route between these locations'));
      setEdgeForm(prev => ({ ...prev, distance: '', estimatedTime: '', geometryGeojson: null }));
    } finally {
      setCalculatingEdge(false);
    }
  };

  const handleCalculateApproveRoute = async () => {
    if (!approveForm.connectToLocationId) return;
    setCalculatingApprove(true);
    setApproveFormError('');
    try {
      let incomingDist = '', incomingTime = '', incomingGeom = null;
      let outgoingDist = '', outgoingTime = '', outgoingGeom = null;

      if (approveForm.createIncomingEdge) {
        const inRes = await api.get('/edges/calculate-route', {
          params: { sourceId: approveForm.connectToLocationId, destinationId: approvingLoc.id }
        });
        incomingDist = inRes.data.distanceKm.toFixed(2);
        incomingTime = Math.round(inRes.data.durationMin).toString();
        incomingGeom = inRes.data.geometryGeoJson || null;
      }

      if (approveForm.createOutgoingEdge) {
        const outRes = await api.get('/edges/calculate-route', {
          params: { sourceId: approvingLoc.id, destinationId: approveForm.connectToLocationId }
        });
        outgoingDist = outRes.data.distanceKm.toFixed(2);
        outgoingTime = Math.round(outRes.data.durationMin).toString();
        outgoingGeom = outRes.data.geometryGeoJson || null;
      }

      setApproveForm(prev => ({
        ...prev,
        incomingDistance: incomingDist,
        incomingEstimatedTime: incomingTime,
        incomingGeometryGeojson: incomingGeom,
        outgoingDistance: outgoingDist,
        outgoingEstimatedTime: outgoingTime,
        outgoingGeometryGeojson: outgoingGeom
      }));
    } catch (err) {
      setApproveFormError(parseApiError(err, 'Failed to route one or more directions.'));
      setApproveForm(prev => ({
        ...prev,
        incomingDistance: '', incomingEstimatedTime: '', incomingGeometryGeojson: null,
        outgoingDistance: '', outgoingEstimatedTime: '', outgoingGeometryGeojson: null
      }));
    } finally {
      setCalculatingApprove(false);
    }
  };

  const fetchData = () => {
    setLoading(true);
    setFetchError('');
    Promise.all([
      api.get('/locations', { params: { size: 200 } }),
      api.get('/edges', { params: { size: 200 } }),
    ])
      .then(([locRes, edgeRes]) => {
        setLocations(locRes.data.content || []);
        setEdges(edgeRes.data.content || []);
      })
      .catch((err) => {
        setFetchError(parseApiError(err, 'Failed to load locations and network edges'));
      })
      .finally(() => setLoading(false));
  };

  const location = useLocation();

  useEffect(() => {
    fetchData();
  }, []);

  useEffect(() => {
    if (location.hash === '#roads' || location.hash === '#edges') {
      const el = document.getElementById('roads-section');
      if (el) {
        setTimeout(() => el.scrollIntoView({ behavior: 'smooth' }), 120);
      }
    }
  }, [location.hash, loading]);

  const handleSyncBengaluruHubs = async () => {
    setSyncingHubs(true);
    setSyncMessage('');
    try {
      const res = await api.post('/locations/hubs/setup-bengaluru');
      setSyncMessage(res.data.message || `Successfully synchronized Bengaluru hubs: ${res.data.created} created, ${res.data.reused} reused. Total active hubs: ${res.data.totalActiveHubs}`);
      fetchData();
    } catch (err) {
      alert(parseApiError(err, 'Failed to synchronize Bengaluru candidate hubs'));
    } finally {
      setSyncingHubs(false);
    }
  };

  const handleToggleHubStatus = async (hubId, currentActive) => {
    try {
      await api.patch(`/locations/hubs/${hubId}/status`, null, {
        params: { active: !currentActive }
      });
      fetchData();
    } catch (err) {
      alert(parseApiError(err, 'Failed to update hub status'));
    }
  };

  const openCreateLoc = () => {
    setLocForm({ name: '', city: '', address: '', latitude: '', longitude: '', locationType: '' });
    setEditingLoc(null);
    setLocFormError('');
    setShowLocModal(true);
  };

  const openEditLoc = (loc) => {
    setLocForm({
      name: loc.name,
      city: loc.city || '',
      address: loc.address || '',
      latitude: loc.latitude,
      longitude: loc.longitude,
      locationType: loc.locationType || '',
    });
    setEditingLoc(loc);
    setLocFormError('');
    setShowLocModal(true);
  };

  const handleGeocode = async () => {
    if (!locForm.address) {
      setLocFormError('Please enter an address to search on the map.');
      return;
    }
    setGeocoding(true);
    setLocFormError('');
    try {
      const searchAddress = locForm.city ? `${locForm.address}, ${locForm.city}` : locForm.address;
      const res = await api.get(`/locations/geocode?address=${encodeURIComponent(searchAddress)}`);
      if (res.data && res.data.latitude && res.data.longitude) {
        setLocForm(prev => ({
          ...prev,
          latitude: res.data.latitude,
          longitude: res.data.longitude
        }));
      }
    } catch (err) {
      setLocFormError(parseApiError(err, 'Failed to find address on map. Please correct the address.'));
      setLocForm(prev => ({ ...prev, latitude: '', longitude: '' })); // clear invalid coords
    } finally {
      setGeocoding(false);
    }
  };

  const openCreateEdge = () => {
    setEdgeForm({
      sourceLocationId: '',
      destinationLocationId: '',
      distance: '',
      estimatedTime: '',
      geometryGeojson: null,
    });
    setEdgeFormError('');
    setShowEdgeModal(true);
  };

  const handleLocSubmit = async (e) => {
    e.preventDefault();
    setLocFormError('');

    const trimmedName = locForm.name.trim();
    if (!trimmedName) {
      setLocFormError('Location name is required.');
      return;
    }

    const lat = Number(locForm.latitude);
    const lng = Number(locForm.longitude);
    if (isNaN(lat) || isNaN(lng)) {
      setLocFormError('Valid latitude and longitude coordinates are required.');
      return;
    }

    try {
      const payload = {
        name: trimmedName,
        city: locForm.city?.trim() || null,
        address: locForm.address?.trim() || null,
        latitude: lat,
        longitude: lng,
        locationType: locForm.locationType?.trim() || null,
      };

      if (editingLoc) {
        await api.put(`/locations/${editingLoc.id}`, payload);
      } else {
        await api.post('/locations', payload);
      }

      setShowLocModal(false);
      setEditingLoc(null);
      fetchData();
    } catch (err) {
      setLocFormError(parseApiError(err, 'Failed to save location'));
    }
  };

  const handleEdgeSubmit = async (e) => {
    e.preventDefault();
    setEdgeFormError('');

    const sourceId = Number(edgeForm.sourceLocationId);
    const destId = Number(edgeForm.destinationLocationId);

    if (!sourceId || !destId) {
      setEdgeFormError('Source and destination locations are required.');
      return;
    }
    if (sourceId === destId) {
      setEdgeFormError('Source and destination locations cannot be the same.');
      return;
    }

    const dist = Number(edgeForm.distance);
    if (isNaN(dist) || dist <= 0) {
      setEdgeFormError('Distance must be greater than zero.');
      return;
    }

    if (edgeForm.estimatedTime !== '') {
      const time = Number(edgeForm.estimatedTime);
      if (isNaN(time) || time < 0) {
        setEdgeFormError('Estimated time must be zero or greater.');
        return;
      }
    }

    try {
      const payload = {
        sourceLocationId: sourceId,
        destinationLocationId: destId,
        distance: dist,
        estimatedTime: edgeForm.estimatedTime ? Number(edgeForm.estimatedTime) : null,
        geometryGeojson: edgeForm.geometryGeojson || null,
      };

      await api.post('/edges', payload);
      setShowEdgeModal(false);
      fetchData();
    } catch (err) {
      setEdgeFormError(parseApiError(err, 'Failed to create network edge'));
    }
  };

  const openApproveModal = (loc) => {
    setApprovingLoc(loc);
    setApproveForm({
      connectToLocationId: '',
      createIncomingEdge: true,
      createOutgoingEdge: true,
      incomingDistance: '',
      incomingEstimatedTime: '',
      incomingGeometryGeojson: null,
      outgoingDistance: '',
      outgoingEstimatedTime: '',
      outgoingGeometryGeojson: null
    });
    setApproveFormError('');
    setShowApproveModal(true);
  };

  const handleApproveSubmit = async (e) => {
    e.preventDefault();
    setApproveFormError('');

    const connectTo = Number(approveForm.connectToLocationId);
    if (!connectTo) {
      setApproveFormError('You must select an active network node to connect to.');
      return;
    }

    if (!approveForm.createIncomingEdge && !approveForm.createOutgoingEdge) {
      setApproveFormError('You must create at least one directional edge connection.');
      return;
    }

    const payload = {
      connectToLocationId: connectTo,
      createIncomingEdge: approveForm.createIncomingEdge,
      createOutgoingEdge: approveForm.createOutgoingEdge,
      incomingDistance: approveForm.incomingDistance ? Number(approveForm.incomingDistance) : null,
      incomingEstimatedTime: approveForm.incomingEstimatedTime ? Number(approveForm.incomingEstimatedTime) : null,
      incomingGeometryGeojson: approveForm.incomingGeometryGeojson || null,
      outgoingDistance: approveForm.outgoingDistance ? Number(approveForm.outgoingDistance) : null,
      outgoingEstimatedTime: approveForm.outgoingEstimatedTime ? Number(approveForm.outgoingEstimatedTime) : null,
      outgoingGeometryGeojson: approveForm.outgoingGeometryGeojson || null
    };

    try {
      await api.post(`/locations/${approvingLoc.id}/approve`, payload);
      setShowApproveModal(false);
      fetchData();
    } catch (err) {
      setApproveFormError(parseApiError(err, 'Failed to approve and connect location'));
    }
  };

  const confirmDeleteLoc = async () => {
    if (!deletingLocId) return;
    setDeleteLoading(true);
    try {
      await api.delete(`/locations/${deletingLocId}`);
      setDeletingLocId(null);
      fetchData();
    } catch (err) {
      alert(parseApiError(err, 'Failed to deactivate location. It may be referenced in existing routes.'));
    } finally {
      setDeleteLoading(false);
    }
  };

  const confirmDeleteEdge = async () => {
    if (!deletingEdgeId) return;
    setDeleteLoading(true);
    try {
      await api.delete(`/edges/${deletingEdgeId}`);
      setDeletingEdgeId(null);
      fetchData();
    } catch (err) {
      alert(parseApiError(err, 'Failed to deactivate network edge'));
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Locations & Network Edges"
        subtitle="Manage graph vertices and road segments for Dijkstra route optimization"
        action={
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {isAdmin && (
              <Button
                variant="secondary"
                icon={Building2}
                onClick={handleSyncBengaluruHubs}
                loading={syncingHubs}
                id="sync-bengaluru-hubs-btn"
              >
                Sync 20 Bengaluru Hubs
              </Button>
            )}
            <Button
              variant="secondary"
              icon={Network}
              onClick={openCreateEdge}
              id="add-edge-btn"
            >
              Add Edge
            </Button>
            <Button
              variant="primary"
              icon={Plus}
              onClick={openCreateLoc}
              id="add-location-btn"
            >
              Add Location
            </Button>
          </div>
        }
      />

      {syncMessage && (
        <div className="alert alert-success mb-4 flex items-center gap-2">
          <CheckCircle2 size={18} />
          <span>{syncMessage}</span>
        </div>
      )}

      {fetchError && (
        <ErrorState
          error={fetchError}
          onRetry={fetchData}
          className="mb-4"
        />
      )}

      {loading ? (
        <LoadingState message="Loading network topology..." />
      ) : (
        <>
          {isAdmin && locations.filter(l => !l.active).length > 0 && (
            <Card
              title={`Pending Network Approvals (${locations.filter(l => !l.active).length})`}
              subtitle="Customer package destinations awaiting administrative graph integration"
              className="mb-6"
              style={{ borderLeft: '4px solid var(--theme-warning)' }}
            >
              <Table>
                <TableHeader>
                  <tr>
                    <TableHead>Location Name</TableHead>
                    <TableHead>City</TableHead>
                    <TableHead>Address</TableHead>
                    <TableHead>Coordinates</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                  </tr>
                </TableHeader>
                <TableBody>
                  {locations.filter(l => !l.active).map((loc) => (
                    <TableRow key={loc.id}>
                      <TableCell style={{ fontWeight: 600 }}>{loc.name}</TableCell>
                      <TableCell>{loc.city || '—'}</TableCell>
                      <TableCell>{loc.address || '—'}</TableCell>
                      <TableCell style={{ fontFamily: 'monospace' }}>
                        {loc.latitude?.toFixed(4)}, {loc.longitude?.toFixed(4)}
                      </TableCell>
                      <TableCell>
                        <StatusBadge status="PENDING" />
                      </TableCell>
                      <TableCell style={{ textAlign: 'right' }}>
                        <Button
                          variant="primary"
                          size="sm"
                          icon={Network}
                          onClick={() => openApproveModal(loc)}
                        >
                          Approve & Connect
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Card>
          )}

          {/* Section 0: Interactive Map */}
          <Card title="Network Map" subtitle="Geographic visualization of your logistics network and road edges" className="mb-6">
            <MapComponent locations={locations} edges={edges} height="450px" />
          </Card>

          {/* Section 1: Locations */}
          <Card
            title={`Locations (${locations.length})`}
            subtitle="Registered graph nodes, depots, hubs, and customer delivery points"
            className="mb-6"
          >
            {locations.length === 0 ? (
              <EmptyState
                icon={MapPin}
                title="No locations available"
                description="Use the Add Location button in the page header above to build your delivery network graph."
              />
            ) : (
              <Table>
                <TableHeader>
                  <tr>
                    <TableHead>Location Name</TableHead>
                    <TableHead>City</TableHead>
                    <TableHead>Address</TableHead>
                    <TableHead>Coordinates (Lat, Lng)</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                  </tr>
                </TableHeader>
                <TableBody>
                  {locations.filter(l => l.active).map((loc) => (
                    <TableRow key={loc.id}>
                      <TableCell style={{ fontWeight: 600 }}>{loc.name}</TableCell>
                      <TableCell>{loc.city || '—'}</TableCell>
                      <TableCell>{loc.address || '—'}</TableCell>
                      <TableCell style={{ fontFamily: 'monospace' }}>
                        {loc.latitude?.toFixed(4)}, {loc.longitude?.toFixed(4)}
                      </TableCell>
                      <TableCell>
                        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}>
                          {loc.locationType ? (
                            <span
                              style={{
                                padding: '2px 6px',
                                borderRadius: 4,
                                border: '1px solid var(--theme-border)',
                                backgroundColor: loc.locationType === 'HUB' ? 'rgba(99, 102, 241, 0.15)' : 'var(--theme-elevated)',
                                color: loc.locationType === 'HUB' ? '#818cf8' : 'var(--theme-text)',
                                fontSize: 11,
                                fontWeight: loc.locationType === 'HUB' ? 700 : 500,
                              }}
                            >
                              {loc.locationType}
                            </span>
                          ) : (
                            '—'
                          )}
                          {loc.locationType === 'HUB' && (
                            <span
                              style={{
                                padding: '2px 5px',
                                borderRadius: 4,
                                backgroundColor: 'rgba(6, 182, 212, 0.1)',
                                color: '#06b6d4',
                                fontSize: 10,
                                fontWeight: 600,
                              }}
                            >
                              Virtual Hub
                            </span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={loc.active ? 'ACTIVE' : 'INACTIVE'} />
                      </TableCell>
                      <TableCell style={{ textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', gap: 6 }}>
                          {loc.locationType === 'HUB' && (
                            <Button
                              variant={loc.active ? "secondary" : "primary"}
                              size="sm"
                              icon={Power}
                              onClick={() => handleToggleHubStatus(loc.id, loc.active)}
                              title={loc.active ? "Deactivate virtual hub" : "Activate virtual hub"}
                            />
                          )}
                          <Button
                            variant="secondary"
                            size="sm"
                            icon={Edit2}
                            onClick={() => openEditLoc(loc)}
                            title="Edit location"
                          />
                          <Button
                            variant="danger"
                            size="sm"
                            icon={Trash2}
                            onClick={() => setDeletingLocId(loc.id)}
                            title="Deactivate location"
                          />
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>

          {/* Section 2: Roads / Edges */}
          <div id="roads-section">
            <Card
              title={`Roads & Graph Edges (${edges.length})`}
              subtitle="Connected segments with physical distances and transit time metrics"
            >
              {edges.length === 0 ? (
                <EmptyState
                  icon={Network}
                  title="No edges available"
                  description="Use the Add Edge button in the page header above to connect locations and enable Dijkstra route optimization."
                />
              ) : (
              <Table>
                <TableHeader>
                  <tr>
                    <TableHead>Source Node</TableHead>
                    <TableHead>Destination Node</TableHead>
                    <TableHead>Distance</TableHead>
                    <TableHead>Est. Transit Time</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                  </tr>
                </TableHeader>
                <TableBody>
                  {edges.map((edge) => (
                    <TableRow key={edge.id}>
                      <TableCell style={{ fontWeight: 600 }}>
                        {edge.sourceLocationName || `Location #${edge.sourceLocationId}`}
                      </TableCell>
                      <TableCell style={{ fontWeight: 600 }}>
                        {edge.destinationLocationName || `Location #${edge.destinationLocationId}`}
                      </TableCell>
                      <TableCell>{edge.distance} km</TableCell>
                      <TableCell>{edge.estimatedTime ? `${edge.estimatedTime} min` : '—'}</TableCell>
                      <TableCell>
                        <StatusBadge status={edge.active ? 'ACTIVE' : 'INACTIVE'} />
                      </TableCell>
                      <TableCell style={{ textAlign: 'right' }}>
                        <Button
                          variant="danger"
                          size="sm"
                          icon={Trash2}
                          onClick={() => setDeletingEdgeId(edge.id)}
                          title="Deactivate edge"
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>
          </div>
        </>
      )}

      {/* Location Modal */}
      <Modal
        isOpen={showLocModal}
        onClose={() => setShowLocModal(false)}
        title={editingLoc ? 'Edit Location' : 'New Location'}
        footer={
          <div className="modal-footer">
            <Button variant="secondary" onClick={() => setShowLocModal(false)}>
              Cancel
            </Button>
            <Button variant="primary" onClick={handleLocSubmit}>
              {editingLoc ? 'Update Location' : 'Create Location'}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleLocSubmit}>
          {locFormError && <div className="alert alert-error mb-4">{locFormError}</div>}
          <Input
            label="Name"
            id="location-name"
            value={locForm.name}
            onChange={(e) => setLocForm({ ...locForm, name: e.target.value })}
            required
          />
          <div style={{ marginBottom: 12 }}>
            <Input
              label="City"
              id="location-city"
              value={locForm.city}
              onChange={(e) => setLocForm({ ...locForm, city: e.target.value })}
              placeholder="Bengaluru"
            />
          </div>

          <LocationPicker
            idPrefix="admin-loc"
            inputId="location-address"
            locateBtnId="find-address-btn"
            label="Location Address & Coordinates"
            placeholder="e.g. MG Road, Indiranagar, Electronic City, Bengaluru"
            address={locForm.address}
            latitude={locForm.latitude}
            longitude={locForm.longitude}
            onChange={({ address, latitude, longitude }) => {
              setLocForm(prev => ({
                ...prev,
                address,
                latitude,
                longitude
              }));
            }}
            error={locFormError}
            markerColor="#6366F1"
            markerLabel="NODE"
            helperText="Use 'Locate address', 'Use my current location', or 'Choose on map' to pinpoint this graph location."
          />

          <Input
            label="Type"
            id="location-type"
            placeholder="e.g. HUB, DEPOT, CUSTOMER"
            value={locForm.locationType}
            onChange={(e) => setLocForm({ ...locForm, locationType: e.target.value })}
          />
        </form>
      </Modal>

      {/* Edge Modal */}
      <Modal
        isOpen={showEdgeModal}
        onClose={() => setShowEdgeModal(false)}
        title="New Road / Graph Edge"
        footer={
          <div className="modal-footer">
            <Button variant="secondary" onClick={() => setShowEdgeModal(false)}>
              Cancel
            </Button>
            <Button variant="primary" onClick={handleEdgeSubmit} disabled={!edgeForm.distance}>
              Create Edge
            </Button>
          </div>
        }
      >
        <form onSubmit={handleEdgeSubmit}>
          {edgeFormError && <div className="alert alert-error mb-4">{edgeFormError}</div>}
          {edgeCalcError && <div className="alert alert-error mb-4">{edgeCalcError}</div>}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Select
              label="Source Location"
              id="edge-source"
              value={edgeForm.sourceLocationId}
              onChange={(e) => {
                setEdgeForm({ ...edgeForm, sourceLocationId: e.target.value, distance: '', estimatedTime: '' });
                setEdgeCalcError('');
              }}
              required
            >
              <option value="">Select source</option>
              {locations.filter(l => l.active).map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </Select>
            <Select
              label="Destination Location"
              id="edge-dest"
              value={edgeForm.destinationLocationId}
              onChange={(e) => {
                setEdgeForm({ ...edgeForm, destinationLocationId: e.target.value, distance: '', estimatedTime: '' });
                setEdgeCalcError('');
              }}
              required
            >
              <option value="">Select destination</option>
              {locations.filter(l => l.active).map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </Select>
          </div>
          <div style={{ marginTop: 12, marginBottom: 16 }}>
            <Button 
              type="button" 
              variant="secondary" 
              onClick={handleCalculateEdgeRoute} 
              loading={calculatingEdge}
              disabled={!edgeForm.sourceLocationId || !edgeForm.destinationLocationId}
            >
              Calculate Road Route
            </Button>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Input
              label="Provider Distance (km)"
              id="edge-distance"
              type="number"
              step="0.1"
              min="0.1"
              value={edgeForm.distance}
              readOnly
              required
            />
            <Input
              label="Provider Est. Time (min)"
              id="edge-time"
              type="number"
              step="1"
              min="0"
              value={edgeForm.estimatedTime}
              readOnly
            />
          </div>
        </form>
      </Modal>

      {/* Approve Modal */}
      <Modal
        isOpen={showApproveModal}
        onClose={() => setShowApproveModal(false)}
        title="Approve & Connect Location"
        footer={
          <div className="modal-footer">
            <Button variant="secondary" onClick={() => setShowApproveModal(false)}>
              Cancel
            </Button>
            <Button 
              variant="primary" 
              onClick={handleApproveSubmit}
              disabled={
                (!approveForm.createIncomingEdge && !approveForm.createOutgoingEdge) ||
                (approveForm.createIncomingEdge && (!approveForm.incomingDistance || !approveForm.incomingEstimatedTime)) ||
                (approveForm.createOutgoingEdge && (!approveForm.outgoingDistance || !approveForm.outgoingEstimatedTime))
              }
            >
              Confirm Integration
            </Button>
          </div>
        }
      >
        <form onSubmit={handleApproveSubmit}>
          <div className="mb-4 text-sm" style={{ color: 'var(--theme-text-muted)' }}>
            Integrating pending node <strong>{approvingLoc?.name}</strong>. Choose an active node to bridge the connection.
          </div>
          {approveFormError && <div className="alert alert-error mb-4">{approveFormError}</div>}
          
          <Select
            label="Connect To Active Node"
            id="approve-connect-to"
            value={approveForm.connectToLocationId}
            onChange={(e) => {
              setApproveForm({ 
                ...approveForm, 
                connectToLocationId: e.target.value,
                incomingDistance: '', incomingEstimatedTime: '',
                outgoingDistance: '', outgoingEstimatedTime: ''
              });
              setApproveFormError('');
            }}
            required
            className="mb-4"
          >
            <option value="">Select an active node</option>
            {locations.filter(l => l.active).map((l) => (
              <option key={l.id} value={l.id}>
                {l.name}
              </option>
            ))}
          </Select>

          <div style={{ marginBottom: 16 }}>
            <Button 
              type="button" 
              variant="secondary" 
              onClick={handleCalculateApproveRoute} 
              loading={calculatingApprove}
              disabled={!approveForm.connectToLocationId || (!approveForm.createIncomingEdge && !approveForm.createOutgoingEdge)}
            >
              Calculate Connection Routes
            </Button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            {/* Incoming Edge */}
            <div style={{ border: '1px solid var(--theme-border)', padding: 12, borderRadius: 6 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                <input
                  type="checkbox"
                  id="create-incoming"
                  checked={approveForm.createIncomingEdge}
                  onChange={(e) => setApproveForm({ 
                    ...approveForm, 
                    createIncomingEdge: e.target.checked,
                    incomingDistance: '', incomingEstimatedTime: '' 
                  })}
                />
                <label htmlFor="create-incoming" style={{ fontWeight: 600, fontSize: 13 }}>
                  Route TO pending node
                </label>
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <Input
                  label="Provider Dist (km)"
                  id="incoming-dist"
                  type="number"
                  step="0.1"
                  min="0.1"
                  disabled={!approveForm.createIncomingEdge}
                  value={approveForm.incomingDistance}
                  readOnly
                  required={approveForm.createIncomingEdge}
                />
                <Input
                  label="Duration (min)"
                  id="incoming-time"
                  type="number"
                  step="1"
                  min="0"
                  disabled={!approveForm.createIncomingEdge}
                  value={approveForm.incomingEstimatedTime}
                  readOnly
                  required={approveForm.createIncomingEdge}
                />
              </div>
            </div>

            {/* Outgoing Edge */}
            <div style={{ border: '1px solid var(--theme-border)', padding: 12, borderRadius: 6 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                <input
                  type="checkbox"
                  id="create-outgoing"
                  checked={approveForm.createOutgoingEdge}
                  onChange={(e) => setApproveForm({ 
                    ...approveForm, 
                    createOutgoingEdge: e.target.checked,
                    outgoingDistance: '', outgoingEstimatedTime: '' 
                  })}
                />
                <label htmlFor="create-outgoing" style={{ fontWeight: 600, fontSize: 13 }}>
                  Route FROM pending node
                </label>
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <Input
                  label="Provider Dist (km)"
                  id="outgoing-dist"
                  type="number"
                  step="0.1"
                  min="0.1"
                  disabled={!approveForm.createOutgoingEdge}
                  value={approveForm.outgoingDistance}
                  readOnly
                  required={approveForm.createOutgoingEdge}
                />
                <Input
                  label="Duration (min)"
                  id="outgoing-time"
                  type="number"
                  step="1"
                  min="0"
                  disabled={!approveForm.createOutgoingEdge}
                  value={approveForm.outgoingEstimatedTime}
                  readOnly
                  required={approveForm.createOutgoingEdge}
                />
              </div>
            </div>
          </div>
        </form>
      </Modal>

      {/* Confirm Location Deactivation */}
      <ConfirmDialog
        isOpen={Boolean(deletingLocId)}
        onClose={() => setDeletingLocId(null)}
        onConfirm={confirmDeleteLoc}
        title="Deactivate Location"
        message="Are you sure you want to deactivate this location? Connected roads and routes may be affected."
        confirmLabel="Deactivate"
        loading={deleteLoading}
      />

      {/* Confirm Edge Deactivation */}
      <ConfirmDialog
        isOpen={Boolean(deletingEdgeId)}
        onClose={() => setDeletingEdgeId(null)}
        onConfirm={confirmDeleteEdge}
        title="Deactivate Edge"
        message="Are you sure you want to deactivate this edge? Dijkstra path calculation will no longer traverse this segment."
        confirmLabel="Deactivate"
        loading={deleteLoading}
      />
    </div>
  );
}
