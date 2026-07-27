import { Alert, Button, Modal, Space, Tabs, Tag } from "antd";
import { CompactTable } from "@/components/table";
import { GOVERNANCE_STATUS_DICT, LIFECYCLE_STATUS_DICT, resolveEnumLabel } from "./assetEnumLabels";
import {
	formatTime,
	type GovernanceGapRow,
	type LineageFailureRow,
	type ResolutionFailureRow,
} from "./assetPageShared";

type AssetLedgerDialogsProps = {
	governanceGapRows: GovernanceGapRow[];
	lineageFailureRows: LineageFailureRow[];
	onCloseRemediation: () => void;
	onCloseResolutionFailures: () => void;
	onNavigate: (path: string) => void;
	onOpenGovernanceRemediation: (assetId?: string) => void;
	onRefreshGovernanceSignals: () => void;
	onSyncLineage: (assetId?: string) => void;
	remediationLoading: string | null;
	remediationOpen: boolean;
	resolutionFailures: ResolutionFailureRow[];
	resolutionFailuresLoading: boolean;
	resolutionFailuresOpen: boolean;
	signalsLoading: boolean;
};

export function AssetLedgerDialogs({
	governanceGapRows,
	lineageFailureRows,
	onCloseRemediation,
	onCloseResolutionFailures,
	onNavigate,
	onOpenGovernanceRemediation,
	onRefreshGovernanceSignals,
	onSyncLineage,
	remediationLoading,
	remediationOpen,
	resolutionFailures,
	resolutionFailuresLoading,
	resolutionFailuresOpen,
	signalsLoading,
}: AssetLedgerDialogsProps) {
	return (
		<>
			<Modal
				title="治理缺口处置工作台"
				open={remediationOpen}
				onCancel={onCloseRemediation}
				footer={
					<Space>
						<Button onClick={onRefreshGovernanceSignals} loading={signalsLoading}>
							刷新报告
						</Button>
						<Button onClick={onCloseRemediation}>关闭</Button>
					</Space>
				}
				width={1120}
			>
				<Alert
					type={governanceGapRows.length || lineageFailureRows.length ? "warning" : "success"}
					showIcon
					className="mb-3"
					message={
						governanceGapRows.length || lineageFailureRows.length
							? `当前筛选发现治理缺口 ${governanceGapRows.length} 条、血缘失败 ${lineageFailureRows.length} 条`
							: "当前筛选没有需要处置的治理缺口"
					}
					description="点击补治理字段会进入资产详情的治理扩展页；点击同步血缘会重新拉取当前资产的上下游证据并刷新报告。"
				/>
				<Tabs
					items={[
						{
							key: "governance-gaps",
							label: `治理缺口（${governanceGapRows.length}）`,
							children: (
								<CompactTable<GovernanceGapRow>
									rowKey={(row) => row.id || row.assetKey || row.fqn || row.displayName || "asset"}
									size="small"
									loading={signalsLoading}
									dataSource={governanceGapRows}
									autoEllipsis={false}
									pagination={{ defaultPageSize: 10 }}
									scroll={{ x: 980 }}
									columns={[
										{
											title: "资产",
											dataIndex: "displayName",
											width: 220,
											render: (value, row) => (
												<div>
													<div className="font-medium text-slate-900">{value || row.fqn || "-"}</div>
													<div className="truncate font-mono text-[11px] text-slate-500">
														{row.assetKey || row.fqn || "-"}
													</div>
												</div>
											),
										},
										{
											title: "严重度",
											dataIndex: "severity",
											width: 100,
											render: (value) => (
												<Tag color={value === "BLOCKING" ? "red" : value === "READY" ? "green" : "orange"}>
													{value || "-"}
												</Tag>
											),
										},
										{
											title: "阻断项",
											dataIndex: "blockingGaps",
											width: 220,
											render: (value: string[]) =>
												value?.length
													? value.map((item) => (
															<Tag color="red" key={item}>
																{item}
															</Tag>
														))
													: "-",
										},
										{
											title: "提示项",
											dataIndex: "warningGaps",
											width: 220,
											render: (value: string[]) =>
												value?.length
													? value.map((item) => (
															<Tag color="orange" key={item}>
																{item}
															</Tag>
														))
													: "-",
										},
										{
											title: "状态",
											width: 160,
											render: (_, row) => (
												<Space direction="vertical" size={2}>
													<Tag>{resolveEnumLabel(GOVERNANCE_STATUS_DICT, row.governanceStatus, "-")}</Tag>
													<span className="text-xs text-slate-500">
														{resolveEnumLabel(LIFECYCLE_STATUS_DICT, row.lifecycleStatus, "-")}
													</span>
												</Space>
											),
										},
										{
											title: "操作",
											width: 180,
											fixed: "right",
											render: (_, row) => (
												<Space>
													<Button size="small" onClick={() => onOpenGovernanceRemediation(row.id)}>
														补治理字段
													</Button>
													<Button size="small" onClick={() => onNavigate(`/catalog/datasets/${row.id}`)}>
														详情
													</Button>
												</Space>
											),
										},
									]}
								/>
							),
						},
						{
							key: "lineage-failures",
							label: `血缘失败（${lineageFailureRows.length}）`,
							children: (
								<CompactTable<LineageFailureRow>
									rowKey={(row) => row.id || row.assetKey || row.fqn || row.displayName || "asset"}
									size="small"
									loading={signalsLoading}
									dataSource={lineageFailureRows}
									autoEllipsis={false}
									pagination={{ defaultPageSize: 10 }}
									scroll={{ x: 1020 }}
									columns={[
										{
											title: "资产",
											dataIndex: "displayName",
											width: 220,
											render: (value, row) => (
												<div>
													<div className="font-medium text-slate-900">{value || row.fqn || "-"}</div>
													<div className="truncate font-mono text-[11px] text-slate-500">
														{row.assetKey || row.fqn || "-"}
													</div>
												</div>
											),
										},
										{
											title: "严重度",
											dataIndex: "severity",
											width: 100,
											render: (value, row) => <Tag color={row.blocking ? "red" : "orange"}>{value || "-"}</Tag>,
										},
										{
											title: "原因",
											dataIndex: "reason",
											width: 220,
											render: (value) => (value ? <Tag color="orange">{value}</Tag> : "-"),
										},
										{
											title: "下一步",
											dataIndex: "nextAction",
											width: 220,
											render: (value) => value || "同步血缘或补齐治理字段",
										},
										{
											title: "证据源",
											dataIndex: "evidenceSource",
											width: 160,
											render: (value) => value || "-",
										},
										{
											title: "操作",
											width: 220,
											fixed: "right",
											render: (_, row) => (
												<Space>
													<Button
														size="small"
														type="primary"
														loading={remediationLoading === row.id}
														onClick={() => onSyncLineage(row.id)}
													>
														同步血缘
													</Button>
													<Button size="small" onClick={() => onOpenGovernanceRemediation(row.id)}>
														治理
													</Button>
													<Button size="small" onClick={() => onNavigate(`/catalog/datasets/${row.id}?tab=lineage`)}>
														详情
													</Button>
												</Space>
											),
										},
									]}
								/>
							),
						},
					]}
				/>
			</Modal>
			<Modal
				title="资产身份解析失败"
				open={resolutionFailuresOpen}
				onCancel={onCloseResolutionFailures}
				footer={<Button onClick={onCloseResolutionFailures}>关闭</Button>}
				width={920}
			>
				<Alert
					type={resolutionFailures.length ? "warning" : "success"}
					showIcon
					className="mb-3"
					message={
						resolutionFailures.length ? `最近发现 ${resolutionFailures.length} 条解析失败` : "最近没有资产身份解析失败"
					}
					description="请核对 ref 命名、资产类型映射和历史兼容配置。"
				/>
				<CompactTable<ResolutionFailureRow>
					rowKey={(row) => row.id || `${row.ref || "ref"}-${row.requestedAt || "time"}`}
					size="small"
					loading={resolutionFailuresLoading}
					dataSource={resolutionFailures}
					autoEllipsis={false}
					pagination={{ defaultPageSize: 10 }}
					scroll={{ x: 900 }}
					columns={[
						{
							title: "引用",
							dataIndex: "ref",
							width: 280,
							render: (value) => <span className="font-mono text-xs">{value || "-"}</span>,
						},
						{
							title: "类型猜测",
							dataIndex: "typeHintGuess",
							width: 120,
							render: (value) => (value ? <Tag>{value}</Tag> : "-"),
						},
						{
							title: "原因",
							dataIndex: "reason",
							width: 180,
							render: (value) => (value ? <Tag color="orange">{value}</Tag> : "-"),
						},
						{
							title: "调用方",
							dataIndex: "caller",
							width: 150,
							render: (value) => value || "-",
						},
						{
							title: "发生时间",
							dataIndex: "requestedAt",
							width: 180,
							render: (value) => formatTime(value),
						},
					]}
				/>
			</Modal>
		</>
	);
}
