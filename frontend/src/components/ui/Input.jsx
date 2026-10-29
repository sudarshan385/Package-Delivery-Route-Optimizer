import React, { forwardRef } from 'react';

const Input = forwardRef(function Input({
  label,
  error,
  helperText,
  icon: Icon,
  className = '',
  id,
  required,
  ...props
}, ref) {
  const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

  return (
    <div className="form-group">
      {label && (
        <label htmlFor={inputId}>
          {label} {required && <span style={{ color: 'var(--color-danger)' }}>*</span>}
        </label>
      )}
      <div style={{ position: 'relative', width: '100%' }}>
        {Icon && (
          <div
            style={{
              position: 'absolute',
              left: 10,
              top: '50%',
              transform: 'translateY(-50%)',
              color: 'var(--theme-muted)',
              display: 'flex',
              alignItems: 'center',
              pointerEvents: 'none',
            }}
          >
            <Icon size={16} />
          </div>
        )}
        <input
          ref={ref}
          id={inputId}
          required={required}
          className={`form-input ${error ? 'error' : ''} ${className}`}
          style={Icon ? { paddingLeft: 34 } : undefined}
          {...props}
        />
      </div>
      {error ? (
        <span className="form-error">{error}</span>
      ) : helperText ? (
        <span style={{ fontSize: 11, color: 'var(--theme-muted)', marginTop: 4 }}>{helperText}</span>
      ) : null}
    </div>
  );
});

export default Input;
