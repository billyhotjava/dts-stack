import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (relativePath: string) => readFileSync(new URL(relativePath, import.meta.url), "utf8");

const workflowSource = read("./WorkflowCenterPage.tsx");
const dataManagementSource = read("./DataManagementWorkbenchPage.tsx");
const leaderSource = read("./LeaderOverviewPage.tsx");
const filterSource = read("./components/WorkbenchFilterBar.tsx");
const domainSelectSource = read("./components/BizDomainSelect.tsx");
const coreAssetsSource = read("./components/CoreAssetsBlock.tsx");
const topReportsSource = read("./components/TopReportsBlock.tsx");

test("workbench todo enums and timestamps use customer-facing Chinese labels", () => {
	assert.match(workflowSource, /todoTypeLabel\(value\)/);
	assert.match(workflowSource, /statusLabel\(value\)/);
	assert.match(workflowSource, /formatTime\(value\)/);
	assert.doesNotMatch(workflowSource, /\{value \|\| "TODO"\}/);
	assert.doesNotMatch(workflowSource, /date\.toLocaleString\(\)/);
	assert.match(dataManagementSource, /chain\.currentStageLabel \|\| statusLabel\(chain\.status\)/);
});

test("workbench asset domain codes resolve through configured catalog names", () => {
	assert.match(domainSelectSource, /domainLabelsRef\.current\?\.\(labels\)/);
	assert.match(filterSource, /onDomainLabelsChange=\{onDomainLabelsChange\}/);
	assert.match(leaderSource, /domainLabels=\{domainLabels\}/);
	assert.match(coreAssetsSource, /humanizeBizDomain\(a\.bizDomain, domainLabels\)/);
	assert.match(topReportsSource, /humanizeBizDomain\(r\.bizDomain, domainLabels\)/);
	assert.match(read("./hooks/bizDomain.ts"), /domainLabels\[trimmed\] \?\? "未命名业务域"/);
});
