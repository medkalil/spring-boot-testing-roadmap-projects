import type { Product } from '../api/types';
import { money } from '../lib/format';

export function ProductCard({
  product,
  onAdd,
}: {
  product: Product;
  onAdd: (product: Product) => void;
}) {
  return (
    <article className="card product-card" data-testid="product-card">
      <h3>{product.name}</h3>
      <p className="price">{money(product.price)}</p>
      <p className="desc">{product.description}</p>
      <button type="button" data-testid="add-to-cart" onClick={() => onAdd(product)}>Add to cart</button>
    </article>
  );
}