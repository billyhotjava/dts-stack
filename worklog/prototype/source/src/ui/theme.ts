import type { ThemeConfig } from "antd";
import { palette, radius } from "./tokens";

/**
 * 把 Swiss token 映射到 Ant Design 5 主题。
 * AntD 通过 ConfigProvider 注入；运行期 CSS-in-JS 在 Chrome 95 legacy 构建下已验证可用。
 */
export const antdTheme: ThemeConfig = {
	token: {
		colorPrimary: palette.accent,
		colorSuccess: palette.success,
		colorWarning: palette.warning,
		colorError: palette.error,
		colorInfo: palette.info,
		colorText: palette.ink,
		colorTextSecondary: palette.inkMuted,
		colorTextTertiary: palette.inkSubtle,
		colorBorder: palette.hairline,
		colorBorderSecondary: palette.hairline,
		colorBgLayout: palette.surfaceSunken,
		colorBgContainer: palette.surface,
		borderRadius: radius.md,
		borderRadiusLG: radius.lg,
		borderRadiusSM: radius.sm,
		fontFamily:
			'"Inter", "Segoe UI", system-ui, -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif',
		fontSize: 14,
		controlHeight: 32,
		wireframe: false,
	},
	components: {
		Layout: {
			headerBg: palette.surface,
			headerHeight: 52,
			bodyBg: palette.surfaceSunken,
			siderBg: palette.surface,
		},
		Menu: {
			itemBg: "transparent",
			itemSelectedBg: palette.accentSoft,
			itemSelectedColor: palette.accentActive,
			itemHeight: 38,
			activeBarWidth: 0,
		},
		Table: {
			headerBg: palette.surfaceSunken,
			headerColor: palette.inkMuted,
			cellPaddingBlock: 9,
			rowHoverBg: palette.accentSoft,
			borderColor: palette.hairline,
		},
		Card: {
			colorBorderSecondary: palette.hairline,
		},
		Button: {
			primaryShadow: "none",
			defaultShadow: "none",
		},
	},
};
