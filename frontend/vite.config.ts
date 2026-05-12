import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [react()],

    // Dev server proxy — only active during `npm run dev`, not in production builds
    server: {
      proxy: {
        '/api': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8080',
          changeOrigin: true,
          secure: false,
        },
      },
    },

    build: {
      // Suppress non-critical warnings
      rollupOptions: {
        onwarn(warning, warn) {
          if (
            warning.message.includes('Module level directives') ||
            warning.message.includes('"use client"') ||
            warning.message.includes('"was ignored"')
          ) {
            return;
          }
          warn(warning);
        },
      },
      // Increase chunk size warning threshold — we know the bundle is large
      chunkSizeWarningLimit: 1000,
    },

    esbuild: {
      // Suppress directive warnings
      logOverride: { 'ignored-directive': 'silent' },
      // Strip console.log in production builds
      drop: mode === 'production' ? ['console', 'debugger'] : [],
    },
  };
});
