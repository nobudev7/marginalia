/** @type {import('tailwindcss').Config} */
export default {
  future: {
    hoverOnlyWhenSupported: true,
  },
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        paper: {
          50: '#F8FBFF',
          100: '#F0F6FC',
          200: '#E2EDF8',
          300: '#CFE0F2',
          400: '#B0CBE6',
          500: '#89AED4',
        },
        ink: {
          950: '#0B1528',
          900: '#0F172A',
          800: '#1E293B',
          700: '#334155',
          600: '#475569',
          500: '#64748B',
          400: '#94A3B8',
          300: '#CBD5E1',
        },
        amberAccent: {
          50: '#F0F7FF',
          100: '#E0EFFD',
          200: '#BAE0FD',
          300: '#7CC4FA',
          400: '#47A7F5',
          500: '#3182CE',
          600: '#2B6CB0',
          700: '#255899',
          800: '#1E467A',
          900: '#16345B',
        },
      },
      fontFamily: {
        serif: ['var(--font-editorial)'],
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
    },
  },
  plugins: [
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    require('@tailwindcss/typography'),
  ],
}
