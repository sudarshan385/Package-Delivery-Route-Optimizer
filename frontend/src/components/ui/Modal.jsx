import React, { useEffect } from 'react';
import { X } from 'lucide-react';
import Button from './Button';

export default function Modal({
  isOpen,
  onClose,
  title,
  children,
  footer,
  primaryAction,
  primaryLabel = 'Save',
  primaryLoading = false,
  secondaryLabel = 'Cancel',
  maxWidth = 540,
}) {
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div
        className="modal"
        style={{ maxWidth }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h2>{title}</h2>
          <button
            type="button"
            className="modal-close"
            onClick={onClose}
            aria-label="Close dialog"
          >
            <X size={16} />
          </button>
        </div>

        <div className="modal-body">{children}</div>

        {(footer !== undefined ? footer : (
          <div className="modal-footer">
            <Button variant="secondary" onClick={onClose}>
              {secondaryLabel}
            </Button>
            {primaryAction && (
              <Button
                variant="primary"
                onClick={primaryAction}
                loading={primaryLoading}
              >
                {primaryLabel}
              </Button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
