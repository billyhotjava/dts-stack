import tailwindcss from "@tailwindcss/vite";
import { vanillaExtractPlugin } from "@vanilla-extract/vite-plugin";
import react from "@vitejs/plugin-react";
import { visualizer } from "rollup-plugin-visualizer";
import { defineConfig, loadEnv } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";

export default defineConfig(({ mode }) => {
	const rawEnv = loadEnv(mode, process.cwd(), "");
  const env = { ...process.env, ...rawEnv };
	const base = env.VITE_APP_PUBLIC_PATH || env.VITE_PUBLIC_PATH || "/";
	const isProduction = mode === "production";
  // Prefer explicit proxy target via env, fallback to local dev backend
  // JHipster dev profile runs on 8081 by default (see application-dev.yml)
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || "http://localhost:8081";
  // Optional prefix for reverse proxy (e.g., Traefik exposes platform under /platform)
  // If not provided, auto-add '/platform' when targeting HTTPS (Traefik) to reduce manual switching
  const autoPrefix = (() => {
    if (env.VITE_API_PROXY_PREFIX) return ""; // explicit beats auto
    try {
      const u = new URL(apiProxyTarget);
      if (u.protocol === "https:") return "/platform";
    } catch {}
    return "";
  })();
  const apiProxyPrefix = env.VITE_API_PROXY_PREFIX || autoPrefix || "";

	return {
		base,
		plugins: [
			react(),
			vanillaExtractPlugin({
				identifiers: ({ debugId }) => `${debugId}`,
			}),
			tailwindcss(),
			tsconfigPaths(),

			isProduction &&
				visualizer({
					// Avoid auto-opening in CI/Docker to prevent PowerShell/xdg-open errors
					open: env.VITE_VISUALIZER_OPEN === "true" && !process.env.CI,
					gzipSize: true,
					brotliSize: true,
					template: "treemap",
				}),
		].filter(Boolean),

		server: {
			open: true,
			host: true,
			port: 3001,
			proxy: {
				"/api": {
					target: apiProxyTarget,
					changeOrigin: true,
					// Auto rewrite when targeting Traefik (HTTPS): /api -> /platform/api
					rewrite: apiProxyPrefix
						? (path) => path.replace(/^\/api/, `${apiProxyPrefix}/api`)
						: undefined,
					secure: false,
				},
			},
		},

		build: {
			target: "esnext",
			minify: "esbuild",
			sourcemap: !isProduction,
			cssCodeSplit: true,
			chunkSizeWarningLimit: 1500,
			rollupOptions: {
				output: {
					manualChunks: {
						"vendor-core": ["react", "react-dom", "react-router"],
						"vendor-ui": ["antd", "@ant-design/cssinjs", "styled-components"],
						"vendor-utils": ["axios", "dayjs", "i18next", "zustand", "@iconify/react"],
						"vendor-charts": ["apexcharts", "react-apexcharts"],
					},
				},
			},
		},

		optimizeDeps: {
			include: ["react", "react-dom", "react-router", "antd", "axios", "dayjs"],
			exclude: ["@iconify/react"],
		},

		esbuild: {
			drop: isProduction ? ["console", "debugger"] : [],
			legalComments: "none",
			target: "esnext",
		},
	};
});
