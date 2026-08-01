import { Alert, Button, Card, Descriptions, Input, Modal, Space, Table, Tag } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { appendIssueAction, closeIssue, createIssue, getIssue, listIssues, updateIssue } from "@/api/platformApi";
import { useUserInfo } from "@/store/userStore";
import { formatTime } from "@/utils/textUtils";
import { QualityStatus } from "./QualityShared";
import { collectCompletePages } from "./qualityTypes";
import {
	buildIssueUpdatePayload,
	findRunIssue,
	type RunIssue,
	type RunIssueAction,
	runNeedsDisposition,
} from "./runIssueDisposition";
import { useQualityMaintainerAccess } from "./useQualityAccess";

const nextStatus = (status?: string) => {
	const normalized = String(status || "OPEN").toUpperCase();
	if (normalized === "OPEN") return "IN_PROGRESS";
	if (normalized === "IN_PROGRESS") return "RESOLVED";
	return undefined;
};

const INCOMPLETE_ISSUE_LOOKUP = "问题单接口未提供全量分页总数，无法确认该运行是否已有问题单";

export function RunIssueDisposition({
	runId,
	datasetId,
	runStatus,
}: {
	runId: string;
	datasetId?: string;
	runStatus?: string;
}) {
	const canManage = useQualityMaintainerAccess();
	const userInfo = useUserInfo();
	const [issue, setIssue] = useState<RunIssue>();
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState("");
	const [creationBlockedReason, setCreationBlockedReason] = useState("");
	const [saving, setSaving] = useState(false);
	const [noteOpen, setNoteOpen] = useState(false);
	const [resolutionOpen, setResolutionOpen] = useState(false);
	const [note, setNote] = useState("");
	const [resolution, setResolution] = useState("");
	const loadSequence = useRef(0);
	const activeRunId = useRef(runId);
	activeRunId.current = runId;

	const lookupRunIssue = useCallback(async () => {
		const result = await collectCompletePages<RunIssue>(
			(page, size) => listIssues({ sourceType: "QUALITY_RUN", datasetId, page, size, limit: size }),
			200,
		);
		const matched = findRunIssue(result.rows, runId);
		if (!matched?.id) return { issue: matched, complete: result.complete };
		const detail = (await getIssue(matched.id)) as RunIssue;
		if (!findRunIssue([detail], runId)) throw new Error("问题单详情与当前质量运行不匹配");
		return { issue: detail, complete: result.complete };
	}, [datasetId, runId]);

	const load = useCallback(async () => {
		const requestedRunId = runId;
		const sequence = ++loadSequence.current;
		setLoading(true);
		setLoadError("");
		setCreationBlockedReason("");
		setIssue(undefined);
		try {
			const result = await lookupRunIssue();
			if (sequence !== loadSequence.current || activeRunId.current !== requestedRunId) return;
			setIssue(result.issue);
			if (!result.issue && !result.complete) setCreationBlockedReason(INCOMPLETE_ISSUE_LOOKUP);
		} catch (error) {
			if (sequence !== loadSequence.current || activeRunId.current !== requestedRunId) return;
			const message = error instanceof Error ? error.message : "运行问题处置加载失败";
			setLoadError(message);
			toast.error(message);
		} finally {
			if (sequence === loadSequence.current && activeRunId.current === requestedRunId) setLoading(false);
		}
	}, [lookupRunIssue, runId]);

	useEffect(() => {
		void load();
	}, [load]);
	const currentIssueMatches = Boolean(issue && findRunIssue([issue], runId));

	const mutate = async (operation: () => Promise<unknown>, success: string) => {
		const operationRunId = runId;
		setSaving(true);
		try {
			await operation();
			toast.success(success);
			if (activeRunId.current === operationRunId) await load();
			return true;
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "问题处置失败");
			return false;
		} finally {
			setSaving(false);
		}
	};

	const createForRun = () => {
		if (creationBlockedReason || loadError || loading || saving) return;
		setSaving(true);
		Modal.confirm({
			title: "确认创建该质量运行的问题单？",
			content: "确认后将再次执行全量去重查询，仅在确认不存在关联问题单时创建。",
			okText: "确认创建",
			onOk: async () => {
				try {
					const verification = await lookupRunIssue();
					if (verification.issue) {
						setIssue(verification.issue);
						toast.warning("该质量运行已存在问题单，已取消重复创建");
						return;
					}
					if (!verification.complete) {
						setCreationBlockedReason(INCOMPLETE_ISSUE_LOOKUP);
						toast.error(INCOMPLETE_ISSUE_LOOKUP);
						return;
					}
					await createIssue({
						sourceType: "QUALITY_RUN",
						sourceId: runId,
						datasetId,
						title: `质量运行异常：${runId}`,
						summary: "由质量运行详情发起，请认领并记录处置过程。",
						status: "OPEN",
						severity: "HIGH",
						priority: "HIGH",
						dataLevel: "DATA_INTERNAL",
						tags: ["QUALITY_RUN"],
					});
					toast.success("问题单已创建");
					if (activeRunId.current === runId) await load();
				} catch (error) {
					toast.error(error instanceof Error ? error.message : "问题单创建失败");
				} finally {
					setSaving(false);
				}
			},
			onCancel: () => setSaving(false),
		});
	};

	const claim = () => {
		if (!issue || !currentIssueMatches) return;
		const assignee = userInfo.username || userInfo.email;
		if (!assignee) {
			toast.error("当前账号缺少可用于认领的用户名");
			return;
		}
		void mutate(
			() => updateIssue(issue.id, buildIssueUpdatePayload(issue, { assignedTo: assignee, status: "IN_PROGRESS" })),
			"问题已认领",
		);
	};

	const resolveIssue = () => {
		if (!issue || !currentIssueMatches || !resolution.trim()) return;
		void mutate(
			() =>
				updateIssue(issue.id, buildIssueUpdatePayload(issue, { status: "RESOLVED", resolution: resolution.trim() })),
			"问题已标记解决",
		).then((succeeded) => {
			if (succeeded) {
				setResolutionOpen(false);
				setResolution("");
			}
		});
	};

	const closeResolvedIssue = () => {
		if (!issue || !currentIssueMatches || !resolution.trim()) return;
		void mutate(() => closeIssue(issue.id, resolution.trim()), "问题已关闭").then((succeeded) => {
			if (succeeded) {
				setResolutionOpen(false);
				setResolution("");
			}
		});
	};

	const appendNote = () => {
		if (!issue || !currentIssueMatches || !note.trim()) return;
		void mutate(
			() => appendIssueAction(issue.id, { actionType: "COMMENT", notes: note.trim(), attachments: [] }),
			"处理记录已追加",
		).then((succeeded) => {
			if (succeeded) {
				setNoteOpen(false);
				setNote("");
			}
		});
	};

	const status = String(issue?.status || "").toUpperCase();
	const normalizedRunStatus = String(runStatus || "").toUpperCase();
	const transition = nextStatus(status);
	const actionColumns = [
		{ title: "时间", dataIndex: "createdDate", width: 180, render: formatTime },
		{ title: "处理人", dataIndex: "actor", width: 130, render: (value: unknown) => String(value || "-") },
		{
			title: "动作",
			dataIndex: "actionType",
			width: 140,
			render: (value: unknown) => <Tag>{String(value || "-")}</Tag>,
		},
		{ title: "处理记录", dataIndex: "notes", render: (value: unknown) => String(value || "-") },
	];

	return (
		<Card title="问题处置进度" loading={loading} extra={<Button onClick={() => void load()}>刷新处置</Button>}>
			{loadError ? (
				<Alert
					showIcon
					type="error"
					message="问题处置状态加载失败"
					description={`${loadError}。为避免重复创建问题单，查询成功前已关闭处置操作。`}
					action={<Button onClick={() => void load()}>重试</Button>}
				/>
			) : !issue ? (
				runNeedsDisposition(runStatus) ? (
					<Alert
						showIcon
						type="warning"
						message={creationBlockedReason ? "无法安全创建问题单" : "该异常运行尚未建立问题单"}
						description={
							creationBlockedReason
								? `${creationBlockedReason}。为避免重复问题单，后端补充精确查询或 total 分页合同前保持禁用。`
								: undefined
						}
						action={
							<Button
								type="primary"
								disabled={!canManage || Boolean(creationBlockedReason)}
								loading={saving}
								onClick={createForRun}
							>
								创建问题单
							</Button>
						}
					/>
				) : ["QUEUED", "RUNNING"].includes(normalizedRunStatus) ? (
					<Alert showIcon type="info" message="运行尚未结束，暂不进入问题处置" />
				) : (
					<Alert showIcon type="success" message="运行正常，无需处置" />
				)
			) : (
				<Space direction="vertical" size={14} style={{ width: "100%" }}>
					<Descriptions bordered size="small" column={{ xs: 1, md: 3 }}>
						<Descriptions.Item label="状态">
							<QualityStatus status={issue.status} />
						</Descriptions.Item>
						<Descriptions.Item label="责任人">{issue.assignedTo || "待认领"}</Descriptions.Item>
						<Descriptions.Item label="SLA">
							<Tag color={issue.overdue ? "red" : "green"}>{issue.overdue ? "已逾期" : "未逾期"}</Tag>{" "}
							{formatTime(issue.dueAt)}
						</Descriptions.Item>
						<Descriptions.Item label="标题" span={2}>
							{issue.title || "-"}
						</Descriptions.Item>
						<Descriptions.Item label="处理结论">{issue.resolution || "-"}</Descriptions.Item>
					</Descriptions>
					<Space wrap>
						<Button
							type="primary"
							disabled={!canManage || !currentIssueMatches || status !== "OPEN"}
							loading={saving}
							onClick={claim}
						>
							立即认领并处置
						</Button>
						<Button disabled={!canManage || !currentIssueMatches} onClick={() => setNoteOpen(true)}>
							追加处理意见
						</Button>
						{transition === "RESOLVED" ? (
							<Button disabled={!canManage || !currentIssueMatches} onClick={() => setResolutionOpen(true)}>
								标记已解决
							</Button>
						) : null}
						{status === "RESOLVED" ? (
							<Button disabled={!canManage || !currentIssueMatches} onClick={() => setResolutionOpen(true)}>
								关闭问题
							</Button>
						) : null}
					</Space>
					<Table<RunIssueAction>
						rowKey={(row) => String(row.id || `${row.createdDate}-${row.actor}`)}
						columns={actionColumns}
						dataSource={issue.actions || []}
						pagination={false}
						size="small"
					/>
				</Space>
			)}

			<Modal
				title="追加处理意见"
				open={noteOpen}
				confirmLoading={saving}
				okButtonProps={{ disabled: !note.trim() }}
				onOk={appendNote}
				onCancel={() => setNoteOpen(false)}
			>
				<Input.TextArea
					rows={4}
					value={note}
					onChange={(event) => setNote(event.target.value)}
					placeholder="记录排查结论、修复动作或后续计划"
				/>
			</Modal>
			<Modal
				title={status === "RESOLVED" ? "关闭问题" : "标记已解决"}
				open={resolutionOpen}
				confirmLoading={saving}
				okButtonProps={{ disabled: !resolution.trim() }}
				onOk={status === "RESOLVED" ? closeResolvedIssue : resolveIssue}
				onCancel={() => setResolutionOpen(false)}
			>
				<Input.TextArea
					rows={4}
					value={resolution}
					onChange={(event) => setResolution(event.target.value)}
					placeholder="请输入处理结论"
				/>
			</Modal>
		</Card>
	);
}
