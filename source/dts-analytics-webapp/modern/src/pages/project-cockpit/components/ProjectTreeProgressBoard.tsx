import type { ProjectCockpitTreeNode } from "../../../api/analyticsApi";
import { Badge } from "../../../ui/Badge/Badge";

type Props = {
	tree: ProjectCockpitTreeNode[];
	selectedId: string;
	onSelect: (nodeId: string) => void;
};

function renderNode(
	node: ProjectCockpitTreeNode,
	selectedId: string,
	onSelect: (nodeId: string) => void,
) {
	const nodeId = String(node.id ?? "");
	const children = node.children ?? [];
	return (
		<li key={nodeId} className="project-cockpit__tree-item">
			<button
				type="button"
				className={`project-cockpit__tree-node ${selectedId === nodeId ? "project-cockpit__tree-node--selected" : ""}`}
				onClick={() => onSelect(nodeId)}
			>
				<div className="project-cockpit__tree-node-main">
					<div className="project-cockpit__tree-node-title">
						<span>{node.name}</span>
						<Badge
							size="sm"
							variant={
								node.riskLevel === "高"
									? "error"
									: node.riskLevel === "中"
										? "warning"
										: "default"
							}
						>
							{node.riskLevel || "未知"}
						</Badge>
					</div>
					<div className="project-cockpit__tree-node-meta">
						<span>{node.status || "推进中"}</span>
						<span>延期 {node.delayDays ?? 0} 天</span>
						<span>高风险 {node.highRiskCount ?? 0}</span>
					</div>
				</div>
				<div className="project-cockpit__tree-progress">
					<div
						className="project-cockpit__tree-progress-fill"
						style={{ width: `${Math.min(Number(node.progressRate ?? 0), 100)}%` }}
					/>
				</div>
			</button>
			{children.length > 0 ? (
				<ul className="project-cockpit__tree-children">
					{children.map((child) => renderNode(child, selectedId, onSelect))}
				</ul>
			) : null}
		</li>
	);
}

export function ProjectTreeProgressBoard({ tree, selectedId, onSelect }: Props) {
	if (tree.length === 0) {
		return <div className="project-cockpit__empty-block">当前筛选范围暂无项目树数据。</div>;
	}
	return <ul className="project-cockpit__tree-root">{tree.map((node) => renderNode(node, selectedId, onSelect))}</ul>;
}
