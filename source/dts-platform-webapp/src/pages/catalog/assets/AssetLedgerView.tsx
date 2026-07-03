import { Button, Space, Table, Tag } from "antd";
import { useRouter } from "@/routes/hooks";
import { resolveAssetReadiness } from "../assetPortalUx.helpers";
import {
	ASSET_ACTION_COLUMN_WIDTH,
	ASSET_TABLE_SCROLL_X,
	LAYER_META,
	classificationText,
	formatTime,
	normalizeLayer,
} from "./assetPageShared";
import type { AssetRow } from "./assetPageShared";

export interface AssetLedgerViewProps {
	records: AssetRow[];
	domainMap: Map<string, string>;
	ledgerIssueCount: number;
	readyCount: number;
	selectedDomainName: string;
	missingDomainCount: number;
	onOpenGovernanceRemediation: (assetId?: string) => void;
}

/** 资产台账视图：登记核验指标 + 核验列组表格（权属/密级/治理/消费出口）。 */
export function AssetLedgerView({
	records,
	domainMap,
	ledgerIssueCount,
	readyCount,
	selectedDomainName,
	missingDomainCount,
	onOpenGovernanceRemediation,
}: AssetLedgerViewProps) {
	const router = useRouter();

	return (
		<div className="asset-ledger-workbench space-y-3">
			<div className="grid gap-3 md:grid-cols-4">
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
					scroll={{ x: ASSET_TABLE_SCROLL_X }}
					tableLayout="fixed"
					className="catalog-assets-table"
					onRow={(row) => ({
						onClick: () => router.push(`/catalog/datasets/${row.id}`),
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
								{row.hiveDatabase && row.hiveTable ? `${row.hiveDatabase}.${row.hiveTable}` : row.description || row.id}
							</div>
						</div>
					),
				},
				{
					title: "类型/分层",
					width: 150,
					render: (_, row) => (
						<Space direction="vertical" size={2}>
							<Tag>{row.type || "未知"}</Tag>
							<Tag color={LAYER_META[normalizeLayer(row.warehouseLayer)].color}>{LAYER_META[normalizeLayer(row.warehouseLayer)].label}</Tag>
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
								<span className="text-xs text-slate-500">{readiness.reasons.slice(0, 2).join(" / ") || row.governanceStatus || "-"}</span>
							</Space>
						);
					},
				},
				{
					title: "密级/主题域",
					width: 180,
					render: (_, row) => (
						<Space direction="vertical" size={2}>
							<Tag color={row.classification ? "orange" : "default"}>{classificationText(row.classification)}</Tag>
							<span className="text-xs text-slate-500">{row.domain || (row.domainId ? domainMap.get(row.domainId) : undefined) || "未归域"}</span>
						</Space>
					),
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
						<Space size={[4, 4]} className="catalog-assets-actions" onClick={(event) => event.stopPropagation()}>
							<Button size="small" onClick={() => router.push(`/security/dataset-access-approval?datasetId=${row.id}`)}>
								申请权限
							</Button>
							<Button size="small" onClick={() => router.push(`/catalog/datasets/${row.id}`)}>
								详情
							</Button>
							<Button size="small" onClick={() => onOpenGovernanceRemediation(row.id)}>
								治理
							</Button>
							<Button size="small" onClick={() => router.push(`/catalog/datasets/${row.id}?tab=lineage-impact`)}>
								查看血缘
							</Button>
							<Button size="small" onClick={() => router.push(`/bi/dashboards?assetId=${row.id}`)}>
								创建报表
							</Button>
							<Button size="small" onClick={() => router.push(`/catalog/data-products?assetId=${row.id}`)}>
								生成数据产品
							</Button>
							<Button size="small" onClick={() => router.push(`/services/apis?assetId=${row.id}`)}>
								发布数据 API
							</Button>
						</Space>
					),
				},
					]}
				/>
			</div>
		</div>
	);
}
