// The only way the app talks to the Spring Boot API.
//
//   - attaches the JWT from localStorage as Authorization: Bearer to every call
//     (the browser must prove it has a token; the backend re-verifies it)
//   - throws ApiError with the backend's { message } so the UI can show it
//   - the token is what the E2E "route guard" and every cart/order call share
const TOKEN_KEY = 'ecommerce.token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function saveToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');

  const token = getToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const hasBody = options.body !== undefined;
  if (hasBody) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(path, { ...options, headers });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const json = (await response.json()) as { message?: string };
      if (json?.message) {
        message = json.message;
      }
    } catch {
      // response was not JSON - keep the generic message
    }
    throw new ApiError(response.status, message);
  }

  // 201 Created (e.g. register) may carry no body - only parse when present.
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}