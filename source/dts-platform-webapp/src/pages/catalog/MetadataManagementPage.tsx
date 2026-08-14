import {
	DatabaseOutlined,
	ExclamationCircleOutlined,
	LinkOutlined,
	SafetyCertificateOutlined,
	TeamOutlined,
} from "@ant-design/icons";
import { Alert, App, Button, Card, Dropdown, Input, Modal, Select, Space, Spin, Tag, Tooltip } from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import {
	listCatalogAssetsV2,
	listCatalogGovernanceIntakeAssets,
	updateCatalogAssetV2Governance,
} from "@/api/platformApi";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { CompactTable } from "@/components/table";
import { useCatalogMaintainerAccess } from "@/hooks/useModuleManageAccess";
import { useRouter } from "@/routes/hooks";
import { classificationRank } from "@/utils/classification";
import {
	CLASSIFICATION_OPTIONS,
	classificationTagColor,
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

const optionalText = (value: unknown) => {
	const text = String(value ?? "").trim();
	return text || undefined;
};

const normalizeAsset = (item: Record<string, unknown>): MetadataAssetRow => {
	const columnCount = Number(item.columnCount);
	return {
		id: optionalText(item.id) || "",
		name:
			optionalText(item.displayName) ||
			optionalText(item.table) ||
			optionalText(item.name) ||
			optionalText(item.fqn) ||
			"-",
		type: optionalText(item.type),
		description: optionalText(item.description),
		owner: optionalText(item.owner),
		ownerDept: optionalText(item.ownerDept),
		classification: optionalText(item.classification),
		domainId: optionalText(item.domainId),
		domain: optionalText(item.domainName) || optionalText(item.domain),
		lifecycleStatus: optionalText(item.lifecycleStatus),
		governanceStatus: optionalText(item.governanceStatus),
		matchStatus: optionalText(item.matchStatus),
		metadataSource: optionalText(item.metadataSource),
		legacyDatasetId: optionalText(item.legacyDatasetId),
		columnCount: Number.isFinite(columnCount) ? columnCount : undefined,
		updatedAt:
			optionalText(item.lastSyncedAt) ||
			optionalText(item.lastModifiedDate) ||
			optionalText(item.updatedAt) ||
			optionalText(item.createdDate),
	};
};

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
	const { message: toast } = App.useApp();
	const [searchParams, setSearchParams] = useSearchParams();
	const canManage = useCatalogMaintainerAccess();
	const requestSeqRef = useRef(0);
	const pageSizeRef = useRef(LEDGER_PAGE_SIZE);
	const scheduledLoadRef = useRef<number | null>(null);
	// 与台账/搜索一致的 URL 筛选协议：classification/governance/match 可深链、可刷新恢复
	const [keyword, setKeyword] = useState(() => searchParams.get("keyword") || "");
	const [classification, setClassification] = useState(() => searchParams.get("classification") || "ALL");
	const [governanceStatus, setGovernanceStatus] = useState(() => searchParams.get("governance") || "ALL");
	const [matchStatus, setMatchStatus] = useState(() => searchParams.get("match") || "ALL");
	const [loading, setLoading] = useState(false);
	const [loadError, setLoadError] = useState<string | null>(null);
	const [records, setRecords] = useState<MetadataAssetRow[]>([]);
	const [pageState, setPageState] = useState({ page: 1, size: LEDGER_PAGE_SIZE, total: 0 });
	const [classificationAsset, setClassificationAsset] = useState<MetadataAssetRow | null>(null);
	const [classificationValue, setClassificationValue] = useState<string>();
	const [classificationSaving, setClassificationSaving] = useState(false);

	const assetQuery = useCallback(
		(page = 1, size = LEDGER_PAGE_SIZE) => ({
			page: page - 1,
			size,
			keyword: keyword.trim() || undefined,
			classification: classification === "ALL" ? undefined : classification,
			governanceStatus: governanceStatus === "ALL" ? undefined : governanceStatus,
			matchStatus: matchStatus === "ALL" ? undefined : matchStatus,
		}),
		[keyword, classification, governanceStatus, matchStatus],
	);

	const loadAssets = useCallback(
		async (page = 1, size = LEDGER_PAGE_SIZE) => {
			const reqId = ++requestSeqRef.current;
			pageSizeRef.current = size;
			setLoading(true);
			try {
				const loader = canManage ? listCatalogGovernanceIntakeAssets : listCatalogAssetsV2;
				const resp = await loader(assetQuery(page, size));
				if (reqId !== requestSeqRef.current) return;
				const content = Array.isArray(resp?.content) ? resp.content : [];
				setRecords(content.map(normalizeAsset).filter((row: MetadataAssetRow) => row.id));
				setLoadError(null);
				setPageState({
					page: Number(resp?.page ?? page - 1) + 1,
					size: Number(resp?.size ?? size),
					total: Number(resp?.total ?? content.length),
				});
			} catch {
				if (reqId === requestSeqRef.current) {
					setRecords([]);
					setPageState({ page: 1, size, total: 0 });
					setLoadError("资产元数据加载失败，请检查目录权限或稍后重试。");
				}
			} finally {
				if (reqId === requestSeqRef.current) {
					setLoading(false);
				}
			}
		},
		[assetQuery, canManage],
	);

	const cancelScheduledLoad = useCallback(() => {
		if (scheduledLoadRef.current !== null) {
			window.clearTimeout(scheduledLoadRef.current);
			scheduledLoadRef.current = null;
		}
	}, []);

	const loadAssetsImmediately = useCallback(
		(page = 1, size = LEDGER_PAGE_SIZE) => {
			cancelScheduledLoad();
			return loadAssets(page, size);
		},
		[cancelScheduledLoad, loadAssets],
	);

	useEffect(() => {
		requestSeqRef.current += 1;
		cancelScheduledLoad();
		scheduledLoadRef.current = window.setTimeout(() => {
			scheduledLoadRef.current = null;
			void loadAssets(1, pageSizeRef.current);
		}, 260);
		return cancelScheduledLoad;
	}, [cancelScheduledLoad, loadAssets]);

	useEffect(() => {
		const params = new URLSearchParams(searchParams);
		if (keyword.trim()) params.set("keyword", keyword.trim());
		else params.delete("keyword");
		if (classification !== "ALL") params.set("classification", classification);
		else params.delete("classification");
		if (governanceStatus !== "ALL") params.set("governance", governanceStatus);
		else params.delete("governance");
		if (matchStatus !== "ALL") params.set("match", matchStatus);
		else params.delete("match");
		const next = params.toString();
		if (next !== searchParams.toString()) setSearchParams(params, { replace: true });
	}, [keyword, classification, governanceStatus, matchStatus, searchParams, setSearchParams]);

	const classificationOptions = useMemo(() => {
		const currentRank = classificationRank(classificationAsset?.classification);
		return CLASSIFICATION_OPTIONS.filter((option) => option.value !== "ALL").map((option) => {
			const optionRank = classificationRank(option.value);
			return {
				...option,
				disabled: currentRank !== undefined && optionRank !== undefined && optionRank < currentRank,
			};
		});
	}, [classificationAsset?.classification]);

	const openClassificationEditor = (row: MetadataAssetRow) => {
		setClassificationAsset(row);
		setClassificationValue(row.classification);
	};

	const saveClassification = async () => {
		if (!classificationAsset || !classificationValue) {
			toast.warning("请选择资产密级");
			return;
		}
		setClassificationSaving(true);
		try {
			await updateCatalogAssetV2Governance(classificationAsset.id, {
				classification: classificationValue,
			});
			toast.success("资产密级已保存");
			setClassificationAsset(null);
			setClassificationValue(undefined);
			await loadAssetsImmediately(pageState.page, pageState.size);
		} catch {
			toast.error("资产密级保存失败，请检查治理权限后重试");
		} finally {
			setClassificationSaving(false);
		}
	};

	const stats = useMemo(() => {
		const missingOwner = records.filter((row) => isBlank(row.owner)).length;
		const missingClassification = records.filter((row) => isBlank(row.classification)).length;
		const missingDomain = records.filter((row) => isBlank(row.domainId) && isBlank(row.domain)).length;
		const completion = records.filter(needsMetadataCompletion).length;
		const unmapped = records.filter(isOpenMetadataUnmapped).length;
		return { missingOwner, missingClassification, missingDomain, completion, unmapped };
	}, [records]);

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
			title: "密级",
			dataIndex: "classification",
			width: 120,
			render: (value?: string) =>
				value ? (
					<Tag color={classificationTagColor(value)}>{classificationText(value)}</Tag>
				) : (
					<Tag color="red">缺密级</Tag>
				),
		},
		{
			title: "主题域",
			dataIndex: "domain",
			width: 160,
			render: (_: unknown, row: MetadataAssetRow) => row.domain || row.domainId || <Tag color="orange">缺主题域</Tag>,
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
			width: 170,
			render: formatTime,
		},
		{
			title: "操作",
			key: "actions",
			width: 230,
			fixed: "right" as const,
			render: (_: unknown, row: MetadataAssetRow) => (
				<Space size={6} wrap>
					<Button size="small" onClick={() => router.push(`/catalog/datasets/${row.id}?tab=overview`)}>
						详情
					</Button>
					{canManage ? (
						<>
							<Button
								size="small"
								type={isBlank(row.classification) ? "primary" : "default"}
								onClick={() => openClassificationEditor(row)}
							>
								{isBlank(row.classification) ? "补齐密级" : "维护密级"}
							</Button>
							<Dropdown
								menu={{
									items: [
										{
											key: "governance",
											label: "治理属性",
											onClick: () => router.push(`/catalog/datasets/${row.id}?tab=governance`),
										},
										...(isBlank(row.classification)
											? []
											: [
													{
														key: "contract",
														label: "字段契约",
														onClick: () => router.push(`/catalog/datasets/${row.id}?tab=schema-contract`),
													},
												]),
										{
											key: "access",
											label: "申请权限",
											onClick: () =>
												router.push(
													`/security/dataset-access-approval?action=new&assetId=${encodeURIComponent(row.id)}&assetType=dataset`,
												),
										},
									],
								}}
							>
								<Button size="small">更多</Button>
							</Dropdown>
						</>
					) : null}
				</Space>
			),
		},
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="元数据管理"
				actions={canManage ? <Button onClick={() => router.push("/catalog/metadata")}>数据源结构采集</Button> : null}
			/>
			<div className="flex items-center gap-2 text-xs text-slate-500">
				<Tooltip title="缺口统计仅覆盖当前筛选结果中的当前页数据；翻页或调整筛选后数字会随之变化。全量治理缺口请查看资产概览。">
					统计范围：第 {pageState.page} 页 / 共 {pageState.total} 条（当前筛选）
				</Tooltip>
			</div>
			<div className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
				<MetricTile
					icon={<DatabaseOutlined />}
					label="当前页资产"
					value={records.length}
					footnote={`第 ${pageState.page} 页 · 总数 ${pageState.total}`}
				/>
				<MetricTile
					icon={<ExclamationCircleOutlined />}
					label="当前页待补齐"
					value={stats.completion}
					tone="text-amber-600"
					footnote={`第 ${pageState.page} 页`}
				/>
				<MetricTile
					icon={<TeamOutlined />}
					label="当前页缺负责人"
					value={stats.missingOwner}
					tone="text-amber-600"
					footnote={`第 ${pageState.page} 页`}
				/>
				<MetricTile
					icon={<SafetyCertificateOutlined />}
					label="当前页缺密级"
					value={stats.missingClassification}
					tone="text-amber-600"
					footnote={`第 ${pageState.page} 页`}
				/>
				<MetricTile
					icon={<LinkOutlined />}
					label="当前页缺主题域"
					value={stats.missingDomain}
					tone="text-amber-600"
					footnote={`第 ${pageState.page} 页`}
				/>
				<MetricTile
					icon={<LinkOutlined />}
					label="当前页未映射"
					value={stats.unmapped}
					tone="text-red-600"
					footnote={`第 ${pageState.page} 页`}
				/>
			</div>

			<Card>
				<Space direction="vertical" size={12} className="w-full">
					<Space wrap>
						<Input.Search
							allowClear
							value={keyword}
							onChange={(event) => setKeyword(event.target.value)}
							onSearch={() => void loadAssetsImmediately(1, pageState.size)}
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
						<Button onClick={() => void loadAssetsImmediately(1, pageState.size)}>刷新</Button>
					</Space>
					{loadError ? <Alert type="error" showIcon message={loadError} /> : null}
					<Spin spinning={loading}>
						{records.length ? (
							<CompactTable
								rowKey={(row: MetadataAssetRow) => row.id}
								columns={columns}
								dataSource={records}
								scroll={{ x: 1200 }}
								expandable={{
									rowExpandable: (row: MetadataAssetRow) =>
										Boolean(row.owner || row.ownerDept || row.lifecycleStatus || row.columnCount),
									expandedRowRender: (row: MetadataAssetRow) => (
										<div className="flex flex-wrap gap-x-8 gap-y-1 px-4 py-2 text-xs text-slate-600">
											<span>负责人：{row.owner || <span className="text-amber-600">缺负责人</span>}</span>
											<span>归属部门：{row.ownerDept || "-"}</span>
											<span>生命周期：{row.lifecycleStatus || "-"}</span>
											<span>字段数：{row.columnCount ?? "-"}</span>
										</div>
									),
								}}
								pagination={{
									current: pageState.page,
									pageSize: pageState.size,
									total: pageState.total,
									showSizeChanger: true,
									onChange: (page, size) => void loadAssetsImmediately(size !== pageState.size ? 1 : page, size),
								}}
							/>
						) : loadError ? null : (
							<EmptyState
								title="暂无资产元数据"
								description={
									canManage
										? "请先完成数据源结构采集，或调整当前筛选条件。"
										: "当前账号下暂无可见资产，请调整筛选条件或联系目录维护人员。"
								}
							/>
						)}
					</Spin>
				</Space>
			</Card>
			<Modal
				title={classificationAsset ? `维护密级：${classificationAsset.name}` : "维护资产密级"}
				open={Boolean(classificationAsset)}
				okText="保存密级"
				cancelText="取消"
				confirmLoading={classificationSaving}
				onOk={() => void saveClassification()}
				onCancel={() => {
					if (!classificationSaving) {
						setClassificationAsset(null);
						setClassificationValue(undefined);
					}
				}}
			>
				<Space direction="vertical" size={12} className="w-full">
					<div className="text-sm text-slate-500">
						密级保存后将立即参与资产访问控制；部门维护者只能维护本部门资产，密级只允许升高、不能降级。
					</div>
					<Select
						className="w-full"
						placeholder="请选择资产密级"
						value={classificationValue}
						options={classificationOptions}
						onChange={setClassificationValue}
					/>
				</Space>
			</Modal>
		</div>
	);
}
