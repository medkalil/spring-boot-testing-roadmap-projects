import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';

export function RegisterPage() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setMessage('');
    setBusy(true);
    try {
      const body = JSON.stringify({ fullName, email, password });
      await api<void>('/api/auth/register', { method: 'POST', body });
      setMessage('Account created successfully');
      setFullName('');
      setEmail('');
      setPassword('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Registration failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="box">
      <h1>Create account</h1>

      {message && <p className="success" role="status">{message}</p>}
      {error && <p className="error" role="alert">{error}</p>}

      <form onSubmit={handleSubmit}>
        <label htmlFor="fullName">Full name</label>
        <input
          id="fullName"
          name="fullName"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
        />

        <label htmlFor="email">Email</label>
        <input
          id="email"
          name="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        <button type="submit" disabled={busy}>Register</button>
        <p>Already have an account? <Link to="/login">Log in</Link></p>
      </form>
    </div>
  );
}