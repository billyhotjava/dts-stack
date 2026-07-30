import assert from "node:assert/strict";
import test from "node:test";
import { buildScreenPayload, validateScreenPayload } from "./screenSpec.ts";
import type { DrillLevel, ScreenComponent, ScreenConfig } from "./types.ts";

function screenWithDrillLevel(level: DrillLevel) {
	const config: ScreenConfig = {
		id: "draft",
		name: "Neutral drill fixture",
		width: 1920,
		height: 1080,
		backgroundColor: "#08121f",
		components: [
			{
				id: "chart-1",
				type: "bar-chart",
				name: "Neutral chart",
				x: 0,
				y: 0,
				width: 640,
				height: 360,
				zIndex: 1,
				locked: false,
				visible: true,
				config: {},
				dataSource: {
					type: "sql",
					sqlConfig: { databaseId: 1, query: "select 1" },
				},
				drillDown: { enabled: true, levels: [level] },
			},
		],
	};
	return buildScreenPayload(config);
}

test("accepts a generic drill level", () => {
	const payload = screenWithDrillLevel({
		label: "Level 1",
		dataSource: {
			type: "sql",
			sqlConfig: { databaseId: 1, query: "select 1" },
		},
		mappings: [{ sourcePath: "data.key", variableKey: "selectedKey", transform: "string" }],
		inheritContext: true,
	});

	assert.deepEqual(validateScreenPayload(payload).errors, []);
});

test("accepts a legacy card drill level", () => {
	const payload = screenWithDrillLevel({
		cardId: 12,
		paramName: "selectedKey",
		label: "Level 1",
	});

	assert.deepEqual(validateScreenPayload(payload).errors, []);
});

test("rejects a generic drill level without a target or mappings", () => {
	const payload = screenWithDrillLevel({ label: "Level 1" });

	assert.match(validateScreenPayload(payload).errors.join("\n"), /drillDown\.levels\[0\]/);
});

test("rejects duplicate target parameters in one drill level", () => {
	const payload = screenWithDrillLevel({
		label: "Level 1",
		dataSource: {
			type: "api",
			apiConfig: { url: "/example", method: "GET" },
		},
		mappings: [
			{ sourcePath: "data.key", variableKey: "selectedKey", transform: "string" },
			{ sourcePath: "data.parent", variableKey: "selectedKey", transform: "string" },
		],
	});

	assert.match(validateScreenPayload(payload).errors.join("\n"), /variableKey 重复: selectedKey/);
});

test("validates drill-view targets through the existing action contract", () => {
	const payload = screenWithDrillLevel({ cardId: 12, paramName: "selectedKey", label: "Legacy" });
	const component = (payload.components as ScreenComponent[])[0];
	component.actions = [
		{
			type: "drill-view",
			drillViewId: "detail-view",
			drillViewLabel: "明细",
			mappings: [{ sourcePath: "data.key", variableKey: "selectedKey", transform: "string" }],
		},
	];
	assert.deepEqual(validateScreenPayload(payload).errors, []);

	component.actions[0].drillViewId = "";
	assert.match(validateScreenPayload(payload).errors.join("\n"), /drillViewId 不能为空/);
});

test("validates generic drill configuration inside a screen page", () => {
	const payload = screenWithDrillLevel({ cardId: 12, paramName: "selectedKey", label: "Legacy" });
	const component = (payload.components as ScreenComponent[])[0];
	payload.components = [];
	payload.pages = [{ id: "page-1", name: "Page 1", components: [component] }];
	component.drillDown = {
		enabled: true,
		levels: [
			{
				label: "Level 1",
				dataSource: { type: "api", apiConfig: { url: "/example", method: "GET" } },
				mappings: [],
			},
		],
	};

	assert.match(validateScreenPayload(payload).errors.join("\n"), /pages\[0\]\.components\[0\]\.drillDown\.levels\[0\]/);
});

test("does not report the same invalid mirrored component twice", () => {
	const payload = screenWithDrillLevel({});
	const component = (payload.components as ScreenComponent[])[0];
	const mirroredComponent = Object.fromEntries(Object.entries(component).reverse()) as unknown as ScreenComponent;
	payload.pages = [{ id: "page-1", name: "Page 1", components: [mirroredComponent] }];

	const errors = validateScreenPayload(payload).errors;

	assert.equal(errors.length, 2);
	assert.ok(errors.every((error) => error.startsWith("components[0].drillDown.levels[0]")));
});
