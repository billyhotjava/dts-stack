import { describe, expect, it } from "vitest";
import { ownershipTransitionSummary } from "./ownershipTransitionPresentation";

describe("ownership transition confirmation", () => {
	it("includes immutable pins and irreversible warning only when validate says so", () => {
		const irreversible = ownershipTransitionSummary({ allowed: true, reasons: [], previewChecksum: "1234567890abcdef", reversible: false }, 7, 3);
		expect(irreversible).toMatchObject({ source: "可视化维护", target: "dbt 代码维护", modelRevision: "r7", implementationRevision: "r3", previewChecksum: "1234567890ab" });
		expect(irreversible.reversibility).toContain("不可回退");
		expect(ownershipTransitionSummary({ allowed: true, reasons: [], previewChecksum: "a", reversible: true }, 1, 1).reversibility).not.toContain("不可回退");
	});
});
