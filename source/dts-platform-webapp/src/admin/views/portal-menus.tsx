import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { PortalMenuItem } from "@/admin/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Textarea } from "@/ui/textarea";
import { Button } from "@/ui/button";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

export default function PortalMenusView() {
	const queryClient = useQueryClient();
	const { data = [] } = useQuery({
		queryKey: ["admin", "portal-menus"],
		queryFn: adminApi.getPortalMenus,
	});

	const [draft, setDraft] = useState<PortalMenuItem>({ name: "", path: "" });
	const [targetId, setTargetId] = useState<string>("");

	const refresh = () => queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });

	const handleCreate = async () => {
		if (!draft.name || !draft.path) {
			toast.error("请填写菜单名称和路径");
			return;
		}
		try {
			await adminApi.draftCreateMenu(draft);
			toast.success("新增菜单已提交审批");
			setDraft({ name: "", path: "" });
			refresh();
		} catch (error) {
			toast.error("提交失败，请稍后重试");
		}
	};

	const handleUpdate = async () => {
		const id = Number(targetId);
		if (!id) {
			toast.error("请指定要更新的菜单ID");
			return;
		}
		try {
			await adminApi.draftUpdateMenu(id, draft);
			toast.success("更新请求已提交");
			refresh();
		} catch (error) {
			toast.error("提交失败");
		}
	};

	const handleDelete = async () => {
		const id = Number(targetId);
		if (!id) {
			toast.error("请指定要删除的菜单ID");
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
			<Card>
				<CardHeader>
					<CardTitle>现有菜单树</CardTitle>
				</CardHeader>
				<CardContent>
					{data.length === 0 ? <Text variant="body3">暂无菜单数据。</Text> : <MenuTree items={data} />}
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>提交菜单变更</CardTitle>
				</CardHeader>
				<CardContent className="grid gap-4 md:grid-cols-2">
					<Input
						placeholder="菜单名称"
						value={draft.name}
						onChange={(event) => setDraft((prev) => ({ ...prev, name: event.target.value }))}
					/>
					<Input
						placeholder="菜单路径"
						value={draft.path}
						onChange={(event) => setDraft((prev) => ({ ...prev, path: event.target.value }))}
					/>
					<Input
						placeholder="前端组件"
						value={draft.component || ""}
						onChange={(event) => setDraft((prev) => ({ ...prev, component: event.target.value }))}
					/>
					<Input
						type="number"
						placeholder="排序 (可选)"
						value={draft.sortOrder?.toString() || ""}
						onChange={(event) => setDraft((prev) => ({ ...prev, sortOrder: Number(event.target.value) || undefined }))}
					/>
					<Input
						placeholder="父级ID (可选)"
						value={draft.parentId?.toString() || ""}
						onChange={(event) =>
							setDraft((prev) => ({ ...prev, parentId: event.target.value ? Number(event.target.value) : undefined }))
						}
					/>
					<Textarea
						placeholder="元数据 JSON"
						value={draft.metadata || ""}
						onChange={(event) => setDraft((prev) => ({ ...prev, metadata: event.target.value }))}
						rows={3}
					/>
					<Input
						placeholder="目标菜单ID（用于更新/删除）"
						value={targetId}
						onChange={(event) => setTargetId(event.target.value)}
					/>
					<div className="flex flex-col gap-3">
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

function MenuTree({ items }: { items: PortalMenuItem[] }) {
	return (
		<ul className="space-y-2">
			{items.map((item) => (
				<li key={item.id} className="rounded-md border p-3">
					<div className="flex items-center justify-between gap-2 text-sm">
						<span className="font-semibold">{item.name}</span>
						<span className="text-muted-foreground">ID: {item.id}</span>
					</div>
					<Text variant="body3" className="text-muted-foreground">
						路径：{item.path} · 组件：{item.component || "--"}
					</Text>
					{item.children && item.children.length > 0 ? (
						<div className="mt-2 border-l pl-4">
							<MenuTree items={item.children} />
						</div>
					) : null}
				</li>
			))}
		</ul>
	);
}
