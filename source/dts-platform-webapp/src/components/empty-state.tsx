import type { ReactNode } from "react";
import { Icon } from "@/components/icon";
import { cn } from "@/utils";

type EmptyStateProps = {
	title: string;
	description?: string;
	icon?: string;
	actions?: ReactNode;
	compact?: boolean;
	className?: string;
};

export function EmptyState({ title, description, icon, actions, compact, className }: EmptyStateProps) {
	return (
		<div
			className={cn(
				"flex flex-col items-center justify-center gap-2 text-center text-sm text-muted-foreground",
				compact ? "py-6" : "py-12",
				className,
			)}
		>
			<Icon icon={icon || "solar:box-minimalistic-bold-duotone"} size={compact ? 36 : 52} className="text-muted-foreground/70" />
			<div className="text-sm font-medium text-text-secondary">{title}</div>
			{description ? <div className="text-xs text-text-tertiary">{description}</div> : null}
			{actions ? <div className="flex flex-wrap justify-center gap-2 pt-1">{actions}</div> : null}
		</div>
	);
}
