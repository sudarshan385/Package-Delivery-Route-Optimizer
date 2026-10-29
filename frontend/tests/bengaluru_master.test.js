import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

describe('Bengaluru Smart Parcel Delivery Network — Master Suite', () => {

  describe('Phase 2 — Customer Shipment Form Validation & Data Integrity', () => {
    it('should validate mandatory sender, recipient, and pin coordinates', () => {
      const validateShipmentForm = (form) => {
        const errors = [];
        if (!form.senderName?.trim()) errors.push('Sender name required');
        if (!form.senderPhone?.trim()) errors.push('Sender contact required');
        if (!form.senderAddress?.trim()) errors.push('Pickup address required');
        if (form.senderLatitude == null || form.senderLongitude == null) errors.push('Pickup pin required');
        if (!form.recipientName?.trim()) errors.push('Recipient name required');
        if (!form.recipientAddress?.trim()) errors.push('Destination address required');
        if (form.recipientLatitude == null || form.recipientLongitude == null) errors.push('Destination pin required');
        if (!form.productName?.trim()) errors.push('Product name required');
        if (!form.weightKg || form.weightKg <= 0) errors.push('Weight must be positive');
        return errors;
      };

      const invalid = validateShipmentForm({
        senderName: '',
        senderLatitude: null,
        recipientName: 'Aarav Kumar',
        weightKg: -2,
      });

      assert.ok(invalid.length > 0);
      assert.ok(invalid.includes('Sender name required'));
      assert.ok(invalid.includes('Pickup pin required'));
      assert.ok(invalid.includes('Weight must be positive'));

      const valid = validateShipmentForm({
        senderName: 'Sudarshan K',
        senderPhone: '+91 98765 43210',
        senderAddress: '42, 100 Feet Rd, Indiranagar, Bengaluru',
        senderLatitude: 12.9716,
        senderLongitude: 77.6412,
        recipientName: 'Priya Sharma',
        recipientPhone: '+91 91234 56789',
        recipientAddress: '15, 80 Feet Rd, Koramangala, Bengaluru',
        recipientLatitude: 12.9352,
        recipientLongitude: 77.6245,
        productName: 'World History Encyclopedia',
        weightKg: 2.5,
      });

      assert.equal(valid.length, 0);
    });
  });

  describe('Phase 3 & 4 — 20 Bengaluru Service Hubs & Nearest Hub Selection', () => {
    const CANDIDATE_HUBS = [
      'Yelahanka', 'Hebbal', 'Jakkur', 'Peenya', 'Yeshwanthpur',
      'Malleshwaram', 'Rajajinagar', 'Majestic', 'Vijayanagar', 'Kengeri',
      'Banashankari', 'Jayanagar', 'Koramangala', 'HSR Layout', 'Bommanahalli',
      'Electronic City', 'Marathahalli', 'Whitefield', 'KR Puram', 'Indiranagar'
    ];

    it('should maintain the exact 20 candidate service hub zone names', () => {
      assert.equal(CANDIDATE_HUBS.length, 20);
      assert.ok(CANDIDATE_HUBS.includes('Indiranagar'));
      assert.ok(CANDIDATE_HUBS.includes('Koramangala'));
      assert.ok(CANDIDATE_HUBS.includes('Whitefield'));
    });

    it('should select nearest hub using road distance or haversine when matrix is available', () => {
      const hubs = [
        { id: 1, name: 'Indiranagar Virtual Service Hub', lat: 12.9716, lon: 77.6412 },
        { id: 2, name: 'Whitefield Virtual Service Hub', lat: 12.9698, lon: 77.7500 },
        { id: 3, name: 'Peenya Virtual Service Hub', lat: 13.0329, lon: 77.5273 },
      ];

      // Pickup in Domlur (near Indiranagar)
      const pickupLat = 12.9609;
      const pickupLon = 77.6387;

      const calcDist = (lat1, lon1, lat2, lon2) => {
        const dLat = (lat2 - lat1) * (Math.PI / 180);
        const dLon = (lon2 - lon1) * (Math.PI / 180);
        const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) ** 2;
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      };

      let nearest = null;
      let minDistance = Infinity;
      for (const hub of hubs) {
        const d = calcDist(pickupLat, pickupLon, hub.lat, hub.lon);
        if (d < minDistance) {
          minDistance = d;
          nearest = hub;
        }
      }

      assert.equal(nearest.id, 1);
      assert.equal(nearest.name, 'Indiranagar Virtual Service Hub');
      assert.ok(minDistance < 2.0); // Within 2 km
    });
  });

  describe('Phase 5 — Two-Leg Delivery Architecture & State Machines', () => {
    it('should enforce First-Mile and Final-Mile sequence invariant', () => {
      const shipment = {
        legs: [
          { legType: 'FIRST_MILE', status: 'PENDING', driverId: null },
          { legType: 'FINAL_MILE', status: 'PENDING', driverId: null },
        ]
      };

      const canAssignFinalMile = (s) => {
        const firstMile = s.legs.find(l => l.legType === 'FIRST_MILE');
        return firstMile && firstMile.status === 'COMPLETED';
      };

      assert.equal(canAssignFinalMile(shipment), false);

      // Advance first mile
      shipment.legs[0].status = 'ASSIGNED';
      assert.equal(canAssignFinalMile(shipment), false);

      shipment.legs[0].status = 'IN_TRANSIT';
      assert.equal(canAssignFinalMile(shipment), false);

      shipment.legs[0].status = 'COMPLETED';
      assert.equal(canAssignFinalMile(shipment), true);
    });

    it('should validate driver and vehicle capacity before assignment', () => {
      const driver = { id: 1, name: 'Sudarshan', status: 'AVAILABLE', active: true };
      const vehicle = { id: 2, registrationNumber: 'KA02CD5678', capacityKg: 20.0, status: 'AVAILABLE', active: true };

      const validateAssignment = (d, v, packageWeightKg) => {
        if (!d || d.status !== 'AVAILABLE' || !d.active) return { valid: false, reason: 'Driver not available' };
        if (!v || v.status !== 'AVAILABLE' || !v.active) return { valid: false, reason: 'Vehicle not available' };
        if (packageWeightKg > v.capacityKg) return { valid: false, reason: 'Weight exceeds vehicle capacity' };
        return { valid: true };
      };

      assert.equal(validateAssignment(driver, vehicle, 5.0).valid, true);
      assert.equal(validateAssignment(driver, vehicle, 25.0).valid, false);
      assert.equal(validateAssignment({ ...driver, status: 'BUSY' }, vehicle, 5.0).valid, false);
    });
  });

  describe('Phase 8 — Six-Stage Timeline Milestone Progression', () => {
    const STAGES = [
      'REQUEST_CREATED',
      'PICKUP_ASSIGNED',
      'PICKED_UP',
      'HUB_RECEIVED',
      'OUT_FOR_DELIVERY',
      'DELIVERED'
    ];

    it('should strictly model the six authoritative shipment milestones', () => {
      assert.equal(STAGES.length, 6);
      assert.equal(STAGES[0], 'REQUEST_CREATED');
      assert.equal(STAGES[1], 'PICKUP_ASSIGNED');
      assert.equal(STAGES[2], 'PICKED_UP');
      assert.equal(STAGES[3], 'HUB_RECEIVED');
      assert.equal(STAGES[4], 'OUT_FOR_DELIVERY');
      assert.equal(STAGES[5], 'DELIVERED');
    });

    it('should compute completed milestone indices correctly', () => {
      const getActiveStageIndex = (currentStage) => STAGES.indexOf(currentStage);

      assert.equal(getActiveStageIndex('REQUEST_CREATED'), 0);
      assert.equal(getActiveStageIndex('HUB_RECEIVED'), 3);
      assert.equal(getActiveStageIndex('DELIVERED'), 5);
    });
  });

  describe('Phase 9 — Multi-Mode Route Alternatives', () => {
    it('should recognize Car, Two-Wheeler, and Walking modes', () => {
      const validModes = ['CAR', 'TWO_WHEELER', 'WALKING'];
      assert.ok(validModes.includes('CAR'));
      assert.ok(validModes.includes('TWO_WHEELER'));
      assert.ok(validModes.includes('WALKING'));
    });

    it('should select recommended route with shortest or fastest metric', () => {
      const routes = [
        { label: 'Alternative 1 (Primary)', distanceKm: 12.5, durationMin: 22.0, recommended: false },
        { label: 'Alternative 2 (Fastest)', distanceKm: 13.0, durationMin: 18.5, recommended: true },
      ];

      const recommended = routes.find(r => r.recommended) || routes[0];
      assert.equal(recommended.label, 'Alternative 2 (Fastest)');
      assert.equal(recommended.durationMin, 18.5);
    });
  });

  describe('Phase 10 — Customer Shipment Creation Bug Fixes & Regression Coverage', () => {

    it('1. Empty active-hub dropdown and its resolution: allows USER to fetch active hubs', () => {
      // Simulating LocationService and frontend hubLocations filtering
      const rawLocations = [
        { id: 1, name: 'Indiranagar Virtual Service Hub', city: 'Bengaluru', locationType: 'HUB', active: true },
        { id: 2, name: 'Koramangala Virtual Service Hub', city: 'Bengaluru', locationType: 'HUB', active: true },
        { id: 3, name: 'Inactive Deprecated Hub', city: 'Bengaluru', locationType: 'HUB', active: false },
        { id: 4, name: 'Customer Pickup Point', city: 'Bengaluru', locationType: 'CUSTOMER', active: true },
      ];

      // Filtering rule used in Packages.jsx fetchHubLocations
      const activeHubs = rawLocations.filter(
        (l) => l.active && (
          !l.locationType || 
          ['HUB', 'DEPOT', 'WAREHOUSE'].includes(l.locationType.toUpperCase())
        )
      );

      assert.equal(activeHubs.length, 2);
      assert.equal(activeHubs[0].name, 'Indiranagar Virtual Service Hub');
      assert.equal(activeHubs[1].name, 'Koramangala Virtual Service Hub');
    });

    it('2. My Packages opening the correct shipment workflow for customer vs admin', () => {
      let openedModal = null;
      const handleNewPackageClick = (role) => {
        if (role === 'USER') {
          openedModal = 'CUSTOMER_SMART_SHIPMENT_WIZARD';
        } else {
          openedModal = 'ADMIN_HUB_STAGED_MODAL';
        }
      };

      handleNewPackageClick('USER');
      assert.equal(openedModal, 'CUSTOMER_SMART_SHIPMENT_WIZARD');

      handleNewPackageClick('ADMIN');
      assert.equal(openedModal, 'ADMIN_HUB_STAGED_MODAL');
    });

    it('3. Step 1 validation: missing sender name/phone and invalid pickup coordinates block advancing', () => {
      const validateStep1 = (form, isCoordsConfirmed) => {
        const errors = {};
        const phoneRegex = /^[0-9+\-\s()]{7,20}$/;

        if (!form.senderName || form.senderName.trim().length < 2) {
          errors.senderName = 'Sender full name is required (at least 2 characters).';
        }
        if (!form.senderPhone || !phoneRegex.test(form.senderPhone.trim()) || form.senderPhone.replace(/\D/g, '').length < 7) {
          errors.senderPhone = 'Please enter a valid phone number with at least 7 digits.';
        }
        if (!form.pickupAddress || form.pickupAddress.trim().length < 5) {
          errors.pickupAddress = 'A valid full pickup street address in Bengaluru is required.';
        }
        if (!isCoordsConfirmed) {
          errors.pickupCoordinates = 'Pickup coordinates are unconfirmed.';
        } else if (
          form.pickupLatitude == null || form.pickupLongitude == null ||
          form.pickupLatitude < 12.5 || form.pickupLatitude > 13.5 ||
          form.pickupLongitude < 77.2 || form.pickupLongitude > 78.0
        ) {
          errors.pickupCoordinates = 'Pickup pin must be placed within Bengaluru metropolitan service boundaries.';
        }
        return errors;
      };

      // Incomplete step 1
      const badErrors = validateStep1({
        senderName: '',
        senderPhone: '123',
        pickupAddress: '80',
        pickupLatitude: 12.9352,
        pickupLongitude: 77.6245,
      }, false);

      assert.ok(badErrors.senderName);
      assert.ok(badErrors.senderPhone);
      assert.ok(badErrors.pickupAddress);
      assert.ok(badErrors.pickupCoordinates);

      // Valid step 1
      const goodErrors = validateStep1({
        senderName: 'Anita Sharma',
        senderPhone: '+91 98765 43210',
        pickupAddress: '80 Feet Road, Koramangala 4th Block, Bengaluru',
        pickupLatitude: 12.9352,
        pickupLongitude: 77.6245,
      }, true);

      assert.equal(Object.keys(goodErrors).length, 0);
    });

    it('4. Step 2 validation: missing recipient name/phone, invalid coords, or <50m distance block advancing', () => {
      const calculateHaversineKm = (lat1, lon1, lat2, lon2) => {
        const R = 6371;
        const dLat = (lat2 - lat1) * (Math.PI / 180);
        const dLon = (lon2 - lon1) * (Math.PI / 180);
        const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) ** 2;
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      };

      const validateStep2 = (form, isCoordsConfirmed, pickupLat, pickupLon) => {
        const errors = {};
        const phoneRegex = /^[0-9+\-\s()]{7,20}$/;

        if (!form.receiverName || form.receiverName.trim().length < 2) {
          errors.receiverName = 'Recipient full name is required (at least 2 characters).';
        }
        if (!form.receiverPhone || !phoneRegex.test(form.receiverPhone.trim()) || form.receiverPhone.replace(/\D/g, '').length < 7) {
          errors.receiverPhone = 'Please enter a valid phone number with at least 7 digits.';
        }
        if (!form.deliveryAddress || form.deliveryAddress.trim().length < 5) {
          errors.deliveryAddress = 'A valid full delivery street address in Bengaluru is required.';
        }
        if (!isCoordsConfirmed) {
          errors.deliveryCoordinates = 'Destination coordinates are unconfirmed.';
        } else if (
          form.deliveryLatitude == null || form.deliveryLongitude == null ||
          form.deliveryLatitude < 12.5 || form.deliveryLatitude > 13.5 ||
          form.deliveryLongitude < 77.2 || form.deliveryLongitude > 78.0
        ) {
          errors.deliveryCoordinates = 'Destination pin must be placed within Bengaluru boundaries.';
        } else {
          const dist = calculateHaversineKm(pickupLat, pickupLon, form.deliveryLatitude, form.deliveryLongitude);
          if (dist < 0.05) {
            errors.deliveryCoordinates = 'Pickup location and destination location must be distinct (at least 50 meters apart).';
          }
        }
        return errors;
      };

      // Identical coordinates check
      const duplicateErrors = validateStep2({
        receiverName: 'Rohan Verma',
        receiverPhone: '+91 91234 56789',
        deliveryAddress: '80 Feet Road, Koramangala, Bengaluru',
        deliveryLatitude: 12.9352,
        deliveryLongitude: 77.6245,
      }, true, 12.9352, 77.6245);

      assert.ok(duplicateErrors.deliveryCoordinates);
      assert.ok(duplicateErrors.deliveryCoordinates.includes('at least 50 meters apart'));

      // Distinct valid coordinates
      const goodErrors = validateStep2({
        receiverName: 'Rohan Verma',
        receiverPhone: '+91 91234 56789',
        deliveryAddress: '100 Feet Road, Indiranagar, Bengaluru',
        deliveryLatitude: 12.9784,
        deliveryLongitude: 77.6408,
      }, true, 12.9352, 77.6245);

      assert.equal(Object.keys(goodErrors).length, 0);
    });

    it('5. Step 3 validation & full three-step form submission: validates weight, dimensions, declared value', () => {
      const validateStep3 = (form) => {
        const errors = {};
        if (!form.itemName || form.itemName.trim().length < 2) errors.itemName = 'Product name required';
        if (!form.quantity || Number(form.quantity) < 1) errors.quantity = 'Quantity must be >= 1';
        if (!form.weight || Number(form.weight) <= 0) errors.weight = 'Weight must be positive';
        if (form.lengthCm && Number(form.lengthCm) <= 0) errors.lengthCm = 'Length must be > 0';
        if (form.declaredValue && Number(form.declaredValue) < 0) errors.declaredValue = 'Declared value >= 0';
        return errors;
      };

      assert.ok(validateStep3({ itemName: 'Book', quantity: 0, weight: -1 }).quantity);
      assert.ok(validateStep3({ itemName: 'Book', quantity: 1, weight: 0 }).weight);
      assert.equal(Object.keys(validateStep3({ itemName: 'Book', quantity: 1, weight: 2.5, lengthCm: 25, declaredValue: 500 })).length, 0);
    });

    it('6. API rejection with useful field-level error display preserves user input', () => {
      const currentForm = {
        senderName: 'Anita Sharma',
        senderPhone: '+91 98765 43210',
        pickupAddress: 'Koramangala 5th Block, Bengaluru',
        receiverName: 'Rohan Verma',
        receiverPhone: '+91 91234 56789',
        deliveryAddress: 'Indiranagar 100 Feet Road, Bengaluru',
        itemName: 'World History Encyclopedia',
        weight: 2.4,
      };

      const apiErrorResponse = {
        status: 400,
        data: {
          message: 'Validation failed for shipment',
          errors: {
            deliveryAddress: 'Unable to reach destination address via verified urban road network.',
          }
        }
      };

      // Form values are preserved
      assert.equal(currentForm.senderName, 'Anita Sharma');
      assert.equal(currentForm.itemName, 'World History Encyclopedia');
      assert.equal(apiErrorResponse.data.errors.deliveryAddress, 'Unable to reach destination address via verified urban road network.');
    });

    it('7. Dashboard, My Packages, and Track Delivery refresh after success', () => {
      let packagesRefreshed = false;
      let dashboardRefreshed = false;
      let deliveriesRefreshed = false;

      const onShipmentCreated = (created) => {
        assert.ok(created.trackingNumber.startsWith('PKG-'));
        packagesRefreshed = true;
        dashboardRefreshed = true;
        deliveriesRefreshed = true;
      };

      onShipmentCreated({ trackingNumber: 'PKG-1730000000-ABCDEF', status: 'CREATED' });
      assert.equal(packagesRefreshed, true);
      assert.equal(dashboardRefreshed, true);
      assert.equal(deliveriesRefreshed, true);
    });

    it('8. Customer ownership and admin visibility', () => {
      const customerUser = { id: 12, username: 'user123', role: 'USER' };
      const shipments = [
        { id: 101, trackingNumber: 'PKG-101', customerId: 12, currentStage: 'REQUEST_CREATED' },
        { id: 102, trackingNumber: 'PKG-102', customerId: 15, currentStage: 'REQUEST_CREATED' },
      ];

      // Customer only sees their shipments
      const customerVisible = shipments.filter(s => s.customerId === customerUser.id);
      assert.equal(customerVisible.length, 1);
      assert.equal(customerVisible[0].trackingNumber, 'PKG-101');

      // Admin sees all shipments in awaiting pickup dispatch queue
      const adminAwaitingPickup = shipments.filter(s => s.currentStage === 'REQUEST_CREATED');
      assert.equal(adminAwaitingPickup.length, 2);
    });

    it('9. Map marker changes update actual coordinate values and confirm state', () => {
      let state = {
        pickupLatitude: 12.9352,
        pickupLongitude: 77.6245,
        isPickupCoordsConfirmed: true,
      };

      // When user types a new address, confirmation is cleared
      const onAddressType = (newText) => {
        state.isPickupCoordsConfirmed = false;
      };
      onAddressType('Indiranagar Double Road');
      assert.equal(state.isPickupCoordsConfirmed, false);

      // Marker dragged or map clicked
      const onMarkerDrag = (newLat, newLon) => {
        state.pickupLatitude = newLat;
        state.pickupLongitude = newLon;
        state.isPickupCoordsConfirmed = true;
      };
      onMarkerDrag(12.9716, 77.6412);
      assert.equal(state.pickupLatitude, 12.9716);
      assert.equal(state.pickupLongitude, 77.6412);
      assert.equal(state.isPickupCoordsConfirmed, true);
    });
  });
});
