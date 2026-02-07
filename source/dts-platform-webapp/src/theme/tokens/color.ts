import { ThemeColorPresets } from "#/enum";
import { rgbAlpha } from "@/utils/theme";

const primary509EE3 = {
	lighter: "#C7E0F4",
	light: "#7AB8E8",
	default: "#509EE3",
	dark: "#2E6FAF",
	darker: "#1A4A7A",
};

export const presetsColors = {
	[ThemeColorPresets.Default]: primary509EE3,
	[ThemeColorPresets.Cyan]: {
		lighter: "#CCF4FE",
		light: "#68CDF9",
		default: "#078DEE",
		dark: "#0351AB",
		darker: "#012972",
	},
	[ThemeColorPresets.Purple]: {
		lighter: "#EBD6FD",
		light: "#B985F4",
		default: "#7635DC",
		dark: "#431A9E",
		darker: "#200A69",
	},
	[ThemeColorPresets.Blue]: primary509EE3,
	[ThemeColorPresets.Orange]: {
		lighter: "#FEF4D4",
		light: "#FED680",
		default: "#FDA92D",
		dark: "#B66816",
		darker: "#793908",
	},
	[ThemeColorPresets.Red]: {
		lighter: "#FFE3D5",
		light: "#FF9882",
		default: "#FF3030",
		dark: "#B71833",
		darker: "#7A0930",
	},
};

/**
 * We recommend picking colors with these values for [Eva Color Design](https://colors.eva.design/):
 *  + lighter : 100
 *  + light : 300
 *  + main : 500
 *  + dark : 700
 *  + darker : 900
 */
export const paletteColors = {
	primary: primary509EE3,
	success: {
		lighter: "#D8FBDE",
		light: "#86E8AB",
		default: "#36B37E",
		dark: "#1B806A",
		darker: "#0A5554",
	},
	warning: {
		lighter: "#FFF5CC",
		light: "#FFD666",
		default: "#FFAB00",
		dark: "#B76E00",
		darker: "#7A4100",
	},
	error: {
		lighter: "#FFE9D5",
		light: "#FFAC82",
		default: "#FF5630",
		dark: "#B71D18",
		darker: "#7A0916",
	},
	info: {
		lighter: "#CAFDF5",
		light: "#61F3F3",
		default: "#00B8D9",
		dark: "#006C9C",
		darker: "#003768",
	},
	gray: {
		"100": "#FAFAFA",
		"200": "#F5F5F5",
		"300": "#E8E8E8",
		"400": "#C8C8C8",
		"500": "#999999",
		"600": "#6B6B6B",
		"700": "#4D4D4D",
		"800": "#2D2D2D",
		"900": "#1A1A1A",
	},
};

export const commonColors = {
	white: "#FFFFFF",
	black: "#09090B",
};

export const actionColors = {
	hover: rgbAlpha(paletteColors.gray[500], 0.1),
	selected: rgbAlpha(paletteColors.gray[500], 0.1),
	focus: rgbAlpha(paletteColors.gray[500], 0.12),
	disabled: rgbAlpha(paletteColors.gray[500], 0.48),
	active: rgbAlpha(paletteColors.gray[500], 1),
};

export const lightColorTokens = {
	palette: paletteColors,
	common: commonColors,
	action: actionColors,
	text: {
		primary: "rgba(0, 0, 0, 0.85)",
		secondary: "rgba(0, 0, 0, 0.55)",
		disabled: "rgba(0, 0, 0, 0.35)",
	},
	background: {
		default: "#FFFFFF", // pure white page bg
		paper: "#FFFFFF", // card — same as page, use border to distinguish
		neutral: "#F5F5F5", // muted / secondary surfaces
	},
};

export const darkColorTokens = {
	palette: paletteColors,
	common: commonColors,
	action: actionColors,
	text: {
		primary: "rgba(255, 255, 255, 0.92)",
		secondary: "rgba(255, 255, 255, 0.60)",
		disabled: "rgba(255, 255, 255, 0.38)",
	},
	background: {
		default: "#1C1C1C", // comfortable dark page bg
		paper: "#232323", // card — lighter than page bg for layering
		neutral: "#2A2A2A", // popover / highest elevation
	},
};
