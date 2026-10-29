import { createContext, useContext, useState, useEffect } from 'react';
import api from '../api/axios';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [setupRequired, setSetupRequired] = useState(null);
  const [loading, setLoading] = useState(true);

  const checkSetupStatus = async () => {
    try {
      const res = await api.get('/auth/setup/status');
      const required = Boolean(res.data?.setupRequired);
      setSetupRequired(required);
      return required;
    } catch {
      setSetupRequired(false);
      return false;
    }
  };

  useEffect(() => {
    const initAuth = async () => {
      const token = localStorage.getItem('token');
      const savedUser = localStorage.getItem('user');
      if (token && savedUser) {
        try {
          setUser(JSON.parse(savedUser));
          setSetupRequired(false);
          setLoading(false);
          return;
        } catch {
          localStorage.removeItem('token');
          localStorage.removeItem('user');
        }
      }

      await checkSetupStatus();
      setLoading(false);
    };

    initAuth();
  }, []);

  const login = async (username, password) => {
    // Clear any previous session artifacts before authenticating
    localStorage.removeItem('token');
    localStorage.removeItem('user');

    const res = await api.post('/auth/login', { username, password });
    const data = res.data;
    // AuthResponse: accessToken, tokenType, expiresInMs, userId, username, email, role
    localStorage.setItem('token', data.accessToken);
    const userData = {
      userId: data.userId,
      username: data.username,
      email: data.email,
      role: data.role,
    };
    localStorage.setItem('user', JSON.stringify(userData));
    setUser(userData);
    setSetupRequired(false);
    return userData;
  };

  const register = async (registrationData) => {
    // Public self-registration endpoint (defaulted to safe non-ADMIN role)
    const res = await api.post('/auth/register', registrationData);
    return res.data;
  };

  const setup = async (data) => {
    // Initial admin setup: POST /api/auth/setup
    const res = await api.post('/auth/setup', data);
    const respData = res.data;
    localStorage.setItem('token', respData.accessToken);
    const userData = {
      userId: respData.userId,
      username: respData.username,
      email: respData.email,
      role: respData.role,
    };
    localStorage.setItem('user', JSON.stringify(userData));
    setUser(userData);
    setSetupRequired(false);
    return userData;
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, setupRequired, loading, login, register, setup, logout, checkSetupStatus }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
