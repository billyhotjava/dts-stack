import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "antd";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";
import {
    listSemanticSubjectDomains,
    listSemanticBusinessObjects,
    listSemanticMetrics,
    listSemanticModels,
    updateSemanticMetric,
    type SemanticSubjectDomain,
    type SemanticBusinessObject,
    type SemanticMetric,
    type SemanticModel,
} from "@/api/semanticModelingApi";
import { SemanticWorkspaceFrame } from "./semantic-workspace/SemanticWorkspaceFrame";
import { MetricCanvas } from "./metric-workbench/MetricCanvas";
import { MetricDetailPanel } from "./metric-workbench/MetricDetailPanel";
import { SubjectBrowserPanel } from "./metric-workbench/SubjectBrowserPanel";
import { buildSemanticMetricUpdatePayload } from "./metric-workbench/metricCanvas.helpers";

export default function MetricWorkbenchPage() {
    const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
    const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
    const [metrics, setMetrics] = useState<SemanticMetric[]>([]);
    const [models, setModels] = useState<SemanticModel[]>([]);
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);

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

    const activeMetrics = useMemo(
        () => metrics.filter((metric) => metric.status === "ACTIVE").length,
        [metrics],
    );
    const releasedModels = useMemo(
        () => models.filter((model) => model.reviewStatus === "APPROVED").length,
        [models],
    );

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
                <Button onClick={() => void load()} loading={loading}>
                    <RefreshCw size={16} />
                    刷新
                </Button>
            }
        >
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
                            onMetricUpdated={() => {
                                void refreshMetrics();
                            }}
                        />
                    </div>
                </div>
            </div>
        </SemanticWorkspaceFrame>
    );
}
