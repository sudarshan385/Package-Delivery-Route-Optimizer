const axios = require('axios');

// ============================================================================
// DATABASE SAFETY GUARD & ENVIRONMENT VERIFICATION
// ============================================================================
const TARGET_URL = process.env.TEST_TARGET_URL;
const ALLOW_DESTRUCTIVE = process.env.ALLOW_E2E_DESTRUCTIVE_WRITES === 'true';

if (!TARGET_URL || !ALLOW_DESTRUCTIVE) {
    console.error('================================================================================');
    console.error('[SAFETY GUARD] Execution Aborted: Database Protection Active');
    console.error('================================================================================');
    console.error('This script executes write operations that create deliveries, packages,');
    console.error('locations, and users in the database.');
    console.error('To protect development and production databases (packagedeliveryrouteoptimizer),');
    console.error('this script refuses to execute against unverified or development environments.');
    console.error('');
    console.error('Mandatory environment configuration:');
    console.error('  TEST_TARGET_URL=<url-of-dedicated-test-server>');
    console.error('  ALLOW_E2E_DESTRUCTIVE_WRITES=true');
    console.error('  E2E_ADMIN_USERNAME=<test-admin-username>');
    console.error('  E2E_ADMIN_PASSWORD=<test-admin-password>');
    console.error('================================================================================');
    process.exit(1);
}

const api = axios.create({
    baseURL: TARGET_URL,
    headers: { 'Content-Type': 'application/json' }
});

const TIMESTAMP = Date.now();
let authToken = '';

// Shared IDs
let locSourceId, locInterId, locDestId;
let driverId;
let vehicleId;
let packageId;
let deliveryId;
let routeId;

const log = (msg) => console.log(`[E2E] ${msg}`);
const err = (msg) => console.error(`[E2E ERROR] ${msg}`);

async function verifyServerDatabasePreflight(targetUrl) {
    if (process.env.ALLOW_E2E_DESTRUCTIVE_WRITES !== 'true') {
        err('Safety violation: ALLOW_E2E_DESTRUCTIVE_WRITES must be set to "true".');
        process.exit(1);
    }
    try {
        const preflightUrl = `${targetUrl}/api/test-guard/preflight`;
        log(`Verifying server-side test database preflight: ${preflightUrl}`);
        const res = await axios.get(preflightUrl, { timeout: 5000 });
        if (res.status === 200 && res.data && res.data.verified === true && res.data.environment === 'test') {
            log('Server test database preflight verified: Dedicated test database confirmed.');
            return true;
        } else {
            err(`Preflight verification failed! Unexpected response: ${JSON.stringify(res.data)}`);
            process.exit(1);
        }
    } catch (e) {
        console.error('================================================================================');
        console.error('[SAFETY GUARD] Execution Aborted: Server-side Test Database Preflight Failed');
        console.error('================================================================================');
        console.error('The target server refused preflight verification or is not running under the dedicated');
        console.error('test profile with "route_optimizer_test_db".');
        if (e.response) {
            console.error(`HTTP Status : ${e.response.status}`);
            console.error(`Response    : ${JSON.stringify(e.response.data)}`);
        } else {
            console.error(`Error       : ${e.message}`);
        }
        console.error('To protect development and production databases, all destructive operations are blocked.');
        console.error('================================================================================');
        process.exit(1);
    }
}

async function authenticate() {
    log('Authenticating administrator via environment credentials...');
    const adminUser = process.env.E2E_ADMIN_USERNAME;
    const adminPass = process.env.E2E_ADMIN_PASSWORD;

    if (!adminUser || !adminPass) {
        err('Missing E2E_ADMIN_USERNAME or E2E_ADMIN_PASSWORD.');
        process.exit(1);
    }

    const loginRes = await api.post('/api/auth/login', {
        username: adminUser,
        password: adminPass
    });
    authToken = loginRes.data.accessToken;
    api.defaults.headers.common['Authorization'] = `Bearer ${authToken}`;
    log('Authentication successful.');
}

async function createData() {
    log('Creating Locations...');
    const srcRes = await api.post('/api/locations', { name: `E2E Source ${TIMESTAMP}`, address: 'Addr 1', latitude: 10.0, longitude: 10.0, locationType: 'WAREHOUSE' });
    locSourceId = srcRes.data.id;
    const interRes = await api.post('/api/locations', { name: `E2E Intermediate ${TIMESTAMP}`, address: 'Addr 2', latitude: 11.0, longitude: 11.0, locationType: 'STORE' });
    locInterId = interRes.data.id;
    const destRes = await api.post('/api/locations', { name: `E2E Destination ${TIMESTAMP}`, address: 'Addr 3', latitude: 12.0, longitude: 12.0, locationType: 'CUSTOMER' });
    locDestId = destRes.data.id;
    log(`Created locations: Source=${locSourceId}, Inter=${locInterId}, Dest=${locDestId}`);

    log('Creating Edges...');
    await api.post('/api/edges', { sourceLocationId: locSourceId, destinationLocationId: locInterId, distance: 10.5, estimatedTime: 15.0 });
    await api.post('/api/edges', { sourceLocationId: locInterId, destinationLocationId: locDestId, distance: 5.2, estimatedTime: 8.0 });
    log('Created edges.');

    log('Creating Driver...');
    const driverRes = await api.post('/api/drivers', { name: `Driver E2E ${TIMESTAMP}`, phone: `555-${String(TIMESTAMP).slice(-4)}`, licenseNumber: `LIC-${TIMESTAMP}`, status: 'AVAILABLE' });
    driverId = driverRes.data.id;
    log(`Created driver: ${driverId}`);

    log('Creating Vehicle...');
    const vehicleRes = await api.post('/api/vehicles', { registrationNumber: `REG-${TIMESTAMP}`, vehicleType: 'VAN', capacity: 100.0, status: 'AVAILABLE' });
    vehicleId = vehicleRes.data.id;
    log(`Created vehicle: ${vehicleId}`);

    log('Creating Package...');
    const pkgRes = await api.post('/api/packages', {
        trackingNumber: `TRK-${TIMESTAMP}`, senderName: 'Sender', senderPhone: '111', receiverName: 'Receiver', receiverPhone: '222', city: 'City',
        sourceLocationId: locSourceId, destinationLocationId: locDestId, weight: 10.0, priority: 'HIGH', status: 'PENDING',
        pickupLocation: 'Addr 1', deliveryLocation: 'Addr 3'
    });
    packageId = pkgRes.data.id;
    log(`Created package: ${packageId}`);
}

async function deliveryAndAssignment() {
    log('Creating Delivery...');
    const deliveryRes = await api.post('/api/deliveries', { packageId: packageId, notes: 'E2E test delivery' });
    deliveryId = deliveryRes.data.id;
    
    log('Assigning Delivery...');
    await api.post(`/api/deliveries/${deliveryId}/assign`, { driverId: driverId, vehicleId: vehicleId, notes: 'E2E assign' });
    
    log('Verifying Delivery Assignment...');
    const checkDelivery = await api.get(`/api/deliveries/${deliveryId}`);
    if (checkDelivery.data.packageId !== packageId) throw new Error('Package relation mismatch');
    if (checkDelivery.data.driverId !== driverId) throw new Error('Driver relation mismatch');
    if (checkDelivery.data.vehicleId !== vehicleId) throw new Error('Vehicle relation mismatch');
    if (checkDelivery.data.status !== 'ASSIGNED') throw new Error('Delivery status is not ASSIGNED');
    log('Delivery assignment verified.');
}

async function optimization() {
    log('Running Dijkstra Optimization...');
    const optRes = await api.post('/api/optimization', { startLocationId: locSourceId, destinationLocationId: locDestId, deliveryId: deliveryId });
    if (!optRes.data || !optRes.data.orderedLocationIds || optRes.data.orderedLocationIds.length === 0) throw new Error('Optimization response missing ordered nodes');
    if (optRes.data.totalDistance == null) throw new Error('Optimization missing distance');
    routeId = optRes.data.routeId;
    log(`Optimization successful. Route ID: ${routeId}, Distance: ${optRes.data.totalDistance}`);
}

async function routeRetrieval() {
    log('Retrieving Route...');
    const routeRes = await api.get(`/api/routes/${routeId}`);
    if (routeRes.data.deliveryId !== deliveryId) throw new Error('Route delivery relation mismatch');
    if (routeRes.data.stops.length === 0) throw new Error('Route stops empty');
    log('Route retrieval verified.');
}

async function deliveryLifecycle() {
    log('Testing Lifecycle Transitions...');
    
    // ASSIGNED -> IN_PROGRESS
    await api.patch(`/api/deliveries/${deliveryId}/status`, { status: 'IN_PROGRESS', reason: 'E2E testing start' });
    let checkDelivery = await api.get(`/api/deliveries/${deliveryId}`);
    if (checkDelivery.data.status !== 'IN_PROGRESS') throw new Error('Failed to transition to IN_PROGRESS');
    
    // IN_PROGRESS -> COMPLETED
    await api.patch(`/api/deliveries/${deliveryId}/status`, { status: 'COMPLETED', reason: 'E2E testing finish' });
    checkDelivery = await api.get(`/api/deliveries/${deliveryId}`);
    if (checkDelivery.data.status !== 'COMPLETED') throw new Error('Failed to transition to COMPLETED');
    
    // Check package, driver, vehicle statuses
    const checkPkg = await api.get(`/api/packages/${packageId}`);
    if (checkPkg.data.status !== 'DELIVERED') throw new Error(`Package status is ${checkPkg.data.status}, expected DELIVERED`);
    
    const checkDriver = await api.get(`/api/drivers/${driverId}`);
    if (checkDriver.data.status !== 'AVAILABLE') throw new Error(`Driver status is ${checkDriver.data.status}, expected AVAILABLE`);
    
    const checkVehicle = await api.get(`/api/vehicles/${vehicleId}`);
    if (checkVehicle.data.status !== 'AVAILABLE') throw new Error(`Vehicle status is ${checkVehicle.data.status}, expected AVAILABLE`);
    
    log('Lifecycle transitions verified.');
}

async function negativeTests() {
    log('Running Negative API Tests...');
    // Unauthorized
    try {
        await axios.get(`${BASE_URL}/api/locations/${locSourceId}`);
        throw new Error('Expected 401 Unauthorized');
    } catch (e) {
        if (e.response && e.response.status === 401) log('Unauthorized request correctly blocked.');
        else throw e;
    }
    
    // Invalid transition
    try {
        await api.patch(`/api/deliveries/${deliveryId}/status`, { status: 'CREATED', reason: 'Invalid' });
        throw new Error('Expected 4xx for invalid transition');
    } catch (e) {
        if (e.response && e.response.status >= 400 && e.response.status < 500) log('Invalid lifecycle transition correctly blocked.');
        else throw e;
    }
}

async function testDDQN() {
    log('Testing DDQN endpoint...');
    const ddqnRes = await api.get('/api/experiments/ddqn/latest');
    if (ddqnRes.status === 200) log('DDQN endpoint verified.');
    else throw new Error('DDQN endpoint failed');
}

async function tryDelete(url, name) {
    try {
        await api.delete(url);
        log(`Deleted ${name}`);
    } catch (e) {
        log(`Failed to delete ${name}: ${e.response ? JSON.stringify(e.response.data) : e.message}`);
    }
}

async function cleanup() {
    log('Cleaning up E2E records...');
    await tryDelete(`/api/deliveries/${deliveryId}`, 'delivery');
    await tryDelete(`/api/packages/${packageId}`, 'package');
    await tryDelete(`/api/drivers/${driverId}`, 'driver');
    await tryDelete(`/api/vehicles/${vehicleId}`, 'vehicle');
    await tryDelete(`/api/locations/${locSourceId}`, 'source location');
    await tryDelete(`/api/locations/${locInterId}`, 'inter location');
    await tryDelete(`/api/locations/${locDestId}`, 'dest location');
}

async function runAll() {
    try {
        await verifyServerDatabasePreflight(TARGET_URL);
        await authenticate();
        await createData();
        await deliveryAndAssignment();
        await optimization();
        await routeRetrieval();
        await deliveryLifecycle();
        await negativeTests();
        await testDDQN();
        await cleanup();
        log('ALL TESTS PASSED');
    } catch (e) {
        err(e.message);
        if (e.response) {
            err(JSON.stringify(e.response.data));
        }
        process.exit(1);
    }
}

runAll();
