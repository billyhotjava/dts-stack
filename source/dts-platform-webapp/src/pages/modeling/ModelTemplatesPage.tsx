import { Modal, Select as AntSelect, Spin, Table as AntTable, Tabs } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	createModelTemplate,
	deleteModelTemplate,
	listDatasets,
	listModelTemplates,
	listTablesByDataset,
	updateModelTemplate,
	validateModelTemplate,
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

type TemplateRow = {
	id: string;
	name: string;
	layer?: string | null;
	status?: string | null;
	version?: string | null;
	versionNotes?: string | null;
	namingRule?: string | null;
	fieldsTemplate?: string | null;
	reviewChecklist?: string | null;
	lastModifiedDate?: string | null;
};

type FormState = {
	id?: string;
	name: string;
	layer: string;
	status: "ACTIVE" | "ARCHIVED";
	version: string;
	versionNotes: string;
	namingRule: string;
	fieldsTemplate: string;
	reviewChecklist: string;
};

const DEFAULT_FORM: FormState = {
	name: "",
	layer: "DWD",
	status: "ACTIVE",
	version: "v1",
	versionNotes: "",
	namingRule: "",
	fieldsTemplate: "",
	reviewChecklist: "",
};

type DatasetOption = { id: string; name: string; ownerDept?: string | null; classification?: string | null };
type TableOption = { id: string; name: string };

export default function ModelTemplatesPage() {
	const [loading, setLoading] = useState(false);
	const [items, setItems] = useState<TemplateRow[]>([]);
	const [keyword, setKeyword] = useState("");

	const [dialogOpen, setDialogOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [form, setForm] = useState<FormState>(DEFAULT_FORM);

	const [detailOpen, setDetailOpen] = useState(false);
	const [activeTemplate, setActiveTemplate] = useState<TemplateRow | null>(null);

	const [validateOpen, setValidateOpen] = useState(false);
	const [validateLoading, setValidateLoading] = useState(false);
	const [validateResult, setValidateResult] = useState<any | null>(null);

	const [datasetKeyword, setDatasetKeyword] = useState("");
	const [datasetsLoading, setDatasetsLoading] = useState(false);
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [datasetId, setDatasetId] = useState<string>("");

	const [tableKeyword, setTableKeyword] = useState("");
	const [tablesLoading, setTablesLoading] = useState(false);
	const [tables, setTables] = useState<TableOption[]>([]);
	const [tableId, setTableId] = useState<string>("");

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
					version: t?.version ?? null,
					versionNotes: t?.versionNotes ?? t?.version_notes ?? null,
					namingRule: t?.namingRule ?? t?.naming_rule ?? null,
					fieldsTemplate: t?.fieldsTemplate ?? t?.fields_template ?? null,
					reviewChecklist: t?.reviewChecklist ?? t?.review_checklist ?? null,
					lastModifiedDate: t?.lastModifiedDate ?? t?.last_modified_date ?? null,
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
		return items.filter(
			(it) =>
				(it.name || "").toLowerCase().includes(k) ||
				String(it.layer || "").toLowerCase().includes(k) ||
				String(it.status || "").toLowerCase().includes(k),
		);
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
			version: String(row.version || "v1"),
			versionNotes: String(row.versionNotes || ""),
			namingRule: String(row.namingRule || ""),
			fieldsTemplate: String(row.fieldsTemplate || ""),
			reviewChecklist: String(row.reviewChecklist || ""),
		});
		setDialogOpen(true);
	};

	const openDetail = (row: TemplateRow) => {
		setActiveTemplate(row);
		setDetailOpen(true);
	};

	const downloadText = (filename: string, content: string, type = "text/plain;charset=utf-8") => {
		const blob = new Blob([content], { type });
		const url = URL.createObjectURL(blob);
		const a = document.createElement("a");
		a.href = url;
		a.download = filename;
		document.body.appendChild(a);
		a.click();
		a.remove();
		URL.revokeObjectURL(url);
	};

	const exportMarkdown = (row: TemplateRow) => {
		const lines: string[] = [];
		lines.push(`# 模型模板：${row.name || "-"}`);
		lines.push("");
		lines.push(`- 分层：${row.layer || "-"}`);
		lines.push(`- 状态：${String(row.status || "").toUpperCase() || "-"}`);
		lines.push(`- 版本：${row.version || "-"}`);
		lines.push("");
		lines.push("## 命名规则");
		lines.push("```");
		lines.push(String(row.namingRule || "").trim() || "(未配置)");
		lines.push("```");
		lines.push("");
		lines.push("## 字段模板");
		lines.push("```");
		lines.push(String(row.fieldsTemplate || "").trim() || "(未配置)");
		lines.push("```");
		lines.push("");
		lines.push("## 评审清单");
		lines.push("```");
		lines.push(String(row.reviewChecklist || "").trim() || "(未配置)");
		lines.push("```");
		lines.push("");
		lines.push("## 使用建议");
		lines.push("- 建议 AI/开发生成 DDL 时在字段注释写入标准编码：`STD:CODE`（例如 `STD:DS_001`）");
		lines.push("- 在“元数据采集与维护”页可用“自动匹配/校验映射”对字段与数据标准做闭环");
		downloadText(`model-template-${row.layer || "LAYER"}-${row.name || row.id}.md`, lines.join("\n"), "text/markdown;charset=utf-8");
	};

	const loadDatasets = useCallback(async () => {
		setDatasetsLoading(true);
		try {
			const resp = (await listDatasets({ page: 0, size: 200, keyword: datasetKeyword.trim() || undefined })) as any;
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(
				content.map((d: any) => ({
					id: String(d.id),
					name: String(d.name || d.id),
					ownerDept: d.ownerDept ?? null,
					classification: d.classification ?? null,
				})),
			);
		} catch (e: any) {
			console.error(e);
			setDatasets([]);
			toast.error(e?.message || "加载数据集失败");
		} finally {
			setDatasetsLoading(false);
		}
	}, [datasetKeyword]);

	const loadTables = useCallback(async () => {
		if (!datasetId) {
			setTables([]);
			return;
		}
		setTablesLoading(true);
		try {
			const resp = (await listTablesByDataset(datasetId, tableKeyword.trim() || undefined)) as any;
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setTables(
				content.map((t: any) => ({
					id: String(t.id),
					name: String(t.name || t.tableName || t.id),
				})),
			);
		} catch (e: any) {
			console.error(e);
			setTables([]);
			toast.error(e?.message || "加载表失败");
		} finally {
			setTablesLoading(false);
		}
	}, [datasetId, tableKeyword]);

	const openValidate = useCallback(
		(row: TemplateRow) => {
			setActiveTemplate(row);
			setValidateResult(null);
			setDatasetId("");
			setTableId("");
			setTableKeyword("");
			setValidateOpen(true);
			void loadDatasets();
		},
		[loadDatasets],
	);

	const runValidate = useCallback(async () => {
		if (!activeTemplate?.id) return;
		if (!tableId) {
			toast.error("请选择要校验的数据表");
			return;
		}
		setValidateLoading(true);
		try {
			const res = (await validateModelTemplate(activeTemplate.id, tableId)) as any;
			setValidateResult(res || null);
			toast.success("校验完成");
		} catch (e: any) {
			console.error(e);
			setValidateResult(null);
			toast.error(e?.message || "校验失败");
		} finally {
			setValidateLoading(false);
		}
	}, [activeTemplate?.id, tableId]);

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
				version: form.version.trim() || null,
				versionNotes: form.versionNotes.trim() || null,
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
					<AntTable
						rowKey={(r: any) => String(r.id)}
						loading={loading}
						size="middle"
						pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (total) => `总计 ${total} 条` }}
						dataSource={filtered}
						locale={{ emptyText: loading ? <Spin size="small" /> : "暂无模板" }}
						columns={[
							{
								title: "名称",
								dataIndex: "name",
								key: "name",
								ellipsis: true,
								render: (v: any, r: any) => (
									<button type="button" className="text-left font-medium hover:underline" onClick={() => openDetail(r)}>
										{String(v || "-")}
									</button>
								),
							},
							{ title: "分层", dataIndex: "layer", key: "layer", width: 90, ellipsis: true },
							{
								title: "状态",
								dataIndex: "status",
								key: "status",
								width: 90,
								render: (v: any) => statusBadge(v),
							},
							{ title: "版本", dataIndex: "version", key: "version", width: 90, ellipsis: true },
							{
								title: "更新时间",
								dataIndex: "lastModifiedDate",
								key: "lastModifiedDate",
								width: 160,
								render: (v: any) => <span className="text-xs text-muted-foreground">{formatDateTime(v)}</span>,
							},
							{
								title: "操作",
								key: "action",
								width: 360,
								render: (_: any, r: any) => (
									<div className="flex flex-wrap gap-2">
										<Button size="sm" variant="outline" onClick={() => openDetail(r)}>
											预览
										</Button>
										<Button size="sm" variant="outline" onClick={() => void openValidate(r)}>
											校验
										</Button>
										<Button size="sm" variant="secondary" onClick={() => openEdit(r)}>
											编辑
										</Button>
										<Button size="sm" variant="outline" onClick={() => exportMarkdown(r)}>
											导出
										</Button>
										<Button size="sm" variant="destructive" onClick={() => void remove(r.id)}>
											删除
										</Button>
									</div>
								),
							},
						]}
					/>
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
						<div className="space-y-2">
							<Label>版本</Label>
							<Input value={form.version} onChange={(e) => setForm((p) => ({ ...p, version: e.target.value }))} placeholder="如：v1" />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>版本说明（可选）</Label>
							<Input value={form.versionNotes} onChange={(e) => setForm((p) => ({ ...p, versionNotes: e.target.value }))} placeholder="简述变更点" />
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>命名规则（可选，支持 regex）</Label>
							<Textarea
								value={form.namingRule}
								onChange={(e) => setForm((p) => ({ ...p, namingRule: e.target.value }))}
								className="min-h-[80px]"
								placeholder="示例：regex:^dwd_[a-z0-9_]+$"
							/>
							<div className="text-xs text-muted-foreground">
								建议：用 `regex:` 指定正则，或直接填写可编译的正则表达式（例如 `^ods_.*$`）。
							</div>
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>字段模板（可选）</Label>
							<Textarea
								value={form.fieldsTemplate}
								onChange={(e) => setForm((p) => ({ ...p, fieldsTemplate: e.target.value }))}
								className="min-h-[140px]"
								placeholder={`推荐 CSV：\nname,dataType,nullable,standardCode\nbiz_date,date,false,DS_001\n...\n\n或 JSON：[{\"name\":\"biz_date\",\"dataType\":\"date\",\"nullable\":false,\"standardCode\":\"DS_001\"}]`}
							/>
							<div className="text-xs text-muted-foreground">
								校验规则来源于此处字段模板：缺字段/类型/可空/标准编码（standardCode）会出问题清单。
							</div>
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

			<Modal
				open={detailOpen}
				title={activeTemplate ? `模板预览：${activeTemplate.name}` : "模板预览"}
				onCancel={() => setDetailOpen(false)}
				footer={null}
				width={980}
				destroyOnClose
			>
				{activeTemplate ? (
					<div className="space-y-3">
						<div className="flex flex-wrap items-center gap-2 text-sm">
							<Badge variant="secondary">分层：{activeTemplate.layer || "-"}</Badge>
							<Badge variant="outline">版本：{activeTemplate.version || "-"}</Badge>
							{statusBadge(activeTemplate.status)}
							<span className="text-xs text-muted-foreground">更新时间：{formatDateTime(activeTemplate.lastModifiedDate)}</span>
						</div>

						<div className="flex flex-wrap gap-2">
							<Button variant="outline" onClick={() => exportMarkdown(activeTemplate)}>
								导出 Markdown
							</Button>
							<Button
								variant="secondary"
								onClick={() => {
									openEdit(activeTemplate);
									setDetailOpen(false);
								}}
							>
								编辑
							</Button>
							<Button variant="outline" onClick={() => void openValidate(activeTemplate)}>
								校验
							</Button>
						</div>

						<Tabs
							items={[
								{
									key: "naming",
									label: "命名规则",
									children: (
										<pre className="whitespace-pre-wrap rounded border bg-muted/30 p-3 text-xs">
											{String(activeTemplate.namingRule || "").trim() || "(未配置)"}
										</pre>
									),
								},
								{
									key: "fields",
									label: "字段模板",
									children: (
										<pre className="whitespace-pre-wrap rounded border bg-muted/30 p-3 text-xs">
											{String(activeTemplate.fieldsTemplate || "").trim() || "(未配置)"}
										</pre>
									),
								},
								{
									key: "checklist",
									label: "评审清单",
									children: (
										<pre className="whitespace-pre-wrap rounded border bg-muted/30 p-3 text-xs">
											{String(activeTemplate.reviewChecklist || "").trim() || "(未配置)"}
										</pre>
									),
								},
								{
									key: "guide",
									label: "使用建议",
									children: (
										<div className="space-y-2 text-sm">
											<div>1) AI/开发生成 DDL：字段注释建议写 `STD:CODE`（例如 `STD:DS_001`）。</div>
											<div>2) 平台采集后，在“元数据采集与维护”页可用“自动匹配/校验映射”完成字段↔标准闭环。</div>
											<div>3) 本页面“校验”用于检查表结构是否符合模板约束（缺字段/类型/可空/标准编码）。</div>
										</div>
									),
								},
							]}
						/>
					</div>
				) : null}
			</Modal>

			<Modal
				open={validateOpen}
				title={activeTemplate ? `模板校验：${activeTemplate.name}` : "模板校验"}
				onCancel={() => setValidateOpen(false)}
				footer={null}
				width={1080}
				destroyOnClose
			>
				<div className="space-y-3">
					<div className="grid grid-cols-1 gap-3 md:grid-cols-2">
						<div className="space-y-2">
							<div className="flex items-center justify-between gap-2">
								<Label>数据集</Label>
								<Button size="sm" variant="outline" onClick={() => void loadDatasets()} disabled={datasetsLoading}>
									{datasetsLoading ? "加载中…" : "搜索"}
								</Button>
							</div>
							<Input value={datasetKeyword} onChange={(e) => setDatasetKeyword(e.target.value)} placeholder="搜索数据集" />
							<AntSelect
								showSearch
								allowClear
								value={datasetId || undefined}
								placeholder="选择数据集"
								filterOption={false}
								onChange={(v) => {
									setDatasetId(v ? String(v) : "");
									setTableId("");
									setTables([]);
								}}
								onSearch={() => void loadDatasets()}
								notFoundContent={datasetsLoading ? <Spin size="small" /> : null}
								options={datasets.map((d) => ({ value: d.id, label: `${d.name}${d.ownerDept ? ` · ${d.ownerDept}` : ""}` }))}
								style={{ width: "100%" }}
							/>
						</div>

						<div className="space-y-2">
							<div className="flex items-center justify-between gap-2">
								<Label>数据表</Label>
								<Button size="sm" variant="outline" onClick={() => void loadTables()} disabled={!datasetId || tablesLoading}>
									{tablesLoading ? "加载中…" : "搜索"}
								</Button>
							</div>
							<Input value={tableKeyword} onChange={(e) => setTableKeyword(e.target.value)} placeholder="搜索表（可选）" disabled={!datasetId} />
							<AntSelect
								showSearch
								allowClear
								value={tableId || undefined}
								placeholder={datasetId ? "选择数据表" : "请先选择数据集"}
								filterOption={false}
								onChange={(v) => setTableId(v ? String(v) : "")}
								onSearch={() => void loadTables()}
								notFoundContent={tablesLoading ? <Spin size="small" /> : null}
								options={tables.map((t) => ({ value: t.id, label: t.name }))}
								style={{ width: "100%" }}
								disabled={!datasetId}
							/>
						</div>
					</div>

					<div className="flex items-center justify-between gap-2">
						<div className="text-xs text-muted-foreground">
							说明：校验基于“字段模板”内容（支持 CSV/JSON/纯文本）。建议先在元数据页完成字段采集与标准绑定。
						</div>
						<Button onClick={() => void runValidate()} disabled={!tableId || validateLoading}>
							{validateLoading ? "校验中…" : "执行校验"}
						</Button>
					</div>

					{validateResult?.summary ? (
						<div className="grid grid-cols-2 gap-2 text-sm md:grid-cols-6">
							<div>必备字段：{Number(validateResult?.summary?.requiredFields ?? 0)}</div>
							<div>缺字段：{Number(validateResult?.summary?.missingFields ?? 0)}</div>
							<div>类型不符：{Number(validateResult?.summary?.typeMismatches ?? 0)}</div>
							<div>可空不符：{Number(validateResult?.summary?.nullableMismatches ?? 0)}</div>
							<div>未绑定标准：{Number(validateResult?.summary?.unmappedStandards ?? 0)}</div>
							<div>标准不符：{Number(validateResult?.summary?.standardMismatches ?? 0)}</div>
						</div>
					) : null}

					<AntTable
						rowKey={(r: any) => `${r.code || ""}:${r.columnName || ""}:${r.message || ""}`}
						size="small"
						pagination={{ pageSize: 10, showSizeChanger: false }}
						dataSource={Array.isArray(validateResult?.issues) ? validateResult.issues : []}
						locale={{ emptyText: validateLoading ? <Spin size="small" /> : "暂无校验结果" }}
						columns={[
							{
								title: "级别",
								dataIndex: "severity",
								key: "severity",
								width: 90,
								render: (v: any) => {
									const s = String(v || "").toUpperCase();
									if (s === "ERROR") return <Badge variant="destructive">ERROR</Badge>;
									if (s === "WARN") return <Badge variant="outline">WARN</Badge>;
									return <Badge variant="secondary">INFO</Badge>;
								},
							},
							{ title: "字段", dataIndex: "columnName", key: "columnName", width: 180, ellipsis: true },
							{ title: "问题", dataIndex: "message", key: "message", ellipsis: true },
							{ title: "期望", dataIndex: "expected", key: "expected", width: 220, ellipsis: true },
							{ title: "实际", dataIndex: "actual", key: "actual", width: 220, ellipsis: true },
							{ title: "代码", dataIndex: "code", key: "code", width: 160, ellipsis: true },
						]}
					/>
				</div>
			</Modal>
		</div>
	);
}
