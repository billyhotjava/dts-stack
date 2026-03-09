import type { ReactNode } from "react";
import { Badge } from "@/ui/badge";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { cn } from "@/utils";

type HeroTone = "default" | "info" | "success" | "warning";

type PlatformPageHeroProps = {
	title: string;
	description?: string;
	eyebrow?: string;
	actions?: ReactNode;
	meta?: ReactNode;
	className?: string;
	tone?: HeroTone;
};

type SummaryCardItem = {
	label: string;
	value: ReactNode;
	note?: ReactNode;
	icon?: ReactNode;
	tone?: HeroTone;
};

type SummaryCardsProps = {
	items: SummaryCardItem[];
	className?: string;
};

type SectionCardProps = {
	title: string;
	description?: string;
	action?: ReactNode;
	children: ReactNode;
	className?: string;
	bodyClassName?: string;
};

type FilterBarProps = {
	children: ReactNode;
	className?: string;
};

const TONES: Record<HeroTone, string> = {
	default: "bg-primary/10 text-primary-dark",
	info: "bg-info/15 text-info-dark",
	success: "bg-success/15 text-success-dark",
	warning: "bg-warning/15 text-warning-dark",
};

export function PlatformPageHero({
	title,
	description,
	eyebrow = "Platform Console",
	actions,
	meta,
	className,
	tone = "default",
}: PlatformPageHeroProps) {
	return (
		<div className={cn("rounded-[30px] border border-border/70 bg-card px-6 py-6 shadow-sm md:px-7", className)}>
			<div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
				<div className="space-y-3">
					<Badge variant="outline" className={cn("rounded-full border-0 px-3 py-1 text-xs", TONES[tone])}>
						{eyebrow}
					</Badge>
					<div className="space-y-1.5">
						<h1 className="text-2xl font-semibold tracking-tight text-foreground">{title}</h1>
						{description ? <p className="max-w-3xl text-sm leading-6 text-muted-foreground">{description}</p> : null}
					</div>
				</div>
				{actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
			</div>
			{meta ? <div className="mt-4 flex flex-wrap items-center gap-2">{meta}</div> : null}
		</div>
	);
}

export function PlatformMetaPill({ children, className }: { children: ReactNode; className?: string }) {
	return (
		<div
			className={cn(
				"inline-flex items-center gap-2 rounded-full border border-border/70 bg-background/80 px-3 py-1 text-xs text-muted-foreground",
				className,
			)}
		>
			{children}
		</div>
	);
}

export function PlatformSummaryCards({ items, className }: SummaryCardsProps) {
	return (
		<div className={cn("grid gap-4 md:grid-cols-2 xl:grid-cols-4", className)}>
			{items.map((item) => (
				<Card key={item.label} className="rounded-[28px] border-border/70 bg-card/95 shadow-sm">
					<CardContent className="flex items-start justify-between gap-4 py-6">
						<div className="space-y-2">
							<div className="text-sm text-muted-foreground">{item.label}</div>
							<div className="text-3xl font-semibold tracking-tight text-foreground">{item.value}</div>
							{item.note ? <div className="text-sm text-muted-foreground">{item.note}</div> : null}
						</div>
						{item.icon ? (
							<div className={cn("flex h-11 w-11 items-center justify-center rounded-2xl", TONES[item.tone || "default"])}>
								{item.icon}
							</div>
						) : null}
					</CardContent>
				</Card>
			))}
		</div>
	);
}

export function PlatformSectionCard({
	title,
	description,
	action,
	children,
	className,
	bodyClassName,
}: SectionCardProps) {
	return (
		<Card className={cn("rounded-[28px] border-border/70 bg-card/95 shadow-sm", className)}>
			<CardHeader className="gap-3 border-b border-border/70 pb-5">
				<div>
					<CardTitle className="text-base font-semibold tracking-tight">{title}</CardTitle>
					{description ? <CardDescription className="mt-1 text-sm leading-6">{description}</CardDescription> : null}
				</div>
				{action ? <CardAction>{action}</CardAction> : null}
			</CardHeader>
			<CardContent className={cn("pt-6", bodyClassName)}>{children}</CardContent>
		</Card>
	);
}

export function PlatformFilterBar({ children, className }: FilterBarProps) {
	return (
		<div className={cn("rounded-[24px] border border-border/70 bg-card/90 p-4 shadow-sm", className)}>
			<div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">{children}</div>
		</div>
	);
}
