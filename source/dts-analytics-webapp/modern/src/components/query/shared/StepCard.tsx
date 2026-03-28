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
			className={`rounded-md mb-2 ${warning ? "border border-warning" : "border border-border-default"} ${disabled ? "opacity-50 pointer-events-none" : ""}`}
		>
			<div
				onClick={() => { if (summary != null) setExpanded((v) => !v); }}
				className={`flex items-center justify-between px-3 py-2 select-none ${isCollapsed ? "bg-surface-muted rounded-md" : "rounded-t-md"} ${summary != null ? "cursor-pointer" : "cursor-default"}`}
			>
				<strong>{title}</strong>
				{isCollapsed && (
					<span className="text-text-secondary text-sm">
						{summary}
					</span>
				)}
			</div>
			{!isCollapsed && (
				<div className="px-3 pb-3">
					{children}
				</div>
			)}
			{warning && (
				<div className="px-3 py-1 text-sm text-warning">
					{warning}
				</div>
			)}
		</div>
	);
}
