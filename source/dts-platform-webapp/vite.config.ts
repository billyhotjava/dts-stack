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
	const env = { ...rawEnv, ...process.env } as Record<string, string | undefined>;
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

	const rawProxyTarget = rawEnv.VITE_API_PROXY_TARGET;
	const runtimeProxyTarget = env.VITE_API_PROXY_TARGET || rawProxyTarget || "http://localhost:18082";

	let explicitProxyPrefix = process.env.VITE_API_PROXY_PREFIX;
	if (explicitProxyPrefix === undefined) {
		const rawProxyPrefix = rawEnv.VITE_API_PROXY_PREFIX;
		if (rawProxyPrefix && rawProxyTarget && rawProxyTarget === runtimeProxyTarget) {
			explicitProxyPrefix = rawProxyPrefix;
		}
	}

	const autoPrefix = (() => {
		if (explicitProxyPrefix !== undefined) {
			return "";
		}
		try {
			const u = new URL(runtimeProxyTarget);
			if (u.protocol === "https:") return "/platform";
		} catch {}
		return "";
	})();

	const apiProxyPrefix = explicitProxyPrefix !== undefined ? explicitProxyPrefix : autoPrefix;

	if (mode !== "production") {
		// Helpful runtime log for diagnosing 401 during login in dev
		console.info(
			`[dev-proxy] target=${runtimeProxyTarget} prefix=${apiProxyPrefix || ""} base=${base}`,
		);
	}

  // Dev-only helper: serve /runtime-config.js, mirroring prod entrypoint behavior.
  const runtimeConfigPlugin = (() => {
    const koalCsv = (env as any).KOAL_PKI_ENDPOINTS || (env as any).VITE_KOAL_PKI_ENDPOINTS || "";
    const koalList = String(koalCsv)
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    const enableRaw = (env as any).WEBAPP_PASSWORD_LOGIN_ENABLED ?? "";
    const hideRaw = (env as any).VITE_HIDE_PASSWORD_LOGIN ?? "";
    const vendorBase = (env as any).KOAL_VENDOR_BASE || (env as any).VITE_KOAL_VENDOR_BASE || "";
    const enable = String(enableRaw).trim().toLowerCase();
    const hide = String(hideRaw).trim().toLowerCase();
    return {
      name: "dev-runtime-config",
      apply: "serve",
      configureServer(server: any) {
        server.middlewares.use((req: any, res: any, next: any) => {
          if (req.url === "/runtime-config.js") {
            let js = "(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};";
            if (koalList.length > 0) {
              js += `w.__RUNTIME_CONFIG__.koalPkiEndpoints=${JSON.stringify(koalList)};`;
            }
            if (enable) {
              js += `w.__RUNTIME_CONFIG__.enablePasswordLogin=${JSON.stringify(enable)};`;
            }
            if (hide) {
              js += `w.__RUNTIME_CONFIG__.hidePasswordLogin=${JSON.stringify(hide)};`;
            }
            if (String(vendorBase).trim()) {
              js += `w.__RUNTIME_CONFIG__.koalVendorBase=${JSON.stringify(String(vendorBase).trim())};`;
            }
            js += "})(window);\n";
            res.setHeader("Content-Type", "application/javascript; charset=utf-8");
            res.end(js);
            return;
          }
          next();
        });
      },
    };
  })();

  return {
    base,
    envPrefix: ["VITE_", "WEBAPP_"],
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
      runtimeConfigPlugin,

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
      // Accept requests from other containers (admin dev proxy) by host header
      // like 'dts-platform-webapp'.
      allowedHosts: true,
      // Restrict file serving to this project only
      fs: { strict: true, allow: [rootDir] },
      // Ignore sibling workspace mounts to avoid cross-project file watching
      watch: { ignored: ["**/dts-admin-webapp/**"] },
			proxy: {
				"/api": {
					target: runtimeProxyTarget,
					changeOrigin: true,
					// Auto rewrite when targeting Traefik (HTTPS): /api -> /platform/api
					rewrite:
						typeof apiProxyPrefix === "string" && apiProxyPrefix.length > 0
							? (path: string) => path.replace(/^\/api/, `${apiProxyPrefix}/api`)
							: undefined,
					secure: false,
					xfwd: true,
				},
				// Proxy Admin API under same-origin path to avoid browser CORS in dev.
            // When VITE_ADMIN_API_BASE_URL = '/admin/api', frontend calls hit Vite and are forwarded here.
				"/admin/api": ((): any => {
					const adminTarget = env.VITE_ADMIN_PROXY_TARGET || rawEnv.VITE_ADMIN_PROXY_TARGET;
					if (!adminTarget) return undefined;
                    return {
                        target: adminTarget,
                        changeOrigin: true,
                        secure: false,
                        xfwd: true,
                        // Conditional rewrite:
                        // - Keycloak endpoints live under '/api/keycloak/**' on the admin service
                        //   Map '/admin/api/keycloak/**' -> '/api/keycloak/**'
                        // - Admin endpoints live under '/api/admin/**'
                        //   Map all other '/admin/api/**' -> '/api/admin/**'
                        rewrite: (path: string) => {
                            if (/^\/admin\/api\/keycloak\//.test(path)) {
                                return path.replace(/^\/admin\/api\//, "/api/");
                            }
                            return path.replace(/^\/admin\/api/, "/api/admin");
                        },
                    };
                })(),
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

		css: {
			postcss: {
				plugins: legacyEnabled ? [unwrapCssLayers()] : [],
			},
      // Do not attempt to resolve absolute container paths in CSS urls
      url: {
        filter: (url) => {
          if (url.startsWith("/workspace/")) return false;
          return true;
        },
      },
    },
	};
});
