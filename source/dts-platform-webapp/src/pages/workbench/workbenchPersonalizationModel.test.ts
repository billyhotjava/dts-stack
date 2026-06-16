import assert from "node:assert/strict";
import test from "node:test";
import type {
	WorkbenchComponentDescriptor,
	WorkbenchPreferenceItem,
} from "@/api/services/workbenchService";
import {
	moveWorkbenchPreferenceItem,
	normalizeWorkbenchPreferenceItems,
} from "./workbenchPersonalizationModel.ts";

const availableComponents: WorkbenchComponentDescriptor[] = [
	{ key: "leader-kpi", title: "概览指标", description: "指标", enabled: true },
	{ key: "todo", title: "待办事项", description: "待办", enabled: true },
	{ key: "ops-health", title: "运行健康", description: "运行", enabled: true },
];

test("normalizeWorkbenchPreferenceItems defaults all available components to visible", () => {
	const items = normalizeWorkbenchPreferenceItems(undefined, availableComponents);

	assert.deepEqual(items, [
		{ key: "leader-kpi", visible: true, order: 10 },
		{ key: "todo", visible: true, order: 20 },
		{ key: "ops-health", visible: true, order: 30 },
	]);
});

test("normalizeWorkbenchPreferenceItems preserves saved visibility and normalizes order", () => {
	const saved: WorkbenchPreferenceItem[] = [
		{ key: "ops-health", visible: false, order: 5 },
		{ key: "leader-kpi", visible: true, order: 30 },
	];

	const items = normalizeWorkbenchPreferenceItems(saved, availableComponents);

	assert.deepEqual(items, [
		{ key: "ops-health", visible: false, order: 10 },
		{ key: "leader-kpi", visible: true, order: 20 },
		{ key: "todo", visible: false, order: 30 },
	]);
});

test("moveWorkbenchPreferenceItem swaps adjacent items and reassigns stable order numbers", () => {
	const items: WorkbenchPreferenceItem[] = [
		{ key: "leader-kpi", visible: true, order: 10 },
		{ key: "todo", visible: true, order: 20 },
		{ key: "ops-health", visible: true, order: 30 },
	];

	assert.deepEqual(moveWorkbenchPreferenceItem(items, "todo", -1), [
		{ key: "todo", visible: true, order: 10 },
		{ key: "leader-kpi", visible: true, order: 20 },
		{ key: "ops-health", visible: true, order: 30 },
	]);
	assert.deepEqual(moveWorkbenchPreferenceItem(items, "todo", 1), [
		{ key: "leader-kpi", visible: true, order: 10 },
		{ key: "ops-health", visible: true, order: 20 },
		{ key: "todo", visible: true, order: 30 },
	]);
});
