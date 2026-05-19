import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card } from "antd";
import { CompactTable } from "@/components/table";
import { EmptyState } from "@/components/empty-state";
import { getCatalogLineageImpact } from "@/api/platformApi";
import {
	columnLineageColumns,
	EmptyAction,
	exportImpactCsv,
	type ImpactResult,
	LineageDataFilters,
	type LineageDirection,
	LineageSectionNav,
	loadDatasetOptions,
	toIsoInstant,
	useLineageData,
} from "./lineageShared";

export default function LineageColumnsPage() {
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
	const datasetOptions = useMemo(() => datasets.map((item) => ({ label: item.name, value: item.id })), [datasets]);
	const { nodes, edges, columnLineages } = useLineageData(impact, keyword);

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

	return (
		<div className="space-y-4">
			<Card title="血缘与影响分析 / 字段血缘" extra={<Button onClick={() => exportImpactCsv(nodes, edges, columnLineages)} disabled={!columnLineages.length}>导出字段血缘</Button>}>
				<div className="mb-3"><LineageSectionNav section="columns" /></div>
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
			{!selectedId ? <Alert type="info" message="请选择一个数据集查看字段血缘。" showIcon action={<EmptyAction onReload={loadDatasets} />} /> : null}
			{selectedId ? (
				<Card title="字段级输入输出关系">
					{columnLineages.length ? <CompactTable rowKey={(row, idx) => row.id || `${row.upstreamColumn || "up"}-${row.downstreamColumn || "down"}-${idx}`} columns={columnLineageColumns} dataSource={columnLineages} loading={loading} scroll={{ x: 1200 }} pagination={{ pageSize: 10 }} /> : <EmptyState title="暂无字段血缘" description="当前条件下未检索到字段级血缘。" />}
				</Card>
			) : null}
		</div>
	);
}
