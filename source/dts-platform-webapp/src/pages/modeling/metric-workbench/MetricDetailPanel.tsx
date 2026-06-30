import { useState, useEffect } from "react";
import { Alert, Tabs, Select, Input, Button, Space, Tag, Empty } from "antd";
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
    onMetricUpdated: () => void;
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
            await updateSemanticMetric(metric.id, { formulaType, formulaJson, unit });
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

function RunsTab() {
    const [models, setModels] = useState<SemanticModel[]>([]);
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
        void (async () => {
            try {
                const list = await listSemanticModels();
                setModels(Array.isArray(list) ? list : []);
            } catch {
                setModels([]);
            }
        })();
    }, []);

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

    const modelOptions = models.map((m) => ({
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
            </Space>
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
    onMetricUpdated,
}: MetricDetailPanelProps) {
    if (!selectedId) {
        return (
            <div className="flex h-full items-center justify-center text-gray-400 text-sm p-4 text-center">
                请在画布中选择节点
            </div>
        );
    }

    if (selectedId.startsWith("obj-")) {
        const objectId = selectedId.replace("obj-", "");
        const obj = objects.find((o) => o.id === objectId);
        if (!obj) return null;
        return (
            <div className="p-4 space-y-2">
                <div className="font-semibold text-gray-700">{obj.name}</div>
                <div className="text-xs text-gray-400">{obj.code}</div>
                <div className="text-xs text-gray-500">主表: {obj.mainTable ?? "未配置"}</div>
                <div className="text-xs text-gray-500">主键: {obj.primaryKey ?? "未配置"}</div>
            </div>
        );
    }

    if (selectedId.startsWith("metric-")) {
        const metricId = selectedId.replace("metric-", "");
        const metric = metrics.find((m) => m.id === metricId);
        if (!metric) return null;
        return (
            <Tabs
                size="small"
                items={[
                    {
                        key: "formula",
                        label: "公式/维度",
                        children: <MetricFormulaTab metric={metric} onSaved={onMetricUpdated} />,
                    },
                    {
                        key: "runs",
                        label: "消费数据",
                        children: <RunsTab />,
                    },
                ]}
            />
        );
    }

    return null;
}
