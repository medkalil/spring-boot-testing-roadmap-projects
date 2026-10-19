import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { getToken } from '../api/client';

/**
 * Frontend route guard: a logged-out customer hitting /cart, /checkout or
 * /orders is redirected to /login.
 *
 * This is UX, NOT security: a user can bypass React and call the API
 * directly, so the real protection stays in Spring Security (400/401/404 on
 * the cart & order endpoints). Always test BOTH layers.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  if (!getToken()) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}