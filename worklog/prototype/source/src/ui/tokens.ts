/**
 * Swiss / 国际主义设计 token（JS 侧事实源）。
 * 全部 HSL/hex —— 禁用 oklch，保证 Chrome 95 兼容。
 * tokens.css 中的 CSS 变量与此保持同值。
 */
export const palette = {
	// 墨色文字阶
	ink: "hsl(222, 24%, 12%)",
	inkMuted: "hsl(220, 9%, 40%)",
	inkSubtle: "hsl(220, 8%, 56%)",
	// 表面
	surface: "hsl(0, 0%, 100%)",
	surfaceSunken: "hsl(220, 20%, 97%)",
	surfaceRaised: "hsl(0, 0%, 100%)",
	// 1px 发丝分隔线（Swiss 的灵魂）
	hairline: "hsl(220, 16%, 88%)",
	hairlineStrong: "hsl(220, 14%, 78%)",
	// 单一功能强调色：沉稳蓝
	accent: "hsl(222, 80%, 48%)",
	accentHover: "hsl(222, 82%, 42%)",
	accentActive: "hsl(222, 84%, 36%)",
	accentSoft: "hsl(222, 80%, 96%)",
	// 语义色
	success: "hsl(152, 56%, 36%)",
	successSoft: "hsl(152, 46%, 95%)",
	warning: "hsl(38, 92%, 44%)",
	warningSoft: "hsl(38, 92%, 95%)",
	error: "hsl(2, 72%, 50%)",
	errorSoft: "hsl(2, 72%, 96%)",
	info: "hsl(222, 80%, 48%)",
} as const;

/** 8px 基线间距。 */
export const space = {
	xs: 4,
	sm: 8,
	md: 12,
	lg: 16,
	xl: 24,
	xxl: 32,
} as const;

export const radius = {
	sm: 2,
	md: 3,
	lg: 6,
} as const;

export const font = {
	family:
		'"Inter", "Segoe UI", system-ui, -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif',
	mono: '"SFMono-Regular", "JetBrains Mono", Menlo, Consolas, monospace',
} as const;
