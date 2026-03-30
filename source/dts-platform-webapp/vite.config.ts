import tailwindcss from "@tailwindcss/vite";
import { vanillaExtractPlugin } from "@vanilla-extract/vite-plugin";
import react from "@vitejs/plugin-react";
import { visualizer } from "rollup-plugin-visualizer";
import { defineConfig, loadEnv } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve as resolvePath } from "node:path";
import legacy from "@vitejs/plugin-legacy";
import { unwrapCssLayers } from "./tools/postcss/unwrap-css-layers";
import { legacyCssFallbacks } from "./tools/postcss/legacy-css-fallbacks";

const rootDir = fileURLToPath(new URL(".", import.meta.url));
const legacySupportedBrowsers = ["chrome >= 95", "edge >= 95", "firefox >= 102", "safari >= 15.4", "ios >= 15.5", "android >= 95"];
const modernSupportedBrowsers = ["chrome >= 109", "edge >= 109", "firefox >= 115", "safari >= 16.4", "ios >= 16.4", "android >= 109"];
const adminServiceTarget = { host: "dts-admin", containerPort: 8081, hostPort: 18081 };
const analyticsApiServiceTarget = { host: "dts-analytics", containerPort: 3000, hostPort: 3000 };
const analyticsUiServiceTarget = { host: "dts-analytics-webapp-modern", containerPort: 3002, hostPort: 3002 };

type PlatformServerProxyOptions = {
	apiProxyTarget: string;
	apiProxyPrefix: string;
	adminProxyTarget: string;
	analyticsApiProxyTarget: string;
	analyticsUiProxyTarget: string;
};

function resolveServiceProxyTarget(
	envValue: string | undefined,
	service: { host: string; containerPort: number; hostPort: number },
	runningInContainer: boolean,
) {
	if (envValue && envValue.trim()) {
		return envValue.trim();
	}
	return runningInContainer
		? `http://${service.host}:${service.containerPort}`
		: `http://127.0.0.1:${service.hostPort}`;
}

export function createPlatformServerProxy({
	apiProxyTarget,
	apiProxyPrefix,
	adminProxyTarget,
	analyticsApiProxyTarget,
	analyticsUiProxyTarget,
}: PlatformServerProxyOptions) {
	return {
		"/analytics/api": {
			target: analyticsApiProxyTarget,
			changeOrigin: true,
			rewrite: (path: string) => path.replace(/^\/analytics\/api/, "/api"),
			secure: false,
			ws: true,
			xfwd: true,
		},
		// Analytics UI is now embedded in platform-webapp — no proxy needed.
		// The "/analytics/api" proxy above still forwards API calls to dts-analytics.
		"/api": {
			target: apiProxyTarget,
			changeOrigin: true,
			// Auto rewrite when targeting Traefik (HTTPS): /api -> /platform/api
			rewrite:
				typeof apiProxyPrefix === "string" && apiProxyPrefix.length > 0
					? (path: string) => path.replace(/^\/api/, `${apiProxyPrefix}/api`)
					: undefined,
			secure: false,
			xfwd: true,
		},
		// Keep a legacy same-origin admin proxy for local debugging and backwards compatibility.
		"/admin/api": {
			target: adminProxyTarget,
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
		},
	};
}

export default defineConfig(({ mode }) => {
	const rawEnv = loadEnv(mode, process.cwd(), "");
	const env = { ...rawEnv, ...process.env } as Record<string, string | undefined>;
	const base = env.VITE_APP_PUBLIC_PATH || env.VITE_PUBLIC_PATH || "/";
	const isProduction = mode === "production";
	const runningInContainer = existsSync("/.dockerenv");

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
	const adminProxyTarget = resolveServiceProxyTarget(
		env.VITE_ADMIN_PROXY_TARGET || rawEnv.VITE_ADMIN_PROXY_TARGET,
		adminServiceTarget,
		runningInContainer,
	);
	const analyticsApiProxyTarget = resolveServiceProxyTarget(
		env.VITE_ANALYTICS_API_PROXY_TARGET || rawEnv.VITE_ANALYTICS_API_PROXY_TARGET,
		analyticsApiServiceTarget,
		runningInContainer,
	);
	const analyticsUiProxyTarget = resolveServiceProxyTarget(
		env.VITE_ANALYTICS_UI_PROXY_TARGET || rawEnv.VITE_ANALYTICS_UI_PROXY_TARGET,
		analyticsUiServiceTarget,
		runningInContainer,
	);
	const pollingEnabled = String(env.CHOKIDAR_USEPOLLING || "").trim().toLowerCase() === "true";
	const pollingInterval = Number(env.CHOKIDAR_INTERVAL || 1000) || 1000;

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
    const platformBase = (env as any).PLATFORM_PUBLIC_BASE_URL || (env as any).VITE_PLATFORM_PUBLIC_BASE_URL || "";
    const sqlWorkbenchRaw = (env as any).VITE_ENABLE_SQL_WORKBENCH ?? (env as any).WEBAPP_ENABLE_SQL_WORKBENCH ?? "";
    const enable = String(enableRaw).trim().toLowerCase();
    const hide = String(hideRaw).trim().toLowerCase();
    const sqlWorkbench = String(sqlWorkbenchRaw).trim().toLowerCase();
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
            if (sqlWorkbench) {
              js += `w.__RUNTIME_CONFIG__.enableSqlWorkbench=${JSON.stringify(sqlWorkbench)};`;
            }
            if (String(vendorBase).trim()) {
              js += `w.__RUNTIME_CONFIG__.koalVendorBase=${JSON.stringify(String(vendorBase).trim())};`;
            }
            if (String(platformBase).trim()) {
              js += `w.__RUNTIME_CONFIG__.platformBaseUrl=${JSON.stringify(String(platformBase).trim())};`;
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
      watch: {
        ignored: [
          "**/dts-admin-webapp/**",
          "**/.pnpm-store/**",
          "**/.pnpm/**",
          "**/pnpm-store/**",
          "**/.vite-cache/**",
        ],
        usePolling: pollingEnabled,
        interval: pollingEnabled ? pollingInterval : undefined,
      },
				proxy: createPlatformServerProxy({
					apiProxyTarget: runtimeProxyTarget,
					apiProxyPrefix,
					adminProxyTarget,
					analyticsApiProxyTarget,
					analyticsUiProxyTarget,
				}),
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
						"vendor-monaco": ["monaco-editor"],
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
				plugins: legacyEnabled ? [unwrapCssLayers(), legacyCssFallbacks()] : [],
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
