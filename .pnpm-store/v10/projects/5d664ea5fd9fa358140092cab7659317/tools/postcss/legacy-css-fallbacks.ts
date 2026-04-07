import type { PluginCreator } from "postcss";

const COLOR_MIX_WITH_TRANSPARENT =
	/^color-mix\(\s*in\s+[^,]+,\s*(.+?)\s+\d+(?:\.\d+)?%\s*,\s*transparent\s*\)$/i;

const hasLegacyViewportUnit = (value: string) => /\b(?:s|d|l)vh\b/.test(value);

const replaceLegacyViewportUnit = (value: string) => value.replace(/\b(\d*\.?\d+)(?:s|d|l)vh\b/g, "$1vh");

const toColorMixFallback = (value: string): string | null => {
	const normalized = value.trim();
	const match = normalized.match(COLOR_MIX_WITH_TRANSPARENT);
	if (!match) return null;
	const fallback = match[1]?.trim();
	return fallback && fallback.length > 0 ? fallback : null;
};

/**
 * 为 Chrome 95 补齐两类常见样式降级：
 * 1. color-mix(..., transparent) -> 使用原色作为保底值
 * 2. svh/dvh/lvh -> 回退到 vh
 */
export const legacyCssFallbacks: PluginCreator<void> = () => ({
	postcssPlugin: "legacy-css-fallbacks",
	Declaration(decl) {
		const fallbackValues: string[] = [];
		const mixFallback = toColorMixFallback(decl.value);
		if (mixFallback && mixFallback !== decl.value) {
			fallbackValues.push(mixFallback);
		}

		if (hasLegacyViewportUnit(decl.value)) {
			const viewportFallback = replaceLegacyViewportUnit(decl.value);
			if (viewportFallback !== decl.value) {
				fallbackValues.push(viewportFallback);
			}
		}

		for (const fallbackValue of fallbackValues) {
			const prevDecl = decl.prev();
			if (
				prevDecl?.type === "decl" &&
				prevDecl.prop === decl.prop &&
				prevDecl.value === fallbackValue &&
				prevDecl.important === decl.important
			) {
				continue;
			}
			decl.cloneBefore({ value: fallbackValue });
		}
	},
});

legacyCssFallbacks.postcss = true;
