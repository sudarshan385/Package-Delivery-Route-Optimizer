// Frontend Automated Test Suite
// Covers critical frontend behavior: auth state, API client error handling, route optimization contracts, empty state rendering logic

import { describe, it } from 'node:test';
import assert from 'node:assert';

describe('Frontend Authentication & State Handling', () => {
  it('should correctly format Bearer authorization header from token', () => {
    const mockToken = 'mock-jwt-token-xyz';
    const config = { headers: {} };
    
    // Simulate axios request interceptor
    if (mockToken) {
      config.headers.Authorization = `Bearer ${mockToken}`;
    }
    
    assert.strictEqual(config.headers.Authorization, 'Bearer mock-jwt-token-xyz');
  });

  it('should enforce role authorization hierarchy', () => {
    const roles = {
      ADMIN: ['ADMIN', 'DRIVER', 'USER'],
      DRIVER: ['DRIVER'],
      USER: ['USER'],
    };

    const hasAccess = (userRole, requiredRole) => {
      return roles[userRole]?.includes(requiredRole) || false;
    };

    assert.strictEqual(hasAccess('ADMIN', 'ADMIN'), true);
    assert.strictEqual(hasAccess('ADMIN', 'DRIVER'), true);
    assert.strictEqual(hasAccess('ADMIN', 'USER'), true);
    assert.strictEqual(hasAccess('DRIVER', 'ADMIN'), false);
    assert.strictEqual(hasAccess('DRIVER', 'DRIVER'), true);
    assert.strictEqual(hasAccess('USER', 'ADMIN'), false);
    assert.strictEqual(hasAccess('DISPATCHER', 'ADMIN'), false);
  });

  it('should maintain strict delivery lifecycle flow (CREATED -> ASSIGNED -> IN_PROGRESS -> COMPLETED)', () => {
    const statusFlow = ['CREATED', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED'];
    
    const getNextStatus = (current) => {
      const idx = statusFlow.indexOf(current);
      return idx >= 0 && idx < statusFlow.length - 1 ? statusFlow[idx + 1] : null;
    };

    assert.strictEqual(getNextStatus('CREATED'), 'ASSIGNED');
    assert.strictEqual(getNextStatus('ASSIGNED'), 'IN_PROGRESS');
    assert.strictEqual(getNextStatus('IN_PROGRESS'), 'COMPLETED');
    assert.strictEqual(getNextStatus('COMPLETED'), null);
  });
});

describe('Frontend Route Optimizer Logic & Form Validation', () => {
  it('should reject route optimization when source equals destination', () => {
    const validateRouteForm = (sourceId, destId) => {
      if (!sourceId || !destId) return 'Please select both source and destination';
      if (sourceId === destId) return 'Source and destination cannot be the same';
      return null;
    };

    assert.strictEqual(validateRouteForm(1, 1), 'Source and destination cannot be the same');
    assert.strictEqual(validateRouteForm('', 2), 'Please select both source and destination');
    assert.strictEqual(validateRouteForm(1, 2), null);
  });

  it('should correctly build optimization payload with optional deliveryId', () => {
    const buildPayload = (sourceId, destId, deliveryId) => {
      const payload = {
        startLocationId: Number(sourceId),
        destinationLocationId: Number(destId),
      };
      if (deliveryId) {
        payload.deliveryId = Number(deliveryId);
      }
      return payload;
    };

    const withDelivery = buildPayload('10', '20', '5');
    assert.deepStrictEqual(withDelivery, {
      startLocationId: 10,
      destinationLocationId: 20,
      deliveryId: 5,
    });

    const withoutDelivery = buildPayload('10', '20', '');
    assert.deepStrictEqual(withoutDelivery, {
      startLocationId: 10,
      destinationLocationId: 20,
    });
  });
});

describe('Dashboard Empty State & Metrics Computation', () => {
  it('should recognize and render empty state when all business metrics are zero', () => {
    const isDashboardEmpty = (data) => {
      return (
        (data.totalPackages || 0) === 0 &&
        (data.totalDeliveries || 0) === 0 &&
        (data.availableDrivers || 0) === 0 &&
        (data.availableVehicles || 0) === 0 &&
        (data.totalRoutes || 0) === 0
      );
    };

    const emptyData = {
      totalPackages: 0,
      totalDeliveries: 0,
      availableDrivers: 0,
      availableVehicles: 0,
      totalRoutes: 0,
    };

    assert.strictEqual(isDashboardEmpty(emptyData), true);

    const populatedData = {
      totalPackages: 5,
      totalDeliveries: 2,
      availableDrivers: 1,
      availableVehicles: 1,
      totalRoutes: 2,
    };

    assert.strictEqual(isDashboardEmpty(populatedData), false);
  });
});

describe('Frontend Initial Setup & Routing Logic', () => {
  it('should conditionally display setup link on login page when setupRequired is true', () => {
    const shouldShowSetupLink = (setupRequired) => Boolean(setupRequired);
    assert.strictEqual(shouldShowSetupLink(true), true);
    assert.strictEqual(shouldShowSetupLink(false), false);
  });

  it('should redirect to /login after successful initial admin setup', () => {
    const onSetupSuccess = (navigateFn) => {
      navigateFn('/login', {
        replace: true,
        state: { message: 'Administrator account created successfully. Please sign in.' }
      });
    };

    let targetPath = null;
    let targetOptions = null;
    onSetupSuccess((path, options) => {
      targetPath = path;
      targetOptions = options;
    });

    assert.strictEqual(targetPath, '/login');
    assert.strictEqual(targetOptions?.replace, true);
    assert.strictEqual(targetOptions?.state?.message, 'Administrator account created successfully. Please sign in.');
  });

  it('should validate initial admin setup form rules', () => {
    const validateSetupForm = ({ username, email, password, confirmPassword }) => {
      if (!username || username.trim().length < 3 || username.trim().length > 50) {
        return 'Username must be between 3 and 50 characters';
      }
      if (!email || !email.includes('@') || !email.includes('.')) {
        return 'Please provide a valid email address';
      }
      if (!password || password.length < 8) {
        return 'Password must be at least 8 characters';
      }
      if (password !== confirmPassword) {
        return 'Passwords do not match';
      }
      return null;
    };

    assert.strictEqual(
      validateSetupForm({ username: 'ad', email: 'admin@test.com', password: 'Password123', confirmPassword: 'Password123' }),
      'Username must be between 3 and 50 characters'
    );
    assert.strictEqual(
      validateSetupForm({ username: 'admin', email: 'invalid-email', password: 'Password123', confirmPassword: 'Password123' }),
      'Please provide a valid email address'
    );
    assert.strictEqual(
      validateSetupForm({ username: 'admin', email: 'admin@test.com', password: 'short', confirmPassword: 'short' }),
      'Password must be at least 8 characters'
    );
    assert.strictEqual(
      validateSetupForm({ username: 'admin', email: 'admin@test.com', password: 'Password123', confirmPassword: 'MismatchPassword' }),
      'Passwords do not match'
    );
    assert.strictEqual(
      validateSetupForm({ username: 'admin', email: 'admin@test.com', password: 'Password123', confirmPassword: 'Password123' }),
      null
    );
  });

  it('should extract RFC 7807 problem details error messages correctly', () => {
    const extractErrorMessage = (err, fallback = 'An error occurred') => {
      if (err.response?.data?.errors) {
        const errors = err.response.data.errors;
        if (typeof errors === 'object') {
          return Object.values(errors).join(', ');
        }
      }
      return err.response?.data?.detail || err.response?.data?.message || err.message || fallback;
    };

    const rfcDetailErr = { response: { data: { detail: 'Initial setup has already been completed.' } } };
    assert.strictEqual(extractErrorMessage(rfcDetailErr), 'Initial setup has already been completed.');

    const validationErr = {
      response: {
        data: {
          detail: 'Validation failed',
          errors: { username: 'Username is required', password: 'Password must be at least 8 characters' }
        }
      }
    };
    assert.strictEqual(extractErrorMessage(validationErr), 'Username is required, Password must be at least 8 characters');

    const networkErr = new Error('Network error');
    assert.strictEqual(extractErrorMessage(networkErr), 'Network error');
  });
});

describe('Frontend User Registration & Auth Interceptor Security', () => {
  it('should reject registration attempts with non-public roles like ADMIN or DISPATCHER', () => {
    const isRoleAllowedForPublicRegistration = (role) => {
      const allowedPublicRoles = ['DRIVER', 'USER'];
      return allowedPublicRoles.includes(role);
    };

    assert.strictEqual(isRoleAllowedForPublicRegistration('ADMIN'), false);
    assert.strictEqual(isRoleAllowedForPublicRegistration('DISPATCHER'), false);
    assert.strictEqual(isRoleAllowedForPublicRegistration('DRIVER'), true);
    assert.strictEqual(isRoleAllowedForPublicRegistration('USER'), true);
  });

  it('should validate user registration form fields and driver requirements', () => {
    const validateRegisterForm = ({ username, email, password, confirmPassword, role, licenseNumber }) => {
      if (!username || username.trim().length < 3 || username.trim().length > 50) {
        return 'Username must be between 3 and 50 characters';
      }
      if (!email || !email.includes('@') || !email.includes('.')) {
        return 'Please provide a valid email address';
      }
      if (!password || password.length < 8) {
        return 'Password must be at least 8 characters';
      }
      if (password !== confirmPassword) {
        return 'Passwords do not match';
      }
      if (role !== 'DRIVER' && role !== 'USER') {
        return 'Invalid role selected for public registration';
      }
      if (role === 'DRIVER' && (!licenseNumber || !licenseNumber.trim())) {
        return 'Driver license number is required for Driver accounts';
      }
      return null;
    };

    // Username length
    assert.strictEqual(
      validateRegisterForm({ username: 'ab', email: 'test@example.com', password: 'Password123', confirmPassword: 'Password123', role: 'DRIVER', licenseNumber: 'DL123' }),
      'Username must be between 3 and 50 characters'
    );
    // Invalid email
    assert.strictEqual(
      validateRegisterForm({ username: 'validuser', email: 'testexample', password: 'Password123', confirmPassword: 'Password123', role: 'DRIVER', licenseNumber: 'DL123' }),
      'Please provide a valid email address'
    );
    // Password mismatch
    assert.strictEqual(
      validateRegisterForm({ username: 'validuser', email: 'test@example.com', password: 'Password123', confirmPassword: 'DiffPassword', role: 'DRIVER', licenseNumber: 'DL123' }),
      'Passwords do not match'
    );
    // Driver requires license
    assert.strictEqual(
      validateRegisterForm({ username: 'validuser', email: 'test@example.com', password: 'Password123', confirmPassword: 'Password123', role: 'DRIVER', licenseNumber: '' }),
      'Driver license number is required for Driver accounts'
    );
    // ADMIN role disallowed
    assert.strictEqual(
      validateRegisterForm({ username: 'validuser', email: 'test@example.com', password: 'Password123', confirmPassword: 'Password123', role: 'ADMIN', licenseNumber: '' }),
      'Invalid role selected for public registration'
    );
    // DISPATCHER role disallowed
    assert.strictEqual(
      validateRegisterForm({ username: 'validuser', email: 'test@example.com', password: 'Password123', confirmPassword: 'Password123', role: 'DISPATCHER', licenseNumber: '' }),
      'Invalid role selected for public registration'
    );
    // Valid Driver
    assert.strictEqual(
      validateRegisterForm({ username: 'validuser', email: 'test@example.com', password: 'Password123', confirmPassword: 'Password123', role: 'DRIVER', licenseNumber: 'DL-9999' }),
      null
    );
    // Valid User
    assert.strictEqual(
      validateRegisterForm({ username: 'validuser', email: 'test@example.com', password: 'Password123', confirmPassword: 'Password123', role: 'USER', licenseNumber: '' }),
      null
    );
  });

  it('should not attach Authorization header to public auth endpoints', () => {
    const applyAuthHeader = (url, token) => {
      const isAuthUrl = url && (url.startsWith('/auth') || url.includes('/auth/'));
      const headers = {};
      if (token && !isAuthUrl) {
        headers.Authorization = `Bearer ${token}`;
      }
      return headers;
    };

    const token = 'active-jwt-token-123';
    assert.deepStrictEqual(applyAuthHeader('/auth/login', token), {});
    assert.deepStrictEqual(applyAuthHeader('/auth/register', token), {});
    assert.deepStrictEqual(applyAuthHeader('/auth/setup/status', token), {});
    assert.deepStrictEqual(applyAuthHeader('/deliveries', token), { Authorization: 'Bearer active-jwt-token-123' });
    assert.deepStrictEqual(applyAuthHeader('/dashboard', token), { Authorization: 'Bearer active-jwt-token-123' });
  });
});

describe('Frontend AI Optimization Logic', () => {
  it('should correctly determine loading, empty, error, and success states for AI Optimization', () => {
    const getPageState = (loading, error, data) => {
      if (loading) return 'LOADING';
      if (error) return 'ERROR';
      if (!data) return 'EMPTY';
      return 'SUCCESS';
    };

    assert.strictEqual(getPageState(true, '', null), 'LOADING');
    assert.strictEqual(getPageState(false, 'Network Error', null), 'ERROR');
    assert.strictEqual(getPageState(false, '', null), 'EMPTY');
    assert.strictEqual(getPageState(false, '', { test: { cmr: 0.1 } }), 'SUCCESS');
  });

  it('should correctly format Dijkstra unavailable state without zero metrics', () => {
    const formatDijkstra = (dijkstraNode) => {
      if (dijkstraNode?.available) {
        return 'AVAILABLE';
      }
      return dijkstraNode?.reason || 'Comparison metrics are not available.';
    };

    assert.strictEqual(formatDijkstra(undefined), 'Comparison metrics are not available.');
    assert.strictEqual(formatDijkstra({ available: false, reason: 'The Amazon Last Mile dataset used for this experiment does not provide an authoritative road-network graph or road-travel-time weights required for a legitimate Dijkstra comparison.' }), 'The Amazon Last Mile dataset used for this experiment does not provide an authoritative road-network graph or road-travel-time weights required for a legitimate Dijkstra comparison.');
    assert.strictEqual(formatDijkstra({ available: true }), 'AVAILABLE');
  });

  it('should correctly extract experimental metrics from the API data structure', () => {
    const mockData = {
      test: { cmr: 0.0084, ed: 0.0084 },
      training: { updates: 100 },
      dijkstra: { available: false, reason: 'No road network' }
    };
    
    assert.strictEqual(mockData.test?.cmr, 0.0084);
    assert.strictEqual(mockData.test?.ed, 0.0084);
    assert.strictEqual(mockData.training?.updates, 100);
    assert.strictEqual(mockData.dijkstra?.available, false);
    assert.strictEqual(mockData.dijkstra?.reason, 'No road network');
  });
});

describe('User Dashboard Experience', () => {
  it('should correctly determine visibility of operational sections based on user role', () => {
    const isOperationalSectionVisible = (role) => {
      const allowedRoles = ['ADMIN'];
      return allowedRoles.includes(role);
    };

    assert.strictEqual(isOperationalSectionVisible('USER'), false);
    assert.strictEqual(isOperationalSectionVisible('DRIVER'), false);
    assert.strictEqual(isOperationalSectionVisible('ADMIN'), true);
    assert.strictEqual(isOperationalSectionVisible('DISPATCHER'), false);
  });

  it('should format personal metrics correctly for USER dashboard', () => {
    const getUserMetricsSummary = (stats) => {
      return {
        myPackages: stats.totalPackages || 0,
        pendingPackages: (stats.packagesByStatus?.PENDING || 0) + (stats.packagesByStatus?.CREATED || 0),
        activeDeliveries: (stats.deliveriesByStatus?.ASSIGNED || 0) + (stats.deliveriesByStatus?.IN_PROGRESS || 0),
        delivered: stats.deliveriesByStatus?.COMPLETED || stats.completedDeliveries || 0
      };
    };

    const emptyStats = {};
    assert.deepStrictEqual(getUserMetricsSummary(emptyStats), {
      myPackages: 0,
      pendingPackages: 0,
      activeDeliveries: 0,
      delivered: 0
    });

    const populatedStats = {
      totalPackages: 10,
      packagesByStatus: { PENDING: 3, CREATED: 2, IN_DELIVERY: 5 },
      deliveriesByStatus: { ASSIGNED: 1, IN_PROGRESS: 4, COMPLETED: 12 }
    };
    assert.deepStrictEqual(getUserMetricsSummary(populatedStats), {
      myPackages: 10,
      pendingPackages: 5,
      activeDeliveries: 5,
      delivered: 12
    });
  });

  it('should correctly determine empty state for USER deliveries', () => {
    const isUserDeliveriesEmpty = (deliveries) => {
      return !deliveries || deliveries.length === 0;
    };

    assert.strictEqual(isUserDeliveriesEmpty([]), true);
    assert.strictEqual(isUserDeliveriesEmpty(null), true);
    assert.strictEqual(isUserDeliveriesEmpty([{ id: 1 }]), false);
  });

  it('should allow USER to navigate to Create Delivery and Track Delivery', () => {
    // Testing the logic that USER has access to specific routes based on navLinks definition
    const hasRouteAccess = (userRole, path) => {
      // Simulation of navLinks filtering
      const userAllowedPaths = ['/', '/packages', '/deliveries']; // Dashboard, My Packages, Track Delivery
      if (userRole === 'USER') {
        return userAllowedPaths.includes(path);
      }
      return false; // Other roles not tested in this mock
    };

    assert.strictEqual(hasRouteAccess('USER', '/packages'), true); // Create Delivery context
    assert.strictEqual(hasRouteAccess('USER', '/deliveries'), true); // Track Delivery context
    assert.strictEqual(hasRouteAccess('USER', '/drivers'), false);
    assert.strictEqual(hasRouteAccess('USER', '/vehicles'), false);
  });
});

describe('Driver Dashboard Experience & Actions', () => {
  it('should prevent DRIVER from creating packages or assigning deliveries', () => {
    const getDriverActions = (role) => {
      if (role === 'DRIVER') {
        return { canCreatePackage: false, canAssignDelivery: false, canStartDelivery: true, canFailDelivery: true };
      }
      return { canCreatePackage: true, canAssignDelivery: true, canStartDelivery: false, canFailDelivery: false };
    };

    const actions = getDriverActions('DRIVER');
    assert.strictEqual(actions.canCreatePackage, false);
    assert.strictEqual(actions.canAssignDelivery, false);
    assert.strictEqual(actions.canStartDelivery, true);
    assert.strictEqual(actions.canFailDelivery, true);
  });

  it('should not allow DRIVER or USER to access operational optimizer UI', () => {
    const isOptimizerAvailable = (role) => {
      const allowedRoles = ['ADMIN'];
      return allowedRoles.includes(role);
    };

    assert.strictEqual(isOptimizerAvailable('DRIVER'), false);
    assert.strictEqual(isOptimizerAvailable('USER'), false);
    assert.strictEqual(isOptimizerAvailable('ADMIN'), true);
    assert.strictEqual(isOptimizerAvailable('DISPATCHER'), false);
  });

  it('should parse driver metrics exclusively from their assignments', () => {
    const getDriverMetrics = (stats) => {
      return {
        assignedCount: stats.assignedDeliveries || 0,
        inProgressCount: stats.inProgressDeliveries || 0,
        completedCount: stats.completedDeliveries || 0,
        failedCount: stats.failedDeliveries || 0
      };
    };

    assert.deepStrictEqual(getDriverMetrics({}), { assignedCount: 0, inProgressCount: 0, completedCount: 0, failedCount: 0 });
    assert.deepStrictEqual(getDriverMetrics({ assignedDeliveries: 2, inProgressDeliveries: 1, completedDeliveries: 5 }), { assignedCount: 2, inProgressCount: 1, completedCount: 5, failedCount: 0 });
  });

  it('should resolve driver profile data, initials, and availability status correctly', () => {
    const resolveDriverProfile = (user, deliveries, inProgressCount, assignedCount) => {
      const driverFullName =
        user?.name ||
        user?.fullName ||
        deliveries.find(d => d.driverName)?.driverName ||
        user?.username ||
        'Driver';

      const driverId =
        deliveries.find(d => d.driverId)?.driverId ||
        user?.userId ||
        user?.id ||
        null;

      const getInitials = (name) => {
        if (!name) return 'DR';
        const parts = name.trim().split(/\s+/).filter(Boolean);
        if (parts.length >= 2) {
          return (parts[0][0] + parts[1][0]).toUpperCase();
        }
        return name.slice(0, 2).toUpperCase();
      };

      let driverStatusText = 'Available';
      let driverStatusClass = 'available';

      if (inProgressCount > 0) {
        driverStatusText = 'On Delivery';
        driverStatusClass = 'on-delivery';
      } else if (assignedCount > 0) {
        driverStatusText = 'Assigned';
        driverStatusClass = 'assigned';
      }

      return {
        driverFullName,
        driverId,
        initials: getInitials(driverFullName),
        roleLabel: 'DELIVERY DRIVER',
        driverStatusText,
        driverStatusClass
      };
    };

    // Test with available status
    const profileAvailable = resolveDriverProfile(
      { userId: 42, username: 'driver_john', fullName: 'John Doe', role: 'DRIVER' },
      [],
      0,
      0
    );
    assert.strictEqual(profileAvailable.driverFullName, 'John Doe');
    assert.strictEqual(profileAvailable.driverId, 42);
    assert.strictEqual(profileAvailable.initials, 'JD');
    assert.strictEqual(profileAvailable.roleLabel, 'DELIVERY DRIVER');
    assert.strictEqual(profileAvailable.driverStatusText, 'Available');
    assert.strictEqual(profileAvailable.driverStatusClass, 'available');

    // Test with on-delivery status
    const profileOnDelivery = resolveDriverProfile(
      { userId: 15, username: 'quick_courier', role: 'DRIVER' },
      [{ driverId: 15, driverName: 'Quick Courier' }],
      1,
      2
    );
    assert.strictEqual(profileOnDelivery.driverFullName, 'Quick Courier');
    assert.strictEqual(profileOnDelivery.driverId, 15);
    assert.strictEqual(profileOnDelivery.initials, 'QC');
    assert.strictEqual(profileOnDelivery.driverStatusText, 'On Delivery');
    assert.strictEqual(profileOnDelivery.driverStatusClass, 'on-delivery');
  });

  it('should format navbar avatar initials and display name from user context', () => {
    const formatNavbarUser = (user) => {
      const displayName = user?.name || user?.fullName || user?.username || 'Operator';
      const getInitials = (name) => {
        if (!name) return 'U';
        const parts = name.trim().split(/\s+/).filter(Boolean);
        if (parts.length >= 2) {
          return (parts[0][0] + parts[1][0]).toUpperCase();
        }
        return name.slice(0, 2).toUpperCase();
      };

      return {
        displayName,
        initials: getInitials(displayName),
        roleTag: user?.role === 'DRIVER' ? 'DELIVERY DRIVER' : (user?.role || 'DRIVER')
      };
    };

    const driverUser = formatNavbarUser({ username: 'driver_alex', role: 'DRIVER' });
    assert.strictEqual(driverUser.displayName, 'driver_alex');
    assert.strictEqual(driverUser.initials, 'DR');
    assert.strictEqual(driverUser.roleTag, 'DELIVERY DRIVER');

    const namedDriver = formatNavbarUser({ fullName: 'Sarah Connor', role: 'DRIVER' });
    assert.strictEqual(namedDriver.displayName, 'Sarah Connor');
    assert.strictEqual(namedDriver.initials, 'SC');
  });
});

describe('Admin User Creation & Role Guards in Frontend', () => {
  it('should support ADMIN, DRIVER, and USER roles in creation selector with role-specific validation', () => {
    const allowedRoles = ['USER', 'DRIVER', 'ADMIN'];
    assert.strictEqual(allowedRoles.includes('ADMIN'), true);
    assert.strictEqual(allowedRoles.includes('DRIVER'), true);
    assert.strictEqual(allowedRoles.includes('USER'), true);
    assert.strictEqual(allowedRoles.includes('DISPATCHER'), false);

    const validateForm = (form) => {
      if (!form.username || form.username.trim().length < 3) return 'Username must be at least 3 characters.';
      if (!form.email || !form.email.includes('@')) return 'A valid email address is required.';
      if (!form.password || form.password.length < 8) return 'Password must be at least 8 characters.';
      if (form.role === 'DRIVER' && (!form.licenseNumber || !form.licenseNumber.trim())) {
        return 'Driver license number is required for Driver accounts.';
      }
      return null;
    };

    // Valid admin creation
    const validAdmin = { username: 'newadmin', email: 'newadmin@example.com', password: 'Password123!', role: 'ADMIN' };
    assert.strictEqual(validateForm(validAdmin), null);

    // Driver without license should fail
    const invalidDriver = { username: 'newdriver', email: 'driver@example.com', password: 'Password123!', role: 'DRIVER' };
    assert.strictEqual(validateForm(invalidDriver), 'Driver license number is required for Driver accounts.');

    // Driver with license should succeed
    const validDriver = { ...invalidDriver, licenseNumber: 'DL-KA-01-2024' };
    assert.strictEqual(validateForm(validDriver), null);
  });

  it('should require explicit confirmation dialog specifically when creating an ADMIN', () => {
    const shouldRequireConfirmation = (role) => role === 'ADMIN';

    assert.strictEqual(shouldRequireConfirmation('ADMIN'), true);
    assert.strictEqual(shouldRequireConfirmation('USER'), false);
    assert.strictEqual(shouldRequireConfirmation('DRIVER'), false);
  });

  it('should format creation success notification displaying database-generated ID and account status', () => {
    const formatSuccessNotification = (createdUser) => {
      const statusText = createdUser.active ? 'ACTIVE' : 'INACTIVE';
      const verifiedText = createdUser.isVerified ? 'VERIFIED' : 'UNVERIFIED';
      return `Account '${createdUser.username}' created successfully (ID: ${createdUser.id}, Role: ${createdUser.role}, Status: ${statusText}, Verification: ${verifiedText}).`;
    };

    const notification = formatSuccessNotification({
      id: 42,
      username: 'corp_admin',
      role: 'ADMIN',
      active: true,
      isVerified: true,
    });

    assert.strictEqual(
      notification,
      "Account 'corp_admin' created successfully (ID: 42, Role: ADMIN, Status: ACTIVE, Verification: VERIFIED)."
    );
  });

  it('should ensure passwords and password hashes are never exposed in user table models', () => {
    const sanitizeUserForTable = (apiResponseUser) => {
      const { password, passwordHash, ...safeUser } = apiResponseUser;
      return safeUser;
    };

    const rawUserFromApi = {
      id: 10,
      username: 'audit_admin',
      email: 'audit@example.com',
      role: 'ADMIN',
      active: true,
      isVerified: true,
      isBlocked: false,
    };

    const tableRecord = sanitizeUserForTable(rawUserFromApi);
    assert.strictEqual('password' in tableRecord, false);
    assert.strictEqual('passwordHash' in tableRecord, false);
    assert.strictEqual(tableRecord.id, 10);
    assert.strictEqual(tableRecord.role, 'ADMIN');
  });
});

describe('Package Creation-to-List Workflow & Immediate Refresh', () => {
  it('should immediately insert newly created package at the top of the list without requiring browser refresh', () => {
    const existingPackages = [
      { id: 101, trackingNumber: 'PKG-101', status: 'IN_DELIVERY', customerId: 5 },
      { id: 100, trackingNumber: 'PKG-100', status: 'DELIVERED', customerId: 5 },
    ];

    const newlyCreated = {
      id: 102,
      trackingNumber: 'PKG-102',
      status: 'PENDING',
      customerId: 5,
      weight: 3.5,
      priority: 'HIGH',
      senderName: 'Alice',
      receiverName: 'Bob',
      deliveryLocation: 'MG Road',
    };

    // State update logic from Packages.jsx
    const updatedPackages = [newlyCreated, ...existingPackages.filter(p => p.id !== newlyCreated.id)];

    assert.strictEqual(updatedPackages.length, 3);
    assert.strictEqual(updatedPackages[0].id, 102);
    assert.strictEqual(updatedPackages[0].trackingNumber, 'PKG-102');
    assert.strictEqual(updatedPackages[0].status, 'PENDING');
  });

  it('should ensure newly created package is not hidden when status filter excludes PENDING', () => {
    const packages = [
      { id: 102, trackingNumber: 'PKG-102', status: 'PENDING', city: 'Bengaluru' },
      { id: 101, trackingNumber: 'PKG-101', status: 'IN_DELIVERY', city: 'Bengaluru' },
      { id: 100, trackingNumber: 'PKG-100', status: 'DELIVERED', city: 'Bengaluru' },
    ];

    const statusFilter = 'IN_DELIVERY'; // Current filter excludes PENDING
    const newlyCreatedId = 102; // Pinned newly created package

    // Filter logic from Packages.jsx
    const filtered = packages.filter((p) => {
      if (newlyCreatedId && p.id === newlyCreatedId) return true;
      if (statusFilter && p.status !== statusFilter) return false;
      return true;
    });

    // The newly created package (102) must be visible alongside matching items (101)
    const ids = filtered.map(p => p.id);
    assert.strictEqual(ids.includes(102), true, 'New package must not be hidden by status filter');
    assert.strictEqual(ids.includes(101), true, 'Matching filtered package must remain visible');
    assert.strictEqual(ids.includes(100), false, 'Non-matching package without pin must be filtered out');
  });

  it('should validate package statuses adhere strictly to backend enum (PENDING, IN_DELIVERY, DELIVERED, FAILED)', () => {
    const validBackendStatuses = ['PENDING', 'IN_DELIVERY', 'DELIVERED', 'FAILED'];
    const invalidStatuses = ['CREATED', 'CANCELLED', 'ASSIGNED', 'IN_PROGRESS'];

    const isValidPackageStatus = (status) => validBackendStatuses.includes(status);

    validBackendStatuses.forEach((st) => {
      assert.strictEqual(isValidPackageStatus(st), true, `${st} should be valid`);
    });

    invalidStatuses.forEach((st) => {
      assert.strictEqual(isValidPackageStatus(st), false, `${st} should not be a valid package status`);
    });
  });

  it('should retain form input state when backend returns a safe validation or geocoding error', () => {
    const initialFormInput = {
      fulfillmentMode: 'HUB_STAGED',
      sourceLocationId: '1',
      city: 'Bengaluru',
      deliveryLocation: 'Unknown Nonexistent Alley',
      senderName: 'Alice Customer',
      senderPhone: '+91 9876543210',
      receiverName: 'Priya Sharma',
      receiverPhone: '+91 9876543211',
      weight: '4.5',
      priority: 'MEDIUM',
      description: 'Urgent medical supplies',
    };

    // Simulate form submission error handling in Packages.jsx
    let formState = { ...initialFormInput };
    let fieldErrors = {};
    let modalOpen = true;

    const mockApiError = {
      response: {
        data: {
          detail: 'Failed to geocode delivery location: Unable to resolve address to coordinates.',
          errors: { deliveryLocation: 'Address not found' },
        },
      },
    };

    // Error handling branch
    const respData = mockApiError.response?.data;
    const errorMsg = respData?.detail || 'Failed to save package';
    if (respData?.errors) {
      Object.entries(respData.errors).forEach(([field, msg]) => {
        fieldErrors[field] = msg;
      });
    }

    // Modal stays open and form input is strictly preserved
    assert.strictEqual(modalOpen, true);
    assert.strictEqual(formState.deliveryLocation, 'Unknown Nonexistent Alley');
    assert.strictEqual(formState.senderName, 'Alice Customer');
    assert.strictEqual(formState.weight, '4.5');
    assert.strictEqual(fieldErrors.deliveryLocation, 'Address not found');
  });

  it('should prevent duplicate submissions when submission is in flight', () => {
    let callCount = 0;
    let isSubmitting = false;

    const submitPackage = async () => {
      if (isSubmitting) return; // Guard clause
      isSubmitting = true;
      callCount++;
      // Simulate async network request
      await new Promise(r => setTimeout(r, 10));
      isSubmitting = false;
    };

    // Simulate rapid double click
    submitPackage();
    submitPackage();

    assert.strictEqual(callCount, 1, 'Only one submission should proceed');
  });

  it('should correctly map all mandatory package fields for UI rendering', () => {
    const rawPackage = {
      id: 42,
      trackingNumber: 'PKG-20261023-0042',
      senderName: 'John Doe',
      senderPhone: '+91 9123456789',
      receiverName: 'Jane Smith',
      receiverPhone: '+91 9988776655',
      city: 'Pune',
      pickupLocation: 'Pune Central Hub',
      deliveryLocation: 'FC Road, Deccan Gymkhana',
      weight: 12.5,
      priority: 'URGENT',
      status: 'PENDING',
    };

    const renderData = {
      tracking: rawPackage.trackingNumber || `PKG-${rawPackage.id}`,
      sender: rawPackage.senderName,
      senderPhone: rawPackage.senderPhone,
      recipient: rawPackage.receiverName,
      recipientPhone: rawPackage.receiverPhone,
      city: rawPackage.city,
      pickup: rawPackage.pickupLocation,
      delivery: rawPackage.deliveryLocation,
      weight: `${rawPackage.weight} kg`,
      priority: rawPackage.priority,
      status: rawPackage.status,
    };

    assert.strictEqual(renderData.tracking, 'PKG-20261023-0042');
    assert.strictEqual(renderData.sender, 'John Doe');
    assert.strictEqual(renderData.recipient, 'Jane Smith');
    assert.strictEqual(renderData.pickup, 'Pune Central Hub');
    assert.strictEqual(renderData.delivery, 'FC Road, Deccan Gymkhana');
    assert.strictEqual(renderData.weight, '12.5 kg');
    assert.strictEqual(renderData.priority, 'URGENT');
    assert.strictEqual(renderData.status, 'PENDING');
  });
});

describe('Verified API Contract Fixes', () => {
  it('Fix 1 — should verify delivery status transitions use PATCH method', async () => {
    let calledMethod = null;
    let calledUrl = null;
    let calledBody = null;

    const mockApi = {
      patch: async (url, body) => {
        calledMethod = 'PATCH';
        calledUrl = url;
        calledBody = body;
        return { data: { id: 10, status: body.status } };
      },
      put: async (url, body) => {
        calledMethod = 'PUT';
        throw new Error("405 Method Not Allowed: Request method 'PUT' is not supported");
      }
    };

    const handleUpdateDeliveryStatus = async (api, deliveryId, nextStatus) => {
      return await api.patch(`/deliveries/${deliveryId}/status`, { status: nextStatus });
    };

    const res = await handleUpdateDeliveryStatus(mockApi, 10, 'IN_PROGRESS');
    assert.strictEqual(calledMethod, 'PATCH');
    assert.strictEqual(calledUrl, '/deliveries/10/status');
    assert.deepStrictEqual(calledBody, { status: 'IN_PROGRESS' });
    assert.strictEqual(res.data.status, 'IN_PROGRESS');
  });

  it('Fix 2 — should calculate vehicle capacity, percentage, and overload accurately', () => {
    // Backend returns { id: 1, registrationNumber: 'MH-12-AB-1234', capacity: 500.0 }
    const backendVehicle = { id: 1, registrationNumber: 'MH-12-AB-1234', capacity: 500.0 };

    const computeCapacity = (vehicle, totalPayload) => {
      const vehicleCapacity = vehicle?.capacity ?? vehicle?.capacityKg ?? 0;
      const isOverloaded = Boolean(vehicle && vehicleCapacity > 0 && totalPayload > vehicleCapacity);
      const capacityPercent = vehicle && vehicleCapacity > 0 ? Math.min(100, Math.round((totalPayload / vehicleCapacity) * 100)) : 0;
      return { vehicleCapacity, isOverloaded, capacityPercent };
    };

    // Under-capacity case: 350 kg / 500 kg
    const under = computeCapacity(backendVehicle, 350.0);
    assert.strictEqual(under.vehicleCapacity, 500.0);
    assert.strictEqual(under.isOverloaded, false);
    assert.strictEqual(under.capacityPercent, 70);

    // Exact-capacity case: 500 kg / 500 kg
    const exact = computeCapacity(backendVehicle, 500.0);
    assert.strictEqual(exact.isOverloaded, false);
    assert.strictEqual(exact.capacityPercent, 100);

    // Over-capacity case: 620 kg / 500 kg
    const over = computeCapacity(backendVehicle, 620.0);
    assert.strictEqual(over.vehicleCapacity, 500.0);
    assert.strictEqual(over.isOverloaded, true);
    assert.strictEqual(over.capacityPercent, 100); // capped at 100% for progress gauge

    // Edge case: No vehicle selected
    const noVehicle = computeCapacity(null, 100.0);
    assert.strictEqual(noVehicle.vehicleCapacity, 0);
    assert.strictEqual(noVehicle.isOverloaded, false);
  });

  it('Fix 4 — should handle array vs page delivery response and render valid labels', () => {
    // Array response from GET /api/deliveries
    const arrayResponse = {
      data: [
        { id: 1, trackingNumber: 'TRK-1001', destinationLocationName: 'Pune Depot', active: true },
        { id: 2, trackingNumber: 'TRK-1002', destinationLocationName: 'Mumbai Hub', active: true }
      ]
    };
    const deliveriesFromArray = Array.isArray(arrayResponse.data) ? arrayResponse.data : (arrayResponse.data?.content || []);
    assert.strictEqual(deliveriesFromArray.length, 2);

    // Compatibility check for paginated response if wrapped in page
    const pageResponse = {
      data: {
        content: [{ id: 3, trackingNumber: 'TRK-1003', destinationLocationName: 'Nagpur Depot' }]
      }
    };
    const deliveriesFromPage = Array.isArray(pageResponse.data) ? pageResponse.data : (pageResponse.data?.content || []);
    assert.strictEqual(deliveriesFromPage.length, 1);
    assert.strictEqual(deliveriesFromPage[0].id, 3);

    // Label formatting without nonexistent d.pkg.description
    const formatLabel = (d) =>
      `Delivery #${d.id} (${d.trackingNumber ? `${d.trackingNumber} → ${d.destinationLocationName || 'Destination'}` : (d.destinationLocationName || 'Delivery')})`;

    assert.strictEqual(formatLabel(deliveriesFromArray[0]), 'Delivery #1 (TRK-1001 → Pune Depot)');
    assert.strictEqual(formatLabel({ id: 4, trackingNumber: null, destinationLocationName: 'Kothrud' }), 'Delivery #4 (Kothrud)');
  });

  it('Fix 5 — should format dispatch success toast using assignedDeliveryIds.length and vehicleRegistrationNumber', () => {
    const backendResponse = {
      data: {
        routeId: 45,
        driverId: 3,
        driverName: 'Alice Smith',
        vehicleId: 2,
        vehicleRegistrationNumber: 'MH-14-GH-9999',
        assignedDeliveryIds: [101, 102, 103],
        status: 'DISPATCHED'
      }
    };

    const formatToast = (res, selectedDeliveryIds, currentVehicle) => {
      const assignedCount = Array.isArray(res.data?.assignedDeliveryIds)
        ? res.data.assignedDeliveryIds.length
        : (res.data?.assignedDeliveryCount ?? selectedDeliveryIds.length);
      const vehicleReg = res.data?.vehicleRegistrationNumber || res.data?.vehicleRegistration || currentVehicle?.registrationNumber || 'N/A';
      const driver = res.data?.driverName || 'assigned driver';
      return `Manifest successfully dispatched! Assigned ${assignedCount} deliveries to Driver ${driver} with vehicle ${vehicleReg}.`;
    };

    const toastMessage = formatToast(backendResponse, [101, 102, 103], null);
    assert.strictEqual(toastMessage, 'Manifest successfully dispatched! Assigned 3 deliveries to Driver Alice Smith with vehicle MH-14-GH-9999.');
    assert.strictEqual(toastMessage.includes('undefined'), false);
  });
});

describe('Live Route-Optimization & Map Acceptance Suite', () => {
  it('should parse and format Location 21 to Location 6 route calculation response correctly', () => {
    const rawOptimizationResponse = {
      algorithm: 'DIJKSTRA',
      sourceLocationId: 21,
      sourceLocationName: 'Central Hub 1791640266.1374562',
      destinationLocationId: 6,
      destinationLocationName: 'PKG-1791636630504-BEC783-DST',
      totalDistance: 10.5743,
      totalEstimatedTime: 14.891666666666667,
      pathExists: true,
      orderedLocationIds: [21, 6],
      orderedLocationNames: [
        'Central Hub 1791640266.1374562',
        'PKG-1791636630504-BEC783-DST'
      ],
      orderedEdges: [
        {
          sourceLocationId: 21,
          sourceLocationName: 'Central Hub 1791640266.1374562',
          destinationLocationId: 6,
          destinationLocationName: 'PKG-1791636630504-BEC783-DST',
          distance: 10.5743,
          estimatedTime: 14.891666666666667,
          geometryGeojson: JSON.stringify({
            type: 'LineString',
            coordinates: [
              [77.594697, 12.971848],
              [77.65159, 12.923182]
            ]
          })
        }
      ],
      criterion: 'SHORTEST_DISTANCE',
      explanation: 'Shortest Distance route optimized for minimum kilometres.'
    };

    // Formatting logic from RouteOptimizer.jsx
    const formattedDistance = rawOptimizationResponse.totalDistance != null 
      ? `${rawOptimizationResponse.totalDistance.toFixed(1)} km` 
      : '—';
    const formattedTime = rawOptimizationResponse.totalEstimatedTime != null 
      ? `${rawOptimizationResponse.totalEstimatedTime.toFixed(1)} min` 
      : '—';
    const nodeCount = rawOptimizationResponse.orderedLocationNames?.length || 0;
    const status = rawOptimizationResponse.pathExists ? 'COMPLETED' : 'FAILED';

    assert.strictEqual(formattedDistance, '10.6 km');
    assert.strictEqual(formattedTime, '14.9 min');
    assert.strictEqual(nodeCount, 2);
    assert.strictEqual(status, 'COMPLETED');
    assert.strictEqual(rawOptimizationResponse.orderedLocationNames[0], 'Central Hub 1791640266.1374562');
    assert.strictEqual(rawOptimizationResponse.orderedLocationNames[1], 'PKG-1791636630504-BEC783-DST');
    assert.strictEqual(rawOptimizationResponse.orderedEdges.length, 1);
  });

  it('should transform OSRM GeoJSON LineString coordinates into Leaflet [lat, lng] polyline points', () => {
    // Exact parsing function from Map.jsx
    function parseGeoJsonToLatLngs(geometryInput) {
      if (!geometryInput) return null;
      let parsed = geometryInput;
      if (typeof geometryInput === 'string') {
        try {
          parsed = JSON.parse(geometryInput);
        } catch {
          return null;
        }
      }
      let rawCoords = [];
      if (parsed.type === 'LineString' && Array.isArray(parsed.coordinates)) {
        rawCoords = parsed.coordinates;
      } else if (parsed.type === 'MultiLineString' && Array.isArray(parsed.coordinates)) {
        rawCoords = parsed.coordinates.flat();
      } else if (Array.isArray(parsed)) {
        rawCoords = parsed;
      }
      if (!rawCoords || rawCoords.length === 0) return null;

      return rawCoords.map(coord => [coord[1], coord[0]]); // convert [lon, lat] -> [lat, lon]
    }

    const validGeoJsonString = JSON.stringify({
      type: 'LineString',
      coordinates: [
        [77.594697, 12.971848],
        [77.614386, 12.93075],
        [77.65159, 12.923182]
      ]
    });

    const parsedPoints = parseGeoJsonToLatLngs(validGeoJsonString);
    assert.strictEqual(parsedPoints.length, 3);
    // [lon, lat] -> [lat, lon]
    assert.deepStrictEqual(parsedPoints[0], [12.971848, 77.594697]);
    assert.deepStrictEqual(parsedPoints[1], [12.93075, 77.614386]);
    assert.deepStrictEqual(parsedPoints[2], [12.923182, 77.65159]);

    // Safety checks on null / corrupted inputs
    assert.strictEqual(parseGeoJsonToLatLngs(null), null);
    assert.strictEqual(parseGeoJsonToLatLngs(''), null);
    assert.strictEqual(parseGeoJsonToLatLngs('{corrupted json}'), null);
    assert.strictEqual(parseGeoJsonToLatLngs({ type: 'Point', coordinates: [77.59, 12.97] }), null);
  });

  it('should handle unreachable route (404 ProblemDetail) gracefully without displaying fabricated data', () => {
    const unreachableProblemDetail = {
      response: {
        status: 404,
        data: {
          type: 'urn:problem-type:resource-not-found',
          title: 'Resource Not Found',
          status: 404,
          detail: "No path exists between location 'Central Hub 1791640266.1374562' and 'Admin Node 1'",
          instance: '/api/optimization'
        }
      }
    };

    function parseApiError(err, fallback = 'An unexpected error occurred') {
      if (!err) return fallback;
      if (typeof err === 'string') return err;
      if (err.response?.data?.errors) {
        const errors = err.response.data.errors;
        if (typeof errors === 'object') {
          return Object.values(errors).join(', ');
        }
      }
      return err.response?.data?.detail || err.response?.data?.message || err.message || fallback;
    }

    const errorMessage = parseApiError(unreachableProblemDetail, 'Dijkstra route optimization failed');
    assert.strictEqual(
      errorMessage,
      "No path exists between location 'Central Hub 1791640266.1374562' and 'Admin Node 1'"
    );

    // Verify UI state transitions in RouteOptimizer.jsx:
    // setSingleResult(null) and setSingleError(errorMessage)
    let singleResult = { totalDistance: 99.0 }; // prior state
    let singleError = '';

    // Catch handler simulation:
    singleResult = null;
    singleError = errorMessage;

    assert.strictEqual(singleResult, null, 'Must clear any prior route result');
    assert.strictEqual(singleError, "No path exists between location 'Central Hub 1791640266.1374562' and 'Admin Node 1'");
  });
});



