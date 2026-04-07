import type { ProjectCockpitTreeNode } from "../../../api/analyticsApi";
import { Card, Tag } from "antd";
import type { BreadcrumbItem } from "../views/majorProjectTreeView.helpers";

type Props = {
	node: ProjectCockpitTreeNode | null;
	breadcrumb?: BreadcrumbItem[];
	onBreadcrumbClick?: (id: string) => void;
};

export function ProjectTreeDetailPanel({ node, breadcrumb, onBreadcrumbClick }: Props) {
	const cardTitle = (
		<span>
			{node?.name ?? "请选择节点"}
			<span style={{ fontSize: "0.85em", fontWeight: "normal", color: "var(--color-text-secondary)", marginLeft: 8 }}>
				{node ? `${node.level ?? "node"} 详情` : "树状看板会显示当前选中节点的细节"}
			</span>
		</span>
	);
	const cardExtra = node?.riskLevel ? (
		<Tag color={node.riskLevel === "高" ? "error" : node.riskLevel === "中" ? "warning" : undefined}>{node.riskLevel}</Tag>
	) : null;
	return (
		<Card className="project-cockpit__detail-card" title={cardTitle} extra={cardExtra}>
			{breadcrumb && breadcrumb.length > 0 && (
				<div className="project-cockpit__breadcrumb">
					{breadcrumb.map((item, idx) => {
						const isLast = idx === breadcrumb.length - 1;
						return (
							<span key={item.id}>
								{idx > 0 && <span className="project-cockpit__breadcrumb-sep">/</span>}
								{isLast ? (
									<span className="project-cockpit__breadcrumb-current">{item.name}</span>
								) : (
									<span
										className="project-cockpit__breadcrumb-link"
										onClick={() => onBreadcrumbClick?.(item.id)}
									>
										{item.name}
									</span>
								)}
							</span>
						);
					})}
				</div>
			)}
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
		</Card>
	);
}
