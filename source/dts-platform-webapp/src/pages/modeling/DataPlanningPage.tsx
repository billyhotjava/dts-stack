import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	createModelingPlan,
	getModelingPlan,
	deleteModelingPlan,
	listModelingPlans,
	updateModelingPlan,
} from "@/api/platformApi";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { formatDateTime } from "@/utils/format";

type PlanRow = {
	id: string;
	name: string;
	domain?: string | null;
	status?: string | null;
	version?: string | null;
	tags?: string | null;
	content?: string | null;
	owner?: string | null;
	ownerDept?: string | null;
	lastModifiedDate?: string | null;
};

type PlanForm = {
	id?: string;
	name: string;
	domain: string;
	status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
	version: string;
	owner: string;
	tags: string;
	content: string;
};

const DEFAULT_FORM: PlanForm = {
	name: "",
	domain: "",
	status: "DRAFT",
	version: "",
	owner: "",
	tags: "",
	content: "",
};

export default function DataPlanningPage() {
	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [items, setItems] = useState<PlanRow[]>([]);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [form, setForm] = useState<PlanForm>(DEFAULT_FORM);

	const fetchList = useCallback(async () => {
		setLoading(true);
		try {
			const list: any[] = await listModelingPlans({ keyword: keyword.trim() || undefined });
			setItems(
				(Array.isArray(list) ? list : []).map((p: any) => ({
					id: String(p?.id ?? ""),
					name: String(p?.name ?? ""),
					domain: p?.domain ?? null,
					status: p?.status ?? null,
					version: p?.version ?? null,
					tags: p?.tags ?? null,
					content: p?.content ?? null,
					owner: p?.owner ?? null,
					ownerDept: p?.ownerDept ?? p?.owner_dept ?? null,
					lastModifiedDate: p?.lastModifiedDate ?? p?.last_modified_date ?? null,
				})),
			);
		} catch (e: any) {
			toast.error(e?.message || "加载数据规划失败");
		} finally {
			setLoading(false);
		}
	}, [keyword]);

	useEffect(() => {
		void fetchList();
	}, [fetchList]);

	const filtered = useMemo(() => {
		const k = keyword.trim().toLowerCase();
		if (!k) return items;
		return items.filter(
			(it) =>
				(it.name || "").toLowerCase().includes(k) ||
				String(it.domain || "").toLowerCase().includes(k) ||
				String(it.owner || "").toLowerCase().includes(k),
		);
	}, [items, keyword]);

	const openCreate = () => {
		setForm(DEFAULT_FORM);
		setDialogOpen(true);
	};

	const openEdit = useCallback(async (row: PlanRow) => {
		try {
			const full: any = await getModelingPlan(row.id);
			setForm({
				id: String(full?.id ?? row.id),
				name: String(full?.name ?? row.name ?? ""),
				domain: String(full?.domain ?? row.domain ?? ""),
				status: String(full?.status ?? row.status ?? "DRAFT").toUpperCase() === "PUBLISHED"
					? "PUBLISHED"
					: String(full?.status ?? row.status ?? "DRAFT").toUpperCase() === "ARCHIVED"
						? "ARCHIVED"
						: "DRAFT",
				version: String(full?.version ?? row.version ?? ""),
				owner: String(full?.owner ?? row.owner ?? ""),
				tags: String(full?.tags ?? row.tags ?? ""),
				content: String(full?.content ?? row.content ?? ""),
			});
			setDialogOpen(true);
		} catch (e: any) {
			toast.error(e?.message || "加载详情失败");
		}
	}, []);

	const save = useCallback(async () => {
		if (!form.name.trim()) {
			toast.error("请输入名称");
			return;
		}
		setSaving(true);
		try {
			const payload: any = {
				name: form.name.trim(),
				domain: form.domain.trim() || null,
				status: form.status,
				version: form.version.trim() || null,
				owner: form.owner.trim() || null,
				tags: form.tags.trim() || null,
				content: form.content.trim() || null,
			};
			if (form.id) {
				await updateModelingPlan(form.id, payload);
			} else {
				await createModelingPlan(payload);
			}
			toast.success("保存成功");
			setDialogOpen(false);
			await fetchList();
		} catch (e: any) {
			toast.error(e?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	}, [form, fetchList]);

	const remove = useCallback(
		async (row: PlanRow) => {
			if (!row?.id) return;
			if (!window.confirm("确认删除该规划？")) return;
			try {
				await deleteModelingPlan(row.id);
				toast.success("已删除");
				await fetchList();
			} catch (e: any) {
				toast.error(e?.message || "删除失败");
			}
		},
		[fetchList],
	);

	const statusBadge = (status?: string | null) => {
		const s = String(status || "").toUpperCase();
		if (s === "PUBLISHED") return <Badge variant="secondary">已发布</Badge>;
		if (s === "ARCHIVED") return <Badge variant="outline">已归档</Badge>;
		return <Badge variant="outline">草稿</Badge>;
	};

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-row items-center justify-between space-y-0">
					<CardTitle>数据规划管理</CardTitle>
					<Button size="sm" onClick={openCreate}>
						新增
					</Button>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="flex flex-col gap-2 md:flex-row md:items-end">
						<div className="flex-1 space-y-2">
							<Label>检索</Label>
							<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="按名称/主题域/负责人搜索" />
						</div>
						<Button variant="secondary" disabled={loading} onClick={() => void fetchList()}>
							刷新
						</Button>
					</div>
					<div className="overflow-auto rounded border">
						<table className="w-full text-sm">
							<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
								<tr>
									<th className="px-3 py-2">名称</th>
									<th className="px-3 py-2">主题域</th>
									<th className="px-3 py-2">状态</th>
									<th className="px-3 py-2">版本</th>
									<th className="px-3 py-2">负责人</th>
									<th className="px-3 py-2">部门</th>
									<th className="px-3 py-2">更新时间</th>
									<th className="px-3 py-2 w-[180px]">操作</th>
								</tr>
							</thead>
							<tbody>
								{filtered.map((row) => (
									<tr key={row.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs font-medium">{row.name}</td>
										<td className="px-3 py-2 text-xs">{row.domain || "-"}</td>
										<td className="px-3 py-2 text-xs">{statusBadge(row.status)}</td>
										<td className="px-3 py-2 text-xs">{row.version || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.owner || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.ownerDept || "-"}</td>
										<td className="px-3 py-2 text-xs">{formatDateTime(row.lastModifiedDate)}</td>
										<td className="px-3 py-2">
											<div className="flex gap-2">
												<Button size="sm" variant="secondary" onClick={() => void openEdit(row)}>
													编辑
												</Button>
												<Button size="sm" variant="destructive" onClick={() => void remove(row)}>
													删除
												</Button>
											</div>
										</td>
									</tr>
								))}
								{filtered.length === 0 && (
									<tr>
										<td colSpan={8} className="px-3 py-10 text-center text-muted-foreground">
											{loading ? "加载中..." : "暂无数据"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</CardContent>
			</Card>

			<Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
				<DialogContent className="sm:max-w-[720px]">
					<DialogHeader>
						<DialogTitle>{form.id ? "编辑数据规划" : "新增数据规划"}</DialogTitle>
					</DialogHeader>
					<div className="grid grid-cols-1 gap-3 md:grid-cols-2">
						<div className="space-y-2 md:col-span-2">
							<Label>名称 *</Label>
							<Input value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} />
						</div>
						<div className="space-y-2">
							<Label>主题域</Label>
							<Input value={form.domain} onChange={(e) => setForm((p) => ({ ...p, domain: e.target.value }))} placeholder="如：财务 / 项目 / 库存" />
						</div>
						<div className="space-y-2">
							<Label>状态</Label>
							<Select value={form.status} onValueChange={(v: any) => setForm((p) => ({ ...p, status: v }))}>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="DRAFT">草稿</SelectItem>
									<SelectItem value="PUBLISHED">已发布</SelectItem>
									<SelectItem value="ARCHIVED">已归档</SelectItem>
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>版本</Label>
							<Input value={form.version} onChange={(e) => setForm((p) => ({ ...p, version: e.target.value }))} placeholder="如：v1" />
						</div>
						<div className="space-y-2">
							<Label>负责人</Label>
							<Input value={form.owner} onChange={(e) => setForm((p) => ({ ...p, owner: e.target.value }))} />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>标签（逗号分隔）</Label>
							<Input value={form.tags} onChange={(e) => setForm((p) => ({ ...p, tags: e.target.value }))} />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>内容</Label>
							<Textarea
								value={form.content}
								onChange={(e) => setForm((p) => ({ ...p, content: e.target.value }))}
								placeholder="可填写规划背景、业务域拆分、产出物模板链接等"
								className="min-h-[160px]"
							/>
						</div>
					</div>
					<DialogFooter>
						<Button variant="secondary" onClick={() => setDialogOpen(false)}>
							取消
						</Button>
						<Button onClick={() => void save()} disabled={saving}>
							保存
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}
