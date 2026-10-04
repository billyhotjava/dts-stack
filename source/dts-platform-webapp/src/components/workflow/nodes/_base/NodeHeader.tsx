import type { BlockDef } from "../../block-selector";
import { NodeStatusBadge } from "./NodeStatusBadge";
import type { WorkflowNodeStatus } from "./types";

const CLASSIFICATION_LABEL: Record<string, string> = {
	public: "公开",
	internal: "内部",
	secret: "秘密",
	topSecret: "绝密",
};

export function NodeHeader({
	block,
	title,
	status,
	classification,
}: {
	block: BlockDef;
	title?: string;
	status?: WorkflowNodeStatus;
	classification?: string;
}) {
	const classificationLabel = classification ? (CLASSIFICATION_LABEL[classification] ?? classification) : null;

	return (
		<header className="wf-node-header">
			<span className="wf-node-icon" style={{ color: block.color }} aria-hidden="true">
				{block.icon}
			</span>
			<span className="wf-node-title">{title || block.label}</span>
			<NodeStatusBadge status={status} />
			{classificationLabel ? <span className="wf-node-classification">{classificationLabel}</span> : null}
		</header>
	);
}
