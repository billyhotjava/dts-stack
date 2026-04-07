import type { ReactNode } from "react";
import { cn } from "@/utils";

type PageHeaderProps = {
	title: string;
	actions?: ReactNode;
	className?: string;
};

export function PageHeader({ title, actions, className }: PageHeaderProps) {
	return (
		<div className={cn("flex flex-wrap items-center justify-between gap-3", className)}>
			<h1 className="text-xl font-semibold text-text-primary">{title}</h1>
			{actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
		</div>
	);
}
