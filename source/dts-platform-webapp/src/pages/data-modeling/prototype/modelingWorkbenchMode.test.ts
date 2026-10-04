import { describe, expect, it } from "vitest";
import {
	isPersistedVisualReadOnly,
	modelingCapabilityReasonText,
	normalizeWorkbenchView,
	resolveModelingModeAccess,
	updateWorkbenchView,
} from "./modelingWorkbenchMode";

describe("modeling workbench mode URL contract", () => {
	it("normalizes the legacy advanced deep link to the canonical code view", () => {
		expect(normalizeWorkbenchView(new URLSearchParams("modelSpecId=m-1&open=advanced"))).toEqual({
			view: "code",
			legacyAdvanced: true,
		});
	});

	it("does not capability-lock a new, unsaved visual draft", () => {
		expect(isPersistedVisualReadOnly(false, "BLOCKED")).toBe(false);
		expect(isPersistedVisualReadOnly(true, "PREVIEW")).toBe(true);
	});

	it("resolves one capability-driven access mode for visual and code views", () => {
		expect(resolveModelingModeAccess({ view: "visual", canMaintain: true, allowedActions: ["EDIT_MODEL"], capabilityReasons: [] }).access).toBe("EDIT");
		expect(resolveModelingModeAccess({ view: "visual", canMaintain: true, allowedActions: ["OPEN_VISUAL"], capabilityReasons: [] }).access).toBe("PREVIEW");
		expect(resolveModelingModeAccess({ view: "code", canMaintain: false, allowedActions: ["OPEN_CODE"], capabilityReasons: [] }).access).toBe("PREVIEW");
		expect(resolveModelingModeAccess({ view: "code", canMaintain: true, allowedActions: [], capabilityReasons: ["版本不匹配"] })).toMatchObject({ access: "BLOCKED", reason: "版本不匹配" });
	});

	it("maps fail-closed capability codes to customer-readable Chinese", () => {
		expect(modelingCapabilityReasonText("MODEL_REPRESENTATION_ARTIFACT_PIN_MISMATCH")).toContain("版本不一致");
		expect(modelingCapabilityReasonText("MODEL_REPRESENTATION_FIELDS_UNTRUSTED")).toContain("字段结构");
		expect(modelingCapabilityReasonText("MODEL_REPRESENTATION_DYNAMIC_DEPENDENCY")).toContain("动态依赖");
		expect(modelingCapabilityReasonText("MODEL_REPRESENTATION_DEPENDENCIES_UNTRUSTED")).toContain("上游依赖");
	});

	it("uses modelSpecId and view only for new visual/code links", () => {
		expect(updateWorkbenchView(new URLSearchParams("modelSpecId=m-1&open=advanced"), "visual").toString()).toBe(
			"modelSpecId=m-1&view=visual",
		);
		expect(updateWorkbenchView(new URLSearchParams("modelSpecId=m-1"), "code").toString()).toBe(
			"modelSpecId=m-1&view=code",
		);
	});
});
