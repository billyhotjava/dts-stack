import { describe, expect, it } from "vitest";
import { materializationFailureReason } from "./materializationFailureReason";

describe("materialization failure reason", () => {
	it("explains a missing dbt target instead of echoing the bare code", () => {
		expect(materializationFailureReason("DBT_TARGET_DATASOURCE_NOT_FOUND", "DBT_TARGET_DATASOURCE_NOT_FOUND")).toBe(
			"dbt 目标数仓配置指向的数据源不存在，构建未启动。请联系平台管理员核对目标数仓配置。",
		);
	});

	it.each([
		"DBT_TARGET_SECRET_UNAVAILABLE",
		"DBT_TARGET_SECRET_UNPROTECTED",
		"DBT_EXECUTION_TARGET_SECRET_UNAVAILABLE",
	])("explains %s as an unavailable warehouse credential", (code) => {
		expect(materializationFailureReason(code, code)).toContain("目标数仓凭据不可用");
	});

	it("keeps an explicit server message and existing fallbacks", () => {
		expect(materializationFailureReason("MODEL_X", "服务端说明")).toBe("服务端说明");
		expect(materializationFailureReason("MODEL_AIRFLOW_DAG_NOT_REGISTERED", null)).toBe("执行任务尚未就绪，构建未启动");
		expect(materializationFailureReason("MODEL_DBT_AIRFLOW_UPSTREAM_FAILED", null)).toBe(
			"MODEL_DBT_AIRFLOW_UPSTREAM_FAILED",
		);
		expect(materializationFailureReason(null, null)).toBe("—");
	});
});
