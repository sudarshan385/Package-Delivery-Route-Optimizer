import { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { User, Eye, EyeOff, Route, ShieldCheck } from 'lucide-react';
import api from '../../api/axios';
import Button from '../ui/Button';
import Input from '../ui/Input';

export const extractErrorMessage = (err, fallback = 'Invalid username or password') => {
  if (err.response?.data?.errors) {
    const errors = err.response.data.errors;
    if (typeof errors === 'object') {
      return Object.values(errors).join(', ');
    }
  }
  if (typeof err.response?.data === 'string' && err.response.data.trim()) {
    return err.response.data;
  }
  if (err.response?.status === 401) {
    if (err.config?.url?.includes('/login')) {
      return 'Invalid username or password';
    }
    return 'Session expired or unauthorized. Please log in again.';
  }
  if (err.response?.status === 403) {
    return 'Access forbidden: invalid credentials or permissions';
  }
  return err.response?.data?.detail || err.response?.data?.message || err.message || fallback;
};

export default function Login() {
  const { login } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [setupRequired, setSetupRequired] = useState(false);

  useEffect(() => {
    if (location.state?.message) {
      setSuccess(location.state.message);
    }

    api.get('/auth/setup/status')
      .then((res) => {
        setSetupRequired(Boolean(res.data?.setupRequired));
      })
      .catch(() => {
        setSetupRequired(false);
      });
  }, [location.state]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (loading) return;
    if (!username.trim() || !password) {
      setError('Please enter both username/email and password');
      return;
    }
    setError('');
    setSuccess('');
    setLoading(true);
    try {
      await login(username.trim(), password);
      navigate('/', { replace: true });
    } catch (err) {
      setError(extractErrorMessage(err, 'Invalid username or password'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="relative min-h-screen w-full overflow-x-hidden flex items-center justify-center md:justify-end px-4 sm:px-8 md:pr-[7%] lg:pr-[8%] xl:pr-[9%] py-8">
      {/* 1. Fullscreen Video Background */}
      <video
        src="/login-authentication.mp4"
        autoPlay
        muted
        loop
        playsInline
        className="fixed inset-0 w-screen h-screen object-cover z-0 pointer-events-none"
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: '100vw',
          height: '100vh',
          objectFit: 'cover',
        }}
      />

      {/* 2. Subtle Dark Navy Overlay for readability and seamless aesthetic */}
      <div
        className="fixed inset-0 pointer-events-none z-10"
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: '100vw',
          height: '100vh',
          background: 'linear-gradient(to right, rgba(6, 11, 24, 0.2) 0%, rgba(6, 11, 24, 0.4) 45%, rgba(6, 11, 24, 0.65) 100%)',
        }}
      />

      {/* 3. Translucent Glassmorphism Login Card on the Right Side Only */}
      <div
        className="login-glass-card relative z-20 w-full max-w-[420px] p-6 sm:p-8"
        style={{
          backgroundColor: 'rgba(10, 20, 38, 0.38)',
          backdropFilter: 'blur(14px) saturate(125%)',
          WebkitBackdropFilter: 'blur(14px) saturate(125%)',
          border: '1px solid rgba(180, 205, 255, 0.22)',
          boxShadow: '0 12px 40px rgba(0, 0, 0, 0.16)',
          borderRadius: '18px',
        }}
      >
        {/* Header Branding */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
            <div
              style={{
                width: 34,
                height: 34,
                borderRadius: 8,
                backgroundColor: 'rgba(37, 99, 235, 0.85)',
                border: '1px solid rgba(147, 197, 253, 0.35)',
                color: '#FFFFFF',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Route size={18} />
            </div>
            <div>
              <span style={{ fontSize: 14, fontWeight: 700, color: '#FFFFFF', letterSpacing: '-0.2px' }}>
                RouteOptimizer
              </span>
              <span style={{ display: 'block', fontSize: 10, color: '#60A5FA', fontWeight: 600, letterSpacing: '0.5px', textTransform: 'uppercase' }}>
                Operations Portal
              </span>
            </div>
          </div>
          <h1 style={{ fontSize: 18, fontWeight: 700, margin: 0, color: '#FFFFFF', letterSpacing: '-0.3px' }}>
            Sign In
          </h1>
          <p style={{ fontSize: 12, color: '#94A3B8', marginTop: 4, lineHeight: 1.4 }}>
            Enter your credentials to access operations
          </p>
        </div>

        {/* Feedback Alerts */}
        {success && (
          <div
            className="alert alert-success"
            role="status"
            style={{
              marginBottom: 16,
              padding: '10px 14px',
              borderRadius: '8px',
              backgroundColor: 'rgba(16, 185, 129, 0.15)',
              border: '1px solid rgba(16, 185, 129, 0.35)',
              color: '#6EE7B7',
              fontSize: 12,
            }}
          >
            {success}
          </div>
        )}
        {error && (
          <div
            className="alert alert-error"
            role="alert"
            style={{
              marginBottom: 16,
              padding: '10px 14px',
              borderRadius: '8px',
              backgroundColor: 'rgba(239, 68, 68, 0.18)',
              border: '1px solid rgba(239, 68, 68, 0.35)',
              color: '#FCA5A5',
              fontSize: 12,
            }}
          >
            {error}
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleSubmit} noValidate>
          <div style={{ marginBottom: 16 }}>
            <Input
              label="Username or Email"
              id="login-username"
              type="text"
              icon={User}
              placeholder="Enter your username or email"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              autoComplete="username"
              autoFocus
              style={{
                backgroundColor: 'rgba(8, 16, 30, 0.28)',
                border: '1px solid rgba(180, 205, 255, 0.2)',
                color: '#FFFFFF',
                padding: '10px 12px 10px 34px',
                borderRadius: '8px',
                fontSize: 12,
              }}
            />
          </div>

          <div className="form-group" style={{ marginBottom: 18 }}>
            <label htmlFor="login-password" style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#E2E8F0', marginBottom: 6 }}>
              Password <span style={{ color: 'var(--color-danger, #EF4444)' }}>*</span>
            </label>
            <div style={{ position: 'relative' }}>
              <input
                id="login-password"
                type={showPassword ? 'text' : 'password'}
                className="form-input"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
                style={{
                  width: '100%',
                  padding: '10px 38px 10px 12px',
                  backgroundColor: 'rgba(8, 16, 30, 0.28)',
                  border: '1px solid rgba(180, 205, 255, 0.2)',
                  borderRadius: '8px',
                  color: '#FFFFFF',
                  fontSize: 12,
                  outline: 'none',
                }}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{
                  position: 'absolute',
                  right: 10,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  color: '#94A3B8',
                  cursor: 'pointer',
                  padding: 4,
                  display: 'flex',
                  alignItems: 'center',
                }}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>

          <Button
            type="submit"
            variant="primary"
            disabled={loading}
            loading={loading}
            id="login-submit"
            style={{
              width: '100%',
              marginTop: 6,
              padding: '11px 16px',
              background: 'linear-gradient(135deg, rgba(37, 99, 235, 0.9) 0%, rgba(29, 78, 216, 0.95) 100%)',
              border: '1px solid rgba(147, 197, 253, 0.35)',
              color: '#FFFFFF',
              fontWeight: 600,
              fontSize: 13,
              borderRadius: '8px',
              cursor: loading ? 'not-allowed' : 'pointer',
              boxShadow: '0 4px 14px rgba(37, 99, 235, 0.25)',
              transition: 'all 0.2s ease',
            }}
          >
            Sign In
          </Button>
        </form>

        {/* Footer Links & Authentication Info */}
        <div
          style={{
            marginTop: 20,
            paddingTop: 16,
            borderTop: '1px solid rgba(180, 205, 255, 0.15)',
            fontSize: 12,
            color: '#94A3B8',
            display: 'flex',
            flexDirection: 'column',
            gap: 12,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>New operator or driver?</span>
            <Link
              to="/register"
              id="login-create-account-link"
              style={{
                color: '#60A5FA',
                fontWeight: 600,
                textDecoration: 'none',
                transition: 'color 0.15s ease',
              }}
            >
              Create an account
            </Link>
          </div>

          {setupRequired && (
            <div
              style={{
                padding: '9px 12px',
                backgroundColor: 'rgba(15, 23, 42, 0.35)',
                borderRadius: '8px',
                border: '1px solid rgba(180, 205, 255, 0.18)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                fontSize: 11,
              }}
            >
              <span style={{ color: '#E2E8F0' }}>First time setup?</span>
              <Link to="/setup" style={{ color: '#60A5FA', fontWeight: 600, textDecoration: 'none' }}>
                Set up administrator →
              </Link>
            </div>
          )}

          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: '#64748B' }}>
            <ShieldCheck size={14} color="#60A5FA" />
            <span>Protected by Bearer JWT Authentication</span>
          </div>
        </div>
      </div>
    </div>
  );
}
