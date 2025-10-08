import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { ScrollArea } from "@/ui/scroll-area";
import { toast } from "sonner";
import {
    getDataset,
    previewSecurityViews,
    updateDataset,
    getAccessPolicy,
    upsertAccessPolicy,
    syncDatasetSchema,
    previewDataset,
    applyPolicy,
    getDatasetJob,
    latestQuality,
    triggerQuality,
} from "@/api/platformApi";
import type { DatasetAsset, DatasetJob, DatasetJobStatus, DataLevel, Scope, ShareScope } from "@/types/catalog";
import deptService, { type DeptDto } from "@/api/services/deptService";

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const DATA_LEVELS = [
  { value: "DATA_PUBLIC", label: "公开 (DATA_PUBLIC)" },
  { value: "DATA_INTERNAL", label: "内部 (DATA_INTERNAL)" },
  { value: "DATA_SECRET", label: "秘密 (DATA_SECRET)" },
  { value: "DATA_TOP_SECRET", label: "机密 (DATA_TOP_SECRET)" },
] as const;

export default function DatasetDetailPage() {
	const params = useParams();
	const id = String(params.id || "");
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [dataset, setDataset] = useState<DatasetAsset | null>(null);

	const [allowRoles, setAllowRoles] = useState("ROLE_PUBLIC,ROLE_INTERNAL");
	const [rowFilter, setRowFilter] = useState("");
	const [defaultMasking, setDefaultMasking] = useState("NONE");
    const [preview, setPreview] = useState<Record<string, string> | null>(null);
    const [dataPreview, setDataPreview] = useState<{ headers: string[]; rows: any[] } | null>(null);
    const [busy, setBusy] = useState(false);
    const [syncing, setSyncing] = useState(false);
    const [lastJob, setLastJob] = useState<DatasetJob | null>(null);
    const [qualitySummary, setQualitySummary] = useState<any | null>(null);
    const [qualityLoading, setQualityLoading] = useState(false);
	const [qualityRunning, setQualityRunning] = useState(false);
    const [deptOptions, setDeptOptions] = useState<DeptDto[]>([]);
    const [deptLoading, setDeptLoading] = useState(false);

    const loadDataset = async (withSpinner = false) => {
        if (withSpinner) setLoading(true);
        try {
            const data = (await getDataset(id)) as any;
            setDataset(data);
            try {
                const p = (await getAccessPolicy(id)) as any;
                if (p) {
                    setAllowRoles(p.allowRoles || allowRoles);
                    setRowFilter(p.rowFilter || "");
                    setDefaultMasking(p.defaultMasking || "NONE");
                }
            } catch (inner) {
                console.warn("Failed to load policy", inner);
            }
        } catch (e) {
            console.error(e);
            toast.error("加载失败");
        } finally {
            if (withSpinner) setLoading(false);
        }
    };

    const loadQualitySummary = async () => {
        if (!id) return;
        setQualityLoading(true);
        try {
            const summary = (await latestQuality(id)) as any;
            setQualitySummary(summary);
        } catch (error) {
            console.warn("Latest quality fetch failed", error);
            setQualitySummary(null);
        } finally {
            setQualityLoading(false);
        }
    };

    const handleTriggerQuality = async () => {
        if (!id) return;
        setQualityRunning(true);
        try {
            await triggerQuality(id);
            toast.success("已提交质量检测任务");
            setTimeout(() => {
                void loadQualitySummary();
            }, 2000);
        } catch (error) {
            console.error(error);
            toast.error("触发质量检测失败");
        } finally {
            setQualityRunning(false);
        }
    };

    const resolveJobError = (job: DatasetJob | null): string => {
        if (!job) return "任务失败";
        if (job.detail && typeof job.detail === "object" && job.detail !== null) {
            const detail = job.detail as Record<string, any>;
            if (typeof detail.error === "string" && detail.error.trim().length) {
                return detail.error;
            }
        }
        return job.message || "任务失败";
    };

    const jobStatusLabel = (status: DatasetJobStatus) => {
        switch (status) {
            case "QUEUED":
                return "排队中";
            case "RUNNING":
                return "执行中";
            case "SUCCEEDED":
                return "已完成";
            case "FAILED":
                return "失败";
            default:
                return status;
        }
    };

    const formatJobTime = (job: DatasetJob) => {
        if (!job.finishedAt) return "";
        try {
            return new Date(job.finishedAt).toLocaleString();
        } catch {
            return job.finishedAt;
        }
    };

    const pollJobStatus = async (jobId: string): Promise<DatasetJob> => {
        const maxAttempts = 10;
        let attempt = 0;
        while (attempt < maxAttempts) {
            try {
                const job = (await getDatasetJob(jobId)) as DatasetJob | null;
                if (job) {
                    if (job.status === "SUCCEEDED") {
                        return job;
                    }
                    if (job.status === "FAILED") {
                        throw new Error(resolveJobError(job));
                    }
                }
            } catch (e) {
                if (e instanceof Error && e.message.includes("404")) {
                    // job may not be persisted yet; continue polling
                } else {
                    throw e instanceof Error ? e : new Error("任务状态查询失败");
                }
            }
            attempt += 1;
            await wait(1200);
        }
        throw new Error("任务仍在执行，请稍后在任务列表查看");
    };

	useEffect(() => {
		void loadDataset(true);
		void loadQualitySummary();
	}, [id]);

    useEffect(() => {
        let mounted = true;
        if (dataset?.scope === "DEPT") {
            setDeptLoading(true);
            deptService
                .listDepartments()
                .then((list) => mounted && setDeptOptions(list || []))
                .finally(() => mounted && setDeptLoading(false));
        }
        // Enforce INST scope cannot use PRIVATE_DEPT; auto-correct if necessary
        if (dataset?.scope === "INST" && dataset?.shareScope === "PRIVATE_DEPT") {
            setDataset({ ...(dataset as any), shareScope: "SHARE_INST" });
        }
        return () => {
            mounted = false;
        };
    }, [dataset?.scope]);

	const onAddColumn = () => {
		if (!dataset) return;
		const next = { ...dataset } as DatasetAsset;
		const cols = next.table?.columns || [];
		const idc = `${dataset.id}_c_${Date.now()}`;
		const col = { id: idc, name: `col_${cols.length + 1}`, dataType: "STRING", sensitiveTags: [] as string[] };
		next.table = next.table || { tableName: dataset.name, columns: [] };
		next.table.columns = [...cols, col];
		setDataset(next);
	};

	const onRemoveColumn = (cid: string) => {
		if (!dataset || !dataset.table) return;
		setDataset({ ...dataset, table: { ...dataset.table, columns: dataset.table.columns.filter((c) => c.id !== cid) } });
	};

	const onSave = async () => {
		if (!dataset) return;
		setSaving(true);
		try {
			await updateDataset(dataset.id, dataset);
			await upsertAccessPolicy(dataset.id, { allowRoles, rowFilter, defaultMasking });
			toast.success("已保存");
		} catch (e) {
			console.error(e);
			toast.error("保存失败");
		} finally {
			setSaving(false);
		}
	};

    // Legacy classification UI removed; only DATA_* is used going forward

	const hasHive = dataset?.source?.sourceType === "HIVE";

	const viewKeys = useMemo(() => (preview ? Object.keys(preview) : []), [preview]);

	if (loading) return <div className="text-sm text-muted-foreground">加载中…</div>;
	if (!dataset) return <div className="text-sm text-muted-foreground">未找到该数据集</div>;

	return (
		<div className="space-y-4">
			<Card>
            <CardHeader className="flex items-center justify-between">
                <CardTitle className="text-base">基础信息</CardTitle>
                <div className="flex items-center gap-2">
                    <Button variant="outline" asChild>
                        <Link to={`/iam/authorization?datasetId=${id}`}>权限与策略</Link>
                    </Button>
                    <Button
                        variant="outline"
                        disabled={syncing}
                        onClick={async () => {
                            setSyncing(true);
                            try {
                                const resp = (await syncDatasetSchema(id)) as { job?: DatasetJob } | undefined;
                                const job = (resp?.job ?? null) as DatasetJob | null;
                                if (!job) {
                                    throw new Error("未返回任务信息");
                                }
                                toast.success("已提交同步任务");
                                const completed = await pollJobStatus(job.id);
                                setLastJob(completed);
                                await loadDataset();
                                toast.success("已同步表结构");
                            } catch (e) {
                                console.error(e);
                                const message = e instanceof Error ? e.message : "同步失败";
                                toast.error(`同步失败：${message}`);
                            } finally {
                                setSyncing(false);
                            }
                        }}
                    >
                        {syncing ? "同步中…" : "同步表结构"}
                    </Button>
                    <Button
                        variant="outline"
                        onClick={async () => {
                            try {
                                const r = (await previewDataset(id, 50)) as any;
                                setDataPreview({ headers: r.headers || [], rows: r.rows || [] });
                                toast.success("已加载数据预览");
                            } catch (e) {
                                console.error(e);
                                toast.error("预览失败");
                            }
                        }}
                    >
                        数据预览
                    </Button>
                    <Button
                        variant="outline"
                        onClick={async () => {
                            try {
                                const data = (await previewSecurityViews(dataset.id)) as any;
                                setPreview(data || {});
                                toast.success("已生成预览");
                            } catch (e) {
                                console.error(e);
                                toast.error("预览失败");
                            }
                        }}
                    >
                        预览安全视图SQL
                    </Button>
                    <Button
                        onClick={async () => {
                            setBusy(true);
                            try {
                                const resp = (await applyPolicy(id)) as { job?: DatasetJob } | undefined;
                                const job = (resp?.job ?? null) as DatasetJob | null;
                                if (!job) {
                                    throw new Error("未返回任务信息");
                                }
                                toast.success("已提交策略生效任务");
                                const completed = await pollJobStatus(job.id);
                                setLastJob(completed);
                                await loadDataset();
                                toast.success("策略已生效，视图已生成/刷新");
                            } catch (e) {
                                console.error(e);
                                const message = e instanceof Error ? e.message : "策略生效失败";
                                toast.error(`策略生效失败：${message}`);
                            } finally {
                                setBusy(false);
                            }
                        }}
                        disabled={busy}
                    >
                        {busy ? "生效中…" : "策略生效"}
                    </Button>
                    <Button onClick={onSave} disabled={saving}>
                        {saving ? "保存中…" : "保存"}
                    </Button>
                </div>
            </CardHeader>
            {lastJob && (
                <div className="px-6 pb-2 text-xs text-muted-foreground">
                    最近任务：{jobStatusLabel(lastJob.status)}（{lastJob.message || "完成"}
                    {lastJob.finishedAt ? ` · ${formatJobTime(lastJob)}` : ""}）
                </div>
            )}
				<CardContent className="grid gap-4 md:grid-cols-2">
					<div className="grid gap-2">
						<Label>名称</Label>
						<Input
							value={dataset.name}
							onChange={(e) => setDataset({ ...(dataset as DatasetAsset), name: e.target.value })}
						/>
					</div>
					<div className="grid gap-2">
						<Label>负责人</Label>
						<Input
							value={dataset.owner}
							onChange={(e) => setDataset({ ...(dataset as DatasetAsset), owner: e.target.value })}
						/>
					</div>
					{/* 统一为 DATA_*，不再展示 legacy 密级 */}

					<div className="grid gap-2">
						<Label>数据密级（DATA_*）</Label>
						<Select
							value={(dataset.dataLevel as DataLevel) || "DATA_INTERNAL"}
							onValueChange={(v: DataLevel) => setDataset({ ...(dataset as DatasetAsset), dataLevel: v })}
						>
							<SelectTrigger>
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								{DATA_LEVELS.map((l) => (
									<SelectItem key={l.value} value={l.value}>
										{l.label}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>

					<div className="grid gap-2">
						<Label>作用域</Label>
						<Select
							value={(dataset.scope as Scope) || "DEPT"}
							onValueChange={(v: Scope) => setDataset({ ...(dataset as DatasetAsset), scope: v })}
						>
							<SelectTrigger>
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="DEPT">DEPT（部门）</SelectItem>
								<SelectItem value="INST">INST（研究所）</SelectItem>
							</SelectContent>
						</Select>
					</div>

					{((dataset.scope as Scope) || "DEPT") === "DEPT" ? (
						<div className="grid gap-2">
							<Label>所属部门</Label>
							<Select
								value={dataset.ownerDept || undefined}
								onValueChange={(v) => setDataset({ ...(dataset as DatasetAsset), ownerDept: v })}
							>
								<SelectTrigger>
									<SelectValue placeholder={deptLoading ? "加载中…" : "选择部门…"} />
								</SelectTrigger>
								<SelectContent>
									{deptOptions.map((d) => (
										<SelectItem key={d.code} value={d.code}>
											{d.nameZh || d.nameEn || d.code}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
					) : (
						<div className="grid gap-2">
							<Label>共享范围（INST）</Label>
							<Select
								value={(dataset.shareScope as ShareScope) || "SHARE_INST"}
								onValueChange={(v: ShareScope) => setDataset({ ...(dataset as DatasetAsset), shareScope: v })}
							>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="SHARE_INST">SHARE_INST（所内共享）</SelectItem>
									<SelectItem value="PUBLIC_INST">PUBLIC_INST（所内公开）</SelectItem>
								</SelectContent>
							</Select>
						</div>
					)}
					<div className="grid gap-2">
						<Label>标签（逗号分隔）</Label>
						<Input
							value={(dataset.tags || []).join(",")}
							onChange={(e) =>
								setDataset({
									...(dataset as DatasetAsset),
									tags: e.target.value
										.split(",")
										.map((s) => s.trim())
										.filter(Boolean),
								})
							}
						/>
					</div>
					<div className="md:col-span-2 grid gap-2">
						<Label>描述</Label>
						<Textarea
							value={dataset.description || ""}
							onChange={(e) => setDataset({ ...(dataset as DatasetAsset), description: e.target.value })}
						/>
					</div>
					<div className="grid gap-2">
						<Label>来源类型</Label>
						<Input disabled value={dataset.source?.sourceType || "EXTERNAL"} />
					</div>
					{hasHive && (
						<>
							<div className="grid gap-2">
								<Label>Hive Database</Label>
								<Input
									value={dataset.source?.hiveDatabase || ""}
									onChange={(e) =>
										setDataset({
											...(dataset as DatasetAsset),
											source: { ...(dataset.source as any), hiveDatabase: e.target.value },
										})
									}
								/>
							</div>
							<div className="grid gap-2">
								<Label>Hive Table</Label>
								<Input
									value={dataset.source?.hiveTable || ""}
									onChange={(e) =>
										setDataset({
											...(dataset as DatasetAsset),
											source: { ...(dataset.source as any), hiveTable: e.target.value },
										})
									}
								/>
							</div>
						</>
					)}
			</CardContent>
			</Card>

			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<div>
						<CardTitle className="text-base">质量监控</CardTitle>
						<p className="text-xs text-muted-foreground">查看最近一次质量检测状态，快速触发按需检测。</p>
					</div>
					<div className="flex items-center gap-2">
						{qualitySummary?.status && (
							<Badge variant="outline">{qualitySummary.status}</Badge>
						)}
						<Button variant="outline" size="sm" disabled={qualityRunning} onClick={handleTriggerQuality}>
							{qualityRunning ? "执行中…" : "立即检测"}
						</Button>
						<Button variant="ghost" size="sm" asChild>
							<Link to="/governance">前往治理</Link>
						</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-2 text-sm">
					{qualityLoading ? (
						<div className="text-muted-foreground">正在加载质量检测信息…</div>
					) : (
						<>
							<div>
								<span className="text-muted-foreground">最近一次：</span>
								<span className="text-foreground">{qualitySummary?.time ? new Date(qualitySummary.time).toLocaleString() : "暂无记录"}</span>
							</div>
							<div className="text-muted-foreground">结果：{qualitySummary?.message || qualitySummary?.status || "尚未执行"}</div>
						</>
					)}
				</CardContent>
			</Card>

			<Card>
				<CardHeader className="flex items-center justify-between">
					<CardTitle className="text-base">表结构与敏感标注</CardTitle>
					<Button size="sm" variant="outline" onClick={onAddColumn}>
						新增字段
					</Button>
				</CardHeader>
				<CardContent className="p-0">
					<ScrollArea className="h-[360px]">
						<table className="w-full min-w-[840px] table-fixed text-sm">
							<thead className="sticky top-0 bg-muted/40 text-left text-xs uppercase text-muted-foreground">
								<tr>
									<th className="px-3 py-2 w-[40px]">#</th>
									<th className="px-3 py-2">字段名</th>
									<th className="px-3 py-2">类型</th>
									<th className="px-3 py-2">敏感标签</th>
									<th className="px-3 py-2">操作</th>
								</tr>
							</thead>
							<tbody>
								{(dataset.table?.columns || []).map((c, idx) => (
									<tr key={c.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs text-muted-foreground">{idx + 1}</td>
										<td className="px-3 py-2">
											<Input
												value={c.name}
												onChange={(e) => {
													const cols = (dataset.table?.columns || []).map((it) =>
														it.id === c.id ? { ...it, name: e.target.value } : it,
													);
													setDataset({
														...(dataset as DatasetAsset),
														table: { ...(dataset.table as any), columns: cols },
													});
												}}
											/>
										</td>
										<td className="px-3 py-2">
											<Input
												value={c.dataType}
												onChange={(e) => {
													const cols = (dataset.table?.columns || []).map((it) =>
														it.id === c.id ? { ...it, dataType: e.target.value } : it,
													);
													setDataset({
														...(dataset as DatasetAsset),
														table: { ...(dataset.table as any), columns: cols },
													});
												}}
											/>
										</td>
										<td className="px-3 py-2">
											<Input
												value={(c.sensitiveTags || []).join(",")}
												onChange={(e) => {
													const tags = e.target.value
														.split(",")
														.map((s) => s.trim())
														.filter(Boolean);
													const cols = (dataset.table?.columns || []).map((it) =>
														it.id === c.id ? { ...it, sensitiveTags: tags } : it,
													);
													setDataset({
														...(dataset as DatasetAsset),
														table: { ...(dataset.table as any), columns: cols },
													});
												}}
												placeholder="例如: PII:phone, PII:id"
											/>
										</td>
										<td className="px-3 py-2">
											<Button size="sm" variant="ghost" onClick={() => onRemoveColumn(c.id)}>
												删除
											</Button>
										</td>
									</tr>
								))}
								{!(dataset.table?.columns || []).length && (
									<tr>
										<td colSpan={5} className="px-3 py-6 text-center text-xs text-muted-foreground">
											暂无字段
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</ScrollArea>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle className="text-base">访问策略（RLS/列级脱敏）</CardTitle>
				</CardHeader>
				<CardContent className="grid gap-3">
					<div className="grid gap-2">
						<Label>允许角色（逗号分隔）</Label>
						<Input value={allowRoles} onChange={(e) => setAllowRoles(e.target.value)} />
					</div>
					<div className="grid gap-2">
						<Label>行级过滤表达式（可选）</Label>
						<Textarea value={rowFilter} onChange={(e) => setRowFilter(e.target.value)} rows={3} />
					</div>
					<div className="grid gap-2">
						<Label>默认兜底策略</Label>
						<Select value={defaultMasking} onValueChange={setDefaultMasking}>
							<SelectTrigger className="w-[200px]">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="NONE">NONE</SelectItem>
								<SelectItem value="PARTIAL">PARTIAL</SelectItem>
								<SelectItem value="HASH">HASH</SelectItem>
								<SelectItem value="TOKENIZE">TOKENIZE</SelectItem>
								<SelectItem value="CUSTOM">CUSTOM</SelectItem>
							</SelectContent>
						</Select>
					</div>
				</CardContent>
			</Card>

            {preview && (
				<Card>
					<CardHeader>
						<CardTitle className="text-base">安全视图预览</CardTitle>
					</CardHeader>
					<CardContent>
						<div className="grid gap-3 md:grid-cols-2">
							{viewKeys.map((k) => (
								<div key={k} className="rounded border bg-muted/30 p-2">
									<div className="mb-1 text-xs font-semibold">{k}</div>
									<pre className="text-xs whitespace-pre-wrap">{String(preview[k])}</pre>
								</div>
							))}
						</div>
					</CardContent>
				</Card>
            )}

            {dataPreview && (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">数据预览</CardTitle>
                    </CardHeader>
                    <CardContent className="overflow-x-auto">
                        <table className="w-full min-w-[800px] table-fixed border-collapse text-sm">
                            <thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
                                <tr>
                                    {dataPreview.headers.map((h) => (
                                        <th key={h} className="px-3 py-2">{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {dataPreview.rows.map((row, i) => (
                                    <tr key={i} className="border-b border-border/40 last:border-none">
                                        {dataPreview.headers.map((h) => (
                                            <td key={h} className="px-3 py-2 truncate" title={String(row[h])}>
                                                {String(row[h])}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </CardContent>
                </Card>
            )}
		</div>
	);
}
