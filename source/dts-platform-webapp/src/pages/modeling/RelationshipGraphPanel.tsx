import { Alert, Button, Empty, Input, Select, Space, Spin, Tag } from "antd";
import { RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
	getWarehousePlanRelationshipGraph,
	type WarehousePlanRelationshipGraph,
	type WarehousePlanRelationshipGraphKind,
} from "@/api/warehousePlanApi";
import { LineageGraph } from "@/components/lineage/LineageGraph";
import { toLineageGraph } from "./relationshipGraphAdapter";

type RelationshipGraphPanelProps = {
	planId?: string;
	onNavigate: (route: string) => void;
};

const errorText = (error: unknown) => (error instanceof Error && error.message ? error.message : "关系图加载失败");

export default function RelationshipGraphPanel({ planId, onNavigate }: RelationshipGraphPanelProps) {
	const [graph, setGraph] = useState<WarehousePlanRelationshipGraph | null>(null);
	const [keyword, setKeyword] = useState("");
	const [query, setQuery] = useState("");
	const [kind, setKind] = useState<WarehousePlanRelationshipGraphKind | "">("");
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState("");
	const requestSequence = useRef(0);

	const load = useCallback(
		async (cursor?: string) => {
			const currentRequest = ++requestSequence.current;
			if (!planId) {
				setGraph(null);
				setError("");
				setLoading(false);
				return;
			}
			setLoading(true);
			setError("");
			setGraph(null);
			try {
				const nextGraph = await getWarehousePlanRelationshipGraph(planId, {
					kind: kind || undefined,
					query: query || undefined,
					limit: 500,
					cursor: cursor || undefined,
				});
				if (currentRequest !== requestSequence.current) return;
				setGraph(nextGraph);
			} catch (nextError) {
				if (currentRequest !== requestSequence.current) return;
				setGraph(null);
				setError(errorText(nextError));
			} finally {
				if (currentRequest === requestSequence.current) setLoading(false);
			}
		},
		[kind, planId, query],
	);

	useEffect(() => {
		void load(undefined);
		return () => {
			requestSequence.current += 1;
		};
	}, [load]);

	const lineage = useMemo(() => (graph ? toLineageGraph(graph) : { nodes: [], edges: [] }), [graph]);
	const routeById = useMemo(() => new Map((graph?.nodes || []).map((node) => [node.id, node.route || ""])), [graph]);
	const routeByIdRef = useRef(routeById);
	const onNavigateRef = useRef(onNavigate);
	routeByIdRef.current = routeById;
	onNavigateRef.current = onNavigate;
	const handleNodeClick = useCallback((node: { id?: unknown }) => {
		const route = routeByIdRef.current.get(String(node.id || ""));
		if (route) onNavigateRef.current(route);
	}, []);

	if (!planId) {
		return (
			<div className="grid min-h-[360px] place-items-center" data-testid="relationship-graph-panel">
				<Empty description="请先在顶部选择建设计划" />
			</div>
		);
	}

	return (
		<section className="space-y-3 p-4" data-testid="relationship-graph-panel">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<div className="font-semibold text-slate-950">建设计划关系图</div>
					<div className="mt-1 text-xs text-slate-500">仅展示当前账号可见的主线模型及显式引用关系</div>
				</div>
				<Space wrap>
					<Select<WarehousePlanRelationshipGraphKind>
						allowClear
						value={kind || undefined}
						onChange={(value) => setKind(value || "")}
						placeholder="请选择节点类型"
						className="w-44"
						options={[
							{ value: "PLAN", label: "建设计划" },
							{ value: "DIMENSION", label: "业务维度" },
							{ value: "MODEL", label: "逻辑模型" },
							{ value: "STANDARD", label: "数据标准" },
							{ value: "INDICATOR", label: "指标" },
						]}
					/>
					<Input.Search
						value={keyword}
						onChange={(event) => {
							const value = event.target.value;
							setKeyword(value);
							if (!value) setQuery("");
						}}
						onSearch={(value) => setQuery(value.trim())}
						allowClear
						placeholder="按名称或标识搜索"
						className="w-56"
					/>
					<Button icon={<RefreshCw size={16} />} loading={loading} onClick={() => void load(undefined)}>
						刷新
					</Button>
				</Space>
			</div>

			{error ? (
				<Alert
					type="error"
					showIcon
					message="关系图不可用"
					description={error}
					action={<Button onClick={() => void load(undefined)}>重试</Button>}
				/>
			) : null}
			{graph?.truncated ? (
				<Alert
					type="warning"
					showIcon
					message="关系图已按安全上限截断"
					description={
						graph.nextCursor
							? "当前筛选仍有后续结果，可继续搜索下一批。"
							: graph.nextHint === "FILTER_BY_KIND_OR_QUERY"
								? "请按节点类型或名称缩小范围后重试。"
								: graph.nextHint || "请缩小筛选范围后重试。"
					}
					action={
						graph.nextCursor ? (
							<Button disabled={loading} onClick={() => void load(graph.nextCursor || undefined)}>
								继续搜索 / 下一批
							</Button>
						) : undefined
					}
				/>
			) : null}
			{graph ? (
				<div className="flex flex-wrap gap-2 text-xs text-slate-500">
					<Tag>{graph.nodes.length} 个节点</Tag>
					<Tag>{graph.edges.length} 条关系</Tag>
					{kind ? <Tag color="blue">类型：{kind}</Tag> : null}
					{query ? <Tag color="blue">关键字：{query}</Tag> : null}
				</div>
			) : null}

			{loading && !graph ? (
				<div className="grid min-h-[420px] place-items-center">
					<Spin />
				</div>
			) : null}
			{!loading && !error && graph && lineage.nodes.length === 0 ? (
				<Empty className="py-20" description={query || kind ? "没有匹配的关系节点" : "当前建设计划暂无可见关系"} />
			) : null}
			{!error && lineage.nodes.length > 0 ? (
				<div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
					<LineageGraph
						nodes={lineage.nodes}
						edges={lineage.edges}
						height={620}
						showToolbar
						showMiniMap
						highlightKeyword={query}
						onNodeClick={handleNodeClick}
					/>
				</div>
			) : null}
		</section>
	);
}
