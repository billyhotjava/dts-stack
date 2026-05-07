import type { WorkflowNodeStatus } from "./types";

const STATUS_LABEL: Record<WorkflowNodeStatus, string> = {
	idle: "待运行",
	running: "运行中",
	success: "成功",
	error: "异常",
};

export function NodeStatusBadge({ status = "idle" }: { status?: WorkflowNodeStatus }) {
	return (
		<span className="wf-node-status" data-status={status}>
			{STATUS_LABEL[status]}
		</span>
	);
}
