import React from 'react';
import { AlertCircle, RefreshCw } from 'lucide-react';
import Button from './Button';

export function parseApiError(err, fallback = 'An unexpected error occurred') {
  if (!err) return fallback;
  if (typeof err === 'string') return err;
  
  // RFC 7807 problem details parsing (matches frontend.test.js requirement)
  if (err.response?.data?.errors) {
    const errors = err.response.data.errors;
    if (typeof errors === 'object') {
      return Object.values(errors).join(', ');
    }
  }
  return err.response?.data?.detail || err.response?.data?.message || err.message || fallback;
}

export default function ErrorState({
  error,
  onRetry,
  title = 'Failed to load data',
  className = '',
}) {
  const errorMessage = parseApiError(error);

  return (
    <div
      className={`alert alert-error ${className}`.trim()}
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <AlertCircle size={18} style={{ flexShrink: 0 }} />
        <div>
          {title && <strong style={{ display: 'block', marginBottom: 2 }}>{title}</strong>}
          <span>{errorMessage}</span>
        </div>
      </div>
      {onRetry && (
        <Button variant="ghost" size="sm" icon={RefreshCw} onClick={onRetry}>
          Retry
        </Button>
      )}
    </div>
  );
}
