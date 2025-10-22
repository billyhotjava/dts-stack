import tailwindcss from "@tailwindcss/vite";
import { vanillaExtractPlugin } from "@vanilla-extract/vite-plugin";
import react from "@vitejs/plugin-react";
import { visualizer } from "rollup-plugin-visualizer";
import { defineConfig, loadEnv } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";
import { fileURLToPath } from "node:url";
import { resolve as resolvePath } from "node:path";
import legacy from "@vitejs/plugin-legacy";

const rootDir = fileURLToPath(new URL(".", import.meta.url));
const supportedBrowsers = ["chrome >= 109", "edge >= 109", "firefox >= 102", "safari >= 15.4", "ios >= 15.5", "android >= 109"];

export default defineConfig(({ mode }) => {
  const rawEnv = loadEnv(mode, process.cwd(), "");
  const env = { ...process.env, ...rawEnv };
  const base = env.VITE_APP_PUBLIC_PATH || env.VITE_PUBLIC_PATH || "/";
  const isProduction = mode === "production";
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
				targets: supportedBrowsers,
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
        "/api": {
          target: apiProxyTarget,
          changeOrigin: true,
          // If targeting Traefik via HTTPS, auto prefix '/admin'
          rewrite: apiProxyPrefix ? (p) => p.replace(/^\/api/, `${apiProxyPrefix}/api`) : undefined,
          secure: false,
        },
      },
    },

		build: {
			target: "chrome98",
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
  			target: "chrome98",
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
      },
  	};
});
