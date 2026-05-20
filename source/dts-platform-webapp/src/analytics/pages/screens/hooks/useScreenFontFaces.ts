import { useEffect } from "react";
import apiClient from "@/api/apiClient";

type ScreenFontAsset = {
	fontFamily: string;
	url: string;
	format?: string;
};

type ScreenFontResponse = ScreenFontAsset[] | { data?: ScreenFontAsset[] };

const STYLE_ID = "screen-custom-fonts";
let activeConsumers = 0;

function resolveScreenFontAssets(response: ScreenFontResponse): ScreenFontAsset[] {
	const items = Array.isArray(response) ? response : response.data;
	if (!Array.isArray(items)) {
		return [];
	}
	return items.filter((item) => (
		item
		&& typeof item.fontFamily === "string"
		&& item.fontFamily.trim().length > 0
		&& typeof item.url === "string"
		&& item.url.trim().length > 0
	));
}

function escapeCssString(value: string): string {
	return value.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\A ");
}

function resolveFontFormat(format?: string): string {
	const normalized = String(format || "").toLowerCase();
	const formatMap: Record<string, string> = {
		ttf: "truetype",
		otf: "opentype",
		woff: "woff",
		woff2: "woff2",
	};
	return formatMap[normalized] || "truetype";
}

function upsertFontStyle(fonts: ScreenFontAsset[]) {
	if (fonts.length === 0) {
		return;
	}
	let el = document.getElementById(STYLE_ID) as HTMLStyleElement | null;
	if (!el) {
		el = document.createElement("style");
		el.id = STYLE_ID;
		document.head.appendChild(el);
	}
	el.textContent = fonts.map((font) => {
		const fontFamily = escapeCssString(font.fontFamily.trim());
		const url = escapeCssString(font.url.trim());
		const format = resolveFontFormat(font.format);
		return `@font-face { font-family: "${fontFamily}"; src: url("${url}") format("${format}"); font-display: swap; }`;
	}).join("\n");
}

export function useScreenFontFaces() {
	useEffect(() => {
		let cancelled = false;
		activeConsumers += 1;
		apiClient.get<ScreenFontResponse>({ url: "/infra/screen-fonts" })
			.then((res) => {
				if (cancelled || typeof document === "undefined") {
					return;
				}
				upsertFontStyle(resolveScreenFontAssets(res));
			})
			.catch(() => {});

		return () => {
			cancelled = true;
			activeConsumers = Math.max(0, activeConsumers - 1);
			if (activeConsumers === 0 && typeof document !== "undefined") {
				document.getElementById(STYLE_ID)?.remove();
			}
		};
	}, []);
}
