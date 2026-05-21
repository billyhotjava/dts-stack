import react from "@vitejs/plugin-react";
import legacy from "@vitejs/plugin-legacy";
import { defineConfig, loadEnv } from "vite";
import { fileURLToPath } from "node:url";

const outDir = fileURLToPath(new URL("../resources/static", import.meta.url));
const legacySupportedBrowsers = ["chrome >= 95", "edge >= 95", "firefox >= 60", "safari >= 15.4", "ios >= 15.5", "android >= 95"];
const modernSupportedBrowsers = ["chrome >= 109", "edge >= 109", "firefox >= 115", "safari >= 16.4", "ios >= 16.4", "android >= 109"];

export default defineConfig(({ mode }) => {
  const env = { ...process.env, ...loadEnv(mode, process.cwd(), "") };
  const legacyFlag = String(env.LEGACY_BROWSER_BUILD ?? (mode === "production" ? "1" : "0")).toLowerCase();
  const legacyEnabled = legacyFlag !== "0" && legacyFlag !== "false";
  const buildTarget = legacyEnabled ? "es2015" : "chrome109";

  return {
    base: env.VITE_PUBLIC_PATH || "/",
    plugins: [
      react(),
      legacy({
        targets: legacyEnabled ? legacySupportedBrowsers : modernSupportedBrowsers,
        modernPolyfills: true,
        renderLegacyChunks: false
      })
    ],
    server: {
      host: true,
      port: 3010,
      proxy: {
        "/api": {
          target: env.VITE_API_PROXY_TARGET || "http://localhost:18090",
          changeOrigin: true
        }
      }
    },
    build: {
      target: buildTarget,
      outDir,
      emptyOutDir: true,
      sourcemap: false,
      cssCodeSplit: true
    },
    esbuild: {
      target: buildTarget,
      legalComments: "none"
    }
  };
});
