import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { createModelTemplate, deleteModelTemplate, listModelTemplates, updateModelTemplate } from "@/api/platformApi";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";

type TemplateRow = {
	id: string;
	name: string;
	layer?: string | null;
	status?: string | null;
	namingRule?: string | null;
	fieldsTemplate?: string | null;
	reviewChecklist?: string | null;
};

type FormState = {
	id?: string;
	name: string;
	layer: string;
	status: "ACTIVE" | "ARCHIVED";
	namingRule: string;
	fieldsTemplate: string;
	reviewChecklist: string;
};

const DEFAULT_FORM: FormState = {
	name: "",
	layer: "DWD",
	status: "ACTIVE",
	namingRule: "",
	fieldsTemplate: "",
	reviewChecklist: "",
};

export default function ModelTemplatesPage() {
	const [loading, setLoading] = useState(false);
	const [items, setItems] = useState<TemplateRow[]>([]);
	const [keyword, setKeyword] = useState("");

	const [dialogOpen, setDialogOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [form, setForm] = useState<FormState>(DEFAULT_FORM);

	const fetchList = useCallback(async () => {
		setLoading(true);
		try {
			const list: any[] = await listModelTemplates();
			setItems(
				(Array.isArray(list) ? list : []).map((t: any) => ({
					id: String(t?.id ?? ""),
					name: String(t?.name ?? ""),
					layer: t?.layer ?? null,
					status: t?.status ?? null,
					namingRule: t?.namingRule ?? t?.naming_rule ?? null,
					fieldsTemplate: t?.fieldsTemplate ?? t?.fields_template ?? null,
					reviewChecklist: t?.reviewChecklist ?? t?.review_checklist ?? null,
				})),
			);
		} catch (e: any) {
			toast.error(e?.message || "加载模板失败");
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
		return items.filter((it) => (it.name || "").toLowerCase().includes(k) || String(it.layer || "").toLowerCase().includes(k));
	}, [items, keyword]);

	const openCreate = () => {
		setForm(DEFAULT_FORM);
		setDialogOpen(true);
	};

	const openEdit = (row: TemplateRow) => {
		setForm({
			id: row.id,
			name: row.name || "",
			layer: String(row.layer || "DWD"),
			status: String(row.status || "ACTIVE").toUpperCase() === "ARCHIVED" ? "ARCHIVED" : "ACTIVE",
			namingRule: String(row.namingRule || ""),
			fieldsTemplate: String(row.fieldsTemplate || ""),
			reviewChecklist: String(row.reviewChecklist || ""),
		});
		setDialogOpen(true);
	};

	const save = useCallback(async () => {
		if (!form.name.trim()) {
			toast.error("请输入名称");
			return;
		}
		setSaving(true);
		try {
			const payload: any = {
				name: form.name.trim(),
				layer: form.layer || null,
				status: form.status,
				namingRule: form.namingRule.trim() || null,
				fieldsTemplate: form.fieldsTemplate.trim() || null,
				reviewChecklist: form.reviewChecklist.trim() || null,
			};
			if (form.id) {
				await updateModelTemplate(form.id, payload);
			} else {
				await createModelTemplate(payload);
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
		async (id: string) => {
			if (!window.confirm("确认删除该模板？")) return;
			try {
				await deleteModelTemplate(id);
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
		return s === "ARCHIVED" ? <Badge variant="outline">归档</Badge> : <Badge variant="secondary">启用</Badge>;
	};

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-row items-center justify-between space-y-0">
					<CardTitle>模型规范与模板</CardTitle>
					<Button size="sm" onClick={openCreate}>
						新增
					</Button>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="flex flex-col gap-2 md:flex-row md:items-end">
						<div className="flex-1 space-y-2">
							<Label>检索</Label>
							<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="按名称/分层筛选" />
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
									<th className="px-3 py-2">分层</th>
									<th className="px-3 py-2">状态</th>
									<th className="px-3 py-2 w-[220px]">操作</th>
								</tr>
							</thead>
							<tbody>
								{filtered.map((row) => (
									<tr key={row.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs font-medium">{row.name}</td>
										<td className="px-3 py-2 text-xs">{row.layer || "-"}</td>
										<td className="px-3 py-2 text-xs">{statusBadge(row.status)}</td>
										<td className="px-3 py-2">
											<div className="flex gap-2">
												<Button size="sm" variant="secondary" onClick={() => openEdit(row)}>
													编辑
												</Button>
												<Button size="sm" variant="destructive" onClick={() => void remove(row.id)}>
													删除
												</Button>
											</div>
										</td>
									</tr>
								))}
								{filtered.length === 0 && (
									<tr>
										<td colSpan={4} className="px-3 py-10 text-center text-muted-foreground">
											{loading ? "加载中..." : "暂无模板"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</CardContent>
			</Card>

			<Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
				<DialogContent className="sm:max-w-[920px]">
					<DialogHeader>
						<DialogTitle>{form.id ? "编辑模板" : "新增模板"}</DialogTitle>
					</DialogHeader>
					<div className="grid grid-cols-1 gap-3 md:grid-cols-2">
						<div className="space-y-2 md:col-span-2">
							<Label>名称 *</Label>
							<Input value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} />
						</div>
						<div className="space-y-2">
							<Label>分层</Label>
							<Select value={form.layer} onValueChange={(v) => setForm((p) => ({ ...p, layer: v }))}>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="ODS">ODS</SelectItem>
									<SelectItem value="DWD">DWD</SelectItem>
									<SelectItem value="DWS">DWS</SelectItem>
									<SelectItem value="ADS">ADS</SelectItem>
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>状态</Label>
							<Select value={form.status} onValueChange={(v: any) => setForm((p) => ({ ...p, status: v }))}>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="ACTIVE">启用</SelectItem>
									<SelectItem value="ARCHIVED">归档</SelectItem>
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>字段命名规范（可选）</Label>
							<Textarea
								value={form.namingRule}
								onChange={(e) => setForm((p) => ({ ...p, namingRule: e.target.value }))}
								className="min-h-[80px]"
								placeholder="例如：ods_业务域_表名；字段使用下划线小写..."
							/>
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>字段模板（可选）</Label>
							<Textarea
								value={form.fieldsTemplate}
								onChange={(e) => setForm((p) => ({ ...p, fieldsTemplate: e.target.value }))}
								className="min-h-[140px]"
								placeholder="可填写字段清单模板（JSON/Markdown/纯文本均可）"
							/>
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>评审要点清单（可选）</Label>
							<Textarea
								value={form.reviewChecklist}
								onChange={(e) => setForm((p) => ({ ...p, reviewChecklist: e.target.value }))}
								className="min-h-[140px]"
								placeholder="例如：主键/分区；字段口径；敏感字段标记；血缘影响评估..."
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

