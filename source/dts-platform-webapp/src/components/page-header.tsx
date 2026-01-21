import type { ReactNode } from "react";
import { cn } from "@/utils";

type PageHeaderProps = {
	title: string;
	description?: string;
	actions?: ReactNode;
	meta?: ReactNode;
	className?: string;
};

export function PageHeader({ title, description, actions, meta, className }: PageHeaderProps) {
	return (
		<div className={cn("flex flex-col gap-3", className)}>
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div className="space-y-1">
					<h1 className="text-xl font-semibold text-text-primary">{title}</h1>
					{description ? <p className="text-sm text-text-secondary">{description}</p> : null}
				</div>
				{actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
			</div>
			{meta ? <div className="flex flex-wrap gap-2 text-xs text-text-tertiary">{meta}</div> : null}
		</div>
	);
}
