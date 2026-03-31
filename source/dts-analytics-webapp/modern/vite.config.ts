import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import legacy from "@vitejs/plugin-legacy";
import { existsSync } from "node:fs";
import { resolve as resolvePath } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig, loadEnv } from "vite";
import { unwrapCssLayers } from "./tools/postcss/unwrap-css-layers";
import { legacyCssFallbacks } from "./tools/postcss/legacy-css-fallbacks";

const publicBase = "/analytics/";
const rootDir = fileURLToPath(new URL(".", import.meta.url));
const sharedSessionCoreDir = resolvePath(rootDir, "../../dts-session-core/src");
const legacySupportedBrowsers = ["chrome >= 95", "firefox >= 90", "safari >= 14"];
const platformServiceTarget = { host: "dts-platform", containerPort: 8081, hostPort: 18082 };
const platformUiTarget = { host: "dts-platform-webapp", containerPort: 3001, hostPort: 18012 };
const analyticsServiceTarget = { host: "dts-analytics", containerPort: 3000, hostPort: 3000 };
const runtimeConfigRequestPaths = new Set(["/runtime-config.js", `${publicBase}runtime-config.js`]);

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

export function createAnalyticsServerProxy(
	env: Record<string, string | undefined>,
	runningInContainer = existsSync("/.dockerenv"),
) {
	const platformApiTarget = resolveServiceProxyTarget(env.VITE_API_PROXY_TARGET, platformServiceTarget, runningInContainer);
	const analyticsApiTarget = resolveServiceProxyTarget(
		env.VITE_ANALYTICS_API_PROXY_TARGET,
		analyticsServiceTarget,
		runningInContainer,
	);

	return {
		"/api": {
			target: platformApiTarget,
			changeOrigin: true,
			secure: false,
			xfwd: true,
		},
		"/analytics/api": {
			target: analyticsApiTarget,
			changeOrigin: true,
			secure: false,
			xfwd: true,
			rewrite: (path: string) => path.replace(/^\/analytics\/api/, "/api"),
		},
	};
}

export function resolveBrowserPlatformBaseUrl(
	explicitPlatformBaseUrl: string | undefined,
	requestHost: string | undefined,
	requestProtocol: string | undefined,
) {
	const explicit = String(explicitPlatformBaseUrl || "").trim();
	if (explicit) {
		return explicit;
	}

	const host = String(requestHost || "").trim();
	if (!host) {
		return "";
	}

	const protocol = String(requestProtocol || "").trim().toLowerCase() === "https" ? "https" : "http";
	if (host.endsWith(":3002")) {
		return `${protocol}://${host.replace(/:3002$/, ":18012")}`;
	}
	return `${protocol}://${host}`;
}

export function isRuntimeConfigRequestPath(urlPath: string | undefined): boolean {
	const pathname = String(urlPath || "").split("?")[0];
	return runtimeConfigRequestPaths.has(pathname);
}

export function createAnalyticsViteConfig(
	mode: string,
	envOverrides: Record<string, string | undefined> = process.env,
	runningInContainer = existsSync("/.dockerenv"),
) {
	const rawEnv = loadEnv(mode, process.cwd(), "");
	const env = { ...rawEnv, ...envOverrides };
	const isProduction = mode === "production";
	const legacyFlagRaw =
		env.LEGACY_BROWSER_BUILD ??
		env.VITE_LEGACY_BUILD ??
		(isProduction ? "1" : "0");
	const normalizedLegacyFlag = String(legacyFlagRaw).trim().toLowerCase();
	const legacyEnabled = normalizedLegacyFlag !== "0" && normalizedLegacyFlag !== "false";
	const buildTarget = legacyEnabled ? "chrome95" : "chrome109";
	const port = Number.parseInt(env.PORT ?? "3002", 10);
	const explicitPlatformPublicBaseUrl =
		env.VITE_PLATFORM_PUBLIC_BASE_URL ||
		env.PLATFORM_PUBLIC_BASE_URL ||
		(runningInContainer ? "" : resolveServiceProxyTarget("", platformUiTarget, false));

	const runtimeConfigPlugin = {
		name: "analytics-dev-runtime-config",
		apply: "serve" as const,
		configureServer(server: any) {
			server.middlewares.use((req: any, res: any, next: any) => {
				if (isRuntimeConfigRequestPath(req.url)) {
					const requestProtocol = String(req.headers["x-forwarded-proto"] || "").split(",")[0] || "http";
					const platformPublicBaseUrl = resolveBrowserPlatformBaseUrl(
						explicitPlatformPublicBaseUrl,
						req.headers.host,
						requestProtocol,
					);
					let js = "(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};";
					if (String(platformPublicBaseUrl).trim()) {
						js += `w.__RUNTIME_CONFIG__.platformBaseUrl=${JSON.stringify(String(platformPublicBaseUrl).trim())};`;
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

	return {
		base: publicBase,
		plugins: [
			tailwindcss(),
			react(),
			legacyEnabled &&
				legacy({
					targets: legacySupportedBrowsers,
					modernPolyfills: true,
					renderLegacyChunks: false,
				}),
			runtimeConfigPlugin,
		].filter(Boolean),
		server: {
			host: true,
			// Containerized dev may proxy analytics through sibling services (for example dts-platform-webapp),
			// so keep host checks permissive here like the other webapp dev servers.
			allowedHosts: true,
			port,
			strictPort: true,
			proxy: createAnalyticsServerProxy(env, runningInContainer),
		},
		preview: {
			host: true,
			port,
			strictPort: true,
		},
		build: {
			target: buildTarget,
			chunkSizeWarningLimit: 700,
		},
		resolve: {
			alias: {
				"@dts-session-core": sharedSessionCoreDir,
			},
		},
		esbuild: {
			target: buildTarget,
		},
		css: {
			postcss: {
				plugins: legacyEnabled ? [unwrapCssLayers(), legacyCssFallbacks()] : [],
			},
		},
	};
}

export default defineConfig(({ mode }) => createAnalyticsViteConfig(mode));
