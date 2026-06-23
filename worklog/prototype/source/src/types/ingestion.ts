/** 接入变更申请。 */
export interface AccessChange {
	id: string;
	departmentId: string;
	sourceName: string;
	changeType: "新增接入" | "字段变更" | "权限调整";
	requester: string;
	status: "pending" | "approved" | "rejected";
	at: string;
}

/** 采集调度任务。 */
export interface ScheduleJob {
	id: string;
	departmentId: string;
	name: string;
	sourceName: string;
	/** cron 表达式 */
	cron: string;
	mode: "全量" | "增量";
	enabled: boolean;
	lastRun?: string;
	nextRun?: string;
	status: "success" | "failed" | "idle";
	/** 增量水位 */
	watermark?: string;
}
