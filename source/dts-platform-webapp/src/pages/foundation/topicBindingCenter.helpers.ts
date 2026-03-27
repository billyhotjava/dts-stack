import type {
	TopicBindingDiagnostics,
	TopicBindingRow,
	TopicBindingTemplateView,
} from "@/api/services/topicBindingService";

export type TopicTemplateSummary = TopicBindingTemplateView & {
	rows: TopicBindingRow[];
	requiredCount: number;
	boundCount: number;
	missingRequiredCount: number;
};

import { normalizeText } from "@/utils/textUtils";

export type TopicSourceCandidate = {
	id?: string;
	schema?: string;
	table?: string;
	description?: string;
	systemCode?: string;
	bizCode?: string;
	entityCode?: string;
	sourceDataSourceId?: string;
	sourceDataSourceName?: string;
	sourceSnippet?: string;
};

export function buildTopicTemplateSummaries(
	templates: TopicBindingTemplateView[],
	diagnostics: TopicBindingDiagnostics | null,
): TopicTemplateSummary[] {
	const rows = Array.isArray(diagnostics?.rows) ? diagnostics.rows : [];
	const missing = new Set(Array.isArray(diagnostics?.missingRequired) ? diagnostics.missingRequired : []);

	return templates.map((template) => {
		const templateRows = rows.filter((row) => row.templateCode === template.templateCode);
		const requiredCount = templateRows.filter((row) => row.required).length;
		const boundCount = templateRows.filter((row) => row.bound).length;
		const missingRequiredCount = templateRows.filter((row) => missing.has(`${template.templateCode}.${row.entityCode}`)).length;
		return {
			...template,
			rows: templateRows,
			requiredCount,
			boundCount,
			missingRequiredCount,
		};
	});
}

export function selectPreferredTopicSource(
	sources: TopicSourceCandidate[],
	preferredDataSourceId?: string,
): TopicSourceCandidate | undefined {
	if (!Array.isArray(sources) || sources.length === 0) {
		return undefined;
	}
	const preferred = normalizeText(preferredDataSourceId);
	if (preferred) {
		const matched = sources.find((item) => normalizeText(item.sourceDataSourceId) === preferred);
		if (matched) {
			return matched;
		}
	}
	return sources[0];
}
