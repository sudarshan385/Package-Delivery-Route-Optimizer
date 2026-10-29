import React from 'react';

export default function PageHeader({
  title,
  subtitle,
  children,
  action,
  className = '',
}) {
  return (
    <div className={`page-header ${className}`.trim()}>
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="page-header-subtitle">{subtitle}</p>}
      </div>
      {(action || children) && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {action}
          {children}
        </div>
      )}
    </div>
  );
}
