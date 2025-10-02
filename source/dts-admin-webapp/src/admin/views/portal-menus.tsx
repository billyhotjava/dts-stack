import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { PortalMenuItem } from "@/admin/types";
import { PERSON_SECURITY_LEVELS } from "@/constants/governance";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Textarea } from "@/ui/textarea";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Label } from "@/ui/label";
import { Text } from "@/ui/typography";
import { ScrollArea } from "@/ui/scroll-area";
import { toast } from "sonner";

const INITIAL_MENU_DRAFT: PortalMenuItem = {
	name: "",
	path: "",
	securityLevel: "GENERAL",
};

export default function PortalMenusView() {
	const queryClient = useQueryClient();
	const { data } = useQuery({
		queryKey: ["admin", "portal-menus"],
		queryFn: adminApi.getPortalMenus,
	});

	const activeMenus = data?.active ?? [];
	const deletedMenus = data?.deleted ?? [];
	const securityLabelMap = useMemo(
		() => new Map(PERSON_SECURITY_LEVELS.map((item) => [item.value, item.label])),
		[],
	);

	const [draft, setDraft] = useState<PortalMenuItem>({ ...INITIAL_MENU_DRAFT });
	const [targetId, setTargetId] = useState<string>("");

	const refresh = () => queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });

	const handleCreate = async () => {
		if (!draft.name?.trim() || !draft.path?.trim()) {
			toast.error("请填写菜单名称和路径");
			return;
		}
	const payload: PortalMenuItem = {
		...draft,
		securityLevel: (draft.securityLevel ?? "GENERAL") as PortalMenuItem["securityLevel"],
	};
		try {
			await adminApi.draftCreateMenu(payload);
			toast.success("新增菜单已提交审批");
			setDraft({ ...INITIAL_MENU_DRAFT });
			setTargetId("");
			refresh();
		} catch (error) {
			toast.error("提交失败，请稍后重试");
		}
	};

	const handleUpdate = async () => {
		const id = Number(targetId);
		if (!id) {
			toast.error("请指定要更新的菜单编号");
			return;
		}
	const payload: PortalMenuItem = {
		...draft,
		securityLevel: (draft.securityLevel ?? "GENERAL") as PortalMenuItem["securityLevel"],
	};
		try {
			await adminApi.draftUpdateMenu(id, payload);
			toast.success("更新请求已提交");
			refresh();
		} catch (error) {
			toast.error("提交失败");
		}
	};

	const handleDelete = async () => {
		const id = Number(targetId);
		if (!id) {
			toast.error("请指定要删除的菜单编号");
			return;
		}
		try {
			await adminApi.draftDeleteMenu(id);
			toast.success("删除请求已提交");
			refresh();
		} catch (error) {
			toast.error("提交失败");
		}
	};

	return (
		<div className="space-y-6">
			<div className="grid gap-6 xl:grid-cols-[minmax(0,0.6fr)_minmax(0,0.4fr)]">
				<Card>
					<CardHeader>
						<CardTitle>可用菜单</CardTitle>
					</CardHeader>
					<CardContent>
						{activeMenus.length === 0 ? (
							<Text variant="body3">暂无菜单数据。</Text>
						) : (
							<MenuTree items={activeMenus} securityLabelMap={securityLabelMap} />
						)}
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle>已删除菜单</CardTitle>
					</CardHeader>
					<CardContent>
						<DeletedMenuList items={deletedMenus} securityLabelMap={securityLabelMap} />
					</CardContent>
				</Card>
			</div>

			<Card>
				<CardHeader>
					<CardTitle>提交菜单变更</CardTitle>
				</CardHeader>
				<CardContent className="grid gap-4 md:grid-cols-2">
					<div className="space-y-2">
						<Label htmlFor="menu-name">菜单名称</Label>
						<Input
							id="menu-name"
							placeholder="业务审批"
							value={draft.name}
							onChange={(event) => setDraft((prev) => ({ ...prev, name: event.target.value }))}
						/>
					</div>
					<div className="space-y-2">
						<Label htmlFor="menu-path">菜单路径</Label>
						<Input
							id="menu-path"
							placeholder="/portal/approval"
							value={draft.path}
							onChange={(event) => setDraft((prev) => ({ ...prev, path: event.target.value }))}
						/>
					</div>
					<div className="space-y-2">
						<Label>菜单密级</Label>
			<Select
				value={draft.securityLevel ?? "GENERAL"}
				onValueChange={(value) =>
					setDraft((prev) => ({ ...prev, securityLevel: value as PortalMenuItem["securityLevel"] }))
				}
			>
							<SelectTrigger>
								<SelectValue placeholder="请选择菜单密级" />
							</SelectTrigger>
							<SelectContent>
								{PERSON_SECURITY_LEVELS.map((option) => (
									<SelectItem key={option.value} value={option.value}>
										{option.label}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>
					<div className="space-y-2">
						<Label htmlFor="menu-icon">图标</Label>
						<Input
							id="menu-icon"
							placeholder="solar:book-bold-duotone"
							value={draft.icon || ""}
							onChange={(event) => setDraft((prev) => ({ ...prev, icon: event.target.value || undefined }))}
						/>
					</div>
					<div className="space-y-2">
						<Label htmlFor="menu-component">前端组件</Label>
						<Input
							id="menu-component"
							placeholder="/pages/portal/approval"
							value={draft.component || ""}
							onChange={(event) => setDraft((prev) => ({ ...prev, component: event.target.value || undefined }))}
						/>
					</div>
					<div className="space-y-2">
						<Label htmlFor="menu-sort">排序 (可选)</Label>
						<Input
							id="menu-sort"
							type="number"
							placeholder="1"
							value={draft.sortOrder?.toString() || ""}
							onChange={(event) =>
								setDraft((prev) => ({
									...prev,
									sortOrder: event.target.value ? Number(event.target.value) : undefined,
								}))
							}
						/>
					</div>
					<div className="space-y-2">
						<Label htmlFor="menu-parent">父级编号 (可选)</Label>
						<Input
							id="menu-parent"
							placeholder="例如 12"
							value={draft.parentId?.toString() || ""}
							onChange={(event) =>
								setDraft((prev) => ({
									...prev,
									parentId: event.target.value ? Number(event.target.value) : undefined,
								}))
							}
						/>
					</div>
					<div className="space-y-2 md:col-span-2">
						<Label htmlFor="menu-metadata">元数据 JSON</Label>
						<Textarea
							id="menu-metadata"
							placeholder='{"title":"数据资产"}'
							value={draft.metadata || ""}
							onChange={(event) => setDraft((prev) => ({ ...prev, metadata: event.target.value || undefined }))}
							rows={3}
						/>
					</div>
					<div className="space-y-2 md:col-span-2">
						<Label htmlFor="menu-target">目标菜单编号（用于更新/删除）</Label>
						<Input
							id="menu-target"
							placeholder="请输入菜单编号"
							value={targetId}
							onChange={(event) => setTargetId(event.target.value)}
						/>
					</div>
					<div className="flex flex-col gap-3 md:col-span-2 md:flex-row md:items-end">
						<Button onClick={handleCreate}>新增菜单</Button>
						<Button variant="secondary" onClick={handleUpdate}>
							更新菜单
						</Button>
						<Button variant="destructive" onClick={handleDelete}>
							删除菜单
						</Button>
					</div>
				</CardContent>
			</Card>
		</div>
	);
}

function MenuTree({ items, securityLabelMap }: { items: PortalMenuItem[]; securityLabelMap: Map<string, string> }) {
	return (
		<ul className="space-y-2">
			{items.map((item) => {
				const label = securityLabelMap.get(item.securityLevel ?? "") ?? item.securityLevel ?? "--";
				return (
					<li key={item.id ?? item.path} className="rounded-md border p-3">
						<div className="flex flex-wrap items-start justify-between gap-2 text-sm">
							<div>
								<div className="flex items-center gap-2">
									<span className="font-semibold">{item.displayName ?? item.name}</span>
									<Badge variant="outline">{label}</Badge>
								</div>
								<Text variant="body3" className="text-muted-foreground">
									路径：{item.path} · 图标：{item.icon || "--"} · 组件：{item.component || "--"}
								</Text>
							</div>
							<Text variant="body3" className="text-muted-foreground">
								编号：{item.id ?? "--"}
							</Text>
						</div>
						{item.children && item.children.length > 0 ? (
							<div className="mt-2 border-l pl-4">
								<MenuTree items={item.children} securityLabelMap={securityLabelMap} />
							</div>
						) : null}
					</li>
				);
			})}
		</ul>
	);
}

function DeletedMenuList({ items, securityLabelMap }: { items: PortalMenuItem[]; securityLabelMap: Map<string, string> }) {
	if (items.length === 0) {
		return <Text variant="body3">暂无删除记录。</Text>;
	}
	return (
		<ScrollArea className="max-h-96 pr-2">
			<ul className="space-y-2">
				{items.map((item) => {
					const label = securityLabelMap.get(item.securityLevel ?? "") ?? item.securityLevel ?? "--";
					return (
						<li key={`deleted-${item.id ?? item.path}`} className="rounded-md border border-dashed p-3">
							<div className="flex flex-wrap items-start justify-between gap-2 text-sm">
								<div>
									<div className="flex items-center gap-2">
										<span className="font-medium">{item.displayName ?? item.name}</span>
										<Badge variant="outline">{label}</Badge>
									</div>
									<Text variant="body3" className="text-muted-foreground">
										路径：{item.path}
									</Text>
								</div>
								<Text variant="body3" className="text-muted-foreground">
									编号：{item.id ?? "--"} · 父级编号：{item.parentId ?? "--"}
								</Text>
							</div>
						</li>
					);
				})}
			</ul>
		</ScrollArea>
	);
}
