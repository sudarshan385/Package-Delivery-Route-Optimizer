import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Route, ShieldCheck, Eye, EyeOff } from 'lucide-react';
import api from '../api/axios';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Select from '../components/ui/Select';

export const extractErrorMessage = (err, fallback = 'Account creation failed') => {
  if (err.response?.data?.errors) {
    const errors = err.response.data.errors;
    if (typeof errors === 'object') {
      return Object.values(errors).join(', ');
    }
  }
  if (typeof err.response?.data === 'string' && err.response.data.trim()) {
    return err.response.data;
  }
  if (err.response?.status === 404) {
    return 'Public self-registration is awaiting backend endpoint activation (/api/auth/register). Please contact your administrator.';
  }
  if (err.response?.status === 403) {
    return 'Self-registration is restricted. Only administrators can provision accounts, or public registration is currently disabled.';
  }
  return err.response?.data?.detail || err.response?.data?.message || err.message || fallback;
};

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    fullName: '',
    username: '',
    email: '',
    role: 'DRIVER', // Safe default: DRIVER (ADMIN is strictly excluded)
    phone: '',
    licenseNumber: '',
    password: '',
    confirmPassword: '',
  });

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    // Security safeguard: Never allow setting ADMIN role through public registration
    if (name === 'role' && value === 'ADMIN') {
      return;
    }
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (loading) return;
    setError('');

    // Client-side validations adhering to backend DTO constraints & test suite
    const trimmedUsername = form.username.trim();
    const trimmedEmail = form.email.trim();

    if (!trimmedUsername || trimmedUsername.length < 3 || trimmedUsername.length > 50) {
      setError('Username must be between 3 and 50 characters');
      return;
    }

    if (!trimmedEmail || !trimmedEmail.includes('@') || !trimmedEmail.includes('.')) {
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

    if (form.role === 'DRIVER' && !form.licenseNumber.trim()) {
      setError('Driver license number is required for Driver accounts');
      return;
    }

    if (form.role !== 'DRIVER' && form.role !== 'USER') {
      setError('Invalid role selected for public registration');
      return;
    }

    const payload = {
      username: trimmedUsername,
      email: trimmedEmail,
      password: form.password,
      role: form.role,
      fullName: form.fullName.trim() || undefined,
    };

    if (form.role === 'DRIVER') {
      payload.phone = form.phone.trim() || undefined;
      payload.licenseNumber = form.licenseNumber.trim();
    }

    setLoading(true);
    try {
      await api.post('/auth/register', payload);
      navigate('/login', {
        replace: true,
        state: { message: 'Account registered successfully. Please sign in with your credentials.' },
      });
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to complete registration'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card" style={{ maxWidth: 480 }}>
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
            <Route size={20} />
          </div>
          <h1>Create an Account</h1>
          <p>Register as a driver or standard user</p>
        </div>

        {error && (
          <div className="alert alert-error" role="alert">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
            <Input
              label="Full Name"
              id="register-fullname"
              name="fullName"
              placeholder="e.g. John Doe"
              value={form.fullName}
              onChange={handleChange}
            />
            <Input
              label="Username"
              id="register-username"
              name="username"
              placeholder="3-50 characters"
              value={form.username}
              onChange={handleChange}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
            <Input
              label="Email Address"
              id="register-email"
              name="email"
              type="email"
              placeholder="john@example.com"
              value={form.email}
              onChange={handleChange}
              required
            />
            <Select
              label="Account Role"
              id="register-role"
              name="role"
              value={form.role}
              onChange={handleChange}
              required
            >
              <option value="USER">USER</option>
              <option value="DRIVER">DRIVER</option>
            </Select>
          </div>

          {form.role === 'DRIVER' && (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
              <Input
                label="Contact Phone"
                id="register-phone"
                name="phone"
                placeholder="e.g. +91 98765 43210"
                value={form.phone}
                onChange={handleChange}
              />
              <Input
                label="Driver License #"
                id="register-license"
                name="licenseNumber"
                placeholder="Official license number"
                value={form.licenseNumber}
                onChange={handleChange}
                required
              />
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
            <div className="form-group">
              <label htmlFor="register-password">Password *</label>
              <div style={{ position: 'relative' }}>
                <input
                  id="register-password"
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
              <label htmlFor="register-confirm-password">Confirm Password *</label>
              <div style={{ position: 'relative' }}>
                <input
                  id="register-confirm-password"
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
            id="register-submit"
            style={{ width: '100%', marginTop: 8 }}
          >
            Create Account
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
          Already have an account?{' '}
          <Link to="/login" style={{ fontWeight: 600 }}>
            Sign in
          </Link>
        </div>
      </div>
    </div>
  );
}
