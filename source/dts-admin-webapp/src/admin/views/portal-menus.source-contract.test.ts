import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const viewSource = fs.readFileSync(path.resolve(import.meta.dirname, "portal-menus.tsx"), "utf8");

describe("portal menu management visibility contract", () => {
	it("shows the active menu tree by default and exposes disabled menus only through an explicit switch", () => {
		expect(viewSource).toContain("const [showDisabledMenus, setShowDisabledMenus] = useState(false)");
		expect(viewSource).toMatch(/showDisabledMenus\s*\?\s*allMenus\s*:\s*activeMenus/);
		expect(viewSource).toContain('aria-label="显示已禁用菜单"');
		expect(viewSource).toContain("显示已禁用菜单");
	});

	it("keeps summary statistics and portal cache based on the complete tree", () => {
		expect(viewSource).toMatch(/walk\(allMenus\)/);
		expect(viewSource).toMatch(/\}, \[allMenus\]\);/);
		expect(viewSource).toContain("setPortalMenus(activeMenus, allMenus)");
	});

	it("continues to render backend display names as menu titles", () => {
		expect(viewSource).toContain("const baseName = item.displayName ?? item.name ?? String(id)");
	});
});
