import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import {
	getPublishedQueryDataset,
	listPublishedQueryDatasets,
	type AnalysisDatasetDetail,
	type AnalysisDatasetPage,
	type AnalysisDatasetSummary,
} from "@/api/sql-workbench";
import { PageHeader } from "@/components/page-header";
import { actionColumn, CompactTable } from "@/components/table";
import { useUserRoles } from "@/store/userStore";
import type { ColumnsType } from "antd/es/table";
import {
	Alert,
	Button,
	Descriptions,
	Drawer,
	Empty,
	Input,
	Select,
	Space,
	Spin,
	Tag,
	Tooltip,
	Typography,
} from "antd";
import { PageSection } from "../components/PageContainer/PageContainer";
import { canPromoteSemanticModel } from "./semantic/semanticAccess";

const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZE_OPTIONS = [10, 20, 50];

type ErrorState = {
	message: string;
	correlationId?: string;
};

function positiveInt(value: string | null, fallback: number): number {
	const parsed = Number(value);
	return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function errorState(error: unknown): ErrorState {
	const candidate = error as {
		message?: string;
		response?: { data?: { message?: string; detail?: string; correlationId?: string }; headers?: Record<string, string> };
	};
	return {
		message: candidate.response?.data?.detail || candidate.response?.data?.message || candidate.message || "数据集目录暂时不可用",
		correlationId:
			candidate.response?.data?.correlationId ||
			candidate.response?.headers?.["x-correlation-id"] ||
			candidate.response?.headers?.["x-request-id"],
	};
}

function formatTime(value?: string | null): string {
	return value ? new Date(value).toLocaleString("zh-CN") : "-";
}

export default function DataPage() {
	const navigate = useNavigate();
	const roles = useUserRoles();
	const canCreateAnalysis = canPromoteSemanticModel(roles);
	const [searchParams, setSearchParams] = useSearchParams();
	const page = positiveInt(searchParams.get("page"), 1);
	const pageSize = positiveInt(searchParams.get("size"), DEFAULT_PAGE_SIZE);
	const keyword = searchParams.get("keyword") || "";
	const ownerDept = searchParams.get("ownerDept") || "";
	const bizDomain = searchParams.get("bizDomain") || "";
	const warehouseLayer = searchParams.get("warehouseLayer") || "";
	const classification = searchParams.get("classification") || "";

	const [searchText, setSearchText] = useState(keyword);
	const [ownerText, setOwnerText] = useState(ownerDept);
	const [domainText, setDomainText] = useState(bizDomain);
	const [result, setResult] = useState<AnalysisDatasetPage | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<ErrorState | null>(null);
	const [selected, setSelected] = useState<AnalysisDatasetSummary | null>(null);
	const [detail, setDetail] = useState<AnalysisDatasetDetail | null>(null);
	const [detailLoading, setDetailLoading] = useState(false);
	const [detailError, setDetailError] = useState<ErrorState | null>(null);

	const updateLocation = useCallback(
		(values: Record<string, string | number | undefined>, resetPage = true) => {
			const next = new URLSearchParams(searchParams);
			for (const [key, value] of Object.entries(values)) {
				if (value === undefined || value === "") next.delete(key);
				else next.set(key, String(value));
			}
			if (resetPage) next.set("page", "1");
			setSearchParams(next, { replace: true });
		},
		[searchParams, setSearchParams],
	);

	const load = useCallback(async () => {
		setLoading(true);
		setError(null);
		try {
			const response = await listPublishedQueryDatasets({
				page: page - 1,
				size: pageSize,
				keyword: keyword || undefined,
				ownerDept: ownerDept || undefined,
				bizDomain: bizDomain || undefined,
				warehouseLayer: warehouseLayer || undefined,
				classification: classification || undefined,
			});
			setResult(response);
		} catch (requestError) {
			setResult(null);
			setError(errorState(requestError));
		} finally {
			setLoading(false);
		}
	}, [bizDomain, classification, keyword, ownerDept, page, pageSize, warehouseLayer]);

	useEffect(() => {
		void load();
	}, [load]);

	useEffect(() => {
		setSearchText(keyword);
		setOwnerText(ownerDept);
		setDomainText(bizDomain);
	}, [bizDomain, keyword, ownerDept]);

	const openContract = useCallback(async (dataset: AnalysisDatasetSummary) => {
		setSelected(dataset);
		setDetail(null);
		setDetailError(null);
		setDetailLoading(true);
		try {
			setDetail(await getPublishedQueryDataset(dataset.datasetId, dataset.version));
		} catch (requestError) {
			setDetailError(errorState(requestError));
		} finally {
			setDetailLoading(false);
		}
	}, []);

	const createAnalysis = useCallback(
		(dataset: AnalysisDatasetSummary) => {
			navigate(
				`/bi/questions/new?datasetId=${encodeURIComponent(dataset.datasetId)}&version=${dataset.version}&checksum=${encodeURIComponent(dataset.contractChecksum)}`,
			);
		},
		[navigate],
	);

	const columns: ColumnsType<AnalysisDatasetSummary> = useMemo(
		() => [
			{
				title: "数据集",
				dataIndex: "name",
				key: "name",
				minWidth: 220,
				render: (name: string, row) => (
					<div>
						<Typography.Text strong>{name}</Typography.Text>
						{row.description ? (
							<Typography.Paragraph type="secondary" ellipsis={{ rows: 1 }} className="!mb-0 !mt-1">
								{row.description}
							</Typography.Paragraph>
						) : null}
					</div>
				),
			},
			{ title: "版本", dataIndex: "version", key: "version", width: 82, render: (value: number) => `v${value}` },
			{
				title: "分层",
				dataIndex: "warehouseLayer",
				key: "warehouseLayer",
				width: 86,
				render: (value: string) => <Tag color={value === "ADS" ? "blue" : "cyan"}>{value}</Tag>,
			},
			{
				title: "密级",
				dataIndex: "classification",
				key: "classification",
				width: 140,
				render: (value?: string | null) => <Tag color="gold">{value || "未标注"}</Tag>,
			},
			{ title: "责任部门", dataIndex: "ownerDept", key: "ownerDept", width: 150, render: (value) => value || "-" },
			{
				title: "语义模型",
				dataIndex: "semanticModelNames",
				key: "semanticModelNames",
				minWidth: 180,
				ellipsis: true,
				render: (values: string[]) => (
					<Tooltip title={(values || []).join("、")}>
						<span>{values?.length ? values.join("、") : "-"}</span>
					</Tooltip>
				),
			},
			{ title: "刷新策略", dataIndex: "refreshStrategy", key: "refreshStrategy", width: 110 },
			{
				title: "更新时间",
				dataIndex: "updatedAt",
				key: "updatedAt",
				width: 180,
				render: formatTime,
			},
			actionColumn<AnalysisDatasetSummary>(
				(row) => [
					{ key: "detail", label: "查看详情", onClick: () => void openContract(row) },
					{
						key: "create",
						label: "创建分析",
						disabled: !canCreateAnalysis,
						tooltip: canCreateAnalysis ? undefined : "当前角色没有分析写入权限",
						onClick: () => createAnalysis(row),
					},
				],
				{ maxActions: 2 },
			),
		],
		[canCreateAnalysis, createAnalysis, openContract],
	);

	const hasFilters = Boolean(keyword || ownerDept || bizDomain || warehouseLayer || classification);
	const emptyText = hasFilters ? "没有符合筛选条件的数据集" : "暂无已发布数据集";

	return (
		<div className="space-y-4">
			<div>
				<PageHeader title="BI 数据 / 选择数据集" />
				<p className="mt-1 text-sm text-text-secondary">从平台已发布且治理就绪的数据集中创建可追溯分析。</p>
			</div>
			<PageSection
				title="已发布分析数据集"
				description="仅展示已发布且版本确定、字段指标定义完整、密级策略已生效的 DWS / ADS 数据集。"
			>
				<Space wrap className="mb-4" size={12}>
					<Input.Search
						aria-label="搜索数据集"
						placeholder="搜索名称或语义模型"
						allowClear
						value={searchText}
						style={{ width: 260 }}
						onChange={(event) => setSearchText(event.target.value)}
						onSearch={(value) => updateLocation({ keyword: value.trim() })}
					/>
					<Input
						aria-label="责任部门"
						placeholder="责任部门"
						allowClear
						value={ownerText}
						style={{ width: 150 }}
						onChange={(event) => setOwnerText(event.target.value)}
						onPressEnter={() => updateLocation({ ownerDept: ownerText.trim() })}
						onBlur={() => updateLocation({ ownerDept: ownerText.trim() })}
					/>
					<Input
						aria-label="业务域"
						placeholder="业务域"
						allowClear
						value={domainText}
						style={{ width: 150 }}
						onChange={(event) => setDomainText(event.target.value)}
						onPressEnter={() => updateLocation({ bizDomain: domainText.trim() })}
						onBlur={() => updateLocation({ bizDomain: domainText.trim() })}
					/>
					<Select
						aria-label="数仓分层"
						placeholder="数仓分层"
						allowClear
						value={warehouseLayer || undefined}
						style={{ width: 130 }}
						options={[{ value: "DWS", label: "DWS" }, { value: "ADS", label: "ADS" }]}
						onChange={(value) => updateLocation({ warehouseLayer: value })}
					/>
					<Select
						aria-label="数据密级"
						placeholder="数据密级"
						allowClear
						value={classification || undefined}
						style={{ width: 160 }}
						options={[
							{ value: "DATA_PUBLIC", label: "公开" },
							{ value: "DATA_INTERNAL", label: "内部" },
							{ value: "DATA_SENSITIVE", label: "敏感" },
							{ value: "DATA_RESTRICTED", label: "受限" },
						]}
						onChange={(value) => updateLocation({ classification: value })}
					/>
				</Space>

				{error ? (
					<Alert
						type="error"
						showIcon
						message="数据集加载失败"
						description={error.correlationId ? `${error.message}（请求号：${error.correlationId}）` : error.message}
						action={<Button onClick={() => void load()}>重新加载</Button>}
					/>
				) : (
					<Spin spinning={loading} tip="加载已发布数据集">
						<CompactTable<AnalysisDatasetSummary>
							rowKey={(row) => `${row.datasetId}:${row.version}:${row.contractChecksum}`}
							columns={columns}
							dataSource={result?.items || []}
							scroll={{ x: 1320 }}
							locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} /> }}
							pagination={{
								current: page,
								pageSize,
								total: result?.totalElements || 0,
								showSizeChanger: true,
								pageSizeOptions: PAGE_SIZE_OPTIONS,
								showTotal: (total) => `共 ${total} 个数据集`,
								onChange: (nextPage, nextSize) => {
									if (nextSize !== pageSize) updateLocation({ size: nextSize, page: 1 }, false);
									else updateLocation({ page: nextPage }, false);
								},
							}}
						/>
					</Spin>
				)}
			</PageSection>

			<Drawer
				title={selected ? `数据集详情 · ${selected.name}` : "数据集详情"}
				open={Boolean(selected)}
				width={720}
				destroyOnClose
				onClose={() => {
					setSelected(null);
					setDetail(null);
					setDetailError(null);
				}}
			>
				<Spin spinning={detailLoading} tip="加载数据集详情">
					{detailError ? (
						<Alert
							type="error"
							showIcon
							message="数据集详情加载失败"
							description={detailError.correlationId ? `${detailError.message}（请求号：${detailError.correlationId}）` : detailError.message}
						/>
					) : detail ? (
						<Space direction="vertical" size={20} className="w-full">
							<Descriptions bordered size="small" column={2}>
								<Descriptions.Item label="数据集版本">v{detail.dataset.version}</Descriptions.Item>
								<Descriptions.Item label="字段定义版本">{detail.dataset.semanticContractVersion}</Descriptions.Item>
								<Descriptions.Item label="数仓分层">{detail.dataset.warehouseLayer}</Descriptions.Item>
								<Descriptions.Item label="数据密级">{detail.dataset.classification || "未标注"}</Descriptions.Item>
								<Descriptions.Item label="责任部门">{detail.dataset.ownerDept || "-"}</Descriptions.Item>
								<Descriptions.Item label="刷新策略">{detail.dataset.refreshStrategy}</Descriptions.Item>
								<Descriptions.Item label="语义模型" span={2}>
									{detail.dataset.semanticModelNames.join("、") || "-"}
								</Descriptions.Item>
								<Descriptions.Item label="版本校验值" span={2}>
									<Typography.Text copyable code>{detail.dataset.contractChecksum}</Typography.Text>
								</Descriptions.Item>
							</Descriptions>
							<div>
								<Typography.Title level={5}>可分析字段</Typography.Title>
								<CompactTable
									rowKey="code"
									size="small"
									pagination={false}
									dataSource={detail.dimensions}
									columns={[
										{ title: "字段编码", dataIndex: "code", key: "code" },
										{ title: "业务名称", dataIndex: "label", key: "label" },
										{ title: "数据类型", dataIndex: "dataType", key: "dataType", width: 120 },
									]}
								/>
							</div>
						</Space>
					) : null}
				</Spin>
			</Drawer>
		</div>
	);
}
