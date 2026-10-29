import React, { useState, useEffect } from 'react';
import api from '../../api/axios';
import Card from '../ui/Card';
import Button from '../ui/Button';
import Input from '../ui/Input';
import MapComponent from '../ui/Map';
import { Route, Zap, Navigation, MapPin } from 'lucide-react';

export default function DynamicReplanningDemo() {
  const [deliveries, setDeliveries] = useState([]);
  const [selectedInitialDeliveries, setSelectedInitialDeliveries] = useState([]);
  const [newDeliveryId, setNewDeliveryId] = useState('');
  const [driverLocationId, setDriverLocationId] = useState('');
  const [locations, setLocations] = useState([]);
  const [edges, setEdges] = useState([]);

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    // Fetch some basic data to populate dropdowns
    api.get('/locations', { params: { size: 200 } }).then(res => setLocations(res.data.content || []));
    api.get('/edges', { params: { size: 500 } }).then(res => setEdges(res.data.content || []));
    api.get('/deliveries').then(res => setDeliveries(Array.isArray(res.data) ? res.data : (res.data?.content || [])));
  }, []);

  const handleToggleInitialDelivery = (id) => {
    if (selectedInitialDeliveries.includes(id)) {
      setSelectedInitialDeliveries(selectedInitialDeliveries.filter(d => d !== id));
    } else {
      setSelectedInitialDeliveries([...selectedInitialDeliveries, id]);
    }
  };

  const handleRunExperiment = async () => {
    setLoading(true);
    setError('');
    setResult(null);

    try {
      const res = await api.post('/experiments/ddqn/replan', {
        driverLocationId: driverLocationId ? parseInt(driverLocationId) : null,
        currentDeliveryIds: selectedInitialDeliveries,
        newDeliveryId: newDeliveryId ? parseInt(newDeliveryId) : null
      });
      setResult(res.data);
    } catch (err) {
      setError(err.response?.data?.detail || err.message || 'Failed to run dynamic replanning simulation');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card title="DDQN Dynamic Package Delivery Replanning Demonstration" className="mt-8">
      <div className="text-sm text-theme-muted mb-6">
        Simulate a dynamic replanning event using the isolated DDQN research policy.
        This does not mutate production data.
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '24px' }}>
        <div>
          <h3 style={{ fontSize: '14px', fontWeight: 'bold', marginBottom: '12px' }}>Scenario Setup (t = 0)</h3>
          
          <div style={{ marginBottom: '16px' }}>
            <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: '4px' }}>Driver Start Location</label>
            <select 
              value={driverLocationId} 
              onChange={e => setDriverLocationId(e.target.value)}
              style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid var(--theme-border)', backgroundColor: 'var(--theme-bg)' }}
            >
              <option value="">Select driver start location</option>
              {locations.map(loc => (
                <option key={loc.id} value={loc.id}>{loc.name}</option>
              ))}
            </select>
          </div>

          <div style={{ marginBottom: '16px' }}>
            <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: '4px' }}>Initial Deliveries (Select multiple)</label>
            <div style={{ maxHeight: '150px', overflowY: 'auto', border: '1px solid var(--theme-border)', borderRadius: '4px', padding: '8px', backgroundColor: 'var(--theme-elevated)' }}>
              {deliveries.length === 0 ? <div className="text-sm text-theme-muted">No deliveries available.</div> : null}
              {deliveries.map(d => (
                <div key={d.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                  <input 
                    type="checkbox" 
                    checked={selectedInitialDeliveries.includes(d.id)}
                    onChange={() => handleToggleInitialDelivery(d.id)}
                  />
                  <span style={{ fontSize: '13px' }}>
                    Delivery #{d.id} ({d.trackingNumber ? `${d.trackingNumber} → ${d.destinationLocationName || 'Destination'}` : (d.destinationLocationName || 'Delivery')})
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div>
          <h3 style={{ fontSize: '14px', fontWeight: 'bold', marginBottom: '12px' }}>Dynamic Event (t &gt; 0)</h3>
          
          <div style={{ marginBottom: '16px' }}>
            <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: '4px' }}>New Arriving Request (Delivery)</label>
            <select 
              value={newDeliveryId} 
              onChange={e => setNewDeliveryId(e.target.value)}
              style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid var(--theme-border)', backgroundColor: 'var(--theme-bg)' }}
            >
              <option value="">None (Run initial only)</option>
              {deliveries.filter(d => !selectedInitialDeliveries.includes(d.id)).map(d => (
                <option key={d.id} value={d.id}>
                  Delivery #{d.id} ({d.trackingNumber ? `${d.trackingNumber} → ${d.destinationLocationName || 'Destination'}` : (d.destinationLocationName || 'Delivery')})
                </option>
              ))}
            </select>
          </div>

          <div style={{ marginTop: '32px' }}>
            <Button 
              variant="primary" 
              onClick={handleRunExperiment} 
              loading={loading}
              disabled={selectedInitialDeliveries.length === 0 || !driverLocationId}
              style={{ width: '100%' }}
            >
              <Zap size={16} /> Run Research Simulation
            </Button>
          </div>
        </div>
      </div>

      {error && <div className="alert alert-error mb-4">{error}</div>}

      {result && (
        <div style={{ borderTop: '1px solid var(--theme-border)', paddingTop: '24px' }}>
          <h3 style={{ fontSize: '16px', fontWeight: 'bold', marginBottom: '16px' }}>Simulation Results</h3>
          
          {!result.feasible ? (
            <div className="alert alert-error">
              <strong>Experiment Failed or Unreachable Route:</strong> {result.rejectionReason}
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
              <div>
                <h4 style={{ fontSize: '14px', fontWeight: 'bold', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}><Route size={14}/> Initial Plan (t=0)</h4>
                <div style={{ backgroundColor: 'var(--theme-elevated)', padding: '12px', borderRadius: '8px', border: '1px solid var(--theme-border)', marginBottom: '16px' }}>
                  <div style={{ fontSize: '13px', marginBottom: '4px' }}><strong>Calculation Source:</strong> Dijkstra-equivalent TSM-TW</div>
                  <div style={{ fontSize: '13px', marginBottom: '4px' }}><strong>Total Distance:</strong> {result.originalDistance?.toFixed(2) || 0} km</div>
                  <div style={{ fontSize: '13px', marginBottom: '4px' }}><strong>Total Time:</strong> {result.originalTime?.toFixed(0) || 0} min</div>
                  <div style={{ fontSize: '13px', marginTop: '8px' }}><strong>Stop Order:</strong></div>
                  <ol style={{ fontSize: '12px', margin: '4px 0 0 20px', color: 'var(--theme-muted)' }}>
                    {result.initialRoute?.orderedTasks?.map(t => (
                      <li key={t.id}>{t.taskType} (Delivery #{t.deliveryId}) at Loc #{t.locationId}</li>
                    ))}
                  </ol>
                  <div style={{ marginTop: '16px' }}>
                    <MapComponent 
                      locations={locations} 
                      edges={edges} 
                      routePathNames={
                        result.initialRoute?.orderedTasks ? 
                        [locations.find(l => l.id === parseInt(driverLocationId))?.name, ...result.initialRoute.orderedTasks.map(t => locations.find(l => l.id === t.locationId)?.name)].filter(Boolean)
                        : null
                      }
                      height="250px"
                    />
                  </div>
                </div>
              </div>

              <div>
                <h4 style={{ fontSize: '14px', fontWeight: 'bold', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--color-primary)' }}><Navigation size={14}/> Replanned Result (t&gt;0)</h4>
                {result.revisedRoute ? (
                  <div style={{ backgroundColor: 'var(--theme-elevated)', padding: '12px', borderRadius: '8px', border: '2px solid var(--color-primary)' }}>
                    <div style={{ fontSize: '13px', marginBottom: '4px' }}><strong>Calculation Source:</strong> {result.calculationSource}</div>
                    <div style={{ fontSize: '13px', marginBottom: '4px' }}>
                      <strong>New Total Distance:</strong> {result.newDistance?.toFixed(2) || 0} km 
                      {result.newDistance > result.originalDistance ? <span style={{color:'red'}}> (+{(result.newDistance - result.originalDistance).toFixed(2)})</span> : <span style={{color:'green'}}> ({(result.newDistance - result.originalDistance).toFixed(2)})</span>}
                    </div>
                    <div style={{ fontSize: '13px', marginBottom: '4px' }}>
                      <strong>New Total Time:</strong> {result.newTime?.toFixed(0) || 0} min
                    </div>
                    <div style={{ fontSize: '13px', marginTop: '8px' }}><strong>New Stop Order:</strong></div>
                    <ol style={{ fontSize: '12px', margin: '4px 0 0 20px', color: 'var(--theme-text)' }}>
                      {result.revisedRoute?.orderedTasks?.map(t => (
                        <li key={t.id}>{t.taskType} (Delivery #{t.deliveryId}) at Loc #{t.locationId} {t.deliveryId === parseInt(newDeliveryId) ? <span style={{color: 'var(--color-primary)', fontWeight: 'bold'}}>← NEW</span> : ''}</li>
                      ))}
                    </ol>
                    <div style={{ marginTop: '16px' }}>
                      <MapComponent 
                        locations={locations} 
                        edges={edges} 
                        routePathNames={
                          result.revisedRoute?.orderedTasks ? 
                          [locations.find(l => l.id === parseInt(driverLocationId))?.name, ...result.revisedRoute.orderedTasks.map(t => locations.find(l => l.id === t.locationId)?.name)].filter(Boolean)
                          : null
                        }
                        height="250px"
                      />
                    </div>
                  </div>
                ) : (
                  <div className="text-sm text-theme-muted" style={{ padding: '12px' }}>
                    No dynamic event configured, so no replan was executed.
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}
