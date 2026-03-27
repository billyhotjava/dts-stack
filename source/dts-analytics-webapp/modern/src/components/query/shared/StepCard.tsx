import { useState, type ReactNode } from "react";

type Props = {
	title: string;
	summary?: string | null;
	defaultExpanded?: boolean;
	disabled?: boolean;
	warning?: string | null;
	children: ReactNode;
};

export function StepCard({ title, summary, defaultExpanded = false, disabled = false, warning, children }: Props) {
	const [expanded, setExpanded] = useState(defaultExpanded);
	const isCollapsed = !expanded && summary != null;

	return (
		<div
			style={{
				border: warning ? "1px solid var(--color-warning, #E8A735)" : "1px solid var(--color-border, #e0e0e0)",
				borderRadius: "var(--radius-md, 8px)",
				marginBottom: "var(--spacing-sm, 8px)",
				opacity: disabled ? 0.5 : 1,
				pointerEvents: disabled ? "none" : undefined,
			}}
		>
			<div
				onClick={() => { if (summary != null) setExpanded((v) => !v); }}
				style={{
					display: "flex",
					alignItems: "center",
					justifyContent: "space-between",
					padding: "var(--spacing-sm, 8px) var(--spacing-md, 12px)",
					cursor: summary != null ? "pointer" : "default",
					userSelect: "none",
					background: isCollapsed ? "var(--color-bg-secondary, #f8f9fa)" : undefined,
					borderRadius: isCollapsed ? "var(--radius-md, 8px)" : "var(--radius-md, 8px) var(--radius-md, 8px) 0 0",
				}}
			>
				<strong>{title}</strong>
				{isCollapsed && (
					<span style={{ color: "var(--color-text-secondary)", fontSize: "var(--font-size-sm, 13px)" }}>
						{summary}
					</span>
				)}
			</div>
			{!isCollapsed && (
				<div style={{ padding: "0 var(--spacing-md, 12px) var(--spacing-md, 12px)" }}>
					{children}
				</div>
			)}
			{warning && (
				<div style={{
					padding: "var(--spacing-xs, 4px) var(--spacing-md, 12px)",
					fontSize: "var(--font-size-sm, 13px)",
					color: "var(--color-warning, #E8A735)",
				}}>
					{warning}
				</div>
			)}
		</div>
	);
}
