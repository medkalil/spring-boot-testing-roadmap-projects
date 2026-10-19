import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { clearToken, getToken } from '../api/client';

export function Navbar() {
  const [token, setToken] = useState<string | null>(() => getToken());
  const navigate = useNavigate();

  function logout() {
    clearToken();
    setToken(null);
    navigate('/');
  }

  return (
    <header className="navbar">
      <nav className="container nav-inner">
        <Link to="/" className="brand">Online Store</Link>
        <Link to="/products">Products</Link>
        {token && <Link to="/cart">Cart</Link>}
        {token && <Link to="/orders">My orders</Link>}
        <span className="nav-spacer" />
        {token ? (
          <button type="button" onClick={logout}>Logout</button>
        ) : (
          <Link to="/login">Login</Link>
        )}
      </nav>
    </header>
  );
}