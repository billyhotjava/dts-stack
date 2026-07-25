import { Alert, Button, Descriptions, Spin, Tag } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
	getCatalogAssetV2Lineage,
	getCatalogLineageImpact,
	getDatasetFields,
	getDatasetGovernanceHealth,
	getDatasetIndicatorDeps,
	syncCatalogAssetV2Lineage,
} from "@/api/platformApi";
import { LineageGraph } from "@/components/lineage";
import { CompactTable } from "@/components/table";
import { useRouter } from "@/routes/hooks";
import type { ImpactEdge, ImpactNode } from "./lineageShared";

export function LegacyGovernanceNotice({ dataset }: { dataset: Record<string, any> }) {
	return (
		<div className="space-y-4 py-2">
			<Alert
				type="info"
				showIcon
				message="这是 DTS 原生资产"
				description="当前资产未进入 OpenMetadata 主目录扩展编辑链路；基础属性仍由原数据资产台账维护，质量和指标关系请在“质量与SLA”页查看。"
			/>
			<Descriptions bordered size="small" column={2}>
				<Descriptions.Item label="密级">{dataset.classification || "-"}</Descriptions.Item>
				<Descriptions.Item label="仓库分层">{dataset.warehouseLayer || "-"}</Descriptions.Item>
				<Descriptions.Item label="负责人">{dataset.owner || "-"}</Descriptions.Item>
				<Descriptions.Item label="归属部门">{dataset.ownerDept || "-"}</Descriptions.Item>
				<Descriptions.Item label="生命周期">{dataset.lifecycleStatus || "-"}</Descriptions.Item>
				<Descriptions.Item label="治理状态">{dataset.governanceStatus || "-"}</Descriptions.Item>
			</Descriptions>
		</div>
	);
}

export function MetadataJsonBlock({ title, value }: { title: string; value?: string }) {
	if (!value) {
		return <Alert type="info" showIcon message={`${title} 未同步`} />;
	}
	let text = value;
	try {
		text = JSON.stringify(JSON.parse(value), null, 2);
	} catch {
		// Keep the original payload when it is not JSON.
	}
	return (
		<div>
			<div className="mb-2 text-sm font-medium text-slate-700">{title}</div>
			<pre className="max-h-72 overflow-auto rounded border border-slate-200 bg-slate-950 p-3 text-xs text-slate-100">
				{text}
			</pre>
		</div>
	);
}

export function DatasetFieldsTab({ datasetId, columns }: { datasetId: string; columns?: any[] }) {
	const [fields, setFields] = useState<any[]>([]);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		if (Array.isArray(columns)) {
			setFields(
				columns.map((item) => ({
					name: item.name,
					dataType: item.dataType,
					comment: item.description,
					tableName: item.omColumnFqn || item.source,
				})),
			);
			setLoading(false);
			return;
		}
		void getDatasetFields(datasetId)
			.then((rows: any) => setFields(Array.isArray(rows) ? rows : []))
			.catch(() => setFields([]))
			.finally(() => setLoading(false));
	}, [datasetId, columns]);

	if (loading) {
		return (
			<div className="py-4">
				<Spin />
			</div>
		);
	}
	if (!fields.length) {
		return <div className="py-4 text-sm text-slate-500">暂无字段信息（未同步或数据集无表结构）。</div>;
	}

	return (
		<CompactTable
			size="small"
			rowKey={(_, index) => String(index)}
			dataSource={fields}
			pagination={false}
			columns={[
				{
					title: "字段名",
					dataIndex: "name",
					render: (value) => <span className="font-mono text-xs">{value}</span>,
					sorter: (left, right) => (left.name || "").localeCompare(right.name || ""),
				},
				{ title: "类型", dataIndex: "dataType", width: 120 },
				{ title: "描述", dataIndex: "comment", render: (value) => value ?? "-" },
				{
					title: "所属表",
					dataIndex: "tableName",
					render: (value) => value ?? "-",
					sorter: (left, right) => (left.tableName || "").localeCompare(right.tableName || ""),
				},
			]}
		/>
	);
}

export function DatasetGovernanceTab({ datasetId }: { datasetId: string }) {
	const [health, setHealth] = useState<Record<string, any> | null>(null);
	const [loading, setLoading] = useState(true);
	const [indicators, setIndicators] = useState<any[]>([]);

	useEffect(() => {
		void Promise.all([
			getDatasetGovernanceHealth(datasetId)
				.then((result: any) => setHealth(result ?? null))
				.catch(() => setHealth(null)),
			getDatasetIndicatorDeps(datasetId)
				.then((result: any) => {
					const list = Array.isArray(result?.data?.data)
						? result.data.data
						: Array.isArray(result?.data)
							? result.data
							: Array.isArray(result)
								? result
								: [];
					setIndicators(list);
				})
				.catch(() => setIndicators([])),
		]).finally(() => setLoading(false));
	}, [datasetId]);

	if (loading) {
		return (
			<div className="py-4">
				<Spin />
			</div>
		);
	}

	const score = health?.healthScore ?? health?.quality?.healthScore;
	return (
		<div className="space-y-4 py-2 text-sm">
			{score != null && (
				<div className="flex items-center gap-2">
					<span className="text-slate-500">健康分：</span>
					<span className="text-lg font-bold text-blue-600">{score}</span>
					{health?.healthLevel && <Tag>{health.healthLevel}</Tag>}
				</div>
			)}
			{health?.quality?.totalRuns != null && (
				<div className="grid grid-cols-3 gap-3">
					<div>
						<span className="text-slate-500">总运行：</span>
						{health.quality.totalRuns}
					</div>
					<div>
						<span className="text-slate-500">通过：</span>
						<span className="text-green-600">{health.quality.passRuns ?? 0}</span>
					</div>
					<div>
						<span className="text-slate-500">失败：</span>
						<span className="text-red-500">{health.quality.failRuns ?? 0}</span>
					</div>
				</div>
			)}
			{!score && !health?.quality && <div className="text-slate-500">暂无治理健康数据。</div>}
			{indicators.length > 0 && (
				<div>
					<div className="mb-2 font-medium text-slate-700">关联指标（{indicators.length}）</div>
					<CompactTable
						size="small"
						rowKey={(_, index) => String(index)}
						dataSource={indicators}
						pagination={false}
						columns={[
							{
								title: "指标名称",
								dataIndex: "name",
								render: (value: any) => value ?? "-",
								sorter: (left, right) => (left.name || "").localeCompare(right.name || ""),
							},
							{
								title: "类型",
								dataIndex: "type",
								width: 100,
								render: (value: any) => (value ? <Tag>{value}</Tag> : "-"),
							},
							{
								title: "状态",
								dataIndex: "status",
								width: 90,
								render: (value: any) =>
									value ? <Tag color={value === "PUBLISHED" ? "green" : "default"}>{value}</Tag> : "-",
							},
						]}
					/>
				</div>
			)}
		</div>
	);
}

function OpenMetadataLineageTab({ assetId }: { assetId: string }) {
	const [lineage, setLineage] = useState<any>(null);
	const [loading, setLoading] = useState(true);
	const [syncing, setSyncing] = useState(false);

	const loadLineage = useCallback(async () => {
		setLoading(true);
		try {
			const result = await getCatalogAssetV2Lineage(assetId);
			setLineage(result || null);
		} catch {
			setLineage(null);
		} finally {
			setLoading(false);
		}
	}, [assetId]);

	useEffect(() => {
		void loadLineage();
	}, [loadLineage]);

	const syncLineage = async () => {
		setSyncing(true);
		try {
			await syncCatalogAssetV2Lineage(assetId, { upstreamDepth: 2, downstreamDepth: 2 });
			await loadLineage();
		} catch {
			// The global interceptor reports synchronization failures.
		} finally {
			setSyncing(false);
		}
	};

	if (loading) {
		return (
			<div className="py-6">
				<Spin />
			</div>
		);
	}

	const edges = Array.isArray(lineage?.edges) ? lineage.edges : [];
	if (!edges.length) {
		return (
			<div className="space-y-3 py-4 text-sm text-slate-500">
				<div>暂无OpenMetadata血缘缓存。</div>
				<Button size="small" onClick={() => void syncLineage()} loading={syncing}>
					同步OpenMetadata血缘
				</Button>
			</div>
		);
	}
	return (
		<div className="space-y-3 py-2">
			<div className="flex items-center justify-between">
				<Tag color="blue">OpenMetadata血缘缓存</Tag>
				<Button size="small" onClick={() => void syncLineage()} loading={syncing}>
					同步血缘
				</Button>
			</div>
			<CompactTable
				size="small"
				rowKey={(row: any, index) => row.id || `${row.fromFqn}-${row.toFqn}-${index}`}
				dataSource={edges}
				pagination={{ defaultPageSize: 10 }}
				columns={[
					{
						title: "上游",
						dataIndex: "fromFqn",
						render: (value: any) => <span className="font-mono text-xs">{value || "-"}</span>,
					},
					{
						title: "下游",
						dataIndex: "toFqn",
						render: (value: any) => <span className="font-mono text-xs">{value || "-"}</span>,
					},
					{
						title: "来源",
						dataIndex: "source",
						width: 140,
						render: (value: any) => <Tag>{value || "openmetadata"}</Tag>,
					},
					{ title: "类型", dataIndex: "edgeType", width: 120, render: (value: any) => value || "TABLE" },
				]}
			/>
		</div>
	);
}

export function DatasetLineageImpactTab({ dataset }: { dataset: Record<string, any> }) {
	const router = useRouter();
	const hasOpenMetadataAsset = dataset.__source === "openmetadata" && dataset.id;
	const hasLegacyDataset = Boolean(dataset.__legacyDatasetId);
	return (
		<div className="space-y-4 py-2">
			<Alert
				type={hasOpenMetadataAsset || hasLegacyDataset ? "info" : "warning"}
				showIcon
				message="血缘与影响用于判断资产变更会影响哪些指标、数据产品和看板"
				description={
					hasOpenMetadataAsset && hasLegacyDataset
						? "当前资产同时具备 OpenMetadata 血缘缓存和 DTS 本地治理资产影响链路。"
						: hasOpenMetadataAsset
							? "当前仅有 OpenMetadata 血缘缓存，尚未映射到 DTS 本地治理影响链路。"
							: hasLegacyDataset
								? "当前使用 DTS 本地治理资产影响链路。"
								: "当前资产没有可用血缘证据。"
				}
				action={
					<Button
						size="small"
						onClick={() =>
							dataset.__legacyDatasetId
								? router.push(
										`/catalog/lineage/graph?datasetId=${encodeURIComponent(String(dataset.__legacyDatasetId))}`,
									)
								: router.push("/catalog/lineage/graph")
						}
					>
						血缘图 →
					</Button>
				}
			/>
			{hasOpenMetadataAsset ? (
				<div>
					<div className="mb-2 text-sm font-medium text-slate-700">OpenMetadata 血缘缓存</div>
					<OpenMetadataLineageTab assetId={String(dataset.id)} />
				</div>
			) : null}
			{hasLegacyDataset ? (
				<div>
					<div className="mb-2 text-sm font-medium text-slate-700">DTS 本地影响分析</div>
					<DatasetLineageTab datasetId={String(dataset.__legacyDatasetId)} />
				</div>
			) : null}
			{!hasOpenMetadataAsset && !hasLegacyDataset ? (
				<div className="py-4 text-sm text-slate-500">未映射到DTS治理资产，暂无本地血缘图。</div>
			) : null}
		</div>
	);
}

function DatasetLineageTab({ datasetId }: { datasetId: string }) {
	const router = useRouter();
	const [impact, setImpact] = useState<any>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		void getCatalogLineageImpact(datasetId, { direction: "BOTH", depth: 3 })
			.then((result: any) => setImpact(result ?? null))
			.catch(() => setImpact(null))
			.finally(() => setLoading(false));
	}, [datasetId]);

	const lineageNodes: ImpactNode[] = useMemo(
		() => (Array.isArray(impact?.nodes) ? (impact.nodes as ImpactNode[]) : []),
		[impact],
	);
	const lineageEdges: ImpactEdge[] = useMemo(
		() => (Array.isArray(impact?.edges) ? (impact.edges as ImpactEdge[]) : []),
		[impact],
	);

	if (loading) {
		return (
			<div className="py-6">
				<Spin />
			</div>
		);
	}

	const lineageGraphUrl = `/catalog/lineage/graph?datasetId=${encodeURIComponent(datasetId)}`;
	if (!lineageNodes.length) {
		return (
			<div className="space-y-2 py-4 text-sm text-slate-500">
				<div>暂无血缘数据。</div>
				<Button type="link" size="small" className="px-0" onClick={() => router.push(lineageGraphUrl)}>
					前往血缘分析页 →
				</Button>
			</div>
		);
	}

	return (
		<div className="space-y-2">
			<LineageGraph
				nodes={lineageNodes}
				edges={lineageEdges}
				height={400}
				layoutDirection="LR"
				showMiniMap={false}
				showToolbar={false}
				emptyText="暂无血缘节点"
			/>
			<div className="text-right">
				<Button type="link" size="small" onClick={() => router.push(lineageGraphUrl)}>
					查看完整血缘分析 →
				</Button>
			</div>
		</div>
	);
}
