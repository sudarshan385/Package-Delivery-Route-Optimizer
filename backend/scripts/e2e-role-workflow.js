const axios = require('axios');
const crypto = require('crypto');

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
    console.error('');
    console.error('RECOMMENDATION: Use the Java integration test suite');
    console.error('  mvn test -Dtest=BackendEndToEndAndAnalyticsIntegrationTest');
    console.error('which enforces TestDatabaseGuard and automatically isolates to route_optimizer_test_db.');
    console.error('================================================================================');
    process.exit(1);
}

const adminUser = process.env.E2E_ADMIN_USERNAME;
const adminPass = process.env.E2E_ADMIN_PASSWORD;

if (!adminUser || !adminPass) {
    console.error('[SAFETY GUARD] Execution Aborted: Missing E2E_ADMIN_USERNAME or E2E_ADMIN_PASSWORD.');
    console.error('Hardcoded administrator credentials have been removed. Provide credentials via environment.');
    process.exit(1);
}

const api = axios.create({
    baseURL: TARGET_URL,
    headers: { 'Content-Type': 'application/json' }
});

const TIMESTAMP = Date.now();
const testAccountPassword = crypto.randomUUID();

let adminToken = '';
let driverToken = '';
let userToken = '';

// Shared IDs
let locSourceId, locDestId, locInterId;
let driverProfileId;
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

async function authenticate(username, password) {
    const res = await axios.post(`${TARGET_URL}/api/auth/login`, { username, password });
    return res.data.accessToken;
}

async function register(username, email, password, role, extras = {}) {
    await api.post(`/api/users`, {
        username,
        email,
        password,
        fullName: `${role} User`,
        role,
        ...extras
    });
}

async function setupUsers() {
    log('Authenticating administrator...');
    adminToken = await authenticate(adminUser, adminPass);
    api.defaults.headers.common['Authorization'] = `Bearer ${adminToken}`;
    
    // Register Driver User with dynamically generated password
    log('Setting up ephemeral test driver user...');
    await register(`driver_${TIMESTAMP}`, `drv_${TIMESTAMP}@test.com`, testAccountPassword, 'DRIVER', { 
        phone: `555-${String(TIMESTAMP).slice(-4)}`, 
        licenseNumber: `LIC-${TIMESTAMP}` 
    });
    driverToken = await authenticate(`driver_${TIMESTAMP}`, testAccountPassword);
    
    // Fetch Driver Profile created by User creation
    const drvUserRes = await api.get('/api/users');
    const drvUserId = drvUserRes.data.find(u => u.username === `driver_${TIMESTAMP}`).id;
    const allDrivers = await api.get('/api/drivers');
    driverProfileId = allDrivers.data.content.find(d => d.userId === drvUserId).id;

    // Register Customer User with dynamically generated password
    log('Setting up ephemeral test customer user...');
    await register(`customer_${TIMESTAMP}`, `cust_${TIMESTAMP}@test.com`, testAccountPassword, 'USER');
    userToken = await authenticate(`customer_${TIMESTAMP}`, testAccountPassword);

    log('Ephemeral test users configured.');
}

async function setupData() {
    api.defaults.headers.common['Authorization'] = `Bearer ${adminToken}`;
    log('Creating Locations and Edges...');
    const srcRes = await api.post('/api/locations', { name: `E2E Source ${TIMESTAMP}`, address: 'Addr 1', latitude: 10.0, longitude: 10.0, locationType: 'WAREHOUSE' });
    locSourceId = srcRes.data.id;
    const interRes = await api.post('/api/locations', { name: `E2E Intermediate ${TIMESTAMP}`, address: 'Addr 2', latitude: 11.0, longitude: 11.0, locationType: 'STORE' });
    locInterId = interRes.data.id;
    const destRes = await api.post('/api/locations', { name: `E2E Destination ${TIMESTAMP}`, address: 'Addr 3', latitude: 12.0, longitude: 12.0, locationType: 'CUSTOMER' });
    locDestId = destRes.data.id;

    await api.post('/api/edges', { sourceLocationId: locSourceId, destinationLocationId: locInterId, distance: 10.5, estimatedTime: 15.0 });
    await api.post('/api/edges', { sourceLocationId: locInterId, destinationLocationId: locDestId, distance: 5.2, estimatedTime: 8.0 });

    const vehicleRes = await api.post('/api/vehicles', { registrationNumber: `REG-${TIMESTAMP}`, vehicleType: 'VAN', capacity: 100.0, status: 'AVAILABLE' });
    vehicleId = vehicleRes.data.id;
    log('Test routing and vehicle data setup complete.');
}

async function runWorkflow() {
    // 1. USER -> Create Package
    log('1. USER creating package...');
    api.defaults.headers.common['Authorization'] = `Bearer ${userToken}`;
    const pkgRes = await api.post('/api/packages', {
        trackingNumber: `TRK-${TIMESTAMP}`, senderName: 'User Sender', senderPhone: '111', receiverName: 'Receiver', receiverPhone: '222', city: 'City',
        sourceLocationId: locSourceId, destinationLocationId: locDestId, weight: 10.0, priority: 'HIGH', status: 'PENDING',
        pickupLocation: 'Addr 1', deliveryLocation: 'Addr 3'
    });
    packageId = pkgRes.data.id;
    log(`Package created by user.`);

    // 2. ADMIN -> Assign Driver/Vehicle -> Creates Delivery
    log('2. ADMIN scheduling delivery...');
    api.defaults.headers.common['Authorization'] = `Bearer ${adminToken}`;
    
    const deliveryRes = await api.post('/api/deliveries', { packageId: packageId, notes: 'Admin test assignment' });
    deliveryId = deliveryRes.data.id;
    
    await api.post(`/api/deliveries/${deliveryId}/assign`, { driverId: driverProfileId, vehicleId: vehicleId, notes: 'Assigned by admin' });
    log(`Delivery assigned.`);

    // 3. DIJKSTRA -> Optimize Route
    log('3. ADMIN generating route via Dijkstra...');
    const optRes = await api.post('/api/optimization', { startLocationId: locSourceId, destinationLocationId: locDestId, deliveryId: deliveryId });
    routeId = optRes.data.routeId;
    log(`Route optimized.`);

    // 4. DRIVER -> Transitions status
    log('4. DRIVER starting and completing delivery...');
    api.defaults.headers.common['Authorization'] = `Bearer ${driverToken}`;
    
    await api.patch(`/api/deliveries/${deliveryId}/status`, { status: 'IN_PROGRESS' });
    await api.patch(`/api/deliveries/${deliveryId}/status`, { status: 'COMPLETED' });
    log('Delivery completed by driver.');

    // 5. USER -> Track DELIVERED
    log('5. USER verifying package status...');
    api.defaults.headers.common['Authorization'] = `Bearer ${userToken}`;
    const trackRes = await api.get(`/api/packages/${packageId}`);
    if (trackRes.data.status !== 'DELIVERED') {
        throw new Error(`Package status is ${trackRes.data.status}, expected DELIVERED`);
    }
    
    // Check Dashboard API for USER
    const dashRes = await api.get('/api/dashboard');
    if (dashRes.data.totalPackages !== 1) {
        throw new Error('User dashboard should only see 1 package');
    }
    log('User verification successful.');
}

async function runAll() {
    try {
        await verifyServerDatabasePreflight(TARGET_URL);
        await setupUsers();
        await setupData();
        await runWorkflow();
        log('ALL ROLE-BASED TESTS PASSED!');
    } catch (e) {
        err(e.message);
        if (e.response) {
            err(JSON.stringify(e.response.data));
        }
        process.exit(1);
    }
}

runAll();
