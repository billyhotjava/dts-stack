import assert from "node:assert/strict";
import test from "node:test";
import { buildExecutionSnapshot } from "./executionView.helpers";

test("buildExecutionSnapshot summarizes overdue work, milestones and busiest dept", () => {
	const snapshot = buildExecutionSnapshot(
		[
			{ name: "关键算法验证", isOverdue: true, delayDays: 15, majorProjectName: "苍穹导航综合工程" },
			{ name: "热真空试验", isOverdue: false, delayDays: 0, majorProjectName: "龙眼光电探测工程" },
			{ name: "总装联调", isOverdue: true, delayDays: 7, majorProjectName: "苍穹导航综合工程" },
		],
		[
			{ name: "设计冻结", status: "计划中", planDate: "2026-03-20" },
			{ name: "联调验收", status: "延期中", planDate: "2026-04-12" },
		],
		[
			{ name: "关键算法验证", dept: "导航室", delayDays: 15 },
			{ name: "总装联调", dept: "总装室", delayDays: 7 },
		],
		[
			{ dept: "导航室", activeCount: 6, overdueCount: 2, highRiskCount: 2 },
			{ dept: "总装室", activeCount: 3, overdueCount: 1, highRiskCount: 1 },
		],
	);

	assert.equal(snapshot.overdueTaskCount, 2);
	assert.equal(snapshot.maxDelayDays, 15);
	assert.equal(snapshot.nextMilestoneName, "设计冻结");
	assert.equal(snapshot.busiestDept, "导航室");
});
