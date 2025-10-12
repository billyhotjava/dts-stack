import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
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
} from "@/api/platformApi";
import type { DatasetAsset, DatasetJob, DatasetJobStatus, DataLevel, Scope, ShareScope } from "@/types/catalog";
import deptService, { type DeptDto } from "@/api/services/deptService";
import { useActiveDept } from "@/store/contextStore";
import { useRouter } from "@/routes/hooks";

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
	const activeDept = useActiveDept();
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
    const [deptOptions, setDeptOptions] = useState<DeptDto[]>([]);
    const [deptLoading, setDeptLoading] = useState(false);
    const router = useRouter();
    const nonRootDeptOptions = useMemo(() => deptOptions.filter((d) => d.parentId != null && d.parentId !== 0), [deptOptions]);

    const loadDataset = async (withSpinner = false) => {
        if (withSpinner) setLoading(true);
        try {
            const data = (await getDataset(id)) as any;
            // Normalize server payload for UI editing
            const normalized: any = {
                ...data,
                // Ensure tags is an array for the input join/split logic below
                tags: Array.isArray((data as any)?.tags)
                    ? (data as any).tags
                    : (typeof (data as any)?.tags === "string" && (data as any).tags.trim().length
                        ? String((data as any).tags)
                              .split(",")
                              .map((s) => s.trim())
                              .filter(Boolean)
                        : []),
            };
            setDataset(normalized);
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
	}, [id]);

    useEffect(() => {
        let mounted = true;
        const scope = ((dataset?.scope as string) || "DEPT").toUpperCase();
        if (scope === "DEPT") {
            setDeptLoading(true);
            deptService
                .listDepartments()
                .then((list) => mounted && setDeptOptions(list || []))
                .finally(() => mounted && setDeptLoading(false));
        }
        // Enforce INST scope cannot use PRIVATE_DEPT; auto-correct if necessary
        if (scope === "INST" && dataset?.shareScope === "PRIVATE_DEPT") {
            setDataset({ ...(dataset as any), shareScope: "SHARE_INST" });
        }
        return () => {
            mounted = false;
        };
    }, [dataset?.scope, dataset?.id]);

    // If dataset is scoped to DEPT but ownerDept is missing, prefill with active dept from context.
    useEffect(() => {
        if (!dataset) return;
        const scope = ((dataset.scope as string) || "DEPT").toUpperCase();
        if (scope === "DEPT" && (!dataset.ownerDept || String(dataset.ownerDept).trim() === "") && activeDept) {
            setDataset({ ...(dataset as DatasetAsset), ownerDept: activeDept });
        }
    }, [dataset, activeDept]);

    // Disallow choosing root when scope=DEPT: if当前值为根则清空
    useEffect(() => {
        if (!dataset) return;
        const scope = ((dataset.scope as string) || "DEPT").toUpperCase();
        if (scope !== "DEPT") return;
        if (!dataset.ownerDept) return;
        const root = deptOptions.find((d) => d.parentId == null || d.parentId === 0);
        if (root && String(root.code) === String(dataset.ownerDept)) {
            setDataset({ ...(dataset as DatasetAsset), ownerDept: undefined as any });
        }
    }, [dataset?.scope, dataset?.ownerDept, deptOptions]);


    const toLegacyClassification = (dl?: string): string => {
        const v = String(dl || "").toUpperCase();
        if (v === "DATA_PUBLIC") return "PUBLIC";
        if (v === "DATA_INTERNAL") return "INTERNAL";
        if (v === "DATA_SECRET") return "SECRET";
        if (v === "DATA_TOP_SECRET") return "TOP_SECRET";
        return "INTERNAL";
    };

    const onSave = async () => {
        if (!dataset) return;
        // Validate scope dependent fields
        const scope = (dataset.scope as string) || "DEPT";
        if (scope === "DEPT" && !String(dataset.ownerDept || "").trim()) {
            toast.error("请选择所属部门（DEPT 范围需要部门）");
            return;
        }
        if (scope === "INST" && !dataset.shareScope) {
            toast.error("请选择共享范围（INST 范围需要 shareScope）");
            return;
        }
        // Sanitize payload for backend
        const payload: any = {
            ...dataset,
            classification: toLegacyClassification(dataset.dataLevel as string),
            dataLevel: dataset.dataLevel,
            scope,
            ownerDept: scope === "DEPT" ? dataset.ownerDept || undefined : undefined,
            shareScope: scope === "INST" ? dataset.shareScope || "SHARE_INST" : undefined,
            // Backend expects a string; submit as comma-separated list
            tags: Array.isArray((dataset as any).tags)
                ? ((dataset as any).tags as string[]).join(",")
                : String((dataset as any).tags || ""),
        };
        setSaving(true);
        try {
            await updateDataset(dataset.id, payload);
            await upsertAccessPolicy(dataset.id, { allowRoles, rowFilter, defaultMasking });
            toast.success("已保存");
            // 返回目录列表
            try { router.push("/catalog/assets"); } catch {}
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
						<Label>数据密级</Label>
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
                            onValueChange={(v) =>
                                setDataset({
                                    ...(dataset as DatasetAsset),
                                    ownerDept: v,
                                    // Ensure scope is explicitly DEPT when choosing a department
                                    scope: "DEPT" as any,
                                    // Clear shareScope to avoid accidental mixed state
                                    shareScope: undefined as any,
                                })
                            }
                        >
								<SelectTrigger>
									<SelectValue placeholder={deptLoading ? "加载中…" : "选择部门…"} />
								</SelectTrigger>
								<SelectContent>
                                    {nonRootDeptOptions.map((d) => (
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
