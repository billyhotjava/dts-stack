import fs from "node:fs";
import { describe, expect, it } from "vitest";

const source = fs.readFileSync(new URL("./PageContainer.tsx", import.meta.url), "utf8");

describe("analytics PageContainer layout contract", () => {
	it("uses explicit layout modes instead of default centered max-width containers", () => {
		expect(source).toContain("layout?: PageContainerLayout");
		expect(source).toContain("layout = 'workspace'");
		expect(source).toContain("workspace:");
		expect(source).toContain("readable:");
		expect(source).toContain("form:");
		expect(source).toContain("canvas:");
		expect(source).not.toContain("maxWidth = 'lg'");
		expect(source).not.toContain("w-full mx-auto ${maxWidthMap[maxWidth]}");
	});
});
