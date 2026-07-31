import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const MODULE_ROOT = fileURLToPath(new URL(".", import.meta.url));
const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

function collectFiles(directory: string): string[] {
	return readdirSync(directory).flatMap((entry) => {
		const path = `${directory}/${entry}`;
		return statSync(path).isDirectory() ? collectFiles(path) : [path];
	});
}

function collectExternalLinks(node: unknown): string[] {
	if (!node || typeof node !== "object") return [];
	const record = node as Record<string, unknown>;
	const own = typeof record.externalLink === "string" ? [record.externalLink] : [];
	const children = Array.isArray(record.children) ? record.children.flatMap(collectExternalLinks) : [];
	return [...own, ...children];
}

describe("prototype-driven data-modeling architecture", () => {
	it("installs data modeling as a root menu and removes it from Data Studio", () => {
		const seed = JSON.parse(read("../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json")) as {
			portalNavSections: Array<Record<string, unknown>>;
		};
		const studio = seed.portalNavSections.find((node) => node.key === "studio");
		const modeling = seed.portalNavSections.find((node) => node.key === "modeling");
		expect(studio).toBeTruthy();
		expect(modeling).toMatchObject({ title: "数据建模" });
		expect((studio?.children as Array<Record<string, unknown>>).some((node) => node.key === "modeling")).toBe(false);
		expect((modeling?.children as Array<Record<string, unknown>>).map((node) => node.title)).toEqual([
			"建模概览",
			"数仓规划",
			"数据标准",
			"维度建模",
			"数据指标",
			"通用工具",
			"关系图",
		]);
		expect((modeling?.children as Array<Record<string, unknown>>)[0]).toMatchObject({
			key: "modeling-home-workspace",
			externalLink: "/data-modeling/home/workspace",
		});
		expect(JSON.stringify(modeling)).not.toMatch(/modeling-home-(?:recent|tasks)|"title":"首页"/);

		const links = collectExternalLinks(modeling);
		expect(links).toHaveLength(27);
		expect(links.every((link) => link.startsWith("/data-modeling/"))).toBe(true);
		expect(links).not.toContain("/data-modeling/home/recent");
		expect(links).not.toContain("/data-modeling/home/tasks");
	});

	it("routes all new pages to the prototype React owner and old pages to a redirect only", () => {
		const staticRoutes = read("../../routes/sections/dashboard/static-routes.tsx");
		const dynamicResolver = read("../../routes/sections/dashboard/dynamic-resolver.tsx");
		expect(staticRoutes).toContain('path: "data-modeling/*"');
		expect(staticRoutes).toContain('path: "modeling/*"');
		expect(staticRoutes).toContain('path: "studio/modeling/*"');
		expect(staticRoutes).toContain("<DataModelingPage />");
		expect(staticRoutes).not.toContain("@/pages/modeling/");
		expect(dynamicResolver).toContain('normalized.startsWith("/data-modeling/")');
		expect(dynamicResolver).toContain("/pages/data-modeling/LegacyDataModelingRedirect");
		expect(dynamicResolver).not.toContain("/pages/modeling/");
	});

	it("physically retires the old page directory after shared contracts are moved", () => {
		expect(existsSync(new URL("../modeling", import.meta.url))).toBe(false);
		expect(existsSync(new URL("../../features/modeling/contracts/modelSpecV2Contract.ts", import.meta.url))).toBe(true);
		expect(existsSync(new URL("../../features/modeling/navigation/warehousePlanViewModel.ts", import.meta.url))).toBe(
			true,
		);
	});

	it("keeps the new UI front-end-only, fail-closed, and within the file-size gate", () => {
		const productionFiles = collectFiles(MODULE_ROOT).filter(
			(file) => /\.(tsx?|css)$/.test(file) && !/(\.test|source-contract\.test)\./.test(file),
		);
		const source = productionFiles.map((file) => readFileSync(file, "utf8")).join("\n");
		expect(source).not.toMatch(/from ["']@\/api\//);
		expect(source).not.toMatch(/\bfetch\s*\(/);
		expect(source).not.toMatch(/\baxios\b/);
		expect(source).not.toMatch(/message\.success|notification\.success/);
		expect(source).not.toMatch(/xzmfly|@163\.com|Xieha/);
		expect(source).toContain("BackendPendingButton");

		for (const file of productionFiles) {
			const lines = readFileSync(file, "utf8").split(/\r?\n/).length;
			expect(lines, `${file} exceeds the 800-line UI gate`).toBeLessThanOrEqual(800);
		}
	});
});
