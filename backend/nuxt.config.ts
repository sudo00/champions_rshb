export default defineNuxtConfig({
  modules: [
    "@nuxthub/core",
    "@nuxt/eslint",
  ],
  devtools: { enabled: true },
  compatibilityDate: "2025-12-11",
  hub: {
    db: "postgresql",
    kv: false,
    blob: true,
    cache: false,
  },
  eslint: {
    config: {
      stylistic: true,
    },
  },
  nitro: {
    preset: "node-server",
  },
  vite: {
    server: {
      watch: {
        usePolling: true,
        interval: 300,
      },
      hmr: {
        protocol: "ws",
        host: "localhost",
        clientPort: 3000,
      },
    },
  },
  css: ["~/assets/css/main.css"],
  routeRules: {
    "/api/**": {
      cors: true,
    },
  },
});
