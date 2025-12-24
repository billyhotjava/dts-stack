import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { listDatasets, listIndicators, previewQuery, updateIndicator } from "@/api/platformApi";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";

export default function IndicatorComputeRulesPage() {
	const [loading, setLoading] = useState(false);
	const [items, setItems] = useState<any[]>([]);
	const [page, setPage] = useState(0);
	const [total, setTotal] = useState(0);
	const [keyword, setKeyword] = useState("");
	const [status, setStatus] = useState<"ALL" | string>("ALL");

	const [selected, setSelected] = useState<any | null>(null);
	const [datasetId, setDatasetId] = useState("");
	const [sqlText, setSqlText] = useState("");

	const [datasetsLoading, setDatasetsLoading] = useState(false);
	const [datasets, setDatasets] = useState<any[]>([]);
	const [datasetKeyword, setDatasetKeyword] = useState("");

	const [saving, setSaving] = useState(false);
	const [previewing, setPreviewing] = useState(false);
	const [preview, setPreview] = useState<any | null>(null);

	const statusOptions = useMemo(
		() => [
			{ value: "ALL", label: "全部" },
			{ value: "DRAFT", label: "草稿" },
			{ value: "PUBLISHED", label: "已发布" },
			{ value: "DEPRECATED", label: "已废止" },
		],
		[],
	);

	const fetchIndicators = useCallback(async () => {
		setLoading(true);
		try {
			const params: any = { page, size: 10, keyword };
			if (status !== "ALL") params.status = status;
			const resp = (await listIndicators(params)) as any;
			const content = (resp && resp.content) || [];
			setItems(Array.isArray(content) ? content : []);
			setTotal(Number(resp?.total || content.length || 0));
		} catch (e) {
			console.error(e);
			toast.error("加载指标失败");
		} finally {
			setLoading(false);
		}
	}, [keyword, page, status]);

	const fetchDatasets = useCallback(async (kw: string) => {
		setDatasetsLoading(true);
		try {
			const params: any = { page: 0, size: 200, keyword: kw };
			const resp = (await listDatasets(params)) as any;
			const content = (resp && resp.content) || [];
			setDatasets(Array.isArray(content) ? content : []);
		} catch (e) {
			console.error(e);
			toast.error("加载数据集失败");
		} finally {
			setDatasetsLoading(false);
		}
	}, []);

	useEffect(() => {
		fetchIndicators();
	}, [fetchIndicators]);

	useEffect(() => {
		fetchDatasets("");
	}, [fetchDatasets]);

	const onSelectIndicator = useCallback((row: any) => {
		setSelected(row);
		setDatasetId(String(row?.datasetId || ""));
		setSqlText(String(row?.expressionSql || ""));
		setPreview(null);
	}, []);

	const isUuid = useCallback((value: string) => {
		const v = (value || "").trim();
		if (!v) return false;
		return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(v);
	}, []);

	const canSave = useMemo(() => {
		return !!selected && !saving && isUuid(datasetId) && sqlText.trim().length > 0;
	}, [datasetId, isUuid, saving, selected, sqlText]);

	const saveComputeRule = useCallback(async () => {
		if (!selected?.id) return;
		if (!isUuid(datasetId)) {
			toast.error("请先选择有效的数据集（UUID）");
			return;
		}
		if (!sqlText.trim()) {
			toast.error("请先填写 SQL");
			return;
		}
		setSaving(true);
		try {
			const payload: any = {
				code: selected.code,
				name: selected.name,
				category: selected.category,
				definition: selected.definition,
				expressionSql: sqlText,
				datasetId: datasetId,
				owner: selected.owner,
				ownerDept: selected.ownerDept,
				dataLevel: selected.dataLevel,
				status: selected.status,
				version: selected.version,
				versionNotes: selected.versionNotes,
				tags: selected.tags,
			};
			const updated = (await updateIndicator(String(selected.id), payload)) as any;
			toast.success("已保存计算规则");
			setSelected(updated);
			setItems((prev) => prev.map((it) => (String(it.id) === String(selected.id) ? updated : it)));
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	}, [datasetId, isUuid, selected, sqlText]);

	const runPreview = useCallback(async () => {
		const effectiveDatasetId = datasetId.trim();
		if (!isUuid(effectiveDatasetId)) {
			toast.error("请先选择有效的数据集（UUID）");
			return;
		}
		if (!sqlText.trim()) {
			toast.error("请先填写 SQL");
			return;
		}
		setPreviewing(true);
		try {
			const resp = (await previewQuery({ datasetId: effectiveDatasetId, sqlText })) as any;
			setPreview(resp || null);
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "预览失败");
		} finally {
			setPreviewing(false);
		}
	}, [datasetId, isUuid, sqlText]);

	const fillSampleSql = useCallback(async () => {
		const effectiveDatasetId = datasetId.trim();
		if (!isUuid(effectiveDatasetId)) {
			toast.error("请先选择有效的数据集（UUID）");
			return;
		}
		setPreviewing(true);
		try {
			const resp = (await previewQuery({ datasetId: effectiveDatasetId, sqlText: "" })) as any;
			const effectiveSql = String(resp?.effectiveSql || "").trim();
			if (!effectiveSql) {
				toast.info("未生成示例 SQL");
				return;
			}
			setSqlText(effectiveSql);
			setPreview(resp || null);
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "生成示例 SQL 失败");
		} finally {
			setPreviewing(false);
		}
	}, [datasetId, isUuid]);

	const previewHeaders: string[] = useMemo(() => {
		const headers = preview?.headers;
		if (Array.isArray(headers) && headers.length > 0) return headers.map((h) => String(h));
		const rows = preview?.rows;
		if (Array.isArray(rows) && rows.length > 0 && rows[0] && typeof rows[0] === "object") {
			return Object.keys(rows[0]);
		}
		return [];
	}, [preview]);

	const previewRows: any[] = useMemo(() => {
		const rows = preview?.rows;
		if (Array.isArray(rows)) return rows.slice(0, 20);
		return [];
	}, [preview]);

	const selectedStatus = String(selected?.status || "").toUpperCase();
	const statusLabel = statusOptions.find((s) => s.value === selectedStatus)?.label || (selectedStatus ? selectedStatus : "-");

	return (
		<div className="p-4">
			<div className="mb-4 flex flex-wrap items-end gap-3">
				<div className="min-w-[240px]">
					<Label className="text-xs text-muted-foreground">指标搜索</Label>
					<Input
						value={keyword}
						onChange={(e) => {
							setKeyword(e.target.value);
							setPage(0);
						}}
						placeholder="按名称/编码/分类搜索"
					/>
				</div>
				<div className="min-w-[160px]">
					<Label className="text-xs text-muted-foreground">状态</Label>
					<Select
						value={status}
						onValueChange={(v) => {
							setStatus(v);
							setPage(0);
						}}
					>
						<SelectTrigger>
							<SelectValue />
						</SelectTrigger>
						<SelectContent>
							{statusOptions.map((opt) => (
								<SelectItem key={opt.value} value={opt.value}>
									{opt.label}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</div>
				<Button variant="secondary" onClick={fetchIndicators} disabled={loading}>
					刷新
				</Button>
				<div className="ml-auto flex items-center gap-2">
					<span className="text-xs text-muted-foreground">
						{loading ? "加载中…" : `共 ${total} 条`}
					</span>
					<Button
						variant="outline"
						disabled={loading || page <= 0}
						onClick={() => setPage((p) => Math.max(0, p - 1))}
					>
						上一页
					</Button>
					<Button
						variant="outline"
						disabled={loading || (page + 1) * 10 >= total}
						onClick={() => setPage((p) => p + 1)}
					>
						下一页
					</Button>
				</div>
			</div>

			<div className="grid grid-cols-1 gap-4 lg:grid-cols-[360px_1fr]">
				<Card>
					<CardHeader>
						<CardTitle className="text-base">指标列表</CardTitle>
					</CardHeader>
					<CardContent>
						<ScrollArea className="h-[560px] pr-2">
							<div className="space-y-2">
								{items.map((it) => {
									const active = String(selected?.id || "") === String(it.id || "");
									return (
										<button
											key={String(it.id)}
											type="button"
											onClick={() => onSelectIndicator(it)}
											className={[
												"w-full rounded-md border px-3 py-2 text-left",
												active ? "border-primary bg-primary/5" : "border-border hover:bg-muted/30",
											].join(" ")}
										>
											<div className="flex items-center justify-between gap-2">
												<div className="min-w-0">
													<div className="truncate text-sm font-medium">{it.name || "-"}</div>
													<div className="truncate text-xs text-muted-foreground">{it.code || "-"}</div>
												</div>
												<Badge variant={String(it.status).toUpperCase() === "PUBLISHED" ? "default" : "secondary"}>
													{String(it.status || "-")}
												</Badge>
											</div>
											<div className="mt-1 flex flex-wrap gap-2 text-xs text-muted-foreground">
												<span>{it.ownerDept ? `部门: ${it.ownerDept}` : "部门: -"} </span>
												<span>{it.datasetId ? "已绑定数据集" : "未绑定数据集"}</span>
											</div>
										</button>
									);
								})}
								{!loading && items.length === 0 ? (
									<div className="py-8 text-center text-sm text-muted-foreground">暂无数据</div>
								) : null}
							</div>
						</ScrollArea>
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle className="text-base">计算规则</CardTitle>
					</CardHeader>
					<CardContent className="space-y-4">
						{selected ? (
							<>
								<div className="flex flex-wrap items-center gap-2">
									<div className="text-sm font-medium">{selected.name || "-"}</div>
									<Badge variant={selectedStatus === "PUBLISHED" ? "default" : "secondary"}>{statusLabel}</Badge>
									<div className="text-xs text-muted-foreground">{selected.code || "-"}</div>
								</div>

								<div className="grid grid-cols-1 gap-3 md:grid-cols-2">
									<div className="space-y-2">
										<Label className="text-xs text-muted-foreground">数据集</Label>
										<Select
											value={datasetId ? datasetId : "__NONE__"}
											onValueChange={(v) => setDatasetId(v === "__NONE__" ? "" : v)}
										>
											<SelectTrigger>
												<SelectValue placeholder={datasetsLoading ? "加载中…" : "选择数据集"} />
											</SelectTrigger>
											<SelectContent>
												<SelectItem value="__NONE__">（未绑定）</SelectItem>
												{datasets.map((ds) => (
													<SelectItem key={String(ds.id)} value={String(ds.id)}>
														{String(ds.name || ds.id)}
														<span className="text-xs text-muted-foreground">
															{ds.ownerDept ? ` · ${ds.ownerDept}` : ""}
														</span>
													</SelectItem>
												))}
											</SelectContent>
										</Select>
										<div className="flex items-center gap-2">
											<Input
												value={datasetKeyword}
												onChange={(e) => setDatasetKeyword(e.target.value)}
												placeholder="搜索数据集（名称关键字）"
											/>
											<Button
												variant="outline"
												onClick={() => fetchDatasets(datasetKeyword)}
												disabled={datasetsLoading}
											>
												搜索
											</Button>
										</div>
									</div>

									<div className="space-y-2">
										<Label className="text-xs text-muted-foreground">数据集ID（可直接粘贴 UUID）</Label>
										<Input value={datasetId} onChange={(e) => setDatasetId(e.target.value)} placeholder="dataset UUID" />
										<div className="text-xs text-muted-foreground">
											用于执行 SQL 预览（走数据集权限、部门上下文与密级过滤）。
										</div>
									</div>
								</div>

								<div className="space-y-2">
									<div className="flex items-center justify-between gap-2">
										<Label className="text-xs text-muted-foreground">SQL（SELECT 查询）</Label>
										<div className="flex gap-2">
											<Button variant="outline" onClick={fillSampleSql} disabled={previewing}>
												生成示例 SQL
											</Button>
											<Button variant="secondary" onClick={runPreview} disabled={previewing}>
												预览
											</Button>
											<Button onClick={saveComputeRule} disabled={!canSave}>
												{saving ? "保存中…" : "保存"}
											</Button>
										</div>
									</div>
									<Textarea
										value={sqlText}
										onChange={(e) => setSqlText(e.target.value)}
										placeholder="例如：SELECT dept, SUM(amount) AS value FROM ... GROUP BY dept"
										className="min-h-[220px] font-mono text-xs leading-relaxed"
									/>
								</div>

								{preview ? (
									<div className="space-y-2">
										<Label className="text-xs text-muted-foreground">预览结果</Label>
										<div className="rounded-md border bg-muted/20 p-3 text-xs text-muted-foreground">
											<div>rowCount: {String(preview?.rowCount ?? "-")}</div>
											<div>durationMs: {String(preview?.durationMs ?? "-")}</div>
										</div>

										{preview?.effectiveSql ? (
											<pre className="max-h-40 overflow-auto rounded-md border bg-muted/40 p-3 text-xs">
												{String(preview.effectiveSql)}
											</pre>
										) : null}

										{previewHeaders.length > 0 ? (
											<div className="overflow-auto rounded-md border">
												<table className="w-full text-left text-xs">
													<thead className="bg-muted/40">
														<tr>
															{previewHeaders.map((h) => (
																<th key={h} className="px-3 py-2 font-medium">
																	{h}
																</th>
															))}
														</tr>
													</thead>
													<tbody>
														{previewRows.map((row, idx) => (
															<tr key={idx} className="border-t">
																{previewHeaders.map((h) => (
																	<td key={`${idx}-${h}`} className="max-w-[320px] truncate px-3 py-2" title={String(row?.[h] ?? "")}>
																		{String(row?.[h] ?? "")}
																	</td>
																))}
															</tr>
														))}
													</tbody>
												</table>
											</div>
										) : (
											<pre className="max-h-64 overflow-auto rounded-md border bg-muted/40 p-3 text-xs">
												{JSON.stringify(preview, null, 2)}
											</pre>
										)}
									</div>
								) : (
									<div className="text-sm text-muted-foreground">选择指标后可配置并预览计算 SQL。</div>
								)}
							</>
						) : (
							<div className="py-12 text-center text-sm text-muted-foreground">请选择左侧指标</div>
						)}
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
