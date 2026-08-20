import { describe, expect, it } from "vitest";
import {
	assetTypeLabel,
	auditActionLabel,
	classificationLabel,
	materializationLabel,
	permissionLabel,
	statusLabel,
	subjectTypeLabel,
} from "./customerDisplayLabels";

describe("customer display labels", () => {
	it.each([
		["current", "当前有效"],
		["PUBLISHED", "已发布"],
		["draft", "草稿"],
		["SUCCESS", "成功"],
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
});
