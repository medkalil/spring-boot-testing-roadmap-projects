import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { Cart } from '../api/types';
import { money } from '../lib/format';

export function CartPage() {
  const [cart, setCart] = useState<Cart | null>(null);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    api<Cart>('/api/cart')
      .then(setCart)
      .catch(() => setError('Could not load your cart.'));
  }, []);

  async function changeQuantity(itemId: number, quantity: number) {
    setError('');
    if (quantity <= 0) return;
    try {
      const body = JSON.stringify({ quantity });
      setCart(await api<Cart>(`/api/cart/items/${itemId}`, { method: 'PATCH', body }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update quantity');
    }
  }

  async function removeItem(itemId: number) {
    setError('');
    try {
      setCart(await api<Cart>(`/api/cart/items/${itemId}`, { method: 'DELETE' }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove item');
    }
  }

  if (!cart) {
    return <p>Loading cart...</p>;
  }

  if (cart.items.length === 0) {
    return (
      <div className="box">
        <h1>Shopping cart</h1>
        <p>Your cart is empty.</p>
        <Link className="btn" to="/products">Browse products</Link>
      </div>
    );
  }

  return (
    <div>
      <h1>Shopping cart</h1>
      {error && <p className="error" role="alert">{error}</p>}

      <ul className="cart-list">
        {cart.items.map((item) => (
          <li key={item.id} className="card cart-item">
            <div>
              <h3>{item.name}</h3>
              <p className="price">{money(item.unitPrice)} each</p>
            </div>
            <div className="row">
              <button type="button" role='button' onClick={() => changeQuantity(item.id, item.quantity - 1)}>-</button>
              <strong data-testid="cart-quantity">{item.quantity}</strong>
              <button type="button" role='button' onClick={() => changeQuantity(item.id, item.quantity + 1)}>+</button>
            </div>
            <div>
              <p className="price">{money(item.lineTotal)}</p>
              <button type="button" data-testid="remove-btn" className="link" onClick={() => removeItem(item.id)}>Remove</button>
            </div>
          </li>
        ))}
      </ul>

      <div className="row cart-summary">
        <p>Total: <strong data-testid="cart-total">{money(cart.total)}</strong></p>
        <button type="button" className="btn" data-testid="cart-checkout" onClick={() => navigate('/checkout')}>
          Checkout
        </button>
      </div>
    </div>
  );
}