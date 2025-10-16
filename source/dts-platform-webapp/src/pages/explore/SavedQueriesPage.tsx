import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Checkbox } from "@/ui/checkbox";
import { Badge } from "@/ui/badge";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { cleanupExpiredResultSets, deleteResultSet, listDatasets, listResultSets, previewResultSet } from "@/api/platformApi";
import { useActiveDept } from "@/store/contextStore";
type Dataset = {
	id: string;
	name: string;
};

type ResultSet = {
	id: string;
	name?: string;
	datasetId?: string;
	datasetName?: string;
	columns: string;
	rowCount?: number;
	expiresAt?: string;
	createdAt?: string;
};

const SOON_EXPIRE_DAYS = 7;

function toUiDataset(input: any): Dataset {
	return {
		id: String(input?.id ?? ""),
		name: String(input?.name || input?.hiveTable || input?.id || "未知数据集"),
	};
}

function formatDateTime(value?: string) {
	if (!value) return "-";
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value;
	}
	return date.toLocaleString();
}

function formatExpiryDetail(value?: string) {
	if (!value) return "-";
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value;
	}
	const msDiff = date.getTime() - Date.now();
	const days = Math.round(msDiff / (24 * 60 * 60 * 1000));
	if (days > 0) {
		return `${date.toLocaleString()}（${days} 天后到期）`;
	}
	if (days === 0) {
		return `${date.toLocaleString()}（今日到期）`;
	}
	return `${date.toLocaleString()}（已过期 ${Math.abs(days)} 天）`;
}

function isExpiringSoon(expiresAt?: string) {
	if (!expiresAt) return false;
	const date = new Date(expiresAt);
	if (Number.isNaN(date.getTime())) return false;
	const diff = date.getTime() - Date.now();
	const days = diff / (24 * 60 * 60 * 1000);
	return days >= 0 && days <= SOON_EXPIRE_DAYS;
}

export default function SavedQueriesPage() {
	const activeDept = useActiveDept();
	const [datasets, setDatasets] = useState<Dataset[]>([]);
	const [resultSets, setResultSets] = useState<ResultSet[]>([]);
	const [isLoading, setLoading] = useState(false);
	const [loadError, setLoadError] = useState<string | null>(null);
	const [keyword, setKeyword] = useState("");
	const [datasetFilter, setDatasetFilter] = useState<string>("all");
	const [onlySoonExpire, setOnlySoonExpire] = useState(false);
	const [previewOpen, setPreviewOpen] = useState(false);
	const [previewSql, setPreviewSql] = useState<string>("");
	const [previewMeta, setPreviewMeta] = useState<{
		datasetName?: string;
		rowCount?: number;
		engine?: string;
		finishedAt?: string;
	}>({});

	const load = async () => {
		setLoading(true);
		setLoadError(null);
		try {
			const [datasetResp, resultResp] = await Promise.all([
				listDatasets({ page: 0, size: 200 }),
				listResultSets(),
			]);

			const datasetListRaw = Array.isArray((datasetResp as any)?.content)
				? (datasetResp as any).content
				: Array.isArray(datasetResp)
				? datasetResp
				: [];
			const datasetList: Dataset[] = datasetListRaw.map(toUiDataset).filter((item: Dataset) => Boolean(item.id));
			setDatasets(datasetList);

			const datasetLookup = datasetList.reduce<Record<string, Dataset>>((acc, entry) => {
				acc[entry.id] = entry;
				return acc;
			}, {});

			const resultSetListRaw = Array.isArray(resultResp)
				? resultResp
				: Array.isArray((resultResp as any)?.data)
				? (resultResp as any).data
				: resultResp;
			const resultSetList: ResultSet[] = (Array.isArray(resultSetListRaw) ? resultSetListRaw : []).map((item: any) => {
			const datasetInfo = item?.datasetId ? datasetLookup[item.datasetId] : undefined;
					const columns = Array.isArray(item?.columns)
						? item.columns.join(", ")
						: typeof item?.columns === "string"
						? item.columns
						: "";
					return {
						id: String(item?.id ?? ""),
						name: typeof item?.name === "string" ? item.name : undefined,
						datasetId: item?.datasetId ? String(item.datasetId) : undefined,
					datasetName: datasetInfo?.name ?? item?.datasetName,
					columns,
					rowCount: item?.rowCount,
					expiresAt: item?.expiresAt,
					createdAt: item?.createdAt,
				};
			});
			setResultSets(resultSetList);
		} catch (e: any) {
			console.error(e);
			const message = typeof e?.message === "string" ? e.message : "结果集加载失败";
			setLoadError(message);
			toast.error(message);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void load();
	}, [activeDept]);

	const expiringSoonCount = useMemo(
		() => resultSets.filter((item) => isExpiringSoon(item.expiresAt)).length,
		[resultSets],
	);

	const filteredResultSets = useMemo(() => {
		return resultSets.filter((item) => {
			if (keyword.trim()) {
				const key = keyword.trim().toLowerCase();
				const match =
					item.id.toLowerCase().includes(key) ||
					(item.datasetName ?? "").toLowerCase().includes(key) ||
					(item.columns ?? "").toLowerCase().includes(key);
				if (!match) return false;
			}
			if (datasetFilter !== "all" && item.datasetId !== datasetFilter) return false;
				if (onlySoonExpire && !isExpiringSoon(item.expiresAt)) return false;
				return true;
			});
		}, [resultSets, keyword, datasetFilter, onlySoonExpire]);

	const handlePreview = async (id: string) => {
		try {
			const resp: any = await previewResultSet(id, 10);
			const headers: string[] = Array.isArray(resp?.headers) ? resp.headers : [];
			const rows: any[] = Array.isArray(resp?.rows) ? resp.rows : [];
			const target = resultSets.find((item) => item.id === id);
			const datasetId = target?.datasetId;
			let displayMap: Record<string, string> | undefined = datasetId ? datasetColumnLabels[datasetId] : undefined;
			if (datasetId && !displayMap) {
				try {
					const detail: any = await getDataset(datasetId);
					const tables: any[] = Array.isArray(detail?.tables) ? detail.tables : [];
					const built: Record<string, string> = {};
					for (const table of tables) {
						const columnList = Array.isArray(table?.columns) ? table.columns : [];
						for (const column of columnList) {
							const columnName = String(column?.name ?? column?.columnName ?? "").trim();
							if (!columnName) continue;
							const key = normalizeColumnKey(columnName);
							if (!key || built[key]) continue;
							const label = pickFirstText(
								column?.displayName,
								column?.alias,
								column?.label,
								column?.bizName,
								column?.bizLabel,
								column?.cnName,
								column?.zhName,
								column?.nameZh,
								column?.nameCn,
								column?.comment,
							);
							if (label) {
								built[key] = label;
							}
						}
					}
					setDatasetColumnLabels((prev) => ({ ...prev, [datasetId]: built }));
					displayMap = built;
				} catch (error) {
					console.error("加载数据集列信息失败", error);
				}
			}
			setPreviewHeaders(headers);
			if (displayMap) {
				setPreviewHeaderLabels(
					headers.map((header) => {
						const key = normalizeColumnKey(header);
						return key && displayMap ? displayMap[key] ?? header : header;
					}),
				);
			} else {
				setPreviewHeaderLabels(headers);
			}
			setPreviewRows(rows);
			setPreviewMasking(resp?.masking ?? null);
			setPreviewOpen(true);
		} catch (e) {
			console.error(e);
			toast.error("预览失败");
		}
	};

	const showDatasetHint = !isLoading && !datasets.length && !loadError;

	const maskMode = previewMasking?.mode ?? (Array.isArray(previewMasking?.maskedColumns) ? "heuristic" : undefined);
	const maskColumns = Array.isArray(previewMasking?.columns)
		? previewMasking.columns
		: Array.isArray(previewMasking?.maskedColumns)
		? previewMasking.maskedColumns
		: [];
	const maskRules = Array.isArray(previewMasking?.rules) ? previewMasking.rules : [];
	const maskDefault = previewMasking?.default;

	return (
		<>
			<div className="space-y-4">
				{showDatasetHint ? (
					<div className="flex items-center justify-between rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
						<span>当前尚未同步任何数据集，请联系管理员完成数据源配置后再查看结果集台账。</span>
						<Button variant="ghost" size="sm" onClick={load} disabled={isLoading}>
							重试
						</Button>
					</div>
				) : null}

				<Card>
					<CardHeader className="space-y-4">
						<div className="flex flex-wrap items-center justify-between gap-3">
							<div>
								<CardTitle className="text-base">结果集台账</CardTitle>
								<p className="text-xs text-muted-foreground">
									结果集统一在「数据查询和预览」运行产生，此处用于盘点、巡检与清理。
								</p>
							</div>
							<div className="flex flex-wrap items-center gap-2">
								<Button variant="outline" size="sm" onClick={load} disabled={isLoading}>
									刷新
								</Button>
								<Button
									size="sm"
									onClick={async () => {
										try {
											const resp: any = await cleanupExpiredResultSets();
											const count = Number(resp?.deleted ?? 0);
											toast.success(`已清理 ${count} 条过期结果集`);
											await load();
										} catch (error) {
											console.error(error);
											toast.error("清理失败");
										}
									}}
									disabled={isLoading}
								>
									清理过期
								</Button>
							</div>
						</div>

						<div className="grid gap-3 lg:grid-cols-3">
							<div className="lg:col-span-2 space-y-1">
								<Label>关键字</Label>
								<Input
									value={keyword}
									onChange={(event) => setKeyword(event.target.value)}
									placeholder="按编号 / 数据集 / 列名检索"
								/>
							</div>
							<div className="space-y-1">
								<Label>所属数据集</Label>
								<Select value={datasetFilter} onValueChange={setDatasetFilter}>
									<SelectTrigger>
										<SelectValue placeholder="全部数据集" />
									</SelectTrigger>
									<SelectContent>
										<SelectItem value="all">全部数据集</SelectItem>
										{datasets.map((dataset) => (
										<SelectItem key={dataset.id} value={dataset.id}>
											{dataset.name}
										</SelectItem>
									))}
									</SelectContent>
								</Select>
							</div>
						</div>

						<div className="flex flex-wrap items-center justify-between gap-3 text-xs text-muted-foreground">
							<div className="flex items-center gap-2">
								<span>
									当前展示 <strong>{filteredResultSets.length}</strong> 条 / 全部 {resultSets.length} 条
								</span>
								<Badge variant="secondary">7 天内到期 {expiringSoonCount} 条</Badge>
							</div>
							<label className="flex items-center gap-2">
								<Checkbox checked={onlySoonExpire} onCheckedChange={(value) => setOnlySoonExpire(Boolean(value))} />
								<span>仅查看 7 天内即将到期</span>
							</label>
						</div>
					</CardHeader>
					<CardContent>
						{filteredResultSets.length ? (
							<div className="overflow-hidden rounded-md border">
								<table className="w-full border-collapse text-sm">
									<thead className="bg-muted/50">
										<tr>
											<th className="border-b px-3 py-2 text-left font-medium">编号</th>
											<th className="border-b px-3 py-2 text-left font-medium">名称</th>
											<th className="border-b px-3 py-2 text-left font-medium">所属数据集</th>
							<th className="border-b px-3 py-2 text-left font-medium">列</th>
											<th className="border-b px-3 py-2 text-left font-medium">行数</th>
											<th className="border-b px-3 py-2 text-left font-medium">生成时间</th>
											<th className="border-b px-3 py-2 text-left font-medium">过期时间</th>
											<th className="border-b px-3 py-2 text-left font-medium">操作</th>
										</tr>
									</thead>
									<tbody>
										{filteredResultSets.map((item) => {
											const soon = isExpiringSoon(item.expiresAt);
											return (
												<tr
													key={item.id}
													className={`border-b last:border-b-0 ${soon ? "bg-amber-50/60" : ""}`}
												>
													<td className="px-3 py-2 font-mono text-xs text-muted-foreground">{item.id}</td>
													<td className="px-3 py-2">{item.name || "-"}</td>
													<td className="px-3 py-2">
														{item.datasetName ? (
															<span>{item.datasetName}</span>
														) : (
															<span className="text-muted-foreground">未关联</span>
														)}
													</td>
													<td className="px-3 py-2 text-xs text-muted-foreground">{item.columns || "-"}</td>
													<td className="px-3 py-2">{item.rowCount ?? "-"}</td>
													<td className="px-3 py-2 text-xs text-muted-foreground">{formatDateTime(item.createdAt)}</td>
													<td className="px-3 py-2 text-xs text-muted-foreground">{formatExpiryDetail(item.expiresAt)}</td>
													<td className="px-3 py-2 space-x-2 text-nowrap">
														<Button size="sm" variant="outline" onClick={() => handlePreview(item.id)} disabled={isLoading}>
															预览
														</Button>
														<Button
															size="sm"
															variant="ghost"
															onClick={async () => {
																try {
																	await deleteResultSet(item.id);
																	toast.success("已删除结果集");
																	await load();
																} catch (error) {
																	console.error(error);
																	toast.error("删除失败");
																}
															}}
															disabled={isLoading}
														>
															删除
														</Button>
													</td>
												</tr>
											);
										})}
									</tbody>
								</table>
							</div>
						) : (
							<div className="rounded-md border border-dashed border-muted-foreground/30 bg-muted/20 p-6 text-center text-sm text-muted-foreground">
								暂无结果集台账数据，可前往「数据查询和预览」运行查询并保存结果。
							</div>
						)}
					</CardContent>
				</Card>
			</div>

			<Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
				<DialogContent className="max-w-3xl">
					<DialogHeader>
						<DialogTitle>结果集预览</DialogTitle>
					</DialogHeader>
					<div className="space-y-3">
						{previewMasking ? (
							<div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
								{maskMode === "policy" ? (
									<div>
										<p className="font-medium">按策略脱敏</p>
										{maskRules.length ? <p>规则：{maskRules.map((r: any) => `${r.column}:${r.fn}`).join(", ")}</p> : null}
										{maskDefault ? <p>默认：{maskDefault}</p> : null}
									</div>
								) : (
									<div>
										<p className="font-medium">启发式脱敏</p>
										{maskColumns.length ? <p>列：{maskColumns.join(", ")}</p> : <p>未识别到敏感列</p>}
									</div>
								)}
							</div>
						) : null}
						<div className="overflow-auto rounded-md border">
							<table className="w-full border-collapse text-xs">
								<thead className="bg-muted/50">
									<tr>
										{previewHeaders.map((header, idx) => {
											const label = previewHeaderLabels[idx] ?? header;
											return (
												<th key={header} className="border-b px-2 py-1 text-left font-medium">
													{label}
											</th>
											);
										})}
									</tr>
								</thead>
								<tbody>
									{previewRows.map((row, index) => (
										<tr key={`row-${index}`} className="border-b last:border-b-0">
											{previewHeaders.map((header) => (
												<td key={header} className="px-2 py-1">
													{String(row[header] ?? "")}
												</td>
											))}
										</tr>
									))}
								</tbody>
							</table>
						</div>
					</div>
					<DialogFooter>
						<Button variant="ghost" onClick={() => setPreviewOpen(false)}>
							关闭
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</>
	);
}
