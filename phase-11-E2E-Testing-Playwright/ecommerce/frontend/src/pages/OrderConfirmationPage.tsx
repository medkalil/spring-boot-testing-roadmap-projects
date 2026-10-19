import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { Order } from '../api/types';
import { money } from '../lib/format';

export function OrderConfirmationPage() {
  const { id } = useParams<{ id: string }>();
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api<Order>(`/api/orders/${id}`)
      .then(setOrder)
      .catch((err: unknown) => {
        if (err instanceof ApiError) {
          setError(err.message); // 404 for someone else's order id included
        } else {
          setError('Could not load the order.');
        }
      });
  }, [id]);

  if (error) {
    return (
      <div className="box">
        <h1>Order</h1>
        <p className="error" role="alert">{error}</p>
        <Link to="/products">Back to products</Link>
      </div>
    );
  }

  if (!order) {
    return <p>Loading order...</p>;
  }

  return (
    <div className="box">
      <h1>Order confirmed</h1>

      <p>
        Status: <strong data-testid="order-status">{order.status}</strong>
      </p>
      <p>Order #{order.id} &middot; {new Date(order.createdAt).toLocaleString()}</p>
      <p>Total: <strong data-testid="order-total">{money(order.total)}</strong></p>
      <p>Shipped to: {order.shippingAddress}</p>

      <h2>Items</h2>
      <ul className="cart-list">
        {order.items.map((item, index) => (
          <li key={index} className="card cart-item">
            <span>{item.productName}</span>
            <span>&times;{item.quantity}</span>
            <span>{money(item.lineTotal)}</span>
          </li>
        ))}
      </ul>

      <Link className="btn" to="/orders">My orders</Link>
    </div>
  );
}