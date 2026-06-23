import react from "@vitejs/plugin-react";
import legacy from "@vitejs/plugin-legacy";
import { defineConfig } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";

/**
 * Chrome 95 兼容：默认开启 legacy 构建。
 * 部分客户只支持 Chrome 95，故 dev/prod 默认都按 chrome95 目标；
 * 调试新特性时可 LEGACY_BROWSER_BUILD=0 切到 chrome109。
 */
const legacyEnabled = String(process.env.LEGACY_BROWSER_BUILD ?? "1").trim().toLowerCase() !== "0";
const buildTarget = legacyEnabled ? "chrome95" : "chrome109";
const legacyBrowsers = ["chrome >= 95", "edge >= 95", "firefox >= 102", "safari >= 15.4"];

export default defineConfig({
	plugins: [
		react(),
		tsconfigPaths(),
		legacyEnabled &&
			legacy({
				targets: legacyBrowsers,
				renderLegacyChunks: false, // 单包 + esbuild 降级到 chrome95，避免 nomodule 双包
				modernPolyfills: true,
			}),
	].filter(Boolean),
	build: {
		target: buildTarget,
		cssTarget: buildTarget,
		sourcemap: true,
		// antd vendor 块体积大属框架固有，已拆为独立可缓存块；调高阈值消除噪音
		chunkSizeWarningLimit: 1200,
		rollupOptions: {
			output: {
				// 拆 vendor，改善缓存与首屏（reactflow 仅集成/血缘用，路由懒加载后不进首屏）
				manualChunks: {
					react: ["react", "react-dom", "react-router"],
					antd: ["antd", "@ant-design/icons"],
					flow: ["@xyflow/react"],
				},
			},
		},
	},
	esbuild: {
		target: buildTarget,
	},
	server: {
		host: "0.0.0.0", // 绑定所有网卡，便于远程访问
		port: 5273,
	},
	preview: {
		host: "0.0.0.0",
		port: 5273,
	},
});
