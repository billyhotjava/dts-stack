import { Alert, Button, Descriptions, Drawer, Popconfirm, Space } from "antd";
import { useNavigate } from "react-router";
import { actionColumn, CompactTable } from "@/components/table";
import { formatTime } from "@/utils/textUtils";
import { QualityStatus } from "./QualityShared";
import { qualityPath } from "./qualityRoutes";
import type { QualityRun, QualityWorkflowRun } from "./qualityTypes";

export const workflowTriggerLabel = (triggerType?: string) => {
	const normalized = String(triggerType || "").toUpperCase();
	if (normalized === "SCHEDULED") return "周期执行";
	if (normalized === "RETRY") return "重新验证";
	if (normalized === "MODEL_BUILD" || normalized === "MODEL_RELEASE") return "模型交付验证";
	if (normalized === "INGESTION") return "数据接入后验证";
	return "人工验证";
};

export const isRetryableWorkflow = (status?: string) =>
	["FAILED", "BLOCKED"].includes(String(status || "").toUpperCase());

export const isActiveWorkflow = (status?: string) => ["QUEUED", "RUNNING"].includes(String(status || "").toUpperCase());

export const hasRetryCapacity = (workflow: QualityWorkflowRun) =>
	Number(workflow.attemptNo || 1) <= Number(workflow.maxRetryAttempts ?? 1);

export function QualityWorkflowEvidenceDrawer({
	workflow,
	loading,
	canManage,
	retrying,
	cancelling,
	ruleNames,
	onClose,
	onRetry,
	onCancel,
}: {
	workflow?: QualityWorkflowRun;
	loading?: boolean;
	canManage: boolean;
	retrying?: boolean;
	cancelling?: boolean;
	ruleNames?: ReadonlyMap<string, string>;
	onClose: () => void;
	onRetry: (workflow: QualityWorkflowRun) => void;
	onCancel?: (workflow: QualityWorkflowRun) => void;
}) {
	const navigate = useNavigate();
	const canRetry = Boolean(workflow && isRetryableWorkflow(workflow.status) && hasRetryCapacity(workflow));
	const canCancel = Boolean(workflow && isActiveWorkflow(workflow.status));

	return (
		<Drawer
			open={Boolean(workflow)}
			title="质量验证详情"
			width="min(760px, 100vw)"
			loading={loading}
			onClose={onClose}
			extra={
				workflow ? (
					<Space>
						{canCancel && onCancel ? (
							<Popconfirm
								title="确认取消本次质量验证？"
								description="已生成的规则运行记录会保留。"
								onConfirm={() => onCancel(workflow)}
							>
								<Button danger disabled={!canManage} loading={cancelling}>
									取消验证
								</Button>
							</Popconfirm>
						) : null}
						{canRetry ? (
							<Button disabled={!canManage} loading={retrying} onClick={() => onRetry(workflow)}>
								重新验证
							</Button>
						) : null}
					</Space>
				) : null
			}
		>
			{workflow ? (
				<Space direction="vertical" size={16} style={{ width: "100%" }}>
					{workflow.message && !["PASSED", "RUNNING", "QUEUED"].includes(String(workflow.status)) ? (
						<Alert showIcon type="warning" message={workflow.message} />
					) : null}
					<Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
						<Descriptions.Item label="验证状态">
							<QualityStatus status={workflow.status} />
						</Descriptions.Item>
						<Descriptions.Item label="触发方式">{workflowTriggerLabel(workflow.triggerType)}</Descriptions.Item>
						<Descriptions.Item label="验证次数">第 {workflow.attemptNo || 1} 次</Descriptions.Item>
						<Descriptions.Item label="重试上限">最多 {workflow.maxRetryAttempts ?? 1} 次重试</Descriptions.Item>
						<Descriptions.Item label="规则进度">
							{workflow.completedRunCount || 0}/{workflow.expectedRunCount || 0}
						</Descriptions.Item>
						<Descriptions.Item label="通过规则">{workflow.passedCount || 0}</Descriptions.Item>
						<Descriptions.Item label="未通过规则">{workflow.failedCount || 0}</Descriptions.Item>
						<Descriptions.Item label="开始时间">{formatTime(workflow.startedAt)}</Descriptions.Item>
						<Descriptions.Item label="完成时间">{formatTime(workflow.finishedAt)}</Descriptions.Item>
					</Descriptions>
					<CompactTable
						rowKey="id"
						dataSource={workflow.ruleRuns || []}
						pagination={false}
						columns={[
							{
								title: "规则",
								key: "rule",
								render: (_value: unknown, row: QualityRun) =>
									ruleNames?.get(String(row.ruleId || "")) || row.ruleId || "未命名规则",
							},
							{
								title: "状态",
								dataIndex: "status",
								width: 110,
								render: (value: unknown) => <QualityStatus status={String(value || "")} />,
							},
							{ title: "开始时间", dataIndex: "startedAt", width: 180, render: formatTime },
							actionColumn<QualityRun>(
								(row) => [
									{
										key: "view",
										label: "查看规则结果",
										onClick: () => navigate(qualityPath("run-detail", { runId: row.id })),
									},
								],
								{ width: 140, fixed: false },
							),
						]}
					/>
				</Space>
			) : null}
		</Drawer>
	);
}
