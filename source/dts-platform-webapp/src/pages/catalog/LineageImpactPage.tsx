import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Collapse, Space, Statistic, Tag } from "antd";
import { CompactTable } from "@/components/table";
import { EmptyState } from "@/components/empty-state";
import { getCatalogLineageImpact } from "@/api/platformApi";
import {
	EmptyAction,
	exportImpactCsv,
	formatTs,
	type ImpactNode,
	type ImpactResult,
	layerColor,
	LineageDataFilters,
	type LineageDirection,
	LineageNodeDrawer,
	LineageSectionNav,
	loadDatasetOptions,
	edgeColumns,
	nodeColumns,
	toIsoInstant,
	useLineageData,
} from "./lineageShared";

export default function LineageImpactPage() {
	const [datasets, setDatasets] = useState<Array<{ id: string; name: string }>>([]);
	const [selectedId, setSelectedId] = useState<string>();
	const [direction, setDirection] = useState<LineageDirection>("BOTH");
	const [depth, setDepth] = useState(3);
	const [projectName, setProjectName] = useState("");
	const [layerFilters, setLayerFilters] = useState<string[]>([]);
	const [changedWithinHours, setChangedWithinHours] = useState(0);
	const [snapshotAt, setSnapshotAt] = useState("");
	const [keyword, setKeyword] = useState("");
	const [loading, setLoading] = useState(false);
	const [impact, setImpact] = useState<ImpactResult | null>(null);
	const [selectedNode, setSelectedNode] = useState<ImpactNode | null>(null);
	const { nodes, edges, columnLineages } = useLineageData(impact, keyword);
	const datasetOptions = useMemo(() => datasets.map((item) => ({ label: item.name, value: item.id })), [datasets]);

	const loadDatasets = async () => {
		try {
			const options = await loadDatasetOptions();
			setDatasets(options);
			if (!selectedId && options.length) setSelectedId(options[0].id);
		} catch {
			// global interceptor handles toast
		}
	};

	const loadImpact = async () => {
		if (!selectedId) {
			setImpact(null);
			return;
		}
		setLoading(true);
		try {
			const resp: any = await getCatalogLineageImpact(selectedId, {
				direction,
				depth,
				projectName: projectName.trim() || undefined,
				layers: layerFilters.length ? layerFilters.join(",") : undefined,
				changedWithinHours: changedWithinHours > 0 ? changedWithinHours : undefined,
				withJobs: true,
				withColumns: true,
				at: toIsoInstant(snapshotAt),
			});
			setImpact(resp || null);
		} catch {
			setImpact(null);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadDatasets();
	}, []);

	useEffect(() => {
		void loadImpact();
	}, [selectedId, direction, depth, projectName, layerFilters, changedWithinHours, snapshotAt]);

	const layerGroupItems = useMemo(() => {
		const groups = new Map<string, ImpactNode[]>();
		for (const node of nodes) {
			const key = String(node.layer || "UNKNOWN").toUpperCase();
			groups.set(key, [...(groups.get(key) ?? []), node]);
		}
		return [...groups.entries()].map(([layer, list]) => ({
			key: layer,
			label: <Space><Tag color={layerColor(layer)}>{layer}</Tag><span>{list.length} 个节点</span></Space>,
			children: (
				<CompactTable
					size="small"
					rowKey={(row, idx) => row.id || `${row.db || "db"}.${row.table || "tb"}-${idx}`}
					columns={[
						{ title: "节点", dataIndex: "name", render: (v, r) => v || `${r.db || "-"}.${r.table || "-"}` , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
						{ title: "模式.表", render: (_, r) => `${r.db || "-"}.${r.table || "-"}` },
						{ title: "负责人", render: (_, r) => r.owner || r.ownerDept || "-" },
						{ title: "最近变更", render: (_, r) => formatTs(r.lastModifiedAt) },
					]}
					dataSource={list}
					pagination={false}
					onRow={(record) => ({ onClick: () => setSelectedNode(record) })}
				/>
			),
		}));
	}, [nodes]);

	return (
		<div className="space-y-4">
			<Card title="血缘与影响分析 / 影响分析" extra={<Button onClick={() => exportImpactCsv(nodes, edges, columnLineages)} disabled={!nodes.length && !edges.length && !columnLineages.length}>导出结果</Button>}>
				<div className="mb-3"><LineageSectionNav section="impact" /></div>
				<LineageDataFilters
					datasetOptions={datasetOptions}
					selectedId={selectedId}
					onSelectedIdChange={setSelectedId}
					direction={direction}
					onDirectionChange={setDirection}
					depth={depth}
					onDepthChange={setDepth}
					projectName={projectName}
					onProjectNameChange={setProjectName}
					layerFilters={layerFilters}
					onLayerFiltersChange={setLayerFilters}
					changedWithinHours={changedWithinHours}
					onChangedWithinHoursChange={setChangedWithinHours}
					keyword={keyword}
					onKeywordChange={setKeyword}
					snapshotAt={snapshotAt}
					onSnapshotAtChange={setSnapshotAt}
				/>
			</Card>

			{!selectedId ? <Alert type="info" message="请选择一个数据集开始分析。" showIcon action={<EmptyAction onReload={loadDatasets} />} /> : null}

			{selectedId ? (
				<>
					<Card title="影响概览" loading={loading}>
						<Space size={24} wrap>
							<Statistic title="节点数" value={Number(impact?.nodeCount || nodes.length)} />
							<Statistic title="边数" value={Number(impact?.edgeCount || edges.length)} />
							<Statistic title="变更节点" value={Number(impact?.impactStats?.changedNodeCount || 0)} />
							<Statistic title="数据源" value={Number(impact?.impactStats?.kindNodeCounts?.source || 0)} />
							<Statistic title="任务节点" value={Number(impact?.impactStats?.kindNodeCounts?.job || 0)} />
							<Statistic title="字段血缘" value={Number(impact?.impactStats?.columnLineageCount || columnLineages.length)} />
							<Statistic title="方向" value={impact?.direction || direction} />
							<Statistic title="深度" value={Number(impact?.depth || depth)} />
							<Statistic title="快照" value={impact?.timeTravel ? formatTs(impact?.snapshotAt) : "当前"} />
						</Space>
					</Card>
					<Card title="分层影响">
						{layerGroupItems.length ? <Collapse items={layerGroupItems} defaultActiveKey={layerGroupItems.map((item) => item.key)} /> : <EmptyState title="暂无分层节点" description="当前筛选条件下无可展示节点。" />}
					</Card>
					<Card title="节点与关系">
						<Space direction="vertical" className="w-full">
							{nodes.length ? <CompactTable rowKey={(row, idx) => row.id || `${row.db || "db"}.${row.table || "tb"}-${idx}`} columns={nodeColumns} dataSource={nodes} loading={loading} scroll={{ x: 1200 }} pagination={{ pageSize: 8 }} onRow={(record) => ({ onClick: () => setSelectedNode(record) })} /> : <EmptyState title="暂无节点" description="当前条件下未检索到血缘节点。" />}
							{edges.length ? <CompactTable rowKey={(row, idx) => row.id || `${row.upstreamDatasetId || "up"}-${row.downstreamDatasetId || "down"}-${idx}`} columns={edgeColumns} dataSource={edges} loading={loading} scroll={{ x: 1000 }} pagination={{ pageSize: 8 }} /> : <EmptyState title="暂无关系边" description="当前条件下未检索到血缘关系。" />}
						</Space>
					</Card>
				</>
			) : null}
			<LineageNodeDrawer selectedNode={selectedNode} onClose={() => setSelectedNode(null)} />
		</div>
	);
}
