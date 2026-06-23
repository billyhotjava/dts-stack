import type { AccessChange, ScheduleJob } from "@/types/ingestion";

export const SEED_ACCESS_CHANGES: AccessChange[] = [
	{ id: "ac-1", departmentId: "dept-sales", sourceName: "销售本地台账", changeType: "新增接入", requester: "测试用户", status: "pending", at: "2026-06-23" },
	{ id: "ac-2", departmentId: "dept-sales", sourceName: "ERP 企业系统", changeType: "字段变更", requester: "测试用户", status: "approved", at: "2026-06-20" },
	{ id: "ac-3", departmentId: "dept-quality", sourceName: "QMIS 质量系统", changeType: "权限调整", requester: "网信中心", status: "pending", at: "2026-06-22" },
];

export const SEED_SCHEDULE_JOBS: ScheduleJob[] = [
	{
		id: "sj-1",
		departmentId: "dept-sales",
		name: "销售宽表增量采集",
		sourceName: "PLM 生产系统",
		cron: "0 2 * * *",
		mode: "增量",
		enabled: true,
		lastRun: "2026-06-23 02:00",
		nextRun: "2026-06-24 02:00",
		status: "success",
		watermark: "order_date ≤ 2026-06-22",
	},
	{
		id: "sj-2",
		departmentId: "dept-quality",
		name: "QMIS 质量采集",
		sourceName: "QMIS 质量系统",
		cron: "0 1 * * *",
		mode: "全量",
		enabled: true,
		lastRun: "2026-06-23 01:00",
		nextRun: "2026-06-24 01:00",
		status: "success",
	},
	{
		id: "sj-3",
		departmentId: "dept-quality",
		name: "月报聚合调度",
		sourceName: "dwd_quality_inspect",
		cron: "0 3 1 * *",
		mode: "增量",
		enabled: false,
		lastRun: "2026-06-01 03:00",
		nextRun: "—",
		status: "idle",
		watermark: "month ≤ 2026-05",
	},
];
