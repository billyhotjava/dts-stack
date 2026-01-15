import type { ReactNode } from "react";

type Props = {
	title: ReactNode;
	description?: ReactNode;
	action?: ReactNode;
};

export function EmptyState({ title, description, action }: Props) {
	return (
		<div className="card">
			<div style={{ fontWeight: 600 }}>{title}</div>
			{description ? <div className="muted" style={{ marginTop: 8 }}>{description}</div> : null}
			{action ? <div style={{ marginTop: 12 }}>{action}</div> : null}
		</div>
	);
}

