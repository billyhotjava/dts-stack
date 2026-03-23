import type { FileUploadResult, IngestionTaskDTO } from "@/api/ingestion";
import type { ExtraColumnDef } from "./steps/types";

type DraftPayload = {
	savedAt: string;
	[key: string]: any;
};

type EditRestoreDeps = {
	mapTaskToForm: (task: IngestionTaskDTO) => Record<string, any>;
	extractFileUploadResult: (task: IngestionTaskDTO) => FileUploadResult | null;
	extractMappingTables: (tableMapping: IngestionTaskDTO["tableMapping"]) => string[];
	tryParseJson: (raw?: unknown) => Record<string, any> | undefined;
};

export type ParsedTransformCreateDraft = {
	savedAt: string;
	formValues: Record<string, any>;
	sourceCategory?: string;
};

export type TransformEditRestoreState = {
	formValues: Record<string, any>;
	fileUploadResult: FileUploadResult | null;
	sourceCategory?: "file" | "database";
	forceReaderType?: string;
	extraColumns: ExtraColumnDef[];
	mappingTables: string[];
};

const normalizeText = (value?: string) => String(value || "").trim();

const normalizeSourceCategory = (value?: string): "file" | "database" | undefined => {
	const text = normalizeText(value).toLowerCase();
	if (text === "file" || text === "database") {
		return text;
	}
	return undefined;
};

export function parseTransformCreateDraft(raw: string | null): ParsedTransformCreateDraft | null {
	if (!raw) return null;
	try {
		const draft = JSON.parse(raw) as DraftPayload | null;
		if (!draft || typeof draft !== "object" || !normalizeText(draft.savedAt)) {
			return null;
		}
		const { savedAt, ...formValues } = draft;
		return {
			savedAt,
			formValues,
			sourceCategory: normalizeSourceCategory(formValues.sourceCategory),
		};
	} catch {
		return null;
	}
}

export function serializeTransformCreateDraft(values: Record<string, any>, savedAt: string = new Date().toISOString()): string {
	return JSON.stringify({ ...values, savedAt });
}

export function buildTransformEditRestoreState(
	task: IngestionTaskDTO,
	deps: EditRestoreDeps
): TransformEditRestoreState {
	const formValues = deps.mapTaskToForm(task);
	const fileUploadResult = deps.extractFileUploadResult(task);
	const destinationConfig = deps.tryParseJson(task.destinationConfig) || {};
	const extraColumns = Array.isArray(destinationConfig._extraColumns) ? destinationConfig._extraColumns : [];
	const mappingTables = deps.extractMappingTables(task.tableMapping);

	return {
		formValues,
		fileUploadResult,
		sourceCategory: fileUploadResult ? "file" : normalizeSourceCategory(formValues.sourceCategory),
		forceReaderType: fileUploadResult ? "txtfilereader" : undefined,
		extraColumns,
		mappingTables,
	};
}

export function resolveTemplateSourceCategory(
	currentValue: unknown,
	templateDefaults?: Record<string, any>,
	templateSourceCategory?: string
): "file" | "database" | undefined {
	return (
		normalizeSourceCategory(typeof currentValue === "string" ? currentValue : undefined) ||
		normalizeSourceCategory(templateDefaults?.sourceCategory) ||
		normalizeSourceCategory(templateSourceCategory)
	);
}
