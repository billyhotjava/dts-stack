import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const OVERVIEW_SOURCE = readFileSync(new URL("./AssetGovernanceOverview.tsx", import.meta.url), "utf8");
const SUPPORT_TABS_SOURCE = readFileSync(new URL("./DatasetDetailSupportTabs.tsx", import.meta.url), "utf8");

test("missing governance quality evidence is never rendered as a numeric healthy score", () => {
	assert.doesNotMatch(OVERVIEW_SOURCE, /healthScore \?\? 0/);
	assert.match(OVERVIEW_SOURCE, /暂无治理质量证据/);
	assert.match(OVERVIEW_SOURCE, /evidenceState/);
	assert.match(SUPPORT_TABS_SOURCE, /暂无治理质量证据/);
});
