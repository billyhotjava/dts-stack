import { Button, Card, Space, Tag } from "antd";
import type { GovernanceHealth, GovernanceImpact } from "./assetDetailPage.types";

type AssetGovernanceOverviewProps = {
	governanceHealth: GovernanceHealth | null;
	impact: GovernanceImpact;
	onNavigate: (path: string) => void;
};

export function AssetGovernanceOverview({ governanceHealth, impact, onNavigate }: AssetGovernanceOverviewProps) {
	return (
		<>
			<div className="grid gap-3 md:grid-cols-3">
				<Card size="small" title="权限授权">
					<div className="text-lg font-semibold">{impact.grantsCount}</div>
				</Card>
				<Card size="small" title="血缘节点">
					<div className="text-lg font-semibold">{impact.lineageNodeCount}</div>
				</Card>
				<Card size="small" title="血缘关系">
					<div className="text-lg font-semibold">{impact.lineageEdgeCount}</div>
				</Card>
			</div>
			<Card size="small" title="治理健康">
				<div className="mb-2 flex items-center gap-2 text-xs text-slate-600">
					<Tag
						color={
							governanceHealth?.healthLevel === "HEALTHY"
								? "green"
								: governanceHealth?.healthLevel === "WARN"
									? "gold"
									: "red"
						}
					>
						{governanceHealth?.healthLevel || "UNKNOWN"}
					</Tag>
					<span>健康分 {Number(governanceHealth?.healthScore ?? 0)}</span>
				</div>
				<div className="mb-2 text-xs text-slate-600">
					质量运行：总 {Number(governanceHealth?.quality?.totalRuns ?? 0)} / 成功{" "}
					{Number(governanceHealth?.quality?.passRuns ?? 0)} / 失败 {Number(governanceHealth?.quality?.failRuns ?? 0)}
				</div>
				<div className="mb-2 text-xs text-slate-600">
					问题工单：总 {Number(governanceHealth?.issues?.total ?? 0)} / 打开{" "}
					{Number(governanceHealth?.issues?.open ?? 0)} / 逾期 {Number(governanceHealth?.issues?.overdue ?? 0)}
				</div>
				<div className="mb-2 flex flex-wrap gap-2">
					{Array.isArray(governanceHealth?.quality?.failureTop) && governanceHealth.quality.failureTop.length > 0 ? (
						governanceHealth.quality.failureTop.map((item, index) => (
							<Tag key={`${item.category || "UNKNOWN"}-${index}`}>
								{item.category || "UNKNOWN"}: {Number(item.count || 0)}
							</Tag>
						))
					) : (
						<Tag color="green">近期开窗内无失败分类</Tag>
					)}
				</div>
				<Space size={8} wrap>
					<Button
						size="small"
						onClick={() => {
							if (governanceHealth?.links?.qualityReportPath) {
								onNavigate(governanceHealth.links.qualityReportPath);
							}
						}}
					>
						查看质量报告
					</Button>
					<Button
						size="small"
						onClick={() => {
							if (governanceHealth?.links?.qualityRulesPath) {
								onNavigate(governanceHealth.links.qualityRulesPath);
							}
						}}
					>
						查看质量运行
					</Button>
					<Button
						size="small"
						onClick={() => {
							if (governanceHealth?.links?.issuesPath) {
								onNavigate(governanceHealth.links.issuesPath);
							}
						}}
					>
						查看问题工单
					</Button>
				</Space>
			</Card>
		</>
	);
}
