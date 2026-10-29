import React from 'react';

export function Table({ children, className = '', ...props }) {
  return (
    <div className="table-container">
      <table className={`data-table ${className}`.trim()} {...props}>
        {children}
      </table>
    </div>
  );
}

export function TableHeader({ children }) {
  return <thead>{children}</thead>;
}

export function TableBody({ children }) {
  return <tbody>{children}</tbody>;
}

export function TableRow({ children, onClick, className = '', style }) {
  return (
    <tr
      onClick={onClick}
      className={className}
      style={{ ...(onClick ? { cursor: 'pointer' } : {}), ...style }}
    >
      {children}
    </tr>
  );
}

export function TableHead({ children, className = '', style }) {
  return (
    <th className={className} style={style}>
      {children}
    </th>
  );
}

export function TableCell({ children, className = '', style }) {
  return (
    <td className={className} style={style}>
      {children}
    </td>
  );
}

export default Table;
