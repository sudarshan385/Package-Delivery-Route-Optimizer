import React, { useState, useEffect, useRef } from 'react';
import { NavLink, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  Menu,
  X,
  LogOut,
  Sun,
  Moon,
  Route,
  Shield,
  User as UserIcon,
  Palette,
  Check,
} from 'lucide-react';
import Button from '../ui/Button';

export const THEMES = [
  { id: 'midnight-teal', name: 'Midnight Teal', primary: '#14B8A6', bg: '#0B131E', tag: 'Futuristic' },
  { id: 'ocean-blue', name: 'Ocean Blue', primary: '#38BDF8', bg: '#0A1128', tag: 'Vivid' },
  { id: 'emerald-professional', name: 'Emerald Professional', primary: '#10B981', bg: '#0B1511', tag: 'Logistics' },
  { id: 'graphite-lime', name: 'Graphite Lime', primary: '#84CC16', bg: '#111215', tag: 'Tech' },
  { id: 'royal-indigo', name: 'Royal Indigo', primary: '#818CF8', bg: '#0D0C1D', tag: 'Executive' },
  { id: 'arctic-light', name: 'Arctic Light', primary: '#0284C7', bg: '#F8FAFC', tag: 'Light' },
  { id: 'slate-monochrome', name: 'Slate Monochrome', primary: '#94A3B8', bg: '#12161A', tag: 'Minimal' },
  { id: 'sunset-amber', name: 'Sunset Amber', primary: '#F59E0B', bg: '#17110C', tag: 'Warm' },
  { id: 'rose-quartz', name: 'Rose Quartz', primary: '#F43F5E', bg: '#170E14', tag: 'Vibrant' },
  { id: 'high-contrast', name: 'High Contrast Accessibility', primary: '#FFD700', bg: '#000000', tag: 'WCAG AAA' },
];

// Navigation items specification with role-based access controls
export const navLinks = [
  { to: '/', label: 'Dashboard', roles: ['ADMIN', 'DRIVER', 'USER'], end: true },
  { to: '/packages', label: 'Packages', roles: ['ADMIN'] },
  { to: '/packages', label: 'My Packages', roles: ['USER'] },
  { to: '/locations', label: 'Locations', roles: ['ADMIN'] },
  { to: '/drivers', label: 'Drivers', roles: ['ADMIN'] },
  { to: '/vehicles', label: 'Vehicles', roles: ['ADMIN'] },
  { to: '/deliveries', label: 'Deliveries', roles: ['ADMIN'] },
  { to: '/deliveries', label: 'My Deliveries', roles: ['DRIVER'] },
  { to: '/deliveries', label: 'Track Delivery', roles: ['USER'] },
  { to: '/routes', label: 'Routes', roles: ['ADMIN', 'DRIVER'] },
  { to: '/analytics', label: 'Analytics', roles: ['ADMIN'] },
  { to: '/reports', label: 'Reports', roles: ['ADMIN'] },
  { to: '/ai-optimization', label: 'AI Optimization', roles: ['ADMIN'] },
  { to: '/users', label: 'Users', roles: ['ADMIN'] },
];

export default function Navbar() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [themeMenuOpen, setThemeMenuOpen] = useState(false);
  const userMenuRef = useRef(null);
  const themeMenuRef = useRef(null);
  const [theme, setTheme] = useState(() => localStorage.getItem('app-theme') || 'midnight-teal');

  // Close menus on route changes
  useEffect(() => {
    setMobileMenuOpen(false);
    setUserMenuOpen(false);
    setThemeMenuOpen(false);
  }, [location.pathname]);

  // Sync theme changes
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('app-theme', theme);
  }, [theme]);

  // Handle clicking outside menus
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target)) {
        setUserMenuOpen(false);
      }
      if (themeMenuRef.current && !themeMenuRef.current.contains(e.target)) {
        setThemeMenuOpen(false);
      }
    };
    if (userMenuOpen || themeMenuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [userMenuOpen, themeMenuOpen]);

  // Keyboard accessibility: Escape closes mobile menu or user menu or theme menu
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        if (userMenuOpen) setUserMenuOpen(false);
        if (themeMenuOpen) setThemeMenuOpen(false);
        if (mobileMenuOpen) setMobileMenuOpen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [mobileMenuOpen, userMenuOpen, themeMenuOpen]);

  const selectTheme = (themeId) => {
    setTheme(themeId);
    setThemeMenuOpen(false);
  };

  const userRole = user?.role || 'DRIVER';
  const isDriver = userRole === 'DRIVER';
  const filteredNavLinks = navLinks.filter((item) =>
    item.roles.includes(userRole)
  );

  const profileImageUrl =
    user?.profileImage ||
    user?.avatar ||
    user?.picture ||
    user?.avatarUrl ||
    user?.imageUrl ||
    null;

  const displayName =
    user?.name ||
    user?.fullName ||
    user?.username ||
    'Operator';

  const getInitials = (name) => {
    if (!name) return 'U';
    const parts = name.trim().split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  };

  return (
    <nav className="top-navbar" role="navigation" aria-label="Main Navigation">
      <div className={`top-navbar-container ${isDriver ? 'top-navbar-driver' : ''}`}>
        {/* LEFT: Application Title & Icon */}
        <div className="top-navbar-left">
          <Link to="/" className="navbar-brand" aria-label="Route Optimizer Home">
            <div className="navbar-brand-icon">
              <Route size={16} />
            </div>
            <span className="navbar-brand-text">Route Optimizer</span>
          </Link>
        </div>

        {/* CENTER: Horizontal Navigation Items (Desktop & Tablet) */}
        <div className="top-navbar-center" role="menubar">
          {filteredNavLinks.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `navbar-nav-link ${isActive ? 'active' : ''}`
              }
              role="menuitem"
            >
              {item.label}
            </NavLink>
          ))}
        </div>

        {/* RIGHT: User Profile, Role Badge, Theme Toggle & Logout */}
        <div className="top-navbar-right">
          {/* 10-Theme Selector Dropdown */}
          <div className="relative" ref={themeMenuRef}>
            <button
              type="button"
              className="navbar-icon-btn flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-themed bg-themed-surface hover:bg-themed-elevated transition"
              onClick={() => setThemeMenuOpen((prev) => !prev)}
              title="Select visual theme (10 presets available)"
              aria-label="Select visual theme"
              id="theme-selector-btn"
            >
              <Palette size={14} style={{ color: 'var(--color-primary)' }} />
              <span className="text-xs font-semibold hidden md:inline" style={{ color: 'var(--color-primary)' }}>
                {THEMES.find((t) => t.id === theme)?.name || 'Theme'}
              </span>
            </button>

            {themeMenuOpen && (
              <div
                className="absolute right-0 mt-2 w-64 rounded-xl shadow-2xl p-2 z-[9999] border"
                style={{
                  backgroundColor: 'var(--color-dark-surface)',
                  borderColor: 'var(--color-dark-border)',
                  boxShadow: '0 12px 36px rgba(0,0,0,0.6)',
                }}
                role="dialog"
                aria-label="Theme Selector Menu"
              >
                <div className="px-2.5 py-1.5 mb-1.5 border-b border-themed flex items-center justify-between">
                  <span className="text-xs font-bold text-themed-primary flex items-center gap-1.5">
                    <Palette size={12} style={{ color: 'var(--color-primary)' }} /> Visual Themes
                  </span>
                  <span className="text-[10px] text-themed-muted font-medium">10 Presets</span>
                </div>

                <div className="space-y-1 max-h-72 overflow-y-auto">
                  {THEMES.map((t) => {
                    const isSelected = theme === t.id;
                    return (
                      <button
                        key={t.id}
                        type="button"
                        onClick={() => selectTheme(t.id)}
                        className={`w-full flex items-center justify-between px-2.5 py-2 rounded-lg text-xs font-medium transition ${
                          isSelected
                            ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 font-semibold'
                            : 'text-themed-secondary hover:bg-themed-elevated hover:text-themed-primary'
                        }`}
                      >
                        <div className="flex items-center gap-2.5">
                          <span
                            className="w-3.5 h-3.5 rounded-full border border-white/20 shadow-sm shrink-0"
                            style={{ backgroundColor: t.primary }}
                          />
                          <span>{t.name}</span>
                        </div>
                        <div className="flex items-center gap-1.5">
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-themed-elevated text-themed-muted">
                            {t.tag}
                          </span>
                          {isSelected && <Check size={13} className="text-emerald-400" />}
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </div>

          <div className="navbar-divider" />

          {/* User & Role Badge with Profile Avatar */}
          <div
            className="navbar-user-area"
            ref={userMenuRef}
            onClick={() => setUserMenuOpen((prev) => !prev)}
            title={`Signed in as ${displayName}`}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                setUserMenuOpen((prev) => !prev);
              }
            }}
          >
            <div className="navbar-avatar" aria-hidden="true">
              {profileImageUrl ? (
                <img src={profileImageUrl} alt={displayName} className="navbar-avatar-img" />
              ) : (
                <span className="navbar-avatar-initials">{getInitials(displayName)}</span>
              )}
            </div>

            {/* Profile Dropdown Menu */}
            {userMenuOpen && (
              <div
                className="navbar-profile-menu-dropdown"
                role="dialog"
                aria-label="Profile Menu"
                onClick={(e) => e.stopPropagation()}
              >
                <div className="navbar-profile-menu-header">
                  <div className="navbar-profile-menu-avatar">
                    {profileImageUrl ? (
                      <img src={profileImageUrl} alt={displayName} className="navbar-avatar-img" />
                    ) : (
                      <span className="navbar-avatar-initials">{getInitials(displayName)}</span>
                    )}
                  </div>
                  <div className="navbar-profile-menu-userinfo">
                    <span className="navbar-profile-menu-name">{displayName}</span>
                    {user?.email && <span className="navbar-profile-menu-email">{user.email}</span>}
                    {user?.userId && <span className="navbar-profile-menu-id">ID: #{user.userId}</span>}
                  </div>
                </div>
                <div className="navbar-profile-menu-footer">
                  <Link
                    to="/profile"
                    className="navbar-profile-menu-link-btn"
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      width: '100%',
                      padding: '10px 16px',
                      border: 'none',
                      background: 'transparent',
                      color: 'var(--theme-text)',
                      fontSize: '13px',
                      fontWeight: 500,
                      cursor: 'pointer',
                      textDecoration: 'none',
                      transition: 'background 0.2s',
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.background = 'var(--theme-surface-hover)'}
                    onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                    onClick={() => setUserMenuOpen(false)}
                  >
                    <UserIcon size={14} />
                    <span>My Profile</span>
                  </Link>
                </div>
              </div>
            )}
          </div>

          {/* Logout Action */}
          <button
            type="button"
            className="navbar-logout-btn"
            onClick={logout}
            id="logout-btn"
            title="Sign out of operations"
          >
            <LogOut size={14} />
            <span>Logout</span>
          </button>

          {/* Mobile Menu Hamburger Button */}
          <button
            type="button"
            className="navbar-mobile-toggle"
            onClick={() => setMobileMenuOpen((prev) => !prev)}
            aria-expanded={mobileMenuOpen}
            aria-label={mobileMenuOpen ? 'Close menu' : 'Open navigation menu'}
          >
            {mobileMenuOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </div>

      {/* MOBILE RESPONSIVE NAVIGATION DRAWER */}
      {mobileMenuOpen && (
        <div className="navbar-mobile-menu" role="menu">
          <div className="navbar-mobile-user-strip">
            <div className="navbar-user-area" style={{ display: 'flex' }}>
              <div className="navbar-avatar" aria-hidden="true">
                {profileImageUrl ? (
                  <img src={profileImageUrl} alt={displayName} className="navbar-avatar-img" />
                ) : (
                  <span className="navbar-avatar-initials">{getInitials(displayName)}</span>
                )}
              </div>
              <span className="navbar-username">{displayName}</span>
              <span className={`navbar-role-badge role-${userRole.toLowerCase()}`}>
                {userRole}
              </span>
            </div>
            <button
              type="button"
              className="navbar-icon-btn"
              onClick={toggleTheme}
              title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
            >
              {theme === 'dark' ? <Sun size={15} /> : <Moon size={15} />}
              <span style={{ fontSize: 11, marginLeft: 6 }}>
                {theme === 'dark' ? 'Light' : 'Dark'}
              </span>
            </button>
          </div>

          <div className="navbar-mobile-links">
            {filteredNavLinks.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `navbar-mobile-link ${isActive ? 'active' : ''}`
                }
                role="menuitem"
                onClick={() => setMobileMenuOpen(false)}
              >
                {item.label}
              </NavLink>
            ))}
          </div>

          <div className="navbar-mobile-footer">
            <button
              type="button"
              className="btn btn-secondary w-full"
              onClick={logout}
              id="sidebar-logout-btn"
              style={{ width: '100%', justifyContent: 'center' }}
            >
              <LogOut size={14} />
              <span>Logout</span>
            </button>
          </div>
        </div>
      )}
    </nav>
  );
}
