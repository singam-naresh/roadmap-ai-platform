/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: ["class"],
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
    "./App.tsx"
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
        mono: ['Space Mono', 'monospace'],
      },
      colors: {
        border: 'rgba(255, 255, 255, 0.1)',
        background: '#050816',
        foreground: '#FFFFFF',
        primary: {
          DEFAULT: '#7C3AED',
          foreground: '#FFFFFF'
        },
        accent: {
          DEFAULT: '#8B5CF6',
          foreground: '#FFFFFF'
        }
      },
      animation: {
        'pulse-slow': 'pulse 4s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      }
    }
  },
  plugins: [require("tailwindcss-animate")],
};