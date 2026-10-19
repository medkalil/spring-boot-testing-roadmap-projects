import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Order } from '../api/types';
import { money } from '../lib/format';

export function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    api<Order[]>('/api/orders')
      .then(setOrders)
      .catch(() => setError('Could not load your orders.'));
  }, []);

  if (orders.length === 0 && !error) {
    return (
      <div className="box">
        <h1>My orders</h1>
        <p>You have no orders yet.</p>
        <Link className="btn" to="/products">Browse products</Link>
      </div>
    );
  }

  return (
    <div>
      <h1>My orders</h1>
      {error && <p className="error" role="alert">{error}</p>}

      {orders.map((order) => (
        <article key={order.id} className="card order">
          <p>
            Order <strong>#{order.id}</strong> &middot; {new Date(order.createdAt).toLocaleString()}
          </p>
          <p>
            Status: <span data-testid="order-status">{order.status}</span>
          </p>
          <ul>
            {order.items.map((item, index) => (
              <li key={index}>
                {item.productName} &times;{item.quantity} — {money(item.lineTotal)}
              </li>
            ))}
          </ul>
          <p>
            Total: <strong>{money(order.total)}</strong>
          </p>
          <Link to={`/orders/${order.id}`}>View order</Link>
        </article>
      ))}
    </div>
  );
}