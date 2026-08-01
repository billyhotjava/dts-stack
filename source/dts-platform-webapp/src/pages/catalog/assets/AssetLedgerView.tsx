import { Alert, Button, Space, Table, Tag } from "antd";
import type { Key } from "react";
import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { toast } from "sonner";
import { batchTagAssets } from "@/api/catalogTagsApi";
import { type ClassificationFactView, getCatalogClassificationFacts } from "@/api/platformApi";
import { AssetTagChips } from "@/components/catalog/tags/AssetTagChips";
import { writeTagIds } from "@/components/catalog/tags/catalogTagUrlState";
import { useRouter } from "@/routes/hooks";
import { resolveAssetReadiness } from "../assetPortalUx.helpers";
import { AssetGovernanceWorkbenchDrawer } from "./AssetGovernanceWorkbenchDrawer";
import { AssetLifecycleWorkbenchDrawer } from "./AssetLifecycleWorkbenchDrawer";
import { ASSET_TYPE_DICT, GOVERNANCE_STATUS_DICT, resolveEnumLabel } from "./assetEnumLabels";
import type { AssetRow } from "./assetPageShared";
import {
	ASSET_ACTION_COLUMN_WIDTH,
	ASSET_TABLE_SCROLL_X,
	classificationText,
	formatTime,
	LAYER_META,
	normalizeLayer,
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
	const classificationFactMap = useMemo(
		() => new Map(classificationFacts.map((fact) => [fact.subjectKey, fact])),
		[classificationFacts],
	);

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

	return (
		<div className="asset-ledger-workbench space-y-3">
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
					rowSelection={
						associationTagId
							? {
									selectedRowKeys: selectedAssetIds,
									onChange: setSelectedAssetIds,
									getCheckboxProps: (row) => ({
										disabled: !row.assetType || !row.assetKey,
										title: !row.assetType || !row.assetKey ? "资产身份不完整，无法关联标签" : undefined,
									}),
								}
							: undefined
					}
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
											<Tag color={effective ? "orange" : "red"}>{classificationText(effective)}</Tag>
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
							width: ASSET_ACTION_COLUMN_WIDTH,
							fixed: "right",
							render: (_, row) => (
								<div className="catalog-assets-actions">
									<Button
										type="primary"
										size="small"
										onClick={(event) => {
											event.stopPropagation();
											setGovernanceAsset(row);
										}}
									>
										治理资产
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
