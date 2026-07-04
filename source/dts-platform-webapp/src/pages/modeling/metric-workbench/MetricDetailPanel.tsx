import { useState, useEffect } from "react";
import { Alert, Tabs, Select, Input, Button, Space, Tag, Empty } from "antd";
import { Activity, AlertTriangle, BarChart3, Box, Database, GitBranch, Rocket, Trash2 } from "lucide-react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import {
    updateSemanticMetric,
    listSemanticModelRuns,
    listSemanticModels,
    triggerSemanticModelRun,
    publishSemanticModelToDbt,
    registerSemanticBiDataset,
    registerSemanticLineage,
    type SemanticBusinessObject,
    type SemanticMetric,
    type SemanticModel,
    type SemanticModelRun,
} from "@/api/semanticModelingApi";
import {
    addMetricDependencyToFormulaJson,
    buildSemanticMetricUpdatePayload,
    findMetricCanvasRelationByEdgeId,
    getMetricDependencyIds,
    removeMetricDependencyFromFormulaJson,
    type MetricCanvasPreflightIssue,
    type MetricCanvasRelation,
} from "./metricCanvas.helpers";

const FORMULA_TYPE_OPTIONS = [
    { label: "aggregation/sum", value: "aggregation/sum" },
    { label: "aggregation/count_distinct", value: "aggregation/count_distinct" },
    { label: "aggregation/avg", value: "aggregation/avg" },
    { label: "aggregation/max", value: "aggregation/max" },
    { label: "aggregation/min", value: "aggregation/min" },
];

const RUN_STATUS_COLOR: Record<string, string> = {
    PENDING: "default",
    RUNNING: "processing",
    SUCCESS: "success",
    FAILED: "error",
    CANCELLED: "warning",
};

export interface MetricDetailPanelProps {
    selectedId: string | null;
    objects: SemanticBusinessObject[];
    metrics: SemanticMetric[];
    models: SemanticModel[];
    onMetricUpdated: () => void;
    onMetricRelationDeleted: (relation: MetricCanvasRelation) => Promise<void> | void;
    preflightIssues?: MetricCanvasPreflightIssue[];
}

function readFormulaField(formulaJson: string | null | undefined, field: string): string {
    if (!formulaJson) {
        return "";
    }
    try {
        const value = JSON.parse(formulaJson) as Record<string, unknown>;
        const fieldValue = value?.[field];
        return typeof fieldValue === "string" ? fieldValue : "";
    } catch {
        return "";
    }
}

function withOptionalFormulaField(formulaJson: string, field: string, value: string): string {
    const next = JSON.parse(formulaJson) as Record<string, unknown>;
    if (value.trim()) {
        next[field] = value.trim();
    } else {
        delete next[field];
    }
    return JSON.stringify(next);
}

function MetricFormulaTab({
    metric,
    onSaved,
}: {
    metric: SemanticMetric;
    onSaved: () => void;
}) {
    const [formulaType, setFormulaType] = useState(metric.formulaType ?? "");
    const [formulaJson, setFormulaJson] = useState(metric.formulaJson ?? "");
    const [unit, setUnit] = useState(metric.unit ?? "");
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        setFormulaType(metric.formulaType ?? "");
        setFormulaJson(metric.formulaJson ?? "");
        setUnit(metric.unit ?? "");
    }, [metric.id, metric.formulaType, metric.formulaJson, metric.unit]);

    const handleSave = async () => {
        setSaving(true);
        try {
            await updateSemanticMetric(
                metric.id,
                buildSemanticMetricUpdatePayload(metric, { formulaType, formulaJson, unit }),
            );
            toast.success("指标已保存");
            onSaved();
        } catch {
            /* global interceptor handles error toast */
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="space-y-3 p-2">
            <div>
                <div className="text-xs text-gray-500 mb-1">公式类型</div>
                <Select
                    options={FORMULA_TYPE_OPTIONS}
                    value={formulaType || undefined}
                    onChange={setFormulaType}
                    style={{ width: "100%" }}
                    placeholder="选择公式类型"
                />
            </div>
            <div>
                <div className="text-xs text-gray-500 mb-1">公式 JSON</div>
                <Input.TextArea
                    value={formulaJson}
                    onChange={(e) => setFormulaJson(e.target.value)}
                    rows={4}
                    placeholder='{"field": "amount", "type": "sum"}'
                    style={{ fontFamily: "monospace", fontSize: 12 }}
                />
            </div>
            <div>
                <div className="text-xs text-gray-500 mb-1">单位</div>
                <Input
                    value={unit}
                    onChange={(e) => setUnit(e.target.value)}
                    placeholder="如：元、个、%"
                />
            </div>
            <Button type="primary" size="small" loading={saving} onClick={handleSave} block>
                保存指标
            </Button>
        </div>
    );
}

function MetricRelationPanel({
    relation,
    objects,
    metrics,
    onSaved,
    onDeleted,
}: {
    relation: MetricCanvasRelation;
    objects: SemanticBusinessObject[];
    metrics: SemanticMetric[];
    onSaved: () => void;
    onDeleted: (relation: MetricCanvasRelation) => Promise<void> | void;
}) {
    const targetMetric = metrics.find((metric) =>
        relation.relationType === "OBJECT_METRIC" ? metric.id === relation.metricId : metric.id === relation.targetMetricId,
    );
    const sourceMetric = relation.relationType === "METRIC_DERIVES"
        ? metrics.find((metric) => metric.id === relation.sourceMetricId)
        : null;
    const sourceObject = relation.relationType === "OBJECT_METRIC"
        ? objects.find((object) => object.id === relation.objectId)
        : null;
    const [expression, setExpression] = useState(() =>
        relation.relationType === "METRIC_DERIVES" ? readFormulaField(targetMetric?.formulaJson, "derivationExpression") : "",
    );
    const [note, setNote] = useState(() =>
        relation.relationType === "METRIC_DERIVES" ? readFormulaField(targetMetric?.formulaJson, "derivationNote") : "",
    );
    const [saving, setSaving] = useState(false);
    const [deleting, setDeleting] = useState(false);

    useEffect(() => {
        if (relation.relationType !== "METRIC_DERIVES") {
            setExpression("");
            setNote("");
            return;
        }
        setExpression(readFormulaField(targetMetric?.formulaJson, "derivationExpression"));
        setNote(readFormulaField(targetMetric?.formulaJson, "derivationNote"));
    }, [relation, targetMetric?.formulaJson]);

    if (!targetMetric) {
        return (
            <div className="p-4">
                <Empty description="关系目标指标不存在" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            </div>
        );
    }

    const handleSave = async () => {
        if (relation.relationType !== "METRIC_DERIVES" || !sourceMetric) {
            return;
        }
        const relationFormula = addMetricDependencyToFormulaJson(targetMetric.formulaJson, sourceMetric.id);
        if (!relationFormula.ok) {
            toast.error("公式 JSON 不合法，未写回派生关系");
            return;
        }
        setSaving(true);
        try {
            const withExpression = withOptionalFormulaField(
                relationFormula.formulaJson,
                "derivationExpression",
                expression,
            );
            const formulaJson = withOptionalFormulaField(withExpression, "derivationNote", note);
            await updateSemanticMetric(
                targetMetric.id,
                buildSemanticMetricUpdatePayload(targetMetric, { formulaJson }),
            );
            toast.success("派生关系已保存");
            onSaved();
        } catch {
            /* global interceptor handles error toast */
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async () => {
        if (relation.relationType === "METRIC_DERIVES") {
            const formulaUpdate = removeMetricDependencyFromFormulaJson(
                targetMetric.formulaJson,
                relation.sourceMetricId,
            );
            if (!formulaUpdate.ok) {
                toast.error("目标指标公式 JSON 不合法，未删除派生关系");
                return;
            }
        }
        setDeleting(true);
        try {
            await onDeleted(relation);
        } finally {
            setDeleting(false);
        }
    };

    return (
        <div className="space-y-4 p-4">
            <div>
                <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
                    <GitBranch size={16} />
                    关系配置
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                    <Tag color={relation.relationType === "METRIC_DERIVES" ? "blue" : "green"}>
                        {relation.relationType}
                    </Tag>
                    <span className="text-gray-500">
                        {relation.relationType === "METRIC_DERIVES"
                            ? `${sourceMetric?.name ?? relation.sourceMetricId} -> ${targetMetric.name}`
                            : `${sourceObject?.name ?? relation.objectId} -> ${targetMetric.name}`}
                    </span>
                </div>
            </div>

            {relation.relationType === "METRIC_DERIVES" ? (
                <div className="space-y-3">
                    <div>
                        <div className="mb-1 text-xs text-gray-500">派生表达式</div>
                        <Input.TextArea
                            value={expression}
                            onChange={(event) => setExpression(event.target.value)}
                            rows={3}
                            placeholder="例如：GMV / 订单数"
                        />
                    </div>
                    <div>
                        <div className="mb-1 text-xs text-gray-500">关系说明</div>
                        <Input.TextArea
                            value={note}
                            onChange={(event) => setNote(event.target.value)}
                            rows={2}
                            placeholder="说明此派生关系的业务口径"
                        />
                    </div>
                    <Button type="primary" size="small" loading={saving} onClick={handleSave} block>
                        保存关系
                    </Button>
                </div>
            ) : (
                <Alert
                    type="info"
                    showIcon
                    message="业务对象绑定关系由指标 objectId 保存，删除关系不会删除指标本身。"
                />
            )}

            <Button danger size="small" icon={<Trash2 size={14} />} loading={deleting} onClick={handleDelete} block>
                删除关系
            </Button>
        </div>
    );
}

function RunsTab({ models }: { models: SemanticModel[] }) {
    const navigate = useNavigate();
    const [availableModels, setAvailableModels] = useState<SemanticModel[]>(models);
    const [modelId, setModelId] = useState<string | null>(null);
    const [runs, setRuns] = useState<SemanticModelRun[]>([]);
    const [loading, setLoading] = useState(false);
    const [triggering, setTriggering] = useState(false);
    const [publishing, setPublishing] = useState(false);
    const [publishNotice, setPublishNotice] = useState<{
        type: "success" | "warning" | "error";
        message: string;
        description?: string;
    } | null>(null);

    // Load all semantic models once on mount (shared data, not per-metric)
    useEffect(() => {
        if (models.length > 0) {
            setAvailableModels(models);
            return;
        }
        void (async () => {
            try {
                const list = await listSemanticModels();
                setAvailableModels(Array.isArray(list) ? list : []);
            } catch {
                setAvailableModels([]);
            }
        })();
    }, [models]);

    // Load runs whenever the selected model changes
    useEffect(() => {
        if (!modelId) {
            setRuns([]);
            return;
        }
        void (async () => {
            setLoading(true);
            try {
                const list = await listSemanticModelRuns(modelId);
                setRuns(Array.isArray(list) ? (list as SemanticModelRun[]) : []);
            } catch {
                setRuns([]);
            } finally {
                setLoading(false);
            }
        })();
    }, [modelId]);

    const handleTrigger = async () => {
        if (!modelId) return;
        setTriggering(true);
        try {
            await triggerSemanticModelRun(modelId, { runType: "MANUAL" });
            toast.success("运行已触发");
        } catch {
            /* global interceptor handles error toast */
        } finally {
            setTriggering(false);
        }
    };

    const handlePublish = async () => {
        if (!modelId) return;
        setPublishing(true);
        setPublishNotice(null);
        try {
            await publishSemanticModelToDbt(modelId);
        } catch {
            setPublishNotice({
                type: "error",
                message: "dbt 发布失败",
                description: "发布未完成，请修复模型制品或任务配置后重试。",
            });
            setPublishing(false);
            return;
        }
        try {
            await registerSemanticBiDataset(modelId);
        } catch {
            setPublishNotice({
                type: "warning",
                message: "dbt 已发布，BI 数据集注册失败",
                description: "模型制品已落地，但消费侧数据集还不可用。请检查 BI 注册配置后重新发布。",
            });
            toast.error("dbt 已发布，BI 数据集注册失败，请重试");
            setPublishing(false);
            return;
        }
        try {
            await registerSemanticLineage(modelId);
            setPublishNotice({
                type: "success",
                message: "发布成功",
                description: "dbt 制品、BI 数据集和血缘已全部注册。",
            });
            toast.success("已发布 dbt 并注册 BI 数据集 + 血缘");
        } catch {
            setPublishNotice({
                type: "warning",
                message: "dbt 与 BI 数据集已完成，血缘注册失败",
                description: "消费侧数据集已可用，但血缘视图暂不完整。请检查血缘注册配置后重新发布。",
            });
            toast.error("dbt 与 BI 数据集已完成，血缘注册失败，请重试");
        } finally {
            setPublishing(false);
        }
    };

    const modelOptions = availableModels.map((m) => ({
        label: `[${m.type ?? "?"}] ${m.name}`,
        value: m.id,
    }));

    return (
        <div className="p-2 space-y-2">
            <div>
                <div className="text-xs text-gray-500 mb-1">关联语义模型</div>
                <Select
                    options={modelOptions}
                    value={modelId ?? undefined}
                    onChange={(v: string) => setModelId(v)}
                    style={{ width: "100%" }}
                    placeholder="选择语义模型"
                    allowClear
                    onClear={() => setModelId(null)}
                />
            </div>
            <Space>
                <Button size="small" loading={triggering} disabled={!modelId} onClick={handleTrigger}>
                    触发运行
                </Button>
                <Button size="small" type="primary" loading={publishing} disabled={!modelId} onClick={handlePublish}>
                    发布 dbt
                </Button>
                <Button size="small" onClick={() => navigate("/ops/instances?entryKey=DBT_RUN")}>
                    任务运维中心
                </Button>
            </Space>
            <Alert
                type="info"
                showIcon
                message="运行实例、失败日志和补数统一在任务运维中心跟踪。"
            />
            {publishNotice && (
                <Alert
                    type={publishNotice.type}
                    message={publishNotice.message}
                    description={publishNotice.description}
                    showIcon
                    closable
                    onClose={() => setPublishNotice(null)}
                />
            )}
            {loading ? (
                <div className="text-sm text-gray-400">加载中...</div>
            ) : runs.length === 0 ? (
                <Empty description="暂无运行记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
                <div className="space-y-1">
                    {runs.slice(0, 10).map((run) => (
                        <div key={run.id} className="flex items-center justify-between text-xs py-1">
                            <span className="text-gray-500">{run.startedAt?.slice(0, 16) ?? "-"}</span>
                            <Tag color={RUN_STATUS_COLOR[run.status ?? ""] ?? "default"}>
                                {run.status ?? "UNKNOWN"}
                            </Tag>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

export function MetricDetailPanel({
    selectedId,
    objects,
    metrics,
    models,
    onMetricUpdated,
    onMetricRelationDeleted,
    preflightIssues = [],
}: MetricDetailPanelProps) {
    if (!selectedId) {
        if (preflightIssues.length > 0) {
            return (
                <div className="space-y-3 p-4">
                    <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
                        <AlertTriangle size={16} />
                        预检结果
                    </div>
                    <div className="space-y-2">
                        {preflightIssues.map((issue, index) => (
                            <Alert
                                key={`${issue.code}-${issue.metricId ?? issue.edgeId ?? index}`}
                                type={issue.severity === "error" ? "error" : "warning"}
                                showIcon
                                message={issue.code}
                                description={issue.message}
                            />
                        ))}
                    </div>
                </div>
            );
        }
        return (
            <div className="flex h-full items-center justify-center p-6 text-center text-sm text-gray-400">
                <div>
                    <Activity className="mx-auto mb-3 text-gray-300" size={28} />
                    请在画布中选择业务对象或指标
                </div>
            </div>
        );
    }

    if (selectedId.startsWith("obj-")) {
        const objectId = selectedId.replace("obj-", "");
        const obj = objects.find((o) => o.id === objectId);
        if (!obj) return null;
        const boundMetrics = metrics.filter((metric) => metric.objectId === obj.id);
        return (
            <div className="space-y-4 p-4">
                <div>
                    <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
                        <Box size={16} />
                        {obj.name}
                    </div>
                    <div className="mt-1 text-xs text-gray-500">{obj.code}</div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                    <div className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2">
                        <div className="text-xs text-gray-500">主表</div>
                        <div className="mt-1 truncate text-sm text-gray-900">{obj.mainTable ?? "未配置"}</div>
                    </div>
                    <div className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2">
                        <div className="text-xs text-gray-500">主键</div>
                        <div className="mt-1 truncate text-sm text-gray-900">{obj.primaryKey ?? "未配置"}</div>
                    </div>
                    <div className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2">
                        <div className="text-xs text-gray-500">挂载指标</div>
                        <div className="mt-1 text-sm font-semibold text-gray-900">{boundMetrics.length}</div>
                    </div>
                    <div className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2">
                        <div className="text-xs text-gray-500">状态</div>
                        <div className="mt-1 text-sm text-gray-900">{boundMetrics.length > 0 ? "可建模" : "待补指标"}</div>
                    </div>
                </div>
            </div>
        );
    }

    if (selectedId.startsWith("metric-")) {
        const metricId = selectedId.replace("metric-", "");
        const metric = metrics.find((m) => m.id === metricId);
        if (!metric) return null;
        const object = objects.find((item) => item.id === metric.objectId);
        const upstreamMetrics = getMetricDependencyIds(metric)
            .map((dependencyId) => metrics.find((item) => item.id === dependencyId))
            .filter((item): item is SemanticMetric => Boolean(item));
        const downstreamMetrics = metrics.filter((item) => getMetricDependencyIds(item).includes(metric.id));
        return (
            <div>
                <div className="border-b border-gray-100 p-4">
                    <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
                        <BarChart3 size={16} />
                        {metric.name}
                    </div>
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-gray-500">
                        <span>{metric.code}</span>
                        {object ? <Tag color="blue">{object.name}</Tag> : <Tag>未绑定对象</Tag>}
                        {metric.status ? <Tag>{metric.status}</Tag> : null}
                    </div>
                    <div className="mt-2 flex flex-wrap gap-2 text-xs">
                        <Tag color={upstreamMetrics.length > 0 ? "blue" : "default"}>
                            上游依赖 {upstreamMetrics.length}
                        </Tag>
                        <Tag color={downstreamMetrics.length > 0 ? "purple" : "default"}>
                            下游派生 {downstreamMetrics.length}
                        </Tag>
                    </div>
                </div>
                <Tabs
                    size="small"
                    items={[
                        {
                            key: "formula",
                            label: (
                                <span className="inline-flex items-center gap-1">
                                    <Database size={14} />
                                    公式配置
                                </span>
                            ),
                            children: <MetricFormulaTab metric={metric} onSaved={onMetricUpdated} />,
                        },
                        {
                            key: "runs",
                            label: (
                                <span className="inline-flex items-center gap-1">
                                    <Rocket size={14} />
                                    发布与运维
                                </span>
                            ),
                            children: <RunsTab models={models} />,
                        },
                    ]}
                />
            </div>
        );
    }

    if (selectedId.startsWith("edge-")) {
        const relation = findMetricCanvasRelationByEdgeId(selectedId, metrics);
        if (!relation) {
            return (
                <div className="p-4">
                    <Empty description="请选择一条有效关系" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                </div>
            );
        }
        return (
            <MetricRelationPanel
                relation={relation}
                objects={objects}
                metrics={metrics}
                onSaved={onMetricUpdated}
                onDeleted={onMetricRelationDeleted}
            />
        );
    }

    return null;
}
