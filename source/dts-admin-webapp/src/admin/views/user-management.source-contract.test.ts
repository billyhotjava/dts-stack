import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const source = fs.readFileSync(path.join(import.meta.dirname, "user-management.tsx"), "utf8");

describe("user management list contract", () => {
	it("does not expose email as a top-level table column", () => {
		expect(source).not.toMatch(/title:\s*"邮箱"[\s\S]{0,160}dataIndex:\s*"email"/);
	});
});
