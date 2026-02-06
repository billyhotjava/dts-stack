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
		"100": "#F9FAFB",
		"200": "#F4F6F8",
		"300": "#DFE3E8",
		"400": "#C4CDD5",
		"500": "#919EAB",
		"600": "#637381",
		"700": "#454F5B",
		"800": "#1C252E",
		"900": "#141A21",
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
		primary: "hsla(204, 66%, 8%, 0.84)", // orionAlpha[80]
		secondary: "hsla(204, 66%, 8%, 0.62)", // orionAlpha[60]
		disabled: "hsla(204, 66%, 8%, 0.44)", // orionAlpha[40]
	},
	background: {
		default: "#f8fafc", // slate-50 — off-white page bg
		paper: commonColors.white, // cards remain white
		neutral: "hsla(240, 4%, 95%, 1)", // orion[10]
	},
};

export const darkColorTokens = {
	palette: paletteColors,
	common: commonColors,
	action: actionColors,
	text: {
		primary: "hsla(0, 0%, 100%, 0.95)", // orionAlphaInverse[80]
		secondary: "hsla(0, 0%, 100%, 0.69)", // orionAlphaInverse[60]
		disabled: "hsla(0, 0%, 100%, 0.46)", // orionAlphaInverse[40]
	},
	background: {
		default: "#09090b", // zinc-950 — deepest page bg
		paper: "#18181b", // zinc-900 — card / elevated surfaces
		neutral: "#27272a", // zinc-800 — popover / highest elevation
	},
};
