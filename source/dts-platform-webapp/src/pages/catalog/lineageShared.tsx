import {
	ApiOutlined,
	CodeOutlined,
	DatabaseOutlined,
	EyeOutlined,
	FunctionOutlined,
	UploadOutlined,
} from "@ant-design/icons";
import { Button, Descriptions, Drawer, Input, Segmented, Select, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { listDatasets } from "@/api/platformApi";
import {
	applyLayeredLayout,
	type ColumnLineage,
	type DatasetOption,
	edgeEndpoint,
	edgeLabel,
	formatTs,
	type ImpactEdge,
	type ImpactNode,
	type ImpactResult,
	type ImpactStats,
	type LayoutDirection,
	type LineageDiffResult,
	type LineageDirection,
	type LineageSection,
	layerColor,
	lineageSectionMeta,
	lineageSections,
	NODE_SIZE,
	nodeTone,
	relationStroke,
	toIsoInstant,
	verificationColor,
} from "@/features/catalog/lineageContracts";
export {
	applyLayeredLayout,
	type ColumnLineage,
	type DatasetOption,
	edgeEndpoint,
	edgeLabel,
	formatTs,
	type ImpactEdge,
	type ImpactNode,
	type ImpactResult,
	type ImpactStats,
	type LayoutDirection,
	layerColor,
	type LineageDiffResult,
	lineageSectionMeta,
	lineageSections,
	type LineageSection,
	type LineageDirection,
	NODE_SIZE,
	nodeTone,
	relationStroke,
	toIsoInstant,
	verificationColor,
};

export function LineageSectionNav({ section }: { section: LineageSection }) {
	const navigate = useNavigate();
	return (
		<Segmented
			value={section}
			onChange={(value) => navigate(lineageSectionMeta[value as LineageSection].path)}
			options={lineageSections.map((key) => ({ label: lineageSectionMeta[key].title, value: key }))}
		/>
	);
}

export async function loadDatasetOptions() {
	const resp: any = await listDatasets({ page: 0, size: 300, enabledOnly: true });
	const content = Array.isArray(resp?.content) ? resp.content : [];
	return content
		.map((item: any) => ({ id: String(item.id || ""), name: String(item.name || "").trim() }))
		.filter((item: DatasetOption) => item.id && item.name);
}

export function LineageDataFilters({
	datasetOptions,
	selectedId,
	onSelectedIdChange,
	direction,
	onDirectionChange,
	depth,
	onDepthChange,
	projectName,
	onProjectNameChange,
	layerFilters,
	onLayerFiltersChange,
	changedWithinHours,
	onChangedWithinHoursChange,
	keyword,
	onKeywordChange,
	snapshotAt,
	onSnapshotAtChange,
	layoutDirection,
	onLayoutDirectionChange,
	showLayout = false,
}: {
	datasetOptions: Array<{ label: string; value: string }>;
	selectedId?: string;
	onSelectedIdChange: (value?: string) => void;
	direction: LineageDirection;
	onDirectionChange: (value: LineageDirection) => void;
	depth: number;
	onDepthChange: (value: number) => void;
	projectName: string;
	onProjectNameChange: (value: string) => void;
	layerFilters: string[];
	onLayerFiltersChange: (value: string[]) => void;
	changedWithinHours: number;
	onChangedWithinHoursChange: (value: number) => void;
	keyword: string;
	onKeywordChange: (value: string) => void;
	snapshotAt: string;
	onSnapshotAtChange: (value: string) => void;
	layoutDirection?: LayoutDirection;
	onLayoutDirectionChange?: (value: LayoutDirection) => void;
	showLayout?: boolean;
}) {
	return (
		<div className="flex flex-wrap items-center gap-2">
			<Select
				placeholder="选择数据集"
				style={{ minWidth: 320 }}
				value={selectedId}
				options={datasetOptions}
				onChange={onSelectedIdChange}
				showSearch
				optionFilterProp="label"
			/>
			<Select
				style={{ width: 150 }}
				value={direction}
				options={[
					{ label: "双向", value: "BOTH" },
					{ label: "仅上游", value: "UPSTREAM" },
					{ label: "仅下游", value: "DOWNSTREAM" },
				]}
				onChange={onDirectionChange}
			/>
			<Select
				style={{ width: 140 }}
				value={depth}
				options={[1, 2, 3, 5].map((value) => ({ label: `深度 ${value}`, value }))}
				onChange={onDepthChange}
			/>
			<Select
				mode="multiple"
				allowClear
				placeholder="层级过滤"
				style={{ minWidth: 220 }}
				value={layerFilters}
				options={["ODS", "STG", "DWD", "DWS", "ADS", "DIM"].map((value) => ({ label: value, value }))}
				onChange={onLayerFiltersChange}
			/>
			<Select
				style={{ width: 180 }}
				value={changedWithinHours}
				options={[
					{ label: "全部变更", value: 0 },
					{ label: "最近 24 小时", value: 24 },
					{ label: "最近 7 天", value: 24 * 7 },
					{ label: "最近 30 天", value: 24 * 30 },
				]}
				onChange={onChangedWithinHoursChange}
			/>
			{showLayout ? (
				<Select
					style={{ width: 140 }}
					value={layoutDirection}
					options={[
						{ label: "横向布局", value: "LR" },
						{ label: "纵向布局", value: "TB" },
					]}
					onChange={onLayoutDirectionChange}
				/>
			) : null}
			<Input
				style={{ width: 220 }}
				placeholder="项目名过滤（可选）"
				value={projectName}
				onChange={(e) => onProjectNameChange(e.target.value)}
				allowClear
			/>
			<Input
				style={{ width: 220 }}
				type="datetime-local"
				value={snapshotAt}
				onChange={(e) => onSnapshotAtChange(e.target.value)}
			/>
			<Input
				style={{ width: 240 }}
				placeholder="链路快速搜索（节点/关系）"
				value={keyword}
				onChange={(e) => onKeywordChange(e.target.value)}
				allowClear
			/>
		</div>
	);
}

const nodeIcon = (node: ImpactNode) => {
	const kind = String(node.kind || "").toLowerCase();
	const jobType = String(node.jobType || "").toUpperCase();
	const assetType = String(node.assetType || node.type || "").toUpperCase();
	if (kind === "source" || assetType.includes("EXTERNAL")) return <ApiOutlined />;
	if (kind === "job" && jobType.includes("ADDAX")) return <UploadOutlined />;
	if (kind === "job" && jobType.includes("DBT")) return <CodeOutlined />;
	if (kind === "job") return <FunctionOutlined />;
	if (assetType.includes("VIEW")) return <EyeOutlined />;
	return <DatabaseOutlined />;
};

export const renderNodeLabel = (node: ImpactNode) => {
	const tone = nodeTone(node);
	const title = node.name || node.table || "未知节点";
	const subTitle = String(node.kind || "dataset").toUpperCase();
	const detail =
		node.kind === "job"
			? node.jobType || node.relationType || "JOB"
			: node.kind === "source"
				? node.type || "SOURCE"
				: node.layer || node.assetType || node.type || "DATASET";
	return (
		<div className="flex w-[170px] items-center gap-2 overflow-hidden text-left">
			<span style={{ color: tone.color }} className="shrink-0 text-base">
				{nodeIcon(node)}
			</span>
			<span className="min-w-0 flex-1">
				<span className="block text-[10px] uppercase leading-4 text-slate-500">
					{subTitle} · {detail}
				</span>
				<span className="block truncate text-xs font-semibold leading-5 text-slate-900">{title}</span>
			</span>
		</div>
	);
};

export function useLineageData(impact: ImpactResult | null, keyword: string) {
	const nodesRaw = useMemo(() => (Array.isArray(impact?.nodes) ? (impact?.nodes ?? []) : []), [impact?.nodes]);
	const edgesRaw = useMemo(() => (Array.isArray(impact?.edges) ? (impact?.edges ?? []) : []), [impact?.edges]);
	const columnLineagesRaw = useMemo(
		() => (Array.isArray(impact?.columnLineages) ? (impact?.columnLineages ?? []) : []),
		[impact?.columnLineages],
	);
	const keywordLower = keyword.trim().toLowerCase();
	const nodes = useMemo(() => {
		if (!keywordLower) return nodesRaw;
		return nodesRaw.filter((node) =>
			`${node.name || ""} ${node.db || ""}.${node.table || ""} ${node.owner || ""} ${node.ownerDept || ""} ${node.jobType || ""} ${node.status || ""} ${node.lastExecutionStatus || ""}`
				.toLowerCase()
				.includes(keywordLower),
		);
	}, [nodesRaw, keywordLower]);
	const nodeIdSet = useMemo(() => new Set(nodes.map((node) => node.id).filter(Boolean)), [nodes]);
	const edges = useMemo(() => {
		if (!keywordLower) return edgesRaw;
		return edgesRaw.filter((edge) => {
			const byText =
				`${edge.upstreamName || ""} ${edge.downstreamName || ""} ${edge.relationType || ""} ${edge.verificationStatus || ""} ${edge.lastExecutionStatus || ""} ${edge.notes || ""}`
					.toLowerCase()
					.includes(keywordLower);
			const byNode =
				(edgeEndpoint(edge, "from") && nodeIdSet.has(edgeEndpoint(edge, "from")!)) ||
				(edgeEndpoint(edge, "to") && nodeIdSet.has(edgeEndpoint(edge, "to")!));
			return Boolean(byText || byNode);
		});
	}, [edgesRaw, keywordLower, nodeIdSet]);
	const columnLineages = useMemo(() => {
		if (!keywordLower) return columnLineagesRaw;
		return columnLineagesRaw.filter((row) =>
			`${row.upstreamColumn || ""} ${row.downstreamColumn || ""} ${row.relationType || ""} ${row.lineageType || ""} ${row.confidence || ""} ${row.projectName || ""} ${row.expression || ""}`
				.toLowerCase()
				.includes(keywordLower),
		);
	}, [columnLineagesRaw, keywordLower]);
	return { nodes, edges, columnLineages };
}

export const nodeColumns: ColumnsType<ImpactNode> = [
	{ title: "节点", dataIndex: "name", render: (value, row) => value || `${row.db || "-"}.${row.table || "-"}` },
	{
		title: "类型",
		key: "kind",
		width: 120,
		render: (_, row) => (
			<Space size={4}>
				<Tag color={row.kind === "job" ? "orange" : row.kind === "source" ? "magenta" : "blue"}>
					{String(row.kind || "dataset").toUpperCase()}
				</Tag>
				<span className="text-xs text-slate-500">{row.jobType || row.assetType || row.type || "-"}</span>
			</Space>
		),
	},
	{
		title: "分层",
		dataIndex: "layer",
		width: 120,
		render: (value) => (value ? <Tag color={layerColor(value)}>{String(value).toUpperCase()}</Tag> : "-"),
	},
	{ title: "模式.表", key: "table", render: (_, row) => `${row.db || "-"}.${row.table || "-"}` },
	{ title: "负责人", key: "owner", render: (_, row) => row.owner || row.ownerDept || "-" },
	{ title: "状态", key: "status", width: 130, render: (_, row) => row.status || row.lastExecutionStatus || "-" },
	{ title: "最近变更", key: "lastModifiedAt", render: (_, row) => formatTs(row.lastModifiedAt) },
];

export const edgeColumns: ColumnsType<ImpactEdge> = [
	{ title: "上游", key: "upstream", render: (_, row) => row.upstreamName || row.upstreamDatasetId || "-" },
	{ title: "下游", key: "downstream", render: (_, row) => row.downstreamName || row.downstreamDatasetId || "-" },
	{
		title: "关系",
		dataIndex: "relationType",
		width: 140,
		render: (value) => (value ? <Tag color="blue">{value}</Tag> : "-"),
	},
	{
		title: "验证",
		dataIndex: "verificationStatus",
		width: 150,
		render: (value) => (value ? <Tag color={verificationColor(value)}>{value}</Tag> : "-"),
	},
	{
		title: "分层",
		key: "layerFlow",
		width: 180,
		render: (_, row) => `${row.upstreamLayer || "-"} -> ${row.downstreamLayer || "-"}`,
	},
	{ title: "项目", dataIndex: "projectName", width: 150, render: (value) => value || "-" },
	{ title: "最近变更", dataIndex: "lastModifiedAt", width: 190, render: (value) => formatTs(value) },
	{
		title: "有效期",
		key: "validity",
		width: 260,
		render: (_, row) => `${formatTs(row.validFrom)} -> ${row.validTo ? formatTs(row.validTo) : "当前"}`,
	},
];

export const columnLineageColumns: ColumnsType<ColumnLineage> = [
	{ title: "上游字段", key: "upstreamColumn", render: (_, row) => row.upstreamColumn || row.upstreamColumnId || "-" },
	{
		title: "下游字段",
		key: "downstreamColumn",
		render: (_, row) => row.downstreamColumn || row.downstreamColumnId || "-",
	},
	{
		title: "关系",
		dataIndex: "relationType",
		width: 120,
		render: (value) => (value ? <Tag color="blue">{value}</Tag> : "-"),
	},
	{ title: "类型", dataIndex: "lineageType", width: 140, render: (value) => value || "-" },
	{
		title: "置信",
		dataIndex: "confidence",
		width: 120,
		render: (value) =>
			value ? <Tag color={String(value).toUpperCase() === "INFERRED" ? "gold" : "green"}>{value}</Tag> : "-",
	},
	{ title: "项目", dataIndex: "projectName", width: 150, render: (value) => value || "-" },
	{ title: "表达式", dataIndex: "expression", render: (value) => value || "-" },
	{ title: "最近观测", dataIndex: "lastObservedAt", width: 190, render: (value) => formatTs(value) },
];

export const downloadBlob = (name: string, content: BlobPart, type: string) => {
	const blob = new Blob([content], { type });
	const url = URL.createObjectURL(blob);
	const anchor = document.createElement("a");
	anchor.href = url;
	anchor.download = name;
	anchor.click();
	URL.revokeObjectURL(url);
};

const csvEscape = (value: unknown) => {
	const text = value == null ? "" : String(value);
	if (text.includes(",") || text.includes('"') || text.includes("\n")) return `"${text.replaceAll('"', '""')}"`;
	return text;
};

export const downloadCsv = (name: string, rows: Array<Record<string, unknown>>) => {
	if (!rows.length) return;
	const keys = Object.keys(rows[0] || {});
	const body = rows.map((row) => keys.map((key) => csvEscape(row[key])).join(",")).join("\n");
	downloadBlob(name, `${keys.join(",")}\n${body}`, "text/csv;charset=utf-8;");
};

export function exportImpactCsv(nodes: ImpactNode[], edges: ImpactEdge[], columnLineages: ColumnLineage[]) {
	if (!nodes.length && !edges.length && !columnLineages.length) {
		toast.warning("当前无可导出的数据");
		return;
	}
	const stamp = new Date().toISOString().slice(0, 19).replaceAll(":", "-");
	downloadCsv(
		`lineage-nodes-${stamp}.csv`,
		nodes.map((row) => ({
			id: row.id,
			name: row.name,
			layer: row.layer,
			type: row.type,
			schema_table: `${row.db || ""}.${row.table || ""}`,
			owner: row.owner || "",
			owner_dept: row.ownerDept || "",
			source_id: row.sourceId || "",
			lineage_job_id: row.lineageJobId || "",
			job_type: row.jobType || "",
			job_status: row.status || "",
			last_execution_status: row.lastExecutionStatus || "",
			last_observed_at: row.lastObservedAt || "",
			last_modified_at: row.lastModifiedAt || "",
		})),
	);
	downloadCsv(
		`lineage-edges-${stamp}.csv`,
		edges.map((row) => ({
			id: row.id,
			upstream: row.upstreamName || row.upstreamDatasetId || "",
			downstream: row.downstreamName || row.downstreamDatasetId || "",
			relation_type: row.relationType || "",
			lineage_job_id: row.lineageJobId || "",
			verification_status: row.verificationStatus || "",
			last_execution_status: row.lastExecutionStatus || "",
			last_observed_at: row.lastObservedAt || "",
			layer_flow: `${row.upstreamLayer || ""}->${row.downstreamLayer || ""}`,
			project_name: row.projectName || "",
			last_modified_at: row.lastModifiedAt || "",
		})),
	);
	downloadCsv(
		`lineage-columns-${stamp}.csv`,
		columnLineages.map((row) => ({
			id: row.id,
			dataset_lineage_id: row.datasetLineageId || "",
			upstream_dataset_id: row.upstreamDatasetId || "",
			downstream_dataset_id: row.downstreamDatasetId || "",
			upstream_column: row.upstreamColumn || "",
			downstream_column: row.downstreamColumn || "",
			relation_type: row.relationType || "",
			lineage_type: row.lineageType || "",
			confidence: row.confidence || "",
			expression: row.expression || "",
			project_name: row.projectName || "",
			lineage_job_id: row.lineageJobId || "",
			last_observed_at: row.lastObservedAt || "",
			last_modified_at: row.lastModifiedAt || "",
		})),
	);
	toast.success("已导出节点、关系与字段血缘 CSV");
}

export function LineageNodeDrawer({ selectedNode, onClose }: { selectedNode: ImpactNode | null; onClose: () => void }) {
	return (
		<Drawer title="节点详情" open={Boolean(selectedNode)} width={520} onClose={onClose} destroyOnClose>
			{selectedNode ? (
				<Descriptions column={1} bordered size="small">
					<Descriptions.Item label="节点名称">{selectedNode.name || "-"}</Descriptions.Item>
					<Descriptions.Item label="模式.表">{`${selectedNode.db || "-"}.${selectedNode.table || "-"}`}</Descriptions.Item>
					<Descriptions.Item label="分层">
						<Tag color={layerColor(selectedNode.layer)}>{String(selectedNode.layer || "UNKNOWN").toUpperCase()}</Tag>
					</Descriptions.Item>
					<Descriptions.Item label="负责人">{selectedNode.owner || "-"}</Descriptions.Item>
					<Descriptions.Item label="所属部门">{selectedNode.ownerDept || "-"}</Descriptions.Item>
					<Descriptions.Item label="来源数据源 ID">{selectedNode.sourceId || "-"}</Descriptions.Item>
					<Descriptions.Item label="任务类型">{selectedNode.jobType || selectedNode.engine || "-"}</Descriptions.Item>
					<Descriptions.Item label="任务状态">
						{selectedNode.status || selectedNode.lastExecutionStatus || "-"}
					</Descriptions.Item>
					<Descriptions.Item label="最近执行 ID">{selectedNode.lastExecutionId || "-"}</Descriptions.Item>
					<Descriptions.Item label="最近观测">{formatTs(selectedNode.lastObservedAt)}</Descriptions.Item>
					<Descriptions.Item label="最近验证">{formatTs(selectedNode.lastVerifiedAt)}</Descriptions.Item>
					<Descriptions.Item label="最近变更">{formatTs(selectedNode.lastModifiedAt)}</Descriptions.Item>
					<Descriptions.Item label="最近采集">{formatTs(selectedNode.snapshotTime)}</Descriptions.Item>
				</Descriptions>
			) : null}
		</Drawer>
	);
}

export function EmptyAction({ onReload }: { onReload: () => void }) {
	return <Button onClick={onReload}>重新加载</Button>;
}
