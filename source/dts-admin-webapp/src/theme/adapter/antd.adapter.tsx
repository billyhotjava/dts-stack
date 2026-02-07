import { StyleProvider, legacyLogicalPropertiesTransformer } from "@ant-design/cssinjs";
import type { Transformer } from "@ant-design/cssinjs";
import type { ThemeConfig } from "antd";
import { App, ConfigProvider, theme } from "antd";
import { ThemeMode } from "#/enum";
import useLocale from "@/locales/use-locale";
import { useSettings } from "@/store/settingStore";
import { removePx, rgbAlpha } from "@/utils/theme";
import { baseThemeTokens } from "../tokens/base";
import { darkColorTokens, lightColorTokens, presetsColors } from "../tokens/color";
import type { UILibraryAdapter } from "../type";

const hasFocusVisiblePattern = /:has\(\s*:focus-visible\s*\)/g;
const hasAdjacentSiblingPattern = /:has\(\s*\+\s*[^)]+\)/g;

const createLegacyHasSelector = (selector: string) => {
	if (!selector.includes(":has(")) {
		return null;
	}

	const fallbackSelector = selector
		.replace(hasFocusVisiblePattern, ":focus-within")
		.replace(hasAdjacentSiblingPattern, ":not(:last-child)");

	if (fallbackSelector === selector || fallbackSelector.includes(":has(") || fallbackSelector.includes(":not()")) {
		return null;
	}

	return fallbackSelector;
};

const legacyHasSelectorTransformer: Transformer = {
	visit: (cssObj) => {
		const transformed = { ...cssObj };
		for (const [selector, style] of Object.entries(cssObj)) {
			const fallbackSelector = createLegacyHasSelector(selector);
			if (!fallbackSelector || transformed[fallbackSelector]) {
				continue;
			}
			transformed[fallbackSelector] = style;
		}

		return transformed;
	},
};

export const AntdAdapter: UILibraryAdapter = ({ mode, children }) => {
	const { language } = useLocale();
	const { themeColorPresets, darkSidebar } = useSettings();
	const algorithm = mode === ThemeMode.Light ? theme.defaultAlgorithm : theme.darkAlgorithm;

	const colorTokens = mode === ThemeMode.Light ? lightColorTokens : darkColorTokens;

	const primaryColorToken = presetsColors[themeColorPresets];

	const isDark = mode === ThemeMode.Dark;
	const siderBg = isDark ? "#161616" : darkSidebar ? "hsla(205, 19%, 23%, 1)" : "#F5F5F5";
	const popupBg = isDark ? colorTokens.background.neutral : colorTokens.background.paper;

	const token: ThemeConfig["token"] = {
		colorPrimary: primaryColorToken.default,
		colorSuccess: colorTokens.palette.success.default,
		colorWarning: colorTokens.palette.warning.default,
		colorError: colorTokens.palette.error.default,
		colorInfo: colorTokens.palette.info.default,

		colorBgLayout: colorTokens.background.default,
		colorBgContainer: colorTokens.background.paper,
		colorBgElevated: popupBg,

		wireframe: false,
		// Enforce Analytics font stack
		fontFamily: `"Lato", "Open Sans Variable", "Inter Variable", system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", "Noto Sans", sans-serif`,
		// Enforce Analytics base size (14px)
		fontSize: 14,

		borderRadiusSM: 6, // Analytics --radius-sm
		borderRadius: 8,   // Analytics --radius-md (base)
		borderRadiusLG: 12, // Analytics --radius-lg

		...(isDark
			? {
				colorText: "rgba(255, 255, 255, 0.92)",
				colorTextSecondary: "rgba(255, 255, 255, 0.60)",
				colorTextTertiary: "rgba(255, 255, 255, 0.38)",
				colorTextQuaternary: "rgba(255, 255, 255, 0.38)",
				colorTextDisabled: "rgba(255, 255, 255, 0.38)",
				colorTextPlaceholder: "rgba(255, 255, 255, 0.38)",
				colorTextLightSolid: "rgba(255, 255, 255, 1)",
				colorBorder: "rgba(255, 255, 255, 0.15)",
				colorBorderSecondary: "rgba(255, 255, 255, 0.08)",
				colorSplit: "rgba(255, 255, 255, 0.08)",
				colorFillAlter: "rgba(255, 255, 255, 0.05)",
				colorFillSecondary: "rgba(255, 255, 255, 0.08)",
				colorFillTertiary: "rgba(255, 255, 255, 0.05)",
				colorFillQuaternary: "rgba(255, 255, 255, 0.03)",
				colorBgTextHover: "rgba(255, 255, 255, 0.05)",
				colorBgTextActive: "rgba(255, 255, 255, 0.08)",
			}
			: {}),
	};

	const components: ThemeConfig["components"] = {
		Breadcrumb: {
			separatorMargin: removePx(baseThemeTokens.spacing[1]),
		},
		Menu: {
			colorFillAlter: "transparent",
			itemColor: colorTokens.text.secondary,
			motionDurationMid: "0.125s",
			motionDurationSlow: "0.125s",
			darkItemBg: siderBg,
			darkPopupBg: popupBg,
			darkSubMenuItemBg: siderBg,
			darkItemColor: "rgba(255,255,255,0.60)",
			darkItemHoverBg: "rgba(255,255,255,0.08)",
			darkItemHoverColor: "rgba(255,255,255,0.92)",
			darkItemSelectedBg: rgbAlpha(primaryColorToken.default, 0.16),
			darkItemSelectedColor: "#C7E0F4",
			darkItemDisabledColor: "rgba(255,255,255,0.38)",
		},
		Button: isDark
			? {
				primaryColor: "#FFFFFF",
				defaultColor: "#FFFFFF",
				defaultHoverColor: "#FFFFFF",
				defaultActiveColor: "#FFFFFF",
				dangerColor: "#FFFFFF",
				solidTextColor: "#FFFFFF",
				textTextColor: "#FFFFFF",
				textTextHoverColor: "#FFFFFF",
				textTextActiveColor: "#FFFFFF",
			}
			: {},
		Tag: {
			borderRadiusSM: 99, // pill shape
			...(isDark
				? {
					defaultBg: "hsla(0, 0%, 100%, 0.06)",
					defaultColor: "hsla(0, 0%, 100%, 0.85)",
				}
				: {}),
		},
		Card: {
			borderRadiusLG: 16, // analytics --radius-xl for cards
		},
		Layout: {
			siderBg: siderBg,
			headerBg: siderBg,
			bodyBg: colorTokens.background.default,
			...(isDark ? { headerColor: colorTokens.text.primary } : {}),
		},
	};

	return (
		<ConfigProvider
			locale={language.antdLocal}
			theme={{ algorithm, token, components }}
			tag={{
				style: {
					borderRadius: 99,
					fontWeight: 700,
					padding: `0 ${baseThemeTokens.spacing[1]}`,
					margin: `0 ${baseThemeTokens.spacing[1]}`,
					borderWidth: 0,
				},
			}}
		>
			<StyleProvider
				hashPriority="high"
				transformers={[legacyLogicalPropertiesTransformer, legacyHasSelectorTransformer]}
			>
				<App>{children}</App>
			</StyleProvider>
		</ConfigProvider>
	);
};
