import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, Button, Space, Tag } from "antd";
import { RefreshCw } from "lucide-react";
import { useLocation, useNavigate } from "react-router";
import { toast } from "sonner";
import {
    listSemanticSubjectDomains,
    listSemanticBusinessObjects,
    listSemanticMetrics,
    listSemanticModels,
    updateSemanticMetric,
    validateSemanticMetricDerivation,
    type SemanticSubjectDomain,
    type SemanticBusinessObject,
    type SemanticMetric,
    type SemanticModel,
} from "@/api/semanticModelingApi";
import { JourneyContextBar } from "@/components/journey";
import { SemanticWorkspaceFrame } from "./semantic-workspace/SemanticWorkspaceFrame";
import { MetricCanvas } from "./metric-workbench/MetricCanvas";
import { MetricDetailPanel } from "./metric-workbench/MetricDetailPanel";
import { SubjectBrowserPanel } from "./metric-workbench/SubjectBrowserPanel";
import {
    addMetricDependencyToFormulaJson,
    buildMetricCanvasPreflightIssues,
    buildSemanticMetricUpdatePayload,
    getMetricDependencyIds,
    removeMetricDependencyFromFormulaJson,
    type MetricCanvasPreflightIssue,
    type MetricCanvasRelation,
} from "./metric-workbench/metricCanvas.helpers";

export default function MetricWorkbenchPage() {
    const location = useLocation();
    const navigate = useNavigate();
    const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
    const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
    const [metrics, setMetrics] = useState<SemanticMetric[]>([]);
    const [models, setModels] = useState<SemanticModel[]>([]);
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [preflightIssues, setPreflightIssues] = useState<MetricCanvasPreflightIssue[]>([]);
    const [loading, setLoading] = useState(true);

    const lowCodeContext = useMemo(() => {
        const params = new URLSearchParams(location.search);
        const journey = params.get("journey");
        if (journey === "low-code-development") {
            return {
                journey,
                target: params.get("target") || "report",
                sourceDatasetId: params.get("sourceDatasetId") || "",
                businessObjectId: params.get("businessObjectId") || "",
            };
        }
        return null;
    }, [location.search]);

    const e2eContext = useMemo(() => {
        const params = new URLSearchParams(location.search);
        const journey = params.get("journey");
        if (journey !== "e2e-data-product") return null;
        return {
            modelId: params.get("modelId") || "",
            standardDraftId: params.get("standardDraftId") || "",
            metricId: params.get("metricId") || "",
        };
    }, [location.search]);

    const load = useCallback(async () => {
        setLoading(true);
        const [d, o, m, modelList] = await Promise.allSettled([
            listSemanticSubjectDomains(),
            listSemanticBusinessObjects(),
            listSemanticMetrics(),
            listSemanticModels(),
        ]);
        if (d.status === "fulfilled") setDomains(Array.isArray(d.value) ? (d.value as SemanticSubjectDomain[]) : []);
        if (o.status === "fulfilled") setObjects(Array.isArray(o.value) ? (o.value as SemanticBusinessObject[]) : []);
        if (m.status === "fulfilled") setMetrics(Array.isArray(m.value) ? (m.value as SemanticMetric[]) : []);
        if (modelList.status === "fulfilled") setModels(Array.isArray(modelList.value) ? (modelList.value as SemanticModel[]) : []);
        setLoading(false);
    }, []);

    useEffect(() => {
        void load();
    }, [load]);

    useEffect(() => {
        if (!lowCodeContext?.businessObjectId || objects.length === 0) return;
        const matched = objects.find((item) => item.id === lowCodeContext.businessObjectId);
        if (matched && selectedId !== `object-${matched.id}`) {
            setSelectedId(`object-${matched.id}`);
        }
    }, [lowCodeContext?.businessObjectId, objects, selectedId]);

    const refreshMetrics = useCallback(async () => {
        const list = await listSemanticMetrics();
        setMetrics(Array.isArray(list) ? (list as SemanticMetric[]) : []);
    }, []);

    const handleMetricBound = useCallback(
        async (metricId: string, objectId: string) => {
            const metric = metrics.find((item) => item.id === metricId);
            const object = objects.find((item) => item.id === objectId);
            if (!metric || !object) {
                toast.error("指标或业务对象不存在，请刷新后重试");
                return;
            }
            await updateSemanticMetric(metricId, buildSemanticMetricUpdatePayload(metric, { objectId }));
            setMetrics((current) =>
                current.map((item) => (item.id === metricId ? { ...item, objectId } : item)),
            );
            toast.success(`已绑定到业务对象：${object.name}`);
            void refreshMetrics();
        },
        [metrics, objects, refreshMetrics],
    );

    const handleMetricDerived = useCallback(
        async (sourceMetricId: string, targetMetricId: string) => {
            const sourceMetric = metrics.find((item) => item.id === sourceMetricId);
            const targetMetric = metrics.find((item) => item.id === targetMetricId);
            if (!sourceMetric || !targetMetric) {
                toast.error("指标不存在，请刷新后重试");
                return;
            }
            const formulaUpdate = addMetricDependencyToFormulaJson(targetMetric.formulaJson, sourceMetricId);
            if (!formulaUpdate.ok) {
                toast.error("目标指标公式 JSON 不合法，未写回派生关系");
                return;
            }
            await updateSemanticMetric(
                targetMetric.id,
                buildSemanticMetricUpdatePayload(targetMetric, { formulaJson: formulaUpdate.formulaJson }),
            );
            setMetrics((current) =>
                current.map((item) =>
                    item.id === targetMetricId ? { ...item, formulaJson: formulaUpdate.formulaJson } : item,
                ),
            );
            setPreflightIssues([]);
            toast.success(`已建立派生关系：${sourceMetric.name} -> ${targetMetric.name}`);
            void refreshMetrics();
        },
        [metrics, refreshMetrics],
    );

    const handleMetricRelationDeleted = useCallback(
        async (relation: MetricCanvasRelation) => {
            if (relation.relationType === "OBJECT_METRIC") {
                const metric = metrics.find((item) => item.id === relation.metricId);
                if (!metric) {
                    toast.error("指标不存在，请刷新后重试");
                    return;
                }
                await updateSemanticMetric(
                    metric.id,
                    buildSemanticMetricUpdatePayload(metric, { objectId: null }),
                );
                setMetrics((current) =>
                    current.map((item) => (item.id === metric.id ? { ...item, objectId: null } : item)),
                );
                setSelectedId(`metric-${metric.id}`);
                setPreflightIssues([]);
                toast.success("业务对象绑定关系已删除");
                void refreshMetrics();
                return;
            }

            const targetMetric = metrics.find((item) => item.id === relation.targetMetricId);
            if (!targetMetric) {
                toast.error("目标指标不存在，请刷新后重试");
                return;
            }
            const formulaUpdate = removeMetricDependencyFromFormulaJson(
                targetMetric.formulaJson,
                relation.sourceMetricId,
            );
            if (!formulaUpdate.ok) {
                toast.error("目标指标公式 JSON 不合法，未删除派生关系");
                return;
            }
            await updateSemanticMetric(
                targetMetric.id,
                buildSemanticMetricUpdatePayload(targetMetric, { formulaJson: formulaUpdate.formulaJson }),
            );
            setMetrics((current) =>
                current.map((item) =>
                    item.id === targetMetric.id ? { ...item, formulaJson: formulaUpdate.formulaJson } : item,
                ),
            );
            setSelectedId(`metric-${targetMetric.id}`);
            setPreflightIssues([]);
            toast.success("指标派生关系已删除");
            void refreshMetrics();
        },
        [metrics, refreshMetrics],
    );

    const handlePreflight = useCallback(async () => {
        const localIssues = buildMetricCanvasPreflightIssues(objects, metrics);
        const derivedMetrics = metrics.filter((metric) => getMetricDependencyIds(metric).length > 0);
        const backendIssues = (
            await Promise.all(
                derivedMetrics.map(async (metric): Promise<MetricCanvasPreflightIssue | null> => {
                    try {
                        const result = await validateSemanticMetricDerivation({
                            targetMetricId: metric.id,
                            formulaJson: metric.formulaJson,
                        });
                        if (result.valid) {
                            return null;
                        }
                        return {
                            code: "DERIVATION_COMPILE_FAILED",
                            severity: "error",
                            message: `${metric.name || metric.code} DSL 编译失败：${result.issues?.join("；") || "后端规则未通过"}`,
                            metricId: metric.id,
                            nodeId: `metric-${metric.id}`,
                        };
                    } catch {
                        return {
                            code: "DERIVATION_COMPILE_FAILED",
                            severity: "error",
                            message: `${metric.name || metric.code} DSL 编译预检调用失败`,
                            metricId: metric.id,
                            nodeId: `metric-${metric.id}`,
                        };
                    }
                }),
            )
        ).filter((issue): issue is MetricCanvasPreflightIssue => Boolean(issue));
        const issues = [...localIssues, ...backendIssues];
        setPreflightIssues(issues);
        setSelectedId(null);
        if (issues.length === 0) {
            toast.success("指标编排与 DSL 编译预检通过");
            return;
        }
        toast.warning(`指标编排预检发现 ${issues.length} 项问题`);
    }, [metrics, objects]);

    const activeMetrics = useMemo(
        () => metrics.filter((metric) => metric.status === "ACTIVE").length,
        [metrics],
    );
    const releasedModels = useMemo(
        () => models.filter((model) => model.reviewStatus === "APPROVED").length,
        [models],
    );
    const buildServiceRoute = (route: string) => {
        if (!e2eContext) return route;
        const params = new URLSearchParams();
        params.set("journey", "e2e-data-product");
        if (e2eContext.modelId) params.set("modelId", e2eContext.modelId);
        if (e2eContext.standardDraftId) params.set("standardDraftId", e2eContext.standardDraftId);
        if (e2eContext.metricId) params.set("metricId", e2eContext.metricId);
        return `${route}?${params.toString()}`;
    };

    return (
        <SemanticWorkspaceFrame
            activeKey="workbench"
            title="指标工作台"
            description="查看并维护业务对象、指标和语义模型关系。"
            stats={[
                { label: "治理主题域", value: domains.length, tone: "blue" },
                { label: "业务对象", value: objects.length, tone: "green" },
                { label: "指标", value: metrics.length, tone: "amber" },
                { label: "已审核模型", value: releasedModels, tone: releasedModels > 0 ? "green" : "gray" },
            ]}
            actions={
                <Space wrap>
                    <Button onClick={() => void load()} loading={loading}>
                        <RefreshCw size={16} />
                        刷新
                    </Button>
                    <Button onClick={() => navigate(buildServiceRoute("/services/apis"))}>
                        发布数据 API
                    </Button>
                    <Button onClick={() => navigate(buildServiceRoute("/services/products"))}>
                        创建数据产品
                    </Button>
                </Space>
            }
        >
            <JourneyContextBar stage="metrics" />
            {e2eContext ? (
                <Alert
                    className="mb-4"
                    type={e2eContext.modelId ? "info" : "warning"}
                    showIcon
                    data-testid="metric-workbench-e2e-context"
                    message="来自端到端数据产品旅程"
                    description={`模型 ${e2eContext.modelId || "待绑定"} · 标准草稿 ${e2eContext.standardDraftId || "待绑定"} · 待绑定指标 ${e2eContext.metricId ? "已选择指标" : "请补充指标口径"}`}
                    action={
                        <Space wrap>
                            <Tag color="blue">标准字段</Tag>
                            <Button size="small" onClick={() => navigate(`/studio/sql-modeling?journey=e2e-data-product${e2eContext.modelId ? `&modelId=${encodeURIComponent(e2eContext.modelId)}` : ""}${e2eContext.standardDraftId ? `&standardDraftId=${encodeURIComponent(e2eContext.standardDraftId)}` : ""}`)}>
                                返回模型
                            </Button>
                        </Space>
                    }
                />
            ) : null}
            {lowCodeContext ? (
                <Alert
                    className="mb-4"
                    type="info"
                    showIcon
                    data-testid="metric-workbench-low-code-context"
                    message="来自低代码开发向导"
                    description="请围绕已确认的业务对象维护指标、维度、统计周期和口径，生成结果会回到模型管理与发布审核。"
                    action={
                        <Space wrap>
                            <Tag color="blue">{lowCodeContext.target === "report" ? "报表数据集" : lowCodeContext.target}</Tag>
                            <Button
                                size="small"
                                onClick={() => navigate("/studio/low-code-development?step=metric_designed&target=report")}
                            >
                                返回低代码向导
                            </Button>
                        </Space>
                    }
                />
            ) : null}
            <div
                className="grid gap-4 xl:grid-cols-[300px_minmax(0,1fr)]"
                data-testid="metric-workbench-page"
            >
                <div className="flex min-h-[680px] flex-col overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
                    <SubjectBrowserPanel
                        domains={domains}
                        objects={objects}
                        metrics={metrics}
                        selectedId={selectedId}
                        onSelect={setSelectedId}
                    />
                </div>
                <div className="flex min-h-[680px] min-w-0 flex-col gap-4" data-testid="metric-workbench-main">
                    <div className="flex min-h-[440px] flex-[1.4] flex-col overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
                        <div className="flex items-center justify-between border-b border-gray-100 px-4 py-3">
                            <div>
                                <div className="text-sm font-semibold text-gray-900">指标关系画布</div>
                                <div className="text-xs text-gray-500">
                                    {objects.length} 个业务对象 / {metrics.length} 个指标 / {activeMetrics} 个可用指标
                                </div>
                            </div>
                        </div>
                        <div className="min-h-[400px] flex-1">
                            <MetricCanvas
                                objects={objects}
                                metrics={metrics}
                                selectedId={selectedId}
                                onNodeSelect={setSelectedId}
                                onMetricBound={handleMetricBound}
                                onMetricDerived={handleMetricDerived}
                                onPreflight={handlePreflight}
                                onArrangementSave={() => toast.success("编排关系已实时保存")}
                                loading={loading}
                            />
                        </div>
                    </div>
                    <div
                        className="min-h-[260px] overflow-y-auto rounded-lg border border-gray-200 bg-white shadow-sm"
                        data-testid="metric-detail-dock"
                    >
                        <MetricDetailPanel
                            selectedId={selectedId}
                            objects={objects}
                            metrics={metrics}
                            models={models}
                            preflightIssues={preflightIssues}
                            onMetricUpdated={() => {
                                setPreflightIssues([]);
                                void refreshMetrics();
                            }}
                            onMetricRelationDeleted={handleMetricRelationDeleted}
                        />
                    </div>
                </div>
            </div>
        </SemanticWorkspaceFrame>
    );
}
