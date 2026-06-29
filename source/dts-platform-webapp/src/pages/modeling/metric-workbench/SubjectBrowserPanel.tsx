import { Tree, Button, Empty } from "antd";
import type { DataNode } from "antd/es/tree";
import { useRouter } from "@/routes/hooks";
import type {
    SemanticSubjectDomain,
    SemanticBusinessObject,
    SemanticMetric,
} from "@/api/semanticModelingApi";

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
    return domains.map((domain) => {
        const domainObjects = objects.filter((o) => o.domainId === domain.id);
        return {
            key: `domain-${domain.id}`,
            title: `📁 ${domain.name}`,
            selectable: false,
            children: domainObjects.map((obj) => {
                const objMetrics = metrics.filter((m) => m.objectId === obj.id);
                return {
                    key: `obj-${obj.id}`,
                    title: `◎ ${obj.name}`,
                    children: objMetrics.map((m) => ({
                        key: `metric-${m.id}`,
                        title: `📈 ${m.name}`,
                        isLeaf: true,
                    })),
                };
            }),
        };
    });
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
            <div className="flex-1 overflow-y-auto p-2">
                {treeData.length === 0 ? (
                    <Empty description="暂无主题域" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                    <Tree
                        treeData={treeData}
                        selectedKeys={selectedId ? [selectedId] : []}
                        onSelect={handleSelect}
                        defaultExpandAll
                        blockNode
                        style={{ fontSize: 12 }}
                    />
                )}
            </div>
            <div className="border-t border-gray-200 p-2 space-y-1">
                <Button
                    size="small"
                    block
                    onClick={() => router.push("/modeling/semantic/subjects")}
                >
                    管理主题域
                </Button>
                <Button
                    size="small"
                    block
                    onClick={() => router.push("/modeling/semantic/objects")}
                >
                    管理业务对象
                </Button>
            </div>
        </div>
    );
}
