import { describe, expect, it } from "vitest";
import { dataModelingPath, resolveDataModelingRoute } from "./navigation";

const EXPECTED_ROUTES = [
	["home", "workspace"],
	["planning", "business-categories"],
	["planning", "layers"],
	["planning", "domains"],
	["planning", "processes"],
	["planning", "marts"],
	["planning", "subjects"],
	["planning", "spaces"],
	["planning", "system"],
	["standards", "fields"],
	["standards", "codes"],
	["standards", "roots"],
	["standards", "dictionary"],
	["standards", "mappings"],
	["dimensions", "workbench"],
	["dimensions", "reverse"],
	["metrics", "composite"],
	["metrics", "derived"],
	["metrics", "atomic"],
	["metrics", "modifiers"],
	["metrics", "periods"],
	["tools", "toolbox"],
	["tools", "imports"],
	["tools", "exports"],
	["graphs", "models"],
	["graphs", "standards"],
	["graphs", "metrics"],
] as const;

describe("data-modeling navigation", () => {
	it.each(EXPECTED_ROUTES)("resolves %s/%s as a refresh-safe route", (workspace, view) => {
		const path = dataModelingPath(workspace, view);
		expect(resolveDataModelingRoute(path)).toMatchObject({ workspace, view });
	});

	it("falls back to the workspace default for an unknown leaf", () => {
		expect(resolveDataModelingRoute("/data-modeling/metrics/not-real")).toMatchObject({
			workspace: "metrics",
			view: "atomic",
		});
	});

	it("falls back to home for an unknown workspace", () => {
		expect(resolveDataModelingRoute("/data-modeling/not-real")).toMatchObject({
			workspace: "home",
			view: "workspace",
			title: "建模概览",
		});
	});

	it.each(["recent", "tasks"])("retires home/%s into the modeling overview", (view) => {
		expect(resolveDataModelingRoute(`/data-modeling/home/${view}`)).toMatchObject({
			workspace: "home",
			view: "workspace",
			title: "建模概览",
		});
	});

	it("keeps the read-only planning routes honest", () => {
		expect(resolveDataModelingRoute("/data-modeling/planning/layers")).toMatchObject({
			workspace: "planning",
			view: "layers",
			title: "数仓分层",
			description: "查看平台内置的贴源、公共和应用层统一分层规范。",
		});
		expect(resolveDataModelingRoute("/data-modeling/planning/system")).toMatchObject({
			workspace: "planning",
			view: "system",
			title: "规划参数配置",
			description: "说明数仓规划参数的当前能力边界；当前版本暂不提供在线维护。",
		});
	});
});
