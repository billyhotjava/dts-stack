import type { ReactNode } from "react";

import { cn } from "@/utils";
import { Text, Title } from "@/ui/typography";

type DetailSectionProps = {
	title?: string;
	description?: string;
	columns?: 1 | 2 | 3;
	children: ReactNode;
};

export function DetailSection({ title, description, columns = 2, children }: DetailSectionProps) {
	const gridClass =
		columns === 1
			? "grid grid-cols-1 gap-4"
			: columns === 3
				? "grid grid-cols-1 gap-4 md:grid-cols-3"
				: "grid grid-cols-1 gap-4 md:grid-cols-2";

	return (
		<section className="space-y-3 rounded-xl border border-border bg-background/90 p-4 shadow-sm">
			{(title || description) && (
				<header className="space-y-1">
					{title && (
						<Title as="h4" className="text-base font-semibold">
							{title}
						</Title>
					)}
					{description && <p className="text-xs text-muted-foreground">{description}</p>}
				</header>
			)}
			<div className={gridClass}>{children}</div>
		</section>
	);
}

type DetailItemProps = {
	label: ReactNode;
	value: ReactNode;
	monospace?: boolean;
	full?: boolean;
};

export function DetailItem({ label, value, monospace, full = false }: DetailItemProps) {
	return (
		<div className={cn("space-y-1", full && "md:col-span-full")}>
			<Text variant="body2" className="text-[12px] font-medium uppercase tracking-wide text-muted-foreground">
				{label}
			</Text>
			<div className={cn("text-sm text-foreground", monospace && "font-mono break-all")}>{value ?? "-"}</div>
		</div>
	);
}
