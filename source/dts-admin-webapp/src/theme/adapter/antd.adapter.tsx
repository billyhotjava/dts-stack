import { StyleProvider } from "@ant-design/cssinjs";
import type { ThemeConfig } from "antd";
import { App, ConfigProvider, theme } from "antd";
import { ThemeMode } from "#/enum";
import useLocale from "@/locales/use-locale";
import { useSettings } from "@/store/settingStore";
import { removePx, rgbAlpha } from "@/utils/theme";
import { baseThemeTokens } from "../tokens/base";
import { typographyTokens } from "../tokens/typography";
import { darkColorTokens, lightColorTokens, presetsColors } from "../tokens/color";
import type { UILibraryAdapter } from "../type";

export const AntdAdapter: UILibraryAdapter = ({ mode, children }) => {
	const { language } = useLocale();
	const { themeColorPresets, fontFamily, fontSize } = useSettings();
	const algorithm = mode === ThemeMode.Light ? theme.defaultAlgorithm : theme.darkAlgorithm;

	const colorTokens = mode === ThemeMode.Light ? lightColorTokens : darkColorTokens;

	const primaryColorToken = presetsColors[themeColorPresets];

	const isDark = mode === ThemeMode.Dark;
	const siderBg = isDark ? colorTokens.background.paper : colorTokens.background.default;
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
		fontFamily: fontFamily,
		// Align with global 0.99 scale when using legacy default 14
		fontSize: fontSize === Number(typographyTokens.fontSize.sm) ? Math.round(16 * 0.99) : fontSize,

		borderRadiusSM: removePx(baseThemeTokens.borderRadius.sm),
		borderRadius: removePx(baseThemeTokens.borderRadius.default),
		borderRadiusLG: removePx(baseThemeTokens.borderRadius.lg),

		...(isDark
			? {
					colorText: colorTokens.text.primary,
					colorTextSecondary: colorTokens.text.secondary,
					colorTextTertiary: "#8A94A6",
					colorTextQuaternary: colorTokens.text.disabled,
					colorTextDisabled: colorTokens.text.disabled,
					colorTextPlaceholder: "#8A94A6",
					colorTextLightSolid: "#FFFFFF",
					colorBorder: "rgba(255,255,255,0.10)",
					colorBorderSecondary: "rgba(255,255,255,0.06)",
					colorSplit: "rgba(255,255,255,0.06)",
					colorFillAlter: "rgba(255,255,255,0.03)",
					colorFillSecondary: "rgba(255,255,255,0.06)",
					colorFillTertiary: "rgba(255,255,255,0.04)",
					colorFillQuaternary: "rgba(255,255,255,0.02)",
					colorBgTextHover: "rgba(255,255,255,0.04)",
					colorBgTextActive: "rgba(255,255,255,0.06)",
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
			darkItemColor: colorTokens.text.secondary,
			darkItemHoverBg: "rgba(255,255,255,0.04)",
			darkItemHoverColor: colorTokens.text.primary,
			darkItemSelectedBg: rgbAlpha(primaryColorToken.default, 0.16),
			darkItemSelectedColor: colorTokens.text.primary,
			darkItemDisabledColor: colorTokens.text.disabled,
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
					borderRadius: removePx(baseThemeTokens.borderRadius.md),
					fontWeight: 700,
					padding: `0 ${baseThemeTokens.spacing[1]}`,
					margin: `0 ${baseThemeTokens.spacing[1]}`,
					borderWidth: 0,
				},
			}}
		>
			<StyleProvider hashPriority="high">
				<App>{children}</App>
			</StyleProvider>
		</ConfigProvider>
	);
};
