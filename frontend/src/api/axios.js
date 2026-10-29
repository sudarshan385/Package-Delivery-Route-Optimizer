import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Attach JWT token to authenticated requests (strictly exclude public auth endpoints)
api.interceptors.request.use((config) => {
  const isAuthUrl = config.url && (
    config.url.startsWith('/auth') ||
    config.url.startsWith('auth') ||
    config.url.includes('/auth/') ||
    config.url.includes('auth/')
  );

  if (isAuthUrl) {
    if (config.headers) {
      delete config.headers.Authorization;
      delete config.headers.authorization;
    }
    return config;
  }

  const token = localStorage.getItem('token');
  if (token && config.headers) {
    if (typeof config.headers.set === 'function') {
      config.headers.set('Authorization', `Bearer ${token}`);
    } else {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// Handle 401 responses — expired or invalid token
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const isAuthUrl = error.config?.url?.includes('/auth/');
      const currentPath = window.location.pathname;
      
      // Only perform automatic logout if it's a genuinely invalid/expired token on a protected route
      if (!isAuthUrl && currentPath !== '/login' && currentPath !== '/setup' && currentPath !== '/register') {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export const userApi = {
  getUsers: (params) => api.get('/users', { params }),
  getUserById: (id) => api.get(`/users/${id}`),
  createUser: (data) => api.post('/users', data),
  verifyUser: (id) => api.put(`/users/${id}/verify`),
  unverifyUser: (id) => api.put(`/users/${id}/unverify`),
  blockUser: (id) => api.put(`/users/${id}/block`),
  unblockUser: (id) => api.put(`/users/${id}/unblock`),
};

export default api;
