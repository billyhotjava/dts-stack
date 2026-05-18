import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
	base: "/metrics/",
	plugins: [react()],
	build: {
		outDir: "../dts-metrics/src/main/resources/static/metrics",
		emptyOutDir: true,
		sourcemap: false,
	},
});
