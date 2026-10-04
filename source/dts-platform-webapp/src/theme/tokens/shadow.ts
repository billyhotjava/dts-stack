import Color from "color";
import { paletteColors } from "./color";

// Pure neutral dark base for shadows
const orionDark = Color("#1A1A1A");

export const lightShadowTokens = {
	none: "none",
	sm: `0 1px 2px 0 ${Color(paletteColors.gray[500]).alpha(0.08)}`,
	default: `0 4px 8px 0 ${Color(paletteColors.gray[500]).alpha(0.08)}`,
	md: `0 8px 16px 0 ${Color(paletteColors.gray[500]).alpha(0.08)}`,
	lg: `0 12px 24px 0 ${Color(paletteColors.gray[500]).alpha(0.08)}`,
	xl: `0 16px 32px 0 ${Color(paletteColors.gray[500]).alpha(0.08)}`,
	"2xl": `0 20px 40px 0 ${Color(paletteColors.gray[500]).alpha(0.08)}`,
	"3xl": `0 24px 48px 0 ${Color(paletteColors.gray[500]).alpha(0.08)}`,
	inner: `inset 0 2px 4px 0 ${Color(paletteColors.gray[500]).alpha(0.08)}`,

	dialog: `-40px 40px 80px -8px ${orionDark.alpha(0.12)}`,
	card: `0 0 0 1px rgba(0, 0, 0, 0.04), 0 1px 2px rgba(0, 0, 0, 0.05)`,
	dropdown: `0 0 2px 0 ${Color(paletteColors.gray[500]).alpha(0.12)}, -20px 20px 40px -4px ${Color(paletteColors.gray[500]).alpha(0.12)}`,

	primary: `0 8px 16px 0 ${Color(paletteColors.primary.default).alpha(0.24)}`,
	info: `0 8px 16px 0 ${Color(paletteColors.info.default).alpha(0.24)}`,
	success: `0 8px 16px 0 ${Color(paletteColors.success.default).alpha(0.24)}`,
	warning: `0 8px 16px 0 ${Color(paletteColors.warning.default).alpha(0.24)}`,
	error: `0 8px 16px 0 ${Color(paletteColors.error.default).alpha(0.24)}`,
};

export const darkShadowTokens = {
	none: "none",
	sm: "none",
	default: "none",
	md: "none",
	lg: "none",
	xl: "none",
	"2xl": "none",
	"3xl": "none",
	inner: "none",

	dialog: "none",
	card: "none",
	dropdown: `0 0 0 1px rgba(255, 255, 255, 0.08)`,

	primary: `0 8px 16px 0 ${Color(paletteColors.primary.default).alpha(0.24)}`,
	info: `0 8px 16px 0 ${Color(paletteColors.info.default).alpha(0.24)}`,
	success: `0 8px 16px 0 ${Color(paletteColors.success.default).alpha(0.24)}`,
	warning: `0 8px 16px 0 ${Color(paletteColors.warning.default).alpha(0.24)}`,
	error: `0 8px 16px 0 ${Color(paletteColors.error.default).alpha(0.24)}`,
};
