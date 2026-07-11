import assert from "node:assert/strict";
import test from "node:test";
import {
	BUSINESS_PROCESS_STORAGE_KEY,
	BUSINESS_PROCESS_SEEDS,
	adoptBusinessProcessSeeds,
	createBusinessProcess,
	loadBusinessProcesses,
	saveBusinessProcesses,
} from "./businessProcess.ts";

const memoryStorage = () => {
	const values = new Map<string, string>();
	return {
		getItem: (key: string) => values.get(key) ?? null,
		setItem: (key: string, value: string) => values.set(key, value),
		removeItem: (key: string) => values.delete(key),
	};
};

test("business process creates a stable versioned draft", () => {
	const process = createBusinessProcess(
		{ domainId: "domain-1", processId: "quality-zero", name: "质量问题归零" },
		() => "2026-07-11T00:00:00.000Z",
	);

	assert.deepEqual(process, {
		version: 1,
		processId: "quality-zero",
		domainId: "domain-1",
		name: "质量问题归零",
		description: undefined,
		createdAt: "2026-07-11T00:00:00.000Z",
		updatedAt: "2026-07-11T00:00:00.000Z",
	});
});

test("business process storage is domain-scoped and restores versioned drafts", () => {
	const storage = memoryStorage();
	const process = createBusinessProcess({ domainId: "domain-1", processId: "risk-release", name: "风险提出与释放" });

	assert.equal(saveBusinessProcesses("domain-1", [process], storage), true);
	assert.deepEqual(loadBusinessProcesses("domain-1", storage), [process]);
	assert.deepEqual(loadBusinessProcesses("domain-2", storage), []);
	assert.ok(storage.getItem(BUSINESS_PROCESS_STORAGE_KEY));
});

test("seed adoption is idempotent and keeps user-created processes", () => {
	const storage = memoryStorage();
	const custom = createBusinessProcess({ domainId: "domain-1", processId: "custom", name: "自定义过程" });

	const first = adoptBusinessProcessSeeds("domain-1", [custom], storage);
	const second = adoptBusinessProcessSeeds("domain-1", first, storage);

	assert.equal(first.length, BUSINESS_PROCESS_SEEDS.length + 1);
	assert.equal(second.length, first.length);
	assert.ok(second.some((item) => item.processId === "custom"));
});
