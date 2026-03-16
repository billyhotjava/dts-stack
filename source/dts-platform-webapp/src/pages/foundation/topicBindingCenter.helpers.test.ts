import assert from "node:assert/strict";
import test from "node:test";
import {
	buildTopicTemplateSummaries,
	selectPreferredTopicSource,
	type TopicSourceCandidate,
} from "./topicBindingCenter.helpers";
import type { TopicBindingDiagnostics, TopicBindingTemplateView } from "@/api/services/topicBindingService";

test("buildTopicTemplateSummaries merges templates with diagnostics rows and missing counts", () => {
	const templates: TopicBindingTemplateView[] = [
		{ templateCode: "project-management", templateName: "项目管理专题", description: "", status: "ACTIVE", bindingScope: "GLOBAL", enabled: true },
		{ templateCode: "plm-overview", templateName: "PLM 专题", description: "", status: "ACTIVE", bindingScope: "GLOBAL", enabled: true },
	];
	const diagnostics: TopicBindingDiagnostics = {
		selector: undefined,
		relevantTemplateCodes: ["project-management", "plm-overview"],
		missingRequired: ["plm-overview.plm_change_domain"],
		rows: [
			{
				templateCode: "project-management",
				templateName: "项目管理专题",
				entityCode: "project_subject_domain",
				entityName: "项目主体域",
				required: true,
				sourceName: "pm_ods",
				logicalTableName: "project_subject_domain",
				expectedSchema: "ods",
				bound: true,
				boundSchemaName: "ods",
				boundTableName: "pm_upload_20260316",
				bindingStatus: "ACTIVE",
			},
			{
				templateCode: "plm-overview",
				templateName: "PLM 专题",
				entityCode: "plm_bom_domain",
				entityName: "PLM BOM 域",
				required: true,
				sourceName: "plm_ods",
				logicalTableName: "plm_bom_domain",
				expectedSchema: "ods",
				bound: true,
				boundSchemaName: "ods",
				boundTableName: "plm_bom_20260316",
				bindingStatus: "ACTIVE",
			},
			{
				templateCode: "plm-overview",
				templateName: "PLM 专题",
				entityCode: "plm_change_domain",
				entityName: "PLM 变更域",
				required: true,
				sourceName: "plm_ods",
				logicalTableName: "plm_change_domain",
				expectedSchema: "ods",
				bound: false,
			},
		],
	};

	const result = buildTopicTemplateSummaries(templates, diagnostics);

	assert.equal(result[0]?.templateCode, "project-management");
	assert.equal(result[0]?.requiredCount, 1);
	assert.equal(result[0]?.boundCount, 1);
	assert.equal(result[0]?.missingRequiredCount, 0);
	assert.equal(result[1]?.templateCode, "plm-overview");
	assert.equal(result[1]?.requiredCount, 2);
	assert.equal(result[1]?.boundCount, 1);
	assert.equal(result[1]?.missingRequiredCount, 1);
});

test("selectPreferredTopicSource prefers matching data source id and falls back to first option", () => {
	const sources: TopicSourceCandidate[] = [
		{ schema: "ods", table: "pm_upload_a", sourceDataSourceId: "ds-1", sourceDataSourceName: "数据湖 A" },
		{ schema: "ods", table: "pm_upload_b", sourceDataSourceId: "ds-2", sourceDataSourceName: "数据湖 B" },
	];

	assert.equal(selectPreferredTopicSource(sources, "ds-2")?.table, "pm_upload_b");
	assert.equal(selectPreferredTopicSource(sources, "missing")?.table, "pm_upload_a");
	assert.equal(selectPreferredTopicSource([], "ds-1"), undefined);
});
