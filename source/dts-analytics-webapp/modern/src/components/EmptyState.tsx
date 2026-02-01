import type { ReactNode } from "react";

type Props = {
	title: ReactNode;
	description?: ReactNode;
	action?: ReactNode;
};

export function EmptyState({ title, description, action }: Props) {
	return (
		<div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 12, textAlign: "center", padding: "var(--spacing-2xl) var(--spacing-md)" }}>
			<div style={{ width: 80, height: 80, borderRadius: "50%", background: "var(--color-bg-tertiary)", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--color-text-tertiary)" }}>
				<svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
					<polyline points="22 12 16 12 14 15 10 15 8 12 2 12" />
					<path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
				</svg>
			</div>
			<div>
				<div style={{ fontSize: "var(--font-size-lg)", fontWeight: "var(--font-weight-semibold)", color: "var(--color-text-primary)" }}>{title}</div>
				{description && <div style={{ fontSize: "var(--font-size-sm)", color: "var(--color-text-secondary)", marginTop: 6, maxWidth: 360, marginInline: "auto" }}>{description}</div>}
			</div>
			{action && <div style={{ marginTop: 8 }}>{action}</div>}
		</div>
	);
}

