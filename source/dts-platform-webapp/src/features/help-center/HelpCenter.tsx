import { ArrowRight, BookOpen, CircleHelp } from "lucide-react";
import { useState } from "react";
import { Link, useLocation } from "react-router";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { ScrollArea } from "@/ui/scroll-area";
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle, SheetTrigger } from "@/ui/sheet";
import { buildHelpTopicHref, getRelatedHelpTopics, type HelpTopic, resolveHelpTopic } from "./helpTopics";

type HelpTopicContentProps = {
	topic: HelpTopic;
	compact?: boolean;
};

export function HelpTopicContent({ topic, compact = false }: HelpTopicContentProps) {
	const relatedTopics = getRelatedHelpTopics(topic);

	return (
		<div className="space-y-6">
			<div className="space-y-2">
				<Badge variant="info">{topic.section}</Badge>
				<p className="text-sm leading-6 text-text-secondary">{topic.summary}</p>
			</div>

			{topic.prerequisites.length > 0 ? (
				<section aria-labelledby={`${topic.id}-prerequisites`} className="space-y-2">
					<h3 id={`${topic.id}-prerequisites`} className="text-sm font-semibold text-text-primary">
						开始前准备
					</h3>
					<ul className="space-y-2 text-sm leading-6 text-text-secondary">
						{topic.prerequisites.map((item) => (
							<li key={item} className="flex gap-2">
								<span aria-hidden="true" className="mt-[9px] h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />
								<span>{item}</span>
							</li>
						))}
					</ul>
				</section>
			) : null}

			<section aria-labelledby={`${topic.id}-steps`} className="space-y-3">
				<h3 id={`${topic.id}-steps`} className="text-sm font-semibold text-text-primary">
					操作步骤
				</h3>
				<ol className="space-y-3">
					{topic.steps.map((step, index) => (
						<li key={step} className="flex gap-3 text-sm leading-6 text-text-secondary">
							<span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary">
								{index + 1}
							</span>
							<span>{step}</span>
						</li>
					))}
				</ol>
			</section>

			{topic.blockers.length > 0 ? (
				<section aria-labelledby={`${topic.id}-blockers`} className="space-y-3">
					<h3 id={`${topic.id}-blockers`} className="text-sm font-semibold text-text-primary">
						常见阻塞
					</h3>
					<div className="space-y-2">
						{topic.blockers.map((blocker) => (
							<div key={blocker.problem} className="rounded-xl border border-border/70 bg-card p-3">
								<div className="text-sm font-medium text-text-primary">{blocker.problem}</div>
								<p className="mt-1 text-sm leading-6 text-text-secondary">{blocker.action}</p>
							</div>
						))}
					</div>
				</section>
			) : null}

			{!compact && relatedTopics.length > 0 ? (
				<section aria-labelledby={`${topic.id}-related`} className="space-y-3">
					<h3 id={`${topic.id}-related`} className="text-sm font-semibold text-text-primary">
						相关主题
					</h3>
					<div className="grid gap-2 sm:grid-cols-2">
						{relatedTopics.map((relatedTopic) => (
							<Link
								key={relatedTopic.id}
								to={buildHelpTopicHref(relatedTopic.id)}
								className="flex items-center justify-between rounded-xl border border-border/70 bg-card px-4 py-3 text-sm font-medium text-text-primary transition-colors hover:bg-accent"
							>
								<span>{relatedTopic.title}</span>
								<ArrowRight className="h-4 w-4 text-text-secondary" />
							</Link>
						))}
					</div>
				</section>
			) : null}
		</div>
	);
}

export default function HelpCenter() {
	const location = useLocation();
	const [open, setOpen] = useState(false);
	const requestedTopicId =
		location.pathname === "/settings/help" ? new URLSearchParams(location.search).get("topic") : undefined;
	const topic = resolveHelpTopic(location.pathname, requestedTopicId, location.search);

	return (
		<Sheet open={open} onOpenChange={setOpen}>
			<SheetTrigger asChild>
				<Button
					variant="ghost"
					size="icon"
					aria-label="打开帮助"
					title="帮助"
					className="h-10 w-10 rounded-2xl border border-border/70 bg-background shadow-[0_10px_24px_rgba(15,23,42,0.06)] hover:bg-accent"
				>
					<CircleHelp aria-hidden="true" className="h-5 w-5" />
				</Button>
			</SheetTrigger>
			<SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-[480px]">
				<SheetHeader className="border-b border-border/70 px-5 py-5 pr-12">
					<div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.14em] text-text-secondary">
						<BookOpen aria-hidden="true" className="h-4 w-4" />
						当前页面帮助
					</div>
					<SheetTitle className="text-xl">{topic.title}</SheetTitle>
					<SheetDescription>内容会根据当前页面自动切换。</SheetDescription>
				</SheetHeader>
				<ScrollArea className="min-h-0 flex-1">
					<div className="px-5 py-5">
						<HelpTopicContent topic={topic} compact />
					</div>
				</ScrollArea>
				<SheetFooter className="border-t border-border/70 px-5 py-4">
					<Button asChild className="w-full" onClick={() => setOpen(false)}>
						<Link to={buildHelpTopicHref(topic.id)}>
							打开完整帮助中心
							<ArrowRight aria-hidden="true" className="h-4 w-4" />
						</Link>
					</Button>
				</SheetFooter>
			</SheetContent>
		</Sheet>
	);
}
