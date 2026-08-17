import { Alert, Button, Select, Space, Tag } from "antd";
import type { Key, MouseEvent as ReactMouseEvent } from "react";
import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { toast } from "sonner";
import { batchTagAssets } from "@/api/catalogTagsApi";
import { updateCatalogAssetV2Governance } from "@/api/platformApi";
import { writeTagIds } from "@/components/catalog/tags/catalogTagUrlState";
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
		const visibleIds = new Set(records.map((row) => row.id));
		setSelectedAssetIds((current) => current.filter((id) => visibleIds.has(String(id))));
	}, [records]);

	const cancelTagAssociation = () => {
		const next = new URLSearchParams(searchParams);
		next.delete("manageTag");
		next.delete("manageTagName");
		setSearchParams(next, { replace: true });
	};

	const associateSelectedAssets = async () => {
		const selectedRows = records.filter((row) => selectedAssetIds.includes(row.id) && row.assetType && row.assetKey);
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
		setBatchSubmitting(true);
		setBatchFailures([]);
		try {
			const result = await assignAssetsToDomain(selectedAssetIds.map(String), batchDomainId, (id, domainId) =>
				updateCatalogAssetV2Governance(id, { domainId }),
			);
			setBatchFailures(result.failures);
			setSelectedAssetIds(result.failures.map((failure) => failure.assetId));
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

	const openDetail = (row: AssetDirectoryRow) => router.push(`/catalog/datasets/${row.id}`);

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
				rowKey="id"
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
						disabled: Boolean(associationTagId) && (!row.assetType || !row.assetKey),
						title: associationTagId && (!row.assetType || !row.assetKey) ? "资产身份不完整，无法关联标签" : undefined,
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
								<Link
									to={`/catalog/datasets/${row.id}`}
									className="block truncate font-medium text-blue-600 hover:text-blue-700"
									onClick={(event) => event.stopPropagation()}
								>
									{value || "-"}
								</Link>
								<div className="truncate text-xs text-slate-500">{row.description || "暂无业务说明"}</div>
							</div>
						),
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
						render: (value) => resolveEnumLabel(DATA_SOURCE_TYPE_DICT, value, "待识别"),
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
				]}
			/>
		</div>
	);
}
