import assert from "node:assert/strict";
import test from "node:test";
import { buildOverviewTrendSnapshot } from "./overviewTrendView.helpers";

test("buildOverviewTrendSnapshot summarizes ranking, alerts and trend points", () => {
	const snapshot = buildOverviewTrendSnapshot(
		[
			{ key: "majorProjectCount", label: "重大项目数", value: "4", unit: "个" },
			{ key: "highRiskNodeCount", label: "高风险节点数", value: "6", unit: "个" },
		],
		[
			{ majorProjectName: "苍穹导航综合工程", healthScore: 68.2, highRiskCount: 2, overdueCount: 3 },
			{ majorProjectName: "龙眼光电探测工程", healthScore: 74.5, highRiskCount: 1, overdueCount: 2 },
		],
		[
			{ title: "关键算法验证", riskLevel: "高", delayDays: 15 },
			{ title: "光机装调", riskLevel: "高", delayDays: 10 },
		],
		[
			{ weekLabel: "2026-02-23周", completionRate: 62.5, delayedNodes: 3, highRiskNodes: 2 },
			{ weekLabel: "2026-03-02周", completionRate: 66.7, delayedNodes: 4, highRiskNodes: 2 },
		],
	);

	assert.equal(snapshot.headlineRiskLabel, "高风险节点数");
	assert.equal(snapshot.topProjectName, "苍穹导航综合工程");
	assert.equal(snapshot.alertCount, 2);
	assert.equal(snapshot.lastWeekLabel, "2026-03-02周");
});
