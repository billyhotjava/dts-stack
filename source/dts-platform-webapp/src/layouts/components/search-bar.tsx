import { useCallback, useEffect, useMemo, useState } from "react";
import { useBoolean } from "react-use";
import { HELP_TOPICS, buildHelpTopicHref } from "@/features/help-center/helpTopics";
import useLocale from "@/locales/use-locale";
import { useRouter } from "@/routes/hooks";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { CommandDialog, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandSeparator } from "@/ui/command";
import { ScrollArea } from "@/ui/scroll-area";
import { Text } from "@/ui/typography";
import { cn } from "@/utils";
import { useFilteredNavData } from "../dashboard/nav";

interface SearchItem {
	key: string;
	label: string;
	path: string;
}

const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const HighlightText = ({ text, query }: { text: string; query: string }) => {
	if (!query) return <>{text}</>;

	const parts = text.split(new RegExp(`(${escapeRegExp(query)})`, "gi"));

	return (
		<>
			{parts.map((part, i) =>
				part.toLowerCase() === query.toLowerCase() ? (
					// biome-ignore lint/suspicious/noArrayIndexKey: <explanation>
					<span key={i} className="text-primary">
						{part}
					</span>
				) : (
					part
				),
			)}
		</>
	);
};

const SearchBar = () => {
	const { t } = useLocale();
	const { replace } = useRouter();
	const [open, setOpen] = useBoolean(false);
	const [searchQuery, setSearchQuery] = useState("");
	const navData = useFilteredNavData();

	const flattenedItems = useMemo(() => {
		const items: SearchItem[] = [];

		const flattenItems = (navItems: typeof navData) => {
			for (const section of navItems) {
				for (const item of section.items) {
					if (item.path) {
						items.push({
							key: item.path,
							label: item.title,
							path: item.path,
						});
					}
					if (item.children) {
						flattenItems([{ items: item.children }]);
					}
				}
			}
		};

		flattenItems(navData);
		return items;
	}, [navData]);

	useEffect(() => {
		const down = (e: KeyboardEvent) => {
			if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
				e.preventDefault();
				setOpen((open: boolean) => !open);
			}
		};

		document.addEventListener("keydown", down);
		return () => document.removeEventListener("keydown", down);
	}, [setOpen]);

	const handleSelect = useCallback(
		(path: string) => {
			if (path === "/metrics" || path.startsWith("/metrics/")) {
				window.location.assign(path);
				setOpen(false);
				return;
			}
			replace(path);
			setOpen(false);
		},
		[replace, setOpen],
	);

	return (
		<>
			<Button
				variant="ghost"
				className={cn(
					"h-10 rounded-2xl border border-border/70 bg-background/70 px-3 text-text-secondary shadow-[0_10px_24px_rgba(15,23,42,0.06)] hover:bg-accent/70",
				)}
				size="sm"
				aria-label="搜索页面与帮助"
				onClick={() => setOpen(true)}
			>
				<div className="flex items-center justify-center gap-3">
					<span className="hidden text-sm font-medium text-text-secondary xl:inline">搜索页面与帮助</span>
					<kbd className="flex items-center justify-center rounded-full bg-primary px-2 py-1 text-xs font-semibold text-common-white">
						Ctrl / ⌘ K
					</kbd>
				</div>
			</Button>

			<CommandDialog
				open={open}
				onOpenChange={setOpen}
				title="全局搜索"
				description="搜索有权限访问的页面和 DTS 帮助主题"
			>
				<CommandInput placeholder="搜索页面或帮助主题..." value={searchQuery} onValueChange={setSearchQuery} />
				<ScrollArea className="h-[400px]">
					<CommandEmpty>未找到结果</CommandEmpty>
					<CommandGroup heading="页面导航">
						{flattenedItems.map(({ key, label }) => (
							<CommandItem
								key={key}
								value={`${t(label)} ${key}`}
								onSelect={() => handleSelect(key)}
								className="flex flex-col items-start"
							>
								<div className="font-medium">
									<HighlightText text={t(label)} query={searchQuery} />
								</div>
								<div className="text-xs text-muted-foreground">
									<HighlightText text={key} query={searchQuery} />
								</div>
							</CommandItem>
						))}
					</CommandGroup>
					<CommandSeparator />
					<CommandGroup heading="帮助主题">
						{HELP_TOPICS.map((topic) => (
							<CommandItem
								key={`help:${topic.id}`}
								value={[topic.id, topic.title, topic.summary, ...topic.keywords].join(" ")}
								onSelect={() => handleSelect(buildHelpTopicHref(topic.id))}
								className="flex flex-col items-start"
							>
								<div className="font-medium">
									<HighlightText text={topic.title} query={searchQuery} />
								</div>
								<div className="line-clamp-2 text-xs text-muted-foreground">
									<HighlightText text={topic.summary} query={searchQuery} />
								</div>
							</CommandItem>
						))}
					</CommandGroup>
				</ScrollArea>
				<CommandSeparator />
				<div className="flex flex-wrap text-text-primary p-2 justify-end gap-2">
					<div className="flex items-center gap-1">
						<Badge variant="info">↑</Badge>
						<Badge variant="info">↓</Badge>
						<Text variant="caption">切换</Text>
					</div>
					<div className="flex items-center gap-1">
						<Badge variant="info">↵</Badge>
						<Text variant="caption">选择</Text>
					</div>
					<div className="flex items-center gap-1">
						<Badge variant="info">ESC</Badge>
						<Text variant="caption">关闭</Text>
					</div>
				</div>
			</CommandDialog>
		</>
	);
};

export default SearchBar;
