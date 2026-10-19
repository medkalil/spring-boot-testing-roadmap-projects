import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError, saveToken } from '../api/client';
import type { AuthResponse } from '../api/types';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const body = JSON.stringify({ email, password });
      const auth = await api<AuthResponse>('/api/auth/login', { method: 'POST', body });
      saveToken(auth.accessToken);
      navigate('/dashboard');
    } catch (err) {
      // Same wording for "unknown email" and "wrong password" - and the UI
      // deliberately uses the phrase the tests assert on.
      if (err instanceof ApiError && err.status === 401) {
        setError('Invalid credentials. Check your email and password.');
      } else {
        setError(err instanceof ApiError ? err.message : 'Login failed');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="box">
      <h1>Log in</h1>

      {error && <p className="error" role="alert">{error}</p>}

      <form onSubmit={handleSubmit}>
        <label htmlFor="email">Email</label>
        <input
          id="email"
          name="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        <button type="submit" disabled={busy}>Login</button>
        <p>New here? <Link to="/register">Create an account</Link></p>
      </form>
    </div>
  );
}