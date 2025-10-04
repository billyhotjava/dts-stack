import tailwindcss from "@tailwindcss/vite";
import { vanillaExtractPlugin } from "@vanilla-extract/vite-plugin";
import react from "@vitejs/plugin-react";
import { visualizer } from "rollup-plugin-visualizer";
import { defineConfig, loadEnv } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";
import { fileURLToPath } from "node:url";
import { resolve as resolvePath } from "node:path";

const rootDir = fileURLToPath(new URL(".", import.meta.url));

export default defineConfig(({ mode }) => {
	const rawEnv = loadEnv(mode, process.cwd(), "");
	const env = { ...process.env, ...rawEnv };
	const base = env.VITE_APP_PUBLIC_PATH || env.VITE_PUBLIC_PATH || "/";
	const isProduction = mode === "production";
	// Prefer explicit proxy target via env, fallback to local dev backend
	// JHipster dev profile runs on 8081 by default (see application-dev.yml)
  // Default to host-mapped platform backend port when running on host
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || "http://localhost:18082";
	// Optional prefix for reverse proxy (e.g., Traefik exposes platform under /platform)
	// If not provided, auto-add '/platform' when targeting HTTPS (Traefik) to reduce manual switching
	const autoPrefix = (() => {
		if (env.VITE_API_PROXY_PREFIX) return ""; // explicit beats auto
		try {
			const u = new URL(apiProxyTarget);
			if (u.protocol === "https:") return "/platform";
		} catch { }
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
      // Restrict file serving to this project only
      fs: { strict: true, allow: [rootDir] },
      // Ignore sibling workspace mounts to avoid cross-project file watching
      watch: { ignored: ["**/dts-admin-webapp/**"] },
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
			exclude: ["@iconify/react", "@vanilla-extract/css"],
		},

		esbuild: {
			drop: isProduction ? ["console", "debugger"] : [],
			legalComments: "none",
			target: "esnext",
		},

    // Do not attempt to resolve absolute container paths in CSS urls
    css: {
      url: {
        filter: (url) => {
          if (url.startsWith("/workspace/")) return false;
          return true;
        },
      },
    },
	};
});
