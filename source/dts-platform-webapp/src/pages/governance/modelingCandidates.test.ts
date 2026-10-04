import assert from "node:assert/strict";
import test from "node:test";
import { confirmedDimensions, confirmedProcesses, pendingCandidateCount } from "./modelingCandidates.ts";

const processes = [
	{
		version: 1,
		processId: "manual-process",
		domainId: "domain-1",
		name: "人工过程",
		sourceType: "MANUAL",
		confirmed: true,
	},
	{
		version: 1,
		processId: "template-process",
		domainId: "domain-1",
		name: "模板候选过程",
		sourceType: "TEMPLATE",
		sourceId: "sample-pack",
		confirmed: false,
	},
];

const dimensions = [
	{
		dimensionId: "manual-dimension",
		name: "人工维度",
		domainIds: ["domain-1"],
		sourceType: "MANUAL",
		confirmed: true,
	},
	{
		dimensionId: "template-dimension",
		name: "模板候选维度",
		domainIds: ["domain-1"],
		sourceType: "TEMPLATE",
		sourceId: "sample-pack",
		confirmed: false,
	},
];

test("only confirmed modeling facts enter downstream process and dimension choices", () => {
	assert.deepEqual(
		confirmedProcesses(processes).map((item) => item.processId),
		["manual-process"],
	);
	assert.deepEqual(
		confirmedDimensions(dimensions).map((item) => item.dimensionId),
		["manual-dimension"],
	);
});

test("pending candidate count combines unconfirmed processes and dimensions", () => {
	assert.equal(pendingCandidateCount(processes, dimensions), 2);
});
