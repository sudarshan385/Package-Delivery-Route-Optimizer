import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import Button from './Button';

export default function Pagination({
  currentPage = 0,
  totalPages = 1,
  totalItems,
  pageSize = 10,
  onPageChange,
}) {
  if (totalPages <= 1 && (!totalItems || totalItems <= pageSize)) return null;

  const startItem = currentPage * pageSize + 1;
  const endItem = Math.min((currentPage + 1) * pageSize, totalItems || (currentPage + 1) * pageSize);

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '12px 16px',
        borderTop: '1px solid var(--theme-border)',
        backgroundColor: 'var(--theme-surface)',
        fontSize: 12,
        color: 'var(--theme-muted)',
      }}
    >
      <div>
        {totalItems != null ? (
          <span>
            Showing <strong>{startItem}</strong>–<strong>{endItem}</strong> of{' '}
            <strong>{totalItems}</strong>
          </span>
        ) : (
          <span>
            Page <strong>{currentPage + 1}</strong> of <strong>{totalPages}</strong>
          </span>
        )}
      </div>

      <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        <Button
          variant="secondary"
          size="sm"
          disabled={currentPage <= 0}
          onClick={() => onPageChange(currentPage - 1)}
          icon={ChevronLeft}
        >
          Previous
        </Button>
        <span style={{ padding: '0 8px', fontSize: 11 }}>
          {currentPage + 1} / {totalPages}
        </span>
        <Button
          variant="secondary"
          size="sm"
          disabled={currentPage >= totalPages - 1}
          onClick={() => onPageChange(currentPage + 1)}
          icon={ChevronRight}
        >
          Next
        </Button>
      </div>
    </div>
  );
}
