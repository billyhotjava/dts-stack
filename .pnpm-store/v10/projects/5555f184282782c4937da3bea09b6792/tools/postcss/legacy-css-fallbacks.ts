import type { PluginCreator } from "postcss";

const COLOR_MIX_WITH_TRANSPARENT =
	/^color-mix\(\s*in\s+[^,]+,\s*(.+?)\s+\d+(?:\.\d+)?%\s*,\s*transparent\s*\)$/i;

// Matches oklch(L C H) or oklch(L C H / alpha)
// L: 0-1 (or 0-100% optional), C: 0-0.4, H: 0-360
const OKLCH_RE = /^oklch\(\s*([\d.]+%?)\s+([\d.]+%?)\s+([\d.]+(?:deg|rad|grad|turn)?)\s*(?:\/\s*([\d.]+%?))?\s*\)$/i;

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
 * oklch → approximate hsl fallback for Chrome 95.
 *
 * Uses a simplified linear approximation (Helmholtz-Kohlrausch not modelled).
 * The output is not perceptually perfect but keeps hue/lightness in the right
 * ballpark so icons and text remain visible with the correct hue family.
 *
 * Based on the OKLab → linear sRGB → sRGB conversion path, simplified to
 * avoid floating-point matrix math in a PostCSS plugin.
 */
function oklchToHslFallback(value: string): string | null {
	const m = value.trim().match(OKLCH_RE);
	if (!m) return null;

	// Parse L (0-1 or 0%-100%)
	let L = parseFloat(m[1]);
	if (m[1].endsWith("%")) L = L / 100;

	// Parse C (0-0.4 or 0%-100%)
	let C = parseFloat(m[2]);
	if (m[2].endsWith("%")) C = (C / 100) * 0.4;

	// Parse H in degrees
	let H = parseFloat(m[3]);
	if (m[3].endsWith("rad")) H = H * (180 / Math.PI);
	else if (m[3].endsWith("grad")) H = H * 0.9;
	else if (m[3].endsWith("turn")) H = H * 360;
	H = ((H % 360) + 360) % 360;

	// Parse alpha
	let alpha = 1;
	if (m[4] !== undefined) {
		alpha = parseFloat(m[4]);
		if (m[4].endsWith("%")) alpha = alpha / 100;
	}

	// Approximate: map OKLab lightness (0-1) to HSL lightness (0%-100%)
	// OKLab L is roughly perceptual; a simple power curve brings it close to sRGB
	const hslL = Math.round(Math.pow(L, 1.25) * 100);

	// Approximate chroma to saturation: at full OKLab chroma (≈0.37) → ~100% saturation
	// Clamp to avoid over-saturation on very light/dark shades
	const rawSat = Math.round((C / 0.37) * 100);
	const hslS = Math.max(0, Math.min(100, rawSat));

	const hslH = Math.round(H);

	if (alpha < 1) {
		const a = Math.round(alpha * 100) / 100;
		return `hsla(${hslH}, ${hslS}%, ${hslL}%, ${a})`;
	}
	return `hsl(${hslH}, ${hslS}%, ${hslL}%)`;
}

/**
 * 为 Chrome 95 补齐三类常见样式降级：
 * 1. oklch(...) → hsl(...) 近似值（Chrome 95 不支持 oklch，Tailwind v4 默认使用 oklch）
 * 2. color-mix(..., transparent) → 使用原色作为保底值
 * 3. svh/dvh/lvh → 回退到 vh
 */
export const legacyCssFallbacks: PluginCreator<void> = () => ({
	postcssPlugin: "legacy-css-fallbacks",
	Declaration(decl) {
		const fallbackValues: string[] = [];

		// oklch() → hsl() fallback (must come first so downstream color-mix check sees hsl)
		if (/oklch\s*\(/i.test(decl.value)) {
			const oklchFallback = oklchToHslFallback(decl.value);
			if (oklchFallback && oklchFallback !== decl.value) {
				fallbackValues.push(oklchFallback);
			}
		}

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
