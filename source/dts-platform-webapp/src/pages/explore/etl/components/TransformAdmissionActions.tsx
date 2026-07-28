import { Button, Dropdown, Space, Tag, Tooltip } from "antd";
import type { IngestionTaskDTO } from "@/api/ingestion";
import { CLASSIFICATION_LABELS_ZH, normalizeClassification } from "@/utils/classification";
import { resolveTaskAdmissionState } from "../fileClassificationAdmission.helpers";

type TransformAdmissionActionsProps = {
	task: IngestionTaskDTO;
	admitSubmitting: boolean;
	executeSubmitting: boolean;
	executeProgressOpen: boolean;
	executeProgressTerminal: boolean;
	onBack: () => void;
	onHistory: () => void;
	onOpenLog: () => void;
	onEdit: () => void;
	onRebuildDag: () => void;
	onRollback: (level: number) => void;
	onAdmit: () => void;
	onExecute: () => void;
};

export default function TransformAdmissionActions({
	task,
	admitSubmitting,
	executeSubmitting,
	executeProgressOpen,
	executeProgressTerminal,
	onBack,
	onHistory,
	onOpenLog,
	onEdit,
	onRebuildDag,
	onRollback,
	onAdmit,
	onExecute,
}: TransformAdmissionActionsProps) {
	const admission = resolveTaskAdmissionState(task);
	const taskDeleted = task.status === "deleted";
	const executionInProgress =
		executeSubmitting ||
		(executeProgressOpen && !executeProgressTerminal) ||
		["preparing", "running"].includes((task.lastExecutionStatus || "").toLowerCase());
	const classification = normalizeClassification(admission.classification, undefined);
	const showAdmissionAction = String(task.status || "").toLowerCase() === "draft";

	return (
		<Space wrap>
			<Button onClick={onBack}>返回</Button>
			<Button onClick={onHistory}>执行历史</Button>
			<Button onClick={onOpenLog} disabled={!task.lastExecutedAt} data-testid="platform-transform-open-log">
				最新日志
			</Button>
			<Button onClick={onEdit} disabled={taskDeleted}>
				编辑
			</Button>
			<Tooltip title={admission.canExecute ? undefined : admission.reason}>
				<Button onClick={onRebuildDag} disabled={taskDeleted || !admission.canExecute || task.airflowEnabled === false}>
					重建 DAG
				</Button>
			</Tooltip>
			<Dropdown
				menu={{
					items: [
						{ key: "1", label: "Level 1 — 清空数据", onClick: () => onRollback(1) },
						{ key: "2", label: "Level 2 — 重建表结构", onClick: () => onRollback(2) },
						{ key: "3", label: "Level 3 — 全链路回退", onClick: () => onRollback(3), danger: true },
					],
				}}
				disabled={taskDeleted}
			>
				<Button danger>数据回退</Button>
			</Dropdown>
			{classification ? (
				<Tag color={admission.canExecute ? "green" : "gold"}>密级：{CLASSIFICATION_LABELS_ZH[classification]}</Tag>
			) : null}
			{showAdmissionAction ? (
				<Tooltip title={admission.canAdmit ? undefined : admission.reason}>
					<Button
						type="primary"
						onClick={onAdmit}
						loading={admitSubmitting}
						disabled={!admission.canAdmit || admitSubmitting}
						data-testid="platform-transform-admit"
					>
						完成密级与准入
					</Button>
				</Tooltip>
			) : null}
			<Tooltip title={admission.canExecute ? undefined : admission.reason}>
				<Button
					type={showAdmissionAction ? "default" : "primary"}
					onClick={onExecute}
					loading={executionInProgress}
					disabled={taskDeleted || !admission.canExecute || executionInProgress}
					data-testid="platform-transform-execute"
				>
					{executeSubmitting
						? "提交中..."
						: (task.lastExecutionStatus || "").toLowerCase() === "preparing"
							? "准备中"
							: executeProgressOpen && !executeProgressTerminal
								? "执行中"
								: "执行任务"}
				</Button>
			</Tooltip>
		</Space>
	);
}
