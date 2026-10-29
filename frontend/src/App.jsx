import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Layout from './components/Layout';
import Login from './pages/Login';
import Register from './pages/Register';
import Setup from './pages/Setup';
import Dashboard from './pages/Dashboard';
import Packages from './pages/Packages';
import Locations from './pages/Locations';
import Drivers from './pages/Drivers';
import Vehicles from './pages/Vehicles';
import Deliveries from './pages/Deliveries';
import RouteOptimizer from './pages/RouteOptimizer';
import Analytics from './pages/Analytics';
import Reports from './pages/Reports';
import Users from './pages/Users';
import AIOptimization from './pages/AIOptimization';
import Profile from './pages/Profile';

import Homepage from './pages/Homepage';

function AppRoutes() {
  const { user, setupRequired, loading } = useAuth();

  if (loading) {
    return (
      <div className="loading-spinner" style={{ height: '100vh' }}>
        <div className="spinner" />
      </div>
    );
  }

  // Unauthenticated routes: allow login, registration, initial admin setup, and homepage
  if (!user) {
    return (
      <Routes>
        <Route path="/" element={<Homepage />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/setup" element={<Setup />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    );
  }

  // 3. Authenticated: protected routes
  const hasRole = (roles) => roles.includes(user.role || 'USER');

  const ProtectedRoute = ({ children, allowedRoles }) => {
    if (!hasRole(allowedRoles)) {
      return <Navigate to="/" replace />;
    }
    return children;
  };

  return (
    <Routes>
      <Route path="/" element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="packages" element={<ProtectedRoute allowedRoles={['ADMIN', 'USER']}><Packages /></ProtectedRoute>} />
        <Route path="locations" element={<ProtectedRoute allowedRoles={['ADMIN']}><Locations /></ProtectedRoute>} />
        <Route path="roads" element={<Navigate to="/locations#roads" replace />} />
        <Route path="drivers" element={<ProtectedRoute allowedRoles={['ADMIN']}><Drivers /></ProtectedRoute>} />
        <Route path="vehicles" element={<ProtectedRoute allowedRoles={['ADMIN']}><Vehicles /></ProtectedRoute>} />
        <Route path="deliveries" element={<ProtectedRoute allowedRoles={['ADMIN', 'DRIVER', 'USER']}><Deliveries /></ProtectedRoute>} />
        <Route path="routes" element={<ProtectedRoute allowedRoles={['ADMIN', 'DRIVER']}><RouteOptimizer /></ProtectedRoute>} />
        <Route path="analytics" element={<ProtectedRoute allowedRoles={['ADMIN']}><Analytics /></ProtectedRoute>} />
        <Route path="reports" element={<ProtectedRoute allowedRoles={['ADMIN']}><Reports /></ProtectedRoute>} />
        <Route path="ai-optimization" element={<ProtectedRoute allowedRoles={['ADMIN']}><AIOptimization /></ProtectedRoute>} />
        <Route path="users" element={<ProtectedRoute allowedRoles={['ADMIN']}><Users /></ProtectedRoute>} />
        <Route path="profile" element={<ProtectedRoute allowedRoles={['ADMIN', 'DRIVER', 'USER']}><Profile /></ProtectedRoute>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
