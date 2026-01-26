import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Alert, Card, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { getDatasetLineage, listDatasets } from "@/api/platformApi";

type DatasetOption = {
	id: string;
	name: string;
};

type LineageNode = {
	id?: string;
	fqn?: string;
	name?: string;
	service?: string;
	database?: string;
	schema?: string;
	type?: string;
	description?: string;
};

type LineageResult = {
	enabled?: boolean;
	found?: boolean;
	message?: string;
	fqn?: string;
	graph?: {
		root?: LineageNode;
		upstream?: LineageNode[];
		downstream?: LineageNode[];
		upstreamDepth?: number;
		downstreamDepth?: number;
	};
};

export default function LineagePage() {
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [selectedId, setSelectedId] = useState<string | undefined>();
	const [loading, setLoading] = useState(false);
	const [lineage, setLineage] = useState<LineageResult | null>(null);

	useEffect(() => {
		void loadDatasets();
	}, []);

	useEffect(() => {
		if (selectedId) {
			void loadLineage(selectedId);
		} else {
			setLineage(null);
		}
	}, [selectedId]);

	const datasetOptions = useMemo(
		() => datasets.map((item) => ({ label: item.name, value: item.id })),
		[datasets],
	);

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200, enabledOnly: true });
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

	const loadLineage = async (id: string) => {
		setLoading(true);
		try {
			const resp: any = await getDatasetLineage(id);
			setLineage(resp || null);
		} catch (error: any) {
			toast.error(error?.message || "血缘加载失败");
			setLineage(null);
		} finally {
			setLoading(false);
		}
	};

	const columns: ColumnsType<LineageNode> = [
		{
			title: "节点",
			dataIndex: "name",
			render: (value, row) => value || row.fqn || "-",
		},
		{
			title: "类型",
			dataIndex: "type",
			width: 120,
			render: (value) => (value ? <Tag>{value}</Tag> : "-"),
		},
		{
			title: "服务",
			dataIndex: "service",
			render: (value) => value || "-",
		},
		{
			title: "数据库",
			dataIndex: "database",
			render: (value) => value || "-",
		},
		{
			title: "Schema",
			dataIndex: "schema",
			render: (value) => value || "-",
		},
		{
			title: "说明",
			dataIndex: "description",
			render: (value) => value || "-",
		},
	];

	const upstream = Array.isArray(lineage?.graph?.upstream) ? lineage?.graph?.upstream ?? [] : [];
	const downstream = Array.isArray(lineage?.graph?.downstream) ? lineage?.graph?.downstream ?? [] : [];

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据资产门户 · 血缘视图"
				description="查看数据集上下游依赖关系与血缘拓扑。"
			/>

			<Card title="选择数据集">
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
				</Space>
			</Card>

			{lineage?.enabled === false ? (
				<Alert type="warning" message="元数据服务未启用，暂无法获取血缘。" showIcon />
			) : lineage?.found === false ? (
				<Alert type="info" message={lineage?.message || "未找到血缘"} showIcon />
			) : null}

			<Card title="上游血缘">
				{upstream.length ? (
					<Table
						rowKey={(row, idx) => row.id || row.fqn || row.name || String(idx)}
						columns={columns}
						dataSource={upstream}
						loading={loading}
						pagination={false}
					/>
				) : (
					<EmptyState title="暂无上游血缘" description="当前数据集暂无上游依赖。" />
				)}
			</Card>

			<Card title="下游血缘">
				{downstream.length ? (
					<Table
						rowKey={(row, idx) => row.id || row.fqn || row.name || String(idx)}
						columns={columns}
						dataSource={downstream}
						loading={loading}
						pagination={false}
					/>
				) : (
					<EmptyState title="暂无下游血缘" description="当前数据集暂无下游依赖。" />
				)}
			</Card>
		</div>
	);
}
