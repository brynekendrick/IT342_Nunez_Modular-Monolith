import React from 'react';

export default function OrderStatusCard({ result }) {
  if (!result) return null;

  const isConfirmed = result.status === 'CONFIRMED';

  return (
    <div style={{
      marginTop: '24px',
      padding: '16px',
      borderRadius: '6px',
      backgroundColor: isConfirmed ? '#e6fffa' : '#fff5f5',
      border: `1px solid ${isConfirmed ? '#319795' : '#e53e3e'}`
    }}>
      <h3 style={{ margin: '0 0 8px 0' }}>Status: {result.status}</h3>
      <p style={{ margin: '0 0 8px 0' }}><strong>Reason:</strong> {result.reason}</p>
      
      {result.inventory && (
        <div style={{ marginTop: '12px', paddingTop: '12px', borderTop: '1px solid #ddd' }}>
          <strong>Updated Inventory:</strong>
          <ul style={{ margin: '8px 0 0 0', paddingLeft: '20px' }}>
            <li>Product ID: {result.inventory.productId}</li>
            <li>Name: {result.inventory.name}</li>
            <li>Remaining Stock: {result.inventory.stock}</li>
          </ul>
        </div>
      )}
    </div>
  );
}