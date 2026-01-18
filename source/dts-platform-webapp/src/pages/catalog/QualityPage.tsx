import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { batchDatasetQuality, getDatasetQuality, listDatasets } from "@/api/platformApi";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";

type DatasetOption = { id: string; name: string; ownerDept?: string | null; classification?: string | null };

const PAGE_SIZE = 200;

export default function QualityPage() {
	const [datasetKeyword, setDatasetKeyword] = useState("");
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [datasetsLoading, setDatasetsLoading] = useState(false);
	const [datasetId, setDatasetId] = useState("");

	const [qualityLoading, setQualityLoading] = useState(false);
	const [qualityInfo, setQualityInfo] = useState<any | null>(null);
	const [batchLoading, setBatchLoading] = useState(false);
	const [batchSummaries, setBatchSummaries] = useState<Record<string, any>>({});

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

	const loadQuality = useCallback(async () => {
		if (!datasetId) {
			setQualityInfo(null);
			return;
		}
		setQualityLoading(true);
		try {
			const resp = (await getDatasetQuality(datasetId)) as any;
			setQualityInfo(resp || null);
		} catch (e: any) {
			console.error(e);
			setQualityInfo(null);
			toast.error(e?.message || "加载质量结果失败");
		} finally {
			setQualityLoading(false);
		}
	}, [datasetId]);

	const loadBatchSummary = useCallback(async () => {
		if (!datasets.length) {
			setBatchSummaries({});
			return;
		}
		setBatchLoading(true);
		try {
			const ids = datasets.slice(0, 50).map((d) => d.id);
			const resp = (await batchDatasetQuality(ids)) as any;
			setBatchSummaries(resp || {});
		} catch (e: any) {
			console.error(e);
			setBatchSummaries({});
			toast.error(e?.message || "加载质量概览失败");
		} finally {
			setBatchLoading(false);
		}
	}, [datasets]);

	useEffect(() => {
		void loadDatasets();
	}, [loadDatasets]);

	useEffect(() => {
		void loadQuality();
	}, [loadQuality]);

	useEffect(() => {
		void loadBatchSummary();
	}, [loadBatchSummary]);

	const currentDataset = useMemo(() => datasets.find((d) => d.id === datasetId) || null, [datasetId, datasets]);
	const summary = useMemo(() => {
		const s = qualityInfo?.snapshot?.summary;
		const total = Number(s?.total || 0);
		const passed = Number(s?.passed || 0);
		const failed = Number(s?.failed || 0);
		const aborted = Number(s?.aborted || 0);
		const missing = Number(s?.missing || 0);
		const passRate = total > 0 ? Math.round((passed / total) * 100) : null;
		return { total, passed, failed, aborted, missing, passRate, lastRunAt: s?.lastRunAt };
	}, [qualityInfo]);
	const cases = useMemo(() => {
		const list = qualityInfo?.snapshot?.cases;
		return Array.isArray(list) ? list : [];
	}, [qualityInfo]);

	const overview = useMemo(() => {
		const list = datasets
			.map((d) => {
				const summary = batchSummaries[d.id];
				return summary ? { dataset: d, summary } : null;
			})
			.filter(Boolean) as Array<{ dataset: DatasetOption; summary: any }>;
		const totalAssets = list.length;
		const failedAssets = list.filter((item) => Number(item.summary?.failed || 0) > 0).length;
		const worst = [...list]
			.sort((a, b) => Number(b.summary?.failed || 0) - Number(a.summary?.failed || 0))
			.slice(0, 5);
		const lowPassRate = [...list]
			.filter((item) => item.summary?.passRate != null)
			.sort((a, b) => Number(a.summary?.passRate || 0) - Number(b.summary?.passRate || 0))
			.slice(0, 5);
		return { totalAssets, failedAssets, worst, lowPassRate };
	}, [batchSummaries, datasets]);

	return (
		<div className="flex flex-col gap-6">
			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
					<CardTitle>数据质量（按资产）</CardTitle>
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
								<Button variant="outline" onClick={loadQuality} disabled={!datasetId || qualityLoading}>
									{qualityLoading ? "加载中…" : "刷新质量"}
								</Button>
							</div>
						</div>
					</div>

					{currentDataset ? (
						<div className="flex flex-wrap gap-2">
							<Badge variant="outline">{String(currentDataset.classification || "-")}</Badge>
							<Badge variant="secondary">{currentDataset.ownerDept ? `部门:${currentDataset.ownerDept}` : "部门:未指定"}</Badge>
						</div>
					) : null}
				</CardContent>
			</Card>

			<Card>
				<CardHeader className="flex items-center justify-between">
					<CardTitle>质量总览（前 50 资产）</CardTitle>
					<Button variant="outline" onClick={loadBatchSummary} disabled={batchLoading}>
						{batchLoading ? "加载中…" : "刷新概览"}
					</Button>
				</CardHeader>
				<CardContent className="space-y-4">
					<div className="grid gap-3 md:grid-cols-3 text-sm">
						<div className="space-y-1">
							<div className="text-xs text-muted-foreground">覆盖资产</div>
							<div className="text-lg font-semibold">{overview.totalAssets}</div>
						</div>
						<div className="space-y-1">
							<div className="text-xs text-muted-foreground">存在失败</div>
							<div className="text-lg font-semibold">{overview.failedAssets}</div>
						</div>
						<div className="space-y-1">
							<div className="text-xs text-muted-foreground">数据来源</div>
							<div className="text-lg font-semibold">质量检测结果</div>
						</div>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<div className="text-sm font-medium">失败最多资产</div>
							{overview.worst.length ? (
								<ul className="space-y-2 text-sm">
									{overview.worst.map((item) => (
										<li key={item.dataset.id} className="rounded-md border px-3 py-2">
											<div className="font-medium">{item.dataset.name}</div>
											<div className="text-xs text-muted-foreground">
												失败 {item.summary?.failed || 0} / 总数 {item.summary?.total || 0}
											</div>
										</li>
									))}
								</ul>
							) : (
								<div className="text-sm text-muted-foreground">暂无统计</div>
							)}
						</div>
						<div className="space-y-2">
							<div className="text-sm font-medium">通过率最低资产</div>
							{overview.lowPassRate.length ? (
								<ul className="space-y-2 text-sm">
									{overview.lowPassRate.map((item) => (
										<li key={item.dataset.id} className="rounded-md border px-3 py-2">
											<div className="font-medium">{item.dataset.name}</div>
											<div className="text-xs text-muted-foreground">
												通过率 {item.summary?.passRate == null ? "-" : `${item.summary.passRate}%`}
											</div>
										</li>
									))}
								</ul>
							) : (
								<div className="text-sm text-muted-foreground">暂无统计</div>
							)}
						</div>
					</div>
				</CardContent>
			</Card>

			<Card>
				<CardHeader className="flex items-center justify-between">
					<CardTitle>质量概览</CardTitle>
					<div className="text-xs text-muted-foreground">数据来自质量检测结果</div>
				</CardHeader>
				<CardContent className="grid gap-3 md:grid-cols-5 text-sm">
					<div className="space-y-1">
						<div className="text-xs text-muted-foreground">总数</div>
						<div className="text-lg font-semibold">{summary.total}</div>
					</div>
					<div className="space-y-1">
						<div className="text-xs text-muted-foreground">通过</div>
						<div className="text-lg font-semibold">{summary.passed}</div>
					</div>
					<div className="space-y-1">
						<div className="text-xs text-muted-foreground">失败</div>
						<div className="text-lg font-semibold">{summary.failed}</div>
					</div>
					<div className="space-y-1">
						<div className="text-xs text-muted-foreground">其他</div>
						<div className="text-lg font-semibold">{summary.aborted + summary.missing}</div>
					</div>
					<div className="space-y-1">
						<div className="text-xs text-muted-foreground">通过率</div>
						<div className="text-lg font-semibold">{summary.passRate === null ? "-" : `${summary.passRate}%`}</div>
					</div>
					<div className="md:col-span-5 text-xs text-muted-foreground">
						最近运行：{summary.lastRunAt ? new Date(summary.lastRunAt).toLocaleString() : "-"}
					</div>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>质量明细</CardTitle>
				</CardHeader>
				<CardContent>
					{!datasetId ? (
						<div className="text-sm text-muted-foreground">请先选择数据集</div>
					) : qualityLoading ? (
						<div className="text-sm text-muted-foreground">加载中…</div>
					) : qualityInfo?.found === false ? (
						<div className="text-sm text-muted-foreground">{qualityInfo?.message || "暂无质量数据"}</div>
					) : cases.length === 0 ? (
						<div className="text-sm text-muted-foreground">暂无质量规则结果</div>
					) : (
						<div className="overflow-auto">
							<table className="w-full text-sm">
								<thead className="text-xs text-muted-foreground">
									<tr className="border-b">
										<th className="py-2 text-left font-medium">规则</th>
										<th className="py-2 text-left font-medium">状态</th>
										<th className="py-2 text-left font-medium">负责人</th>
										<th className="py-2 text-left font-medium">测试集</th>
										<th className="py-2 text-left font-medium">最近运行</th>
									</tr>
								</thead>
								<tbody>
									{cases.map((item: any) => (
										<tr key={item.id || item.name} className="border-b last:border-none">
											<td className="py-2 pr-4">
												<div className="font-medium">{item.name || "-"}</div>
												{item.description ? (
													<div className="text-xs text-muted-foreground">{item.description}</div>
												) : null}
											</td>
											<td className="py-2 pr-4">{item.status || "-"}</td>
											<td className="py-2 pr-4">{item.owner || "-"}</td>
											<td className="py-2 pr-4">{item.testSuite || "-"}</td>
											<td className="py-2 pr-4">{item.lastRunAt ? new Date(item.lastRunAt).toLocaleString() : "-"}</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					)}
				</CardContent>
			</Card>
		</div>
	);
}
