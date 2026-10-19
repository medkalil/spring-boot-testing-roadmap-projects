import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { Navbar } from './components/Navbar';
import { RequireAuth } from './components/RequireAuth';
import { CartPage } from './pages/CartPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { DashboardPage } from './pages/DashboardPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { OrderConfirmationPage } from './pages/OrderConfirmationPage';
import { OrdersPage } from './pages/OrdersPage';
import { ProductsPage } from './pages/ProductsPage';
import { RegisterPage } from './pages/RegisterPage';

export default function App() {
  return (
    <BrowserRouter>
      <Navbar />
      <main className="container">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/products" element={<ProductsPage />} />
          <Route
            path="/dashboard"
            element={<RequireAuth><DashboardPage /></RequireAuth>}
          />
          <Route
            path="/cart"
            element={<RequireAuth><CartPage /></RequireAuth>}
          />
          <Route
            path="/checkout"
            element={<RequireAuth><CheckoutPage /></RequireAuth>}
          />
          <Route
            path="/orders"
            element={<RequireAuth><OrdersPage /></RequireAuth>}
          />
          <Route
            path="/orders/:id"
            element={<RequireAuth><OrderConfirmationPage /></RequireAuth>}
          />
          <Route path="*" element={<HomePage />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}