import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const source = fs.readFileSync(path.join(import.meta.dirname, "audit-center.tsx"), "utf8");

describe("audit center table source contract", () => {
	it("renders the module-name column from module rather than source system", () => {
		expect(source).toContain('title: "模块名称"');
		expect(source).toContain('dataIndex: "module"');
		expect(source).toContain("translateModuleLabel(r.moduleKey, r.module)");
	});
});
