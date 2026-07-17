import assert from "node:assert/strict";
import test from "node:test";
import {
	BUSINESS_PROCESS_STORAGE_KEY,
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

test("saving one domain keeps manually created processes in other domains", () => {
	const storage = memoryStorage();
	const first = createBusinessProcess({ domainId: "domain-1", processId: "process-one", name: "过程一" });
	const second = createBusinessProcess({ domainId: "domain-2", processId: "process-two", name: "过程二" });

	assert.equal(saveBusinessProcesses("domain-1", [first], storage), true);
	assert.equal(saveBusinessProcesses("domain-2", [second], storage), true);
	assert.deepEqual(loadBusinessProcesses("domain-1", storage), [first]);
	assert.deepEqual(loadBusinessProcesses("domain-2", storage), [second]);
});

test("draft storage preserves modeling provenance without promoting a candidate", () => {
	const storage = memoryStorage();
	const candidate = {
		...createBusinessProcess({ domainId: "domain-1", processId: "template-process", name: "模板候选过程" }),
		sourceType: "TEMPLATE",
		sourceId: "sample-pack",
		sourceVersion: "1.0.0",
		confirmed: false,
	};

	assert.equal(saveBusinessProcesses("domain-1", [candidate], storage), true);
	assert.deepEqual(loadBusinessProcesses("domain-1", storage), [candidate]);
});
