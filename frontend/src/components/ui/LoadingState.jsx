import React from 'react';

export default function LoadingState({ message = 'Loading data...', minHeight = 240 }) {
  return (
    <div className="loading-spinner" style={{ minHeight }}>
      <div className="spinner" />
      {message && <p style={{ color: 'var(--theme-muted)', fontSize: 12 }}>{message}</p>}
    </div>
  );
}
