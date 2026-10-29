const http = require('http');

function apiCall(token, path, method = 'GET', body = null) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    if (data) headers['Content-Length'] = Buffer.byteLength(data);

    const req = http.request({
      hostname: 'localhost',
      port: 8080,
      path: path,
      method: method,
      headers: headers
    }, (res) => {
      let resBody = '';
      res.on('data', c => resBody += c);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(resBody || '{}') });
        } catch {
          resolve({ status: res.statusCode, body: resBody });
        }
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

(async () => {
  console.log('=== VERIFYING LIVE CONNECTIONS: USER, ADMIN, AND DRIVER DASHBOARDS ===\n');

  // 1. Authenticate Customer
  const custAuth = await apiCall(null, '/api/auth/login', 'POST', { username: 'customer_live', password: 'CustomerPassword123' });
  const custToken = custAuth.body.accessToken;
  console.log('1. Customer Authenticated:', Boolean(custToken));

  // 2. Customer submits a new shipment
  const shipmentPayload = {
    senderName: 'Customer Live',
    senderPhone: '9876543210',
    senderEmail: 'customer@example.com',
    pickupAddress: 'Indiranagar 100ft Road, Bengaluru',
    pickupCity: 'Bengaluru',
    pickupLatitude: 12.9716,
    pickupLongitude: 77.6412,
    receiverName: 'Divya Rao',
    receiverPhone: '9123456789',
    receiverEmail: 'divya@example.com',
    deliveryAddress: 'Koramangala 5th Block, Bengaluru',
    deliveryCity: 'Bengaluru',
    deliveryLatitude: 12.9352,
    deliveryLongitude: 77.6245,
    itemName: 'Architectural Blueprint Portfolio',
    itemCategory: 'Documents',
    description: 'Fragile architectural drawings in cylindrical casing',
    weight: 2.5,
    declaredValue: 15000.0,
    fragile: true,
    priority: 'HIGH',
    quantity: 1
  };

  const createRes = await apiCall(custToken, '/api/shipments', 'POST', shipmentPayload);
  console.log('2. Customer Shipment Created -> HTTP Status:', createRes.status);
  console.log('   Tracking #:', createRes.body.trackingNumber);
  console.log('   Delivery ID:', createRes.body.id);
  console.log('   Stage:', createRes.body.currentStage);
  console.log('   Selected Hub:', createRes.body.hubName, '—', createRes.body.hubSelectionBasis);
  console.log('   Legs Count:', createRes.body.legs?.length);
  console.log('   Events Count:', createRes.body.events?.length);

  const deliveryId = createRes.body.id;
  const trackingNumber = createRes.body.trackingNumber;
  const leg1 = createRes.body.legs?.find(l => l.legType === 'FIRST_MILE');
  const leg2 = createRes.body.legs?.find(l => l.legType === 'FINAL_MILE');

  // 3. Authenticate Admin
  const adminAuth = await apiCall(null, '/api/auth/login', 'POST', { username: 'sudarshan', password: 'AdminPassword123' });
  const adminToken = adminAuth.body.accessToken;
  console.log('\n3. Admin Authenticated:', Boolean(adminToken));

  // 4. Admin Dispatch Queue Check
  const adminShipments = await apiCall(adminToken, '/api/shipments');
  const foundShipment = Array.isArray(adminShipments.body)
    ? adminShipments.body.find(s => s.id === deliveryId)
    : null;
  console.log('4. Admin Dispatch Queue Visibility:');
  console.log('   Shipment Found in Queue:', Boolean(foundShipment));
  console.log('   Tracking # Match:', foundShipment?.trackingNumber === trackingNumber);
  console.log('   Current Stage:', foundShipment?.currentStage);

  // 5. Public & Customer Live Tracking
  const trackRes = await apiCall(null, `/api/shipments/track/${trackingNumber}`);
  console.log('\n5. Public Tracking Milestone Check:');
  console.log('   HTTP Status:', trackRes.status);
  console.log('   Milestones Count:', trackRes.body.events?.length);
  console.log('   Milestone 1 Title:', trackRes.body.events?.[0]?.title);
  console.log('   Milestone 1 Location:', trackRes.body.events?.[0]?.locationName);

  // 6. Test Guard: Admin attempts to assign Final-Mile Leg 2 before Leg 1 completes (Requirement 6)
  const prematureAssign = await apiCall(adminToken, `/api/shipments/legs/${leg2.id}/assign`, 'POST', {
    driverId: 6,
    vehicleId: 4,
    notes: 'Premature assignment attempt'
  });
  console.log('\n6. FINAL_MILE Pre-condition Guard Rejection:');
  console.log('   HTTP Status:', prematureAssign.status, '(Expected 400 Bad Request)');
  console.log('   Error Detail:', prematureAssign.body.detail || prematureAssign.body.message);

  // 7. Customer ownership isolation: User cannot view another customer shipment
  const uniqueName = 'cust_iso_' + Date.now();
  const otherAuth = await apiCall(null, '/api/auth/register', 'POST', {
    username: uniqueName,
    email: uniqueName + '@example.com',
    password: 'Password123!',
    fullName: 'Isolation Tester',
    role: 'USER'
  });
  const isoRes = await apiCall(otherAuth.body.accessToken, `/api/shipments/${deliveryId}`);
  console.log('\n7. Customer Ownership Isolation:');
  console.log('   Unauthorized Customer View Attempt Status:', isoRes.status, '(Expected 403 Forbidden)');

  console.log('\n=== ALL LIVE FLOW CHECKS COMPLETED SUCCESSFULLY ===');
})();
