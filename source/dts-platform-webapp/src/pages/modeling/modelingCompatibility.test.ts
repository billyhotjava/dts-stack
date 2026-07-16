import assert from "node:assert/strict";
import test from "node:test";

import { canEditLegacyAsset, registerLegacyDbtModel } from "./modelingCompatibility.ts";

test("legacy dbt imports are registered read-only and keep the semantic API route", () => {
	const asset = registerLegacyDbtModel({
		modelId: "pjm-project-node-dwd",
		dbtUniqueId: "model.pm_analytics_v3.biz_dwd_project_node_v2",
		path: "models/dwd/biz_dwd_project_node_v2.sql",
	});

	assert.equal(asset.status, "LEGACY_READONLY");
	assert.equal(asset.apiRoute, "/api/semantic/models");
	assert.equal(canEditLegacyAsset(asset), false);
});
