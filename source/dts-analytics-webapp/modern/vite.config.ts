import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { existsSync } from "node:fs";
import { defineConfig, loadEnv } from "vite";

const publicBase = "/analytics/";
const platformServiceTarget = { host: "dts-platform", containerPort: 8081, hostPort: 18082 };
const analyticsServiceTarget = { host: "dts-analytics", containerPort: 3000, hostPort: 3000 };

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

	return {
		base: publicBase,
		plugins: [tailwindcss(), react()],
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
		esbuild: {
			target: buildTarget,
		},
	};
}

export default defineConfig(({ mode }) => createAnalyticsViteConfig(mode));
