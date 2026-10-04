import { describe, expect, it } from "vitest";
import { resolveHomePathForRoles } from "./home-path";

// F11-UI-001 回归：403 页“返回首页”按三员职责选择落点。
describe("resolveHomePathForRoles", () => {
	it("审计角色回到日志审计页，而不是 sysadmin 专用的 my-changes", () => {
		expect(resolveHomePathForRoles(["ROLE_SECURITY_AUDITOR"])).toBe("/admin/audit");
		expect(resolveHomePathForRoles(["AUDITADMIN"])).toBe("/admin/audit");
	});

	it("授权管理员回到任务审批页", () => {
		expect(resolveHomePathForRoles(["ROLE_AUTH_ADMIN"])).toBe("/admin/approval");
		expect(resolveHomePathForRoles(["authadmin"])).toBe("/admin/approval");
	});

	it("系统管理员保持原 my-changes 落点", () => {
		expect(resolveHomePathForRoles(["ROLE_SYS_ADMIN"])).toBe("/admin/my-changes");
	});

	it("审计优先于其他角色，避免 403 循环", () => {
		expect(resolveHomePathForRoles(["ROLE_SYS_ADMIN", "ROLE_SECURITY_AUDITOR"])).toBe("/admin/audit");
	});
});
