import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	createQualityTask,
	deleteQualityTask,
	listQualityTasks,
	toggleQualityTask,
	triggerQualityTask,
	updateQualityTask,
} from "@/api/platformApi";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/ui/dropdown-menu";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Switch } from "@/ui/switch";
import { formatDateTime } from "@/utils/format";

type TaskRow = {
	id: string;
	name?: string | null;
	datasetId: string;
	ruleId?: string | null;
	ownerDept?: string | null;
	intervalMinutes?: number | null;
	enabled?: boolean | null;
	lastTriggeredAt?: string | null;
};

type TaskForm = {
	id?: string;
	name: string;
	datasetId: string;
	ruleId: string;
	intervalMinutes: string;
	enabled: boolean;
};

const DEFAULT_FORM: TaskForm = {
	name: "",
	datasetId: "",
	ruleId: "",
	intervalMinutes: "60",
	enabled: true,
};

export default function QualityTasksPage() {
	const [loading, setLoading] = useState(false);
	const [items, setItems] = useState<TaskRow[]>([]);
	const [keyword, setKeyword] = useState("");

	const [dialogOpen, setDialogOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [form, setForm] = useState<TaskForm>(DEFAULT_FORM);

	const fetchList = useCallback(async () => {
		setLoading(true);
		try {
			const list: any[] = await listQualityTasks();
			setItems(
				(Array.isArray(list) ? list : []).map((t: any) => ({
					id: String(t?.id ?? ""),
					name: t?.name ?? null,
					datasetId: String(t?.datasetId ?? t?.dataset_id ?? ""),
					ruleId: t?.ruleId ?? t?.rule_id ?? null,
					ownerDept: t?.ownerDept ?? t?.owner_dept ?? null,
					intervalMinutes: Number(t?.intervalMinutes ?? t?.interval_minutes ?? 60),
					enabled: t?.enabled ?? true,
					lastTriggeredAt: t?.lastTriggeredAt ?? t?.last_triggered_at ?? null,
				})),
			);
		} catch (e: any) {
			toast.error(e?.message || "加载巡检计划失败");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void fetchList();
	}, [fetchList]);

	const filtered = useMemo(() => {
		const k = keyword.trim().toLowerCase();
		if (!k) return items;
		return items.filter((it) => {
			const name = String(it.name || "").toLowerCase();
			const ds = String(it.datasetId || "").toLowerCase();
			const rule = String(it.ruleId || "").toLowerCase();
			return name.includes(k) || ds.includes(k) || rule.includes(k);
		});
	}, [items, keyword]);

	const openCreate = () => {
		setForm(DEFAULT_FORM);
		setDialogOpen(true);
	};

	const openEdit = (row: TaskRow) => {
		setForm({
			id: row.id,
			name: String(row.name || ""),
			datasetId: row.datasetId,
			ruleId: String(row.ruleId || ""),
			intervalMinutes: String(row.intervalMinutes ?? 60),
			enabled: Boolean(row.enabled ?? true),
		});
		setDialogOpen(true);
	};

	const save = useCallback(async () => {
		if (!form.datasetId.trim()) {
			toast.error("请输入数据集ID");
			return;
		}
		const interval = Number(form.intervalMinutes || "0");
		if (Number.isNaN(interval) || interval <= 0) {
			toast.error("请输入有效的巡检周期（分钟）");
			return;
		}
		setSaving(true);
		try {
			const payload: any = {
				name: form.name.trim() || null,
				datasetId: form.datasetId.trim(),
				ruleId: form.ruleId.trim() || null,
				intervalMinutes: interval,
				enabled: form.enabled,
			};
			if (form.id) {
				await updateQualityTask(form.id, payload);
			} else {
				await createQualityTask(payload);
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

	const toggle = useCallback(
		async (row: TaskRow) => {
			try {
				await toggleQualityTask(row.id, !Boolean(row.enabled));
				await fetchList();
			} catch (e: any) {
				toast.error(e?.message || "操作失败");
			}
		},
		[fetchList],
	);

	const trigger = useCallback(
		async (row: TaskRow) => {
			try {
				await triggerQualityTask(row.id);
				toast.success("已触发巡检");
				await fetchList();
			} catch (e: any) {
				toast.error(e?.message || "触发失败");
			}
		},
		[fetchList],
	);

	const remove = useCallback(
		async (row: TaskRow) => {
			if (!window.confirm("确认删除该巡检计划？")) return;
			try {
				await deleteQualityTask(row.id);
				toast.success("已删除");
				await fetchList();
			} catch (e: any) {
				toast.error(e?.message || "删除失败");
			}
		},
		[fetchList],
	);

	const enabledBadge = (enabled?: boolean | null) =>
		enabled ? <Badge variant="secondary">启用</Badge> : <Badge variant="outline">停用</Badge>;

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-row items-center justify-between space-y-0">
					<CardTitle>质量巡检计划</CardTitle>
					<Button size="sm" onClick={openCreate}>
						新增
					</Button>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="flex flex-col gap-2 md:flex-row md:items-end">
						<div className="flex-1 space-y-2">
							<Label>检索</Label>
							<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="按名称/数据集ID/规则ID搜索" />
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
									<th className="px-3 py-2">数据集ID</th>
									<th className="px-3 py-2">规则ID</th>
									<th className="px-3 py-2">周期(分钟)</th>
									<th className="px-3 py-2">状态</th>
									<th className="px-3 py-2">上次触发</th>
									<th className="px-3 py-2 w-[120px]">操作</th>
								</tr>
							</thead>
							<tbody>
								{filtered.map((row) => (
									<tr key={row.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs font-medium">{row.name || "-"}</td>
										<td className="px-3 py-2 text-xs font-mono">{row.datasetId}</td>
										<td className="px-3 py-2 text-xs font-mono">{row.ruleId || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.intervalMinutes ?? 60}</td>
										<td className="px-3 py-2 text-xs">{enabledBadge(row.enabled)}</td>
										<td className="px-3 py-2 text-xs">{formatDateTime(row.lastTriggeredAt)}</td>
										<td className="px-3 py-2">
											<DropdownMenu>
												<DropdownMenuTrigger asChild>
													<Button size="sm" variant="secondary">
														操作
													</Button>
												</DropdownMenuTrigger>
												<DropdownMenuContent align="end">
													<DropdownMenuItem onClick={() => openEdit(row)}>编辑</DropdownMenuItem>
													<DropdownMenuItem onClick={() => void trigger(row)}>触发</DropdownMenuItem>
													<DropdownMenuItem onClick={() => void toggle(row)}>
														{row.enabled ? "停用" : "启用"}
													</DropdownMenuItem>
													<DropdownMenuSeparator />
													<DropdownMenuItem className="text-destructive" onClick={() => void remove(row)}>
														删除
													</DropdownMenuItem>
												</DropdownMenuContent>
											</DropdownMenu>
										</td>
									</tr>
								))}
								{filtered.length === 0 && (
									<tr>
										<td colSpan={7} className="px-3 py-10 text-center text-muted-foreground">
											{loading ? "加载中..." : "暂无巡检计划"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
					<div className="text-xs text-muted-foreground">
						说明：当前为轻量巡检计划（按分钟间隔触发已绑定质量规则），更复杂的编排仍由外部ETL/调度平台承担。
					</div>
				</CardContent>
			</Card>

			<Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
				<DialogContent className="sm:max-w-[520px]">
					<DialogHeader>
						<DialogTitle>{form.id ? "编辑巡检计划" : "新增巡检计划"}</DialogTitle>
					</DialogHeader>
					<div className="space-y-3">
						<div className="space-y-2">
							<Label>名称</Label>
							<Input value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} placeholder="可选" />
						</div>
						<div className="space-y-2">
							<Label>数据集ID *</Label>
							<Input
								value={form.datasetId}
								onChange={(e) => setForm((p) => ({ ...p, datasetId: e.target.value }))}
								placeholder="UUID，如：xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
							/>
						</div>
						<div className="space-y-2">
							<Label>规则ID（可选）</Label>
							<Input
								value={form.ruleId}
								onChange={(e) => setForm((p) => ({ ...p, ruleId: e.target.value }))}
								placeholder="不填则运行该数据集绑定的全部规则"
							/>
						</div>
						<div className="space-y-2">
							<Label>巡检周期（分钟）*</Label>
							<Input
								value={form.intervalMinutes}
								onChange={(e) => setForm((p) => ({ ...p, intervalMinutes: e.target.value }))}
								placeholder="例如：60"
							/>
						</div>
						<div className="flex items-center justify-between">
							<div className="space-y-1">
								<Label>启用</Label>
								<div className="text-xs text-muted-foreground">启用后会按周期自动触发</div>
							</div>
							<Switch checked={form.enabled} onCheckedChange={(checked) => setForm((p) => ({ ...p, enabled: checked }))} />
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

