import { StyleProvider } from "@ant-design/cssinjs";
import type { ThemeConfig } from "antd";
import { App, ConfigProvider, theme } from "antd";
import { ThemeMode } from "#/enum";
import useLocale from "@/locales/use-locale";
import { useSettings } from "@/store/settingStore";
import { removePx, rgbAlpha } from "@/utils/theme";
import { baseThemeTokens } from "../tokens/base";
import { darkColorTokens, lightColorTokens, presetsColors } from "../tokens/color";
import type { UILibraryAdapter } from "../type";

export const AntdAdapter: UILibraryAdapter = ({ mode, children }) => {
	const { language } = useLocale();
	const { themeColorPresets } = useSettings();
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
		// Enforce Analytics font stack
		fontFamily: `"Lato", "Open Sans Variable", "Inter Variable", system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", "Noto Sans", sans-serif`,
		// Enforce Analytics base size (14px)
		fontSize: 14,

		borderRadiusSM: 6, // Analytics --radius-sm
		borderRadius: 8,   // Analytics --radius-md (base)
		borderRadiusLG: 12, // Analytics --radius-lg

		...(isDark
			? {
				colorText: "hsla(0, 0%, 100%, 0.95)", // orionAlphaInverse[80]
				colorTextSecondary: "hsla(0, 0%, 100%, 0.69)", // orionAlphaInverse[60]
				colorTextTertiary: "hsla(0, 0%, 100%, 0.46)", // orionAlphaInverse[40]
				colorTextQuaternary: "hsla(0, 0%, 100%, 0.33)", // orionAlphaInverse[30]
				colorTextDisabled: "hsla(0, 0%, 100%, 0.33)", // orionAlphaInverse[30]
				colorTextPlaceholder: "hsla(0, 0%, 100%, 0.46)", // orionAlphaInverse[40]
				colorTextLightSolid: "hsla(0, 0%, 100%, 1)", // orionAlphaInverse[100]
				colorBorder: "hsla(0, 0%, 100%, 0.21)", // orionAlphaInverse[20]
				colorBorderSecondary: "hsla(0, 0%, 100%, 0.10)", // orionAlphaInverse[10]
				colorSplit: "hsla(0, 0%, 100%, 0.10)", // orionAlphaInverse[10]
				colorFillAlter: "hsla(0, 0%, 100%, 0.05)",
				colorFillSecondary: "hsla(0, 0%, 100%, 0.10)", // orionAlphaInverse[10]
				colorFillTertiary: "hsla(0, 0%, 100%, 0.05)",
				colorFillQuaternary: "hsla(0, 0%, 100%, 0.03)",
				colorBgTextHover: "hsla(0, 0%, 100%, 0.05)",
				colorBgTextActive: "hsla(0, 0%, 100%, 0.10)", // orionAlphaInverse[10]
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
		Tag: {
			borderRadiusSM: 99, // pill shape
			...(isDark
				? {
					defaultBg: "hsla(0, 0%, 100%, 0.06)",
					defaultColor: "hsla(0, 0%, 100%, 0.85)",
				}
				: {}),
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
			<StyleProvider hashPriority="high">
				<App>{children}</App>
			</StyleProvider>
		</ConfigProvider>
	);
};
