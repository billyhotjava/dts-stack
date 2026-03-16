import assert from "node:assert/strict";
import test from "node:test";
import type { TopicBindingDiagnostics, TopicBindingTemplateView } from "@/api/services/topicBindingService";
import { selectRecommendedProjectCockpitBinding } from "./projectCockpitImportBinding.helpers";

test("selectRecommendedProjectCockpitBinding prefers project-management project_subject_domain", () => {
	const templates: TopicBindingTemplateView[] = [
		{
			templateCode: "project-management",
			templateName: "项目管理",
			bindingScope: "GLOBAL",
			status: "ACTIVE",
		},
	];
	const diagnostics: TopicBindingDiagnostics = {
		selector: "all",
		relevantTemplateCodes: ["project-management"],
		rows: [
			{
				templateCode: "project-management",
				templateName: "项目管理",
				entityCode: "project_subject_domain",
				entityName: "项目主体域",
				required: true,
				sourceName: "pm_ods",
				logicalTableName: "project_subject_domain",
				expectedSchema: "ods",
				bound: false,
			},
		],
		missingRequired: ["project-management.project_subject_domain"],
	};

	assert.deepEqual(selectRecommendedProjectCockpitBinding(templates, diagnostics), {
		templateCode: "project-management",
		entityCode: "project_subject_domain",
		entityName: "项目主体域",
	});
});

test("selectRecommendedProjectCockpitBinding falls back to the first missing required binding", () => {
	const templates: TopicBindingTemplateView[] = [
		{
			templateCode: "plm-overview",
			templateName: "PLM 总览",
			bindingScope: "GLOBAL",
			status: "ACTIVE",
		},
	];
	const diagnostics: TopicBindingDiagnostics = {
		selector: "all",
		relevantTemplateCodes: ["plm-overview"],
		rows: [
			{
				templateCode: "plm-overview",
				templateName: "PLM 总览",
				entityCode: "plm_bom_domain",
				entityName: "BOM 主体域",
				required: true,
				sourceName: "plm_ods",
				logicalTableName: "plm_bom_domain",
				expectedSchema: "ods",
				bound: false,
			},
		],
		missingRequired: ["plm-overview.plm_bom_domain"],
	};

	assert.deepEqual(selectRecommendedProjectCockpitBinding(templates, diagnostics), {
		templateCode: "plm-overview",
		entityCode: "plm_bom_domain",
		entityName: "BOM 主体域",
	});
});

test("selectRecommendedProjectCockpitBinding returns undefined when no topic rows are available", () => {
	assert.equal(selectRecommendedProjectCockpitBinding([], null), undefined);
});
