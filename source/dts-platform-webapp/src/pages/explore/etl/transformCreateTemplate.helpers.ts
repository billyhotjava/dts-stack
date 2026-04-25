import type { IngestionTaskTemplateDTO, IngestionTemplateRenderDTO } from "@/api/ingestion";
import { normalizeText } from "@/utils/textUtils";

const normalizeSourceCategory = (value?: string): "file" | "database" | "api" | undefined => {
	const text = normalizeText(value).toLowerCase();
	if (text === "file" || text === "database" || text === "api") {
		return text;
	}
	return undefined;
};

type TemplateApplyOutcomeInput = {
	template: IngestionTaskTemplateDTO;
	currentSourceCategory?: string;
	renderResult?: IngestionTemplateRenderDTO | null;
	errorMessage?: string;
};

export type TemplateApplyOutcome = {
	defaults: Record<string, any>;
	infoMessage?: string;
	warningMessage?: string;
	errorMessage?: string;
	sourceCategory?: "file" | "database" | "api";
	successMessage: string;
};

export function buildTemplateRenderRequest(
	snapshot: Record<string, any>,
	selectedDataSourceId?: string,
	fileName?: string
): { params: Record<string, any>; strictRequired: false } {
	const params: Record<string, any> = {
		...snapshot,
		sourceDataSourceId: snapshot.sourceDataSourceId || selectedDataSourceId,
	};
	if (normalizeText(fileName)) {
		params.fileName = fileName;
	}
	return {
		params,
		strictRequired: false,
	};
}

export function resolveTemplateApplyOutcome(input: TemplateApplyOutcomeInput): TemplateApplyOutcome {
	const defaults = ((input.renderResult?.renderedDefaults || input.template.defaults || {}) as Record<string, any>) || {};
	const warnings = Array.isArray(input.renderResult?.warnings) ? input.renderResult?.warnings : input.template.warnings || [];
	const errors = Array.isArray(input.renderResult?.errors) ? input.renderResult.errors : [];
	const warningMessage = errors.length ? `模板参数待补：${errors.slice(0, 2).join("；")}` : undefined;
	const sourceCategory =
		normalizeSourceCategory(input.currentSourceCategory) ||
		normalizeSourceCategory(defaults.sourceCategory) ||
		normalizeSourceCategory(input.template.sourceCategory);

	return {
		defaults,
		infoMessage: warnings.length ? warnings[0] : undefined,
		warningMessage,
		errorMessage: input.errorMessage,
		sourceCategory,
		successMessage: `已应用模板：${input.template.name}`,
	};
}
