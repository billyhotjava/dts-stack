import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const publicBase = "/analytics/";

export default defineConfig(() => {
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
			target: "chrome98",
		},
		esbuild: {
			target: "chrome98",
		},
	};
});
