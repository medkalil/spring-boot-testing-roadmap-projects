import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { Cart, Product } from '../api/types';
import { ProductCard } from '../components/ProductCard';

export function ProductsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    api<Product[]>('/api/products')
      .then(setProducts)
      .catch(() => setError('Could not load the catalog.'));
  }, []);

  async function addToCart(product: Product) {
    setError('');
    setNotice('');
    try {
      const body = JSON.stringify({ productId: product.id, quantity: 1 });
      await api<Cart>('/api/cart/items', { method: 'POST', body });
      setNotice(`${product.name} added to your cart`);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        navigate('/login');
        return;
      }
      setError(err instanceof ApiError ? err.message : 'Could not add item to cart');
    }
  }

  return (
    <div>
      <h1>Products</h1>
      {notice && <p className="success" role="status">{notice}</p>}
      {error && <p className="error" role="alert">{error}</p>}

      <div className="grid" data-testid="products-grid">
        {products.map((product) => (
          <ProductCard key={product.id} product={product} onAdd={addToCart} />
        ))}
      </div>
    </div>
  );
}