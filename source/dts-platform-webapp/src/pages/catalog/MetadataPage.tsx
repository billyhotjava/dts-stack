import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	getDataset,
	getDatasetJob,
	listColumnsByTable,
	listDatasets,
	listDatasetJobs,
	listTablesByDataset,
	syncDatasetSchema,
	updateColumnSchema,
	updateTableSchema,
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
type ColumnRow = { id: string; name: string; dataType?: string | null; nullable?: boolean | null; tags?: string | null; sensitiveTags?: string | null; comment?: string | null };

const PAGE_SIZE = 200;

export default function MetadataPage() {
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
	const [colForm, setColForm] = useState<{ id: string; comment: string; tags: string; sensitiveTags: string }>({
		id: "",
		comment: "",
		tags: "",
		sensitiveTags: "",
	});
	const [savingCol, setSavingCol] = useState(false);

	const selectedTable = useMemo(() => tables.find((t) => t.id === selectedTableId) || null, [selectedTableId, tables]);

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
		setColForm({
			id: row.id,
			comment: String(row.comment || ""),
			tags: String(row.tags || ""),
			sensitiveTags: String(row.sensitiveTags || ""),
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

	return (
		<div className="flex flex-col gap-6">
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
								同步会写入 `catalog_table_schema` / `catalog_column_schema`，用于密级字段识别与后续查询安全策略。
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

						<div className="overflow-auto rounded-md border">
							<table className="min-w-full text-sm">
								<thead className="text-left text-muted-foreground border-b">
									<tr>
										<th className="py-2 px-3 font-medium">字段</th>
										<th className="py-2 px-3 font-medium">类型</th>
										<th className="py-2 px-3 font-medium">敏感标签</th>
										<th className="py-2 px-3 font-medium">注释</th>
										<th className="py-2 px-3 font-medium text-right">操作</th>
									</tr>
								</thead>
								<tbody>
									{columns.map((c) => (
										<tr key={c.id} className="border-b last:border-none">
											<td className="py-2 px-3 font-mono">{c.name}</td>
											<td className="py-2 px-3 text-muted-foreground">{c.dataType || "-"}</td>
											<td className="py-2 px-3 text-muted-foreground">{c.sensitiveTags || "-"}</td>
											<td className="py-2 px-3 text-muted-foreground max-w-[360px] truncate" title={String(c.comment || "")}>
												{c.comment || "-"}
											</td>
											<td className="py-2 px-3 text-right">
												<Button size="sm" variant="outline" onClick={() => openEditColumn(c)}>
													编辑
												</Button>
											</td>
										</tr>
									))}
									{columnsLoading ? (
										<tr>
											<td colSpan={5} className="py-10 text-center text-muted-foreground">
												加载中…
											</td>
										</tr>
									) : null}
									{!columnsLoading && selectedTableId && columns.length === 0 ? (
										<tr>
											<td colSpan={5} className="py-10 text-center text-muted-foreground">
												暂无字段
											</td>
										</tr>
									) : null}
								</tbody>
							</table>
						</div>

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
		</div>
	);
}
