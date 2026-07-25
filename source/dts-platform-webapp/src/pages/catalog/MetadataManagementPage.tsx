import {
	CheckCircleOutlined,
	DatabaseOutlined,
	ExclamationCircleOutlined,
	LinkOutlined,
	ReloadOutlined,
	SafetyCertificateOutlined,
	TeamOutlined,
} from "@ant-design/icons";
import { Alert, Button, Card, Input, message, Select, Space, Spin, Tabs, Tag } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import { getCatalogAssetsV2GovernanceGaps, listCatalogAssetsV2, syncCatalogAssetsV2 } from "@/api/platformApi";
import { TagManagementTab } from "@/components/catalog/tags/TagManagementTab";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { CompactTable } from "@/components/table";
import { useCatalogTagGovernanceAccess } from "@/hooks/useModuleManageAccess";
import { useRouter } from "@/routes/hooks";
import {
	CLASSIFICATION_OPTIONS,
	classificationText,
	formatTime,
	GOVERNANCE_OPTIONS,
	LEDGER_PAGE_SIZE,
	MATCH_OPTIONS,
	MetricTile,
} from "./assets/assetPageShared";

type MetadataAssetRow = {
	id: string;
	name: string;
	type?: string;
	description?: string;
	owner?: string;
	ownerDept?: string;
	classification?: string;
	domainId?: string;
	domain?: string;
	lifecycleStatus?: string;
	governanceStatus?: string;
	matchStatus?: string;
	metadataSource?: string;
	legacyDatasetId?: string;
	columnCount?: number;
	updatedAt?: string;
};

const isBlank = (value?: string) => !String(value || "").trim();

const normalizeAsset = (item: any): MetadataAssetRow => ({
	id: String(item.id || ""),
	name: String(item.displayName || item.table || item.name || item.fqn || "-"),
	type: item.type || undefined,
	description: item.description || undefined,
	owner: item.owner || undefined,
	ownerDept: item.ownerDept || undefined,
	classification: item.classification || undefined,
	domainId: item.domainId ? String(item.domainId) : undefined,
	domain: item.domainName || item.domain || undefined,
	lifecycleStatus: item.lifecycleStatus || undefined,
	governanceStatus: item.governanceStatus || undefined,
	matchStatus: item.matchStatus || undefined,
	metadataSource: item.metadataSource || undefined,
	legacyDatasetId: item.legacyDatasetId ? String(item.legacyDatasetId) : undefined,
	columnCount: Number.isFinite(Number(item.columnCount)) ? Number(item.columnCount) : undefined,
	updatedAt: item.lastSyncedAt || item.lastModifiedDate || item.updatedAt || item.createdDate || undefined,
});

const needsMetadataCompletion = (row: MetadataAssetRow) =>
	isBlank(row.description) ||
	isBlank(row.owner) ||
	isBlank(row.ownerDept) ||
	isBlank(row.classification) ||
	(isBlank(row.domainId) && isBlank(row.domain));

const isOpenMetadataUnmapped = (row: MetadataAssetRow) =>
	String(row.metadataSource || "")
		.toLowerCase()
		.includes("openmetadata") &&
	(isBlank(row.legacyDatasetId) || String(row.matchStatus || "").toUpperCase() !== "MATCHED");

export default function MetadataManagementPage() {
	const router = useRouter();
	const canManageCatalog = useCatalogTagGovernanceAccess();
	const requestSeqRef = useRef(0);
	const [activeTab, setActiveTab] = useState("asset-metadata");
	const [keyword, setKeyword] = useState("");
	const [classification, setClassification] = useState("ALL");
	const [governanceStatus, setGovernanceStatus] = useState("ALL");
	const [matchStatus, setMatchStatus] = useState("ALL");
	const [loading, setLoading] = useState(false);
	const [syncing, setSyncing] = useState(false);
	const [gapLoading, setGapLoading] = useState(false);
	const [records, setRecords] = useState<MetadataAssetRow[]>([]);
	const [governanceGapReport, setGovernanceGapReport] = useState<any | null>(null);
	const [pageState, setPageState] = useState({ page: 1, size: LEDGER_PAGE_SIZE, total: 0 });

	useEffect(() => {
		const timer = window.setTimeout(() => {
			void loadAssets(1, pageState.size);
			void loadGovernanceGaps();
		}, 260);
		return () => window.clearTimeout(timer);
	}, [keyword, classification, governanceStatus, matchStatus, pageState.size]);

	const assetQuery = (page = 1, size = LEDGER_PAGE_SIZE) => ({
		page: page - 1,
		size,
		keyword: keyword.trim() || undefined,
		classification: classification === "ALL" ? undefined : classification,
		governanceStatus: governanceStatus === "ALL" ? undefined : governanceStatus,
		matchStatus: matchStatus === "ALL" ? undefined : matchStatus,
	});

	const loadAssets = async (page = 1, size = LEDGER_PAGE_SIZE) => {
		const reqId = ++requestSeqRef.current;
		setLoading(true);
		try {
			const resp: any = await listCatalogAssetsV2(assetQuery(page, size));
			if (reqId !== requestSeqRef.current) return;
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setRecords(content.map(normalizeAsset).filter((row: MetadataAssetRow) => row.id));
			setPageState({
				page: Number(resp?.page ?? page - 1) + 1,
				size: Number(resp?.size ?? size),
				total: Number(resp?.total ?? content.length),
			});
		} catch {
			if (reqId === requestSeqRef.current) {
				setRecords([]);
			}
		} finally {
			if (reqId === requestSeqRef.current) {
				setLoading(false);
			}
		}
	};

	const loadGovernanceGaps = async () => {
		setGapLoading(true);
		try {
			const result = await getCatalogAssetsV2GovernanceGaps(assetQuery(1, 50));
			setGovernanceGapReport(result || null);
		} catch {
			setGovernanceGapReport(null);
		} finally {
			setGapLoading(false);
		}
	};

	const syncOpenMetadata = async () => {
		setSyncing(true);
		try {
			await syncCatalogAssetsV2(500);
			await loadAssets(1, pageState.size);
			await loadGovernanceGaps();
			message.success("OpenMetadata 资产同步已触发");
		} finally {
			setSyncing(false);
		}
	};

	const stats = useMemo(() => {
		const missingOwner = records.filter((row) => isBlank(row.owner)).length;
		const missingClassification = records.filter((row) => isBlank(row.classification)).length;
		const missingDomain = records.filter((row) => isBlank(row.domainId) && isBlank(row.domain)).length;
		const completion = records.filter(needsMetadataCompletion).length;
		const unmapped = records.filter(isOpenMetadataUnmapped).length;
		const blocking = Number(governanceGapReport?.severityCounts?.BLOCKING || 0);
		return { missingOwner, missingClassification, missingDomain, completion, unmapped, blocking };
	}, [records, governanceGapReport]);

	const columns = [
		{
			title: "资产",
			dataIndex: "name",
			width: 260,
			render: (_: unknown, row: MetadataAssetRow) => (
				<Space direction="vertical" size={2}>
					<Button
						type="link"
						className="h-auto p-0 text-left"
						onClick={() => router.push(`/catalog/datasets/${row.id}?tab=overview`)}
					>
						{row.name}
					</Button>
					<Space size={4} wrap>
						<Tag>{row.type || "TABLE"}</Tag>
						{row.metadataSource ? <Tag color="blue">{row.metadataSource}</Tag> : null}
					</Space>
				</Space>
			),
		},
		{
			title: "业务描述",
			dataIndex: "description",
			width: 260,
			render: (value?: string) => value || <Tag color="orange">待补齐</Tag>,
		},
		{
			title: "负责人",
			dataIndex: "owner",
			width: 140,
			render: (value?: string) => value || <Tag color="orange">缺负责人</Tag>,
		},
		{
			title: "归属部门",
			dataIndex: "ownerDept",
			width: 140,
			render: (value?: string) => value || "-",
		},
		{
			title: "密级",
			dataIndex: "classification",
			width: 120,
			render: (value?: string) =>
				value ? <Tag color="green">{classificationText(value)}</Tag> : <Tag color="orange">缺密级</Tag>,
		},
		{
			title: "主题域",
			dataIndex: "domain",
			width: 160,
			render: (_: unknown, row: MetadataAssetRow) => row.domain || row.domainId || <Tag color="orange">缺主题域</Tag>,
		},
		{
			title: "生命周期",
			dataIndex: "lifecycleStatus",
			width: 120,
			render: (value?: string) => value || "-",
		},
		{
			title: "字段",
			dataIndex: "columnCount",
			width: 90,
			render: (value?: number) => value ?? "-",
		},
		{
			title: "映射",
			dataIndex: "matchStatus",
			width: 150,
			render: (_: unknown, row: MetadataAssetRow) =>
				isOpenMetadataUnmapped(row) ? (
					<Tag color="red">OpenMetadata 未映射</Tag>
				) : (
					<Tag color="green">{row.matchStatus || "已登记"}</Tag>
				),
		},
		{
			title: "更新时间",
			dataIndex: "updatedAt",
			width: 180,
			render: formatTime,
		},
		{
			title: "操作",
			key: "actions",
			width: 250,
			fixed: "right" as const,
			render: (_: unknown, row: MetadataAssetRow) => (
				<Space size={6} wrap>
					<Button size="small" onClick={() => router.push(`/catalog/datasets/${row.id}?tab=governance`)}>
						治理属性
					</Button>
					<Button size="small" onClick={() => router.push(`/catalog/datasets/${row.id}?tab=schema-contract`)}>
						字段契约
					</Button>
					<Button size="small" onClick={() => router.push("/catalog/metadata")}>
						结构采集
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="元数据管理"
				actions={
					activeTab === "asset-metadata" ? (
						<>
							<Button icon={<DatabaseOutlined />} onClick={() => router.push("/catalog/metadata")}>
								数据源结构采集
							</Button>
							<Button
								type="primary"
								icon={<ReloadOutlined />}
								loading={syncing}
								onClick={() => void syncOpenMetadata()}
							>
								同步 OpenMetadata
							</Button>
						</>
					) : null
				}
			/>
			<Tabs
				activeKey={activeTab}
				onChange={setActiveTab}
				items={[
					{ key: "asset-metadata", label: "资产元数据" },
					{ key: "catalog-tags", label: "数据标签" },
				]}
			/>
			{activeTab === "asset-metadata" ? (
				<>
					<div className="text-sm text-slate-500">
						资产语义元数据用于补齐业务描述、权属、密级、主题域、生命周期和标准映射；表/字段结构由数据源结构采集提供。
					</div>

					<Alert
						type="info"
						showIcon
						message="采集与管理已拆分"
						description="数据集成负责扫描表、字段和索引；数据资产负责补齐业务含义、权属和可消费前置条件。"
					/>

					<div className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
						<MetricTile
							icon={<DatabaseOutlined />}
							label="当前页资产"
							value={records.length}
							footnote={`总数 ${pageState.total}`}
						/>
						<MetricTile
							icon={<ExclamationCircleOutlined />}
							label="待补齐"
							value={stats.completion}
							tone="text-amber-600"
						/>
						<MetricTile icon={<TeamOutlined />} label="缺负责人" value={stats.missingOwner} tone="text-amber-600" />
						<MetricTile
							icon={<SafetyCertificateOutlined />}
							label="缺密级"
							value={stats.missingClassification}
							tone="text-amber-600"
						/>
						<MetricTile icon={<LinkOutlined />} label="缺主题域" value={stats.missingDomain} tone="text-amber-600" />
						<MetricTile
							icon={<CheckCircleOutlined />}
							label="OpenMetadata 未映射"
							value={stats.unmapped}
							tone="text-red-600"
						/>
					</div>

					<Card>
						<Space direction="vertical" size={12} className="w-full">
							<Space wrap>
								<Input.Search
									allowClear
									value={keyword}
									onChange={(event) => setKeyword(event.target.value)}
									onSearch={() => void loadAssets(1, pageState.size)}
									placeholder="搜索资产、表名或业务描述"
									style={{ width: 280 }}
								/>
								<Select
									value={classification}
									onChange={setClassification}
									options={CLASSIFICATION_OPTIONS}
									style={{ width: 140 }}
								/>
								<Select
									value={governanceStatus}
									onChange={setGovernanceStatus}
									options={GOVERNANCE_OPTIONS}
									style={{ width: 160 }}
								/>
								<Select value={matchStatus} onChange={setMatchStatus} options={MATCH_OPTIONS} style={{ width: 150 }} />
								<Button onClick={() => void loadAssets(1, pageState.size)}>刷新</Button>
							</Space>
							{stats.blocking ? (
								<Alert
									type="warning"
									showIcon
									message={`治理阻断 ${stats.blocking} 项`}
									description="请优先进入治理属性或字段契约补齐阻断项。"
								/>
							) : null}
							<Spin spinning={loading || gapLoading}>
								{records.length ? (
									<CompactTable
										rowKey={(row: MetadataAssetRow) => row.id}
										columns={columns}
										dataSource={records}
										scroll={{ x: 1900 }}
										pagination={{
											current: pageState.page,
											pageSize: pageState.size,
											total: pageState.total,
											showSizeChanger: true,
											onChange: (page, size) => void loadAssets(size !== pageState.size ? 1 : page, size),
										}}
									/>
								) : (
									<EmptyState title="暂无资产元数据" description="请先完成数据源结构采集，或调整当前筛选条件。" />
								)}
							</Spin>
						</Space>
					</Card>
				</>
			) : (
				<TagManagementTab canManage={canManageCatalog} />
			)}
		</div>
	);
}
