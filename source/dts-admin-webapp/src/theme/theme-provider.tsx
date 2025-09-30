import { useEffect } from "react";
import { HtmlDataAttribute } from "#/enum";
import { useSettings } from "@/store/settingStore";
import type { UILibraryAdapter } from "./type";
import { typographyTokens } from "./tokens/typography";

interface ThemeProviderProps {
	children: React.ReactNode;
	adapters?: UILibraryAdapter[];
}

export function ThemeProvider({ children, adapters = [] }: ThemeProviderProps) {
	const { themeMode, themeColorPresets, fontFamily, fontSize } = useSettings();

	// Update HTML class to support Tailwind dark mode
	useEffect(() => {
		const root = window.document.documentElement;
		root.setAttribute(HtmlDataAttribute.ThemeMode, themeMode);
	}, [themeMode]);

	// Dynamically update theme color related CSS variables
	useEffect(() => {
		const root = window.document.documentElement;
		root.setAttribute(HtmlDataAttribute.ColorPalette, themeColorPresets);
	}, [themeColorPresets]);

	// Update font size and font family
	useEffect(() => {
    const root = window.document.documentElement;
    // Clear any old inline font-size to let CSS control it
    root.style.removeProperty("font-size");

    // Derive a scale factor for CSS var --app-font-scale
    const base = 16; // browser default
    const defaultStored = Number(typographyTokens.fontSize.sm); // legacy default (14)
    // Reduce ~10% from previous 1.1 baseline -> 0.99
    const scale = fontSize === defaultStored ? 0.99 : fontSize / base;
    root.style.setProperty("--app-font-scale", String(scale));

		const body = window.document.body;
		body.style.fontFamily = fontFamily;
  }, [fontFamily, fontSize]);

	// Wrap children with adapters
	const wrappedWithAdapters = adapters.reduce(
		(children, Adapter) => (
			<Adapter key={Adapter.name} mode={themeMode}>
				{children}
			</Adapter>
		),
		children,
	);

	return wrappedWithAdapters;
}
