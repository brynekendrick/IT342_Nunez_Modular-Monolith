import React, { useState } from 'react';
import './App.css';

const PRODUCTS = [
  { id: 'P100', name: 'P100 Wireless Mouse' },
  { id: 'P200', name: 'P200 Mechanical Keyboard' },
  { id: 'P300', name: 'P300 USB-C Hub' },
];

export default function App() {
  const [productId, setProductId] = useState('P100');
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setResult(null);

    try {
      const response = await fetch('http://localhost:8080/api/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId, quantity: Number(quantity) }),
      });

      const data = await response.json();
      setResult(data);
    } catch (err) {
      setResult({ status: 'ERROR', reason: 'Failed to connect to backend server' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div class="terminal-container">
      <div class="terminal-header">
        <h2 class="terminal-title">Shop Order Terminal</h2>
        <p class="terminal-subtitle">Select inventory items and place real-time orders</p>
      </div>

      <form onSubmit={handleSubmit}>
        <div class="form-group">
          <label class="form-label">Select Product</label>
          <select 
            class="form-select"
            value={productId} 
            onChange={(e) => setProductId(e.target.value)}
          >
            {PRODUCTS.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
        </div>

        <div class="form-group">
          <label class="form-label">Quantity</label>
          <input 
            type="number" 
            min="1" 
            class="form-input"
            value={quantity} 
            onChange={(e) => setQuantity(e.target.value)}
          />
        </div>

        <button type="submit" class="submit-btn" disabled={loading}>
          {loading ? 'Processing...' : 'Place Order'}
        </button>
      </form>

      {result && (
        <div class={`status-card ${result.status.toLowerCase()}`}>
          <div class="status-header">
            <span>{result.status === 'CONFIRMED' ? '✓' : '✕'}</span>
            <span>Order {result.status}</span>
          </div>
          <p class="status-reason">{result.reason}</p>

          {result.inventory && (
            <div class="inventory-details">
              <div class="inventory-title">Updated Inventory</div>
              <ul class="inventory-list">
                <li><span>Product ID:</span> <strong>{result.inventory.productId}</strong></li>
                <li><span>Product Name:</span> <strong>{result.inventory.name}</strong></li>
                <li><span>Remaining Stock:</span> <strong>{result.inventory.stock} units</strong></li>
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}