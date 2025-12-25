import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { getDataset, listDatasets, listDatasetJobs, syncDatasetSchema, updateDataset } from "@/api/platformApi";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";

type DatasetOption = { id: string; name: string; ownerDept?: string | null; classification?: string | null; editable?: boolean };

const PAGE_SIZE = 200;

export default function LifecyclePage() {
	const [datasetKeyword, setDatasetKeyword] = useState("");
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [datasetsLoading, setDatasetsLoading] = useState(false);
	const [datasetId, setDatasetId] = useState("");

	const [loading, setLoading] = useState(false);
	const [dataset, setDataset] = useState<any | null>(null);
	const [jobsLoading, setJobsLoading] = useState(false);
	const [jobs, setJobs] = useState<any[]>([]);
	const [syncing, setSyncing] = useState(false);
	const [saving, setSaving] = useState(false);

	const [lifecycleStatus, setLifecycleStatus] = useState<string>("");
	const [retentionDays, setRetentionDays] = useState<string>("");
	const [expiresAt, setExpiresAt] = useState<string>(""); // ISO string

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
					editable: Boolean(d.editable),
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
			setDataset(null);
			setLifecycleStatus("");
			setRetentionDays("");
			setExpiresAt("");
			return;
		}
		setLoading(true);
		try {
			const resp = (await getDataset(datasetId)) as any;
			setDataset(resp || null);
			setLifecycleStatus(String(resp?.lifecycleStatus || ""));
			setRetentionDays(resp?.retentionDays != null ? String(resp.retentionDays) : "");
			setExpiresAt(resp?.expiresAt ? String(resp.expiresAt) : "");
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "加载失败");
			setDataset(null);
		} finally {
			setLoading(false);
		}
	}, [datasetId]);

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
		void loadDatasetDetail();
		void loadJobs();
	}, [loadDatasetDetail, loadJobs]);

	const currentOption = useMemo(() => datasets.find((d) => d.id === datasetId) || null, [datasetId, datasets]);
	const editable = Boolean(currentOption?.editable);

	const computeExpiresAt = useCallback(() => {
		const days = Number.parseInt(retentionDays.trim(), 10);
		if (!Number.isFinite(days) || days <= 0) {
			toast.error("请输入有效的保留天数");
			return;
		}
		const d = new Date();
		d.setDate(d.getDate() + days);
		setExpiresAt(d.toISOString());
		toast.success("已计算到期时间");
	}, [retentionDays]);

	const submit = useCallback(async () => {
		if (!datasetId) return;
		if (!editable) {
			toast.error("当前用户无权修改该数据集");
			return;
		}
		setSaving(true);
		try {
			const payload: any = {
				...dataset,
				lifecycleStatus: lifecycleStatus.trim() || null,
				retentionDays: retentionDays.trim() ? Number.parseInt(retentionDays.trim(), 10) : null,
				expiresAt: expiresAt.trim() ? expiresAt.trim() : null,
			};
			await updateDataset(datasetId, payload);
			toast.success("已保存生命周期配置");
			await loadDatasetDetail();
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	}, [dataset, datasetId, editable, expiresAt, lifecycleStatus, loadDatasetDetail, retentionDays]);

	const triggerSync = useCallback(async () => {
		if (!datasetId) return;
		setSyncing(true);
		try {
			await syncDatasetSchema(datasetId, {});
			toast.success("已提交同步任务");
			await loadJobs();
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "提交失败");
		} finally {
			setSyncing(false);
		}
	}, [datasetId, loadJobs]);

	if (loading) {
		return <div className="text-sm text-muted-foreground">加载中…</div>;
	}

	return (
		<div className="flex flex-col gap-6">
			<Card>
				<CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
					<CardTitle>生命周期管理（轻量）</CardTitle>
					<div className="flex flex-col gap-2 md:flex-row md:items-center">
						<Input value={datasetKeyword} onChange={(e) => setDatasetKeyword(e.target.value)} placeholder="搜索数据集" className="w-full md:w-64" />
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
								<Button onClick={submit} disabled={!datasetId || saving || !editable}>
									{saving ? "保存中…" : "保存"}
								</Button>
								<Button variant="outline" onClick={computeExpiresAt} disabled={!datasetId}>
									按保留天数计算到期
								</Button>
								<Button onClick={triggerSync} disabled={!datasetId || syncing}>
									{syncing ? "提交中…" : "触发元数据同步"}
								</Button>
							</div>
							<div className="text-xs text-muted-foreground">
								生命周期字段用于到期提醒与归档/销毁流程管理；数据访问仍由“部门 + 个人密级 + 表内密级字段”控制。
							</div>
						</div>
					</div>

					{dataset ? (
						<div className="flex flex-wrap gap-2">
							<Badge variant="outline">{String(dataset.classification || "-")}</Badge>
							<Badge variant="secondary">{dataset.ownerDept ? `部门:${dataset.ownerDept}` : "部门:未指定"}</Badge>
							<Badge variant="secondary">{dataset.type ? `来源:${dataset.type}` : "来源:-"}</Badge>
							{editable ? <Badge>可编辑</Badge> : <Badge variant="secondary">只读</Badge>}
						</div>
					) : null}

					<div className="grid gap-4 md:grid-cols-3">
						<div className="space-y-2">
							<Label>生命周期状态</Label>
							<Select value={lifecycleStatus || "__NONE__"} onValueChange={(v) => setLifecycleStatus(v === "__NONE__" ? "" : v)} disabled={!editable}>
								<SelectTrigger>
									<SelectValue placeholder="请选择…" />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="__NONE__">（未设置）</SelectItem>
									<SelectItem value="ACTIVE">ACTIVE</SelectItem>
									<SelectItem value="ARCHIVED">ARCHIVED</SelectItem>
									<SelectItem value="DESTROYED">DESTROYED</SelectItem>
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>保留天数</Label>
							<Input value={retentionDays} onChange={(e) => setRetentionDays(e.target.value)} placeholder="例如：365" disabled={!editable} />
						</div>
						<div className="space-y-2">
							<Label>到期时间（ISO）</Label>
							<Input value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} placeholder="例如：2026-12-31T00:00:00Z" disabled={!editable} />
						</div>
					</div>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle className="text-base">最近任务（采集/同步记录）</CardTitle>
				</CardHeader>
				<CardContent>
					<ScrollArea className="h-[360px] pr-2">
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
											<td className="py-2 px-3 text-muted-foreground max-w-[520px] truncate" title={String(j.message || "")}>
												{String(j.message || "-")}
											</td>
										</tr>
									))}
									{jobsLoading ? (
										<tr>
											<td colSpan={3} className="py-10 text-center text-muted-foreground">
												加载中…
											</td>
										</tr>
									) : null}
									{!jobsLoading && datasetId && jobs.length === 0 ? (
										<tr>
											<td colSpan={3} className="py-10 text-center text-muted-foreground">
												暂无任务
											</td>
										</tr>
									) : null}
									{!datasetId ? (
										<tr>
											<td colSpan={3} className="py-10 text-center text-muted-foreground">
												请先选择数据集
											</td>
										</tr>
									) : null}
								</tbody>
							</table>
						</div>
					</ScrollArea>
				</CardContent>
			</Card>
		</div>
	);
}

