export const FontFamilyPreset = {
	openSans: "Open Sans Variable",
	inter: "Inter Variable",
};

export const typographyTokens = {
	fontFamily: {
		openSans: `"Lato", "Open Sans Variable", "Inter Variable", system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", "Noto Sans", sans-serif`,
		inter: `"Lato", "Open Sans Variable", "Inter Variable", system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", "Noto Sans", sans-serif`,
	},
	fontSize: {
		xs: "11",      // Analytics --font-size-xs: labels, badges
		sm: "12",      // Analytics --font-size-sm: captions, secondary text
		default: "14", // Analytics --font-size-md: body text (base)
		lg: "17",      // Analytics --font-size-lg: subheadings
		xl: "21",      // Analytics --font-size-xl: headings
	},
	fontWeight: {
		light: "300",
		normal: "400",
		medium: "500",
		semibold: "600",
		bold: "700",
	},
	lineHeight: {
		none: "1",
		tight: "1.25",
		normal: "1.5",
		relaxed: "1.75",
	},
};
