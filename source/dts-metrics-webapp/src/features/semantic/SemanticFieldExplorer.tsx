import { useMemo, useState } from "react";
import type { DragEvent } from "react";
import {
	buildSemanticFieldExplorerTree,
	collectSemanticFieldExplorerExpandedKeys,
	filterSemanticFieldExplorerTree,
	type SemanticFieldExplorerNode,
} from "./semanticFieldExplorer.helpers";
import type { SemanticModelMeta } from "./semanticTypes";

const FIELD_DRAG_MIME = "application/vnd.dts-metrics-field";

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

function SemanticFieldNode({
	node,
	canEdit,
	onToggleMeasure,
	onToggleDimension,
}: {
	node: SemanticFieldExplorerNode;
	canEdit: boolean;
	onToggleMeasure: (fieldId: string) => void;
	onToggleDimension: (fieldId: string) => void;
}) {
	if (node.type === "field") {
		const draggable = canEdit && Boolean(node.fieldId && node.fieldKind);
		const handleDragStart = (event: DragEvent<HTMLButtonElement>) => {
			if (!node.fieldId || !node.fieldKind) return;
			event.dataTransfer.setData(
				FIELD_DRAG_MIME,
				JSON.stringify({
					fieldId: node.fieldId,
					fieldKind: node.fieldKind,
					name: node.fieldId,
					label: node.label,
					role: node.fieldKind,
					tableName: node.modelId ?? "",
					modelId: node.modelId,
				}),
			);
			event.dataTransfer.effectAllowed = "copy";
		};

		return (
			<button
				type="button"
				className={`semantic-field-row ${node.selected ? "selected" : ""}`}
				data-field-id={node.fieldId}
				data-field-kind={node.fieldKind}
				disabled={!canEdit || !node.fieldId}
				draggable={draggable}
				onClick={() => {
					if (!node.fieldId) return;
					if (node.fieldKind === "metric") onToggleMeasure(node.fieldId);
					else onToggleDimension(node.fieldId);
				}}
				onDragStart={handleDragStart}
			>
				<span className={`field-kind ${node.fieldKind}`}>{node.fieldKind === "metric" ? "指标" : "维度"}</span>
				<span>
					<strong>{node.label}</strong>
					{node.description ? <em>{node.description}</em> : null}
				</span>
				{node.selected ? <b>已选</b> : null}
			</button>
		);
	}

	return (
		<details className={`semantic-tree-node ${node.type}`} open>
			<summary>
				<strong>{node.label}</strong>
				<span>{node.count ?? 0} 项</span>
				{node.selectedCount ? <em>已选 {node.selectedCount}</em> : null}
				{node.isBase ? <b>Base</b> : null}
			</summary>
			<div className="semantic-tree-children">
				{node.children?.map((child) => (
					<SemanticFieldNode
						canEdit={canEdit}
						key={child.key}
						node={child}
						onToggleDimension={onToggleDimension}
						onToggleMeasure={onToggleMeasure}
					/>
				))}
			</div>
		</details>
	);
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

	if (!baseModelId) {
		return <div className="semantic-empty">请选择基础模型后浏览指标和维度</div>;
	}

	return (
		<div className="semantic-field-explorer">
			<div className="semantic-field-stats">
				<span>{selectedModelIds.length} 个模型</span>
				<span>指标 {selectedMeasures.length}</span>
				<span>维度 {selectedDimensions.length}</span>
				<span>展开 {expandedKeys.length}</span>
			</div>
			<input
				className="semantic-input"
				value={keyword}
				onChange={(event) => setKeyword(event.target.value)}
				placeholder="搜索模型、指标、维度"
			/>
			{filteredTree.length === 0 ? (
				<div className="semantic-empty">当前模型路径下没有可选字段</div>
			) : (
				<div className="semantic-tree">
					{filteredTree.map((node) => (
						<SemanticFieldNode
							canEdit={canEdit}
							key={node.key}
							node={node}
							onToggleDimension={onToggleDimension}
							onToggleMeasure={onToggleMeasure}
						/>
					))}
				</div>
			)}
		</div>
	);
}
