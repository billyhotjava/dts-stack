import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { appendIssueAction, closeIssue, createIssue, listIssues, updateIssue } from "@/api/platformApi";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { formatDateTime } from "@/utils/format";

type IssueRow = {
	id: string;
	title?: string | null;
	summary?: string | null;
	status?: string | null;
	severity?: string | null;
	priority?: string | null;
	assignedTo?: string | null;
	createdBy?: string | null;
	createdDate?: string | null;
	lastModifiedDate?: string | null;
	actions?: Array<{ actor?: string | null; actionType?: string | null; notes?: string | null; createdDate?: string | null }> | null;
};

type IssueForm = {
	id?: string;
	title: string;
	summary: string;
	severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
	priority: "P1" | "P2" | "P3";
	assignedTo: string;
};

const DEFAULT_FORM: IssueForm = {
	title: "",
	summary: "",
	severity: "MEDIUM",
	priority: "P2",
	assignedTo: "",
};

export default function QualityIssuesPage() {
	const [loading, setLoading] = useState(false);
	const [items, setItems] = useState<IssueRow[]>([]);
	const [keyword, setKeyword] = useState("");

	const [dialogOpen, setDialogOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [form, setForm] = useState<IssueForm>(DEFAULT_FORM);

	const [detailOpen, setDetailOpen] = useState(false);
	const [active, setActive] = useState<IssueRow | null>(null);
	const [note, setNote] = useState("");
	const [closing, setClosing] = useState(false);
	const [resolution, setResolution] = useState("");

	const fetchList = useCallback(async () => {
		setLoading(true);
		try {
			const list: any[] = await listIssues();
			setItems(
				(Array.isArray(list) ? list : []).map((t: any) => ({
					id: String(t?.id ?? ""),
					title: t?.title ?? null,
					summary: t?.summary ?? null,
					status: t?.status ?? null,
					severity: t?.severity ?? null,
					priority: t?.priority ?? null,
					assignedTo: t?.assignedTo ?? null,
					createdBy: t?.createdBy ?? null,
					createdDate: t?.createdDate ?? null,
					lastModifiedDate: t?.lastModifiedDate ?? null,
					actions: Array.isArray(t?.actions) ? t.actions : null,
				})),
			);
		} catch (e: any) {
			toast.error(e?.message || "加载问题单失败");
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
		return items.filter((it) => String(it.title || "").toLowerCase().includes(k) || String(it.assignedTo || "").toLowerCase().includes(k));
	}, [items, keyword]);

	const statusBadge = (status?: string | null) => {
		const s = String(status || "").toUpperCase();
		if (s === "CLOSED") return <Badge variant="outline">已关闭</Badge>;
		if (s === "IN_PROGRESS") return <Badge variant="secondary">处理中</Badge>;
		if (s === "PENDING") return <Badge variant="secondary">待处理</Badge>;
		return <Badge variant="outline">新建</Badge>;
	};

	const openCreate = () => {
		setForm(DEFAULT_FORM);
		setDialogOpen(true);
	};

	const openEdit = (row: IssueRow) => {
		setForm({
			id: row.id,
			title: String(row.title || ""),
			summary: String(row.summary || ""),
			severity: (String(row.severity || "MEDIUM").toUpperCase() as any) || "MEDIUM",
			priority: (String(row.priority || "P2").toUpperCase() as any) || "P2",
			assignedTo: String(row.assignedTo || ""),
		});
		setDialogOpen(true);
	};

	const save = useCallback(async () => {
		if (!form.title.trim()) {
			toast.error("请输入标题");
			return;
		}
		setSaving(true);
		try {
			const payload: any = {
				title: form.title.trim(),
				summary: form.summary.trim() || null,
				severity: form.severity,
				priority: form.priority,
				assignedTo: form.assignedTo.trim() || null,
			};
			if (form.id) {
				await updateIssue(form.id, payload);
			} else {
				await createIssue(payload);
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

	const openDetail = (row: IssueRow) => {
		setActive(row);
		setNote("");
		setResolution("");
		setDetailOpen(true);
	};

	const addNote = useCallback(async () => {
		if (!active?.id) return;
		if (!note.trim()) {
			toast.error("请输入处理记录");
			return;
		}
		try {
			await appendIssueAction(active.id, { actionType: "NOTE", notes: note.trim(), attachments: [] });
			toast.success("已添加");
			setNote("");
			await fetchList();
		} catch (e: any) {
			toast.error(e?.message || "添加失败");
		}
	}, [active, note, fetchList]);

	const doClose = useCallback(async () => {
		if (!active?.id) return;
		setClosing(true);
		try {
			await closeIssue(active.id, resolution.trim() || undefined);
			toast.success("已关闭");
			setDetailOpen(false);
			await fetchList();
		} catch (e: any) {
			toast.error(e?.message || "关闭失败");
		} finally {
			setClosing(false);
		}
	}, [active, resolution, fetchList]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-row items-center justify-between space-y-0">
					<CardTitle>问题闭环与工单</CardTitle>
					<Button size="sm" onClick={openCreate}>
						新建问题单
					</Button>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="flex flex-col gap-2 md:flex-row md:items-end">
						<div className="flex-1 space-y-2">
							<Label>检索</Label>
							<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="按标题/指派人搜索" />
						</div>
						<Button variant="secondary" disabled={loading} onClick={() => void fetchList()}>
							刷新
						</Button>
					</div>
					<div className="overflow-auto rounded border">
						<table className="w-full text-sm">
							<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
								<tr>
									<th className="px-3 py-2">标题</th>
									<th className="px-3 py-2">状态</th>
									<th className="px-3 py-2">严重度</th>
									<th className="px-3 py-2">优先级</th>
									<th className="px-3 py-2">指派给</th>
									<th className="px-3 py-2">更新时间</th>
									<th className="px-3 py-2 w-[180px]">操作</th>
								</tr>
							</thead>
							<tbody>
								{filtered.map((row) => (
									<tr key={row.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs font-medium">{row.title || "-"}</td>
										<td className="px-3 py-2 text-xs">{statusBadge(row.status)}</td>
										<td className="px-3 py-2 text-xs">{row.severity || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.priority || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.assignedTo || "-"}</td>
										<td className="px-3 py-2 text-xs">{formatDateTime(row.lastModifiedDate || row.createdDate)}</td>
										<td className="px-3 py-2">
											<div className="flex gap-2">
												<Button size="sm" variant="secondary" onClick={() => openDetail(row)}>
													查看
												</Button>
												<Button size="sm" variant="outline" onClick={() => openEdit(row)}>
													编辑
												</Button>
											</div>
										</td>
									</tr>
								))}
								{filtered.length === 0 && (
									<tr>
										<td colSpan={7} className="px-3 py-10 text-center text-muted-foreground">
											{loading ? "加载中..." : "暂无问题单"}
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
						<DialogTitle>{form.id ? "编辑问题单" : "新建问题单"}</DialogTitle>
					</DialogHeader>
					<div className="grid grid-cols-1 gap-3 md:grid-cols-2">
						<div className="space-y-2 md:col-span-2">
							<Label>标题 *</Label>
							<Input value={form.title} onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))} />
						</div>
						<div className="space-y-2">
							<Label>严重度</Label>
							<Select value={form.severity} onValueChange={(v: any) => setForm((p) => ({ ...p, severity: v }))}>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="LOW">LOW</SelectItem>
									<SelectItem value="MEDIUM">MEDIUM</SelectItem>
									<SelectItem value="HIGH">HIGH</SelectItem>
									<SelectItem value="CRITICAL">CRITICAL</SelectItem>
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>优先级</Label>
							<Select value={form.priority} onValueChange={(v: any) => setForm((p) => ({ ...p, priority: v }))}>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="P1">P1</SelectItem>
									<SelectItem value="P2">P2</SelectItem>
									<SelectItem value="P3">P3</SelectItem>
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>指派给（可选）</Label>
							<Input value={form.assignedTo} onChange={(e) => setForm((p) => ({ ...p, assignedTo: e.target.value }))} placeholder="用户名" />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>问题描述</Label>
							<Textarea
								value={form.summary}
								onChange={(e) => setForm((p) => ({ ...p, summary: e.target.value }))}
								className="min-h-[160px]"
								placeholder="描述问题现象、影响范围、建议处理方式等"
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

			<Dialog open={detailOpen} onOpenChange={setDetailOpen}>
				<DialogContent className="sm:max-w-[820px]">
					<DialogHeader>
						<DialogTitle>问题单详情</DialogTitle>
					</DialogHeader>
					{active ? (
						<div className="space-y-3">
							<div className="space-y-1">
								<div className="text-sm font-medium">{active.title}</div>
								<div className="text-xs text-muted-foreground">
									{statusBadge(active.status)} · 严重度 {active.severity || "-"} · 优先级 {active.priority || "-"} · 指派给 {active.assignedTo || "-"}
								</div>
							</div>
							{active.summary ? <div className="text-sm whitespace-pre-wrap">{active.summary}</div> : null}
							<div className="space-y-2">
								<Label>处理记录</Label>
								<div className="max-h-[240px] overflow-auto rounded border p-2 text-sm">
									{(active.actions || []).length ? (
										<div className="space-y-2">
											{(active.actions || []).map((a, idx) => (
												<div key={idx} className="rounded bg-muted/30 p-2">
													<div className="text-xs text-muted-foreground">
														{a.actor || "-"} · {a.actionType || "-"} · {formatDateTime(a.createdDate)}
													</div>
													<div className="text-sm whitespace-pre-wrap">{a.notes || "-"}</div>
												</div>
											))}
										</div>
									) : (
										<div className="text-xs text-muted-foreground">暂无记录</div>
									)}
								</div>
							</div>
							<div className="space-y-2">
								<Label>新增处理记录</Label>
								<Textarea value={note} onChange={(e) => setNote(e.target.value)} className="min-h-[90px]" placeholder="填写处理过程、证据、结论等" />
								<Button variant="secondary" onClick={() => void addNote()}>
									添加
								</Button>
							</div>
							<div className="space-y-2">
								<Label>关闭原因（可选）</Label>
								<Input value={resolution} onChange={(e) => setResolution(e.target.value)} placeholder="例如：已修复 / 误报 / 已延期" />
								<Button variant="destructive" disabled={closing} onClick={() => void doClose()}>
									关闭问题单
								</Button>
							</div>
						</div>
					) : (
						<div className="text-sm text-muted-foreground">未选择问题单</div>
					)}
					<DialogFooter>
						<Button variant="secondary" onClick={() => setDetailOpen(false)}>
							关闭
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}

