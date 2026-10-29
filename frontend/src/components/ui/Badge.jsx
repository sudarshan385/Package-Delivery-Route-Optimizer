import React from 'react';

export function Badge({
  children,
  variant = 'default', // 'default' | 'success' | 'warning' | 'danger' | 'info'
  className = '',
  ...props
}) {
  return (
    <span className={`badge badge-${variant} ${className}`.trim()} {...props}>
      {children}
    </span>
  );
}

export function StatusBadge({ status, type = 'status', className = '' }) {
  if (!status) return <span className="badge">—</span>;

  const normalized = String(status).toUpperCase();

  let variant = 'default';

  if (['COMPLETED', 'DELIVERED', 'AVAILABLE', 'SUCCESS', 'ACTIVE'].includes(normalized)) {
    variant = 'success';
  } else if (['IN_PROGRESS', 'ASSIGNED', 'PENDING', 'MAINTENANCE', 'HIGH', 'WARNING', 'BUSY'].includes(normalized)) {
    variant = 'warning';
  } else if (['FAILED', 'CANCELLED', 'OFFLINE', 'URGENT', 'DANGER', 'INACTIVE'].includes(normalized)) {
    variant = 'danger';
  } else if (['CREATED', 'IN_DELIVERY', 'IN_USE', 'MEDIUM', 'INFO', 'ADMIN'].includes(normalized)) {
    variant = 'info';
  } else if (['LOW', 'DRIVER'].includes(normalized)) {
    variant = 'default';
  }

  const displayText = normalized.replace(/_/g, ' ');

  return (
    <Badge variant={variant} className={className}>
      <span
        style={{
          width: 5,
          height: 5,
          borderRadius: '50%',
          backgroundColor: 'currentColor',
          display: 'inline-block',
        }}
      />
      {displayText}
    </Badge>
  );
}

export default Badge;
