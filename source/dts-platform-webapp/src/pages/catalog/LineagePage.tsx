import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Alert, Card, Input, Select, Space, Statistic, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { getCatalogLineageImpact, listDatasets } from "@/api/platformApi";

type DatasetOption = {
	id: string;
	name: string;
};

type ImpactNode = {
	id?: string;
	name?: string;
	db?: string;
	table?: string;
	type?: string;
	layer?: string;
	ownerDept?: string;
};

type ImpactEdge = {
	id?: string;
	relationType?: string;
	upstreamDatasetId?: string;
	downstreamDatasetId?: string;
	upstreamName?: string;
	downstreamName?: string;
	upstreamAssetType?: string;
	downstreamAssetType?: string;
	direction?: string;
	projectName?: string;
	notes?: string;
};

type ImpactResult = {
	datasetId?: string;
	direction?: string;
	depth?: number;
	projectName?: string;
	nodeCount?: number;
	edgeCount?: number;
	nodes?: ImpactNode[];
	edges?: ImpactEdge[];
};

const layerColor = (layer?: string) => {
	const key = String(layer || "").toUpperCase();
	if (key === "ODS") return "default";
	if (key === "DWD") return "blue";
	if (key === "DWS") return "cyan";
	if (key === "ADS") return "green";
	if (key === "DIM") return "purple";
	return "processing";
};

export default function LineagePage() {
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [selectedId, setSelectedId] = useState<string | undefined>();
	const [loading, setLoading] = useState(false);
	const [impact, setImpact] = useState<ImpactResult | null>(null);
	const [direction, setDirection] = useState<"UPSTREAM" | "DOWNSTREAM" | "BOTH">("BOTH");
	const [depth, setDepth] = useState<number>(3);
	const [projectName, setProjectName] = useState<string>("");

	useEffect(() => {
		void loadDatasets();
	}, []);

	useEffect(() => {
		if (!selectedId) {
			setImpact(null);
			return;
		}
		void loadImpact(selectedId, direction, depth, projectName);
	}, [selectedId, direction, depth, projectName]);

	const datasetOptions = useMemo(() => datasets.map((item) => ({ label: item.name, value: item.id })), [datasets]);

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 300, enabledOnly: true });
			const content = Array.isArray(resp?.content) ? resp.content : [];
			const options = content
				.map((item: any) => ({ id: String(item.id || ""), name: String(item.name || "").trim() }))
				.filter((item: DatasetOption) => item.id && item.name);
			setDatasets(options);
			if (!selectedId && options.length) {
				setSelectedId(options[0].id);
			}
		} catch (error: any) {
			toast.error(error?.message || "数据集加载失败");
		}
	};

	const loadImpact = async (
		datasetId: string,
		dir: "UPSTREAM" | "DOWNSTREAM" | "BOTH",
		depthValue: number,
		project: string,
	) => {
		setLoading(true);
		try {
			const resp: any = await getCatalogLineageImpact(datasetId, {
				direction: dir,
				depth: depthValue,
				projectName: project.trim() || undefined,
			});
			setImpact(resp || null);
		} catch (error: any) {
			toast.error(error?.message || "血缘影响分析加载失败");
			setImpact(null);
		} finally {
			setLoading(false);
		}
	};

	const nodeColumns: ColumnsType<ImpactNode> = [
		{
			title: "节点",
			dataIndex: "name",
			render: (value, row) => value || `${row.db || "-"}.${row.table || "-"}`,
		},
		{
			title: "分层",
			dataIndex: "layer",
			width: 120,
			render: (value) => {
				if (!value) return "-";
				return <Tag color={layerColor(value)}>{String(value).toUpperCase()}</Tag>;
			},
		},
		{
			title: "类型",
			dataIndex: "type",
			width: 130,
			render: (value) => (value ? <Tag>{String(value).toUpperCase()}</Tag> : "-"),
		},
		{
			title: "Schema.Table",
			key: "table",
			render: (_, row) => `${row.db || "-"}.${row.table || "-"}`,
		},
		{
			title: "项目/部门",
			dataIndex: "ownerDept",
			render: (value) => value || "-",
		},
	];

	const edgeColumns: ColumnsType<ImpactEdge> = [
		{
			title: "上游",
			key: "upstream",
			render: (_, row) => row.upstreamName || row.upstreamDatasetId || "-",
		},
		{
			title: "下游",
			key: "downstream",
			render: (_, row) => row.downstreamName || row.downstreamDatasetId || "-",
		},
		{
			title: "关系",
			dataIndex: "relationType",
			width: 140,
			render: (value) => (value ? <Tag color="blue">{value}</Tag> : "-"),
		},
		{
			title: "资产类型",
			key: "assetType",
			width: 220,
			render: (_, row) => {
				const up = row.upstreamAssetType || "-";
				const down = row.downstreamAssetType || "-";
				return `${up} -> ${down}`;
			},
		},
		{
			title: "方向",
			dataIndex: "direction",
			width: 190,
			render: (value) => (value ? <Tag>{value}</Tag> : "-"),
		},
		{
			title: "项目",
			dataIndex: "projectName",
			width: 150,
			render: (value) => value || "-",
		},
	];

	const nodes = Array.isArray(impact?.nodes) ? impact?.nodes ?? [] : [];
	const edges = Array.isArray(impact?.edges) ? impact?.edges ?? [] : [];

	return (
		<div className="space-y-4">
			<PageHeader title="数据资产门户 · 血缘影响分析" description="查看跨模块数据链路（ODS/DWD/DWS/ADS）并按项目过滤。" />

			<Card title="分析条件">
				<Space size={12} wrap>
					<Select
						placeholder="选择数据集"
						style={{ minWidth: 320 }}
						value={selectedId}
						options={datasetOptions}
						onChange={(value) => setSelectedId(value)}
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
						onChange={(value) => setDirection(value)}
					/>
					<Select
						style={{ width: 140 }}
						value={depth}
						options={[
							{ label: "深度 1", value: 1 },
							{ label: "深度 2", value: 2 },
							{ label: "深度 3", value: 3 },
							{ label: "深度 5", value: 5 },
						]}
						onChange={(value) => setDepth(value)}
					/>
					<Input
						style={{ width: 220 }}
						placeholder="按项目名过滤（可选）"
						value={projectName}
						onChange={(e) => setProjectName(e.target.value)}
						allowClear
					/>
				</Space>
			</Card>

			{!selectedId ? <Alert type="info" message="请选择一个数据集开始分析。" showIcon /> : null}

			<Card title="影响概览" loading={loading}>
				<Space size={24} wrap>
					<Statistic title="节点数" value={Number(impact?.nodeCount || 0)} />
					<Statistic title="边数" value={Number(impact?.edgeCount || 0)} />
					<Statistic title="方向" value={impact?.direction || direction} />
					<Statistic title="深度" value={Number(impact?.depth || depth)} />
				</Space>
			</Card>

			<Card title="节点列表">
				{nodes.length ? (
					<Table
						rowKey={(row, idx) => row.id || `${row.db || "db"}.${row.table || "tb"}-${idx}`}
						columns={nodeColumns}
						dataSource={nodes}
						loading={loading}
						pagination={{ pageSize: 10 }}
					/>
				) : (
					<EmptyState title="暂无节点" description="当前条件下未检索到血缘节点。" />
				)}
			</Card>

			<Card title="关系边列表">
				{edges.length ? (
					<Table
						rowKey={(row, idx) => row.id || `${row.upstreamDatasetId || "up"}-${row.downstreamDatasetId || "down"}-${idx}`}
						columns={edgeColumns}
						dataSource={edges}
						loading={loading}
						pagination={{ pageSize: 10 }}
					/>
				) : (
					<EmptyState title="暂无关系边" description="当前条件下未检索到血缘关系。" />
				)}
			</Card>
		</div>
	);
}
