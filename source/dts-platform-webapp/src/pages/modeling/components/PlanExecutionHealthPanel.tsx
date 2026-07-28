import { Alert, Button, Card, Empty, Space, Table, Tag, Typography } from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
	getPlanExecutionWorkspace,
	type PlanExecutionBinding,
	type PlanExecutionWorkspace,
	repairPlanExecutionBinding,
	runPlanExecutionNow,
} from "@/api/modelSpecApi";

const { Text } = Typography;

const stateLabel: Record<PlanExecutionBinding["state"], string> = {
	ONLINE: "上线完成",
	DEPLOYING: "部署中",
	DISABLED: "已停用",
	DEGRADED: "运行异常",
	UNKNOWN: "状态未知",
};

const stateColor = (state: PlanExecutionBinding["state"]) => {
	if (state === "ONLINE") return "success";
	if (state === "DEPLOYING") return "processing";
	if (state === "DISABLED") return "default";
	return "warning";
};

const ownerLabel: Record<string, string> = {
	MODEL_MAINTAINER: "模型维护人",
	RELEASE_OPERATOR: "上线操作人",
	PLATFORM_OPERATOR: "平台运维",
};

const dateTime = (value?: string | null) => (value ? new Date(value).toLocaleString() : "—");

const requestKey = (bindingId: string) => {
	const nonce =
		typeof globalThis.crypto?.randomUUID === "function"
			? globalThis.crypto.randomUUID()
			: `${Date.now()}-${Math.random().toString(16).slice(2)}`;
	return `plan-run:${bindingId}:${nonce}`;
};

export function PlanExecutionHealthPanel({ planId }: { planId: string }) {
	const requestRef = useRef(0);
	const [workspace, setWorkspace] = useState<PlanExecutionWorkspace | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const [runningBindingId, setRunningBindingId] = useState("");

	const refresh = useCallback(async () => {
		const requestId = ++requestRef.current;
		try {
			const current = await getPlanExecutionWorkspace(planId);
			if (requestId !== requestRef.current) return;
			setWorkspace(current);
			setError("");
		} catch (failure) {
			if (requestId !== requestRef.current) return;
			const response = failure as { response?: { data?: { code?: string; message?: string } } };
			setError(response.response?.data?.message || "上线计算状态暂时无法读取");
		} finally {
			if (requestId === requestRef.current) setLoading(false);
		}
	}, [planId]);

	useEffect(() => {
		setLoading(true);
		setWorkspace(null);
		void refresh();
		return () => {
			requestRef.current += 1;
		};
	}, [refresh]);

	const shouldPoll = useMemo(
		() =>
			Boolean(
				workspace?.bindings.some(
					(binding) =>
						binding.state === "DEPLOYING" ||
						["PENDING", "CLAIMED", "SUBMITTED"].includes(binding.latestOperationalRun?.status || ""),
				),
			),
		[workspace],
	);

	useEffect(() => {
		if (!shouldPoll) return;
		const timer = window.setInterval(() => {
			if (document.visibilityState === "visible") void refresh();
		}, 4000);
		return () => window.clearInterval(timer);
	}, [refresh, shouldPoll]);

	const runNow = async (binding: PlanExecutionBinding) => {
		if (runningBindingId || !binding.allowedActions.includes("RUN_NOW")) return;
		setRunningBindingId(binding.id);
		setError("");
		try {
			await runPlanExecutionNow(planId, binding.id, requestKey(binding.id));
			await refresh();
		} catch (failure) {
			const response = failure as { response?: { data?: { code?: string; message?: string } } };
			setError(response.response?.data?.message || "计算请求结果尚未确认，请刷新服务端状态");
		} finally {
			setRunningBindingId("");
		}
	};

	const repair = async (binding: PlanExecutionBinding) => {
		if (runningBindingId || !binding.allowedActions.includes("REPAIR_DEPLOYMENT")) return;
		setRunningBindingId(binding.id);
		setError("");
		try {
			await repairPlanExecutionBinding(planId, binding.id, binding.version);
			await refresh();
		} catch (failure) {
			const response = failure as { response?: { data?: { code?: string; message?: string } } };
			setError(response.response?.data?.message || "修复请求未生效；绑定版本可能已变化，请刷新后重试");
		} finally {
			setRunningBindingId("");
		}
	};

	if (loading) return <Card loading data-testid="plan-execution-health-loading" />;

	return (
		<Card
			title="上线计算"
			extra={
				<Button disabled={Boolean(runningBindingId)} onClick={() => void refresh()}>
					刷新服务端状态
				</Button>
			}
			data-testid="plan-execution-health"
		>
			{error ? <Alert className="mb-4" type="error" showIcon message={error} /> : null}
			{!workspace?.bindings.length ? (
				<Empty description="当前计划尚未生成上线执行绑定；候选发布成功后才会创建计划 DAG。" />
			) : (
				<Table<PlanExecutionBinding>
					rowKey="id"
					size="small"
					pagination={false}
					scroll={{ x: 1500 }}
					dataSource={workspace.bindings}
					columns={[
						{
							title: "环境 / 状态",
							render: (_, row) => (
								<Space direction="vertical" size={2}>
									<Text>{row.environment}</Text>
									<Tag color={stateColor(row.state)}>{stateLabel[row.state]}</Tag>
								</Space>
							),
						},
						{
							title: "更新方式",
							render: (_, row) =>
								row.scheduleMode === "MANUAL_ONLY"
									? "手动运行"
									: `${row.desiredSchedule || "—"} · ${row.desiredTimezone || "—"}`,
						},
						{
							title: "Airflow 实际状态",
							render: (_, row) => (
								<Space direction="vertical" size={2}>
									<Text>
										{row.airflowState === "OBSERVED" ? (row.airflowPaused ? "已暂停" : "运行中") : "无法确认"}
									</Text>
									<Text type="secondary">
										{row.scheduleMode === "CRON_ENABLED" ? row.actualSchedule || "实际计划未知" : "无定时计划"}
									</Text>
								</Space>
							),
						},
						{ title: "下次运行", render: (_, row) => dateTime(row.nextRunAt) },
						{
							title: "最近业务运行",
							render: (_, row) => (
								<Space direction="vertical" size={2}>
									<Text>{row.latestOperationalRun?.status || "尚未运行"}</Text>
									<Text type="secondary">{dateTime(row.latestOperationalRun?.startedAt)}</Text>
								</Space>
							),
						},
						{
							title: "真实物理关系",
							render: (_, row) => (
								<Space direction="vertical" size={2}>
									<Text>{row.latestRelation?.physicalRelation || "尚无运行观测"}</Text>
									<Text type={row.latestRelation?.verified === false ? "danger" : "secondary"}>
										{row.latestRelation?.verified === true
											? "已核验"
											: row.latestRelation?.verified === false
												? "核验失败"
												: "未运行"}
									</Text>
								</Space>
							),
						},
						{
							title: "阻断 / 责任人",
							render: (_, row) =>
								row.primaryBlocker ? (
									<Space direction="vertical" size={2}>
										<Text type="danger">{row.primaryBlocker.message}</Text>
										<Text type="secondary">
											{ownerLabel[row.primaryBlocker.owner] || row.primaryBlocker.owner} · {row.primaryBlocker.code}
										</Text>
									</Space>
								) : (
									"—"
								),
						},
						{
							title: "操作",
							fixed: "right",
							render: (_, row) => (
								<Space>
									{row.allowedActions.includes("REPAIR_DEPLOYMENT") ? (
										<Button danger loading={runningBindingId === row.id} onClick={() => void repair(row)}>
											修复部署
										</Button>
									) : (
										<Button
											type="primary"
											disabled={!row.allowedActions.includes("RUN_NOW")}
											loading={runningBindingId === row.id}
											onClick={() => void runNow(row)}
										>
											立即运行
										</Button>
									)}
								</Space>
							),
						},
					]}
				/>
			)}
			<div className="mt-3">
				<Text type="secondary">
					上线计算只使用当前已发布范围；DAG、selector、target 和运行凭据均由服务端生成，页面不要求用户填写。
				</Text>
			</div>
		</Card>
	);
}
