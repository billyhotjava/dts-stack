import type { CSSProperties } from "react";

export type DotTone = "done" | "active" | "todo" | "success" | "warning" | "error" | "muted";

const TONE_COLOR: Record<DotTone, string> = {
	done: "var(--success)",
	active: "var(--accent)",
	todo: "var(--ink-subtle)",
	success: "var(--success)",
	warning: "var(--warning)",
	error: "var(--error)",
	muted: "var(--ink-subtle)",
};

// Chrome 95 安全：用不透明的 *-soft 环代替 color-mix 透明混合
const TONE_GLOW: Record<DotTone, string> = {
	done: "var(--success-soft)",
	active: "var(--accent-soft)",
	todo: "transparent",
	success: "var(--success-soft)",
	warning: "var(--warning-soft)",
	error: "var(--error-soft)",
	muted: "transparent",
};

interface StatusDotProps {
	tone: DotTone;
	/** active 态加呼吸光晕 */
	pulse?: boolean;
	size?: number;
	label?: string;
	style?: CSSProperties;
}

/** 统一状态点：阶段状态 / 节点状态 / 运行状态共用一套视觉语言。 */
export function StatusDot({ tone, pulse, size = 8, label, style }: StatusDotProps) {
	const color = TONE_COLOR[tone];
	return (
		<span style={{ display: "inline-flex", alignItems: "center", gap: 6, ...style }}>
			<span
				aria-hidden
				style={{
					width: size,
					height: size,
					borderRadius: "50%",
					background: color,
					boxShadow: pulse ? `0 0 0 3px ${TONE_GLOW[tone]}` : undefined,
					flex: "0 0 auto",
				}}
			/>
			{label ? <span style={{ color: "var(--ink-muted)", fontSize: "var(--text-sm)" }}>{label}</span> : null}
		</span>
	);
}
