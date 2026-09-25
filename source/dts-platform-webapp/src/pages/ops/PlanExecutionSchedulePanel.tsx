import { Alert, Button, Pagination, Space, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
	getPlanExecutionWorkspace,
	enablePlanExecution,
	type PlanExecutionBinding,
	repairPlanExecutionBinding,
	runPlanExecutionNow,
} from "@/api/modelSpecApi";
import { listWarehousePlans, type WarehousePlanHeader } from "@/api/warehousePlanApi";
import { CompactTable } from "@/components/table";
import { normalizeModelingRequestFailure } from "@/pages/data-modeling/prototype/services/planningProjectionService";

import { PlanExecutionPublicationPicker } from "./PlanExecutionPublicationPicker";

type ScheduleRow = { plan: WarehousePlanHeader; binding: PlanExecutionBinding };

const PAGE_SIZE = 10;
const ENVIRONMENT_LABELS: Record<string, string> = { dev: "开发环境", test: "测试环境", prod: "生产环境" };
const STATE_LABELS: Record<PlanExecutionBinding["state"], { label: string; color: string }> = {
	ONLINE: { label: "运行正常", color: "green" },
	DEPLOYING: { label: "部署中", color: "blue" },
	DISABLED: { label: "已部署，未启用", color: "default" },
	DEGRADED: { label: "异常", color: "red" },
	UNKNOWN: { label: "待核验", color: "orange" },
};

const formatTime = (value?: string | null) => {
	if (!value) return "—";
	const parsed = new Date(value);
	return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN", { hour12: false });
};

export function PlanExecutionSchedulePanel({ focusPlanId }: { focusPlanId?: string }) {
    const [page, setPage] = useState(1);
    const [total, setTotal] = useState(0);
    const [visiblePlans, setVisiblePlans] = useState<WarehousePlanHeader[]>([]);
    const sequence = useRef(0);
    const [rows, setRows] = useState<ScheduleRow[]>([]);
	const [loading, setLoading] = useState(false);
	const [failure, setFailure] = useState("");
	const [partial, setPartial] = useState(0);
	const [busyBinding, setBusyBinding] = useState("");
	const [notice, setNotice] = useState("");

	const load = useCallback(async () => {
		const current = ++sequence.current;
		setLoading(true);
		setFailure("");
		try {
			const plans = await listWarehousePlans();
			const ordered = focusPlanId
				? [...plans.filter((plan) => plan.id === focusPlanId), ...plans.filter((plan) => plan.id !== focusPlanId)]
				: plans;
            const visible = ordered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
            const next: ScheduleRow[] = [];
			let unreadable = 0;
			for (const plan of visible) {
				try {
					const workspace = await getPlanExecutionWorkspace(plan.id);
					for (const binding of workspace.bindings) next.push({ plan, binding });
				} catch {
					// One plan's execution state failing to load must not hide the others.
					unreadable++;
				}
			}
            if (current !== sequence.current) return;
            setTotal(plans.length);
            setVisiblePlans(visible);
            setRows(next);
			setPartial(unreadable);
		} catch (error) {
			if (current !== sequence.current) return;
			setFailure(normalizeModelingRequestFailure(error, "调度计划读取失败。").message);
		} finally {
			if (current === sequence.current) setLoading(false);
		}
	}, [focusPlanId, page]);

	useEffect(() => { setPage(1); }, [focusPlanId]);
	useEffect(() => {
		void load();
        return () => { sequence.current++; };
	}, [load]);

	const runNow = useCallback(
		async (row: ScheduleRow) => {
			setBusyBinding(row.binding.id);
			setNotice("");
			try {
				await runPlanExecutionNow(row.plan.id, row.binding.id, crypto.randomUUID());
				setNotice(`「${row.plan.name}」已提交运行，结果会出现在任务实例中。`);
				await load();
			} catch (error) {
				setNotice(normalizeModelingRequestFailure(error, "运行计划未能启动。").message);
			} finally {
				setBusyBinding("");
			}
		},
		[load],
	);

	const repair = useCallback(
		async (row: ScheduleRow) => {
			setBusyBinding(row.binding.id);
			setNotice("");
			try {
				await repairPlanExecutionBinding(row.plan.id, row.binding.id, row.binding.version);
				setNotice(`「${row.plan.name}」已提交部署修复。`);
				await load();
			} catch (error) {
				setNotice(normalizeModelingRequestFailure(error, "运行计划部署修复未能启动。").message);
			} finally {
				setBusyBinding("");
			}
		},
		[load],
	);

    const enable = useCallback(async (row: ScheduleRow) => {
        setBusyBinding(row.binding.id);
        try { await enablePlanExecution(row.plan.id, row.binding.id, row.binding.version); setNotice("运行计划已启用。"); await load(); }
        catch (error) { setNotice(normalizeModelingRequestFailure(error, "启用失败。").message); }
        finally { setBusyBinding(""); }
    }, [load]);

	const columns = useMemo<ColumnsType<ScheduleRow>>(
		() => [
			{
				title: "建模规划",
				key: "plan",
				render: (_, row) => (
					<Space direction="vertical" size={0}>
						<strong>{row.plan.name}</strong>
						<Typography.Text type="secondary" style={{ fontSize: 12 }}>
							{row.binding.airflowDagId || "DAG 待生成"}
						</Typography.Text>
					</Space>
				),
			},
			{
				title: "环境",
				key: "environment",
				render: (_, row) => ENVIRONMENT_LABELS[row.binding.environment] || row.binding.environment,
			},
			{
				title: "调度",
				key: "schedule",
				render: (_, row) =>
					row.binding.scheduleMode === "CRON_ENABLED"
						? row.binding.effectiveSchedule || row.binding.desiredSchedule || "定时"
						: "仅手动运行",
			},
			{
				title: "状态",
				key: "state",
				render: (_, row) => {
					const state = STATE_LABELS[row.binding.state];
					return (
						<Space direction="vertical" size={0}>
							<Tag color={state.color}>{state.label}</Tag>
							{row.binding.primaryBlocker ? (
								<Typography.Text type="danger" style={{ fontSize: 12 }}>
									{row.binding.primaryBlocker.message}
								</Typography.Text>
							) : null}
						</Space>
					);
				},
			},
			{
				title: "最近运行",
				key: "latest-run",
				render: (_, row) => {
					const run = row.binding.latestOperationalRun;
					if (!run?.status) return "—";
					return (
						<Space direction="vertical" size={0}>
							<span>{run.status}</span>
							<Typography.Text type="secondary" style={{ fontSize: 12 }}>
								{formatTime(run.finishedAt || run.startedAt || run.createdAt)}
								{run.errorCode ? ` · ${run.errorCode}` : ""}
							</Typography.Text>
						</Space>
					);
				},
			},
			{
				title: "操作",
				key: "actions",
				render: (_, row) => (
					<Space>
                        {row.binding.allowedActions.includes("ENABLE") ? <Button size="small" disabled={Boolean(busyBinding)} onClick={() => void enable(row)}>启用运行</Button> : null}
						{row.binding.allowedActions.includes("RUN_NOW") ? (
							<Button
								disabled={Boolean(busyBinding)}
								loading={busyBinding === row.binding.id}
								onClick={() => void runNow(row)}
								size="small"
							>
								立即运行并核验
							</Button>
						) : null}
						{row.binding.allowedActions.includes("REPAIR_DEPLOYMENT") ? (
							<Button
								disabled={Boolean(busyBinding)}
								loading={busyBinding === row.binding.id}
								onClick={() => void repair(row)}
								size="small"
							>
								修复部署
							</Button>
						) : null}
					</Space>
				),
			},
		],
		[busyBinding, repair, runNow, enable],
	);

	return (
		<section aria-label="调度计划">
			<Alert
				showIcon
				type="info"
				message="已发布模型的运行在这里办理。运行失败只影响本次运行，不会改变模型的发布版本或构建结果。"
				style={{ marginBottom: 12 }}
			/>
			{failure ? (
				<Alert
					showIcon
					type="error"
					message={failure}
					action={<Button onClick={() => void load()}>重新读取</Button>}
					style={{ marginBottom: 12 }}
				/>
			) : null}
			{partial ? (
				<Alert
					showIcon
					type="warning"
					message={`有 ${partial} 个建模规划的调度状态读取失败，其余已显示。`}
					style={{ marginBottom: 12 }}
				/>
			) : null}
			{notice ? (
				<Alert
					showIcon
					type="info"
					message={notice}
					closable
					onClose={() => setNotice("")}
					style={{ marginBottom: 12 }}
				/>
			) : null}
            <PlanExecutionPublicationPicker plans={visiblePlans} onDeployed={load} refreshKey={rows} />
            <Pagination current={page} pageSize={PAGE_SIZE} total={total} onChange={setPage} showSizeChanger={false} showLessItems responsive style={{ flexWrap: "wrap", rowGap: 8 }} showTotal={(count) => `共 ${count} 个规划`} />
			<CompactTable<ScheduleRow>
				pagination={false}
                scroll={{ x: 900 }}
				columns={columns}
				dataSource={rows}
				loading={loading}
				rowKey={(row) => row.binding.id}
				rowClassName={(row) => (focusPlanId && row.plan.id === focusPlanId ? "ops-schedule-focus" : "")}
				locale={{ emptyText: "暂无已发布模型的运行计划" }}
			/>
		</section>
	);
}
