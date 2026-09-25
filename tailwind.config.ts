import type { Config } from 'tailwindcss';

const config: Config = {
  darkMode: 'class',
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        background: '#09090b',
        surface: '#121215',
        'surface-hover': '#1c1c21',
        border: '#27272a',
        tube: {
          primary: '#ff9000',
          secondary: '#ffa31a',
          dark: '#b36200',
        },
        tiktok: {
          red: '#fe2c55',
          cyan: '#25f4ee',
        },
        x: {
          blue: '#1d9bf0',
          dark: '#0f1419',
        },
      },
      keyframes: {
        heartPulse: {
          '0%': { transform: 'scale(0.3) rotate(-15deg)', opacity: '0' },
          '50%': { transform: 'scale(1.2) rotate(0deg)', opacity: '0.95' },
          '100%': { transform: 'scale(1) rotate(10deg)', opacity: '0' },
        },
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
      },
      animation: {
        'heart-burst': 'heartPulse 0.8s ease-out forwards',
        'fade-in': 'fadeIn 0.2s ease-in-out',
      },
    },
  },
  plugins: [],
};
export default config;
