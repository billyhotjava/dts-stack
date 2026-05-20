import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const viewsDir = path.resolve(import.meta.dirname);

const workspaceViewFiles = [
	"user-management.tsx",
	"role-management.tsx",
	"data-lake-config.tsx",
	"data-lake-editor.tsx",
	"my-changes.tsx",
	"audit-center.tsx",
];

describe("admin workspace page layout contract", () => {
	it("keeps management pages fluid instead of centered with fixed max-width", () => {
		const offenders = workspaceViewFiles.flatMap((fileName) => {
			const source = fs.readFileSync(path.join(viewsDir, fileName), "utf8");
			return /mx-auto[\s\S]{0,80}max-w-|max-w-[^\s"]+[\s\S]{0,80}mx-auto/.test(source) ? [fileName] : [];
		});

		expect(offenders).toEqual([]);
	});
});
