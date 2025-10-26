import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { PortalMenuCollection, PortalMenuItem } from "@/admin/types";
import { setPortalMenus } from "@/store/portalMenuStore";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { Input } from "@/ui/input";
import { toast } from "sonner";

export default function PortalMenusView() {
	const queryClient = useQueryClient();
	const { data, isLoading } = useQuery({
		queryKey: ["admin", "portal-menus"],
		queryFn: adminApi.getPortalMenus,
	});

	const treeMenus = useMemo(() => data?.allMenus ?? data?.menus ?? [], [data?.allMenus, data?.menus]);
	const activeMenus = useMemo(() => data?.menus ?? [], [data?.menus]);

	const [pending, setPending] = useState<Record<number, boolean>>({});
	const [resetting, setResetting] = useState(false);
	const [expanded, setExpanded] = useState<Set<number>>(new Set());
	const [keyword, setKeyword] = useState("");

	const filteredTreeMenus = useMemo(() => {
		const trimmed = keyword.trim();
		if (!trimmed) return treeMenus;
		return filterMenusByKeyword(treeMenus, trimmed);
	}, [keyword, treeMenus]);

	const menuStats = useMemo(() => {
		let total = 0;
		let disabled = 0;
		const walk = (items: PortalMenuItem[] = []) => {
			items.forEach((item) => {
				if (!item) return;
				const children = Array.isArray(item.children) ? item.children : [];
				if (children.length) {
					walk(children);
				} else {
					total += 1;
					if (item.deleted) disabled += 1;
				}
			});
		};
		walk(treeMenus);
		const active = total - disabled;
		return { total, active, disabled };
	}, [treeMenus]);

	useEffect(() => {
		if (!isLoading) {
			setPortalMenus(activeMenus, treeMenus);
		}
	}, [activeMenus, treeMenus, isLoading]);

	// 默认展开第一层
	useEffect(() => {
		const next = new Set<number>();
		const roots = Array.isArray(treeMenus) ? treeMenus : [];
		for (const item of roots) {
			if (item?.id != null && item.children && item.children.length > 0) {
				next.add(item.id);
			}
		}
		setExpanded(next);
	}, [treeMenus]);

	useEffect(() => {
		if (!keyword.trim()) return;
		setExpanded(collectFolderIds(filteredTreeMenus));
	}, [keyword, filteredTreeMenus]);

	const refresh = () => queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });

	const updateCache = (next: PortalMenuCollection) => {
		queryClient.setQueryData(["admin", "portal-menus"], next);
	};

	const markPending = (id: number, value: boolean) => {
		setPending((prev) => {
			const next = { ...prev };
			if (value) {
				next[id] = true;
			} else {
				delete next[id];
			}
			return next;
		});
	};

	const handleToggle = async (id: number, currentlyDeleted: boolean) => {
		markPending(id, true);
		try {
			if (currentlyDeleted) {
				const result = await adminApi.updatePortalMenu(id, { deleted: false } as unknown as PortalMenuItem);
				// When审批开启，后端返回的是变更请求而非菜单集合；此时不要污染缓存，直接刷新
				if (result && typeof result === "object" && (result as any).menus) {
					updateCache(result as any);
				} else {
					await refresh();
				}
				// 审批模式下，启用动作也会生成“变更申请”
				toast.success("菜单已提交启用申请，等待授权管理员审批");
			} else {
				const result = await adminApi.deletePortalMenu(id);
				if (result && typeof result === "object" && (result as any).menus) {
					updateCache(result as any);
				} else {
					await refresh();
				}
				// 当启用审批流时，禁用动作会生成“变更申请”而非立即生效
				toast.success("菜单已提交禁用申请，等待授权管理员审批");
			}
		} catch (error: any) {
			toast.error(error?.message || "操作失败，请稍后重试");
			await refresh();
		} finally {
			markPending(id, false);
		}
	};

	const handleReset = async () => {
		setResetting(true);
		try {
			const result = await adminApi.resetPortalMenus();
			updateCache(result);
			toast.success("默认菜单已恢复");
		} catch (error: any) {
			toast.error(error?.message || "恢复失败，请稍后再试");
			await refresh();
		} finally {
			setResetting(false);
		}
	};

	return (
		<div className="space-y-6">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<Text variant="body1" className="text-lg font-semibold">
					菜单管理
				</Text>
				<Button variant="secondary" onClick={handleReset} disabled={resetting}>
					{resetting ? "恢复中.." : "恢复默认菜单"}
				</Button>
			</div>

			<div className="grid gap-3 sm:grid-cols-3">
				<Card>
					<CardContent className="flex flex-col gap-1 px-4 py-3">
						<Text variant="body3" className="text-muted-foreground">
							菜单总数
						</Text>
						<span className="text-2xl font-semibold">{menuStats.total}</span>
					</CardContent>
				</Card>
				<Card>
					<CardContent className="flex flex-col gap-1 px-4 py-3">
						<Text variant="body3" className="text-muted-foreground">
							已启用
						</Text>
						<span className="text-2xl font-semibold text-emerald-600">{menuStats.active}</span>
					</CardContent>
				</Card>
				<Card>
					<CardContent className="flex flex-col gap-1 px-4 py-3">
						<Text variant="body3" className="text-muted-foreground">
							已禁用
						</Text>
						<span className="text-2xl font-semibold text-red-500">{menuStats.disabled}</span>
					</CardContent>
				</Card>
			</div>

			<Card>
				<CardHeader className="pb-2">
					<div className="flex items-center gap-2">
						<CardTitle className="mr-2">菜单编辑器</CardTitle>
						<div className="ml-auto flex items-center gap-2">
							<Input
								value={keyword}
								onChange={(event) => setKeyword(event.target.value)}
								placeholder="按名称搜索菜单"
								className="w-56"
							/>
							{keyword ? (
								<Button size="sm" variant="ghost" onClick={() => setKeyword("")}>
									清除
								</Button>
							) : null}
							<Button size="sm" variant="outline" onClick={() => setExpanded(collectFolderIds(filteredTreeMenus))}>
								全部展开
							</Button>
							<Button size="sm" variant="outline" onClick={() => setExpanded(new Set())}>
								全部折叠
							</Button>
						</div>
					</div>
					<Text variant="body3" className="text-muted-foreground">
						说明：父节点仅用于分组，不提供启用/禁用按钮；叶子节点可切换状态
					</Text>
					<Text variant="body3" color="warning" className="mt-1">
						提示：禁用此项会触发菜单管理审批，请谨慎处理
					</Text>
				</CardHeader>
				<CardContent>
					{isLoading ? (
						<Text variant="body3">加载中..</Text>
					) : filteredTreeMenus.length === 0 ? (
						<Text variant="body3">暂无菜单数据</Text>
					) : (
						<div className="space-y-2">
							{filteredTreeMenus.map((item) => (
								<MenuRow
									key={item.id}
									item={item}
									level={0}
									pathNames={[]}
									expanded={expanded}
									setExpanded={setExpanded}
									pending={pending}
									onToggle={handleToggle}
									keyword={keyword}
								/>
							))}
						</div>
					)}
				</CardContent>
			</Card>
		</div>
	);
}

type MenuRowProps = {
	item: PortalMenuItem;
	level: number;
	pathNames: string[];
	expanded: Set<number>;
	setExpanded: (next: Set<number>) => void;
	pending: Record<number, boolean>;
	onToggle: (id: number, currentlyDeleted: boolean) => void;
	keyword: string;
};

function MenuRow({ item, level, pathNames, expanded, setExpanded, pending, onToggle, keyword }: MenuRowProps) {
	const id = item.id as number | undefined;
	if (id == null) return null;
	const baseName = item.displayName ?? item.name ?? String(id);
	const name = id != null ? `id${id}-${baseName}` : baseName;
	const children = Array.isArray(item.children) ? item.children : [];
	const isFolder = children.length > 0;
	const isExpanded = isFolder && expanded.has(id);
	const busy = pending[id];
	const isDeleted = Boolean(item.deleted);
	const fullPath = [...pathNames, name];

	const toggleExpand = () => {
		if (!isFolder) return;
		const next = new Set(expanded);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		setExpanded(next);
	};

	return (
		<div className="space-y-2">
			<div className="flex items-center gap-2 rounded-xl border bg-background px-3 py-2 hover:bg-accent/5">
				<button
					aria-label="toggle"
					className="h-6 w-6 shrink-0 rounded-md border text-xs"
					onClick={toggleExpand}
					disabled={!isFolder}
					title={isFolder ? "折叠/展开" : "无子节点"}
				>
					{isFolder ? (isExpanded ? "▾" : "▸") : "·"}
				</button>
				<div style={{ marginLeft: level * 14 }} className="-ml-1" />
				<div className="min-w-0 flex-1 truncate font-semibold">{highlightKeyword(name, keyword)}</div>
				<div className="text-xs text-muted-foreground">/{fullPath.join("/")}</div>
				<div className="ml-2 flex items-center gap-2">
					{isFolder ? (
						<Badge variant="outline">目录</Badge>
					) : (
						<>
							<Badge
								variant="outline"
								className={isDeleted ? "border-red-200 text-red-600" : "border-emerald-200 text-emerald-600"}
							>
								{isDeleted ? "已禁用" : "已启用"}
							</Badge>
							<Button size="sm" variant="outline" onClick={() => onToggle(id, isDeleted)} disabled={busy || !isDeleted}>
								启用
							</Button>
							<Button size="sm" variant="outline" onClick={() => onToggle(id, isDeleted)} disabled={busy || isDeleted}>
								禁用
							</Button>
						</>
					)}
				</div>
			</div>

			{isFolder && isExpanded && (
				<div className="space-y-2 pl-6">
					{children.map((c) => (
						<MenuRow
							key={c.id}
							item={c}
							level={level + 1}
							pathNames={[...pathNames, name]}
							expanded={expanded}
							setExpanded={setExpanded}
							pending={pending}
							onToggle={onToggle}
							keyword={keyword}
						/>
					))}
				</div>
			)}
		</div>
	);
}

function highlightKeyword(text: string, keyword: string) {
	if (!keyword) return text;
	const lower = text.toLowerCase();
	const target = keyword.toLowerCase();
	const index = lower.indexOf(target);
	if (index === -1) return text;
	const before = text.slice(0, index);
	const match = text.slice(index, index + keyword.length);
	const after = text.slice(index + keyword.length);
	return (
		<span>
			{before}
			<span className="text-primary font-semibold">{match}</span>
			{after}
		</span>
	);
}

function filterMenusByKeyword(items: PortalMenuItem[], keyword: string): PortalMenuItem[] {
	if (!keyword) return items;
	const lower = keyword.toLowerCase();
	const walk = (nodes: PortalMenuItem[] = [], trail: string[] = []): PortalMenuItem[] => {
		const result: PortalMenuItem[] = [];
		nodes.forEach((node) => {
			if (!node) return;
			const label = (node.displayName || node.name || "").toString();
			const idName = `id${node.id ?? ""}-${label}`;
			const children = Array.isArray(node.children) ? node.children : [];
			const filteredChildren = walk(children, [...trail, label]);
			const match = label.toLowerCase().includes(lower) || idName.toLowerCase().includes(lower);
			if (match || filteredChildren.length > 0) {
				result.push({ ...node, children: filteredChildren });
			}
		});
		return result;
	};
	return walk(items);
}

function collectFolderIds(items: PortalMenuItem[]): Set<number> {
	const out = new Set<number>();
	const walk = (list: PortalMenuItem[]) => {
		for (const it of list) {
			if (it?.id == null) continue;
			if (it.children && it.children.length > 0) {
				out.add(it.id as number);
				walk(it.children);
			}
		}
	};
	walk(items ?? []);
	return out;
}
