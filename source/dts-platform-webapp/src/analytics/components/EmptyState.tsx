import type { ReactNode } from "react";

type Props = {
	title: ReactNode;
	description?: ReactNode;
	action?: ReactNode;
};

export function EmptyState({ title, description, action }: Props) {
	return (
		<div className="flex flex-col items-center justify-center gap-3 text-center py-12 px-4">
			<div className="w-20 h-20 rounded-full bg-surface-muted flex items-center justify-center text-text-muted">
				<svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
					<polyline points="22 12 16 12 14 15 10 15 8 12 2 12" />
					<path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
				</svg>
			</div>
			<div>
				<div className="text-lg font-semibold text-text-primary">{title}</div>
				{description && <div className="text-sm text-text-secondary mt-1.5 max-w-[360px] mx-auto">{description}</div>}
			</div>
			{action && <div className="mt-2">{action}</div>}
		</div>
	);
}
