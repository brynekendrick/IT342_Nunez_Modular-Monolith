import React, { useState, useEffect } from 'react';
import './App.css';

const API_BASE = 'http://localhost:8080/api';

export default function App() {
  const [inventory, setInventory] = useState([]);
  const [orders, setOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [cart, setCart] = useState([{ productId: 'P100', quantity: 1 }]);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);

  const fetchAllData = async () => {
    try {
      const [invRes, ordRes, notifRes] = await Promise.all([
        fetch(`${API_BASE}/inventory`),
        fetch(`${API_BASE}/orders`),
        fetch(`${API_BASE}/notifications`)
      ]);

      if (invRes.ok) {
        const invData = await invRes.json();
        if (Array.isArray(invData)) setInventory(invData);
      }

      if (ordRes.ok) {
        const ordData = await ordRes.json();
        if (Array.isArray(ordData)) setOrders(ordData);
      }

      if (notifRes.ok) {
        const notifData = await notifRes.json();
        if (Array.isArray(notifData)) setNotifications(notifData);
      }
    } catch (err) {
      console.error('Failed to connect to backend server:', err);
    }
  };

  useEffect(() => {
    fetchAllData();
  }, []);

  const handleAddToCart = () => {
    const defaultProduct = inventory.length > 0 ? inventory[0].productId : 'P100';
    setCart([...cart, { productId: defaultProduct, quantity: 1 }]);
  };

  const handleUpdateCart = (index, field, value) => {
    const updated = [...cart];
    updated[index][field] = field === 'quantity' ? Number(value) : value;
    setCart(updated);
  };

  const handleRemoveFromCart = (index) => {
    setCart(cart.filter((_, i) => i !== index));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setResult(null);

    try {
      const response = await fetch(`${API_BASE}/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ items: cart }),
      });

      const data = await response.json();
      setResult({
        status: data?.status || 'UNKNOWN',
        reason: data?.reason || 'No details provided by server.'
      });

      await fetchAllData();
    } catch (err) {
      console.error('Order submission error:', err);
      setResult({ status: 'ERROR', reason: 'Failed to connect to backend server' });
    } finally {
      setLoading(false);
    }
  };

  const handleCancelOrder = async (orderId) => {
    try {
      const response = await fetch(`${API_BASE}/orders/${orderId}/cancel`, { method: 'POST' });
      if (response.ok) {
        setResult({ status: 'CONFIRMED', reason: `Order #${orderId} cancelled and restocked.` });
        fetchAllData();
      } else {
        const errorData = await response.json().catch(() => ({}));
        setResult({ status: 'REJECTED', reason: errorData?.message || 'Unable to cancel order.' });
      }
    } catch (err) {
      console.error('Cancel order error:', err);
      setResult({ status: 'ERROR', reason: 'Failed to cancel order.' });
    }
  };

  const getProductName = (id) => {
    const match = inventory.find((p) => p.productId === id);
    if (match) return match.name;
    if (id === 'P100') return 'Wireless Mouse';
    if (id === 'P200') return 'Mechanical Keyboard';
    if (id === 'P300') return 'USB-C Hub';
    return 'Product';
  };

  return (
    <div className="dashboard-container">
      <h1 className="dashboard-title">Modular Monolith Shop Dashboard</h1>

      {/* TOP ROW: SHOP ORDER TERMINAL, LIVE INVENTORY, EVENT NOTIFICATIONS */}
      <div className="top-row-track">

        {/* 1. SHOP ORDER TERMINAL */}
        <div className="terminal-column">
          <div className="terminal-container">
            <div className="terminal-header">
              <h2 className="terminal-title">Shop Order Terminal</h2>
              <p className="terminal-subtitle">Select inventory items & place real-time orders</p>
            </div>

            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label className="form-label">Order Items (Cart)</label>
                {cart.map((item, index) => (
                  <div key={index} style={{ display: 'flex', gap: '6px', marginBottom: '8px' }}>
                    <select
                      className="form-select"
                      value={item.productId}
                      onChange={(e) => handleUpdateCart(index, 'productId', e.target.value)}
                      style={{ flex: 2 }}
                    >
                      {inventory.length > 0 ? (
                        inventory.map((p) => (
                          <option key={p.productId} value={p.productId}>
                            {p.name} ({p.productId})
                          </option>
                        ))
                      ) : (
                        <option value="P100">Loading products...</option>
                      )}
                    </select>

                    <input
                      type="number"
                      min="1"
                      className="form-input"
                      value={item.quantity}
                      onChange={(e) => handleUpdateCart(index, 'quantity', e.target.value)}
                      style={{ flex: 1 }}
                    />

                    {cart.length > 1 && (
                      <button
                        type="button"
                        onClick={() => handleRemoveFromCart(index)}
                        style={{ background: '#ef4444', color: '#fff', border: 'none', borderRadius: '6px', padding: '0 8px', cursor: 'pointer' }}
                      >
                        ✕
                      </button>
                    )}
                  </div>
                ))}
              </div>

              <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
                <button
                  type="button"
                  onClick={handleAddToCart}
                  style={{ background: '#f1f5f9', color: '#334155', border: '1px solid #cbd5e1', borderRadius: '6px', padding: '8px', fontWeight: '600', cursor: 'pointer', flex: 1 }}
                >
                  + Add Item
                </button>
                <button type="submit" className="submit-btn" disabled={loading} style={{ flex: 2 }}>
                  {loading ? 'Processing...' : 'Place Order'}
                </button>
              </div>
            </form>

            {result && (
              <div className={`status-card ${(result.status || 'error').toLowerCase()}`}>
                <strong>Status: {result.status}</strong>
                <p style={{ margin: '4px 0 0 0' }}>{result.reason}</p>
              </div>
            )}
          </div>
        </div>

        {/* 2. LIVE INVENTORY */}
        <div className="terminal-column">
          <div className="terminal-container">
            <div className="terminal-header">
              <h2 className="terminal-title">Live Inventory</h2>
              <p className="terminal-subtitle">Real-time stock from Supabase</p>
            </div>
            <ul className="inventory-list">
              {inventory.length > 0 ? (
                inventory.map((item) => (
                  <li key={item.productId} style={{ color: item.stock < 5 ? '#dc2626' : 'inherit' }}>
                    <span><strong>{item.name}</strong> ({item.productId})</span>
                    <strong>{item.stock} units {item.stock < 5 && '⚠️ LOW'}</strong>
                  </li>
                ))
              ) : (
                <li>Loading live stock...</li>
              )}
            </ul>
          </div>
        </div>

        {/* 3. EVENT NOTIFICATION LOG */}
        <div className="terminal-column">
          <div className="terminal-container">
            <div className="terminal-header">
              <h2 className="terminal-title">Event Notification Log</h2>
              <p className="terminal-subtitle">In-Monolith ApplicationEvent stream</p>
            </div>
            <ul className="inventory-list" style={{ maxHeight: '240px', overflowY: 'auto' }}>
              {notifications.length > 0 ? (
                notifications.map((notif) => (
                  <li key={notif.notificationId || Math.random()} style={{ flexDirection: 'column', alignItems: 'flex-start' }}>
                    <span style={{ fontSize: '0.8rem', color: '#334155' }}>{notif.message}</span>
                    <small style={{ color: '#94a3b8', fontSize: '0.7rem', marginTop: '2px' }}>
                      {notif.createdAt ? new Date(notif.createdAt).toLocaleTimeString() : ''}
                    </small>
                  </li>
                ))
              ) : (
                <li style={{ color: '#94a3b8' }}>No events logged yet.</li>
              )}
            </ul>
          </div>
        </div>

      </div>

      {/* BOTTOM ROW: ORDER HISTORY (FULL WIDTH BELOW TOP MODULES) */}
      <div className="bottom-row-full">
        <div className="terminal-container">
          <div className="terminal-header">
            <h2 className="terminal-title">Order History</h2>
            <p className="terminal-subtitle">Previous orders and their current status</p>
          </div>

          {(() => {
            const groupedOrders = orders.reduce((acc, currentOrder) => {
              const groupKey = currentOrder.orderGroup || currentOrder.orderId;
              if (!acc[groupKey]) {
                acc[groupKey] = {
                  orderId: groupKey,
                  status: currentOrder.status,
                  createdAt: currentOrder.createdAt,
                  reason: currentOrder.reason,
                  items: []
                };
              }

              if (currentOrder.items && Array.isArray(currentOrder.items)) {
                acc[groupKey].items = currentOrder.items;
              } else {
                acc[groupKey].items.push({
                  productId: currentOrder.productId,
                  quantity: currentOrder.quantity
                });
              }
              return acc;
            }, {});

            const groupedOrdersList = Object.values(groupedOrders);

            return groupedOrdersList.length > 0 ? (
              <div className="order-history-horizontal">
                {groupedOrdersList.map((ord) => (
                  <div key={ord.orderId} className="order-card-horizontal">
                    <div>
                      <div className="order-card-header">
                        <div>
                          <div className="order-number">Order #{ord.orderId}</div>
                          <div className="order-timestamp">
                            {ord.createdAt ? new Date(ord.createdAt).toLocaleString() : new Date().toLocaleString()}
                          </div>
                        </div>
                        <span className="status-pill">{ord.status}</span>
                      </div>

                      <ul className="order-items-list">
                        {ord.items.map((item, idx) => (
                          <li key={idx} className="order-item-row">
                            <span>{item.productId} - {getProductName(item.productId)}</span>
                            <span style={{ fontWeight: '700' }}>× {item.quantity}</span>
                          </li>
                        ))}
                      </ul>
                    </div>

                    <div>
                      <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '6px' }}>
                        {ord.reason || 'Order processed'}
                      </div>

                      {ord.status === 'CONFIRMED' && (
                        <button className="cancel-btn" onClick={() => handleCancelOrder(ord.orderId)}>
                          Cancel Order
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p style={{ color: '#64748b', fontSize: '0.85rem' }}>No orders recorded yet.</p>
            );
          })()}
        </div>
        
      </div>

    </div>
  );
}