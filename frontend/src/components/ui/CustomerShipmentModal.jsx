import React, { useState } from 'react';
import { 
  Package, MapPin, Send, Search, AlertCircle, 
  CheckCircle2, ArrowRight, ArrowLeft, ShieldCheck, Box, HelpCircle 
} from 'lucide-react';
import api from '../../api/axios';
import { parseApiError } from './ErrorState';
import Button from './Button';
import Input from './Input';
import Select from './Select';
import LocationPicker from './LocationPicker';
import { MapContainer, TileLayer, Marker, Popup, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// Custom Map Markers
const pickupIcon = L.divIcon({
  html: `<div style="background-color: #3B82F6; color: white; width: 28px; height: 28px; border-radius: 50%; border: 3px solid white; box-shadow: 0 3px 8px rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 11px;">SRC</div>`,
  className: 'pickup-marker',
  iconSize: [28, 28],
  iconAnchor: [14, 14],
});

const destIcon = L.divIcon({
  html: `<div style="background-color: #10B981; color: white; width: 28px; height: 28px; border-radius: 50%; border: 3px solid white; box-shadow: 0 3px 8px rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 11px;">DST</div>`,
  className: 'dest-marker',
  iconSize: [28, 28],
  iconAnchor: [14, 14],
});

// Calculate distance between two lat/lon coordinates in km using Haversine formula
export const calculateHaversineKm = (lat1, lon1, lat2, lon2) => {
  const R = 6371;
  const dLat = (lat2 - lat1) * (Math.PI / 180);
  const dLon = (lon2 - lon1) * (Math.PI / 180);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * (Math.PI / 180)) * Math.cos(lat2 * (Math.PI / 180)) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
};

// Map click handler to set active pin
function MapClickHandler({ activeMarkerType, onSelectCoordinates }) {
  useMapEvents({
    click(e) {
      if (activeMarkerType) {
        onSelectCoordinates(activeMarkerType, e.latlng.lat, e.latlng.lng, true);
      }
    },
  });
  return null;
}

export default function CustomerShipmentModal({ isOpen, onClose, onCreated }) {
  if (!isOpen) return null;

  const [activeTab, setActiveTab] = useState('SENDER'); // 'SENDER' -> 'RECIPIENT' -> 'PACKAGE'
  const [loadingGeocode, setLoadingGeocode] = useState(false);
  const [geocodeNotice, setGeocodeNotice] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});

  // Confirmation flags for coordinates
  const [isPickupCoordsConfirmed, setIsPickupCoordsConfirmed] = useState(true);
  const [isDeliveryCoordsConfirmed, setIsDeliveryCoordsConfirmed] = useState(true);
  const [pickupGeocodedAddress, setPickupGeocodedAddress] = useState('Koramangala 5th Block, Bengaluru');
  const [deliveryGeocodedAddress, setDeliveryGeocodedAddress] = useState('Indiranagar 100 Feet Road, Bengaluru');

  const [form, setForm] = useState({
    // Sender / Pickup
    senderName: '',
    senderPhone: '',
    senderEmail: '',
    pickupAddress: 'Koramangala 5th Block, Bengaluru',
    pickupCity: 'Bengaluru',
    pickupLatitude: 12.9352,
    pickupLongitude: 77.6245,
    pickupInstructions: '',

    // Recipient / Destination
    receiverName: '',
    receiverPhone: '',
    receiverEmail: '',
    deliveryAddress: 'Indiranagar 100 Feet Road, Bengaluru',
    deliveryCity: 'Bengaluru',
    deliveryLatitude: 12.9784,
    deliveryLongitude: 77.6408,
    deliveryInstructions: '',

    // Package contents
    itemName: 'World History Encyclopedia',
    itemCategory: 'Books & Educational',
    description: 'Special hardcover educational edition with slipcase',
    quantity: 1,
    weight: 2.4,
    lengthCm: 28.0,
    widthCm: 22.0,
    heightCm: 6.0,
    fragile: false,
    declaredValue: 1199.0,
    priority: 'MEDIUM'
  });

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    const val = type === 'checkbox' ? checked : value;
    setForm(prev => ({ ...prev, [name]: val }));

    // Clear field-level error when user starts typing
    if (fieldErrors[name]) {
      setFieldErrors(prev => {
        const copy = { ...prev };
        delete copy[name];
        return copy;
      });
    }
  };

  const handleAddressChange = (type, value) => {
    if (type === 'PICKUP') {
      setForm(prev => ({ ...prev, pickupAddress: value }));
      if (value.trim() !== pickupGeocodedAddress.trim()) {
        setIsPickupCoordsConfirmed(false);
      } else {
        setIsPickupCoordsConfirmed(true);
      }
      if (fieldErrors.pickupAddress || fieldErrors.pickupCoordinates) {
        setFieldErrors(prev => {
          const copy = { ...prev };
          delete copy.pickupAddress;
          delete copy.pickupCoordinates;
          return copy;
        });
      }
    } else {
      setForm(prev => ({ ...prev, deliveryAddress: value }));
      if (value.trim() !== deliveryGeocodedAddress.trim()) {
        setIsDeliveryCoordsConfirmed(false);
      } else {
        setIsDeliveryCoordsConfirmed(true);
      }
      if (fieldErrors.deliveryAddress || fieldErrors.deliveryCoordinates) {
        setFieldErrors(prev => {
          const copy = { ...prev };
          delete copy.deliveryAddress;
          delete copy.deliveryCoordinates;
          return copy;
        });
      }
    }
  };

  const handleCoordinatesSelected = (type, lat, lng, isManualPin = false) => {
    const roundedLat = parseFloat(lat.toFixed(6));
    const roundedLng = parseFloat(lng.toFixed(6));

    if (type === 'PICKUP') {
      setForm(prev => ({ ...prev, pickupLatitude: roundedLat, pickupLongitude: roundedLng }));
      setIsPickupCoordsConfirmed(true);
      setFieldErrors(prev => {
        const copy = { ...prev };
        delete copy.pickupCoordinates;
        return copy;
      });
      if (isManualPin) {
        setGeocodeNotice(`Pickup pin updated to (${roundedLat}, ${roundedLng}) via map positioning.`);
      }
    } else {
      setForm(prev => ({ ...prev, deliveryLatitude: roundedLat, deliveryLongitude: roundedLng }));
      setIsDeliveryCoordsConfirmed(true);
      setFieldErrors(prev => {
        const copy = { ...prev };
        delete copy.deliveryCoordinates;
        return copy;
      });
      if (isManualPin) {
        setGeocodeNotice(`Destination pin updated to (${roundedLat}, ${roundedLng}) via map positioning.`);
      }
    }
  };

  const handleGeocodeAddress = async (type) => {
    const addressToQuery = type === 'PICKUP' ? form.pickupAddress : form.deliveryAddress;
    if (!addressToQuery || addressToQuery.trim().length < 3) {
      const errKey = type === 'PICKUP' ? 'pickupAddress' : 'deliveryAddress';
      setFieldErrors(prev => ({ ...prev, [errKey]: 'Please enter a valid street or neighbourhood address.' }));
      return;
    }

    setLoadingGeocode(true);
    setGeocodeNotice('');
    try {
      const fullQuery = `${addressToQuery.trim()}, Bengaluru, Karnataka`;
      const res = await api.get('/locations/geocode', { params: { address: fullQuery } });
      if (res.data?.latitude && res.data?.longitude) {
        const lat = res.data.latitude;
        const lng = res.data.longitude;

        if (lat < 12.5 || lat > 13.5 || lng < 77.2 || lng > 78.0) {
          throw new Error('Resolved location is outside the Bengaluru metropolitan service area (lat: 12.5-13.5, lon: 77.2-78.0).');
        }

        if (type === 'PICKUP') {
          setForm(prev => ({
            ...prev,
            pickupLatitude: lat,
            pickupLongitude: lng
          }));
          setIsPickupCoordsConfirmed(true);
          setPickupGeocodedAddress(addressToQuery);
          setFieldErrors(prev => {
            const copy = { ...prev };
            delete copy.pickupAddress;
            delete copy.pickupCoordinates;
            return copy;
          });
          setGeocodeNotice(`Pickup pin centered at: ${res.data.displayName?.split(',')[0] || addressToQuery}`);
        } else {
          setForm(prev => ({
            ...prev,
            deliveryLatitude: lat,
            deliveryLongitude: lng
          }));
          setIsDeliveryCoordsConfirmed(true);
          setDeliveryGeocodedAddress(addressToQuery);
          setFieldErrors(prev => {
            const copy = { ...prev };
            delete copy.deliveryAddress;
            delete copy.deliveryCoordinates;
            return copy;
          });
          setGeocodeNotice(`Destination pin centered at: ${res.data.displayName?.split(',')[0] || addressToQuery}`);
        }
      } else {
        throw new Error('No coordinates returned for address.');
      }
    } catch (err) {
      if (type === 'PICKUP') {
        setIsPickupCoordsConfirmed(false);
        setFieldErrors(prev => ({
          ...prev,
          pickupCoordinates: 'Address resolution failed. Please click or drag the pin on the map to confirm coordinates.'
        }));
      } else {
        setIsDeliveryCoordsConfirmed(false);
        setFieldErrors(prev => ({
          ...prev,
          deliveryCoordinates: 'Address resolution failed. Please click or drag the pin on the map to confirm coordinates.'
        }));
      }
      setGeocodeNotice(parseApiError(err, 'Could not resolve exact coordinates automatically. Please click or drag the pin on the map.'));
    } finally {
      setLoadingGeocode(false);
    }
  };

  const handlePickupLocationChange = ({ address, latitude, longitude, isConfirmed }) => {
    setForm(prev => ({
      ...prev,
      pickupAddress: address,
      pickupLatitude: latitude,
      pickupLongitude: longitude
    }));
    setIsPickupCoordsConfirmed(Boolean(isConfirmed && latitude != null && longitude != null));
    if (isConfirmed && latitude != null && longitude != null) {
      setPickupGeocodedAddress(address);
      setFieldErrors(prev => {
        const copy = { ...prev };
        delete copy.pickupAddress;
        delete copy.pickupCoordinates;
        return copy;
      });
    }
  };

  const handleDeliveryLocationChange = ({ address, latitude, longitude, isConfirmed }) => {
    setForm(prev => ({
      ...prev,
      deliveryAddress: address,
      deliveryLatitude: latitude,
      deliveryLongitude: longitude
    }));
    setIsDeliveryCoordsConfirmed(Boolean(isConfirmed && latitude != null && longitude != null));
    if (isConfirmed && latitude != null && longitude != null) {
      setDeliveryGeocodedAddress(address);
      setFieldErrors(prev => {
        const copy = { ...prev };
        delete copy.deliveryAddress;
        delete copy.deliveryCoordinates;
        return copy;
      });
    }
  };

  // STEP 1 VALIDATION
  const validateStep1 = () => {
    const errors = {};
    const phoneRegex = /^[0-9+\-\s()]{7,20}$/;

    if (!form.senderName || form.senderName.trim().length < 2) {
      errors.senderName = 'Sender full name is required (at least 2 characters).';
    }

    if (!form.senderPhone || !form.senderPhone.trim()) {
      errors.senderPhone = 'Sender contact phone is required.';
    } else if (!phoneRegex.test(form.senderPhone.trim()) || form.senderPhone.replace(/\D/g, '').length < 7) {
      errors.senderPhone = 'Please enter a valid phone number with at least 7 digits.';
    }

    if (!form.pickupAddress || form.pickupAddress.trim().length < 5) {
      errors.pickupAddress = 'A valid full pickup street address in Bengaluru is required.';
    }

    if (!isPickupCoordsConfirmed) {
      errors.pickupCoordinates = "Pickup coordinates are unconfirmed. Click 'Locate on Map' or click/drag the pin on the map.";
    } else if (
      form.pickupLatitude == null || form.pickupLongitude == null ||
      form.pickupLatitude < 12.5 || form.pickupLatitude > 13.5 ||
      form.pickupLongitude < 77.2 || form.pickupLongitude > 78.0
    ) {
      errors.pickupCoordinates = 'Pickup pin must be placed within Bengaluru metropolitan service boundaries (lat: 12.5-13.5, lon: 77.2-78.0).';
    }

    return errors;
  };

  // STEP 2 VALIDATION
  const validateStep2 = () => {
    const errors = {};
    const phoneRegex = /^[0-9+\-\s()]{7,20}$/;

    if (!form.receiverName || form.receiverName.trim().length < 2) {
      errors.receiverName = 'Recipient full name is required (at least 2 characters).';
    }

    if (!form.receiverPhone || !form.receiverPhone.trim()) {
      errors.receiverPhone = 'Recipient contact phone is required.';
    } else if (!phoneRegex.test(form.receiverPhone.trim()) || form.receiverPhone.replace(/\D/g, '').length < 7) {
      errors.receiverPhone = 'Please enter a valid phone number with at least 7 digits.';
    }

    if (!form.deliveryAddress || form.deliveryAddress.trim().length < 5) {
      errors.deliveryAddress = 'A valid full delivery street address in Bengaluru is required.';
    }

    if (!isDeliveryCoordsConfirmed) {
      errors.deliveryCoordinates = "Destination coordinates are unconfirmed. Click 'Locate on Map' or click/drag the pin on the map.";
    } else if (
      form.deliveryLatitude == null || form.deliveryLongitude == null ||
      form.deliveryLatitude < 12.5 || form.deliveryLatitude > 13.5 ||
      form.deliveryLongitude < 77.2 || form.deliveryLongitude > 78.0
    ) {
      errors.deliveryCoordinates = 'Destination pin must be placed within Bengaluru metropolitan service boundaries (lat: 12.5-13.5, lon: 77.2-78.0).';
    } else {
      // Distinct coordinate validation (at least 50m apart)
      const dist = calculateHaversineKm(
        form.pickupLatitude, form.pickupLongitude,
        form.deliveryLatitude, form.deliveryLongitude
      );
      if (dist < 0.05) {
        errors.deliveryCoordinates = 'Pickup location and destination location must be distinct (at least 50 meters apart).';
      }
    }

    return errors;
  };

  // STEP 3 VALIDATION
  const validateStep3 = () => {
    const errors = {};

    if (!form.itemName || form.itemName.trim().length < 2) {
      errors.itemName = 'Product or item name is required.';
    }

    const qty = Number(form.quantity);
    if (!form.quantity || isNaN(qty) || qty < 1) {
      errors.quantity = 'Quantity must be at least 1.';
    }

    const wt = Number(form.weight);
    if (!form.weight || isNaN(wt) || wt <= 0) {
      errors.weight = 'Package weight must be a positive number greater than 0 kg.';
    }

    if (form.lengthCm !== undefined && form.lengthCm !== '' && Number(form.lengthCm) <= 0) {
      errors.lengthCm = 'Length must be greater than zero.';
    }
    if (form.widthCm !== undefined && form.widthCm !== '' && Number(form.widthCm) <= 0) {
      errors.widthCm = 'Width must be greater than zero.';
    }
    if (form.heightCm !== undefined && form.heightCm !== '' && Number(form.heightCm) <= 0) {
      errors.heightCm = 'Height must be greater than zero.';
    }

    if (form.declaredValue !== undefined && form.declaredValue !== '' && Number(form.declaredValue) < 0) {
      errors.declaredValue = 'Declared value cannot be negative.';
    }

    return errors;
  };

  // Navigation handlers
  const handleNextFromStep1 = () => {
    const errors = validateStep1();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setSubmitError('Please resolve the highlighted field errors before advancing.');
      return;
    }
    setFieldErrors({});
    setSubmitError('');
    setActiveTab('RECIPIENT');
  };

  const handleNextFromStep2 = () => {
    const errors = validateStep2();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setSubmitError('Please resolve the highlighted field errors before advancing.');
      return;
    }
    setFieldErrors({});
    setSubmitError('');
    setActiveTab('PACKAGE');
  };

  const handleSubmit = async (e) => {
    if (e) e.preventDefault();
    setSubmitError('');
    setFieldErrors({});

    // 1. Comprehensive re-validation of all three steps
    const step1Errors = validateStep1();
    if (Object.keys(step1Errors).length > 0) {
      setFieldErrors(step1Errors);
      setSubmitError('Please resolve the highlighted field errors in Step 1 (Sender & Pickup).');
      setActiveTab('SENDER');
      return;
    }

    const step2Errors = validateStep2();
    if (Object.keys(step2Errors).length > 0) {
      setFieldErrors(step2Errors);
      setSubmitError('Please resolve the highlighted field errors in Step 2 (Recipient & Destination).');
      setActiveTab('RECIPIENT');
      return;
    }

    const step3Errors = validateStep3();
    if (Object.keys(step3Errors).length > 0) {
      setFieldErrors(step3Errors);
      setSubmitError('Please resolve the highlighted field errors in Step 3 (Package Details).');
      setActiveTab('PACKAGE');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        senderName: form.senderName.trim(),
        senderPhone: form.senderPhone.trim(),
        senderEmail: form.senderEmail.trim() || undefined,
        pickupAddress: form.pickupAddress.trim(),
        pickupCity: form.pickupCity.trim() || 'Bengaluru',
        pickupLatitude: Number(form.pickupLatitude),
        pickupLongitude: Number(form.pickupLongitude),
        pickupInstructions: form.pickupInstructions.trim() || undefined,

        receiverName: form.receiverName.trim(),
        receiverPhone: form.receiverPhone.trim(),
        receiverEmail: form.receiverEmail.trim() || undefined,
        deliveryAddress: form.deliveryAddress.trim(),
        deliveryCity: form.deliveryCity.trim() || 'Bengaluru',
        deliveryLatitude: Number(form.deliveryLatitude),
        deliveryLongitude: Number(form.deliveryLongitude),
        deliveryInstructions: form.deliveryInstructions.trim() || undefined,

        itemName: form.itemName.trim(),
        itemCategory: form.itemCategory.trim() || 'General Parcel',
        description: form.description.trim() || undefined,
        quantity: Number(form.quantity) || 1,
        weight: Number(form.weight),
        lengthCm: form.lengthCm ? Number(form.lengthCm) : undefined,
        widthCm: form.widthCm ? Number(form.widthCm) : undefined,
        heightCm: form.heightCm ? Number(form.heightCm) : undefined,
        fragile: Boolean(form.fragile),
        declaredValue: form.declaredValue ? Number(form.declaredValue) : undefined,
        priority: form.priority
      };

      const res = await api.post('/shipments', payload);
      if (onCreated) {
        onCreated(res.data);
      }
      onClose();
    } catch (err) {
      const respData = err.response?.data;
      const errorMsg = respData?.detail || respData?.message || parseApiError(err, 'Failed to schedule shipment request.');
      setSubmitError(errorMsg);

      if (respData?.errors) {
        setFieldErrors(respData.errors);
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div 
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 9999,
        backgroundColor: 'rgba(0, 0, 0, 0.75)',
        backdropFilter: 'blur(6px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 960,
          maxHeight: '92vh',
          backgroundColor: 'var(--theme-surface)',
          border: '1px solid var(--theme-border)',
          borderRadius: 'var(--radius-card)',
          boxShadow: 'var(--shadow-dropdown)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden'
        }}
      >
        {/* Header */}
        <div 
          style={{
            padding: '20px 24px',
            borderBottom: '1px solid var(--theme-border)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            backgroundColor: 'var(--theme-elevated)'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: 'var(--radius-input)',
                backgroundColor: 'rgba(16, 185, 129, 0.12)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--color-primary)'
              }}
            >
              <Package size={22} />
            </div>
            <div>
              <h2 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: 'var(--theme-text)' }}>
                Create Smart Parcel Shipment
              </h2>
              <p style={{ margin: '2px 0 0', fontSize: 13, color: 'var(--theme-muted)' }}>
                Bengaluru Pickup-to-Hub-to-Destination Multi-Leg Workflow
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            aria-label="Close"
            style={{
              background: 'none',
              border: 'none',
              fontSize: 22,
              color: 'var(--theme-muted)',
              cursor: 'pointer',
              padding: 4
            }}
          >
            ✕
          </button>
        </div>

        {/* Tab Navigation */}
        <div style={{ display: 'flex', borderBottom: '1px solid var(--theme-border)', backgroundColor: 'var(--theme-surface)' }}>
          {[
            { id: 'SENDER', label: '1. Sender & Pickup Pin' },
            { id: 'RECIPIENT', label: '2. Recipient & Destination Pin' },
            { id: 'PACKAGE', label: '3. Package & Contents' }
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => {
                setActiveTab(tab.id);
                setSubmitError('');
              }}
              style={{
                flex: 1,
                padding: '14px',
                border: 'none',
                borderBottom: activeTab === tab.id ? '3px solid var(--color-primary)' : '3px solid transparent',
                backgroundColor: activeTab === tab.id ? 'var(--theme-elevated)' : 'transparent',
                color: activeTab === tab.id ? 'var(--color-primary)' : 'var(--theme-muted)',
                fontWeight: activeTab === tab.id ? 700 : 500,
                fontSize: 13,
                cursor: 'pointer',
                transition: 'all 0.2s'
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Modal Body */}
        <div style={{ padding: '20px 24px', overflowY: 'auto', flex: 1 }}>
          {submitError && (
            <div id="shipment-error-banner" className="alert alert-error mb-4" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <AlertCircle size={18} />
              <span>{submitError}</span>
            </div>
          )}

          {geocodeNotice && (
            <div className="alert alert-info mb-4" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
              <CheckCircle2 size={16} />
              <span>{geocodeNotice}</span>
            </div>
          )}

          {/* ================================================================= */}
          {/* TAB 1: SENDER / PICKUP */}
          {/* ================================================================= */}
          {activeTab === 'SENDER' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
                <Input
                  id="sender-name-input"
                  label="Sender Full Name *"
                  name="senderName"
                  value={form.senderName}
                  onChange={handleChange}
                  error={fieldErrors.senderName}
                  placeholder="e.g. Anita Sharma"
                  required
                />
                <Input
                  id="sender-phone-input"
                  label="Sender Contact Phone *"
                  name="senderPhone"
                  value={form.senderPhone}
                  onChange={handleChange}
                  error={fieldErrors.senderPhone}
                  placeholder="+91 98765 43210"
                  required
                />
                <Input
                  id="sender-email-input"
                  label="Sender Email (Optional)"
                  name="senderEmail"
                  type="email"
                  value={form.senderEmail}
                  onChange={handleChange}
                  placeholder="anita@example.com"
                />
              </div>              <LocationPicker
                idPrefix="pickup"
                inputId="pickup-address-input"
                locateBtnId="locate-pickup-btn"
                label="Full Pickup Address in Bengaluru *"
                placeholder="Enter street, building, area (e.g. 80 Feet Road, Koramangala 4th Block)"
                address={form.pickupAddress}
                latitude={form.pickupLatitude}
                longitude={form.pickupLongitude}
                onChange={handlePickupLocationChange}
                error={fieldErrors.pickupAddress || fieldErrors.pickupCoordinates}
                markerColor="#3B82F6"
                markerLabel="SRC"
                helperText="Click 'Locate address', 'Use my current location', or 'Choose on map' to position pin."
              />

              <Input
                label="Pickup Instructions (Optional)"
                name="pickupInstructions"
                value={form.pickupInstructions}
                onChange={handleChange}
                placeholder="e.g. Ring flat 402, call when downstairs"
              />

              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
                <Button 
                  type="button" 
                  variant="primary" 
                  icon={ArrowRight} 
                  onClick={handleNextFromStep1}
                  id="step1-next-btn"
                >
                  Next: Recipient Details
                </Button>
              </div>
            </div>
          )}

          {/* ================================================================= */}
          {/* TAB 2: RECIPIENT / DESTINATION */}
          {/* ================================================================= */}
          {activeTab === 'RECIPIENT' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
                <Input
                  id="recipient-name-input"
                  label="Recipient Full Name *"
                  name="receiverName"
                  value={form.receiverName}
                  onChange={handleChange}
                  error={fieldErrors.receiverName}
                  placeholder="e.g. Rohan Verma"
                  required
                />
                <Input
                  id="recipient-phone-input"
                  label="Recipient Contact Phone *"
                  name="receiverPhone"
                  value={form.receiverPhone}
                  onChange={handleChange}
                  error={fieldErrors.receiverPhone}
                  placeholder="+91 91234 56789"
                  required
                />
                <Input
                  id="recipient-email-input"
                  label="Recipient Email (Optional)"
                  name="receiverEmail"
                  type="email"
                  value={form.receiverEmail}
                  onChange={handleChange}
                  placeholder="rohan@example.com"
                />
              </div>

              <LocationPicker
                idPrefix="delivery"
                inputId="delivery-address-input"
                locateBtnId="locate-delivery-btn"
                label="Full Delivery Address in Bengaluru *"
                placeholder="Enter street, apartment, locality (e.g. 100 Feet Road, Indiranagar)"
                address={form.deliveryAddress}
                latitude={form.deliveryLatitude}
                longitude={form.deliveryLongitude}
                onChange={handleDeliveryLocationChange}
                error={fieldErrors.deliveryAddress || fieldErrors.deliveryCoordinates}
                markerColor="#10B981"
                markerLabel="DST"
                helperText="Click 'Locate address', 'Use my current location', or 'Choose on map' to position pin."
              />

              <Input
                label="Delivery Instructions (Optional)"
                name="deliveryInstructions"
                value={form.deliveryInstructions}
                onChange={handleChange}
                placeholder="e.g. Leave with building security if not answering"
              />

              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12 }}>
                <Button 
                  type="button" 
                  variant="secondary" 
                  icon={ArrowLeft}
                  onClick={() => {
                    setFieldErrors({});
                    setSubmitError('');
                    setActiveTab('SENDER');
                  }}
                  id="step2-back-btn"
                >
                  Back
                </Button>
                <Button 
                  type="button" 
                  variant="primary" 
                  icon={ArrowRight} 
                  onClick={handleNextFromStep2}
                  id="step2-next-btn"
                >
                  Next: Package Contents
                </Button>
              </div>
            </div>
          )}

          {/* ================================================================= */}
          {/* TAB 3: PACKAGE DETAILS & ORDER SUMMARY */}
          {/* ================================================================= */}
          {activeTab === 'PACKAGE' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 16 }}>
                <Input
                  id="product-name-input"
                  label="Product / Item Name *"
                  name="itemName"
                  value={form.itemName}
                  onChange={handleChange}
                  error={fieldErrors.itemName}
                  placeholder="e.g. World History Encyclopedia"
                  required
                />
                <Select
                  label="Item Category"
                  name="itemCategory"
                  value={form.itemCategory}
                  onChange={handleChange}
                >
                  <option value="Books & Educational">Books & Educational</option>
                  <option value="Electronics & Gadgets">Electronics & Gadgets</option>
                  <option value="Documents & Legal">Documents & Legal</option>
                  <option value="Apparel & Fashion">Apparel & Fashion</option>
                  <option value="Medical & Healthcare">Medical & Healthcare</option>
                  <option value="Home & Kitchen">Home & Kitchen</option>
                  <option value="General Parcel">General Parcel</option>
                </Select>
                <Input
                  label="Quantity *"
                  name="quantity"
                  type="number"
                  min="1"
                  value={form.quantity}
                  onChange={handleChange}
                  error={fieldErrors.quantity}
                  required
                />
                <Input
                  id="package-weight-input"
                  label="Package Weight (kg) *"
                  name="weight"
                  type="number"
                  step="0.1"
                  min="0.1"
                  value={form.weight}
                  onChange={handleChange}
                  error={fieldErrors.weight}
                  placeholder="2.4"
                  required
                />
              </div>

              <Input
                label="Item Description"
                name="description"
                value={form.description}
                onChange={handleChange}
                placeholder="Brief description of the package contents"
              />

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16 }}>
                <Input
                  label="Length (cm)"
                  name="lengthCm"
                  type="number"
                  step="0.5"
                  value={form.lengthCm}
                  onChange={handleChange}
                  error={fieldErrors.lengthCm}
                  placeholder="28"
                />
                <Input
                  label="Width (cm)"
                  name="widthCm"
                  type="number"
                  step="0.5"
                  value={form.widthCm}
                  onChange={handleChange}
                  error={fieldErrors.widthCm}
                  placeholder="22"
                />
                <Input
                  label="Height (cm)"
                  name="heightCm"
                  type="number"
                  step="0.5"
                  value={form.heightCm}
                  onChange={handleChange}
                  error={fieldErrors.heightCm}
                  placeholder="6"
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16, alignItems: 'center' }}>
                <Input
                  label="Declared Value (₹)"
                  name="declaredValue"
                  type="number"
                  step="1"
                  value={form.declaredValue}
                  onChange={handleChange}
                  error={fieldErrors.declaredValue}
                  placeholder="1199"
                />
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 18 }}>
                  <input
                    type="checkbox"
                    id="fragile-check"
                    name="fragile"
                    checked={form.fragile}
                    onChange={handleChange}
                    style={{ width: 18, height: 18, accentColor: 'var(--color-primary)' }}
                  />
                  <label htmlFor="fragile-check" style={{ fontSize: 13, fontWeight: 600, color: 'var(--theme-text)', cursor: 'pointer' }}>
                    Fragile / Special Handling Required
                  </label>
                </div>
              </div>

              {/* Dispatch Preview Summary */}
              <div 
                style={{
                  padding: 16,
                  borderRadius: 'var(--radius-input)',
                  backgroundColor: 'var(--theme-elevated)',
                  border: '1px solid var(--theme-border)',
                  marginTop: 8
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, color: 'var(--color-primary)', fontWeight: 700, fontSize: 13 }}>
                  <ShieldCheck size={16} />
                  <span>Automatic Multi-Leg Delivery Network Guarantee</span>
                </div>
                <p style={{ margin: 0, fontSize: 12, color: 'var(--theme-muted)', lineHeight: 1.5 }}>
                  Upon submission, your package will be automatically paired with the nearest reachable Bengaluru service hub based on real road topology. 
                  A first-mile partner will collect the parcel from your sender address, and a dedicated final-mile driver will deliver it to the recipient once processed.
                </p>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12 }}>
                <Button 
                  type="button" 
                  variant="secondary" 
                  icon={ArrowLeft}
                  onClick={() => {
                    setFieldErrors({});
                    setSubmitError('');
                    setActiveTab('RECIPIENT');
                  }}
                  id="step3-back-btn"
                >
                  Back
                </Button>
                <Button
                  type="button"
                  variant="primary"
                  icon={Send}
                  onClick={handleSubmit}
                  loading={submitting}
                  disabled={submitting}
                  id="submit-shipment-btn"
                >
                  {submitting ? 'Scheduling Shipment...' : 'Submit Shipment Request'}
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
