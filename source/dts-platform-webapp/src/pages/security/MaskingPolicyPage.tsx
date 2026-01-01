import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { createMaskingRule, deleteMaskingRule, listMaskingRules, previewMasking, updateMaskingRule } from "@/api/platformApi";
import { useUserInfo } from "@/store/userStore";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { formatDateTime } from "@/utils/format";

type MaskingRow = {
	id: string;
	datasetId?: string | null;
	column?: string | null;
	function?: string | null;
	args?: string | null;
	createdBy?: string | null;
	createdDate?: string | null;
};

type RuleForm = {
	id?: string;
	datasetId: string;
	column: string;
	function: "hash" | "mask_email" | "mask_phone" | "none";
	args: string;
};

const DEFAULT_FORM: RuleForm = {
	datasetId: "",
	column: "",
	function: "mask_phone",
	args: "",
};

export default function MaskingPolicyPage() {
	const userInfo = useUserInfo() as any;
	const roles: string[] = useMemo(() => (Array.isArray(userInfo?.roles) ? userInfo.roles.map((r: any) => String(r ?? "").toUpperCase()) : []), [userInfo]);
	const canEdit = useMemo(() => roles.includes("ROLE_OP_ADMIN") || roles.includes("ROLE_ADMIN") || roles.includes("ROLE_INST_DATA_OWNER") || roles.includes("ROLE_INST_DATA_DEV") || roles.includes("ROLE_DEPT_DATA_OWNER") || roles.includes("ROLE_DEPT_DATA_DEV"), [roles]);

	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [items, setItems] = useState<MaskingRow[]>([]);

	const [dialogOpen, setDialogOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [form, setForm] = useState<RuleForm>(DEFAULT_FORM);

	const [previewValue, setPreviewValue] = useState("");
	const [previewOutput, setPreviewOutput] = useState<string | null>(null);

	const fetchList = useCallback(async () => {
		setLoading(true);
		try {
			const list: any[] = await listMaskingRules();
			setItems(
				(Array.isArray(list) ? list : []).map((r: any) => ({
					id: String(r?.id ?? ""),
					datasetId: String(r?.dataset?.id ?? r?.datasetId ?? r?.dataset_id ?? ""),
					column: r?.column ?? null,
					function: r?.function ?? null,
					args: r?.args ?? null,
					createdBy: r?.createdBy ?? null,
					createdDate: r?.createdDate ?? null,
				})),
			);
		} catch (e: any) {
			toast.error(e?.message || "加载脱敏规则失败");
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
		return items.filter(
			(it) =>
				String(it.datasetId || "").toLowerCase().includes(k) ||
				String(it.column || "").toLowerCase().includes(k) ||
				String(it.function || "").toLowerCase().includes(k),
		);
	}, [items, keyword]);

	const openCreate = () => {
		setForm(DEFAULT_FORM);
		setPreviewValue("");
		setPreviewOutput(null);
		setDialogOpen(true);
	};

	const openEdit = (row: MaskingRow) => {
		setForm({
			id: row.id,
			datasetId: String(row.datasetId || ""),
			column: String(row.column || ""),
			function: (String(row.function || "mask_phone") as any) || "mask_phone",
			args: String(row.args || ""),
		});
		setPreviewValue("");
		setPreviewOutput(null);
		setDialogOpen(true);
	};

	const runPreview = useCallback(async () => {
		try {
			const resp: any = await previewMasking({ function: form.function, value: previewValue });
			setPreviewOutput(String(resp?.output ?? ""));
		} catch (e: any) {
			toast.error(e?.message || "预览失败");
		}
	}, [form.function, previewValue]);

	const save = useCallback(async () => {
		if (!form.datasetId.trim()) {
			toast.error("请输入数据集ID");
			return;
		}
		if (!form.column.trim()) {
			toast.error("请输入字段名");
			return;
		}
		setSaving(true);
		try {
			const payload: any = {
				dataset: { id: form.datasetId.trim() },
				column: form.column.trim(),
				function: form.function === "none" ? null : form.function,
				args: form.args.trim() || null,
			};
			if (form.id) {
				await updateMaskingRule(form.id, payload);
			} else {
				await createMaskingRule(payload);
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
		async (row: MaskingRow) => {
			if (!window.confirm("确认删除该脱敏规则？")) return;
			try {
				await deleteMaskingRule(row.id);
				toast.success("已删除");
				await fetchList();
			} catch (e: any) {
				toast.error(e?.message || "删除失败");
			}
		},
		[fetchList],
	);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-row items-center justify-between space-y-0">
					<CardTitle>脱敏策略中心</CardTitle>
					{canEdit ? (
						<Button size="sm" onClick={openCreate}>
							新增规则
						</Button>
					) : null}
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="text-sm text-muted-foreground">
						说明：轻量脱敏规则（字段级），主要用于查询预览/导出展示侧的保护；更复杂的静态脱敏由外部平台承担。
					</div>
					<div className="flex flex-col gap-2 md:flex-row md:items-end">
						<div className="flex-1 space-y-2">
							<Label>检索</Label>
							<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="按数据集ID/字段/函数搜索" />
						</div>
						<Button variant="secondary" disabled={loading} onClick={() => void fetchList()}>
							刷新
						</Button>
					</div>
					<div className="overflow-auto rounded border">
						<table className="w-full text-sm">
							<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
								<tr>
									<th className="px-3 py-2">数据集ID</th>
									<th className="px-3 py-2">字段</th>
									<th className="px-3 py-2">函数</th>
									<th className="px-3 py-2">参数</th>
									<th className="px-3 py-2">创建时间</th>
									<th className="px-3 py-2 w-[180px]">操作</th>
								</tr>
							</thead>
							<tbody>
								{filtered.map((row) => (
									<tr key={row.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs font-mono">{row.datasetId || "-"}</td>
										<td className="px-3 py-2 text-xs">{row.column || "-"}</td>
										<td className="px-3 py-2 text-xs">
											{row.function ? <Badge variant="secondary">{row.function}</Badge> : <Badge variant="outline">未设置</Badge>}
										</td>
										<td className="px-3 py-2 text-xs">{row.args || "-"}</td>
										<td className="px-3 py-2 text-xs">{formatDateTime(row.createdDate)}</td>
										<td className="px-3 py-2">
											{canEdit ? (
												<div className="flex gap-2">
													<Button size="sm" variant="secondary" onClick={() => openEdit(row)}>
														编辑
													</Button>
													<Button size="sm" variant="destructive" onClick={() => void remove(row)}>
														删除
													</Button>
												</div>
											) : (
												<span className="text-xs text-muted-foreground">无权限</span>
											)}
										</td>
									</tr>
								))}
								{filtered.length === 0 && (
									<tr>
										<td colSpan={6} className="px-3 py-10 text-center text-muted-foreground">
											{loading ? "加载中..." : "暂无规则"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</CardContent>
			</Card>

			<Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
				<DialogContent className="sm:max-w-[640px]">
					<DialogHeader>
						<DialogTitle>{form.id ? "编辑脱敏规则" : "新增脱敏规则"}</DialogTitle>
					</DialogHeader>
					<div className="space-y-3">
						<div className="space-y-2">
							<Label>数据集ID *</Label>
							<Input value={form.datasetId} onChange={(e) => setForm((p) => ({ ...p, datasetId: e.target.value }))} placeholder="UUID" />
						</div>
						<div className="space-y-2">
							<Label>字段名 *</Label>
							<Input value={form.column} onChange={(e) => setForm((p) => ({ ...p, column: e.target.value }))} placeholder="例如：phone / id_card" />
						</div>
						<div className="space-y-2">
							<Label>函数</Label>
							<Select value={form.function} onValueChange={(v: any) => setForm((p) => ({ ...p, function: v }))}>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="mask_phone">mask_phone</SelectItem>
									<SelectItem value="mask_email">mask_email</SelectItem>
									<SelectItem value="hash">hash</SelectItem>
									<SelectItem value="none">不脱敏</SelectItem>
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>参数（可选）</Label>
							<Input value={form.args} onChange={(e) => setForm((p) => ({ ...p, args: e.target.value }))} />
						</div>
						<div className="space-y-2">
							<Label>脱敏预览</Label>
							<div className="flex gap-2">
								<Input value={previewValue} onChange={(e) => setPreviewValue(e.target.value)} placeholder="输入样例值" />
								<Button variant="secondary" onClick={() => void runPreview()}>
									预览
								</Button>
							</div>
							{previewOutput != null ? <div className="text-xs text-muted-foreground">输出：{previewOutput}</div> : null}
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

