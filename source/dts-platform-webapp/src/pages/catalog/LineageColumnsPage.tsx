import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card } from "antd";
import { useSearchParams } from "react-router";
import { CompactTable } from "@/components/table";
import { EmptyState } from "@/components/empty-state";
import { getCatalogLineageImpact } from "@/api/platformApi";
import {
	columnLineageColumns,
	EmptyAction,
	exportImpactCsv,
	type ImpactResult,
	lineageEvidenceDescription,
	LineageDataFilters,
	type LineageDirection,
	LineageSectionNav,
	loadDatasetOptions,
	toIsoInstant,
	useLineageData,
} from "./lineageShared";

export default function LineageColumnsPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const [datasets, setDatasets] = useState<Array<{ id: string; name: string }>>([]);
	const [selectedId, setSelectedId] = useState<string | undefined>(() => searchParams.get("datasetId") || undefined);
	const [direction, setDirection] = useState<LineageDirection>(() =>
		searchParams.get("direction") === "UPSTREAM" || searchParams.get("direction") === "DOWNSTREAM"
			? (searchParams.get("direction") as LineageDirection)
			: "BOTH",
	);
	const [depth, setDepth] = useState(() => {
		const raw = Number(searchParams.get("depth") || 3);
		return Number.isFinite(raw) && raw >= 1 && raw <= 10 ? raw : 3;
	});
	const [projectName, setProjectName] = useState(() => searchParams.get("project") || "");
	const [layerFilters, setLayerFilters] = useState<string[]>(() =>
		(searchParams.get("layers") || "").split(",").filter(Boolean),
	);
	const [changedWithinHours, setChangedWithinHours] = useState(() => {
		const raw = Number(searchParams.get("changed") || 0);
		return Number.isFinite(raw) && raw >= 0 ? raw : 0;
	});
	const [snapshotAt, setSnapshotAt] = useState(() => searchParams.get("at") || "");
	const [keyword, setKeyword] = useState("");
	const [loading, setLoading] = useState(false);
	const [impact, setImpact] = useState<ImpactResult | null>(null);
	const datasetOptions = useMemo(() => datasets.map((item) => ({ label: item.name, value: item.id })), [datasets]);
	const { nodes, edges, columnLineages } = useLineageData(impact, keyword);
	const lineageEvidence = impact?.lineageEvidence;

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

	useEffect(() => {
		const params = new URLSearchParams(searchParams);
		if (selectedId) params.set("datasetId", selectedId);
		else params.delete("datasetId");
		if (direction !== "BOTH") params.set("direction", direction);
		else params.delete("direction");
		if (depth !== 3) params.set("depth", String(depth));
		else params.delete("depth");
		if (projectName.trim()) params.set("project", projectName.trim());
		else params.delete("project");
		if (layerFilters.length) params.set("layers", layerFilters.join(","));
		else params.delete("layers");
		if (changedWithinHours > 0) params.set("changed", String(changedWithinHours));
		else params.delete("changed");
		if (snapshotAt) params.set("at", toIsoInstant(snapshotAt) || snapshotAt);
		else params.delete("at");
		const next = params.toString();
		if (next !== searchParams.toString()) setSearchParams(params, { replace: true });
	}, [
		selectedId,
		direction,
		depth,
		projectName,
		layerFilters,
		changedWithinHours,
		snapshotAt,
		searchParams,
		setSearchParams,
	]);

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
					{lineageEvidence && lineageEvidence.state !== "COMPLETE" ? (
						<Alert
							className="mb-3"
							description={`${lineageEvidenceDescription(lineageEvidence)}。表级 ${lineageEvidence.tableLineageCount} 条，字段级 ${lineageEvidence.columnLineageCount} 条。`}
							message="血缘证据不完整"
							showIcon
							type={lineageEvidence.state === "MISSING" ? "error" : "warning"}
						/>
					) : null}
					{columnLineages.length ? <CompactTable rowKey={(row, idx) => row.id || `${row.upstreamColumn || "up"}-${row.downstreamColumn || "down"}-${idx}`} columns={columnLineageColumns} dataSource={columnLineages} loading={loading} scroll={{ x: 1200 }} pagination={{ defaultPageSize: 10 }} /> : <EmptyState title="暂无字段血缘" description={lineageEvidenceDescription(lineageEvidence)} />}
				</Card>
			) : null}
		</div>
	);
}
