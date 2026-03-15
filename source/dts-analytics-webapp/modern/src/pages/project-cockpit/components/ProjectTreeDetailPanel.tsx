import type { ProjectCockpitTreeNode } from "../../../api/analyticsApi";
import { Badge } from "../../../ui/Badge/Badge";
import { Card, CardBody, CardHeader } from "../../../ui/Card/Card";

type Props = {
	node: ProjectCockpitTreeNode | null;
};

export function ProjectTreeDetailPanel({ node }: Props) {
	return (
		<Card className="project-cockpit__detail-card">
			<CardHeader
				title={node?.name ?? "请选择节点"}
				subtitle={node ? `${node.level ?? "node"} 详情` : "树状看板会显示当前选中节点的细节"}
				action={node?.riskLevel ? <Badge variant={node.riskLevel === "高" ? "error" : node.riskLevel === "中" ? "warning" : "default"}>{node.riskLevel}</Badge> : null}
			/>
			<CardBody>
				{node ? (
					<div className="project-cockpit__detail-grid">
						<div>
							<span className="project-cockpit__meta-label">状态</span>
							<strong>{String(node.status ?? "--")}</strong>
						</div>
						<div>
							<span className="project-cockpit__meta-label">进度</span>
							<strong>{Number(node.progressRate ?? 0)}%</strong>
						</div>
						<div>
							<span className="project-cockpit__meta-label">责任科室</span>
							<strong>{String(node.ownerDept ?? "--")}</strong>
						</div>
						<div>
							<span className="project-cockpit__meta-label">责任人</span>
							<strong>{String(node.ownerUser ?? "--")}</strong>
						</div>
						<div>
							<span className="project-cockpit__meta-label">计划日期</span>
							<strong>{String(node.planDate ?? "--")}</strong>
						</div>
						<div>
							<span className="project-cockpit__meta-label">实际日期</span>
							<strong>{String(node.actualDate ?? "--")}</strong>
						</div>
						<div>
							<span className="project-cockpit__meta-label">延期天数</span>
							<strong>{Number(node.delayDays ?? 0)} 天</strong>
						</div>
						<div>
							<span className="project-cockpit__meta-label">高风险计数</span>
							<strong>{Number(node.highRiskCount ?? 0)}</strong>
						</div>
						<div className="project-cockpit__detail-wide">
							<span className="project-cockpit__meta-label">原因说明</span>
							<p>{String(node.reason ?? "当前选中层级暂无补充说明。")}</p>
						</div>
					</div>
				) : (
					<div className="project-cockpit__empty-block">点击左侧项目树节点后，这里会展示详细说明。</div>
				)}
			</CardBody>
		</Card>
	);
}
