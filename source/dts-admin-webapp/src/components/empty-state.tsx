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
				"flex flex-col items-center justify-center gap-3 text-center",
				compact ? "py-8" : "py-16",
				className,
			)}
		>
			<div
				className={cn(
					"flex items-center justify-center rounded-full bg-muted",
					compact ? "h-14 w-14" : "h-20 w-20",
				)}
			>
				<Icon icon={icon || "lucide:inbox"} size={compact ? 28 : 40} className="text-muted-foreground/50" />
			</div>
			<div className="space-y-1.5">
				<div className="text-base font-semibold">{title}</div>
				{description && <div className="max-w-sm text-sm text-muted-foreground">{description}</div>}
			</div>
			{actions && <div className="flex flex-wrap justify-center gap-2 pt-3">{actions}</div>}
		</div>
	);
}
