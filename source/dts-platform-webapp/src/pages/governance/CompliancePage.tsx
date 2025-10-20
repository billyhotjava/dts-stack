import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Icon } from "@/components/icon";
import { useRouter } from "@/routes/hooks";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Switch } from "@/ui/switch";
import { Textarea } from "@/ui/textarea";
import { Progress } from "@/ui/progress";
import { Checkbox } from "@/ui/checkbox";
import {
    createComplianceBatch,
    getComplianceBatch,
    listComplianceBatches,
    listQualityRules,
    deleteComplianceBatch,
    updateComplianceItem,
    getQualityRun,
} from "@/api/platformApi";
import { cn } from "@/utils";
import { normalizeClassification, type ClassificationLevel } from "@/utils/classification";

type DataLevel = "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_SECRET" | "DATA_CONFIDENTIAL";

const toLegacy = (v: DataLevel): ClassificationLevel =>
    v === "DATA_PUBLIC"
        ? "PUBLIC"
        : v === "DATA_INTERNAL"
            ? "INTERNAL"
            : v === "DATA_SECRET"
                ? "SECRET"
                : "CONFIDENTIAL";
const fromLegacy = (v: string): DataLevel => {
    const normalized = normalizeClassification(v);
    if (normalized === "PUBLIC") return "DATA_PUBLIC";
    if (normalized === "INTERNAL") return "DATA_INTERNAL";
    if (normalized === "SECRET") return "DATA_SECRET";
    if (normalized === "CONFIDENTIAL") return "DATA_CONFIDENTIAL";
    return "DATA_CONFIDENTIAL";
};

const SEVERITY_LABELS: Record<string, string> = {
    CRITICAL: "严重",
    HIGH: "高",
    MEDIUM: "中",
    LOW: "低",
    INFO: "提示",
};

const SEVERITY_BADGE_CLASS: Record<string, string> = {
    CRITICAL: "bg-red-100 text-red-700",
    HIGH: "bg-rose-100 text-rose-700",
    MEDIUM: "bg-amber-100 text-amber-800",
    LOW: "bg-sky-100 text-sky-700",
    INFO: "bg-slate-100 text-slate-600",
};

const BATCH_STATUS_META: Record<string, { label: string; variant: "default" | "secondary" | "destructive" | "outline" }> = {
    RUNNING: { label: "执行中", variant: "secondary" },
    QUEUED: { label: "排队中", variant: "outline" },
    COMPLETED: { label: "已完成", variant: "default" },
    FAILED: { label: "存在不合规", variant: "destructive" },
};

const ITEM_STATUS_LABELS: Record<string, string> = {
    QUEUED: "排队中",
    RUNNING: "执行中",
    PASSED: "合规通过",
    SUCCESS: "合规通过",
    COMPLIANT: "合规通过",
    FAILED: "存在不合规",
    NON_COMPLIANT: "存在不合规",
    BREACHED: "存在不合规",
    WAIVED: "风险接受",
    ACCEPTED_RISK: "风险接受",
};

const ITEM_STATUS_CLASS: Record<string, string> = {
    QUEUED: "bg-muted text-muted-foreground",
    RUNNING: "bg-sky-100 text-sky-700",
    PASSED: "bg-emerald-100 text-emerald-700",
    SUCCESS: "bg-emerald-100 text-emerald-700",
    COMPLIANT: "bg-emerald-100 text-emerald-700",
    FAILED: "bg-red-100 text-red-700",
    NON_COMPLIANT: "bg-red-100 text-red-700",
    BREACHED: "bg-red-100 text-red-700",
    WAIVED: "bg-amber-100 text-amber-800",
    ACCEPTED_RISK: "bg-amber-100 text-amber-800",
};

const ITEM_STATUS_OPTIONS = [
    { value: "PASSED", label: "合规通过" },
    { value: "FAILED", label: "存在不合规" },
    { value: "WAIVED", label: "风险接受/豁免" },
];

const BATCH_STATUS_OPTIONS = [
    { value: "ALL", label: "全部状态" },
    { value: "RUNNING", label: "执行中" },
    { value: "COMPLETED", label: "已完成" },
    { value: "FAILED", label: "存在不合规" },
];

interface RuleBindingView {
    datasetId?: string;
    datasetAlias?: string;
}

interface RuleOption {
    id: string;
    name: string;
    code?: string;
    severity: string;
    dataLevel: DataLevel;
    bindings: RuleBindingView[];
}

interface ComplianceBatchItem {
    id: string;
    batchId?: string;
    ruleId?: string;
    ruleName?: string;
    ruleCode?: string;
    ruleVersion?: number;
    ruleSeverity?: string;
    datasetId?: string;
    datasetAlias?: string;
    status: string;
    severity?: string;
    conclusion?: string | null;
    evidenceRef?: string | null;
    qualityRunId?: string;
    qualityRunStatus?: string;
    qualityRunStartedAt?: string | null;
    qualityRunFinishedAt?: string | null;
    qualityRunDurationMs?: number | null;
    qualityRunMessage?: string | null;
    createdDate?: string | null;
    lastUpdated?: string | null;
}

interface ComplianceBatch {
    id: string;
    name: string;
    templateCode?: string | null;
    status: string;
    progressPct: number;
    evidenceRequired: boolean;
    dataLevel: DataLevel;
    topSeverity?: string | null;
    datasetAliases: string[];
    scheduledAt?: string | null;
    startedAt?: string | null;
    finishedAt?: string | null;
    triggeredBy?: string | null;
    triggeredType?: string | null;
    summary?: string | null;
    metadataJson?: string | null;
    createdDate?: string | null;
    createdBy?: string | null;
    totalItems: number;
    completedItems: number;
    passedItems: number;
    failedItems: number;
    pendingItems: number;
    hasFailure: boolean;
    lastUpdated?: string | null;
    items: ComplianceBatchItem[];
}

interface QualityRunDetail {
    id: string;
    status?: string;
    severity?: string;
    startedAt?: string | null;
    finishedAt?: string | null;
    durationMs?: number | null;
    message?: string | null;
    ruleId?: string;
    ruleName?: string;
    datasetId?: string;
    datasetAlias?: string;
    metrics?: Record<string, unknown> | null;
    raw?: any;
}

interface BatchForm {
    name: string;
    templateCode: string;
    dataLevel: DataLevel;
    evidenceRequired: boolean;
    metadata: string;
    ruleIds: string[];
}

interface ItemDialogState {
    open: boolean;
    item: ComplianceBatchItem | null;
    status: string;
    conclusion: string;
    evidenceRef: string;
    submitting: boolean;
}

interface QualityRunDialogState {
    open: boolean;
    loading: boolean;
    runId?: string;
    run: QualityRunDetail | null;
    error: string | null;
}

const INITIAL_BATCH_FORM: BatchForm = {
    name: "",
    templateCode: "",
    dataLevel: "DATA_INTERNAL",
    evidenceRequired: true,
    metadata: "",
    ruleIds: [],
};

const INITIAL_ITEM_DIALOG: ItemDialogState = {
    open: false,
    item: null,
    status: "PASSED",
    conclusion: "",
    evidenceRef: "",
    submitting: false,
};

const INITIAL_QUALITY_RUN_DIALOG: QualityRunDialogState = {
    open: false,
    loading: false,
    runId: undefined,
    run: null,
    error: null,
};

const severityOrder = { CRITICAL: 5, HIGH: 4, MEDIUM: 3, LOW: 2, INFO: 1 } as const;

function normalizeBatch(raw: any, includeItems: boolean): ComplianceBatch {
    const status = String(raw?.status ?? "RUNNING").toUpperCase();
    const dataLevel = fromLegacy(String(raw?.dataLevel ?? "INTERNAL"));
    const items = includeItems && Array.isArray(raw?.items) ? raw.items.map(normalizeItem) : [];
    const maxSeverityFromItems = items.reduce((acc: string | null, item: ComplianceBatchItem) => {
        const severity = item.severity ? String(item.severity).toUpperCase() : null;
        if (!severity) return acc;
        const severityScore = (level: string | null | undefined) => {
            if (!level) return 0;
            const value = severityOrder[level as keyof typeof severityOrder];
            return typeof value === "number" ? value : 0;
        };
        if (!acc) return severity;
        return severityScore(severity) > severityScore(acc) ? severity : acc;
    }, null);
    const datasets = Array.isArray(raw?.datasets)
        ? raw.datasets.map((d: any) => d?.alias || d?.name || d?.datasetAlias || d?.datasetId).filter(Boolean)
        : Array.isArray(raw?.items)
        ? raw.items
              .map((item: any) => item?.datasetAlias || item?.datasetId)
              .filter((value: any) => typeof value === "string")
        : [];
    return {
        id: String(raw?.id ?? ""),
        name: raw?.name ?? "-",
        templateCode: raw?.templateCode ?? null,
        status,
        progressPct: Number.isFinite(raw?.progressPct) ? Number(raw.progressPct) : 0,
        evidenceRequired: Boolean(raw?.evidenceRequired),
        dataLevel,
        topSeverity: (raw?.maxSeverity || raw?.severity || maxSeverityFromItems || null)
            ? String(raw?.maxSeverity || raw?.severity || maxSeverityFromItems).toUpperCase()
            : null,
        datasetAliases: Array.from(new Set(datasets.map((item: any) => String(item)))),
        scheduledAt: raw?.scheduledAt ?? null,
        startedAt: raw?.startedAt ?? null,
        finishedAt: raw?.finishedAt ?? null,
        triggeredBy: raw?.triggeredBy ?? null,
        triggeredType: raw?.triggeredType ?? null,
        summary: raw?.summary ?? null,
        metadataJson: raw?.metadataJson ?? null,
        createdDate: raw?.createdDate ?? null,
        createdBy: raw?.createdBy ?? null,
        totalItems: Number.isFinite(raw?.totalItems) ? Number(raw.totalItems) : Array.isArray(raw?.items) ? raw.items.length : 0,
        completedItems: Number.isFinite(raw?.completedItems) ? Number(raw.completedItems) : 0,
        passedItems: Number.isFinite(raw?.passedItems) ? Number(raw.passedItems) : 0,
        failedItems: Number.isFinite(raw?.failedItems) ? Number(raw.failedItems) : 0,
        pendingItems: Number.isFinite(raw?.pendingItems)
            ? Number(raw.pendingItems)
            : Math.max(0, (Array.isArray(raw?.items) ? raw.items.length : 0) - (Number(raw?.completedItems) || 0)),
        hasFailure: Boolean(raw?.hasFailure),
        lastUpdated: raw?.lastUpdated ?? raw?.finishedAt ?? raw?.startedAt ?? raw?.createdDate ?? null,
        items,
    };
}

function normalizeItem(raw: any): ComplianceBatchItem {
    const status = String(raw?.status ?? "QUEUED").toUpperCase();
    return {
        id: String(raw?.id ?? ""),
        batchId: raw?.batchId ? String(raw.batchId) : undefined,
        ruleId: raw?.ruleId ? String(raw.ruleId) : undefined,
        ruleName: raw?.ruleName ?? raw?.ruleCode ?? "",
        ruleCode: raw?.ruleCode ?? undefined,
        ruleVersion: Number.isFinite(raw?.ruleVersion) ? Number(raw.ruleVersion) : undefined,
        ruleSeverity: raw?.ruleSeverity ?? raw?.severity ?? undefined,
        datasetId: raw?.datasetId ? String(raw.datasetId) : undefined,
        datasetAlias: raw?.datasetAlias ?? undefined,
        status,
        severity: raw?.severity ?? raw?.ruleSeverity ?? undefined,
        conclusion: raw?.conclusion ?? null,
        evidenceRef: raw?.evidenceRef ?? null,
        qualityRunId: raw?.qualityRunId ? String(raw.qualityRunId) : undefined,
        qualityRunStatus: raw?.qualityRunStatus ?? undefined,
        qualityRunStartedAt: raw?.qualityRunStartedAt ?? null,
        qualityRunFinishedAt: raw?.qualityRunFinishedAt ?? null,
        qualityRunDurationMs: Number.isFinite(raw?.qualityRunDurationMs) ? Number(raw.qualityRunDurationMs) : null,
        qualityRunMessage: raw?.qualityRunMessage ?? null,
        createdDate: raw?.createdDate ?? null,
        lastUpdated: raw?.lastUpdated ?? raw?.createdDate ?? null,
    };
}

function normalizeQualityRun(raw: any): QualityRunDetail {
    const duration =
        Number.isFinite(raw?.durationMs) && raw?.durationMs !== null
            ? Number(raw.durationMs)
            : Number.isFinite(raw?.elapsedMs)
            ? Number(raw.elapsedMs)
            : null;
    return {
        id: String(raw?.id ?? ""),
        status: raw?.status ?? raw?.runStatus ?? undefined,
        severity: raw?.severity ?? raw?.ruleSeverity ?? undefined,
        startedAt: raw?.startedAt ?? raw?.startTime ?? raw?.createdAt ?? null,
        finishedAt: raw?.finishedAt ?? raw?.endTime ?? raw?.finishTime ?? null,
        durationMs: duration,
        message: raw?.message ?? raw?.summary ?? raw?.errorMessage ?? null,
        ruleId: raw?.ruleId ? String(raw.ruleId) : undefined,
        ruleName: raw?.ruleName ?? raw?.qualityRuleName ?? undefined,
        datasetId: raw?.datasetId ? String(raw.datasetId) : undefined,
        datasetAlias: raw?.datasetAlias ?? raw?.datasetName ?? undefined,
        metrics:
            raw?.metrics && typeof raw.metrics === "object" && !Array.isArray(raw.metrics) ? (raw.metrics as Record<string, unknown>) : null,
        raw,
    };
}

function isUrl(value?: string | null): boolean {
    if (!value) return false;
    return /^https?:\/\//i.test(value);
}

function formatDateTime(value?: string | null): string {
    if (!value) return "-";
    try {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }
        return date.toLocaleString();
    } catch {
        return value;
    }
}

function formatDuration(ms?: number | null): string | null {
    if (!ms || ms <= 0) {
        return null;
    }
    if (ms < 1000) {
        return `${ms} ms`;
    }
    const seconds = ms / 1000;
    if (seconds < 60) {
        return `${seconds.toFixed(1)} s`;
    }
    const minutes = Math.floor(seconds / 60);
    const remaining = Math.round(seconds % 60);
    return `${minutes} 分 ${remaining} 秒`;
}

export default function CompliancePage() {
    const router = useRouter();
    const [statusFilter, setStatusFilter] = useState<string>("ALL");
    const [searchKeyword, setSearchKeyword] = useState<string>("");
    const [severityFilter, setSeverityFilter] = useState<string>("ALL");
    const [loading, setLoading] = useState(false);
    const [batches, setBatches] = useState<ComplianceBatch[]>([]);
    const [detailDrawerOpen, setDetailDrawerOpen] = useState(false);
    const [detailLoading, setDetailLoading] = useState(false);
    const [detailBatch, setDetailBatch] = useState<ComplianceBatch | null>(null);
    const [detailBatchId, setDetailBatchId] = useState<string | null>(null);
    const [createOpen, setCreateOpen] = useState(false);
    const [creating, setCreating] = useState(false);
    const [batchForm, setBatchForm] = useState<BatchForm>(INITIAL_BATCH_FORM);
    const [ruleOptions, setRuleOptions] = useState<RuleOption[]>([]);
    const [rulesLoading, setRulesLoading] = useState(false);
    const [itemDialog, setItemDialog] = useState<ItemDialogState>(INITIAL_ITEM_DIALOG);
    const [ruleKeyword, setRuleKeyword] = useState("");
    const [ruleSeverityFilter, setRuleSeverityFilter] = useState<string>("ALL");
    const [qualityRunDialog, setQualityRunDialog] = useState<QualityRunDialogState>(INITIAL_QUALITY_RUN_DIALOG);

    const filteredBatches = useMemo(() => {
        const keyword = searchKeyword.trim().toLowerCase();
        return batches.filter((batch) => {
            const matchKeyword =
                !keyword ||
                batch.name.toLowerCase().includes(keyword) ||
                (batch.templateCode ?? "").toLowerCase().includes(keyword) ||
                batch.datasetAliases.some((alias) => alias.toLowerCase().includes(keyword));
            const matchSeverity = severityFilter === "ALL" || batch.topSeverity === severityFilter;
            return matchKeyword && matchSeverity;
        });
    }, [batches, searchKeyword, severityFilter]);

    const filteredRuleOptions = useMemo(() => {
        const keyword = ruleKeyword.trim().toLowerCase();
        return ruleOptions.filter((rule) => {
            const matchKeyword =
                !keyword ||
                rule.name.toLowerCase().includes(keyword) ||
                (rule.code ?? "").toLowerCase().includes(keyword) ||
                rule.bindings.some((binding) =>
                    (binding.datasetAlias ?? binding.datasetId ?? "").toLowerCase().includes(keyword),
                );
            const matchSeverity = ruleSeverityFilter === "ALL" || rule.severity === ruleSeverityFilter;
            return matchKeyword && matchSeverity;
        });
    }, [ruleOptions, ruleKeyword, ruleSeverityFilter]);

    useEffect(() => {
        void fetchRules();
    }, []);

    useEffect(() => {
        void refreshBatches();
    }, [statusFilter]);

    async function fetchRules() {
        setRulesLoading(true);
        try {
            const data: any = await listQualityRules();
            const mapped: RuleOption[] = (Array.isArray(data) ? data : [])
                .filter((rule) => rule?.enabled !== false)
                .map((rule) => {
                    const severity = String(rule?.severity ?? "MEDIUM").toUpperCase();
                    const dataLevel = fromLegacy(String(rule?.dataLevel ?? "INTERNAL"));
                    const bindings: RuleBindingView[] = Array.isArray(rule?.bindings)
                        ? rule.bindings.map((binding: any) => ({
                              datasetId: binding?.datasetId ? String(binding.datasetId) : undefined,
                              datasetAlias: binding?.datasetAlias ?? undefined,
                          }))
                        : [];
                    return {
                        id: String(rule?.id ?? ""),
                        name: rule?.name ?? rule?.code ?? String(rule?.id ?? ""),
                        code: rule?.code ?? undefined,
                        severity,
                        dataLevel,
                        bindings,
                    };
                })
                .sort((a, b) => (severityOrder[b.severity as keyof typeof severityOrder] ?? 0) - (severityOrder[a.severity as keyof typeof severityOrder] ?? 0));
            setRuleOptions(mapped);
        } catch (error) {
            console.error(error);
            toast.error("加载质量规则失败");
        } finally {
            setRulesLoading(false);
        }
    }

    async function refreshBatches() {
        setLoading(true);
        try {
            const params: Record<string, any> = { limit: 20 };
            if (statusFilter !== "ALL") {
                params.status = statusFilter;
            }
            const data: any = await listComplianceBatches(params);
            const list = (Array.isArray(data) ? data : []).map((item: any) => normalizeBatch(item, false));
            setBatches(list);
            if (detailDrawerOpen && detailBatchId) {
                await loadBatchDetail(detailBatchId, { silent: true });
            }
        } catch (error) {
            console.error(error);
            toast.error("加载合规批次失败");
        } finally {
            setLoading(false);
        }
    }

    async function loadBatchDetail(batchId?: string, options?: { silent?: boolean }) {
        const targetId = batchId ?? detailBatchId;
        if (!targetId) {
            return;
        }
        if (!options?.silent) {
            setDetailBatch(null);
            setDetailDrawerOpen(true);
        }
        setDetailLoading(true);
        try {
            const data: any = await getComplianceBatch(targetId);
            setDetailBatch(normalizeBatch(data, true));
            setDetailBatchId(targetId);
        } catch (error) {
            console.error(error);
            toast.error("加载合规批次失败");
        } finally {
            setDetailLoading(false);
        }
    }

    async function openQualityRunDetail(runId: string) {
        setQualityRunDialog({ open: true, loading: true, runId, run: null, error: null });
        try {
            const data: any = await getQualityRun(runId);
            setQualityRunDialog({ open: true, loading: false, runId, run: normalizeQualityRun(data), error: null });
        } catch (error: any) {
            console.error(error);
            const message = error?.message || "加载质量运行详情失败";
            setQualityRunDialog({ open: true, loading: false, runId, run: null, error: message });
            toast.error(message);
        }
    }

    const closeQualityRunDetail = () => {
        setQualityRunDialog(INITIAL_QUALITY_RUN_DIALOG);
    };

    const handleCreateOpenChange = (open: boolean) => {
        setCreateOpen(open);
        if (!open) {
            setBatchForm(INITIAL_BATCH_FORM);
            setCreating(false);
            setRuleKeyword("");
            setRuleSeverityFilter("ALL");
        }
    };

    const toggleRuleSelection = (ruleId: string, checked: boolean | string) => {
        const nextChecked = typeof checked === "boolean" ? checked : checked === "indeterminate";
        setBatchForm((prev) => {
            const exists = prev.ruleIds.includes(ruleId);
            if (nextChecked && !exists) {
                return { ...prev, ruleIds: [...prev.ruleIds, ruleId] };
            }
            if (!nextChecked && exists) {
                return { ...prev, ruleIds: prev.ruleIds.filter((id) => id !== ruleId) };
            }
            return prev;
        });
    };

    const openBatchDetail = async (batchId: string) => {
        setDetailDrawerOpen(true);
        await loadBatchDetail(batchId, { silent: false });
    };

    const handleDeleteBatch = async (batchId: string, batchName: string) => {
        if (!window.confirm(`确定删除合规批次「${batchName || batchId}」吗？`)) {
            return;
        }
        try {
            await deleteComplianceBatch(batchId);
            toast.success("已删除合规批次");
            if (detailBatchId === batchId) {
                setDetailDrawerOpen(false);
                setDetailBatch(null);
                setDetailBatchId(null);
            }
            await refreshBatches();
        } catch (error) {
            console.error(error);
            toast.error("删除合规批次失败");
        }
    };

    async function handleCreateSubmit() {
        if (!batchForm.name.trim()) {
            toast.error("请填写批次名称");
            return;
        }
        if (!batchForm.ruleIds.length) {
            toast.error("请选择至少一个质量规则");
            return;
        }
        const uniqueRuleIds = Array.from(new Set(batchForm.ruleIds));
        if (!uniqueRuleIds.length) {
            toast.error("请选择至少一个质量规则");
            return;
        }
        let metadataPayload: any = undefined;
        if (batchForm.metadata.trim()) {
            try {
                const parsed = JSON.parse(batchForm.metadata);
                if (typeof parsed !== "object" || parsed === null) {
                    toast.error("元数据需要是 JSON 对象");
                    return;
                }
                metadataPayload = parsed;
            } catch (error) {
                toast.error("元数据需要是合法的 JSON");
                return;
            }
        }
        setCreating(true);
        try {
            await createComplianceBatch({
                name: batchForm.name.trim(),
                templateCode: batchForm.templateCode.trim() || undefined,
                dataLevel: toLegacy(batchForm.dataLevel),
                evidenceRequired: batchForm.evidenceRequired,
                metadata: metadataPayload,
                ruleIds: uniqueRuleIds,
            });
            toast.success("已创建合规批次");
            handleCreateOpenChange(false);
            await refreshBatches();
        } catch (error) {
            console.error(error);
            toast.error("创建合规批次失败");
        } finally {
            setCreating(false);
        }
    }

    async function handleUpdateItem() {
        if (!itemDialog.item) {
            return;
        }
        setItemDialog((prev) => ({ ...prev, submitting: true }));
        try {
            await updateComplianceItem(itemDialog.item.id, {
                status: itemDialog.status,
                conclusion: itemDialog.conclusion.trim() || null,
                evidenceRef: itemDialog.evidenceRef.trim() || null,
            });
            toast.success("合规结果已更新");
            setItemDialog(INITIAL_ITEM_DIALOG);
            await refreshBatches();
            await loadBatchDetail(itemDialog.item.batchId, { silent: true });
        } catch (error) {
            console.error(error);
            toast.error("更新合规结果失败");
            setItemDialog((prev) => ({ ...prev, submitting: false }));
        }
    }

    const selectedBatchStatus = (status: string) => BATCH_STATUS_META[status] ?? { label: status, variant: "outline" };

    return (
        <div className="space-y-6">
            <Card>
                <CardHeader className="flex flex-row items-center justify-between gap-3">
                    <div>
                        <CardTitle className="text-lg font-semibold">合规检查台账</CardTitle>
                        <p className="mt-1 text-sm text-muted-foreground">
                            结合质量规则与数据资产，按批次跟踪合规检查进度、结果与证据。
                        </p>
                    </div>
                    <Button variant="outline" size="sm" onClick={() => refreshBatches()} disabled={loading}>
                        <Icon icon="solar:refresh-bold" className={cn("mr-2 h-4 w-4", loading && "animate-spin")}
                        />刷新
                    </Button>
                </CardHeader>
            </Card>

            <Card>
                <CardHeader className="flex flex-col gap-4">
                    <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
                        <Input
                            placeholder="搜索批次名称 / 模板 / 数据集"
                            value={searchKeyword}
                            onChange={(event) => setSearchKeyword(event.target.value)}
                        />
                        <Select value={statusFilter} onValueChange={setStatusFilter}>
                            <SelectTrigger>
                                <SelectValue placeholder="状态筛选" />
                            </SelectTrigger>
                            <SelectContent>
                                {BATCH_STATUS_OPTIONS.map((option) => (
                                    <SelectItem key={option.value} value={option.value}>
                                        {option.label}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        <Select value={severityFilter} onValueChange={setSeverityFilter}>
                            <SelectTrigger>
                                <SelectValue placeholder="按严重程度" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ALL">全部严重程度</SelectItem>
                                {Object.entries(SEVERITY_LABELS).map(([value, label]) => (
                                    <SelectItem key={value} value={value}>
                                        {label}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex flex-wrap items-center justify-between gap-3">
                        <div className="text-xs text-muted-foreground">
                            当前展示 {filteredBatches.length} / {batches.length} 个批次
                        </div>
                        <div className="flex items-center gap-3">
                            <Button variant="outline" onClick={() => refreshBatches()} disabled={loading}>
                                <Icon icon="solar:refresh-circle-broken" className={cn("mr-2 h-4 w-4", loading && "animate-spin")}
                                />重新加载
                            </Button>
                            <Button onClick={() => handleCreateOpenChange(true)}>
                                <Icon icon="solar:add-circle-bold" className="mr-2 h-4 w-4" />新建合规批次
                            </Button>
                        </div>
                    </div>
                </CardHeader>
                <CardContent>
                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[960px] table-fixed border-collapse text-sm">
                            <thead className="bg-muted/50 text-left text-xs uppercase text-muted-foreground">
                                <tr>
                                    <th className="px-3 py-2 font-medium">批次名称</th>
                                    <th className="px-3 py-2 font-medium">进度</th>
                                    <th className="px-3 py-2 font-medium">统计</th>
                                    <th className="px-3 py-2 font-medium">触发</th>
                                    <th className="px-3 py-2 font-medium">状态</th>
                                    <th className="px-3 py-2 font-medium text-right">操作</th>
                                </tr>
                            </thead>
                            <tbody>
                                {loading ? (
                                    <tr>
                                        <td colSpan={6} className="px-3 py-8 text-center text-muted-foreground">
                                            正在加载合规批次...
                                        </td>
                                    </tr>
                                ) : filteredBatches.length ? (
                                    filteredBatches.map((batch) => {
                                        const statusMeta = selectedBatchStatus(batch.status);
                                        return (
                                            <tr
                                                key={batch.id}
                                                className="cursor-pointer border-b last:border-b-0 hover:bg-muted/40"
                                                onClick={() => openBatchDetail(batch.id)}
                                            >
                                                <td className="px-3 py-3">
                                                    <div className="flex items-center gap-2">
                                                        <div className="font-medium text-foreground">{batch.name}</div>
                                                        {batch.topSeverity && (
                                                            <Badge className={SEVERITY_BADGE_CLASS[batch.topSeverity] ?? "bg-slate-100 text-slate-600"}>
                                                                {SEVERITY_LABELS[batch.topSeverity] ?? batch.topSeverity}
                                                            </Badge>
                                                        )}
                                                    </div>
                                                    {batch.summary && (
                                                        <div className="mt-1 text-xs text-muted-foreground line-clamp-2">
                                                            {batch.summary}
                                                        </div>
                                                    )}
                                                    {batch.datasetAliases.length ? (
                                                        <div className="mt-1 text-xs text-muted-foreground">
                                                            覆盖数据集：{batch.datasetAliases.slice(0, 3).join("、")}
                                                            {batch.datasetAliases.length > 3 ? "…" : ""}
                                                        </div>
                                                    ) : null}
                                                </td>
                                                <td className="px-3 py-3">
                                                    <div className="mb-1 flex items-center gap-2 text-xs text-muted-foreground">
                                                        <span>{`${Math.round(batch.progressPct)}%`}</span>
                                                        <span>
                                                            {formatDateTime(batch.lastUpdated)}
                                                        </span>
                                                    </div>
                                                    <Progress value={Math.min(100, Math.max(0, batch.progressPct))} className="h-2" />
                                                </td>
                                                <td className="px-3 py-3 text-xs text-muted-foreground">
                                                    <div>总计 {batch.totalItems}</div>
                                                    <div className="mt-0.5">已完成 {batch.completedItems}</div>
                                                    <div className="mt-0.5">不合规 {batch.failedItems}</div>
                                                </td>
                                                <td className="px-3 py-3 text-xs text-muted-foreground">
                                                    <div>{batch.triggeredBy || "-"}</div>
                                                    <div className="mt-0.5">{formatDateTime(batch.scheduledAt)}</div>
                                                </td>
                                                <td className="px-3 py-3">
                                                    <Badge variant={statusMeta.variant}>{statusMeta.label}</Badge>
                                                </td>
                                                <td className="px-3 py-3">
                                                    <div className="flex justify-end gap-2">
                                                        <Button
                                                            variant="outline"
                                                            size="sm"
                                                            onClick={(event) => {
                                                                event.stopPropagation();
                                                                void openBatchDetail(batch.id);
                                                            }}
                                                        >
                                                            <Icon name="Eye" className="mr-1 h-4 w-4" />
                                                            查看
                                                        </Button>
                                                        <Button
                                                            variant="ghost"
                                                            size="sm"
                                                            className="text-destructive hover:text-destructive"
                                                            onClick={(event) => {
                                                                event.stopPropagation();
                                                                void handleDeleteBatch(batch.id, batch.name);
                                                            }}
                                                        >
                                                            <Icon name="Trash2" className="mr-1 h-4 w-4" />
                                                            删除
                                                        </Button>
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })
                                ) : (
                                    <tr>
                                        <td colSpan={6} className="px-3 py-8 text-center text-muted-foreground">
                                            {batches.length
                                                ? "没有符合筛选条件的批次，请调整筛选项。"
                                                : "暂无合规批次记录，点击右上角「新建合规批次」。"}
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </CardContent>
            </Card>

            <Dialog
                open={detailDrawerOpen}
                onOpenChange={(open) => {
                    setDetailDrawerOpen(open);
                    if (!open) {
                        setDetailBatch(null);
                        setDetailBatchId(null);
                    }
                }}
            >
                <DialogContent className="mx-auto max-h-[85vh] w-full max-w-5xl overflow-y-auto rounded-xl border bg-background shadow-xl">
                    <DialogHeader>
                        <DialogTitle>{detailBatch?.name ?? "合规批次详情"}</DialogTitle>
                        <DialogDescription>
                            {detailBatch?.summary || "查看批次中各检查项的执行状态和证据信息"}
                        </DialogDescription>
                    </DialogHeader>
                    <div className="flex flex-col gap-4 px-4 pb-6">
                        {detailLoading ? (
                            <div className="py-12 text-center text-muted-foreground">正在加载...</div>
                        ) : detailBatch ? (
                            <>
                                <div className="flex flex-wrap items-center gap-3 text-sm">
                                    <Badge variant={selectedBatchStatus(detailBatch.status).variant}>
                                        {selectedBatchStatus(detailBatch.status).label}
                                    </Badge>
                                    {detailBatch.evidenceRequired ? (
                                        <Badge variant="secondary">需提交证据</Badge>
                                    ) : (
                                        <Badge variant="outline">证据可选</Badge>
                                    )}
                                    {detailBatch.hasFailure && <Badge variant="destructive">存在不合规项</Badge>}
                                </div>

                                <Card>
                                    <CardContent className="grid gap-4 pt-4 sm:grid-cols-2 lg:grid-cols-4">
                                        <div>
                                            <div className="text-xs text-muted-foreground">总检查项</div>
                                            <div className="mt-1 text-lg font-semibold text-foreground">{detailBatch.totalItems}</div>
                                        </div>
                                        <div>
                                            <div className="text-xs text-muted-foreground">已完成</div>
                                            <div className="mt-1 text-lg font-semibold text-foreground">
                                                {detailBatch.completedItems}
                                            </div>
                                        </div>
                                        <div>
                                            <div className="text-xs text-muted-foreground">合规通过</div>
                                            <div className="mt-1 text-lg font-semibold text-foreground">{detailBatch.passedItems}</div>
                                        </div>
                                        <div>
                                            <div className="text-xs text-muted-foreground">不合规</div>
                                            <div className="mt-1 text-lg font-semibold text-destructive">
                                                {detailBatch.failedItems}
                                            </div>
                                        </div>
                                    </CardContent>
                                </Card>

                                <div className="grid gap-4 md:grid-cols-2">
                                    <Card>
                                        <CardHeader className="pb-2">
                                            <CardTitle className="text-base">调度信息</CardTitle>
                                        </CardHeader>
                                        <CardContent className="space-y-2 text-xs text-muted-foreground">
                                            <div>触发人：{detailBatch.triggeredBy || "-"}</div>
                                            <div>触发方式：{detailBatch.triggeredType || "手动触发"}</div>
                                            <div>计划时间：{formatDateTime(detailBatch.scheduledAt)}</div>
                                            <div>开始时间：{formatDateTime(detailBatch.startedAt)}</div>
                                            <div>完成时间：{formatDateTime(detailBatch.finishedAt)}</div>
                                        </CardContent>
                                    </Card>
                                    <Card>
                                        <CardHeader className="pb-2 flex flex-row items-center justify-between">
                                            <CardTitle className="text-base">元数据</CardTitle>
                                            {detailBatch.metadataJson && (
                                                <Button
                                                    variant="ghost"
                                                    size="icon"
                                                    className="h-8 w-8"
                                                    onClick={() => {
                                                        if (navigator?.clipboard?.writeText) {
                                                            navigator.clipboard
                                                                .writeText(detailBatch.metadataJson || "")
                                                                .then(() => toast.success("已复制元数据"))
                                                                .catch(() => toast.error("复制失败"));
                                                        } else {
                                                            toast.error("当前环境不支持复制");
                                                        }
                                                    }}
                                                >
                                                    <Icon icon="solar:copy-bold" className="h-4 w-4" />
                                                </Button>
                                            )}
                                        </CardHeader>
                                        <CardContent>
                                            <ScrollArea className="max-h-40 rounded-md border bg-muted/30 p-3 text-xs text-muted-foreground">
                                                {detailBatch.metadataJson ? (
                                                    <pre className="whitespace-pre-wrap break-all">
                                                        {detailBatch.metadataJson}
                                                    </pre>
                                                ) : (
                                                    <div className="text-muted-foreground">无附加元数据</div>
                                                )}
                                            </ScrollArea>
                                        </CardContent>
                                    </Card>
                                </div>

                                <Card>
                                    <CardHeader className="flex flex-row items-center justify-between">
                                        <CardTitle className="text-base">检查项列表</CardTitle>
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => loadBatchDetail(undefined, { silent: true })}
                                            disabled={detailLoading}
                                        >
                                            <Icon icon="solar:refresh-bold" className={cn("mr-2 h-4 w-4", detailLoading && "animate-spin")}
                                            />刷新
                                        </Button>
                                    </CardHeader>
                                    <CardContent>
                                        <ScrollArea className="h-[420px]">
                                            <table className="w-full min-w-[920px] table-fixed border-collapse text-sm">
                                                <thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
                                                    <tr>
                                                        <th className="px-3 py-2 font-medium">规则</th>
                                                        <th className="px-3 py-2 font-medium">绑定数据集</th>
                                                        <th className="px-3 py-2 font-medium">状态</th>
                                                        <th className="px-3 py-2 font-medium">结论/证据</th>
                                                        <th className="px-3 py-2 font-medium">质量运行</th>
                                                        <th className="px-3 py-2 font-medium">操作</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {detailBatch.items.length ? (
                                                        detailBatch.items.map((item) => {
                                                            const normalizedStatus = item.status?.toUpperCase?.() ?? "";
                                                            const statusLabel = ITEM_STATUS_LABELS[normalizedStatus] ?? item.status;
                                                            const statusClass = ITEM_STATUS_CLASS[normalizedStatus] ?? "bg-muted text-muted-foreground";
                                                            const severityClass = SEVERITY_BADGE_CLASS[item.ruleSeverity?.toUpperCase?.() ?? ""] ?? "bg-slate-100 text-slate-600";
                                                            return (
                                                                <tr key={item.id} className="border-b last:border-b-0">
                                                                    <td className="px-3 py-3 align-top">
                                                                        <div className="font-medium text-foreground">{item.ruleName || "-"}</div>
                                                                        <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                                                                            <span>版本 {item.ruleVersion ?? "-"}</span>
                                                                            {item.ruleSeverity && (
                                                                                <span className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-[11px]", severityClass)}>
                                                                                    {SEVERITY_LABELS[item.ruleSeverity?.toUpperCase?.() ?? ""] || item.ruleSeverity}
                                                                                </span>
                                                                            )}
                                                                        </div>
                                                                    </td>
                                                                    <td className="px-3 py-3 align-top text-xs text-muted-foreground">
                                                                        <div>{item.datasetAlias || item.datasetId || "-"}</div>
                                                                        {item.datasetId && (
                                                                            <Button
                                                                                variant="link"
                                                                                className="px-0 text-xs"
                                                                                onClick={() => {
                                                                                    if (item.datasetId) {
                                                                                        router.push(`/catalog/datasets/${item.datasetId}`);
                                                                                    }
                                                                                }}
                                                                            >
                                                                                查看数据集
                                                                            </Button>
                                                                        )}
                                                                    </td>
                                                                    <td className="px-3 py-3 align-top">
                                                                        <span className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium", statusClass)}>
                                                                            {statusLabel}
                                                                        </span>
                                                                        <div className="mt-1 text-xs text-muted-foreground">
                                                                            更新时间：{formatDateTime(item.lastUpdated)}
                                                                        </div>
                                                                    </td>
                                                                    <td className="px-3 py-3 align-top text-xs text-muted-foreground">
                                                                        <div className="whitespace-pre-line text-foreground">
                                                                            {item.conclusion || "-"}
                                                                        </div>
                                                                        {item.evidenceRef ? (
                                                                            <div className="mt-1">
                                                                                {isUrl(item.evidenceRef) ? (
                                                                                    <a
                                                                                        href={item.evidenceRef || undefined}
                                                                                        target="_blank"
                                                                                        rel="noreferrer"
                                                                                        className="text-primary hover:underline"
                                                                                    >
                                                                                        查看证据
                                                                                    </a>
                                                                                ) : (
                                                                                    <span className="break-all">{item.evidenceRef}</span>
                                                                                )}
                                                                            </div>
                                                                        ) : null}
                                                                    </td>
                                                                    <td className="px-3 py-3 align-top text-xs text-muted-foreground">
                                                                        {item.qualityRunStatus ? (
                                                                            <>
                                                                                <div>状态：{item.qualityRunStatus}</div>
                                                                                {item.qualityRunDurationMs ? (
                                                                                    <div>耗时：{formatDuration(item.qualityRunDurationMs)}</div>
                                                                                ) : null}
                                                                                <div>开始：{formatDateTime(item.qualityRunStartedAt)}</div>
                                                                                <div>结束：{formatDateTime(item.qualityRunFinishedAt)}</div>
                                                                                {item.qualityRunMessage && (
                                                                                    <div className="mt-1 text-[11px] text-muted-foreground line-clamp-3">
                                                                                        {item.qualityRunMessage}
                                                                                    </div>
                                                                                )}
                                                                            </>
                                                                        ) : (
                                                                            <span>-</span>
                                                                        )}
                                                                    </td>
                                                                    <td className="px-3 py-3 align-top">
                                                                        <div className="flex flex-col gap-2 text-xs">
                                                                            <Button
                                                                                variant="outline"
                                                                                size="sm"
                                                                                onClick={() =>
                                                                                    setItemDialog({
                                                                                        open: true,
                                                                                        item,
                                                                                        status: normalizedStatus || "PASSED",
                                                                                        conclusion: item.conclusion || "",
                                                                                        evidenceRef: item.evidenceRef || "",
                                                                                        submitting: false,
                                                                                    })
                                                                                }
                                                                            >
                                                                                登记结果
                                                                            </Button>
                                                                            {item.qualityRunId && (
                                                                                <Button
                                                                                    variant="ghost"
                                                                                    size="sm"
                                                                                    onClick={() => void openQualityRunDetail(item.qualityRunId!)}
                                                                                >
                                                                                    查看质量运行
                                                                                </Button>
                                                                            )}
                                                                        </div>
                                                                    </td>
                                                                </tr>
                                                            );
                                                        })
                                                    ) : (
                                                        <tr>
                                                            <td colSpan={6} className="px-3 py-8 text-center text-muted-foreground">
                                                                该批次尚未生成检查项。
                                                            </td>
                                                        </tr>
                                                    )}
                                                </tbody>
                                            </table>
                                        </ScrollArea>
                                    </CardContent>
                                </Card>
                            </>
                        ) : (
                            <div className="py-12 text-center text-muted-foreground">
                                请选择左侧批次以查看详情。
                            </div>
                        )}
                    </div>
                    <DialogFooter className="border-t bg-background">
                        <DialogClose asChild>
                            <Button variant="outline">关闭</Button>
                        </DialogClose>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            <Dialog open={createOpen} onOpenChange={handleCreateOpenChange}>
                <DialogContent className="sm:max-w-3xl">
                    <DialogHeader>
                        <DialogTitle>新建合规批次</DialogTitle>
                        <p className="text-sm text-muted-foreground">
                            选择需要执行的质量规则，系统将按绑定数据集生成检查项并触发质量作业。
                        </p>
                    </DialogHeader>
                    <div className="space-y-4">
                        <div className="grid gap-4 md:grid-cols-2">
                            <div className="space-y-2">
                                <Label>批次名称</Label>
                                <Input
                                    value={batchForm.name}
                                    onChange={(event) => setBatchForm((prev) => ({ ...prev, name: event.target.value }))}
                                    placeholder="如：季度合规扫描"
                                />
                            </div>
                            <div className="space-y-2">
                                <Label>模板代号（可选）</Label>
                                <Input
                                    value={batchForm.templateCode}
                                    onChange={(event) =>
                                        setBatchForm((prev) => ({ ...prev, templateCode: event.target.value }))
                                    }
                                    placeholder="例如：QC_Q1"
                                />
                            </div>
                            <div className="space-y-2">
                                <Label className="flex items-center justify-between">
                                    <span>证据必填</span>
                                    <Switch
                                        checked={batchForm.evidenceRequired}
                                        onCheckedChange={(checked) =>
                                            setBatchForm((prev) => ({ ...prev, evidenceRequired: Boolean(checked) }))
                                        }
                                    />
                                </Label>
                                <p className="text-xs text-muted-foreground">
                                    启用后，登记结果时必须填写证据链接或说明。
                                </p>
                            </div>
                        </div>
                        <div className="space-y-2">
                            <Label>附加元数据（JSON，可选）</Label>
                            <Textarea
                                value={batchForm.metadata}
                                onChange={(event) => setBatchForm((prev) => ({ ...prev, metadata: event.target.value }))}
                                placeholder='{"campaign":"2025Q1"}'
                                rows={3}
                            />
                        </div>
                        <div className="space-y-3">
                            <div className="flex items-center justify-between">
                                <Label>选择质量规则</Label>
                                <span className="text-xs text-muted-foreground">
                                    已选择 {batchForm.ruleIds.length} / {ruleOptions.length}
                                </span>
                            </div>
                            <ScrollArea className="h-64 rounded-md border">
                                <div className="border-b bg-muted/20 p-3">
                                    <div className="grid gap-2 md:grid-cols-2">
                                        <Input
                                            placeholder="搜索质量规则 / 数据集"
                                            value={ruleKeyword}
                                            onChange={(event) => setRuleKeyword(event.target.value)}
                                        />
                                        <Select value={ruleSeverityFilter} onValueChange={setRuleSeverityFilter}>
                                            <SelectTrigger>
                                                <SelectValue placeholder="严重程度" />
                                            </SelectTrigger>
                                            <SelectContent>
                                                <SelectItem value="ALL">全部严重程度</SelectItem>
                                                {Object.entries(SEVERITY_LABELS).map(([value, label]) => (
                                                    <SelectItem key={value} value={value}>
                                                        {label}
                                                    </SelectItem>
                                                ))}
                                            </SelectContent>
                                        </Select>
                                    </div>
                                    <div className="mt-2 text-xs text-muted-foreground">
                                        筛选结果 {filteredRuleOptions.length} / {ruleOptions.length}
                                    </div>
                                </div>
                                <div className="divide-y">
                                    {rulesLoading ? (
                                        <div className="p-4 text-sm text-muted-foreground">正在加载质量规则...</div>
                                    ) : filteredRuleOptions.length ? (
                                        filteredRuleOptions.map((rule) => (
                                            <label key={rule.id} className="flex items-start gap-3 p-3">
                                                <Checkbox
                                                    className="mt-1"
                                                    checked={batchForm.ruleIds.includes(rule.id)}
                                                    onCheckedChange={(checked) => toggleRuleSelection(rule.id, checked)}
                                                />
                                                <div className="flex-1 space-y-1">
                                                    <div className="flex flex-wrap items-center gap-2 text-sm">
                                                        <span className="font-medium text-foreground">{rule.name}</span>
                                                        <span
                                                            className={cn(
                                                                "inline-flex items-center rounded-full px-2 py-0.5 text-[11px]",
                                                                SEVERITY_BADGE_CLASS[rule.severity] ?? "bg-slate-100 text-slate-600",
                                                            )}
                                                        >
                                                            {SEVERITY_LABELS[rule.severity] ?? rule.severity}
                                                        </span>
                                                    </div>
                                                    <div className="text-xs text-muted-foreground">
                                                        {rule.bindings.length
                                                            ? rule.bindings
                                                                  .map((binding) => binding.datasetAlias || binding.datasetId || "未命名数据集")
                                                                  .join("、")
                                                            : "尚未绑定数据集"}
                                                    </div>
                                                </div>
                                            </label>
                                        ))
                                    ) : (
                                        <div className="p-4 text-sm text-muted-foreground">
                                            {ruleOptions.length
                                                ? "没有符合筛选条件的质量规则，请调整筛选项。"
                                                : "暂无可用的质量规则，请先在「质量规则」页面创建。"}
                                        </div>
                                    )}
                                </div>
                            </ScrollArea>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => handleCreateOpenChange(false)}>
                            取消
                        </Button>
                        <Button onClick={() => void handleCreateSubmit()} disabled={creating}>
                            {creating ? "创建中..." : "创建"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            <Dialog
                open={qualityRunDialog.open}
                onOpenChange={(open) => {
                    if (!open) {
                        closeQualityRunDetail();
                    }
                }}
            >
                <DialogContent className="sm:max-w-lg">
                    <DialogHeader>
                        <DialogTitle>质量运行详情</DialogTitle>
                        <DialogDescription>查看质量检测的执行状态、耗时和相关说明。</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-4 text-sm">
                        {qualityRunDialog.loading ? (
                            <div className="text-muted-foreground">正在加载质量运行详情...</div>
                        ) : qualityRunDialog.error ? (
                            <div className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-destructive">
                                {qualityRunDialog.error}
                            </div>
                        ) : qualityRunDialog.run ? (
                            <div className="space-y-3">
                                <div>
                                    <div className="text-xs text-muted-foreground">运行 ID</div>
                                    <div className="font-mono text-xs text-foreground">
                                        {qualityRunDialog.run.id || qualityRunDialog.runId}
                                    </div>
                                </div>
                                <div className="flex flex-wrap items-center gap-2 text-xs">
                                    <span className="text-muted-foreground">状态</span>
                                    <Badge variant="outline">{qualityRunDialog.run.status || "未知"}</Badge>
                                </div>
                                <div className="flex flex-wrap items-center gap-2 text-xs">
                                    <span className="text-muted-foreground">严重程度</span>
                                    <span>{qualityRunDialog.run.severity || "-"}</span>
                                </div>
                                <div className="space-y-1 text-xs text-muted-foreground">
                                    <div>开始：{formatDateTime(qualityRunDialog.run.startedAt)}</div>
                                    <div>结束：{formatDateTime(qualityRunDialog.run.finishedAt)}</div>
                                    <div>耗时：{formatDuration(qualityRunDialog.run.durationMs) ?? "-"}</div>
                                </div>
                                {(qualityRunDialog.run.ruleName || qualityRunDialog.run.ruleId) && (
                                    <div className="text-xs text-muted-foreground">
                                        关联规则：{qualityRunDialog.run.ruleName || qualityRunDialog.run.ruleId}
                                    </div>
                                )}
                                {(qualityRunDialog.run.datasetAlias || qualityRunDialog.run.datasetId) && (
                                    <div className="text-xs text-muted-foreground">
                                        关联数据集：{qualityRunDialog.run.datasetAlias || qualityRunDialog.run.datasetId}
                                    </div>
                                )}
                                <div className="space-y-1 text-xs">
                                    <div className="text-muted-foreground">说明</div>
                                    <div className="whitespace-pre-wrap break-all text-muted-foreground">
                                        {qualityRunDialog.run.message || "暂无说明"}
                                    </div>
                                </div>
                                {qualityRunDialog.run.metrics && (
                                    <div className="space-y-1 text-xs">
                                        <div className="text-muted-foreground">指标输出</div>
                                        <ScrollArea className="max-h-40 rounded-md border bg-muted/30 p-3">
                                            <pre className="whitespace-pre-wrap break-all">
                                                {JSON.stringify(qualityRunDialog.run.metrics, null, 2)}
                                            </pre>
                                        </ScrollArea>
                                    </div>
                                )}
                            </div>
                        ) : (
                            <div className="text-muted-foreground">暂无质量运行数据。</div>
                        )}
                    </div>
                    <DialogFooter className="border-t bg-muted/30">
                        <DialogClose asChild>
                            <Button variant="outline">关闭</Button>
                        </DialogClose>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            <Dialog
                open={itemDialog.open}
                onOpenChange={(open) => {
                    if (!open) {
                        setItemDialog(INITIAL_ITEM_DIALOG);
                    } else {
                        setItemDialog((prev) => ({ ...prev, open }));
                    }
                }}
            >
                <DialogContent className="sm:max-w-lg">
                    <DialogHeader>
                        <DialogTitle>登记合规结果</DialogTitle>
                        <p className="text-sm text-muted-foreground">
                            同步填写结论与证据，方便后续复核。
                        </p>
                    </DialogHeader>
                    <div className="space-y-4">
                        <div className="space-y-2">
                            <Label>检查项</Label>
                            <div className="rounded-md border bg-muted/40 p-3 text-sm text-muted-foreground">
                                {itemDialog.item?.ruleName || "-"}
                            </div>
                        </div>
                        <div className="space-y-2">
                            <Label>状态</Label>
                            <Select
                                value={itemDialog.status}
                                onValueChange={(value) => setItemDialog((prev) => ({ ...prev, status: value }))}
                            >
                                <SelectTrigger>
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    {ITEM_STATUS_OPTIONS.map((option) => (
                                        <SelectItem key={option.value} value={option.value}>
                                            {option.label}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <Label>结论摘要</Label>
                            <Textarea
                                value={itemDialog.conclusion}
                                onChange={(event) =>
                                    setItemDialog((prev) => ({ ...prev, conclusion: event.target.value }))
                                }
                                rows={3}
                                placeholder="概述检查结果、整改建议或豁免说明"
                            />
                        </div>
                        <div className="space-y-2">
                            <Label>证据链接或说明</Label>
                            <Textarea
                                value={itemDialog.evidenceRef}
                                onChange={(event) =>
                                    setItemDialog((prev) => ({ ...prev, evidenceRef: event.target.value }))
                                }
                                rows={3}
                                placeholder="可填写 wiki 链接、工单号或备注"
                            />
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setItemDialog(INITIAL_ITEM_DIALOG)}>
                            取消
                        </Button>
                        <Button onClick={() => void handleUpdateItem()} disabled={itemDialog.submitting}>
                            {itemDialog.submitting ? "保存中..." : "保存"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}
