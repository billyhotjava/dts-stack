import { useState, useCallback } from "react";
import type { ProjectCockpitTreeNode } from "../../../api/analyticsApi";
import { Tag } from "antd";

type Props = {
	tree: ProjectCockpitTreeNode[];
	selectedId: string;
	onSelect: (nodeId: string) => void;
};

function TreeNode({
	node,
	selectedId,
	onSelect,
	expandedIds,
	onToggle,
}: {
	node: ProjectCockpitTreeNode;
	selectedId: string;
	onSelect: (nodeId: string) => void;
	expandedIds: Set<string>;
	onToggle: (nodeId: string) => void;
}) {
	const nodeId = String(node.id ?? "");
	const children = node.children ?? [];
	const hasChildren = children.length > 0;
	const isExpanded = expandedIds.has(nodeId);

	return (
		<li className="project-cockpit__tree-item">
			<div className={`project-cockpit__tree-node ${selectedId === nodeId ? "project-cockpit__tree-node--selected" : ""}`}>
				{hasChildren ? (
					<button
						type="button"
						className="project-cockpit__tree-toggle"
						onClick={(e) => {
							e.stopPropagation();
							onToggle(nodeId);
						}}
						aria-label={isExpanded ? "折叠" : "展开"}
					>
						<span className={`project-cockpit__tree-arrow ${isExpanded ? "project-cockpit__tree-arrow--open" : ""}`}>
							&#9654;
						</span>
					</button>
				) : (
					<span className="project-cockpit__tree-toggle-placeholder" />
				)}
				<button
					type="button"
					className="project-cockpit__tree-node-body"
					onClick={() => onSelect(nodeId)}
				>
					<div className="project-cockpit__tree-node-main">
						<div className="project-cockpit__tree-node-title">
							<span>{node.name}</span>
							<Tag
								color={
									node.riskLevel === "高"
										? "error"
										: node.riskLevel === "中"
											? "warning"
											: undefined
								}
							>
								{node.riskLevel || "未知"}
							</Tag>
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
			</div>
			{hasChildren && isExpanded ? (
				<ul className="project-cockpit__tree-children">
					{children.map((child) => (
						<TreeNode
							key={String(child.id ?? "")}
							node={child}
							selectedId={selectedId}
							onSelect={onSelect}
							expandedIds={expandedIds}
							onToggle={onToggle}
						/>
					))}
				</ul>
			) : null}
		</li>
	);
}

export function ProjectTreeProgressBoard({ tree, selectedId, onSelect }: Props) {
	// 默认展开选中节点所在的 major project
	const [expandedIds, setExpandedIds] = useState<Set<string>>(() => {
		const initial = new Set<string>();
		for (const node of tree) {
			const nodeId = String(node.id ?? "");
			if (nodeId === selectedId) {
				initial.add(nodeId);
				break;
			}
			const children = node.children ?? [];
			const hasSelected = children.some((child) => {
				if (String(child.id ?? "") === selectedId) return true;
				return (child.children ?? []).some((gc) => String(gc.id ?? "") === selectedId);
			});
			if (hasSelected) {
				initial.add(nodeId);
				break;
			}
		}
		if (initial.size === 0 && tree.length > 0) {
			initial.add(String(tree[0].id ?? ""));
		}
		return initial;
	});

	const onToggle = useCallback((nodeId: string) => {
		setExpandedIds((prev) => {
			const next = new Set(prev);
			if (next.has(nodeId)) {
				next.delete(nodeId);
			} else {
				next.add(nodeId);
			}
			return next;
		});
	}, []);

	if (tree.length === 0) {
		return <div className="project-cockpit__empty-block">当前筛选范围暂无项目树数据。</div>;
	}
	return (
		<ul className="project-cockpit__tree-root">
			{tree.map((node) => (
				<TreeNode
					key={String(node.id ?? "")}
					node={node}
					selectedId={selectedId}
					onSelect={onSelect}
					expandedIds={expandedIds}
					onToggle={onToggle}
				/>
			))}
		</ul>
	);
}
