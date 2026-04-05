import { useEffect, useState } from "react";
import { useParams } from "react-router";
import { Button, Spin, Table, Tabs, Tag } from "antd";
import { useRouter } from "@/routes/hooks";
import { getDataset, getDatasetFields, getDatasetGovernanceHealth } from "@/api/platformApi";

export default function DatasetDetailPage() {
	const { id } = useParams<{ id: string }>();
	const router = useRouter();
	const [dataset, setDataset] = useState<Record<string, any> | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		if (!id) return;
		setLoading(true);
		void getDataset(id)
			.then((d: any) => setDataset(d))
			.catch(() => { /* global interceptor handles */ })
			.finally(() => setLoading(false));
	}, [id]);

	if (loading) {
		return (
			<div className="flex h-64 items-center justify-center">
				<Spin />
			</div>
		);
	}
	if (!dataset) {
		return <div className="p-8 text-slate-500">数据集不存在或无权访问。</div>;
	}

	return (
		<div className="space-y-4 p-4">
			<div className="flex items-center gap-3">
				<Button type="text" onClick={() => router.back()}>← 返回</Button>
				<h2 className="text-lg font-bold text-slate-900">{dataset.name ?? "-"}</h2>
				{dataset.warehouseLayer && (
					<Tag color={
						dataset.warehouseLayer === "ODS" ? "default" :
						dataset.warehouseLayer === "DWD" ? "blue" :
						dataset.warehouseLayer === "DWS" ? "cyan" : "green"
					}>
						{dataset.warehouseLayer}
					</Tag>
				)}
			</div>
			<Tabs
				defaultActiveKey="overview"
				items={[
					{
						key: "overview",
						label: "概览",
						children: <DatasetOverviewTab dataset={dataset} />,
					},
					{
						key: "fields",
						label: "字段详情",
						children: <DatasetFieldsTab datasetId={id!} />,
					},
					{
						key: "lineage",
						label: "血缘图",
						children: (
							<div className="py-4 text-sm text-slate-500">
								<a href={`/catalog/lineage?selectedId=${id}`} className="text-blue-600 underline">
									查看完整血缘 →
								</a>
							</div>
						),
					},
					{
						key: "governance",
						label: "治理健康",
						children: <DatasetGovernanceTab datasetId={id!} />,
					},
					{
						key: "access",
						label: "权限申请",
						children: (
							<div className="py-4 text-sm text-slate-500">
								权限申请功能将在后续版本开放，请联系数据管理员。
							</div>
						),
					},
				]}
			/>
		</div>
	);
}

function DatasetOverviewTab({ dataset }: { dataset: Record<string, any> }) {
	return (
		<div className="space-y-3 py-2">
			<div className="grid grid-cols-2 gap-4 text-sm">
				<div><span className="text-slate-500">仓库分层：</span>{dataset.warehouseLayer ?? "-"}</div>
				<div><span className="text-slate-500">密级：</span>{dataset.classification ?? "-"}</div>
				<div><span className="text-slate-500">负责人：</span>{dataset.owner ?? "-"}</div>
				<div><span className="text-slate-500">所属部门：</span>{dataset.ownerDept ?? "-"}</div>
				<div><span className="text-slate-500">类型：</span>{dataset.type ?? "-"}</div>
				<div><span className="text-slate-500">生命周期：</span>{dataset.lifecycleStatus ?? "-"}</div>
				<div><span className="text-slate-500">Hive 表：</span>{dataset.hiveDatabase && dataset.hiveTable ? `${dataset.hiveDatabase}.${dataset.hiveTable}` : "-"}</div>
				<div><span className="text-slate-500">主题域：</span>{dataset.domainName ?? dataset.domain ?? "-"}</div>
			</div>
			{dataset.description && (
				<div className="text-sm text-slate-600 rounded border border-slate-100 bg-slate-50 p-3">
					{dataset.description}
				</div>
			)}
		</div>
	);
}

function DatasetFieldsTab({ datasetId }: { datasetId: string }) {
	const [fields, setFields] = useState<any[]>([]);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		void getDatasetFields(datasetId)
			.then((f: any) => setFields(Array.isArray(f) ? f : []))
			.catch(() => setFields([]))
			.finally(() => setLoading(false));
	}, [datasetId]);

	if (loading) return <div className="py-4"><Spin /></div>;
	if (!fields.length) {
		return <div className="py-4 text-sm text-slate-500">暂无字段信息（未同步或数据集无表结构）。</div>;
	}

	return (
		<Table
			size="small"
			rowKey={(_, idx) => String(idx)}
			dataSource={fields}
			pagination={false}
			columns={[
				{ title: "字段名", dataIndex: "name", render: (v) => <span className="font-mono text-xs">{v}</span> },
				{ title: "类型", dataIndex: "dataType", width: 120 },
				{ title: "描述", dataIndex: "comment", render: (v) => v ?? "-" },
				{ title: "所属表", dataIndex: "tableName", render: (v) => v ?? "-" },
			]}
		/>
	);
}

function DatasetGovernanceTab({ datasetId }: { datasetId: string }) {
	const [health, setHealth] = useState<Record<string, any> | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		void getDatasetGovernanceHealth(datasetId)
			.then((h: any) => setHealth(h ?? null))
			.catch(() => setHealth(null))
			.finally(() => setLoading(false));
	}, [datasetId]);

	if (loading) return <div className="py-4"><Spin /></div>;
	if (!health) return <div className="py-4 text-sm text-slate-500">暂无治理健康数据。</div>;

	const score = health.healthScore ?? health.quality?.healthScore;
	return (
		<div className="space-y-3 py-2 text-sm">
			{score != null && (
				<div className="flex items-center gap-2">
					<span className="text-slate-500">健康分：</span>
					<span className="text-lg font-bold text-blue-600">{score}</span>
					{health.healthLevel && <Tag>{health.healthLevel}</Tag>}
				</div>
			)}
			{health.quality?.totalRuns != null && (
				<div className="grid grid-cols-3 gap-3">
					<div><span className="text-slate-500">总运行：</span>{health.quality.totalRuns}</div>
					<div><span className="text-slate-500">通过：</span><span className="text-green-600">{health.quality.passRuns ?? 0}</span></div>
					<div><span className="text-slate-500">失败：</span><span className="text-red-500">{health.quality.failRuns ?? 0}</span></div>
				</div>
			)}
		</div>
	);
}
