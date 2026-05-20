import fs from "node:fs";
import { describe, expect, it } from "vitest";

const publicCardSource = fs.readFileSync(new URL("./PublicCardPage.tsx", import.meta.url), "utf8");
const marketplaceSource = fs.readFileSync(new URL("./screens/ScreenMarketplacePage.tsx", import.meta.url), "utf8");

describe("analytics workspace page layout contract", () => {
	it("keeps public card pages readable without making workspace pages centered", () => {
		expect(publicCardSource).toContain('<PageContainer layout="readable">');
		expect(marketplaceSource).not.toContain("mx-auto");
		expect(marketplaceSource).not.toContain("max-w-[1320px]");
	});
});
