import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Alert, Card, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { getDatasetQuality, listDatasets } from "@/api/platformApi";

type DatasetOption = {
	id: string;
	name: string;
};

type QualitySummary = {
	total?: number;
	passed?: number;
	failed?: number;
	aborted?: number;
	missing?: number;
	lastRunAt?: string;
};

type QualityCase = {
	id?: string;
	name?: string;
	description?: string;
	status?: string;
	owner?: string;
	testSuite?: string;
	lastRunAt?: string;
	resultValue?: string;
	passedRows?: number;
	failedRows?: number;
};

type QualityResult = {
	enabled?: boolean;
	found?: boolean;
	message?: string;
	fqn?: string;
	snapshot?: {
		summary?: QualitySummary;
		cases?: QualityCase[];
	};
};

export default function QualityPage() {
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [selectedId, setSelectedId] = useState<string | undefined>();
	const [loading, setLoading] = useState(false);
	const [quality, setQuality] = useState<QualityResult | null>(null);

	useEffect(() => {
		void loadDatasets();
	}, []);

	useEffect(() => {
		if (selectedId) {
			void loadQuality(selectedId);
		} else {
			setQuality(null);
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

	const loadQuality = async (id: string) => {
		setLoading(true);
		try {
			const resp: any = await getDatasetQuality(id);
			setQuality(resp || null);
		} catch (error: any) {
			toast.error(error?.message || "质量结果加载失败");
			setQuality(null);
		} finally {
			setLoading(false);
		}
	};

	const summary: QualitySummary = quality?.snapshot?.summary || {};
	const cases: QualityCase[] = Array.isArray(quality?.snapshot?.cases) ? quality?.snapshot?.cases ?? [] : [];

	const columns: ColumnsType<QualityCase> = [
		{
			title: "规则",
			dataIndex: "name",
			render: (value) => value || "-",
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 120,
			render: (value) => {
				const label = String(value || "UNKNOWN").toUpperCase();
				const color = label === "PASSED" ? "green" : label === "FAILED" ? "red" : "default";
				return <Tag color={color}>{label}</Tag>;
			},
		},
		{
			title: "负责人",
			dataIndex: "owner",
			render: (value) => value || "-",
		},
		{
			title: "测试集",
			dataIndex: "testSuite",
			render: (value) => value || "-",
		},
		{
			title: "最近运行",
			dataIndex: "lastRunAt",
			render: (value) => value || "-",
		},
		{
			title: "结果值",
			dataIndex: "resultValue",
			render: (value) => value || "-",
		},
		{
			title: "失败行数",
			dataIndex: "failedRows",
			render: (value) => (value ?? "-"),
		},
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据治理中心 · 质量报告"
				description="汇总展示数据集质量规则执行结果与趋势。"
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

			{quality?.enabled === false ? (
				<Alert type="warning" message="元数据服务未启用，暂无法获取质量结果。" showIcon />
			) : quality?.found === false ? (
				<Alert type="info" message={quality?.message || "未找到质量结果"} showIcon />
			) : null}

			<Card title="质量概览">
				{quality?.snapshot ? (
					<div className="grid gap-3 md:grid-cols-3">
						<Card size="small" title="总规则数">
							<div className="text-lg font-semibold">{summary.total ?? 0}</div>
						</Card>
						<Card size="small" title="通过">
							<div className="text-lg font-semibold">{summary.passed ?? 0}</div>
						</Card>
						<Card size="small" title="失败">
							<div className="text-lg font-semibold">{summary.failed ?? 0}</div>
						</Card>
					</div>
				) : (
					<EmptyState title="暂无质量概览" description="请先运行质量检测任务。" />
				)}
			</Card>

			<Card title="质量规则详情">
				{cases.length ? (
					<Table
						rowKey={(row, idx) => row.id || row.name || String(idx)}
						columns={columns}
						dataSource={cases}
						loading={loading}
						pagination={{ pageSize: 10 }}
					/>
				) : (
					<EmptyState title="暂无规则结果" description="当前数据集暂无质量规则执行记录。" />
				)}
			</Card>
		</div>
	);
}
