import { useCallback, useEffect, useMemo, useState } from "react";
import { Modal, Select as AntSelect, Spin, Table as AntTable } from "antd";
import { toast } from "sonner";
import {
	getDataset,
	getDatasetJob,
	getCatalogSyncStatus,
	applyAutoMapTableStandardMapping,
	listColumnsByTable,
	listDatasets,
	listDatasetJobs,
	listStandards,
	listTablesByDataset,
	previewAutoMapTableStandardMapping,
	syncDatasetSchema,
	triggerCatalogSync,
	updateColumnSchema,
	updateTableSchema,
	validateTableStandardMapping,
} from "@/api/platformApi";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";

type DatasetOption = { id: string; name: string; ownerDept?: string | null; classification?: string | null };
type TableRow = { id: string; name: string; owner?: string | null; classification?: string | null; bizDomain?: string | null; tags?: string | null };
type ColumnRow = {
	id: string;
	name: string;
	dataType?: string | null;
	nullable?: boolean | null;
	tags?: string | null;
	sensitiveTags?: string | null;
	comment?: string | null;
	standardId?: string | null;
	standardRule?: string | null;
	standardMismatchReason?: string | null;
	standardCode?: string | null;
	standardName?: string | null;
	standardDataType?: string | null;
	standardNullable?: boolean | null;
	standardCodeSet?: string | null;
	computedMismatchReason?: string | null;
	mappingStatus?: string | null;
};

type StandardOption = {
	id: string;
	code: string;
	name: string;
	dataType?: string | null;
	nullable?: boolean | null;
	codeSet?: string | null;
};

const PAGE_SIZE = 200;

export default function MetadataPage() {
	const [fullSyncLoading, setFullSyncLoading] = useState(false);
	const [fullSyncStatusLoading, setFullSyncStatusLoading] = useState(false);
	const [fullSyncStatus, setFullSyncStatus] = useState<any | null>(null);
	const [fullSyncIncludePrimary, setFullSyncIncludePrimary] = useState(true);
	const [fullSyncIncludeJdbc, setFullSyncIncludeJdbc] = useState(true);

	const [datasetKeyword, setDatasetKeyword] = useState("");
	const [datasetsLoading, setDatasetsLoading] = useState(false);
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [datasetId, setDatasetId] = useState<string>("");
	const [datasetDetail, setDatasetDetail] = useState<any | null>(null);

	const [tableKeyword, setTableKeyword] = useState("");
	const [tablesLoading, setTablesLoading] = useState(false);
	const [tables, setTables] = useState<TableRow[]>([]);
	const [selectedTableId, setSelectedTableId] = useState<string>("");

	const [columnKeyword, setColumnKeyword] = useState("");
	const [columnsLoading, setColumnsLoading] = useState(false);
	const [columns, setColumns] = useState<ColumnRow[]>([]);

	const [syncing, setSyncing] = useState(false);
	const [jobs, setJobs] = useState<any[]>([]);
	const [jobsLoading, setJobsLoading] = useState(false);

	const [tableDialogOpen, setTableDialogOpen] = useState(false);
	const [tableForm, setTableForm] = useState<{ id: string; name: string; owner: string; classification: string; bizDomain: string; tags: string }>({
		id: "",
		name: "",
		owner: "",
		classification: "",
		bizDomain: "",
		tags: "",
	});
	const [savingTable, setSavingTable] = useState(false);

	const [colDialogOpen, setColDialogOpen] = useState(false);
	const [colForm, setColForm] = useState<{ id: string; comment: string; tags: string; sensitiveTags: string; standardId: string }>({
		id: "",
		comment: "",
		tags: "",
		sensitiveTags: "",
		standardId: "",
	});
	const [savingCol, setSavingCol] = useState(false);
	const [standardOptions, setStandardOptions] = useState<StandardOption[]>([]);
	const [standardsLoading, setStandardsLoading] = useState(false);

	const [validateOpen, setValidateOpen] = useState(false);
	const [validating, setValidating] = useState(false);
	const [validationResult, setValidationResult] = useState<any | null>(null);

	const [autoMapOpen, setAutoMapOpen] = useState(false);
	const [autoMapLoading, setAutoMapLoading] = useState(false);
	const [autoMapApplying, setAutoMapApplying] = useState(false);
	const [autoMapPreview, setAutoMapPreview] = useState<any | null>(null);
	const [autoMapItems, setAutoMapItems] = useState<any[]>([]);
	const [autoMapOverwrite, setAutoMapOverwrite] = useState(false);
	const [autoMapOnlyUnmapped, setAutoMapOnlyUnmapped] = useState(true);

	const selectedTable = useMemo(() => tables.find((t) => t.id === selectedTableId) || null, [selectedTableId, tables]);
	const standardSelectOptions = useMemo(
		() =>
			standardOptions.map((s) => ({
				value: s.id,
				label: `${s.code}${s.name ? ` · ${s.name}` : ""}`,
			})),
		[standardOptions],
	);

	const loadStandardOptions = useCallback(async (keyword?: string) => {
		setStandardsLoading(true);
		try {
			const resp = (await listStandards({ page: 0, size: 20, keyword: keyword?.trim() || undefined })) as any;
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setStandardOptions(
				content.map((s: any) => ({
					id: String(s.id),
					code: String(s.code || ""),
					name: String(s.name || ""),
					dataType: s.dataType ?? null,
					nullable: s.nullable ?? null,
					codeSet: s.codeSet ?? null,
				})),
			);
		} catch (e) {
			console.error(e);
			setStandardOptions([]);
		} finally {
			setStandardsLoading(false);
		}
	}, []);

	const loadFullSyncStatus = useCallback(async () => {
		setFullSyncStatusLoading(true);
		try {
			const resp = (await getCatalogSyncStatus()) as any;
			setFullSyncStatus(resp || null);
		} catch (e: any) {
			console.error(e);
			setFullSyncStatus(null);
			toast.error(e?.message || "加载采集状态失败");
		} finally {
			setFullSyncStatusLoading(false);
		}
	}, []);

	const triggerFullSync = useCallback(async () => {
		if (!fullSyncIncludePrimary && !fullSyncIncludeJdbc) {
			toast.error("请至少选择一种采集范围（主数据源 / JDBC）");
			return;
		}
		setFullSyncLoading(true);
		try {
			await triggerCatalogSync({
				includePrimary: fullSyncIncludePrimary,
				includeJdbc: fullSyncIncludeJdbc,
				reason: "ui:metadata",
			});
			toast.success("已触发全量采集（异步执行）");
			await loadFullSyncStatus();
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "触发采集失败");
		} finally {
			setFullSyncLoading(false);
		}
	}, [fullSyncIncludeJdbc, fullSyncIncludePrimary, loadFullSyncStatus]);

	const loadDatasets = useCallback(async () => {
		setDatasetsLoading(true);
		try {
			const resp = (await listDatasets({ page: 0, size: PAGE_SIZE, keyword: datasetKeyword.trim() })) as any;
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(
				content.map((d: any) => ({
					id: String(d.id),
					name: String(d.name || d.id),
					ownerDept: d.ownerDept,
					classification: d.classification,
				})),
			);
		} catch (e) {
			console.error(e);
			toast.error("加载数据集失败");
		} finally {
			setDatasetsLoading(false);
		}
	}, [datasetKeyword]);

	const loadDatasetDetail = useCallback(async () => {
		if (!datasetId) {
			setDatasetDetail(null);
			return;
		}
		try {
			const resp = (await getDataset(datasetId)) as any;
			setDatasetDetail(resp || null);
		} catch (e) {
			console.error(e);
			setDatasetDetail(null);
		}
	}, [datasetId]);

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
					owner: t.owner,
					classification: t.classification,
					bizDomain: t.bizDomain,
					tags: t.tags,
				})),
			);
		} catch (e) {
			console.error(e);
			toast.error("加载表失败");
		} finally {
			setTablesLoading(false);
		}
	}, [datasetId, tableKeyword]);

	const loadColumns = useCallback(async () => {
		if (!selectedTableId) {
			setColumns([]);
			return;
		}
		setColumnsLoading(true);
		try {
			const resp = (await listColumnsByTable(selectedTableId, columnKeyword.trim() || undefined)) as any;
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setColumns(
				content.map((c: any) => ({
					id: String(c.id),
					name: String(c.name || c.id),
					dataType: c.dataType,
					nullable: c.nullable,
					tags: c.tags,
					sensitiveTags: c.sensitiveTags,
					comment: c.comment,
					standardId: c.standardId ?? null,
					standardRule: c.standardRule ?? null,
					standardMismatchReason: c.standardMismatchReason ?? null,
					standardCode: c.standardCode ?? null,
					standardName: c.standardName ?? null,
					standardDataType: c.standardDataType ?? null,
					standardNullable: c.standardNullable ?? null,
					standardCodeSet: c.standardCodeSet ?? null,
					computedMismatchReason: c.computedMismatchReason ?? null,
					mappingStatus: c.mappingStatus ?? null,
				})),
			);
		} catch (e) {
			console.error(e);
			toast.error("加载字段失败");
		} finally {
			setColumnsLoading(false);
		}
	}, [columnKeyword, selectedTableId]);

	const loadJobs = useCallback(async () => {
		if (!datasetId) {
			setJobs([]);
			return;
		}
		setJobsLoading(true);
		try {
			const resp = (await listDatasetJobs(datasetId)) as any;
			setJobs(Array.isArray(resp) ? resp : []);
		} catch (e) {
			console.error(e);
			setJobs([]);
		} finally {
			setJobsLoading(false);
		}
	}, [datasetId]);

	useEffect(() => {
		void loadDatasets();
	}, [loadDatasets]);

	useEffect(() => {
		void loadFullSyncStatus();
	}, [loadFullSyncStatus]);

	useEffect(() => {
		setSelectedTableId("");
		void loadDatasetDetail();
		void loadTables();
		void loadJobs();
	}, [datasetId, loadDatasetDetail, loadJobs, loadTables]);

	useEffect(() => {
		void loadTables();
	}, [loadTables]);

	useEffect(() => {
		void loadColumns();
	}, [loadColumns]);

	const triggerSync = useCallback(async () => {
		if (!datasetId) return;
		setSyncing(true);
		try {
			const resp = (await syncDatasetSchema(datasetId, {})) as any;
			const job = resp?.job;
			const jobId = job?.id ? String(job.id) : "";
			toast.success(jobId ? `已提交同步任务：${jobId}` : "已提交同步任务");
			if (jobId) {
				try {
					const latest = (await getDatasetJob(jobId)) as any;
					void latest;
				} catch {
					// ignore
				}
			}
			await loadTables();
			await loadJobs();
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "提交同步任务失败");
		} finally {
			setSyncing(false);
		}
	}, [datasetId, loadJobs, loadTables]);

	const openEditTable = useCallback(
		(row: TableRow) => {
			setTableForm({
				id: row.id,
				name: row.name,
				owner: String(row.owner || ""),
				classification: String(row.classification || ""),
				bizDomain: String(row.bizDomain || ""),
				tags: String(row.tags || ""),
			});
			setTableDialogOpen(true);
		},
		[],
	);

	const saveTable = useCallback(async () => {
		if (!tableForm.id) return;
		setSavingTable(true);
		try {
			const payload = {
				id: tableForm.id,
				name: tableForm.name,
				owner: tableForm.owner.trim() || null,
				classification: tableForm.classification.trim() || null,
				bizDomain: tableForm.bizDomain.trim() || null,
				tags: tableForm.tags.trim() || null,
			};
			const saved = (await updateTableSchema(tableForm.id, payload)) as any;
			toast.success("已保存表元数据");
			setTables((prev) => prev.map((t) => (t.id === tableForm.id ? { ...t, ...saved } : t)));
			setTableDialogOpen(false);
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "保存失败");
		} finally {
			setSavingTable(false);
		}
	}, [tableForm]);

	const openEditColumn = useCallback((row: ColumnRow) => {
		if (row.standardId && row.standardCode) {
			setStandardOptions((prev) => {
				if (prev.some((s) => s.id === row.standardId)) return prev;
				return [
					{
						id: row.standardId as string,
						code: String(row.standardCode || ""),
						name: String(row.standardName || ""),
						dataType: row.standardDataType ?? null,
						nullable: row.standardNullable ?? null,
						codeSet: row.standardCodeSet ?? null,
					},
					...prev,
				];
			});
		}
		setColForm({
			id: row.id,
			comment: String(row.comment || ""),
			tags: String(row.tags || ""),
			sensitiveTags: String(row.sensitiveTags || ""),
			standardId: String(row.standardId || ""),
		});
		setColDialogOpen(true);
	}, []);

	const saveColumn = useCallback(async () => {
		if (!colForm.id) return;
		setSavingCol(true);
		try {
			const existing = columns.find((c) => c.id === colForm.id);
			if (!existing?.name || !existing?.dataType) {
				toast.error("字段结构信息缺失，请先刷新字段列表");
				return;
			}
			const payload = {
				id: colForm.id,
				name: existing?.name,
				dataType: existing?.dataType,
				nullable: existing?.nullable !== false,
				tags: colForm.tags.trim() || null,
				sensitiveTags: colForm.sensitiveTags.trim() || null,
				comment: colForm.comment.trim() || null,
				standardId: colForm.standardId.trim() || null,
			};
			const saved = (await updateColumnSchema(colForm.id, payload)) as any;
			toast.success("已保存字段元数据");
			setColumns((prev) => prev.map((c) => (c.id === colForm.id ? { ...c, ...saved } : c)));
			setColDialogOpen(false);
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "保存失败");
		} finally {
			setSavingCol(false);
		}
	}, [colForm, columns]);

	const runValidation = useCallback(async () => {
		if (!selectedTableId) return;
		setValidating(true);
		try {
			const res = (await validateTableStandardMapping(selectedTableId)) as any;
			setValidationResult(res || null);
			setValidateOpen(true);
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "校验失败");
			setValidationResult(null);
			setValidateOpen(false);
		} finally {
			setValidating(false);
		}
	}, [selectedTableId]);

	const refreshAutoMapPreview = useCallback(async () => {
		if (!selectedTableId) return;
		setAutoMapLoading(true);
		try {
			const res = (await previewAutoMapTableStandardMapping(selectedTableId, {
				overwrite: autoMapOverwrite,
				onlyUnmapped: autoMapOnlyUnmapped,
			})) as any;
			setAutoMapPreview(res || null);
			setAutoMapItems(Array.isArray(res?.items) ? res.items : []);
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "刷新自动匹配预览失败");
			setAutoMapPreview(null);
			setAutoMapItems([]);
		} finally {
			setAutoMapLoading(false);
		}
	}, [autoMapOnlyUnmapped, autoMapOverwrite, selectedTableId]);

	const openAutoMap = useCallback(async () => {
		if (!selectedTableId) return;
		setAutoMapOpen(true);
		await refreshAutoMapPreview();
	}, [refreshAutoMapPreview, selectedTableId]);

	const applyAutoMap = useCallback(async () => {
		if (!selectedTableId) return;
		setAutoMapApplying(true);
		try {
			const res = (await applyAutoMapTableStandardMapping(selectedTableId, {
				overwrite: autoMapOverwrite,
				onlyUnmapped: autoMapOnlyUnmapped,
			})) as any;
			toast.success(`自动匹配完成：应用 ${Number(res?.applied ?? 0)} 条，冲突 ${Number(res?.conflicts ?? 0)} 条`);
			setAutoMapOpen(false);
			await loadColumns();
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "自动匹配失败");
		} finally {
			setAutoMapApplying(false);
		}
	}, [autoMapOnlyUnmapped, autoMapOverwrite, loadColumns, selectedTableId]);

	const mappingCounts = useMemo(() => {
		const counts = { OK: 0, MISMATCHED: 0, UNMAPPED: 0, STANDARD_MISSING: 0, UNKNOWN: 0 };
		for (const c of columns) {
			const s = String(c.mappingStatus || "UNKNOWN");
			if ((counts as any)[s] != null) {
				(counts as any)[s] += 1;
			} else {
				counts.UNKNOWN += 1;
			}
		}
		return counts;
	}, [columns]);

	const validationIssues = useMemo(() => {
		const issues = validationResult?.issues;
		return Array.isArray(issues) ? issues : [];
	}, [validationResult]);

	return (
		<div className="flex flex-col gap-6">
			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<CardTitle>自动采集（全量）</CardTitle>
					<div className="flex flex-wrap items-center gap-2">
						<Button variant="outline" onClick={loadFullSyncStatus} disabled={fullSyncStatusLoading}>
							{fullSyncStatusLoading ? "加载中…" : "刷新状态"}
						</Button>
						<Button onClick={triggerFullSync} disabled={fullSyncLoading}>
							{fullSyncLoading ? "触发中…" : "触发全量采集"}
						</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="flex flex-wrap gap-4 text-sm">
						<label className="flex items-center gap-2">
							<input
								type="checkbox"
								checked={fullSyncIncludePrimary}
								onChange={(e) => setFullSyncIncludePrimary(e.target.checked)}
							/>
							主数据源（Inceptor/内置回退）
						</label>
						<label className="flex items-center gap-2">
							<input
								type="checkbox"
								checked={fullSyncIncludeJdbc}
								onChange={(e) => setFullSyncIncludeJdbc(e.target.checked)}
							/>
							JDBC 多源采集
						</label>
					</div>
					<div className="rounded-md border p-3 text-sm">
						<div className="mb-2 text-xs text-muted-foreground">
							说明：全量采集会扫描 `infra_data_source`（ACTIVE 且配置 jdbcUrl）并自动补齐数据集/表/字段；视图会尝试生成 AUTO_VIEW 血缘。
						</div>
						{fullSyncStatus ? (
							<div className="grid gap-2 md:grid-cols-2">
								<div>
									<div className="font-medium mb-1">主数据源</div>
									<div className="text-xs text-muted-foreground">
										进行中：{String(fullSyncStatus?.primary?.inProgress ?? "-")}
									</div>
									<div className="text-xs text-muted-foreground">
										最近：{String(fullSyncStatus?.primary?.last?.timestamp ?? "-")}
									</div>
									<div className="text-xs text-muted-foreground">
										动作：{Array.isArray(fullSyncStatus?.primary?.last?.actions) ? fullSyncStatus.primary.last.actions.join("；") : "-"}
									</div>
									{fullSyncStatus?.primary?.last?.error ? (
										<div className="text-xs text-destructive">错误：{String(fullSyncStatus.primary.last.error)}</div>
									) : null}
								</div>
								<div>
									<div className="font-medium mb-1">JDBC 多源</div>
									<div className="text-xs text-muted-foreground">
										进行中：{String(fullSyncStatus?.jdbc?.inProgress ?? "-")}
									</div>
									<div className="text-xs text-muted-foreground">
										最近：{String(fullSyncStatus?.jdbc?.last?.timestamp ?? "-")}
									</div>
									<div className="text-xs text-muted-foreground">
										结果：{Array.isArray(fullSyncStatus?.jdbc?.last?.results) ? `sources=${fullSyncStatus.jdbc.last.results.length}` : "-"}
									</div>
									{fullSyncStatus?.jdbc?.last?.error ? (
										<div className="text-xs text-destructive">错误：{String(fullSyncStatus.jdbc.last.error)}</div>
									) : null}
								</div>
							</div>
						) : (
							<div className="text-xs text-muted-foreground">暂无状态</div>
						)}
					</div>
				</CardContent>
			</Card>

			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
					<CardTitle>元数据采集与维护</CardTitle>
					<div className="flex flex-col gap-2 md:flex-row md:items-center">
						<Input
							value={datasetKeyword}
							onChange={(e) => setDatasetKeyword(e.target.value)}
							placeholder="搜索数据集"
							className="w-full md:w-64"
						/>
						<Button variant="secondary" onClick={loadDatasets} disabled={datasetsLoading}>
							{datasetsLoading ? "加载中…" : "搜索"}
						</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-4">
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label>选择数据集</Label>
							<Select value={datasetId || "__NONE__"} onValueChange={(v) => setDatasetId(v === "__NONE__" ? "" : v)}>
								<SelectTrigger>
									<SelectValue placeholder="请选择…" />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="__NONE__">（未选择）</SelectItem>
									{datasets.map((d) => (
										<SelectItem key={d.id} value={d.id}>
											{d.name}
											<span className="text-xs text-muted-foreground">{d.ownerDept ? ` · ${d.ownerDept}` : ""}</span>
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>操作</Label>
							<div className="flex flex-wrap gap-2">
								<Button onClick={triggerSync} disabled={!datasetId || syncing}>
									{syncing ? "同步中…" : "同步元数据"}
								</Button>
								<Button variant="outline" onClick={loadTables} disabled={!datasetId || tablesLoading}>
									刷新表
								</Button>
								<Button variant="outline" onClick={loadJobs} disabled={!datasetId || jobsLoading}>
									刷新任务
								</Button>
							</div>
							<div className="text-xs text-muted-foreground">
								同步会写入 `catalog_table_schema` / `catalog_column_schema`；全量采集用于“多源自动发现”，数据集同步用于“指定数据集补齐字段”。
							</div>
						</div>
					</div>

					{datasetDetail ? (
						<div className="flex flex-wrap gap-2">
							<Badge variant="outline">{String(datasetDetail.classification || "-")}</Badge>
							<Badge variant="secondary">{datasetDetail.ownerDept ? `部门:${datasetDetail.ownerDept}` : "部门:未指定"}</Badge>
							<Badge variant="secondary">{datasetDetail.type ? `来源:${datasetDetail.type}` : "来源:-"}</Badge>
						</div>
					) : null}
				</CardContent>
			</Card>

			<div className="grid grid-cols-1 gap-4 lg:grid-cols-[360px_1fr]">
				<Card>
					<CardHeader>
						<CardTitle className="text-base">表</CardTitle>
					</CardHeader>
					<CardContent className="space-y-3">
						<Input value={tableKeyword} onChange={(e) => setTableKeyword(e.target.value)} placeholder="表名/标签搜索" disabled={!datasetId} />
						<ScrollArea className="h-[560px] pr-2">
							<div className="space-y-2">
								{tables.map((t) => {
									const active = t.id === selectedTableId;
									return (
										<button
											key={t.id}
											type="button"
											onClick={() => setSelectedTableId(t.id)}
											className={[
												"w-full rounded-md border px-3 py-2 text-left",
												active ? "border-primary bg-primary/5" : "border-border hover:bg-muted/30",
											].join(" ")}
										>
											<div className="flex items-center justify-between gap-2">
												<div className="min-w-0">
													<div className="truncate text-sm font-medium">{t.name}</div>
													<div className="truncate text-xs text-muted-foreground">
														{t.tags ? `tags: ${t.tags}` : "tags: -"}
													</div>
												</div>
												<Button
													size="sm"
													variant="outline"
													onClick={(e) => {
														e.preventDefault();
														e.stopPropagation();
														openEditTable(t);
													}}
												>
													编辑
												</Button>
											</div>
										</button>
									);
								})}
								{!tablesLoading && datasetId && tables.length === 0 ? (
									<div className="py-8 text-center text-sm text-muted-foreground">暂无表（可尝试“同步元数据”）</div>
								) : null}
								{tablesLoading ? <div className="py-8 text-center text-sm text-muted-foreground">加载中…</div> : null}
							</div>
						</ScrollArea>
					</CardContent>
				</Card>

				<Card>
					<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
						<CardTitle className="text-base">字段</CardTitle>
						<div className="flex items-center gap-2">
							<Input
								value={columnKeyword}
								onChange={(e) => setColumnKeyword(e.target.value)}
								placeholder="字段/注释/敏感标签搜索"
								disabled={!selectedTableId}
								className="w-64"
							/>
							<Button variant="outline" onClick={loadColumns} disabled={!selectedTableId || columnsLoading}>
								刷新
							</Button>
							<Button variant="outline" onClick={() => void openAutoMap()} disabled={!selectedTableId || autoMapLoading}>
								{autoMapLoading ? "加载中…" : "自动匹配"}
							</Button>
							<Button variant="outline" onClick={runValidation} disabled={!selectedTableId || validating}>
								{validating ? "校验中…" : "校验映射"}
							</Button>
						</div>
					</CardHeader>
					<CardContent className="space-y-3">
						{selectedTable ? (
							<div className="flex flex-wrap items-center gap-2 text-sm">
								<span className="font-medium">{selectedTable.name}</span>
								{selectedTable.classification ? <Badge variant="outline">{selectedTable.classification}</Badge> : null}
								{selectedTable.bizDomain ? <Badge variant="secondary">{selectedTable.bizDomain}</Badge> : null}
							</div>
						) : (
							<div className="text-sm text-muted-foreground">请选择左侧表</div>
						)}

						{selectedTableId ? (
							<div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
								<Badge variant="secondary">OK: {mappingCounts.OK}</Badge>
								<Badge variant="outline">不一致: {mappingCounts.MISMATCHED}</Badge>
								<Badge variant="outline">未映射: {mappingCounts.UNMAPPED}</Badge>
								{mappingCounts.STANDARD_MISSING ? <Badge variant="outline">标准缺失: {mappingCounts.STANDARD_MISSING}</Badge> : null}
							</div>
						) : null}

						<AntTable
							rowKey={(r: any) => String(r.id)}
							size="middle"
							loading={columnsLoading}
							pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (total) => `总计 ${total} 条` }}
							scroll={{ x: 1200 }}
							dataSource={columns}
							locale={{ emptyText: selectedTableId ? "暂无字段" : "请选择左侧表" }}
							columns={[
								{
									title: "字段",
									dataIndex: "name",
									key: "name",
									width: 180,
									ellipsis: true,
									render: (v: any) => <span className="font-mono">{String(v || "-")}</span>,
								},
								{ title: "类型", dataIndex: "dataType", key: "dataType", width: 120, ellipsis: true },
								{
									title: "映射状态",
									dataIndex: "mappingStatus",
									key: "mappingStatus",
									width: 110,
									filters: [
										{ text: "OK", value: "OK" },
										{ text: "不一致", value: "MISMATCHED" },
										{ text: "未映射", value: "UNMAPPED" },
										{ text: "标准缺失", value: "STANDARD_MISSING" },
									],
									onFilter: (value: any, record: any) => String(record?.mappingStatus || "") === String(value),
									render: (_: any, r: any) => {
										const s = String(r?.mappingStatus || "UNKNOWN");
										if (s === "OK") return <Badge variant="secondary">OK</Badge>;
										if (s === "MISMATCHED") return <Badge variant="outline">不一致</Badge>;
										if (s === "UNMAPPED") return <Badge variant="outline">未映射</Badge>;
										if (s === "STANDARD_MISSING") return <Badge variant="destructive">缺失</Badge>;
										return <Badge variant="outline">-</Badge>;
									},
								},
								{
									title: "数据元",
									key: "standard",
									width: 240,
									ellipsis: true,
									render: (_: any, r: any) =>
										r.standardCode ? `${r.standardCode}${r.standardName ? ` · ${r.standardName}` : ""}` : "-",
								},
								{
									title: "不一致原因",
									key: "mismatch",
									width: 280,
									ellipsis: true,
									render: (_: any, r: any) => {
										const reason = String(r.computedMismatchReason || r.standardMismatchReason || "").trim();
										return reason || "-";
									},
								},
								{ title: "敏感标签", dataIndex: "sensitiveTags", key: "sensitiveTags", width: 180, ellipsis: true },
								{
									title: "注释",
									dataIndex: "comment",
									key: "comment",
									ellipsis: true,
									render: (v: any) => String(v || "-"),
								},
								{
									title: "操作",
									key: "action",
									fixed: "right",
									width: 90,
									render: (_: any, r: any) => (
										<Button size="sm" variant="outline" onClick={() => openEditColumn(r)}>
											编辑
										</Button>
									),
								},
							]}
						/>

						<Card>
							<CardHeader>
								<CardTitle className="text-base">采集任务</CardTitle>
							</CardHeader>
							<CardContent>
								<div className="text-xs text-muted-foreground mb-2">最近同步任务（用于追溯采集过程）</div>
								<div className="overflow-auto rounded-md border">
									<table className="min-w-full text-sm">
										<thead className="text-left text-muted-foreground border-b">
											<tr>
												<th className="py-2 px-3 font-medium">时间</th>
												<th className="py-2 px-3 font-medium">状态</th>
												<th className="py-2 px-3 font-medium">信息</th>
											</tr>
										</thead>
										<tbody>
											{jobs.map((j) => (
												<tr key={String(j.id)} className="border-b last:border-none">
													<td className="py-2 px-3 text-muted-foreground">{String(j.createdDate || j.startedAt || "-")}</td>
													<td className="py-2 px-3">
														<Badge variant={String(j.status || "").toUpperCase() === "SUCCESS" ? "default" : "secondary"}>
															{String(j.status || "-")}
														</Badge>
													</td>
													<td className="py-2 px-3 text-muted-foreground max-w-[420px] truncate" title={String(j.message || "")}>
														{String(j.message || "-")}
													</td>
												</tr>
											))}
											{jobsLoading ? (
												<tr>
													<td colSpan={3} className="py-8 text-center text-muted-foreground">
														加载中…
													</td>
												</tr>
											) : null}
											{!jobsLoading && datasetId && jobs.length === 0 ? (
												<tr>
													<td colSpan={3} className="py-8 text-center text-muted-foreground">
														暂无任务
													</td>
												</tr>
											) : null}
										</tbody>
									</table>
								</div>
							</CardContent>
						</Card>
					</CardContent>
				</Card>
			</div>

			<Dialog open={tableDialogOpen} onOpenChange={setTableDialogOpen}>
				<DialogContent className="max-w-xl">
					<DialogHeader>
						<DialogTitle>编辑表元数据</DialogTitle>
					</DialogHeader>
					<div className="grid gap-4">
						<div className="space-y-2">
							<Label>负责人</Label>
							<Input value={tableForm.owner} onChange={(e) => setTableForm((p) => ({ ...p, owner: e.target.value }))} />
						</div>
						<div className="space-y-2">
							<Label>密级（可空，继承数据集）</Label>
							<Input
								value={tableForm.classification}
								onChange={(e) => setTableForm((p) => ({ ...p, classification: e.target.value }))}
								placeholder="例如：INTERNAL / SECRET"
							/>
						</div>
						<div className="space-y-2">
							<Label>业务域</Label>
							<Input value={tableForm.bizDomain} onChange={(e) => setTableForm((p) => ({ ...p, bizDomain: e.target.value }))} />
						</div>
						<div className="space-y-2">
							<Label>标签（逗号分隔）</Label>
							<Input value={tableForm.tags} onChange={(e) => setTableForm((p) => ({ ...p, tags: e.target.value }))} />
						</div>
					</div>
					<DialogFooter className="mt-4">
						<Button variant="outline" onClick={() => setTableDialogOpen(false)}>
							取消
						</Button>
						<Button onClick={saveTable} disabled={savingTable}>
							{savingTable ? "保存中…" : "保存"}
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>

			<Dialog open={colDialogOpen} onOpenChange={setColDialogOpen}>
				<DialogContent className="max-w-2xl">
					<DialogHeader>
						<DialogTitle>编辑字段元数据</DialogTitle>
					</DialogHeader>
					<div className="grid gap-4">
						<div className="space-y-2">
							<Label>关联数据元（字段标准）</Label>
							<AntSelect
								allowClear
								showSearch
								value={colForm.standardId || undefined}
								placeholder="输入编码/名称搜索数据标准"
								filterOption={false}
								onDropdownVisibleChange={(open) => {
									if (open && standardOptions.length === 0) {
										void loadStandardOptions("");
									}
								}}
								onSearch={(value) => void loadStandardOptions(value)}
								onChange={(value) => setColForm((p) => ({ ...p, standardId: value ? String(value) : "" }))}
								options={standardSelectOptions}
								notFoundContent={standardsLoading ? <Spin size="small" /> : null}
								style={{ width: "100%" }}
							/>
							<div className="text-xs text-muted-foreground">
								建议：先在“数据标准台账”中补齐 `数据类型/可空/码表`，再在此绑定到字段。
							</div>
						</div>
						<div className="space-y-2">
							<Label>敏感标签（例如：PII:phone）</Label>
							<Input
								value={colForm.sensitiveTags}
								onChange={(e) => setColForm((p) => ({ ...p, sensitiveTags: e.target.value }))}
								placeholder="逗号分隔"
							/>
						</div>
						<div className="space-y-2">
							<Label>标签</Label>
							<Input value={colForm.tags} onChange={(e) => setColForm((p) => ({ ...p, tags: e.target.value }))} placeholder="逗号分隔" />
						</div>
						<div className="space-y-2">
							<Label>注释/口径说明</Label>
							<Textarea
								value={colForm.comment}
								onChange={(e) => setColForm((p) => ({ ...p, comment: e.target.value }))}
								className="min-h-[140px]"
							/>
						</div>
					</div>
					<DialogFooter className="mt-4">
						<Button variant="outline" onClick={() => setColDialogOpen(false)}>
							取消
						</Button>
						<Button onClick={saveColumn} disabled={savingCol}>
							{savingCol ? "保存中…" : "保存"}
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>

			<Modal
				open={validateOpen}
				title="字段 ↔ 数据元 映射校验"
				onCancel={() => setValidateOpen(false)}
				footer={null}
				width={980}
				destroyOnClose
			>
				<div className="space-y-3">
					<div className="flex flex-wrap items-center gap-2 text-sm">
						<Badge variant="secondary">层级：{String(validationResult?.warehouseLayer || "-")}</Badge>
						{validationResult?.strictDwd ? <Badge variant="outline">DWD 强校验（仅提示）</Badge> : <Badge variant="outline">弱校验</Badge>}
						{validationResult?.blocking ? (
							<Badge variant="destructive">存在需修复项（不拦截）</Badge>
						) : (
							<Badge variant="secondary">未发现问题</Badge>
						)}
					</div>
					<div className="text-xs text-muted-foreground">说明：该校验仅用于提示数据资产质量，不会阻止发布/同步/预览。</div>
					<div className="grid grid-cols-2 gap-2 text-sm md:grid-cols-4">
						<div>总字段：{Number(validationResult?.totalColumns ?? 0)}</div>
						<div>已映射：{Number(validationResult?.mappedColumns ?? 0)}</div>
						<div>未映射：{Number(validationResult?.unmappedColumns ?? 0)}</div>
						<div>不一致：{Number(validationResult?.mismatchedColumns ?? 0)}</div>
					</div>

					<AntTable
						rowKey={(r: any) => String(r.columnId || r.columnName || Math.random())}
						size="small"
						pagination={{ pageSize: 10, showSizeChanger: false }}
						dataSource={validationIssues}
						columns={[
							{ title: "字段", dataIndex: "columnName", key: "columnName", width: 160, ellipsis: true },
							{ title: "字段类型", dataIndex: "columnDataType", key: "columnDataType", width: 120, ellipsis: true },
							{
								title: "数据元",
								key: "standard",
								width: 220,
								ellipsis: true,
								render: (_: any, r: any) => (r.standardCode ? `${r.standardCode}${r.standardName ? ` · ${r.standardName}` : ""}` : "-"),
							},
							{ title: "标准类型", dataIndex: "standardDataType", key: "standardDataType", width: 120, ellipsis: true },
							{ title: "问题", dataIndex: "reason", key: "reason", ellipsis: true },
						]}
					/>
				</div>
			</Modal>

			<Modal
				open={autoMapOpen}
				title="自动匹配字段 → 数据元（预览）"
				onCancel={() => setAutoMapOpen(false)}
				footer={null}
				width={1080}
				destroyOnClose
			>
				<div className="space-y-3">
					<div className="flex flex-wrap items-center gap-3 text-sm">
						<label className="flex items-center gap-2">
							<input type="checkbox" checked={autoMapOnlyUnmapped} onChange={(e) => setAutoMapOnlyUnmapped(e.target.checked)} />
							仅处理未绑定字段
						</label>
						<label className="flex items-center gap-2">
							<input
								type="checkbox"
								checked={autoMapOverwrite}
								onChange={(e) => setAutoMapOverwrite(e.target.checked)}
								disabled={autoMapOnlyUnmapped}
							/>
							允许覆盖已绑定字段
						</label>
						<Button variant="secondary" onClick={() => void refreshAutoMapPreview()} disabled={autoMapLoading}>
							{autoMapLoading ? "刷新中…" : "刷新预览"}
						</Button>
					</div>

					<div className="grid grid-cols-2 gap-2 text-sm md:grid-cols-5">
						<div>总字段：{Number(autoMapPreview?.totalColumns ?? 0)}</div>
						<div>可匹配：{Number(autoMapPreview?.matchedColumns ?? 0)}</div>
						<div>将更新：{Number(autoMapPreview?.willUpdateColumns ?? 0)}</div>
						<div>冲突：{Number(autoMapPreview?.conflictColumns ?? 0)}</div>
						<div>无匹配：{Number(autoMapPreview?.noMatchColumns ?? 0)}</div>
					</div>

					<div className="flex items-center justify-between gap-3">
						<div className="text-xs text-muted-foreground">
							优先规则：注释中的 `STD:CODE` / `标准:CODE` → 字段名=标准编码；若两者冲突则需要人工处理。
						</div>
						<Button
							onClick={() => void applyAutoMap()}
							disabled={autoMapApplying || Number(autoMapPreview?.willUpdateColumns ?? 0) <= 0}
						>
							{autoMapApplying ? "应用中…" : "确认应用"}
						</Button>
					</div>

					<AntTable
						rowKey={(r: any) => String(r.columnId || Math.random())}
						size="small"
						loading={autoMapLoading}
						pagination={{ pageSize: 12, showSizeChanger: false }}
						dataSource={autoMapItems}
						columns={[
							{ title: "字段", dataIndex: "columnName", key: "columnName", width: 180, ellipsis: true },
							{ title: "提示编码", dataIndex: "hintedStandardCode", key: "hintedStandardCode", width: 140, ellipsis: true },
							{
								title: "当前数据元",
								key: "current",
								width: 200,
								ellipsis: true,
								render: (_: any, r: any) => (r.currentStandardCode ? String(r.currentStandardCode) : "-"),
							},
							{
								title: "建议数据元",
								key: "proposed",
								width: 260,
								ellipsis: true,
								render: (_: any, r: any) =>
									r.proposedStandardCode ? `${r.proposedStandardCode}${r.proposedStandardName ? ` · ${r.proposedStandardName}` : ""}` : "-",
							},
							{ title: "来源", dataIndex: "source", key: "source", width: 130, ellipsis: true },
							{
								title: "状态",
								dataIndex: "status",
								key: "status",
								width: 140,
								ellipsis: true,
								render: (v: any) => {
									const s = String(v || "");
									if (s === "WILL_UPDATE") return <Badge variant="secondary">将更新</Badge>;
									if (s === "CONFLICT") return <Badge variant="destructive">冲突</Badge>;
									if (s === "NO_MATCH") return <Badge variant="outline">无匹配</Badge>;
									if (s === "ALREADY_OK") return <Badge variant="outline">已一致</Badge>;
									if (s === "SKIP_MAPPED") return <Badge variant="outline">已跳过</Badge>;
									return <Badge variant="outline">{s || "-"}</Badge>;
								},
							},
							{ title: "说明", dataIndex: "reason", key: "reason", ellipsis: true },
						]}
					/>
				</div>
			</Modal>
		</div>
	);
}
