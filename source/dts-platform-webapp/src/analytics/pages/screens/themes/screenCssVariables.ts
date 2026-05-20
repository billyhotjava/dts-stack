/**
 * CSS Variables injection for screen themes.
 *
 * Instead of passing ScreenThemeTokens through React props,
 * this module injects CSS custom properties on the runtime container.
 * All renderers then read colors via var(--screen-*).
 *
 * This fixes the bug where theme switching in the editor doesn't
 * take effect — because CSS Variables cascade automatically.
 */
import type { ScreenThemeTokens } from '../screenThemes';
import { getThemeTokens } from '../screenThemes';
import type { ScreenCustomTheme, ScreenTheme } from '../types';

/**
 * Map ScreenThemeTokens to CSS variable declarations.
 */
export function themeToCssVariables(tokens: ScreenThemeTokens): Record<string, string> {
	return {
		// Canvas
		'--screen-bg': tokens.canvasBackground,
		'--screen-card-bg': tokens.cardBackground,
		'--screen-card-border': tokens.cardBorder,
		'--screen-card-shadow': tokens.cardShadow,
		'--screen-card-radius': `${tokens.cardBorderRadius}px`,

		// Text
		'--screen-font-family': tokens.fontFamily,
		'--screen-text': tokens.textPrimary,
		'--screen-text-secondary': tokens.textSecondary,
		'--screen-text-muted': tokens.textMuted,
		'--screen-accent': tokens.accentColor,

		// ECharts palette (individual colors for CSS access)
		'--screen-chart-1': tokens.echarts.colorPalette[0] || '#6366f1',
		'--screen-chart-2': tokens.echarts.colorPalette[1] || '#8b5cf6',
		'--screen-chart-3': tokens.echarts.colorPalette[2] || '#06b6d4',
		'--screen-chart-4': tokens.echarts.colorPalette[3] || '#10b981',
		'--screen-chart-5': tokens.echarts.colorPalette[4] || '#f59e0b',
		'--screen-chart-6': tokens.echarts.colorPalette[5] || '#ef4444',

		// ECharts axes
		'--screen-axis-line': tokens.echarts.axisLineColor,
		'--screen-axis-label': tokens.echarts.axisLabelColor,
		'--screen-split-line': tokens.echarts.splitLineColor,
		'--screen-tooltip-bg': tokens.echarts.tooltipBg,
		'--screen-tooltip-border': tokens.echarts.tooltipBorder,

		// Bar gradient
		'--screen-bar-grad-1': tokens.barGradient[0],
		'--screen-bar-grad-2': tokens.barGradient[1],

		// Number card
		'--screen-numcard-bg': tokens.numberCard.background,
		'--screen-numcard-border': tokens.numberCard.border,
		'--screen-numcard-title': tokens.numberCard.titleColor,
		'--screen-numcard-value': tokens.numberCard.valueColor,

		// Scroll board / table
		'--screen-table-header-bg': tokens.scrollBoard.headerBg,
		'--screen-table-odd-bg': tokens.scrollBoard.oddRowBg,
		'--screen-table-even-bg': tokens.scrollBoard.evenRowBg,
		'--screen-table-text': tokens.scrollBoard.textColor,

		// Progress bar
		'--screen-progress-track': tokens.progressBar.trackBg,
		'--screen-progress-fill-1': tokens.progressBar.fillGradient[0],
		'--screen-progress-fill-2': tokens.progressBar.fillGradient[1],
		'--screen-progress-label': tokens.progressBar.labelColor,

		// Gauge
		'--screen-gauge-axis': tokens.gauge.axisLineColor,
		'--screen-gauge-split': tokens.gauge.splitLineColor,
		'--screen-gauge-label': tokens.gauge.axisLabelColor,
		'--screen-gauge-title': tokens.gauge.titleColor,
		'--screen-gauge-detail': tokens.gauge.detailColor,

		// Radar
		'--screen-radar-axis': tokens.radar.axisNameColor,
		'--screen-radar-split': tokens.radar.splitLineColor,

		// Breadcrumb
		'--screen-breadcrumb-bg': tokens.breadcrumb.background,
		'--screen-breadcrumb-text': tokens.breadcrumb.textColor,
		'--screen-breadcrumb-link': tokens.breadcrumb.linkColor,

		// Placeholder
		'--screen-placeholder-bg': tokens.placeholder.background,
		'--screen-placeholder-border': tokens.placeholder.border,
		'--screen-placeholder-color': tokens.placeholder.color,

		// Labels
		'--screen-pie-label': tokens.pieLabelColor,
		'--screen-funnel-label': tokens.funnelLabelColor,
		'--screen-scatter-color': tokens.scatterColor,
		'--screen-error-bg': tokens.errorBg,
	};
}

/**
 * Apply theme CSS variables to a container element.
 */
export function applyThemeCssVariables(container: HTMLElement, theme?: ScreenTheme, customTheme?: ScreenCustomTheme): void {
	const tokens = getThemeTokens(theme, customTheme);
	const vars = themeToCssVariables(tokens);
	for (const [key, value] of Object.entries(vars)) {
		container.style.setProperty(key, value);
	}
}

/**
 * Remove all screen CSS variables from a container.
 */
export function clearThemeCssVariables(container: HTMLElement): void {
	const style = container.style;
	for (let i = style.length - 1; i >= 0; i--) {
		const prop = style.item(i);
		if (prop.startsWith('--screen-')) {
			style.removeProperty(prop);
		}
	}
}

/**
 * Read ECharts color palette from CSS variables on a container.
 * Used by EChartsRenderer to dynamically pick up theme colors.
 */
export function readChartColorsFromCss(container: HTMLElement): string[] {
	const cs = getComputedStyle(container);
	const colors: string[] = [];
	for (let i = 1; i <= 6; i++) {
		const val = cs.getPropertyValue(`--screen-chart-${i}`).trim();
		if (val) colors.push(val);
	}
	return colors.length > 0 ? colors : ['#6366f1', '#8b5cf6', '#06b6d4', '#10b981', '#f59e0b', '#ef4444'];
}
