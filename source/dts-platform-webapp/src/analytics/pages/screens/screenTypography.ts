export const SCREEN_DEFAULT_FONT_FAMILY = '"Lato", "Open Sans Variable", "Inter Variable", system-ui, -apple-system, "Segoe UI", Roboto, Helvetica, Arial, "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", "Noto Sans", sans-serif';

export const SCREEN_MONO_FONT_FAMILY = 'Consolas, Monaco, "Courier New", monospace';

function normalizeFontFamily(value: unknown): string | undefined {
	if (typeof value !== "string") {
		return undefined;
	}
	const trimmed = value.trim();
	return trimmed.length > 0 ? trimmed : undefined;
}

export function resolveScreenFontFamily(
	screenFontFamily?: unknown,
	componentFontFamily?: unknown,
): string {
	return normalizeFontFamily(componentFontFamily)
		?? normalizeFontFamily(screenFontFamily)
		?? SCREEN_DEFAULT_FONT_FAMILY;
}
