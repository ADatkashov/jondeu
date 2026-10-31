import type { Config } from 'tailwindcss';

// JONDEU design tokens (shared with the Flutter app: mobile/lib/core/theme/tokens.dart)
const config: Config = {
  content: ['./app/**/*.{ts,tsx}', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        navy: { DEFAULT: '#183B50', 700: '#1F4A63', 800: '#183B50', 900: '#122E3F' },
        brand: { DEFAULT: '#2477D4', 50: '#EAF2FC', 100: '#D3E4F8', 600: '#1E66B9' },
        accent: { DEFAULT: '#E99545', 50: '#FDF3E8', 600: '#CF7F31' },
        canvas: '#F7F5F0',
        surface: '#FFFFFF',
        ink: { DEFAULT: '#202A32', soft: '#77818A' },
        line: '#E8E4DC',
        success: { DEFAULT: '#36966B', 50: '#E8F4EE' },
        warning: { DEFAULT: '#E8A044', 50: '#FDF4E6' },
        danger: { DEFAULT: '#D9534F', 50: '#FBEAE9' },
      },
      borderRadius: { card: '16px', control: '12px' },
      boxShadow: { card: '0 1px 2px rgba(24,59,80,.05), 0 4px 16px rgba(24,59,80,.05)', pop: '0 12px 40px rgba(24,59,80,.18)' },
      fontFamily: { sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'] },
    },
  },
  plugins: [],
};
export default config;
