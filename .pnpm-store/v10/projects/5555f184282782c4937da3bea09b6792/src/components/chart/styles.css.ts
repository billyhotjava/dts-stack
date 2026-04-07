import { globalStyle, style } from "@vanilla-extract/css";
import { themeVars } from "@/theme/theme.css";
import { rgbAlpha } from "@/utils/theme";

export const chartWrapper = style({}, "apexcharts-wrapper");

// TOOLTIP
globalStyle(`${chartWrapper} .apexcharts-tooltip`, {
	color: themeVars.colors.text.primary,
	borderRadius: themeVars.borderRadius.lg,
	backdropFilter: "blur(6px)",
	backgroundColor: rgbAlpha(themeVars.colors.background.paperChannel, 0.8),
	boxShadow: themeVars.shadows.card,
});

globalStyle(`${chartWrapper} .apexcharts-tooltip-title`, {
	textAlign: "center",
	fontWeight: "bold",
	backgroundColor: themeVars.colors.background.neutral,
});

// TOOLTIP X
globalStyle(`${chartWrapper} .apexcharts-xaxistooltip`, {
	color: themeVars.colors.text.primary,
	borderRadius: themeVars.borderRadius.lg,
	backdropFilter: "blur(6px)",
	borderColor: "transparent",
	boxShadow: themeVars.shadows.card,
	backgroundColor: themeVars.colors.background.paper,
});

globalStyle(`${chartWrapper} .apexcharts-xaxistooltip::before`, {
	borderBottomColor: rgbAlpha(themeVars.colors.background.paperChannel, 0.8),
});

globalStyle(`${chartWrapper} .apexcharts-xaxistooltip::after`, {
	borderBottomColor: themeVars.colors.background.paper,
});

// LEGEND
globalStyle(`${chartWrapper} .apexcharts-legend`, {
	padding: 0,
});

globalStyle(`${chartWrapper} .apexcharts-legend-series`, {
	display: "inline-flex !important",
	alignItems: "center",
});

globalStyle(`${chartWrapper} .apexcharts-legend-text`, {
	lineHeight: "18px",
	textTransform: "capitalize",
});

// "科技模式"图表增强：淡化网格线 + Glow 发光
globalStyle(`:root[data-theme-mode="dark"] ${chartWrapper} .apexcharts-gridline`, {
	stroke: "rgba(0, 242, 255, 0.14)",
});

globalStyle(`:root[data-theme-mode="dark"] ${chartWrapper} .apexcharts-xaxis text`, {
	fill: "rgba(125, 133, 144, 0.95)",
});

globalStyle(`:root[data-theme-mode="dark"] ${chartWrapper} .apexcharts-yaxis text`, {
	fill: "rgba(125, 133, 144, 0.95)",
});

globalStyle(`:root[data-theme-mode="dark"] ${chartWrapper} .apexcharts-series path`, {
	filter: "drop-shadow(0 0 6px rgba(0, 242, 255, 0.32))",
});

globalStyle(`:root[data-theme-mode="dark"] ${chartWrapper} .apexcharts-series circle`, {
	filter: "drop-shadow(0 0 6px rgba(0, 242, 255, 0.22))",
});
