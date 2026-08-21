import { describe, expect, it } from "vitest";
import {
	aggregationLabel,
	assetTypeLabel,
	auditActionLabel,
	changeTypeLabel,
	classificationLabel,
	governanceEventLabel,
	granularityLabel,
	indicatorDomainLabel,
	indicatorTypeLabel,
	materializationLabel,
	migrationDecisionLabel,
	permissionLabel,
	qualityLabel,
	statusAxisLabel,
	statusLabel,
	subjectTypeLabel,
} from "./customerDisplayLabels";

describe("customer display labels", () => {
	it.each([
		["current", "当前有效"],
		["PUBLISHED", "已发布"],
		["draft", "草稿"],
		["SUCCESS", "成功"],
		["OPEN", "待处理"],
		["RESOLVED", "已解决"],
		["CLOSED", "已关闭"],
	])("renders status %s in Chinese", (value, expected) => {
		expect(statusLabel(value)).toBe(expected);
	});

	it.each([
		["table", "表"],
		["incremental", "增量表"],
		["view", "视图"],
		["ephemeral", "临时模型"],
	])("renders materialization %s in Chinese", (value, expected) => {
		expect(materializationLabel(value)).toBe(expected);
	});

	it("does not leak an unknown backend enum", () => {
		expect(statusLabel("NEW_BACKEND_STATE")).toBe("未知状态");
	});

	it("renders governance and service enums in Chinese", () => {
		expect(assetTypeLabel("DASHBOARD")).toBe("分析看板");
		expect(permissionLabel("MANAGE")).toBe("管理");
		expect(subjectTypeLabel("DEPARTMENT")).toBe("部门");
		expect(classificationLabel("CONFIDENTIAL")).toBe("机密");
		expect(auditActionLabel("CHANGE_OWNERSHIP")).toBe("变更所有者");
	});

	it.each([
		["SUCCEEDED", "成功"],
		["PASSED", "通过"],
		["ERROR", "异常"],
		["SKIPPED", "已跳过"],
	])("renders quality run status %s in Chinese", (value, expected) => {
		expect(statusLabel(value)).toBe(expected);
	});

	it.each([
		["MANUAL", "手动执行"],
		["DRY_RUN", "试跑"],
		["COMPLETENESS", "完整性"],
		["MEDIUM", "中"],
		["CRITICAL", "严重"],
		["SQL_EXECUTION_FAILED", "检测语句执行失败"],
		["COMMENT", "处理意见"],
		["AUTO_NOTE", "系统记录"],
		["NOTE", "处理记录"],
	])("renders quality enum %s in Chinese", (value, expected) => {
		expect(qualityLabel(value)).toBe(expected);
	});

	it("renders indicator and catalog governance enums in Chinese", () => {
		expect(indicatorDomainLabel("FINANCE")).toBe("财务");
		expect(indicatorTypeLabel("DERIVED")).toBe("派生指标");
		expect(aggregationLabel("DISTINCT_COUNT")).toBe("去重计数");
		expect(granularityLabel("MONTH")).toBe("月");
		expect(statusAxisLabel("publication")).toBe("发布状态");
		expect(governanceEventLabel("SEMANTIC_DELIVERY")).toBe("语义交付");
		expect(migrationDecisionLabel("BLOCKED_DOWNGRADE")).toBe("禁止降级");
		expect(changeTypeLabel("UPDATED")).toBe("更新");
		expect(permissionLabel("DATA_ACCESS")).toBe("数据访问");
	});
});
