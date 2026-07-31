import assert from "node:assert/strict";
import test from "node:test";
import { buildHelpTopicHref, HELP_TOPICS, resolveHelpTopic } from "./helpTopics.ts";

test("covers the DTS product areas from onboarding through administration", () => {
	assert.deepEqual(
		HELP_TOPICS.map((topic) => topic.id),
		[
			"overview",
			"workbench",
			"data-integration",
			"data-modeling",
			"construction-planning",
			"model-center",
			"sql-modeling",
			"metric-workbench",
			"governance",
			"assets",
			"quality-security-lineage",
			"metrics-bi",
			"services-products",
			"operations",
			"administration",
		],
	);
});

test("resolves the most specific route topic and falls back to overview", () => {
	assert.equal(resolveHelpTopic("/data-modeling/home/workspace").id, "data-modeling");
	assert.equal(resolveHelpTopic("/data-modeling/planning/domains").id, "construction-planning");
	assert.equal(resolveHelpTopic("/data-modeling/dimensions/workbench").id, "model-center");
	assert.equal(resolveHelpTopic("/data-modeling/metrics/atomic").id, "metric-workbench");
	assert.equal(resolveHelpTopic("/data-modeling/standards/fields").id, "governance");
	assert.equal(resolveHelpTopic("/catalog/lineage").id, "quality-security-lineage");
	assert.equal(resolveHelpTopic("/unknown-page").id, "overview");
});

test("honors a requested full-help topic and builds a stable deep link", () => {
	assert.equal(resolveHelpTopic("/settings/help", "operations").id, "operations");
	assert.equal(resolveHelpTopic("/settings/help", "not-found").id, "overview");
	assert.equal(buildHelpTopicHref("data-modeling"), "/settings/help?topic=data-modeling");
});

test("keeps the topic registry within the local-content budget", () => {
	const topicIds = new Set(HELP_TOPICS.map((topic) => topic.id));
	assert.ok(HELP_TOPICS.length <= 20);
	assert.equal(topicIds.size, HELP_TOPICS.length);
	for (const topic of HELP_TOPICS) {
		for (const relatedTopicId of topic.relatedTopicIds) {
			assert.ok(topicIds.has(relatedTopicId), `${topic.id} references missing topic ${relatedTopicId}`);
		}
	}
});
