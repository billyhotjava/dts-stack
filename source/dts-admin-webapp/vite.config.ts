import tailwindcss from "@tailwindcss/vite";
import { vanillaExtractPlugin } from "@vanilla-extract/vite-plugin";
import react from "@vitejs/plugin-react";
import { visualizer } from "rollup-plugin-visualizer";
import { defineConfig, loadEnv } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";
import { fileURLToPath } from "node:url";
import { resolve as resolvePath } from "node:path";
import legacy from "@vitejs/plugin-legacy";
import { unwrapCssLayers } from "./tools/postcss/unwrap-css-layers";

const rootDir = fileURLToPath(new URL(".", import.meta.url));
const legacySupportedBrowsers = ["chrome >= 95", "edge >= 95", "firefox >= 102", "safari >= 15.4", "ios >= 15.5", "android >= 95"];
const modernSupportedBrowsers = ["chrome >= 109", "edge >= 109", "firefox >= 115", "safari >= 16.4", "ios >= 16.4", "android >= 109"];

export default defineConfig(({ mode }) => {
	const rawEnv = loadEnv(mode, process.cwd(), "");
	const env = { ...process.env, ...rawEnv };
	const base = env.VITE_APP_PUBLIC_PATH || env.VITE_PUBLIC_PATH || "/";
	const isProduction = mode === "production";
	const legacyFlagRaw =
		env.LEGACY_BROWSER_BUILD ??
		rawEnv.LEGACY_BROWSER_BUILD ??
		env.VITE_LEGACY_BUILD ??
		rawEnv.VITE_LEGACY_BUILD ??
		(isProduction ? "1" : "0");
	const normalizedLegacyFlag = String(legacyFlagRaw).trim().toLowerCase();
	const legacyEnabled = normalizedLegacyFlag !== "0" && normalizedLegacyFlag !== "false";
	const browserTargets = legacyEnabled ? legacySupportedBrowsers : modernSupportedBrowsers;
	const buildTarget = legacyEnabled ? "chrome95" : "chrome109";
	// Default to host-mapped admin backend port when running on host
	const apiProxyTarget = env.VITE_API_PROXY_TARGET || "http://localhost:18081";
	const autoPrefix = (() => {
		if (env.VITE_API_PROXY_PREFIX) return "";
		try {
			const u = new URL(apiProxyTarget);
			if (u.protocol === "https:") return "/admin";
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
			legacy({
				targets: browserTargets,
				modernPolyfills: true,
				renderLegacyChunks: false,
			}),
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

		resolve: {
			alias: {
				"@": resolvePath(rootDir, "src"),
				"#": resolvePath(rootDir, "src/types"),
			},
		},

		server: {
			open: true,
			host: true,
			port: 3001,
			// Decouple from other workspaces; do not traverse outside project root
			fs: { strict: true, allow: [rootDir] },
			// Ignore any sibling mounts like /workspace/dts-platform-webapp/**
			watch: { ignored: ["**/dts-platform-webapp/**"] },
			proxy: {
				// Serve Koal SDK assets in dev by proxying to the platform dev server,
				// which ships the vendor bundle under /vendor/koal in its public directory.
				// Both dev servers are attached to the same docker network in compose.dev.
				"/vendor/koal": {
					target: process.env.KOAL_VENDOR_PROXY_TARGET || "http://dts-platform-webapp:3001",
					changeOrigin: true,
					secure: false,
					xfwd: true,
				},
				"/api": {
					target: apiProxyTarget,
					changeOrigin: true,
					// If targeting Traefik via HTTPS, auto prefix '/admin'
					rewrite: apiProxyPrefix ? (p) => p.replace(/^\/api/, `${apiProxyPrefix}/api`) : undefined,
					secure: false,
					xfwd: true,
				},
			},
		},

		build: {
			target: buildTarget,
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
			exclude: ["@iconify/react", "@vanilla-extract/css"],
		},

		esbuild: {
			drop: isProduction ? ["console", "debugger"] : [],
			legalComments: "none",
			target: buildTarget,
		},
		// Prevent Vite CSS analyzer from touching absolute container paths
		// that don't belong to this project (e.g., /workspace/dts-platform-webapp/...)
		css: {
			url: {
				filter: (url) => {
					if (url.startsWith("/workspace/")) return false;
					return true;
				},
			},
			postcss: {
				plugins: legacyEnabled ? [unwrapCssLayers()] : [],
			},
		},
	};
});
