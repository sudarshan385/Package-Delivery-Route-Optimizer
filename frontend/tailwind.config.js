/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: 'var(--color-primary)',
        accent: 'var(--color-accent)',
        surface: 'var(--color-surface)',
        elevated: 'var(--color-elevated)',
        'app-text': 'var(--color-text)',
        'app-muted': 'var(--color-muted)',
        'app-border': 'var(--color-border)',
        'dark-bg': 'var(--color-dark-bg)',
        'dark-surface': 'var(--color-dark-surface)',
        'dark-elevated': 'var(--color-dark-elevated)',
        'dark-text': 'var(--color-dark-text)',
        'dark-muted': 'var(--color-dark-muted)',
        'dark-border': 'var(--color-dark-border)',
        'themed-bg': 'var(--theme-bg)',
        'themed-surface': 'var(--theme-surface)',
        'themed-elevated': 'var(--theme-elevated)',
        'themed-primary': 'var(--theme-text)',
        'themed-secondary': 'var(--theme-muted)',
        'themed-muted': 'var(--theme-muted)',
        'themed-border': 'var(--theme-border)',
      },
      borderRadius: {
        button: 'var(--radius-button)',
        card: 'var(--radius-card)',
        input: 'var(--radius-input)',
      },
      spacing: {
        'space-1': 'var(--space-1)',
        'space-2': 'var(--space-2)',
        'space-3': 'var(--space-3)',
        'space-4': 'var(--space-4)',
        'space-5': 'var(--space-5)',
        'space-6': 'var(--space-6)',
        'space-7': 'var(--space-7)',
        'space-8': 'var(--space-8)',
      },
      fontFamily: {
        sans: ['"Helvetica Neue"', 'Helvetica', 'Arial', 'sans-serif'],
      },
      fontSize: {
        xs: ['11px', { lineHeight: '1.4' }],
        sm: ['12px', { lineHeight: '1.5' }],
        base: ['13px', { lineHeight: '1.5' }],
        lg: ['14px', { lineHeight: '1.5' }],
        xl: ['16px', { lineHeight: '1.4' }],
      }
    },
  },
  plugins: [],
};
