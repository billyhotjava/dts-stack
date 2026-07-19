import { Alert, Button, Empty, List, Space, Spin, Tag, Typography } from "antd";
import { RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
	getModelSpecDependencies,
	type ModelSpecDependencyGraph,
	type ModelSpecDependencyState,
} from "@/api/modelSpecApi";

const { Text } = Typography;

const STATE_META: Record<ModelSpecDependencyState, { color: string; label: string }> = {
	CURRENT: { color: "success", label: "当前版本" },
	STALE: { color: "warning", label: "版本漂移" },
	UNKNOWN: { color: "default", label: "引用不可用" },
};

type Props = {
	modelSpecId: string;
	revision: number;
};

export function ModelSpecDependencyPanel({ modelSpecId, revision }: Props) {
	const [graph, setGraph] = useState<ModelSpecDependencyGraph | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");

	const load = useCallback(async () => {
		setLoading(true);
		setError("");
		try {
			setGraph(await getModelSpecDependencies(modelSpecId));
		} catch {
			setGraph(null);
			setError("暂时无法读取上游版本状态，不影响继续编辑草稿");
		} finally {
			setLoading(false);
		}
	}, [modelSpecId]);

	useEffect(() => {
		if (revision > 0) void load();
	}, [load, revision]);

	const nodeById = useMemo(() => new Map((graph?.nodes || []).map((node) => [node.modelSpecId, node])), [graph]);

	return (
		<div className="mt-4 border-t border-border pt-4" data-testid="model-spec-dependency-panel">
			<div className="mb-3 flex flex-wrap items-center justify-between gap-2">
				<div>
					<div className="font-medium">上游版本状态</div>
					<Text type="secondary">关系由已保存的 dependsOn 版本引用派生，不在此处维护第二份关系。</Text>
				</div>
				<Button size="small" icon={<RefreshCw size={14} />} loading={loading} onClick={() => void load()}>
					刷新
				</Button>
			</div>
			{error ? <Alert className="mb-3" type="warning" showIcon message={error} /> : null}
			{loading && !graph ? (
				<div className="flex min-h-20 items-center justify-center">
					<Spin size="small" />
				</div>
			) : graph?.edges.length ? (
				<List
					size="small"
					dataSource={graph.edges}
					renderItem={(edge) => {
						const node = nodeById.get(edge.toModelSpecId);
						const state = STATE_META[edge.state];
						return (
							<List.Item key={`${edge.fromModelSpecId}-${edge.toModelSpecId}-${edge.pinnedRevision}`}>
								<Space direction="vertical" size={2} className="min-w-0">
									<Space wrap>
										<Text strong>{node?.restricted ? "受限上游" : node?.name || edge.toModelSpecId}</Text>
										{node?.modelType ? <Tag>{node.modelType}</Tag> : null}
										<Tag color={state.color}>{state.label}</Tag>
									</Space>
									<Text type="secondary">
										已固定 r{edge.pinnedRevision} · 当前{" "}
										{edge.currentRevision == null ? "不可见" : `r${edge.currentRevision}`}
									</Text>
								</Space>
							</List.Item>
						);
					}}
				/>
			) : (
				<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前模型没有上游依赖" />
			)}
		</div>
	);
}
