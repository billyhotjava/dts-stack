import { Alert, Button, Card, Descriptions, Empty, Space, Table, Tag, Typography } from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
	getReleaseCandidateWorkbench,
	lockReleaseCandidate,
	type ReleaseCandidateEntryEvidence,
	type ReleaseCandidateEvidenceState,
	type ReleaseCandidateEvidenceType,
	type ReleaseCandidateRelationEvidenceState,
	type ReleaseCandidateWorkbenchScreenState,
	releaseCandidateWorkbenchError,
	releaseCandidateWorkbenchForbidden,
	releaseCandidateWorkbenchLoading,
	retryReleaseCandidate,
	toReleaseCandidateWorkbenchScreenState,
} from "@/api/modelSpecApi";

const { Text, Title } = Typography;

const evidenceLabel: Record<ReleaseCandidateEvidenceType, string> = {
	ARTIFACT: "dbt 制品",
	BUILD_RUN: "构建与关系核验",
	QUALITY_RUN: "质量检查",
	REVIEW: "审核",
	PUBLICATION: "发布",
	REGISTRATION: "资产登记",
	ROLLBACK: "回滚",
};

const evidenceStateLabel: Record<ReleaseCandidateEvidenceState, string> = {
	UNAVAILABLE: "尚无证据",
	RUNNING: "进行中",
	PASSED: "已通过",
	FAILED: "失败",
	STALE: "待核对",
};

const relationStateLabel: Record<ReleaseCandidateRelationEvidenceState, string> = {
	NOT_STARTED: "尚未开始",
	PENDING: "等待构建",
	PROBING: "正在核验关系",
	VERIFIED: "关系已核验",
	FAILED: "关系核验失败",
	UNKNOWN: "结果待对账",
};

const stateColor = (state: ReleaseCandidateEvidenceState | ReleaseCandidateRelationEvidenceState) => {
	if (state === "PASSED" || state === "VERIFIED") return "success";
	if (state === "FAILED") return "error";
	if (state === "RUNNING" || state === "PROBING" || state === "PENDING") return "processing";
	if (state === "STALE" || state === "UNKNOWN") return "warning";
	return undefined;
};

const requestKey = (prefix: string, candidateId: string) => {
	const nonce =
		typeof globalThis.crypto?.randomUUID === "function"
			? globalThis.crypto.randomUUID()
			: `${Date.now()}-${Math.random().toString(16).slice(2)}`;
	return `${prefix}:${candidateId}:${nonce}`;
};

const errorProjection = (error: unknown) => {
	const failure = error as {
		response?: { status?: number; data?: { code?: string; message?: string } };
		message?: string;
	};
	return {
		status: failure.response?.status,
		code: failure.response?.data?.code || "MODEL_RELEASE_WORKBENCH_UNAVAILABLE",
		message: failure.response?.data?.message || failure.message || "交付工作台暂时不可用",
	};
};

type Props = {
	planId: string;
	requestedCandidateId?: string;
	requestedModelSpecId?: string;
};

export function ReleaseCandidateWorkbenchPanel({
	planId,
	requestedCandidateId = "",
	requestedModelSpecId = "",
}: Props) {
	const requestRef = useRef(0);
	const [screen, setScreen] = useState<ReleaseCandidateWorkbenchScreenState>(() => releaseCandidateWorkbenchLoading());
	const [busy, setBusy] = useState<"start" | "retry" | "refresh" | null>(null);
	const [actionError, setActionError] = useState("");

	const refresh = useCallback(
		async (manual = false) => {
			const requestId = ++requestRef.current;
			if (manual) setBusy("refresh");
			try {
				const workspace = await getReleaseCandidateWorkbench(planId);
				if (requestId !== requestRef.current) return false;
				setScreen(toReleaseCandidateWorkbenchScreenState(workspace));
				return true;
			} catch (error) {
				if (requestId !== requestRef.current) return false;
				const failure = errorProjection(error);
				setScreen(
					failure.status === 403
						? releaseCandidateWorkbenchForbidden(failure.code, "当前账号没有该计划的交付查看权限")
						: releaseCandidateWorkbenchError(failure.code, "交付证据读取失败，请刷新重试"),
				);
				return false;
			} finally {
				if (manual && requestId === requestRef.current) setBusy(null);
			}
		},
		[planId],
	);

	useEffect(() => {
		setScreen(releaseCandidateWorkbenchLoading());
		setActionError("");
		void refresh();
		return () => {
			requestRef.current += 1;
		};
	}, [refresh]);

	const workspace = screen.kind === "ready" || screen.kind === "empty" ? screen.data : null;
	const candidate = workspace?.candidate || null;
	const shouldPoll =
		Boolean(candidate && ["BUILDING", "QUALITY_RUNNING", "PUBLISHING"].includes(candidate.status)) ||
		Boolean(workspace?.evidence.some((item) => item.state === "RUNNING"));

	useEffect(() => {
		if (!shouldPoll) return;
		const timer = window.setInterval(() => {
			if (document.visibilityState === "visible") void refresh();
		}, 3000);
		return () => window.clearInterval(timer);
	}, [refresh, shouldPoll]);

	const candidateMismatch = Boolean(requestedCandidateId && candidate && candidate.id !== requestedCandidateId);
	const modelInScope = Boolean(
		!requestedModelSpecId || candidate?.entries.some((entry) => entry.modelSpecId === requestedModelSpecId),
	);
	const scopeMismatch = candidateMismatch || Boolean(candidate && !modelInScope);
	const focusedEvidence = useMemo(
		() =>
			scopeMismatch
				? []
				: (workspace?.entryEvidence || []).filter(
						(item) => !requestedModelSpecId || item.modelSpecId === requestedModelSpecId,
					),
		[requestedModelSpecId, scopeMismatch, workspace?.entryEvidence],
	);

	const execute = async (action: "start" | "retry") => {
		if (!candidate || busy || scopeMismatch) return;
		setBusy(action);
		setActionError("");
		try {
			const expected = { id: candidate.id, version: candidate.version };
			if (action === "start") {
				await lockReleaseCandidate(planId, expected, requestKey("candidate-build", candidate.id), "开始候选物化构建");
			} else {
				await retryReleaseCandidate(
					planId,
					expected,
					requestKey("candidate-retry", candidate.id),
					"重试失败的候选物化构建",
				);
			}
			await refresh();
		} catch {
			const reconciled = await refresh();
			if (!reconciled) setActionError("请求结果尚未确认；请恢复连接后刷新工作台，系统不会在本地猜测结果。");
		} finally {
			setBusy(null);
		}
	};

	if (screen.kind === "loading") {
		return <Card loading data-testid="release-candidate-workbench-loading" />;
	}
	if (screen.kind === "forbidden" || screen.kind === "error") {
		return (
			<Alert
				type={screen.kind === "forbidden" ? "warning" : "error"}
				showIcon
				message={screen.message}
				description={screen.code}
				action={<Button onClick={() => void refresh(true)}>重新加载</Button>}
			/>
		);
	}
	if (!workspace || !candidate) {
		return (
			<Card title="构建与关系核验证据" data-testid="release-candidate-workbench-empty">
				<Empty description="当前计划尚无交付候选；请从模型详情完成数据实现后发起构建。" />
			</Card>
		);
	}

	const canStart = workspace.allowedActions.includes("START_BUILD") && !scopeMismatch;
	const canRetry = workspace.allowedActions.includes("RETRY_BUILD") && !scopeMismatch;
	const failedEvidence = workspace.evidence.find((item) => item.state === "FAILED" || item.state === "STALE");

	return (
		<div className="space-y-4" data-testid="release-candidate-workbench">
			<Card
				title="构建与关系核验证据"
				extra={
					<Space wrap>
						{canStart ? (
							<Button type="primary" loading={busy === "start"} onClick={() => void execute("start")}>
								开始构建
							</Button>
						) : null}
						{canRetry ? (
							<Button type="primary" danger loading={busy === "retry"} onClick={() => void execute("retry")}>
								重试构建
							</Button>
						) : null}
						<Button loading={busy === "refresh"} disabled={Boolean(busy)} onClick={() => void refresh(true)}>
							刷新服务端状态
						</Button>
					</Space>
				}
			>
				<Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }}>
					<Descriptions.Item label="候选">
						<Text copyable={{ text: candidate.id }}>{candidate.id}</Text>
					</Descriptions.Item>
					<Descriptions.Item label="范围来源">
						{candidate.origin === "SINGLE_MODEL_INTENT" ? "单模型快捷构建" : "批量交付工作台"}
					</Descriptions.Item>
					<Descriptions.Item label="环境">{candidate.environment}</Descriptions.Item>
					<Descriptions.Item label="候选状态">{candidate.status}</Descriptions.Item>
				</Descriptions>
				{scopeMismatch ? (
					<Alert
						className="mt-4"
						type="error"
						showIcon
						message="深链指向的候选或模型已不是当前工作台范围"
						description="页面不会用当前候选替代原候选证据；请返回模型详情刷新后重新进入。"
					/>
				) : null}
				{workspace.primaryBlocker ? (
					<Alert
						className="mt-4"
						type="warning"
						showIcon
						message={workspace.primaryBlocker.message}
						description={workspace.primaryBlocker.code}
					/>
				) : null}
				{failedEvidence ? (
					<Alert
						className="mt-4"
						type="error"
						showIcon
						message={`${evidenceLabel[failedEvidence.type]}未通过`}
						description={failedEvidence.code || "请查看下方每模型证据"}
					/>
				) : null}
				{actionError ? <Alert className="mt-4" type="error" showIcon message={actionError} /> : null}
				<div className="mt-4 flex flex-wrap gap-2">
					{workspace.evidence.map((item) => (
						<Tag key={item.type} color={stateColor(item.state)}>
							{evidenceLabel[item.type]}：{evidenceStateLabel[item.state]}
						</Tag>
					))}
				</div>
			</Card>

			<Card title="每模型真实运行与物理关系">
				<Table<ReleaseCandidateEntryEvidence>
					rowKey="candidateEntryId"
					size="small"
					pagination={false}
					scroll={{ x: 1100 }}
					dataSource={focusedEvidence}
					locale={{ emptyText: scopeMismatch ? "深链范围不匹配" : "尚无构建运行证据" }}
					columns={[
						{
							title: "业务模型",
							render: (_, row) => (
								<div>
									<div>{row.modelName}</div>
									<Text type="secondary">
										r{row.modelRevision} / i{row.implementationRevision || "—"}
									</Text>
								</div>
							),
						},
						{ title: "目标关系", dataIndex: "targetRelation", render: (value) => value || "待生成" },
						{ title: "dbt / Airflow", dataIndex: "runStatus", render: (value) => value || "尚未开始" },
						{
							title: "关系核验",
							render: (_, row) => (
								<Tag color={stateColor(row.relationState)}>{relationStateLabel[row.relationState]}</Tag>
							),
						},
						{
							title: "Airflow run",
							render: (_, row) =>
								row.airflowRunId ? <Text copyable={{ text: row.airflowRunId }}>{row.airflowRunId}</Text> : "—",
						},
						{
							title: "观测时间",
							dataIndex: "observedAt",
							render: (value) => (value ? new Date(value).toLocaleString() : "—"),
						},
						{ title: "修复码", dataIndex: "repairCode", render: (value) => value || "—" },
					]}
				/>
				<Title level={5} className="mt-4">
					判定规则
				</Title>
				<Text type="secondary">
					dbt 成功与物理关系存在是两条独立证据；只有每个模型的 run 为 BUILT 且关系为“关系已核验”，候选才算构建完成。
				</Text>
			</Card>
		</div>
	);
}
