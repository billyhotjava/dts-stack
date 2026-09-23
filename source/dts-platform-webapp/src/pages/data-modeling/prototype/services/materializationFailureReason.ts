const CREDENTIAL_UNAVAILABLE = "目标数仓凭据不可用，构建未启动。请联系平台管理员核对目标数仓凭据。";

/** Codes whose server message is the bare code; the user needs to know who can fix them. */
const RUNTIME_TARGET_REASONS: Readonly<Record<string, string>> = {
	DBT_TARGET_DATASOURCE_NOT_FOUND: "dbt 目标数仓配置指向的数据源不存在，构建未启动。请联系平台管理员核对目标数仓配置。",
	DBT_TARGET_NOT_CONFIGURED: "未配置 dbt 目标数仓，构建未启动。请联系平台管理员配置目标数仓。",
	DBT_TARGET_JDBC_MISSING: "目标数仓缺少连接地址，构建未启动。请联系平台管理员补充连接配置。",
	DBT_TARGET_SECRET_UNPROTECTED: CREDENTIAL_UNAVAILABLE,
	DBT_TARGET_SECRET_UNAVAILABLE: CREDENTIAL_UNAVAILABLE,
	DBT_EXECUTION_TARGET_SECRET_UNAVAILABLE: CREDENTIAL_UNAVAILABLE,
	MODEL_AIRFLOW_DAG_NOT_REGISTERED: "执行任务尚未就绪，构建未启动",
};

export function materializationFailureReason(code?: string | null, failureMessage?: string | null): string {
	const explained = code ? RUNTIME_TARGET_REASONS[code] : undefined;
	if (explained && (!failureMessage || failureMessage === code)) return explained;
	return failureMessage || code || "—";
}
