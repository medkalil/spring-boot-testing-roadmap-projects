// Shapes of the backend DTOs (mirrored from com.example.ecommerce.dto).
// The Playwright tests assert against what the user SEES, but the app reads
// these responses, so keeping the types in sync matters.

export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  stock: number;
}

export interface CartItem {
  id: number;
  productId: number;
  name: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface Cart {
  items: CartItem[];
  total: number;
}

export interface OrderItemDto {
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Order {
  id: number;
  status: string;
  total: number;
  shippingAddress: string;
  createdAt: string;
  items: OrderItemDto[];
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}