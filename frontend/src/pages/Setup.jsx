import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShieldCheck, Route, Eye, EyeOff } from 'lucide-react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';

export const extractErrorMessage = (err, fallback = 'Initial setup failed') => {
  if (err.response?.data?.errors) {
    const errors = err.response.data.errors;
    if (typeof errors === 'object') {
      return Object.values(errors).join(', ');
    }
  }
  return err.response?.data?.detail || err.response?.data?.message || err.message || fallback;
};

export default function Setup() {
  const navigate = useNavigate();
  const { checkSetupStatus } = useAuth();
  const [form, setForm] = useState({
    fullName: '',
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    api.get('/auth/setup/status')
      .then((res) => {
        if (!res.data?.setupRequired) {
          navigate('/login', { replace: true });
        }
      })
      .catch(() => {
        navigate('/login', { replace: true });
      })
      .finally(() => {
        setChecking(false);
      });
  }, [navigate]);

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (form.username.trim().length < 3 || form.username.trim().length > 50) {
      setError('Username must be between 3 and 50 characters');
      return;
    }

    if (!form.email.includes('@') || !form.email.includes('.')) {
      setError('Please provide a valid email address');
      return;
    }

    if (form.password.length < 8) {
      setError('Password must be at least 8 characters');
      return;
    }

    if (form.password !== form.confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    setLoading(true);
    try {
      await api.post('/auth/setup', {
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
        fullName: form.fullName.trim() || undefined,
      });

      if (checkSetupStatus) {
        await checkSetupStatus();
      }

      navigate('/login', {
        replace: true,
        state: { message: 'Administrator account created successfully. Please sign in.' },
      });
    } catch (err) {
      setError(extractErrorMessage(err, 'Initial setup failed'));
    } finally {
      setLoading(false);
    }
  };

  if (checking) {
    return (
      <div className="auth-container">
        <div className="loading-spinner" style={{ height: '100vh' }}>
          <div className="spinner" />
        </div>
      </div>
    );
  }

  return (
    <div className="auth-container">
      <div className="auth-card" style={{ maxWidth: 460 }}>
        {/* Brand header */}
        <div className="auth-header">
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 'var(--radius-button)',
              backgroundColor: 'var(--color-primary)',
              color: '#FFFFFF',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 12px',
            }}
          >
            <ShieldCheck size={20} />
          </div>
          <h1>Initial Administrator Setup</h1>
          <p>Initialize RouteOptimizer system with the master administrator account</p>
        </div>

        {error && (
          <div className="alert alert-error" role="alert">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <Input
            label="Full Name"
            id="setup-fullname"
            name="fullName"
            placeholder="e.g. System Administrator"
            value={form.fullName}
            onChange={handleChange}
          />

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Input
              label="Admin Username"
              id="setup-username"
              name="username"
              placeholder="Min 3 characters"
              value={form.username}
              onChange={handleChange}
              required
            />
            <Input
              label="Email Address"
              id="setup-email"
              name="email"
              type="email"
              placeholder="admin@example.com"
              value={form.email}
              onChange={handleChange}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div className="form-group">
              <label htmlFor="setup-password">Password *</label>
              <div style={{ position: 'relative' }}>
                <input
                  id="setup-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  className="form-input"
                  placeholder="Min 8 characters"
                  value={form.password}
                  onChange={handleChange}
                  required
                  style={{ paddingRight: 36 }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  style={{
                    position: 'absolute',
                    right: 8,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    background: 'none',
                    border: 'none',
                    color: 'var(--theme-muted)',
                    cursor: 'pointer',
                    padding: 4,
                  }}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="setup-confirm-password">Confirm Password *</label>
              <div style={{ position: 'relative' }}>
                <input
                  id="setup-confirm-password"
                  name="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  className="form-input"
                  placeholder="Re-enter password"
                  value={form.confirmPassword}
                  onChange={handleChange}
                  required
                  style={{ paddingRight: 36 }}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  style={{
                    position: 'absolute',
                    right: 8,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    background: 'none',
                    border: 'none',
                    color: 'var(--theme-muted)',
                    cursor: 'pointer',
                    padding: 4,
                  }}
                  aria-label={showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'}
                >
                  {showConfirmPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>
          </div>

          <Button
            type="submit"
            variant="primary"
            disabled={loading}
            loading={loading}
            id="setup-submit"
            style={{ width: '100%', marginTop: 8 }}
          >
            Create Administrator Account
          </Button>
        </form>

        <div
          style={{
            marginTop: 20,
            paddingTop: 16,
            borderTop: '1px solid var(--theme-border)',
            textAlign: 'center',
            fontSize: 12,
            color: 'var(--theme-muted)',
          }}
        >
          Already set up?{' '}
          <Link to="/login" style={{ fontWeight: 600 }}>
            Sign in
          </Link>
        </div>
      </div>
    </div>
  );
}
