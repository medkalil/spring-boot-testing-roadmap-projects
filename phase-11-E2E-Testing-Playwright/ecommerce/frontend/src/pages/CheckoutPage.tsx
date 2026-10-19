import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { Cart, Order } from '../api/types';
import { money } from '../lib/format';

export function CheckoutPage() {
  const [cart, setCart] = useState<Cart | null>(null);
  const [address, setAddress] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    api<Cart>('/api/cart')
      .then(setCart)
      .catch(() => setCart({ items: [], total: 0 }));
  }, []);

  async function placeOrder(event: FormEvent) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const body = JSON.stringify({ shippingAddress: address });
      const order = await api<Order>('/api/orders', { method: 'POST', body });
      // The backend computed and persisted the total; the confirmation page
      // re-fetches the order from /api/orders/{id}.
      navigate(`/orders/${order.id}`);
    } catch (err) {
      // Including "cart empty" and stock errors: the UI shows the backend's
      // message and never shows a false success.
      setError(err instanceof ApiError ? err.message : 'Could not place the order');
      setBusy(false);
    }
  }

  if (!cart) {
    return <p>Loading checkout...</p>;
  }

  if (cart.items.length === 0) {
    return (
      <div className="box">
        <h1>Checkout</h1>
        <p>Your cart is empty.</p>
        <Link className="btn" to="/products">Continue shopping</Link>
      </div>
    );
  }

  return (
    <div className="box">
      <h1>Checkout</h1>

      {error && <p className="error" role="alert">{error}</p>}

      <p>
        {cart.items.length} item(s), total <strong data-testid="checkout-total">{money(cart.total)}</strong>
      </p>

      <form onSubmit={placeOrder}>
        <label htmlFor="shippingAddress">Shipping address</label>
        <textarea
          id="shippingAddress"
          name="shippingAddress"
          value={address}
          onChange={(e) => setAddress(e.target.value)}
          required
        />
        <p className="hint">Simulated checkout: no payment is taken.</p>
        <button type="submit" disabled={busy}>Place order</button>
      </form>
    </div>
  );
}