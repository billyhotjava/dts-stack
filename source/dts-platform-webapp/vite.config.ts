import { existsSync } from "node:fs";
import { resolve as resolvePath } from "node:path";
import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import { vanillaExtractPlugin } from "@vanilla-extract/vite-plugin";
import legacy from "@vitejs/plugin-legacy";
import react from "@vitejs/plugin-react";
import { visualizer } from "rollup-plugin-visualizer";
import { defineConfig, loadEnv } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";
import { legacyCssFallbacks } from "./tools/postcss/legacy-css-fallbacks";
import { unwrapCssLayers } from "./tools/postcss/unwrap-css-layers";

const rootDir = fileURLToPath(new URL(".", import.meta.url));
const legacySupportedBrowsers = [
	"chrome >= 95",
	"edge >= 95",
	"firefox >= 102",
	"safari >= 15.4",
	"ios >= 15.5",
	"android >= 95",
];
const modernSupportedBrowsers = [
	"chrome >= 109",
	"edge >= 109",
	"firefox >= 115",
	"safari >= 16.4",
	"ios >= 16.4",
	"android >= 109",
];
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
	if (envValue?.trim()) {
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
		"/bi/api": {
			target: analyticsApiProxyTarget,
			changeOrigin: true,
			rewrite: (path: string) => path.replace(/^\/bi\/api/, "/api"),
			secure: false,
			ws: true,
			xfwd: true,
		},
		"/analytics": {
			target: analyticsUiProxyTarget,
			changeOrigin: true,
			secure: false,
			xfwd: true,
		},
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
		// Proxy Admin API under same-origin path to avoid browser CORS in dev.
		// When VITE_ADMIN_API_BASE_URL = '/admin/api', frontend calls hit Vite and are forwarded here.
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
	const analyzeFlag = String(env.ANALYZE ?? "").trim().toLowerCase();
	const analyzeEnabled = isProduction && analyzeFlag !== "" && analyzeFlag !== "0" && analyzeFlag !== "false";
	const runningInContainer = existsSync("/.dockerenv");

	// Default to legacy build (chrome 95+) for both dev and prod so the app loads
	// on older browsers used by customers. Modern-only build still available via
	// LEGACY_BROWSER_BUILD=0 (e.g. when debugging with chrome 109+ features).
	const legacyFlagRaw =
		env.LEGACY_BROWSER_BUILD ?? rawEnv.LEGACY_BROWSER_BUILD ?? env.VITE_LEGACY_BUILD ?? rawEnv.VITE_LEGACY_BUILD ?? "1";
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
	const pollingEnabled =
		String(env.CHOKIDAR_USEPOLLING || "")
			.trim()
			.toLowerCase() === "true";
	const pollingInterval = Number(env.CHOKIDAR_INTERVAL || 1000) || 1000;

	if (mode !== "production") {
		// Helpful runtime log for diagnosing 401 during login in dev
		console.info(`[dev-proxy] target=${runtimeProxyTarget} prefix=${apiProxyPrefix || ""} base=${base}`);
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
		const classifiedBadgeRaw = (env as any).WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE ?? "";
		const workbenchPreferenceApiRaw =
			(env as any).WEBAPP_ENABLE_WORKBENCH_PREFERENCE_API ?? (env as any).VITE_ENABLE_WORKBENCH_PREFERENCE_API ?? "";
		const vendorBase = (env as any).KOAL_VENDOR_BASE || (env as any).VITE_KOAL_VENDOR_BASE || "";
		const platformBase = (env as any).PLATFORM_PUBLIC_BASE_URL || (env as any).VITE_PLATFORM_PUBLIC_BASE_URL || "";
		const allowedExternalHostsRaw =
			(env as any).ALLOWED_EXTERNAL_REDIRECT_HOSTS || (env as any).VITE_ALLOWED_EXTERNAL_REDIRECT_HOSTS || "";
		const allowedExternalHosts = String(allowedExternalHostsRaw)
			.split(",")
			.map((item) => item.trim())
			.filter(Boolean);
		const sqlWorkbenchRaw = (env as any).VITE_ENABLE_SQL_WORKBENCH ?? (env as any).WEBAPP_ENABLE_SQL_WORKBENCH ?? "";
		const sqlIdeV2Raw = (env as any).VITE_ENABLE_SQL_IDE_V2 ?? (env as any).WEBAPP_ENABLE_SQL_IDE_V2 ?? "";
		const enable = String(enableRaw).trim().toLowerCase();
		const hide = String(hideRaw).trim().toLowerCase();
		const classifiedBadge = String(classifiedBadgeRaw).trim().toLowerCase();
		const workbenchPreferenceApi = String(workbenchPreferenceApiRaw).trim().toLowerCase();
		const sqlWorkbench = String(sqlWorkbenchRaw).trim().toLowerCase();
		const sqlIdeV2 = String(sqlIdeV2Raw).trim().toLowerCase();
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
						if (classifiedBadge) {
							js += `w.__RUNTIME_CONFIG__.showClassifiedLoginBadge=${JSON.stringify(classifiedBadge)};`;
						}
						if (workbenchPreferenceApi) {
							js += `w.__RUNTIME_CONFIG__.enableWorkbenchPreferenceApi=${JSON.stringify(workbenchPreferenceApi)};`;
						}
						if (sqlWorkbench) {
							js += `w.__RUNTIME_CONFIG__.enableSqlWorkbench=${JSON.stringify(sqlWorkbench)};`;
						}
						if (sqlIdeV2) {
							js += `w.__RUNTIME_CONFIG__.enableSqlIdeV2=${JSON.stringify(sqlIdeV2)};`;
						}
						if (String(vendorBase).trim()) {
							js += `w.__RUNTIME_CONFIG__.koalVendorBase=${JSON.stringify(String(vendorBase).trim())};`;
						}
						if (String(platformBase).trim()) {
							js += `w.__RUNTIME_CONFIG__.platformBaseUrl=${JSON.stringify(String(platformBase).trim())};`;
						}
						if (allowedExternalHosts.length > 0) {
							js += `w.__RUNTIME_CONFIG__.allowedExternalRedirectHosts=${JSON.stringify(allowedExternalHosts)};`;
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
			// Redirect `import "sonner"` → dedup wrapper everywhere except
			// dedup-toast.ts itself (which needs the real sonner package).
			{
				name: "sonner-dedup",
				enforce: "pre" as const,
				resolveId(source: string, importer: string | undefined) {
					if (source === "sonner" && importer && !importer.includes("dedup-toast")) {
						return resolvePath(rootDir, "src/utils/dedup-toast.ts");
					}
				},
			},
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

			// Opt-in only (`pnpm build:analyze`). gzipSize + brotliSize compress every
			// chunk twice on top of the real build, which is pure overhead for the
			// image build that never reads the report.
			analyzeEnabled &&
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
