import { describe, expect, it } from "vitest";
import { describeBlockingImpact, requiresConfirmation, saveBlocker } from "./policyForm";

describe("model governance policy form", () => {
	it("cannot save before the policy is read, when unchanged, or without a reason", () => {
		expect(saveBlocker(undefined, "BLOCKING", "收紧")).toBe("策略尚未读取");
		expect(saveBlocker("ADVISORY", "ADVISORY", "收紧")).toBe("策略未变化");
		expect(saveBlocker("ADVISORY", "BLOCKING", "   ")).toBe("请填写修改原因");
		expect(saveBlocker("ADVISORY", "BLOCKING", "x".repeat(201))).toContain("200");
		expect(saveBlocker("ADVISORY", "BLOCKING", "上线前收紧")).toBeNull();
	});

	it("asks for confirmation only when tightening to BLOCKING", () => {
		expect(requiresConfirmation("BLOCKING")).toBe(true);
		expect(requiresConfirmation("ADVISORY")).toBe(false);
	});

	it("tells the administrator which release candidates are re-checked and which keep their frozen policy", () => {
		const text = describeBlockingImpact({ unfrozenCandidates: 3, frozenCandidates: 2 });
		expect(text).toContain("3 个尚未通过发布前检查的发布单");
		expect(text).toContain("2 个发布单按原策略继续");
		expect(describeBlockingImpact({ unfrozenCandidates: 0, frozenCandidates: 0 })).toContain("当前没有");
	});
});
