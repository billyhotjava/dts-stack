import { Button, Empty, Tree, Tag } from "antd";
import type { DataNode } from "antd/es/tree";
import { BarChart3, Box, GripVertical, MapPinned } from "lucide-react";
import type { DragEvent } from "react";
import { useRouter } from "@/routes/hooks";
import type {
    SemanticSubjectDomain,
    SemanticBusinessObject,
    SemanticMetric,
} from "@/api/semanticModelingApi";
import { serializeMetricDragPayload } from "./metricCanvas.helpers";

interface SubjectBrowserPanelProps {
    domains: SemanticSubjectDomain[];
    objects: SemanticBusinessObject[];
    metrics: SemanticMetric[];
    selectedId: string | null;
    onSelect: (id: string) => void;
}

function buildTreeData(
    domains: SemanticSubjectDomain[],
    objects: SemanticBusinessObject[],
    metrics: SemanticMetric[],
): DataNode[] {
    const handleMetricDragStart = (event: DragEvent<HTMLSpanElement>, metricId: string) => {
        const payload = serializeMetricDragPayload(metricId);
        event.dataTransfer.effectAllowed = "link";
        event.dataTransfer.setData("application/x-dts-metric", payload);
        event.dataTransfer.setData("text/plain", payload);
    };
    const domainIds = new Set(domains.map((domain) => domain.id));
    const objectIds = new Set(objects.map((obj) => obj.id));
    const toMetricNode = (m: SemanticMetric) => ({
        key: `metric-${m.id}`,
        title: (
            <span
                className="inline-flex cursor-grab items-center gap-2 rounded px-1 py-0.5 active:cursor-grabbing"
                draggable
                onDragStart={(event) => handleMetricDragStart(event, m.id)}
            >
                <GripVertical size={13} className="text-gray-300" />
                <BarChart3 size={14} />
                <span>{m.name}</span>
                {m.status ? <Tag>{m.status}</Tag> : null}
            </span>
        ),
        isLeaf: true,
    });
    const toObjectNodes = (domainObjects: SemanticBusinessObject[]) =>
        domainObjects.map((obj) => {
            const objMetrics = metrics.filter((m) => m.objectId === obj.id);
            return {
                key: `obj-${obj.id}`,
                title: (
                    <span className="inline-flex items-center gap-2">
                        <Box size={14} />
                        <span>{obj.name}</span>
                        <Tag color={objMetrics.length > 0 ? "green" : "default"}>{objMetrics.length}</Tag>
                    </span>
                ),
                children: objMetrics.map(toMetricNode),
            };
        });

    const domainNodes = domains.map((domain) => {
        const domainObjects = objects.filter((o) => o.domainId === domain.id);
        return {
            key: `domain-${domain.id}`,
            title: (
                <span className="inline-flex items-center gap-2">
                    <MapPinned size={14} />
                    <span>{domain.name}</span>
                    <Tag color="blue">{domainObjects.length}</Tag>
                </span>
            ),
            selectable: false,
            children: toObjectNodes(domainObjects),
        };
    });
    const orphanObjects = objects.filter((o) => !o.domainId || !domainIds.has(o.domainId));
    const unboundMetrics = metrics.filter((m) => !m.objectId || !objectIds.has(m.objectId));
    const nodes: DataNode[] = [...domainNodes];
    if (orphanObjects.length > 0) {
        nodes.push({
            key: "domain-unassigned-governance",
            title: (
                <span className="inline-flex items-center gap-2">
                    <MapPinned size={14} />
                    <span>未归属治理主题域</span>
                    <Tag color="orange">{orphanObjects.length}</Tag>
                </span>
            ),
            selectable: false,
            children: toObjectNodes(orphanObjects),
        });
    }
    if (unboundMetrics.length > 0) {
        nodes.push({
            key: "metrics-unbound",
            title: (
                <span className="inline-flex items-center gap-2">
                    <BarChart3 size={14} />
                    <span>未绑定业务对象指标</span>
                    <Tag color="orange">{unboundMetrics.length}</Tag>
                </span>
            ),
            selectable: false,
            children: unboundMetrics.map(toMetricNode),
        });
    }
    return nodes;
}

export function SubjectBrowserPanel({
    domains,
    objects,
    metrics,
    selectedId,
    onSelect,
}: SubjectBrowserPanelProps) {
    const router = useRouter();
    const treeData = buildTreeData(domains, objects, metrics);

    const handleSelect = (keys: React.Key[]) => {
        if (keys.length > 0) {
            onSelect(String(keys[0]));
        }
    };

    return (
        <div className="flex h-full flex-col">
            <div className="border-b border-gray-100 px-4 py-3">
                <div className="text-sm font-semibold text-gray-900">指标目录</div>
                <div className="mt-1 text-xs text-gray-500">
                    {domains.length} 个治理主题域，{objects.length} 个业务对象，{metrics.length} 个指标
                </div>
            </div>
            <div className="flex-1 overflow-y-auto p-2">
                {treeData.length === 0 ? (
                    <Empty description="暂无治理主题域，请先在数据治理中心维护" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                    <Tree
                        treeData={treeData}
                        selectedKeys={selectedId ? [selectedId] : []}
                        onSelect={handleSelect}
                        defaultExpandAll
                        blockNode
                        style={{ fontSize: 13 }}
                    />
                )}
            </div>
            <div className="space-y-2 border-t border-gray-100 p-3">
                <Button
                    block
                    onClick={() => router.push("/governance/subjects")}
                >
                    <MapPinned size={16} />
                    治理主题域
                </Button>
                <Button
                    block
                    onClick={() => router.push("/modeling/semantic/objects")}
                >
                    <Box size={16} />
                    管理业务对象
                </Button>
            </div>
        </div>
    );
}
