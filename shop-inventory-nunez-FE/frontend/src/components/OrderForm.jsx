import React from 'react';

const PRODUCTS = [
  { id: 'P100', name: 'P100 Wireless Mouse' },
  { id: 'P200', name: 'P200 Mechanical Keyboard' },
  { id: 'P300', name: 'P300 USB-C Hub' },
];

export default function OrderForm({ productId, setProductId, quantity, setQuantity, loading, onSubmit }) {
  return (
    <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div>
        <label style={{ display: 'block', marginBottom: '6px', fontWeight: 'bold' }}>Select Product:</label>
        <select 
          value={productId} 
          onChange={(e) => setProductId(e.target.value)}
          style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc' }}
        >
          {PRODUCTS.map((p) => (
            <option key={p.id} value={p.id}>{p.name}</option>
          ))}
        </select>
      </div>

      <div>
        <label style={{ display: 'block', marginBottom: '6px', fontWeight: 'bold' }}>Quantity:</label>
        <input 
          type="number" 
          min="1" 
          value={quantity} 
          onChange={(e) => setQuantity(e.target.value)}
          style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', boxSizing: 'border-box' }}
        />
      </div>

      <button 
        type="submit" 
        disabled={loading} 
        style={{ 
          padding: '12px', 
          backgroundColor: '#0066cc', 
          color: '#fff', 
          border: 'none', 
          borderRadius: '4px', 
          cursor: 'pointer',
          fontWeight: 'bold'
        }}
      >
        {loading ? 'Processing Order...' : 'Place Order'}
      </button>
    </form>
  );
}