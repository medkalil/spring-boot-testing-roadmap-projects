import { Link } from 'react-router-dom';

export function HomePage() {
  return (
    <div className="box">
      <h1>Online Store</h1>
      <p>Quality tech accessories for every desk.</p>
      <p>
        <Link className="btn" to="/products">Browse products</Link>
      </p>
    </div>
  );
}