import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("semantic empty-state navigation", () => {
	it("commits the cross-workbench route synchronously instead of leaving the old hash view mounted", () => {
		const source = readFileSync(new URL("./SemanticModelingEmptyState.tsx", import.meta.url), "utf8");

		expect(source).toContain("useNavigate");
		expect(source).toMatch(/navigate\(MODEL_WORKBENCH_PATH,\s*\{\s*flushSync:\s*true\s*\}\)/);
		expect(source).not.toMatch(/<Link to=["']\/data-modeling\/dimensions\/workbench["']/);
	});
});
