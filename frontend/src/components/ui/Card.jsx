import React from 'react';

export default function Card({
  title,
  subtitle,
  actions,
  children,
  className = '',
  padding = 24,
  ...props
}) {
  return (
    <div
      className={`card ${className}`.trim()}
      style={padding !== 24 ? { padding } : undefined}
      {...props}
    >
      {(title || subtitle || actions) && (
        <div className="card-header">
          <div>
            {title && <h2 className="card-title">{title}</h2>}
            {subtitle && (
              <p style={{ fontSize: 11, color: 'var(--theme-muted)', marginTop: 2 }}>{subtitle}</p>
            )}
          </div>
          {actions && <div className="card-actions">{actions}</div>}
        </div>
      )}
      {children}
    </div>
  );
}
