import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";

const publicBase = "/analytics/";

export default defineConfig(({ mode }) => {
	const rawEnv = loadEnv(mode, process.cwd(), "");
	const isProduction = mode === "production";
	const legacyFlagRaw =
		rawEnv.LEGACY_BROWSER_BUILD ??
		rawEnv.VITE_LEGACY_BUILD ??
		(isProduction ? "1" : "0");
	const normalizedLegacyFlag = String(legacyFlagRaw).trim().toLowerCase();
	const legacyEnabled = normalizedLegacyFlag !== "0" && normalizedLegacyFlag !== "false";
	const buildTarget = legacyEnabled ? "chrome95" : "chrome109";
	const port = Number.parseInt(process.env.PORT ?? "3002", 10);
	return {
		base: publicBase,
		plugins: [react()],
		server: {
			host: true,
			allowedHosts: ["bi.iae.caep", "localhost", "127.0.0.1"],
			port,
			strictPort: true,
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
});
