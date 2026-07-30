import assert from "node:assert/strict";
import test from "node:test";
import {
	buildMetricWorkbenchViewLocation,
	metricViewFromWorkspace,
	resolveMetricWorkbenchView,
	workspaceViewFromMetric,
} from "./metricWorkbenchNavigation.ts";

test("resolves one workbench task at a time and opens model context in the model task", () => {
	assert.equal(resolveMetricWorkbenchView(""), "definition");
	assert.equal(resolveMetricWorkbenchView("?view=templates"), "templates");
	assert.equal(resolveMetricWorkbenchView("?view=consumption"), "consumption");
	assert.equal(resolveMetricWorkbenchView("?modelSpecId=model-1"), "model");
	assert.equal(resolveMetricWorkbenchView("?view=unknown&modelSpecId=model-1"), "model");
});

test("builds a deep link without dropping journey context", () => {
	assert.equal(
		buildMetricWorkbenchViewLocation(
			"model",
			"?journey=low-code-development&modelSpecId=model-1&view=definition",
			"#field-amount",
		),
		"/modeling/metric-workbench?journey=low-code-development&modelSpecId=model-1&view=model#field-amount",
	);
});

test("maps the four canonical metric tasks to workspace-owned views", () => {
	assert.equal(metricViewFromWorkspace("definitions"), "definition");
	assert.equal(metricViewFromWorkspace("model"), "model");
	assert.equal(metricViewFromWorkspace("templates"), "templates");
	assert.equal(metricViewFromWorkspace("consumption"), "consumption");
	assert.equal(metricViewFromWorkspace("overview"), "definition");

	assert.equal(workspaceViewFromMetric("definition"), "definitions");
	assert.equal(workspaceViewFromMetric("model"), "model");
	assert.equal(workspaceViewFromMetric("templates"), "templates");
	assert.equal(workspaceViewFromMetric("consumption"), "consumption");
});
