import assert from "node:assert/strict";
import test from "node:test";

import { buildPjmGoldenPathFixture } from "./pjmModelingFixture.test-support.ts";

test("PJM golden path binds DWD to DWS to ADS and keeps legacy refs read-only", () => {
	const fixture = buildPjmGoldenPathFixture();
	const models = new Map(fixture.modelSpecs.map((model) => [model.id, model]));

	assert.deepEqual([...models.keys()], ["pjm-project-node-dwd", "pjm-project-node-dws", "pjm-project-node-ads"]);
	assert.deepEqual(models.get("pjm-project-node-dws")?.dependsOn, ["pjm-project-node-dwd"]);
	assert.deepEqual(models.get("pjm-project-node-ads")?.dependsOn, ["pjm-project-node-dws"]);
	assert.equal(fixture.legacyRefs[0]?.status, "LEGACY_READONLY");
	assert.equal(fixture.legacyRefs[0]?.dbtUniqueId, "model.pm_analytics_v3.biz_dwd_project_node_v2");
});
