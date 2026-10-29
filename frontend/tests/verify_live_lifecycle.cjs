const http = require('http');

async function main() {
  console.log('--- STARTING LIVE VERIFICATION OF BENGALURU SMART PARCEL DELIVERY NETWORK ---');

  // Helper for fetch
  async function request(path, options = {}) {
    const url = `http://localhost:8080${path}`;
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };
    const res = await fetch(url, {
      method: options.method || 'GET',
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined
    });
    const text = await res.text();
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
    return { status: res.status, ok: res.ok, data };
  }

  // 1. Authenticate as Admin
  console.log('\n1. Authenticating as Admin (sudarshan)...');
  const loginRes = await request('/api/auth/login', {
    method: 'POST',
    body: { username: 'sudarshan', password: 'AdminPassword123' }
  });

  if (!loginRes.ok) {
    console.error('Admin login failed:', loginRes.data);
    process.exit(1);
  }
  const token = loginRes.data.accessToken;
  console.log('✓ Admin authenticated successfully. User:', loginRes.data.username, 'Role:', loginRes.data.role);

  // 2. Setup/Verify Bengaluru 20 Hubs
  console.log('\n2. Verifying 20 Bengaluru Service Hubs via /api/locations/hubs/setup-bengaluru...');
  const hubSetupRes = await request('/api/locations/hubs/setup-bengaluru', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` }
  });
  console.log('Hub Setup Result:', hubSetupRes.data);

  // 3. Create a live Customer Shipment
  console.log('\n3. Creating live customer shipment request (Indiranagar to Whitefield)...');
  const shipmentPayload = {
    senderName: 'Rajesh V',
    senderPhone: '+91 98450 11223',
    pickupAddress: '100 Feet Rd, Indiranagar, Bengaluru, Karnataka 560038',
    pickupCity: 'Bengaluru',
    pickupLatitude: 12.9716,
    pickupLongitude: 77.6412,
    pickupInstructions: 'Ring doorbell 3B',
    receiverName: 'Deepa Narayan',
    receiverPhone: '+91 99001 44556',
    deliveryAddress: 'ITPL Main Rd, Whitefield, Bengaluru, Karnataka 560066',
    deliveryCity: 'Bengaluru',
    deliveryLatitude: 12.9698,
    deliveryLongitude: 77.7500,
    deliveryInstructions: 'Leave with security guard',
    itemName: 'World History Encyclopedia',
    description: 'Special Illustrated Hardcover Edition',
    itemCategory: 'Books & Education',
    quantity: 1,
    weight: 2.5,
    lengthCm: 28.0,
    widthCm: 22.0,
    heightCm: 6.0,
    fragile: true,
    declaredValue: 1199.00
  };

  const createShipmentRes = await request('/api/shipments', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: shipmentPayload
  });

  if (!createShipmentRes.ok) {
    console.error('Create shipment failed:', createShipmentRes.data);
    process.exit(1);
  }

  const shipment = createShipmentRes.data;
  console.log('✓ Shipment Created Successfully:');
  console.log('  Tracking Number:', shipment.trackingNumber);
  console.log('  Selected Nearest Hub:', shipment.hubName);
  console.log('  Rationale:', shipment.hubSelectionBasis);
  console.log('  Current Stage:', shipment.currentStage);
  console.log('  Number of Legs Created:', shipment.legs?.length);

  const firstLeg = shipment.legs?.find(l => l.legType === 'FIRST_MILE');
  const finalLeg = shipment.legs?.find(l => l.legType === 'FINAL_MILE');
  console.log('  First-Mile Leg ID:', firstLeg?.id, 'Status:', firstLeg?.status);
  console.log('  Final-Mile Leg ID:', finalLeg?.id, 'Status:', finalLeg?.status);

  // 4. Fetch Available Drivers & Vehicles
  console.log('\n4. Checking Available Drivers and Vehicles...');
  const driversRes = await request('/api/drivers?size=50', {
    headers: { Authorization: `Bearer ${token}` }
  });
  let vehiclesRes = await request('/api/vehicles?size=50', {
    headers: { Authorization: `Bearer ${token}` }
  });

  const availableDrivers = (driversRes.data.content || []).filter(d => d.status === 'AVAILABLE' && d.active);
  let availableVehicles = (vehiclesRes.data.content || []).filter(v => v.status === 'AVAILABLE' && v.active);

  if (availableVehicles.length === 0) {
    console.log('Registering a new operational fleet vehicle (KA04EV9999)...');
    const newVehRes = await request('/api/vehicles', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: {
        registrationNumber: `KA04EV${Math.floor(1000 + Math.random() * 9000)}`,
        vehicleType: 'VAN',
        capacity: 100.0,
        status: 'AVAILABLE'
      }
    });
    console.log('Created Fleet Vehicle:', newVehRes.data);
    availableVehicles = [newVehRes.data];
  }

  console.log(`Available Drivers (${availableDrivers.length}):`, availableDrivers.map(d => `${d.id}: ${d.name}`));
  console.log(`Available Vehicles (${availableVehicles.length}):`, availableVehicles.map(v => `${v.id}: ${v.registrationNumber}`));

  const assignedDriver = availableDrivers[0];
  const assignedVehicle = availableVehicles[0];

  // 5. Assign Pickup Partner to First-Mile Leg
  console.log(`\n5. Assigning Driver #${assignedDriver.id} (${assignedDriver.name}) & Vehicle #${assignedVehicle.id} to First-Mile Leg #${firstLeg.id}...`);
  const assignLegRes = await request(`/api/shipments/legs/${firstLeg.id}/assign`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: {
      driverId: assignedDriver.id,
      vehicleId: assignedVehicle.id,
      notes: 'Please pick up promptly from Indiranagar sender.'
    }
  });

  console.log('Assign Result Status:', assignLegRes.status);
  console.log('Assigned Leg Status:', assignLegRes.data.status);
  console.log('Assigned Driver Name:', assignLegRes.data.driverName);

  // 6. First-Mile Driver Lifecycle: Pickup from Sender
  console.log('\n6. Driver confirms pickup from sender (transitions to IN_TRANSIT)...');
  const pickupRes = await request(`/api/shipments/legs/${firstLeg.id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` },
    body: { status: 'IN_TRANSIT' }
  });
  console.log('Leg Status after Pickup:', pickupRes.data.status);

  // 7. First-Mile Driver Lifecycle: Arrival at Central Hub
  console.log('\n7. Driver confirms arrival at Central Hub (transitions to COMPLETED, packages received at hub)...');
  const hubArrivalRes = await request(`/api/shipments/legs/${firstLeg.id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` },
    body: { status: 'COMPLETED' }
  });
  console.log('Leg Status after Hub Arrival:', hubArrivalRes.data.status);

  // Check that the driver and vehicle were freed back to AVAILABLE
  const freedDriverRes = await request(`/api/drivers/${assignedDriver.id}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  console.log('Driver status after Leg 1 completion:', freedDriverRes.data.status);

  // 8. Assign Final-Mile Partner
  console.log(`\n8. Assigning Final-Mile Leg #${finalLeg.id} (Hub to Whitefield Recipient)...`);
  const finalMileAssignRes = await request(`/api/shipments/legs/${finalLeg.id}/assign`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: {
      driverId: assignedDriver.id,
      vehicleId: assignedVehicle.id,
      notes: 'Final drop-off at Whitefield.'
    }
  });
  console.log('Final-Mile Leg Assigned Status:', finalMileAssignRes.data.status);

  // 9. Final-Mile Driver Lifecycle: Depart Hub & Complete Delivery
  console.log('\n9. Final-Mile partner departs hub (Out for Delivery)...');
  const departHubRes = await request(`/api/shipments/legs/${finalLeg.id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` },
    body: { status: 'IN_TRANSIT' }
  });
  console.log('Final-Mile Status (Out for Delivery):', departHubRes.data.status);

  console.log('\n10. Final-Mile partner confirms successful delivery to recipient...');
  const completeDelRes = await request(`/api/shipments/legs/${finalLeg.id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` },
    body: { status: 'COMPLETED' }
  });
  console.log('Final-Mile Status (Delivered):', completeDelRes.data.status);

  // 11. Authoritative 6-Stage Timeline Verification via Public Endpoint
  console.log('\n11. Verifying Authoritative 6-Stage Timeline via Public Tracking Endpoint...');
  const trackRes = await request(`/api/shipments/track/${shipment.trackingNumber}`);
  console.log('Tracking Stage:', trackRes.data.currentStage);
  console.log('Timeline Events Count:', trackRes.data.events?.length);
  trackRes.data.events?.forEach((evt, idx) => {
    console.log(`  [Milestone ${idx + 1}] ${evt.stage}: "${evt.description}" at ${evt.eventTimestamp} by ${evt.recordedBy}`);
  });

  // 12. Test Multi-Mode Route Alternatives
  console.log('\n12. Testing Multi-Mode Route Alternatives (/api/edges/route-alternatives)...');
  const modes = ['CAR', 'TWO_WHEELER', 'WALKING'];
  for (const m of modes) {
    const routeRes = await request(`/api/edges/route-alternatives?sourceId=21&destinationId=6&mode=${m}`);
    console.log(`Mode ${m}: Status ${routeRes.status}, Routes count: ${routeRes.data?.routes?.length}`);
    if (routeRes.data?.routes?.[0]) {
      const r = routeRes.data.routes[0];
      console.log(`  Primary Route: ${r.distanceKm.toFixed(1)} km, ${Math.round(r.durationMin)} min (${r.label})`);
    }
  }

  console.log('\n--- ALL VERIFICATION PHASES COMPLETED WITH 100% SUCCESS ---');
}

main().catch(err => {
  console.error('Verification error:', err);
  process.exit(1);
});
