import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Checkbox } from "@/ui/checkbox";
import { Badge } from "@/ui/badge";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import {
	cleanupExpiredResultSets,
	deleteResultSet,
	listDatasets,
	listResultSets,
	previewResultSet,
	recordResultSetCopy,
} from "@/api/platformApi";
import { useActiveDept } from "@/store/contextStore";
import { cn } from "@/utils";
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

function isExpired(expiresAt?: string) {
	if (!expiresAt) return false;
	const date = new Date(expiresAt);
	if (Number.isNaN(date.getTime())) return false;
	return date.getTime() < Date.now();
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
	const [previewTargetId, setPreviewTargetId] = useState<string | null>(null);
	const [previewMeta, setPreviewMeta] = useState<{
		datasetId?: string;
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
				listDatasets({ page: 0, size: 200, auditPurpose: "explore.preview" }),
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
	const missingDatasetCount = useMemo(
		() => resultSets.filter((item) => !item.datasetId).length,
		[resultSets],
	);

	const filteredResultSets = useMemo(() => {
		const normalizedKeyword = keyword.trim().toLowerCase();
		const hasKeyword = Boolean(normalizedKeyword);
		return resultSets.filter((item) => {
			if (hasKeyword) {
				const match =
					item.id.toLowerCase().includes(normalizedKeyword) ||
					(item.datasetName ?? "").toLowerCase().includes(normalizedKeyword) ||
					(item.columns ?? "").toLowerCase().includes(normalizedKeyword);
				if (!match) return false;
			}
			if (datasetFilter !== "all" && item.datasetId !== datasetFilter) return false;
			if (onlySoonExpire && !isExpiringSoon(item.expiresAt)) return false;
			return true;
		});
	}, [resultSets, keyword, datasetFilter, onlySoonExpire]);

	const handlePreview = async (id: string) => {
		try {
			const resp: any = await previewResultSet(id);
			const sqlText = typeof resp?.sqlText === "string" ? resp.sqlText.trim() : "";
			const target = resultSets.find((item) => item.id === id);
			setPreviewSql(sqlText || "未找到保存时的 SQL");
			const resolvedRowCount =
				typeof resp?.rowCount === "number"
					? resp.rowCount
					: typeof resp?.rowCount === "string" && !Number.isNaN(Number(resp.rowCount))
					? Number(resp.rowCount)
					: undefined;
			setPreviewTargetId(id);
			setPreviewMeta({
				datasetId: typeof resp?.datasetId === "string" ? resp.datasetId : target?.datasetId,
				datasetName: resp?.datasetName || target?.datasetName,
				rowCount: resolvedRowCount ?? target?.rowCount,
				engine: typeof resp?.engine === "string" ? resp.engine : undefined,
				finishedAt: typeof resp?.finishedAt === "string" ? resp.finishedAt : undefined,
			});
			setPreviewOpen(true);
		} catch (e) {
			console.error(e);
			toast.error("加载 SQL 失败");
		}
	};

	const handleCopySql = async () => {
		if (!previewSql) {
			toast.error("暂无可复制的 SQL");
			return;
		}
		try {
			if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
				await navigator.clipboard.writeText(previewSql);
			} else {
				const textarea = document.createElement("textarea");
				textarea.value = previewSql;
				textarea.setAttribute("readonly", "");
				textarea.style.position = "absolute";
				textarea.style.left = "-9999px";
				document.body.appendChild(textarea);
				textarea.select();
				document.execCommand("copy");
				document.body.removeChild(textarea);
			}
			toast.success("SQL 已复制");
		} catch (error) {
			console.error(error);
			toast.error("复制失败，请手动复制");
			return;
		}
		if (previewTargetId) {
			void recordResultSetCopy(previewTargetId, {
				sql: previewSql,
				datasetId: previewMeta.datasetId,
				datasetName: previewMeta.datasetName,
				engine: previewMeta.engine,
				rowCount: previewMeta.rowCount,
				finishedAt: previewMeta.finishedAt,
			}).catch((error) => {
				console.warn("Failed to record copy audit", error);
			});
		}
	};

	const showDatasetHint = !isLoading && !datasets.length && !loadError;

	return (
		<>
			<div className="space-y-6">
				{showDatasetHint ? (
					<div className="flex items-center justify-between rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
						<span>当前尚未同步任何数据集，请联系管理员完成数据源配置后再查看结果集台账。</span>
						<Button variant="ghost" size="sm" onClick={load} disabled={isLoading}>
							重试
						</Button>
					</div>
				) : null}

				<Card>
					<CardHeader className="gap-4 md:grid-cols-[minmax(0,1fr)_auto]">
						<div className="space-y-2">
							<CardTitle className="text-lg">结果集台账</CardTitle>
							<CardDescription>结果集统一在「数据查询和预览」运行产生，此处用于盘点、巡检与清理。</CardDescription>
						</div>
						<CardAction className="flex flex-wrap items-center gap-2">
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
						</CardAction>
					</CardHeader>
					<CardContent className="space-y-6">
						<div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
							<div className="rounded-lg border border-border/70 bg-muted/30 p-4 shadow-sm">
								<p className="text-xs text-muted-foreground">全部结果集</p>
								<p className="mt-2 text-2xl font-semibold">{resultSets.length}</p>
							</div>
							<div className="rounded-lg border border-border/70 bg-muted/30 p-4 shadow-sm">
								<p className="text-xs text-muted-foreground">7 天内即将到期</p>
								<p className="mt-2 text-2xl font-semibold">{expiringSoonCount}</p>
							</div>
							<div className="rounded-lg border border-border/70 bg-muted/30 p-4 shadow-sm">
								<p className="text-xs text-muted-foreground">未关联数据集</p>
								<p className="mt-2 text-2xl font-semibold">{missingDatasetCount}</p>
							</div>
							<div className="rounded-lg border border-border/70 bg-muted/30 p-4 shadow-sm">
								<p className="text-xs text-muted-foreground">当前展示</p>
								<p className="mt-2 text-2xl font-semibold">{filteredResultSets.length}</p>
							</div>
						</div>

						<div className="rounded-lg border border-border/60 bg-card/40 p-4 shadow-sm">
							<div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,16rem)]">
								<div className="space-y-3">
									<div className="space-y-1.5">
										<Label className="text-xs uppercase text-muted-foreground">关键字</Label>
										<Input
											value={keyword}
											onChange={(event) => setKeyword(event.target.value)}
											placeholder="按编号 / 数据集 / 列名检索"
										/>
									</div>
									<label className="flex items-center gap-2 text-xs text-muted-foreground">
										<Checkbox
											checked={onlySoonExpire}
											onCheckedChange={(value) => setOnlySoonExpire(Boolean(value))}
										/>
										<span>仅查看 7 天内即将到期</span>
									</label>
								</div>
								<div className="space-y-1.5">
									<Label className="text-xs uppercase text-muted-foreground">所属数据集</Label>
									<Select value={datasetFilter} onValueChange={setDatasetFilter}>
										<SelectTrigger className="h-10">
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
						</div>

						<div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
							<Badge variant="secondary" className="rounded-full px-3 py-1">
								已筛选 {filteredResultSets.length} / {resultSets.length}
							</Badge>
							{onlySoonExpire ? <Badge variant="outline">聚焦即将到期</Badge> : null}
							{datasetFilter !== "all" ? <Badge variant="outline">仅显示所选数据集</Badge> : null}
							{keyword.trim() ? <Badge variant="outline">关键词：{keyword.trim()}</Badge> : null}
						</div>

						{filteredResultSets.length ? (
							<div className="overflow-x-auto rounded-md border">
								<table className="w-full min-w-[960px] border-collapse text-sm">
									<thead className="bg-muted/60 text-xs uppercase tracking-wide text-muted-foreground">
										<tr>
											<th className="border-b px-3 py-3 text-left font-medium">编号</th>
											<th className="border-b px-3 py-3 text-left font-medium">名称</th>
											<th className="border-b px-3 py-3 text-left font-medium">所属数据集</th>
											<th className="border-b px-3 py-3 text-left font-medium">列</th>
											<th className="border-b px-3 py-3 text-left font-medium">行数</th>
											<th className="border-b px-3 py-3 text-left font-medium">生成时间</th>
											<th className="border-b px-3 py-3 text-left font-medium">过期时间</th>
											<th className="border-b px-3 py-3 text-left font-medium">操作</th>
										</tr>
									</thead>
									<tbody>
										{filteredResultSets.map((item) => {
											const soon = isExpiringSoon(item.expiresAt);
											const expired = isExpired(item.expiresAt);
											return (
												<tr
													key={item.id}
													className={cn(
														"border-b last:border-b-0 transition-colors",
														soon ? "bg-amber-50/60" : "hover:bg-muted/40",
													)}
												>
													<td className="px-3 py-3">
														<span className="inline-flex max-w-[220px] items-center gap-2 rounded-md border bg-background px-2 py-1 font-mono text-xs text-muted-foreground">
															{item.id}
														</span>
													</td>
													<td className="px-3 py-3">
														<div className="flex flex-col gap-1">
															<span className="font-medium text-sm">{item.name || "-"}</span>
															{item.rowCount ? (
																<Badge variant="outline" className="w-fit text-xs font-normal">
																	行数 {item.rowCount}
																</Badge>
															) : null}
														</div>
													</td>
													<td className="px-3 py-2">
														{item.datasetName ? (
															<Badge variant="secondary" className="w-fit">
																{item.datasetName}
															</Badge>
														) : (
															<span className="text-muted-foreground">未关联</span>
														)}
													</td>
													<td className="px-3 py-3 text-xs text-muted-foreground">
														<div className="line-clamp-2 max-w-[320px] leading-relaxed">{item.columns || "-"}</div>
													</td>
													<td className="px-3 py-3 text-sm">{item.rowCount ?? "-"}</td>
													<td className="px-3 py-3 text-xs text-muted-foreground">{formatDateTime(item.createdAt)}</td>
													<td className="px-3 py-3 text-xs text-muted-foreground">
														<div className="flex flex-col gap-1">
															<span>{formatExpiryDetail(item.expiresAt)}</span>
															{soon ? (
																<Badge variant="destructive" className="w-fit bg-amber-500/90 text-white hover:bg-amber-500">
																	即将到期
																</Badge>
															) : null}
															{!soon && expired ? (
																<Badge variant="destructive" className="w-fit">
																	已过期
																</Badge>
															) : null}
														</div>
													</td>
													<td className="px-3 py-3">
														<Button size="sm" variant="outline" onClick={() => handlePreview(item.id)} disabled={isLoading}>
															查看 SQL
														</Button>
														<Button
															size="sm"
															variant="ghost"
															className="ml-1"
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

	<Dialog
		open={previewOpen}
		onOpenChange={(next) => {
			setPreviewOpen(next);
			if (!next) {
				setPreviewTargetId(null);
				setPreviewSql("");
				setPreviewMeta({});
			}
		}}
	>
				<DialogContent className="max-w-3xl">
					<DialogHeader>
						<DialogTitle>查看 SQL</DialogTitle>
					</DialogHeader>
					<div className="space-y-4">
						<div className="rounded-md border border-dashed border-muted-foreground/40 bg-muted/20 px-3 py-2 text-xs text-muted-foreground">
							<p>所属数据集：{previewMeta?.datasetName || "未关联"}</p>
							<p>行数：{typeof previewMeta?.rowCount === "number" ? previewMeta.rowCount : "-"}</p>
							<p>执行引擎：{previewMeta?.engine ?? "-"}</p>
							<p>完成时间：{formatDateTime(previewMeta?.finishedAt)}</p>
						</div>
						<div className="rounded-md border bg-black/90 p-3 text-xs text-white">
							<pre className="whitespace-pre-wrap font-mono leading-relaxed">{previewSql || "未找到保存时的 SQL"}</pre>
						</div>
						<div className="flex items-center justify-end gap-2">
							<Button variant="outline" size="sm" onClick={handleCopySql} disabled={!previewSql}>
								复制 SQL
							</Button>
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
