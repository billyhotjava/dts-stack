import { ToolOutlined } from "@ant-design/icons";
import { Alert, Button, Select, Space, Table, Tag } from "antd";
import type { Key } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import { toast } from "sonner";
import { batchTagAssets } from "@/api/catalogTagsApi";
import {
	type CatalogAssetStatsProjection,
	type ClassificationFactView,
	getCatalogAssetStatsProjection,
	getCatalogClassificationFacts,
	updateCatalogAssetV2Governance,
} from "@/api/platformApi";
import { AssetTagChips } from "@/components/catalog/tags/AssetTagChips";
import { writeTagIds } from "@/components/catalog/tags/catalogTagUrlState";
import { useRouter } from "@/routes/hooks";
import { resolveAssetReadiness } from "../assetPortalUx.helpers";
import { AssetGovernanceWorkbenchDrawer } from "./AssetGovernanceWorkbenchDrawer";
import { AssetLifecycleWorkbenchDrawer } from "./AssetLifecycleWorkbenchDrawer";
import { type AssetDomainAssignmentFailure, assignAssetsToDomain } from "./assetBatchDomainAssignment";
import { ASSET_TYPE_DICT, GOVERNANCE_STATUS_DICT, resolveEnumLabel } from "./assetEnumLabels";
import type { AssetRow } from "./assetPageShared";
import {
	ASSET_ACTION_COLUMN_WIDTH,
	ASSET_TABLE_SCROLL_X,
	classificationTagColor,
	classificationText,
	formatTime,
	LAYER_META,
	normalizeLayer,
	UNASSIGNED_DOMAIN_KEY,
} from "./assetPageShared";

export interface AssetLedgerViewProps {
	records: AssetRow[];
	domainMap: Map<string, string>;
	ledgerIssueCount: number;
	readyCount: number;
	selectedDomainName: string;
	missingDomainCount: number;
	onAssetChanged?: () => void;
}

/** 资产台账视图：登记核验指标 + 核验列组表格（权属/密级/治理/消费出口）。 */
export function AssetLedgerView({
	records,
	domainMap,
	ledgerIssueCount,
	readyCount,
	selectedDomainName,
	missingDomainCount,
	onAssetChanged,
}: AssetLedgerViewProps) {
	const router = useRouter();
	const [searchParams, setSearchParams] = useSearchParams();
	const associationTagId = searchParams.get("manageTag") || "";
	const associationTagName = searchParams.get("manageTagName") || "当前标签";
	const [classificationFacts, setClassificationFacts] = useState<ClassificationFactView[]>([]);
	const [workbenchAsset, setWorkbenchAsset] = useState<AssetRow | null>(null);
	const [governanceAsset, setGovernanceAsset] = useState<AssetRow | null>(null);
	const [selectedAssetIds, setSelectedAssetIds] = useState<Key[]>([]);
	const [associationSubmitting, setAssociationSubmitting] = useState(false);
	const [batchDomainId, setBatchDomainId] = useState("");
	const [batchSubmitting, setBatchSubmitting] = useState(false);
	const [batchFailures, setBatchFailures] = useState<AssetDomainAssignmentFailure[]>([]);
	const [projection, setProjection] = useState<CatalogAssetStatsProjection | null>(null);
	const [projectionLoading, setProjectionLoading] = useState(false);
	const [projectionError, setProjectionError] = useState("");
	const projectionRequestRef = useRef(0);
	const scopeDomain = searchParams.get("domain") || "";
	const classificationFactMap = useMemo(
		() => new Map(classificationFacts.map((fact) => [fact.subjectKey, fact])),
		[classificationFacts],
	);
	const domainOptions = useMemo(
		() =>
			[...domainMap.entries()]
				.map(([value, label]) => ({ value, label }))
				.sort((left, right) => left.label.localeCompare(right.label)),
		[domainMap],
	);

	useEffect(() => {
		if (scopeDomain && scopeDomain !== UNASSIGNED_DOMAIN_KEY && domainMap.has(scopeDomain)) {
			setBatchDomainId((current) => current || scopeDomain);
		}
	}, [domainMap, scopeDomain]);

	const loadProjection = useCallback(async () => {
		const requestId = projectionRequestRef.current + 1;
		projectionRequestRef.current = requestId;
		setProjectionLoading(true);
		setProjectionError("");
		const domainId = scopeDomain && scopeDomain !== UNASSIGNED_DOMAIN_KEY ? scopeDomain : undefined;
		try {
			const response = await getCatalogAssetStatsProjection(domainId);
			if (projectionRequestRef.current !== requestId) return;
			const snapshot = ((response as any)?.data ?? response) as CatalogAssetStatsProjection;
			setProjection(snapshot);
		} catch (error: unknown) {
			if (projectionRequestRef.current !== requestId) return;
			setProjection(null);
			setProjectionError(error instanceof Error && error.message ? error.message : "资产统计投影读取失败");
		} finally {
			if (projectionRequestRef.current === requestId) setProjectionLoading(false);
		}
	}, [scopeDomain]);

	useEffect(() => {
		void loadProjection();
		return () => {
			projectionRequestRef.current += 1;
		};
	}, [loadProjection]);

	// biome-ignore lint/correctness/useExhaustiveDependencies: changing the association target starts a new bounded selection context.
	useEffect(() => {
		setSelectedAssetIds([]);
	}, [associationTagId]);

	useEffect(() => {
		setGovernanceAsset((current) => (current ? records.find((row) => row.id === current.id) || current : current));
		const visibleIds = new Set(records.map((row) => row.id));
		setSelectedAssetIds((current) => current.filter((id) => visibleIds.has(String(id))));
	}, [records]);

	useEffect(() => {
		const subjects = records
			.filter((row) => Boolean(row.assetKey))
			.map((row) => ({ subjectType: "ASSET", subjectKey: String(row.assetKey) }));
		if (subjects.length === 0) {
			setClassificationFacts([]);
			return;
		}
		let cancelled = false;
		void getCatalogClassificationFacts(subjects)
			.then((facts) => {
				if (!cancelled) setClassificationFacts(Array.isArray(facts) ? facts : []);
			})
			.catch(() => {
				if (!cancelled) setClassificationFacts([]);
			});
		return () => {
			cancelled = true;
		};
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
				void loadProjection();
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

	const projectionTotal = useMemo(() => {
		if (!projection) return undefined;
		if (scopeDomain !== UNASSIGNED_DOMAIN_KEY) return projection.total;
		return projection.buckets
			.filter((bucket) => !bucket.domainId)
			.reduce((total, bucket) => total + Number(bucket.count || 0), 0);
	}, [projection, scopeDomain]);
	const projectionFreshnessLabel = projection
		? { FRESH: "新鲜", STALE: "已过期", REBUILDING: "重建中" }[projection.freshness] || projection.freshness
		: "—";

	return (
		<div className="asset-ledger-workbench space-y-3">
			{projectionError ? (
				<Alert
					type="error"
					showIcon
					message="资产统计投影不可用"
					description={`${projectionError}。资产明细仍可读取，但导航计数不应视为精确值。`}
				/>
			) : (
				<Alert
					type={projection?.freshness === "FRESH" && !projection.approximate ? "info" : "warning"}
					showIcon
					message={projectionLoading ? "正在读取资产统计投影" : `资产统计投影：${projectionFreshnessLabel}`}
					description={
						projection
							? `当前范围 ${projectionTotal ?? 0} 个；统计时点 ${formatTime(projection.asOf)}；状态 ${projection.projectionState || "—"}；${projection.approximate ? "当前为近似计数" : "当前为精确计数"}。`
							: "统计投影尚未生成；资产明细不受影响。"
					}
				/>
			)}
			{associationTagId ? (
				<Alert
					type="info"
					showIcon
					message={`正在关联标签：${associationTagName}`}
					description="请选择当前页中需要关联的资产。缺少统一资产身份的记录不可选择。"
					action={
						<Space>
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
					}
				/>
			) : (
				<div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3">
					<div>
						<div className="text-sm font-medium text-slate-900">批量归域</div>
						<div className="text-xs text-slate-500">显式选择当前页资产，逐项执行并保留失败明细；单批最多 100 个。</div>
					</div>
					<Space wrap>
						<Select
							showSearch
							optionFilterProp="label"
							placeholder="选择目标主题域"
							value={batchDomainId || undefined}
							onChange={setBatchDomainId}
							options={domainOptions}
							style={{ width: 240 }}
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
				</div>
			)}
			{batchFailures.length > 0 ? (
				<Alert
					type="error"
					showIcon
					message={`${batchFailures.length} 个资产归域失败`}
					description={batchFailures.map((failure) => `${failure.assetId}：${failure.message}`).join("；")}
				/>
			) : null}
			<div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
				<div className="rounded-lg border border-slate-200 bg-white px-4 py-3">
					<div className="text-xs text-slate-500">登记核验</div>
					<div className="mt-2 text-xl font-semibold text-slate-900">{records.length}</div>
					<div className="mt-1 text-xs text-slate-500">本页资产</div>
				</div>
				<div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
					<div className="text-xs text-amber-700">待补字段</div>
					<div className="mt-2 text-xl font-semibold text-amber-800">{ledgerIssueCount}</div>
					<div className="mt-1 text-xs text-amber-700">治理阻断 / 密级 / 主题域 / 血缘</div>
				</div>
				<div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3">
					<div className="text-xs text-emerald-700">可消费资产</div>
					<div className="mt-2 text-xl font-semibold text-emerald-800">{readyCount}</div>
					<div className="mt-1 text-xs text-emerald-700">可进入报表、产品和 API 发布</div>
				</div>
				<div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
					<div className="text-xs text-slate-500">当前主题域</div>
					<div className="mt-2 truncate text-xl font-semibold text-slate-900">{selectedDomainName}</div>
					<div className="mt-1 text-xs text-slate-500">未归域 {missingDomainCount} 个</div>
				</div>
			</div>
			<div className="rounded-lg border border-slate-200 bg-white">
				<Table<AssetRow>
					bordered
					size="small"
					rowKey="id"
					dataSource={records}
					pagination={false}
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
					scroll={{ x: ASSET_TABLE_SCROLL_X }}
					tableLayout="fixed"
					className="catalog-assets-table"
					onRow={(row) => ({
						onClick: () => {
							if (!associationTagId) router.push(`/catalog/datasets/${row.id}`);
						},
					})}
					columns={[
						{
							title: "资产",
							dataIndex: "name",
							width: 280,
							render: (value, row) => (
								<div className="min-w-0">
									<div className="truncate font-medium text-slate-900">{value || "-"}</div>
									<div className="truncate font-mono text-[11px] text-slate-500">
										{row.hiveDatabase && row.hiveTable
											? `${row.hiveDatabase}.${row.hiveTable}`
											: row.description || row.id}
									</div>
									<AssetTagChips tags={row.assetTags || []} variant="inline" />
								</div>
							),
						},
						{
							title: "类型/分层",
							width: 150,
							render: (_, row) => (
								<Space direction="vertical" size={2}>
									<Tag>{resolveEnumLabel(ASSET_TYPE_DICT, row.type, "未知类型")}</Tag>
									<Tag color={LAYER_META[normalizeLayer(row.warehouseLayer)].color}>
										{LAYER_META[normalizeLayer(row.warehouseLayer)].label}
									</Tag>
								</Space>
							),
						},
						{
							title: "治理状态",
							width: 180,
							render: (_, row) => {
								const readiness = resolveAssetReadiness(row);
								return (
									<Space direction="vertical" size={2}>
										<Tag color={readiness.color}>{readiness.label}</Tag>
										<span className="text-xs text-slate-500">
											{readiness.reasons.slice(0, 2).join(" / ") ||
												resolveEnumLabel(GOVERNANCE_STATUS_DICT, row.governanceStatus, "-")}
										</span>
									</Space>
								);
							},
						},
						{
							title: "有效密级/主题域",
							width: 200,
							render: (_, row) => {
								const fact = row.assetKey ? classificationFactMap.get(row.assetKey) : undefined;
								const effective = fact?.effectiveLevel || row.classification;
								return (
									<Space direction="vertical" size={2}>
										<div
											title={`有效密级取来源声明、识别、人工下限和全部上游的最高值；快照 v${fact?.snapshotVersion ?? 0}`}
										>
											<Tag color={classificationTagColor(effective)}>{classificationText(effective)}</Tag>
											<Tag color={fact?.propagationStatus === "PROPAGATED" ? "green" : "gold"}>
												{fact?.sealed ? fact.propagationStatus || "已封存" : "待封存"}
											</Tag>
										</div>
										<span className="text-xs text-slate-500">
											{fact?.highestSourceType ? `最高来源：${fact.highestSourceType}` : "来源待补充"}
										</span>
										<span className="text-xs text-slate-500">
											{row.domain || (row.domainId ? domainMap.get(row.domainId) : undefined) || "未归域"}
										</span>
									</Space>
								);
							},
						},
						{
							title: "负责人",
							width: 160,
							render: (_, row) => row.owner || row.ownerDept || "-",
						},
						{
							title: "更新时间",
							width: 170,
							render: (_, row) => formatTime(row.snapshotTime || row.updatedAt),
						},
						{
							title: "操作",
							width: ASSET_ACTION_COLUMN_WIDTH + 84,
							fixed: "right",
							render: (_, row) => (
								<div className="catalog-assets-actions">
									<Button
										type="primary"
										size="small"
										icon={<ToolOutlined />}
										onClick={(event) => {
											event.stopPropagation();
											setGovernanceAsset(row);
										}}
									>
										治理资产
									</Button>
									<Button
										size="small"
										data-testid="row-request-access"
										onClick={(event) => {
											event.stopPropagation();
											router.push(
												`/security/dataset-access-approval?action=new&assetId=${encodeURIComponent(row.id)}&assetType=dataset`,
											);
										}}
									>
										申请权限
									</Button>
								</div>
							),
						},
					]}
				/>
			</div>
			<AssetGovernanceWorkbenchDrawer
				open={Boolean(governanceAsset)}
				asset={governanceAsset}
				classificationFact={governanceAsset?.assetKey ? classificationFactMap.get(governanceAsset.assetKey) : undefined}
				onClose={() => setGovernanceAsset(null)}
				onOpenLifecycle={(asset) => {
					setGovernanceAsset(null);
					setWorkbenchAsset(asset);
				}}
				onChanged={onAssetChanged}
			/>
			<AssetLifecycleWorkbenchDrawer
				open={Boolean(workbenchAsset)}
				asset={workbenchAsset}
				classificationFact={workbenchAsset?.assetKey ? classificationFactMap.get(workbenchAsset.assetKey) : undefined}
				onClose={() => setWorkbenchAsset(null)}
				onChanged={() => {
					const subjects = records
						.filter((row) => Boolean(row.assetKey))
						.map((row) => ({ subjectType: "ASSET", subjectKey: String(row.assetKey) }));
					if (subjects.length > 0) {
						void getCatalogClassificationFacts(subjects).then((facts) => {
							setClassificationFacts(Array.isArray(facts) ? facts : []);
						});
					}
				}}
			/>
		</div>
	);
}
