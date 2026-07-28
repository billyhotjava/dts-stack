import { BookOpen, Search } from "lucide-react";
import { useMemo } from "react";
import { useSearchParams } from "react-router";
import { HelpTopicContent } from "./HelpCenter";
import {
	HELP_TOPICS,
	resolveHelpTopic,
} from "./helpTopics";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";

export default function HelpCenterPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const query = searchParams.get("q") || "";
	const activeTopic = resolveHelpTopic("/settings/help", searchParams.get("topic"));
	const normalizedQuery = query.trim().toLocaleLowerCase();
	const visibleTopics = useMemo(() => {
		if (!normalizedQuery) return HELP_TOPICS;
		return HELP_TOPICS.filter((topic) => {
			const searchableText = [
				topic.title,
				topic.section,
				topic.summary,
				...topic.keywords,
			]
				.join(" ")
				.toLocaleLowerCase();
			return searchableText.includes(normalizedQuery);
		});
	}, [normalizedQuery]);

	const selectTopic = (topicId: string) => {
		const next = new URLSearchParams(searchParams);
		next.set("topic", topicId);
		setSearchParams(next);
	};

	const updateQuery = (value: string) => {
		const next = new URLSearchParams(searchParams);
		if (value.trim()) next.set("q", value);
		else next.delete("q");
		setSearchParams(next, { replace: true });
	};

	return (
		<div className="mx-auto w-full max-w-[1280px] space-y-6 p-4 md:p-6" data-testid="help-center-page">
			<header className="rounded-[22px] border border-border/70 bg-card px-5 py-5 md:px-7">
				<div className="flex items-start gap-3">
					<div className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
						<BookOpen aria-hidden="true" className="h-5 w-5" />
					</div>
					<div>
						<h1 className="text-2xl font-semibold text-text-primary">DTS 帮助中心</h1>
						<p className="mt-1 text-sm leading-6 text-text-secondary">
							按业务任务查找操作步骤、前置条件和常见阻塞。
						</p>
					</div>
				</div>
			</header>

			<div className="grid gap-5 lg:grid-cols-[320px_minmax(0,1fr)]">
				<aside className="space-y-3 lg:sticky lg:top-[calc(var(--layout-header-height)+24px)] lg:self-start">
					<div className="relative">
						<Search
							aria-hidden="true"
							className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-secondary"
						/>
						<Input
							value={query}
							onChange={(event) => updateQuery(event.target.value)}
							placeholder="搜索帮助主题"
							aria-label="搜索帮助主题"
							className="h-10 bg-card pl-9"
						/>
					</div>

					<nav aria-label="帮助主题" className="max-h-[calc(100vh-190px)] space-y-1 overflow-y-auto rounded-2xl border border-border/70 bg-card p-2">
						{visibleTopics.map((topic) => {
							const active = topic.id === activeTopic.id;
							return (
								<Button
									key={topic.id}
									type="button"
									variant="ghost"
									onClick={() => selectTopic(topic.id)}
									aria-current={active ? "page" : undefined}
									className={
										active
											? "h-auto w-full justify-start whitespace-normal bg-primary/10 px-3 py-3 text-left text-primary hover:bg-primary/15"
											: "h-auto w-full justify-start whitespace-normal px-3 py-3 text-left text-text-primary"
									}
								>
									<span className="min-w-0">
										<span className="block text-sm font-medium">{topic.title}</span>
										<span className="mt-0.5 block text-xs font-normal text-text-secondary">{topic.section}</span>
									</span>
								</Button>
							);
						})}
						{visibleTopics.length === 0 ? (
							<p className="px-3 py-6 text-center text-sm text-text-secondary">没有匹配的帮助主题</p>
						) : null}
					</nav>
				</aside>

				<Card className="min-w-0">
					<CardHeader className="border-b border-border/70">
						<div className="space-y-2">
							<Badge variant="info">{activeTopic.section}</Badge>
							<CardTitle className="text-xl">{activeTopic.title}</CardTitle>
						</div>
					</CardHeader>
					<CardContent>
						<HelpTopicContent topic={activeTopic} />
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
