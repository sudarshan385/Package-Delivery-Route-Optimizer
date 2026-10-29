import React, { useState, useEffect, useRef } from 'react';
import { MapPin, Search, Compass, Navigation, AlertCircle, CheckCircle2, Crosshair, Map as MapIcon } from 'lucide-react';
import { MapContainer, TileLayer, Marker, Popup, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import api from '../../api/axios';
import { parseApiError } from './ErrorState';
import Button from './Button';

// Bengaluru metropolitan boundaries
export const BENGALURU_BOUNDS = {
  minLat: 12.5,
  maxLat: 13.5,
  minLng: 77.2,
  maxLng: 78.0,
  center: [12.9716, 77.5946],
};

export const isWithinBengaluru = (lat, lng) => {
  if (lat == null || lng == null) return false;
  return lat >= BENGALURU_BOUNDS.minLat && lat <= BENGALURU_BOUNDS.maxLat &&
         lng >= BENGALURU_BOUNDS.minLng && lng <= BENGALURU_BOUNDS.maxLng;
};

// Programmatic map camera controller that pans and zooms smoothly
function MapCameraController({ center, zoom = 15 }) {
  const map = useMap();
  useEffect(() => {
    if (center && center[0] && center[1]) {
      map.flyTo(center, zoom, { duration: 1.0 });
    }
  }, [center, zoom, map]);
  return null;
}

// Map click event listener for manual map positioning
function MapClickHandler({ onMapClick, activeMode }) {
  useMapEvents({
    click(e) {
      if (onMapClick) {
        onMapClick(e.latlng.lat, e.latlng.lng);
      }
    },
  });
  return null;
}

export default function LocationPicker({
  idPrefix = 'loc',
  inputId,
  locateBtnId,
  placeholder = 'e.g. Koramangala 5th Block, Indiranagar 100ft Rd, Whitefield',
  label = 'Address & Map Location',
  address = '',
  latitude = null,
  longitude = null,
  onChange,
  error = '',
  markerColor = '#10B981',
  markerLabel = 'PIN',
  required = true,
  helperText = '',
}) {
  const [inputAddress, setInputAddress] = useState(address || '');
  const [isLocating, setIsLocating] = useState(false);
  const [isGeolocating, setIsGeolocating] = useState(false);
  const [statusNotice, setStatusNotice] = useState('');
  const [statusType, setStatusType] = useState('info'); // 'info' | 'success' | 'error'
  const [chooseOnMapMode, setChooseOnMapMode] = useState(false);
  const [resolvedName, setResolvedName] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [showResultsDropdown, setShowResultsDropdown] = useState(false);
  const latestSearchRequestId = useRef(0);

  // Sync internal input if parent address changes
  useEffect(() => {
    if (address !== inputAddress && address !== undefined) {
      setInputAddress(address);
    }
  }, [address]);

  // Dynamic custom marker icon
  const markerIcon = L.divIcon({
    html: `<div style="background-color: ${markerColor}; color: white; width: 32px; height: 32px; border-radius: 50%; border: 3px solid white; box-shadow: 0 4px 10px rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 11px; letter-spacing: -0.5px;">${markerLabel}</div>`,
    className: 'custom-location-marker',
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });

  const handleInputChange = (e) => {
    const val = e.target.value;
    setInputAddress(val);
    setShowResultsDropdown(false);

    // CRITICAL: When address is modified, if it differs from current resolved location,
    // clear coordinates or flag as unconfirmed to prevent stale coordinates
    if (onChange) {
      onChange({
        address: val,
        latitude: null, // clear stale coordinates on address change until resolved
        longitude: null,
        isConfirmed: false,
      });
    }
    setStatusNotice('');
  };

  // ACTION 1: "Locate address"
  const handleLocateAddress = async () => {
    if (!inputAddress || inputAddress.trim().length < 3) {
      setStatusNotice('Please enter a street name, locality, or landmark in Bengaluru.');
      setStatusType('error');
      return;
    }

    setIsLocating(true);
    setStatusNotice('');
    setShowResultsDropdown(false);
    const requestId = ++latestSearchRequestId.current;

    try {
      // Bias search to Bengaluru, Karnataka, India
      const query = inputAddress.toLowerCase().includes('bengaluru') || inputAddress.toLowerCase().includes('bangalore')
        ? inputAddress.trim()
        : `${inputAddress.trim()}, Bengaluru, Karnataka, India`;

      const res = await api.get('/locations/geocode', { params: { address: query } });

      // Discard stale asynchronous responses if a newer search was started
      if (requestId !== latestSearchRequestId.current) {
        return;
      }

      if (res.data && res.data.latitude && res.data.longitude) {
        const lat = parseFloat(Number(res.data.latitude).toFixed(6));
        const lng = parseFloat(Number(res.data.longitude).toFixed(6));

        if (!isWithinBengaluru(lat, lng)) {
          setStatusNotice(`Resolved location (${lat}, ${lng}) is outside the Bengaluru service area (lat 12.5-13.5, lon 77.2-78.0).`);
          setStatusType('error');
          if (onChange) {
            onChange({
              address: inputAddress,
              latitude: null,
              longitude: null,
              isConfirmed: false,
            });
          }
          return;
        }

        const displayName = res.data.displayName || inputAddress;
        setResolvedName(displayName);
        setStatusNotice(`Centered on map: ${displayName.split(',')[0]} (${lat}, ${lng})`);
        setStatusType('success');

        if (onChange) {
          onChange({
            address: inputAddress,
            latitude: lat,
            longitude: lng,
            isConfirmed: true,
            displayName,
          });
        }
      } else {
        throw new Error('No coordinates returned for address.');
      }
    } catch (err) {
      if (requestId === latestSearchRequestId.current) {
        // A failed search must not retain stale coordinates as if they belonged to the new address
        if (onChange) {
          onChange({
            address: inputAddress,
            latitude: null,
            longitude: null,
            isConfirmed: false,
          });
        }
        setStatusNotice(parseApiError(err, 'Address could not be resolved automatically. Please check spelling or use "Choose on map".'));
        setStatusType('error');
      }
    } finally {
      if (requestId === latestSearchRequestId.current) {
        setIsLocating(false);
      }
    }
  };

  // ACTION 2: "Use my current location"
  const handleUseCurrentLocation = () => {
    if (!navigator.geolocation) {
      setStatusNotice('Geolocation is not supported by your browser.');
      setStatusType('error');
      return;
    }

    setIsGeolocating(true);
    setStatusNotice('Detecting GPS location with browser permission...');
    setStatusType('info');

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = parseFloat(pos.coords.latitude.toFixed(6));
        const lng = parseFloat(pos.coords.longitude.toFixed(6));

        if (!isWithinBengaluru(lat, lng)) {
          setStatusNotice(`Device GPS location (${lat}, ${lng}) is outside the supported Bengaluru metropolitan service area.`);
          setStatusType('error');
          setIsGeolocating(false);
          return;
        }

        const locText = `Current GPS Location (${lat}, ${lng})`;
        setInputAddress(locText);
        setResolvedName(locText);
        setStatusNotice(`Positioned at your current device location: (${lat}, ${lng})`);
        setStatusType('success');

        if (onChange) {
          onChange({
            address: locText,
            latitude: lat,
            longitude: lng,
            isConfirmed: true,
          });
        }
        setIsGeolocating(false);
      },
      (err) => {
        setStatusNotice(`Geolocation denied or unavailable: ${err.message}. Please use "Locate address" or "Choose on map".`);
        setStatusType('error');
        setIsGeolocating(false);
      },
      { timeout: 10000, enableHighAccuracy: true }
    );
  };

  // ACTION 3: "Choose on map"
  const handleToggleChooseOnMap = () => {
    setChooseOnMapMode((prev) => {
      const next = !prev;
      if (next) {
        setStatusNotice('Click anywhere on the map below to position the pin.');
        setStatusType('info');
      } else {
        setStatusNotice('');
      }
      return next;
    });
  };

  // Map pin placement / marker drag handler
  const handleMapPositionSelected = (lat, lng) => {
    const roundedLat = parseFloat(lat.toFixed(6));
    const roundedLng = parseFloat(lng.toFixed(6));

    if (!isWithinBengaluru(roundedLat, roundedLng)) {
      setStatusNotice(`Selected pin (${roundedLat}, ${roundedLng}) is outside the Bengaluru metropolitan boundary.`);
      setStatusType('error');
      return;
    }

    setStatusNotice(`Pin positioned intentionally at (${roundedLat}, ${roundedLng}).`);
    setStatusType('success');
    setChooseOnMapMode(false);

    if (onChange) {
      onChange({
        address: inputAddress || `Selected Pin (${roundedLat}, ${roundedLng})`,
        latitude: roundedLat,
        longitude: roundedLng,
        isConfirmed: true,
      });
    }
  };

  const currentCenter = (latitude && longitude && isWithinBengaluru(latitude, longitude))
    ? [latitude, longitude]
    : BENGALURU_BOUNDS.center;

  const hasValidCoordinates = latitude != null && longitude != null && isWithinBengaluru(latitude, longitude);

  return (
    <div className="space-y-3 location-picker-root">
      <div className="flex items-center justify-between">
        <label className="text-sm font-semibold text-themed-primary flex items-center gap-1.5">
          <MapPin className="w-4 h-4" style={{ color: markerColor }} />
          {label}
          {required && <span className="text-rose-500">*</span>}
        </label>
        {hasValidCoordinates ? (
          <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <CheckCircle2 className="w-3 h-3" />
            Verified Coordinates ({latitude}, {longitude})
          </span>
        ) : (
          <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-medium bg-amber-500/10 text-amber-400 border border-amber-500/20">
            <AlertCircle className="w-3 h-3" />
            Pin Placement Required
          </span>
        )}
      </div>

      {/* Address Input and Search Button */}
      <div className="flex flex-col sm:flex-row gap-2">
        <div className="relative flex-1">
          <input
            id={inputId || `${idPrefix}-address-input`}
            type="text"
            value={inputAddress}
            onChange={handleInputChange}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                handleLocateAddress();
              }
            }}
            placeholder={placeholder}
            className={`w-full px-3.5 py-2.5 rounded-lg text-sm bg-themed-surface text-themed-primary border ${
              error ? 'border-rose-500 focus:ring-rose-500' : 'border-themed focus:border-emerald-500 focus:ring-emerald-500'
            } focus:outline-none focus:ring-2 transition shadow-sm`}
          />
        </div>

        {/* Action 1: Locate address */}
        <Button
          id={locateBtnId || `${idPrefix}-locate-btn`}
          type="button"
          variant="primary"
          onClick={handleLocateAddress}
          loading={isLocating}
          disabled={!inputAddress || inputAddress.trim().length < 2 || isLocating}
          className="whitespace-nowrap px-4 py-2.5 flex items-center justify-center gap-1.5"
          title="Resolve entered address and center map"
        >
          <Search className="w-4 h-4" />
          Locate address
        </Button>
      </div>

      {/* Distinct Actions Bar: "Use my current location" and "Choose on map" */}
      <div className="flex flex-wrap items-center gap-2 pt-1 text-xs">
        <span className="text-themed-muted font-medium">Or choose location:</span>

        {/* Action 2: Use my current location */}
        <button
          type="button"
          onClick={handleUseCurrentLocation}
          disabled={isGeolocating}
          className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md font-medium text-themed-secondary bg-themed-elevated hover:bg-themed-surface border border-themed hover:border-themed-primary transition disabled:opacity-50"
          title="Detect GPS coordinates using browser location"
        >
          <Crosshair className={`w-3.5 h-3.5 text-blue-400 ${isGeolocating ? 'animate-spin' : ''}`} />
          Use my current location
        </button>

        {/* Action 3: Choose on map */}
        <button
          type="button"
          onClick={handleToggleChooseOnMap}
          className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md font-medium transition ${
            chooseOnMapMode
              ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
              : 'text-themed-secondary bg-themed-elevated hover:bg-themed-surface border border-themed hover:border-themed-primary'
          }`}
          title="Click or drag pin directly on the interactive map"
        >
          <MapIcon className="w-3.5 h-3.5 text-emerald-400" />
          {chooseOnMapMode ? 'Click on map now...' : 'Choose on map'}
        </button>
      </div>

      {/* Status Notice or Error Banner */}
      {statusNotice && (
        <div
          className={`px-3 py-2 rounded-lg text-xs flex items-center gap-2 border ${
            statusType === 'error'
              ? 'bg-rose-500/10 text-rose-400 border-rose-500/30'
              : statusType === 'success'
              ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
              : 'bg-blue-500/10 text-blue-400 border-blue-500/30'
          }`}
        >
          {statusType === 'error' ? (
            <AlertCircle className="w-4 h-4 shrink-0" />
          ) : statusType === 'success' ? (
            <CheckCircle2 className="w-4 h-4 shrink-0" />
          ) : (
            <Navigation className="w-4 h-4 shrink-0" />
          )}
          <span>{statusNotice}</span>
        </div>
      )}

      {error && (
        <p className="text-xs text-rose-500 flex items-center gap-1">
          <AlertCircle className="w-3.5 h-3.5" />
          {error}
        </p>
      )}

      {/* Interactive Map */}
      <div className={`relative h-48 w-full rounded-lg overflow-hidden border ${
        chooseOnMapMode ? 'border-2 border-emerald-500 shadow-md ring-2 ring-emerald-500/20' : 'border-themed'
      }`}>
        {chooseOnMapMode && (
          <div className="absolute top-2 left-2 z-[1000] bg-emerald-950/90 text-emerald-300 text-xs px-2.5 py-1 rounded-md border border-emerald-500/40 shadow pointer-events-none flex items-center gap-1.5 font-medium animate-pulse">
            <Crosshair className="w-3.5 h-3.5" />
            Click anywhere on the map to place {markerLabel} pin
          </div>
        )}

        <MapContainer
          center={currentCenter}
          zoom={hasValidCoordinates ? 15 : 12}
          style={{ height: '100%', width: '100%' }}
          scrollWheelZoom={false}
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />

          <MapCameraController center={currentCenter} zoom={hasValidCoordinates ? 15 : 12} />
          <MapClickHandler onMapClick={handleMapPositionSelected} activeMode={chooseOnMapMode} />

          {hasValidCoordinates && (
            <Marker
              position={[latitude, longitude]}
              icon={markerIcon}
              draggable={true}
              eventHandlers={{
                dragend: (e) => {
                  const m = e.target;
                  const pos = m.getLatLng();
                  handleMapPositionSelected(pos.lat, pos.lng);
                },
              }}
            >
              <Popup>
                <div className="text-xs space-y-1">
                  <div className="font-bold text-gray-900">{markerLabel}: {inputAddress || 'Selected Pin'}</div>
                  <div className="text-gray-600">Coordinates: {latitude}, {longitude}</div>
                  <div className="text-[10px] text-emerald-600 font-medium">Drag pin to fine-tune exact location</div>
                </div>
              </Popup>
            </Marker>
          )}
        </MapContainer>
      </div>

      {helperText && <p className="text-xs text-themed-muted">{helperText}</p>}
    </div>
  );
}
