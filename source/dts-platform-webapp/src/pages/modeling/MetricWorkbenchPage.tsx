import { useEffect, useState } from "react";
import { PageHeader } from "@/components/page-header";
import {
    listSemanticSubjectDomains,
    listSemanticBusinessObjects,
    listSemanticMetrics,
    type SemanticSubjectDomain,
    type SemanticBusinessObject,
    type SemanticMetric,
} from "@/api/semanticModelingApi";
import { MetricCanvas } from "./metric-workbench/MetricCanvas";

export default function MetricWorkbenchPage() {
    const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
    const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
    const [metrics, setMetrics] = useState<SemanticMetric[]>([]);
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        void (async () => {
            setLoading(true);
            const [d, o, m] = await Promise.allSettled([
                listSemanticSubjectDomains(),
                listSemanticBusinessObjects(),
                listSemanticMetrics(),
            ]);
            if (d.status === "fulfilled") setDomains(Array.isArray(d.value) ? (d.value as SemanticSubjectDomain[]) : []);
            if (o.status === "fulfilled") setObjects(Array.isArray(o.value) ? (o.value as SemanticBusinessObject[]) : []);
            if (m.status === "fulfilled") setMetrics(Array.isArray(m.value) ? (m.value as SemanticMetric[]) : []);
            setLoading(false);
        })();
    }, []);

    return (
        <div className="flex h-full flex-col" data-testid="metric-workbench-page">
            <PageHeader title="指标工作台" />
            <div className="flex flex-1 overflow-hidden">
                <div
                    style={{ width: 240, borderRight: "1px solid #e5e7eb" }}
                    className="overflow-y-auto p-2 text-sm text-gray-500"
                >
                    {loading ? "加载中..." : `主题域 ${domains.length} · 对象 ${objects.length}`}
                </div>
                <div className="flex-1 overflow-hidden" style={{ minHeight: 0 }}>
                    <MetricCanvas
                        objects={objects}
                        metrics={metrics}
                        selectedId={selectedId}
                        onNodeSelect={setSelectedId}
                    />
                </div>
                <div
                    style={{ width: 360, borderLeft: "1px solid #e5e7eb" }}
                    className="overflow-y-auto p-4 text-gray-400 text-sm"
                    data-selected-id={selectedId ?? ""}
                >
                    {selectedId == null ? "请在画布中选择节点" : `已选：${selectedId}`}
                    {selectedId != null && (
                        <button
                            className="ml-2 text-xs underline"
                            onClick={() => setSelectedId(null)}
                        >
                            取消
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}
