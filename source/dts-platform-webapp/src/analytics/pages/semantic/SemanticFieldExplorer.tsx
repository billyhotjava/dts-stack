import { Empty, Input, Space, Tag, Tree, Typography } from "antd";
import type { DataNode } from "antd/es/tree";
import { useMemo, useState } from "react";
import type { SemanticModelMeta } from "../../api/analyticsApi";
import {
	buildSemanticFieldExplorerTree,
	collectSemanticFieldExplorerExpandedKeys,
	filterSemanticFieldExplorerTree,
	type SemanticFieldExplorerNode,
	type SemanticFieldKind,
} from "./semanticFieldExplorer.helpers";

type Props = {
	models: SemanticModelMeta[];
	baseModelId: string;
	selectedModelIds: string[];
	selectedMeasures: string[];
	selectedDimensions: string[];
	canEdit: boolean;
	onToggleMeasure: (fieldId: string) => void;
	onToggleDimension: (fieldId: string) => void;
};

type ExplorerTreeNode = DataNode & {
	explorerType?: SemanticFieldExplorerNode["type"];
	fieldKind?: SemanticFieldKind;
	fieldId?: string;
};

function securityColor(securityLevel?: string): string {
	switch (String(securityLevel ?? "").toUpperCase()) {
		case "PUBLIC":
			return "green";
		case "SENSITIVE":
			return "orange";
		case "CONFIDENTIAL":
			return "red";
		default:
			return "blue";
	}
}

function kindColor(kind?: SemanticFieldKind): string {
	return kind === "metric" ? "processing" : "geekblue";
}

function renderTitle(node: SemanticFieldExplorerNode) {
	if (node.type === "subject") {
		return (
			<Space size={8} wrap>
				<Typography.Text strong>{node.label}</Typography.Text>
				<Tag style={{ marginInlineEnd: 0 }}>{node.count ?? 0} 个字段</Tag>
				{(node.selectedCount ?? 0) > 0 && (
					<Tag color="green" style={{ marginInlineEnd: 0 }}>
						已选 {node.selectedCount}
					</Tag>
				)}
			</Space>
		);
	}

	if (node.type === "model") {
		return (
			<div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
				<Space size={6} wrap>
					<Typography.Text strong>{node.label}</Typography.Text>
					{node.isBase && (
						<Tag color="blue" style={{ marginInlineEnd: 0 }}>
							Base
						</Tag>
					)}
					<Tag color={securityColor(node.securityLevel)} style={{ marginInlineEnd: 0 }}>
						{node.securityLevel}
					</Tag>
				</Space>
				<Typography.Text type="secondary" style={{ fontSize: 12 }}>
					指标 {node.metricCount ?? 0} / 维度 {node.dimensionCount ?? 0}
				</Typography.Text>
			</div>
		);
	}

	if (node.type === "group") {
		return (
			<Space size={8} wrap>
				<Tag color={kindColor(node.fieldKind)} style={{ marginInlineEnd: 0 }}>
					{node.label}
				</Tag>
				<Typography.Text type="secondary">{node.count ?? 0} 项</Typography.Text>
				{(node.selectedCount ?? 0) > 0 && <Typography.Text type="secondary">已选 {node.selectedCount}</Typography.Text>}
			</Space>
		);
	}

	return (
		<div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
			<Space size={6} wrap>
				<Tag color={kindColor(node.fieldKind)} style={{ marginInlineEnd: 0 }}>
					{node.fieldKind === "metric" ? "指标" : "维度"}
				</Tag>
				<Typography.Text style={node.selected ? { color: "#1677ff", fontWeight: 600 } : undefined}>
					{node.label}
				</Typography.Text>
				{node.selected && (
					<Tag color="green" style={{ marginInlineEnd: 0 }}>
						已选
					</Tag>
				)}
			</Space>
			{node.description && (
				<Typography.Text type="secondary" style={{ fontSize: 12 }}>
					{node.description}
				</Typography.Text>
			)}
		</div>
	);
}

function toTreeData(nodes: SemanticFieldExplorerNode[]): ExplorerTreeNode[] {
	return nodes.map((node) => ({
		key: node.key,
		title: renderTitle(node),
		selectable: node.type === "field",
		explorerType: node.type,
		fieldKind: node.fieldKind,
		fieldId: node.fieldId,
		children: node.children ? toTreeData(node.children) : undefined,
	}));
}

export default function SemanticFieldExplorer({
	models,
	baseModelId,
	selectedModelIds,
	selectedMeasures,
	selectedDimensions,
	canEdit,
	onToggleMeasure,
	onToggleDimension,
}: Props) {
	const [keyword, setKeyword] = useState("");

	const tree = useMemo(
		() => buildSemanticFieldExplorerTree(models, selectedModelIds, baseModelId, selectedMeasures, selectedDimensions),
		[baseModelId, models, selectedDimensions, selectedMeasures, selectedModelIds],
	);
	const filteredTree = useMemo(() => filterSemanticFieldExplorerTree(tree, keyword), [keyword, tree]);
	const expandedKeys = useMemo(() => collectSemanticFieldExplorerExpandedKeys(filteredTree), [filteredTree]);
	const treeData = useMemo(() => toTreeData(filteredTree), [filteredTree]);
	const selectedKeys = useMemo(
		() => [
			...selectedMeasures.map((fieldId) => `metric:${fieldId}`),
			...selectedDimensions.map((fieldId) => `dimension:${fieldId}`),
		],
		[selectedDimensions, selectedMeasures],
	);

	if (!baseModelId) {
		return <Empty description="请选择基础模型后浏览指标和维度" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
	}

	return (
		<div className="space-y-3">
			<Space wrap>
				<Tag color="blue">{selectedModelIds.length} 个模型已载入</Tag>
				<Tag color="processing">指标 {selectedMeasures.length}</Tag>
				<Tag color="geekblue">维度 {selectedDimensions.length}</Tag>
			</Space>
			<Input.Search
				value={keyword}
				onChange={(event) => setKeyword(event.target.value)}
				placeholder="搜索模型、指标、维度"
				allowClear
			/>
			{treeData.length === 0 ? (
				<Empty description="当前模型路径下没有可选字段" image={Empty.PRESENTED_IMAGE_SIMPLE} />
			) : (
				<Tree
					blockNode
					multiple
					height={560}
					expandedKeys={expandedKeys}
					selectedKeys={selectedKeys}
					treeData={treeData}
					onSelect={(_keys, info) => {
						if (!canEdit) {
							return;
						}
						const node = info.node as ExplorerTreeNode;
						if (node.explorerType !== "field" || !node.fieldId || !node.fieldKind) {
							return;
						}
						if (node.fieldKind === "metric") {
							onToggleMeasure(node.fieldId);
							return;
						}
						onToggleDimension(node.fieldId);
					}}
				/>
			)}
		</div>
	);
}
