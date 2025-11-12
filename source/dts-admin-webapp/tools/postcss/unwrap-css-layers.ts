import type { PluginCreator } from "postcss";

/**
 * Chrome 95 及以下不支持 CSS Cascade Layers（@layer）。
 * Tailwind 4 默认会把所有样式包裹在 @layer base/components/utilities 里，
 * 旧浏览器会直接忽略整段样式，导致页面完全失真。
 *
 * 该 PostCSS 插件会把 @layer 包裹的内容原样展开，从而兼容旧内核。
 * 仅在 LEGACY_BROWSER_BUILD=1 时启用，避免影响现代浏览器的层叠顺序。
 */
export const unwrapCssLayers: PluginCreator<void> = () => ({
	postcssPlugin: "unwrap-css-layers",
	AtRule(atRule) {
		if (atRule.name !== "layer") return;
		if (!atRule.nodes || atRule.nodes.length === 0) {
			atRule.remove();
			return;
		}
		const clones = atRule.nodes.map((node) => node.clone());
		atRule.replaceWith(...clones);
	},
});

unwrapCssLayers.postcss = true;
