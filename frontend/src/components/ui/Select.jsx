import React, { forwardRef } from 'react';

const Select = forwardRef(function Select({
  label,
  error,
  helperText,
  options = [],
  children,
  className = '',
  id,
  required,
  ...props
}, ref) {
  const selectId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

  return (
    <div className="form-group">
      {label && (
        <label htmlFor={selectId}>
          {label} {required && <span style={{ color: 'var(--color-danger)' }}>*</span>}
        </label>
      )}
      <select
        ref={ref}
        id={selectId}
        required={required}
        className={`form-select ${error ? 'error' : ''} ${className}`}
        {...props}
      >
        {children ? (
          children
        ) : (
          options.map((opt) => {
            const val = typeof opt === 'object' ? opt.value : opt;
            const label = typeof opt === 'object' ? opt.label : opt;
            return (
              <option key={val} value={val}>
                {label}
              </option>
            );
          })
        )}
      </select>
      {error ? (
        <span className="form-error">{error}</span>
      ) : helperText ? (
        <span style={{ fontSize: 11, color: 'var(--theme-muted)', marginTop: 4 }}>{helperText}</span>
      ) : null}
    </div>
  );
});

export default Select;
