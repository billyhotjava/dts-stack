import type { TopicBindingDiagnostics, TopicBindingTemplateView } from "@/api/services/topicBindingService";

export type RecommendedTopicBinding = {
	templateCode: string;
	entityCode: string;
	entityName?: string;
};

const PROJECT_MANAGEMENT_TEMPLATE = "project-management";
const PROJECT_SUBJECT_ENTITY = "project_subject_domain";

const normalizeText = (value?: string | null) => String(value || "").trim().toLowerCase();

export function selectRecommendedProjectCockpitBinding(
	templates: TopicBindingTemplateView[],
	diagnostics: TopicBindingDiagnostics | null,
): RecommendedTopicBinding | undefined {
	const rows = Array.isArray(diagnostics?.rows) ? diagnostics.rows : [];
	const preferred = rows.find(
		(row) =>
			normalizeText(row.templateCode) === PROJECT_MANAGEMENT_TEMPLATE &&
			normalizeText(row.entityCode) === PROJECT_SUBJECT_ENTITY,
	);
	if (preferred) {
		return {
			templateCode: preferred.templateCode,
			entityCode: preferred.entityCode,
			entityName: preferred.entityName,
		};
	}

	const missing = new Set(Array.isArray(diagnostics?.missingRequired) ? diagnostics.missingRequired.map(normalizeText) : []);
	const firstMissing = rows.find((row) => missing.has(normalizeText(`${row.templateCode}.${row.entityCode}`)));
	if (firstMissing) {
		return {
			templateCode: firstMissing.templateCode,
			entityCode: firstMissing.entityCode,
			entityName: firstMissing.entityName,
		};
	}

	const firstTemplateCode = templates[0]?.templateCode;
	const firstRow = rows.find((row) => normalizeText(row.templateCode) === normalizeText(firstTemplateCode)) || rows[0];
	if (!firstRow) {
		return undefined;
	}
	return {
		templateCode: firstRow.templateCode,
		entityCode: firstRow.entityCode,
		entityName: firstRow.entityName,
	};
}
