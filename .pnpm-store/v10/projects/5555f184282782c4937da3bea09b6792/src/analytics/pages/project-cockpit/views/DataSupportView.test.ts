import assert from "node:assert/strict";
import test from "node:test";
import { buildDataSupportSnapshot } from "./dataSupportView.helpers";

test("buildDataSupportSnapshot counts missing items and identifies last source", () => {
	const snapshot = buildDataSupportSnapshot(
		"2026-03-15",
		{
			batchId: "batch-20260316-001",
			status: "MODELED",
			issueRows: 164,
		},
		{
			issueCount: 12,
			unmappedSubprojectCount: 4,
			unknownDelayReasonCount: 9,
		},
		[
			{ label: "有效行数", value: "1836 / 2000" },
			{ label: "覆盖项目", value: "5 / 5" },
		],
		[
			{ id: "missing-major-mapping", status: "待补充", title: "项目正式映射表" },
			{ id: "missing-resource-load", status: "待客户补充", title: "资源投入与工时" },
		],
		[
			{ name: "project-cockpit-test-batch-2000.xlsx", description: "项目主体域上传批次" },
			{ name: "biz_dwd_project_node_enriched", description: "项目主体域正式 DWD 节点明细" },
		],
	);

	assert.equal(snapshot.lastUpdatedAt, "2026-03-15");
	assert.equal(snapshot.coverageCount, 2);
	assert.equal(snapshot.pendingChecklistCount, 2);
	assert.equal(snapshot.latestSourceName, "biz_dwd_project_node_enriched");
	assert.equal(snapshot.batchId, "batch-20260316-001");
	assert.equal(snapshot.batchStatus, "MODELED");
	assert.equal(snapshot.issueCount, 12);
	assert.equal(snapshot.unmappedSubprojectCount, 4);
	assert.equal(snapshot.unknownDelayReasonCount, 9);
});
