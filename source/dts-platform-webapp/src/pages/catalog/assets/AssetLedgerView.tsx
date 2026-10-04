import { Alert, Button, Descriptions, Drawer, Empty, List, Select, Space, Tag, Typography } from "antd";
import type { Key, MouseEvent as ReactMouseEvent } from "react";
import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { toast } from "sonner";
import { batchTagAssets } from "@/api/catalogTagsApi";
import { updateCatalogAssetV2Governance } from "@/api/platformApi";
import { writeTagIds } from "@/components/catalog/tags/catalogTagUrlState";
import { GovernedAssetTagPanel } from "@/components/catalog/tags/GovernedAssetTagPanel";
import { CompactTable } from "@/components/table";
import { useRouter } from "@/routes/hooks";
import { type AssetDomainAssignmentFailure, assignAssetsToDomain } from "./assetBatchDomainAssignment";
import { ASSET_TYPE_DICT, resolveEnumLabel } from "./assetEnumLabels";
import type { AssetRow } from "./assetPageShared";
import { classificationTagColor, classificationText, formatTime, LAYER_META, normalizeLayer } from "./assetPageShared";

const DATA_SOURCE_TYPE_DICT: Record<string, string> = {
	...ASSET_TYPE_DICT,
	POSTGRESQL: "PostgreSQL",
	MYSQL: "MySQL",
	ORACLE: "Oracle",
	DAMENG: "达梦",
};

const ELIGIBILITY_META: Record<string, { label: string; color: string }> = {
	ELIGIBLE: { label: "可消费", color: "green" },
	CONDITIONAL: { label: "有条件", color: "gold" },
	BLOCKED: { label: "不可消费", color: "red" },
};

const SERVING_STATUS_META: Record<string, { label: string; color: string }> = {
	SYNCED: { label: "已同步", color: "green" },
	SYNC_PENDING: { label: "同步中", color: "blue" },
	SYNC_FAILED: { label: "同步失败", color: "red" },
	NOT_READY: { label: "待就绪", color: "gold" },
	NOT_APPLICABLE: { label: "不适用", color: "default" },
};

const QUALITY_STATUS_META: Record<string, { label: string; color: string }> = {
	PASSED: { label: "已通过", color: "green" },
	FAILED: { label: "未通过", color: "red" },
	UNKNOWN: { label: "暂无证据", color: "default" },
};

const ASSET_FAMILY_LABELS: Record<string, string> = {
	DATASET: "数据表",
	SEMANTIC_MODEL: "数据模型",
	GOV_INDICATOR: "指标",
	BI_DATASET: "分析数据集",
	SCREEN: "看板",
	DATA_PRODUCT: "数据产品",
	API_SERVICE: "数据服务",
};

const RELATION_TYPE_LABELS: Record<string, string> = {
	MATERIALIZES_TO: "构建为数据表",
	MATERIALIZED_FROM: "由数据模型构建",
	DERIVED_FROM: "来源于数据模型",
	SERVES_BI_DATASET: "支撑分析数据集",
	VISUALIZES: "使用分析数据集",
	VISUALIZED_BY: "用于看板展示",
	SERVES: "基于数据表提供服务",
	SERVED_BY: "由数据服务提供",
	CALCULATED_FROM: "基于数据表计算",
	SUPPORTS_INDICATOR: "支撑指标计算",
};

export type AssetDirectoryRow = AssetRow & {
	service?: string;
	columnCount?: number;
	syncStatus?: string;
};

export interface AssetLedgerViewProps {
	records: AssetDirectoryRow[];
	domainMap: Map<string, string>;
	loading: boolean;
	page: number;
	pageSize: number;
	total: number;
	onPageChange: (page: number, pageSize: number) => void;
	onAssetChanged?: () => void;
}

const selectionKey = (row: AssetDirectoryRow) =>
	row.catalogIdentity || `${row.assetType || "DATASET"}\u0000${row.assetKey || row.id}`;

/** 数据资产目录表格：仅承载检索结果、批量动作和详情导航。 */
export function AssetLedgerView({
	records,
	domainMap,
	loading,
	page,
	pageSize,
	total,
	onPageChange,
	onAssetChanged,
}: AssetLedgerViewProps) {
	const router = useRouter();
	const [searchParams, setSearchParams] = useSearchParams();
	const associationTagId = searchParams.get("manageTag") || "";
	const associationTagName = searchParams.get("manageTagName") || "当前标签";
	const [selectedAssetIds, setSelectedAssetIds] = useState<Key[]>([]);
	const [associationSubmitting, setAssociationSubmitting] = useState(false);
	const [batchDomainId, setBatchDomainId] = useState("");
	const [batchSubmitting, setBatchSubmitting] = useState(false);
	const [batchFailures, setBatchFailures] = useState<AssetDomainAssignmentFailure[]>([]);
	const [detailAsset, setDetailAsset] = useState<AssetDirectoryRow | null>(null);
	const scopeDomain = searchParams.get("domain") || "";

	const domainOptions = useMemo(
		() =>
			[...domainMap.entries()]
				.map(([value, label]) => ({ value, label }))
				.sort((left, right) => left.label.localeCompare(right.label, "zh-CN")),
		[domainMap],
	);

	useEffect(() => {
		if (scopeDomain && domainMap.has(scopeDomain)) {
			setBatchDomainId((current) => current || scopeDomain);
		}
	}, [domainMap, scopeDomain]);

	useEffect(() => {
		void associationTagId;
		setSelectedAssetIds([]);
	}, [associationTagId]);

	useEffect(() => {
		const visibleIds = new Set(records.map(selectionKey));
		setSelectedAssetIds((current) => current.filter((id) => visibleIds.has(String(id))));
	}, [records]);

	const cancelTagAssociation = () => {
		const next = new URLSearchParams(searchParams);
		next.delete("manageTag");
		next.delete("manageTagName");
		setSearchParams(next, { replace: true });
	};

	const associateSelectedAssets = async () => {
		const selectedRows = records.filter(
			(row) => selectedAssetIds.includes(selectionKey(row)) && row.assetType && row.assetKey,
		);
		if (!associationTagId || selectedRows.length === 0 || associationSubmitting) return;
		setAssociationSubmitting(true);
		try {
			const result = await batchTagAssets({
				assets: selectedRows.map((row) => ({
					assetType: String(row.assetType),
					assetKey: String(row.assetKey),
				})),
				tagIds: [associationTagId],
			});
			toast.success(
				`已关联 ${String(result?.assetCount || selectedRows.length)} 个资产，新增 ${String(result?.created || 0)} 条关系`,
			);
			setSelectedAssetIds([]);
			const next = new URLSearchParams(searchParams);
			next.delete("manageTag");
			next.delete("manageTagName");
			next.delete("tab");
			setSearchParams(writeTagIds(next, [associationTagId]), { replace: true });
		} catch (error: unknown) {
			toast.error(error instanceof Error && error.message ? error.message : "批量关联业务数据标签失败");
		} finally {
			setAssociationSubmitting(false);
		}
	};

	const assignSelectedAssets = async () => {
		if (!batchDomainId || selectedAssetIds.length === 0 || batchSubmitting) return;
		const selectedDatasets = records.filter(
			(row) => selectedAssetIds.includes(selectionKey(row)) && row.assetType === "DATASET",
		);
		if (selectedDatasets.length === 0) {
			toast.error("仅数据表资产支持批量归域");
			return;
		}
		setBatchSubmitting(true);
		setBatchFailures([]);
		try {
			const result = await assignAssetsToDomain(
				selectedDatasets.map((row) => row.id),
				batchDomainId,
				(id, domainId) => updateCatalogAssetV2Governance(id, { domainId }),
			);
			setBatchFailures(result.failures);
			const failedIds = new Set(result.failures.map((failure) => failure.assetId));
			setSelectedAssetIds(selectedDatasets.filter((row) => failedIds.has(row.id)).map(selectionKey));
			if (result.succeededIds.length > 0) {
				toast.success(`已完成 ${result.succeededIds.length} 个资产归域`);
				onAssetChanged?.();
			}
			if (result.failures.length > 0) {
				toast.error(`${result.failures.length} 个资产归域失败，请查看逐项结果`);
			}
		} catch (error: unknown) {
			toast.error(error instanceof Error && error.message ? error.message : "批量归域失败");
		} finally {
			setBatchSubmitting(false);
		}
	};

	const openDetail = (row: AssetDirectoryRow) => {
		if (row.assetType !== "DATASET") {
			setDetailAsset(row);
			return;
		}
		router.push(`/catalog/datasets/${row.id}`);
	};

	const editAsset = (row: AssetDirectoryRow) => {
		const family = String(row.assetFamily || row.assetType || "");
		if (family === "DATASET") {
			router.push(`/catalog/datasets/${row.id}?tab=governance`);
			return;
		}
		const sourceRoute = family === "SCREEN" ? "/bi/screens" : row.detailRoute;
		if (sourceRoute) router.push(sourceRoute);
	};

	const isInteractiveTarget = (event: ReactMouseEvent<HTMLElement>) =>
		Boolean((event.target as HTMLElement).closest("a, button, input, .ant-checkbox-wrapper, .ant-select"));

	return (
		<div className="min-w-0 space-y-3">
			<div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
				{associationTagId ? (
					<>
						<div className="min-w-0 text-sm text-slate-700">
							正在关联标签：<span className="font-medium text-slate-900">{associationTagName}</span>
							<span className="ml-2 text-xs text-slate-500">已选择 {selectedAssetIds.length} 项</span>
						</div>
						<Space wrap>
							<Button onClick={cancelTagAssociation}>取消</Button>
							<Button
								type="primary"
								loading={associationSubmitting}
								disabled={selectedAssetIds.length === 0}
								onClick={() => void associateSelectedAssets()}
							>
								关联选中资产（{selectedAssetIds.length}）
							</Button>
						</Space>
					</>
				) : (
					<>
						<div className="text-sm text-slate-700">
							<span className="font-medium text-slate-900">批量归域</span>
							<span className="ml-2 text-xs text-slate-500">
								已选择 {selectedAssetIds.length} 项，单批最多选择 100 个资产
							</span>
						</div>
						<Space wrap>
							<Select
								aria-label="批量归域目标业务归属数据域"
								showSearch
								optionFilterProp="label"
								placeholder="目标业务归属数据域"
								value={batchDomainId || undefined}
								onChange={setBatchDomainId}
								options={domainOptions}
								className="w-52"
							/>
							<Button
								type="primary"
								loading={batchSubmitting}
								disabled={!batchDomainId || selectedAssetIds.length === 0}
								onClick={() => void assignSelectedAssets()}
							>
								归域选中资产（{selectedAssetIds.length}）
							</Button>
						</Space>
					</>
				)}
			</div>

			{batchFailures.length > 0 ? (
				<Alert
					type="error"
					showIcon
					message={`${batchFailures.length} 个资产归域失败`}
					description={batchFailures.map((failure) => `${failure.assetId}：${failure.message}`).join("；")}
				/>
			) : null}

			<CompactTable<AssetDirectoryRow>
				rowKey={selectionKey}
				loading={loading}
				dataSource={records}
				className="catalog-assets-table"
				rowClassName={() => "cursor-pointer"}
				pagination={{
					current: page,
					pageSize,
					total,
					onChange: onPageChange,
				}}
				rowSelection={{
					selectedRowKeys: selectedAssetIds,
					onChange: (keys) => {
						if (keys.length > 100) {
							toast.error("单批最多选择 100 个资产");
							return;
						}
						setSelectedAssetIds(keys);
					},
					getCheckboxProps: (row) => ({
						disabled: associationTagId ? !row.assetType || !row.assetKey : row.assetType !== "DATASET",
						title: associationTagId
							? !row.assetType || !row.assetKey
								? "资产身份不完整，无法关联标签"
								: undefined
							: row.assetType !== "DATASET"
								? "仅数据表资产支持批量归域"
								: undefined,
					}),
				}}
				onRow={(row) => ({
					onClick: (event) => {
						if (!isInteractiveTarget(event)) openDetail(row);
					},
				})}
				columns={[
					{
						title: "资产名称",
						dataIndex: "name",
						width: 260,
						render: (value, row) => (
							<div className="min-w-0">
								{row.assetType === "DATASET" ? (
									<Link
										to={`/catalog/datasets/${row.id}`}
										className="block truncate font-medium text-blue-600 hover:text-blue-700"
										onClick={(event) => event.stopPropagation()}
									>
										{value || "-"}
									</Link>
								) : (
									<button
										type="button"
										className="block max-w-full truncate font-medium text-blue-600 hover:text-blue-700"
										onClick={(event) => {
											event.stopPropagation();
											openDetail(row);
										}}
									>
										{value || "-"}
									</button>
								)}
								<div className="truncate text-xs text-slate-500">{row.description || "暂无业务说明"}</div>
							</div>
						),
					},
					{
						title: "资产家族",
						dataIndex: "assetFamily",
						width: 120,
						render: (value, row) =>
							ASSET_FAMILY_LABELS[String(value || row.assetType || "DATASET")] || value || "待识别",
					},
					{
						title: "技术标识",
						dataIndex: "assetKey",
						width: 250,
						render: (value, row) => <span className="font-mono text-xs">{value || row.id}</span>,
					},
					{
						title: "数据源类型",
						dataIndex: "type",
						width: 120,
						render: (value, row) =>
							row.assetType !== "DATASET" ? "不适用" : resolveEnumLabel(DATA_SOURCE_TYPE_DICT, value, "待识别"),
					},
					{
						title: "来源系统",
						dataIndex: "service",
						width: 150,
						render: (value) => value || "-",
					},
					{
						title: "业务归属数据域",
						width: 150,
						render: (_, row) => row.domain || (row.domainId ? domainMap.get(row.domainId) : undefined) || "待归域",
					},
					{
						title: "数据分层",
						dataIndex: "warehouseLayer",
						width: 110,
						render: (value) => {
							const layer = LAYER_META[normalizeLayer(value)];
							return <Tag color={layer.color}>{layer.label}</Tag>;
						},
					},
					{
						title: "密级",
						dataIndex: "classification",
						width: 100,
						render: (value) => <Tag color={classificationTagColor(value)}>{classificationText(value)}</Tag>,
					},
					{
						title: "责任归属",
						width: 160,
						render: (_, row) =>
							row.owner && row.ownerDept && row.owner !== row.ownerDept
								? `${row.owner}（${row.ownerDept}）`
								: row.owner || row.ownerDept || "待明确",
					},
					{
						title: "消费资格",
						dataIndex: "consumptionEligibility",
						width: 120,
						render: (value, row) => {
							const meta = ELIGIBILITY_META[String(value || "CONDITIONAL")] || ELIGIBILITY_META.CONDITIONAL;
							return (
								<Tag color={meta.color} title={row.eligibilityReasons?.join("；") || undefined}>
									{meta.label}
								</Tag>
							);
						},
					},
					{
						title: "服务状态",
						width: 120,
						render: (_, row) => {
							const code = String(row.servingSync?.status || "NOT_APPLICABLE");
							const meta = SERVING_STATUS_META[code] || { label: code, color: "default" };
							return (
								<Tag color={meta.color} title={row.servingSync?.lastError || undefined}>
									{meta.label}
								</Tag>
							);
						},
					},
					{
						title: "质量状态",
						dataIndex: "qualityStatus",
						width: 120,
						render: (value) => {
							const code = String(value || "UNKNOWN");
							const meta = QUALITY_STATUS_META[code] || { label: code, color: "default" };
							return <Tag color={meta.color}>{meta.label}</Tag>;
						},
					},
					{
						title: "更新时间",
						width: 170,
						render: (_, row) => formatTime(row.snapshotTime || row.updatedAt),
					},
					{
						title: "操作",
						width: 110,
						fixed: "right",
						render: (_, row) => {
							const family = String(row.assetFamily || row.assetType || "");
							const editable = family === "DATASET" || family === "SCREEN" || Boolean(row.detailRoute);
							return (
								<Button
									type="link"
									size="small"
									disabled={!editable}
									onClick={(event) => {
										event.stopPropagation();
										editAsset(row);
									}}
								>
									编辑资产
								</Button>
							);
						},
					},
				]}
			/>

			<UnifiedAssetDetailDrawer asset={detailAsset} onClose={() => setDetailAsset(null)} />
		</div>
	);
}

function UnifiedAssetDetailDrawer({ asset, onClose }: { asset: AssetDirectoryRow | null; onClose: () => void }) {
	const family = String(asset?.assetFamily || asset?.assetType || "");
	const relationships = asset?.relationships || [];
	const sourceRoute = family === "SCREEN" ? "/bi/screens" : asset?.detailRoute;
	return (
		<Drawer
			open={Boolean(asset)}
			onClose={onClose}
			width={720}
			title={asset ? `${ASSET_FAMILY_LABELS[family] || "数据资产"}详情` : "数据资产详情"}
			destroyOnClose
		>
			{asset ? (
				<div className="space-y-6">
					<Descriptions bordered size="small" column={2}>
						<Descriptions.Item label="资产名称" span={2}>
							{asset.name}
						</Descriptions.Item>
						<Descriptions.Item label="资产家族">{ASSET_FAMILY_LABELS[family] || family || "待识别"}</Descriptions.Item>
						<Descriptions.Item label="资产子类型">{asset.subtype || "不适用"}</Descriptions.Item>
						<Descriptions.Item label="生命周期">{asset.lifecycleStatus || "待明确"}</Descriptions.Item>
						<Descriptions.Item label="密级">{classificationText(asset.classification)}</Descriptions.Item>
						<Descriptions.Item label="数据分层">
							{LAYER_META[normalizeLayer(asset.warehouseLayer)].label}
						</Descriptions.Item>
						<Descriptions.Item label="责任人">{asset.owner || "待明确"}</Descriptions.Item>
						<Descriptions.Item label="责任部门">{asset.ownerDept || "待明确"}</Descriptions.Item>
						<Descriptions.Item label="质量状态">
							{QUALITY_STATUS_META[String(asset.qualityStatus || "UNKNOWN")]?.label || "暂无证据"}
						</Descriptions.Item>
						<Descriptions.Item label="来源功能">{asset.service || "平台登记"}</Descriptions.Item>
						<Descriptions.Item label="技术标识" span={2}>
							<Typography.Text code copyable>
								{asset.assetKey || asset.id}
							</Typography.Text>
						</Descriptions.Item>
						<Descriptions.Item label="业务说明" span={2}>
							{asset.description || "暂无业务说明"}
						</Descriptions.Item>
					</Descriptions>

					<div>
						<div className="mb-2 font-medium text-slate-900">资产关系</div>
						{relationships.length ? (
							<List
								bordered
								size="small"
								dataSource={relationships}
								renderItem={(relation) => (
									<List.Item extra={<Tag>{relation.direction === "OUTGOING" ? "本资产关联" : "关联到本资产"}</Tag>}>
										<div className="min-w-0">
											<div className="font-medium">{relation.displayName || relation.assetKey || "未命名资产"}</div>
											<div className="truncate text-xs text-slate-500">
												{RELATION_TYPE_LABELS[String(relation.relationType)] || "其他已登记关系"} ·{" "}
												{ASSET_FAMILY_LABELS[String(relation.assetType)] || relation.assetType}
											</div>
										</div>
										{relation.detailRoute ? <Link to={relation.detailRoute}>查看</Link> : null}
									</List.Item>
								)}
							/>
						) : (
							<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已登记关系" />
						)}
					</div>

					<div>
						<div className="mb-2 font-medium text-slate-900">业务数据标签</div>
						{asset.assetType && asset.assetKey ? (
							<GovernedAssetTagPanel assetType={asset.assetType} assetKey={asset.assetKey} />
						) : (
							<Alert type="warning" showIcon message="资产身份不完整，暂不能治理标签" />
						)}
					</div>

					{sourceRoute ? (
						<div className="flex justify-end">
							<Link to={sourceRoute}>{family === "SCREEN" ? "进入大屏管理" : "进入来源功能"}</Link>
						</div>
					) : null}
				</div>
			) : null}
		</Drawer>
	);
}
