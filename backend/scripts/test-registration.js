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
    console.error('This script executes write operations that create users and drivers.');
    console.error('To protect development and production databases (packagedeliveryrouteoptimizer),');
    console.error('this script refuses to execute against unverified or development environments.');
    console.error('');
    console.error('Mandatory environment configuration:');
    console.error('  TEST_TARGET_URL=<url-of-dedicated-test-server>');
    console.error('  ALLOW_E2E_DESTRUCTIVE_WRITES=true');
    console.error('');
    console.error('RECOMMENDATION: Use the Java integration test suite');
    console.error('  mvn test -Dtest=RegistrationIntegrationTest');
    console.error('which enforces TestDatabaseGuard and automatically isolates to route_optimizer_test_db.');
    console.error('================================================================================');
    process.exit(1);
}

const api = axios.create({
    baseURL: TARGET_URL,
    headers: { 'Content-Type': 'application/json' }
});

const TIMESTAMP = Date.now();
const dynamicPassword = crypto.randomUUID();

const log = (msg) => console.log(`[TEST] ${msg}`);
const err = (msg) => console.error(`[TEST ERROR] ${msg}`);

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

async function runTests() {
    await verifyServerDatabasePreflight(TARGET_URL);
    log('Testing USER registration...');
    const userRes = await api.post('/api/auth/register', {
        username: `test_user_${TIMESTAMP}`,
        email: `user_${TIMESTAMP}@test.com`,
        password: dynamicPassword,
        fullName: 'Test User',
        role: 'USER'
    });
    if (!userRes.data.accessToken) throw new Error('User registration failed');
    log('USER registration succeeded');

    log('Testing DRIVER registration...');
    const driverRes = await api.post('/api/auth/register', {
        username: `test_driver_${TIMESTAMP}`,
        email: `driver_${TIMESTAMP}@test.com`,
        password: dynamicPassword,
        fullName: 'Test Driver',
        role: 'DRIVER',
        phone: '12345',
        licenseNumber: `LIC_${TIMESTAMP}`
    });
    if (!driverRes.data.accessToken) throw new Error('Driver registration failed');
    log('DRIVER registration succeeded');

    log('Testing DISPATCHER registration rejection...');
    try {
        await api.post('/api/auth/register', {
            username: `test_disp_${TIMESTAMP}`,
            email: `disp_${TIMESTAMP}@test.com`,
            password: dynamicPassword,
            fullName: 'Test Dispatcher',
            role: 'DISPATCHER'
        });
        throw new Error('Expected DISPATCHER registration to be rejected');
    } catch (e) {
        if (e.response && (e.response.status === 400 || e.response.status === 403)) {
            log('DISPATCHER registration successfully rejected');
        } else {
            throw e;
        }
    }

    log('Testing ADMIN registration rejection...');
    try {
        await api.post('/api/auth/register', {
            username: `test_admin_${TIMESTAMP}`,
            email: `admin_${TIMESTAMP}@test.com`,
            password: dynamicPassword,
            fullName: 'Test Admin',
            role: 'ADMIN'
        });
        throw new Error('Expected 403 Forbidden for ADMIN registration');
    } catch (e) {
        if (e.response && (e.response.status === 403 || e.response.status === 400)) {
            log('ADMIN registration successfully rejected');
        } else {
            throw e;
        }
    }

    log('Testing duplicate email registration...');
    try {
        await api.post('/api/auth/register', {
            username: `test_user2_${TIMESTAMP}`,
            email: `user_${TIMESTAMP}@test.com`,
            password: dynamicPassword,
            fullName: 'Test User 2',
            role: 'USER'
        });
        throw new Error('Expected 409 Conflict for duplicate email');
    } catch (e) {
        if (e.response && e.response.status === 409) {
            log('Duplicate email registration successfully rejected');
        } else {
            throw e;
        }
    }

    log('Testing login of newly registered users...');
    await authenticate(`test_user_${TIMESTAMP}`, dynamicPassword);
    log('USER login succeeded');
    await authenticate(`test_driver_${TIMESTAMP}`, dynamicPassword);
    log('DRIVER login succeeded');

    log('Testing initial setup rejection when an admin already exists...');
    try {
        await api.post('/api/auth/setup', {
            username: `fake_admin_${TIMESTAMP}`,
            email: `fake_admin_${TIMESTAMP}@test.com`,
            password: dynamicPassword,
            fullName: 'Fake Admin'
        });
        log('WARNING: Setup succeeded. This means no ADMIN existed in the database before this test.');
    } catch (e) {
        if (e.response && e.response.status === 409) {
            log('Setup successfully rejected because ADMIN exists');
        } else {
            throw e;
        }
    }

    log('ALL TESTS PASSED');
}

runTests().catch(e => {
    err(e.message);
    if (e.response) err(JSON.stringify(e.response.data));
    process.exit(1);
});
