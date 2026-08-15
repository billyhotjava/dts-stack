import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type {
	IndicatorDefinition,
	IndicatorEditValues,
} from "@/features/modeling/indicators/indicatorDefinitionContract";

const METRIC_MODEL_TYPES = new Set(["FACT", "SUMMARY", "APPLICATION"]);

export function governedMetricModels(models: readonly ModelSpecView[]): ModelSpecView[] {
	return models.filter(
		(model) =>
			model.contractVersion === 2 &&
			model.compatibilityMode === "CANONICAL" &&
			model.status === "PUBLISHED" &&
			METRIC_MODEL_TYPES.has(model.modelType) &&
			model.fields.some((field) => field.role === "MEASURE"),
	);
}

export function bindAtomicMetricModel(values: IndicatorEditValues, model: ModelSpecView | null): IndicatorEditValues {
	if (!model) return { ...values, measureField: null, sourceRefs: [] };
	const measureFields = new Set(model.fields.filter((field) => field.role === "MEASURE").map((field) => field.name));
	const currentMeasure = String(values.measureField || "");
	return {
		...values,
		measureField: measureFields.has(currentMeasure) ? currentMeasure : null,
		sourceRefs: [
			{
				sourceType: "SEMANTIC_MODEL_REVISION",
				sourceId: model.id,
				sourceVersion: `r${model.revision}`,
			},
		],
	};
}

const physicalModelName = (model: ModelSpecView): string =>
	String(model.implementationPolicy?.physicalName || model.name || "");

export function bindMetricImplementationModel(
	values: IndicatorEditValues,
	model: ModelSpecView | null,
): IndicatorEditValues {
	if (!model) {
		return {
			...values,
			targetModelName: null,
			sourceTable: null,
			sourceLayer: null,
			targetLayer: null,
			measureField: null,
		};
	}
	const measureFields = new Set(model.fields.filter((field) => field.role === "MEASURE").map((field) => field.name));
	const currentMeasure = String(values.measureField || "");
	return {
		...values,
		targetModelName: model.name,
		sourceTable: physicalModelName(model),
		sourceLayer: model.layer,
		targetLayer: model.layer,
		measureField: measureFields.has(currentMeasure) ? currentMeasure : null,
	};
}

export const metricModelKey = (model: Pick<ModelSpecView, "id" | "revision">): string =>
	`${model.id}@r${model.revision}`;

export const selectedMetricModelKey = (values: IndicatorEditValues): string => {
	const source = values.sourceRefs?.find((ref) => ref.sourceType === "SEMANTIC_MODEL_REVISION");
	return source ? `${source.sourceId}@${source.sourceVersion}` : "";
};

export const selectedImplementationModelKey = (
	values: IndicatorEditValues,
	models: readonly ModelSpecView[],
): string => {
	const targetModelName = String(values.targetModelName || "");
	if (!targetModelName) return "";
	const candidates = models
		.filter((model) => model.name === targetModelName)
		.sort((left, right) => right.revision - left.revision);
	return candidates.length ? metricModelKey(candidates[0]) : "";
};

export function bindIndicatorDependencies(
	values: IndicatorEditValues,
	selectedIds: readonly string[],
	catalog: readonly IndicatorDefinition[],
): IndicatorEditValues {
	const selected = selectedIds
		.map((id) => catalog.find((indicator) => indicator.id === id))
		.filter((indicator): indicator is IndicatorDefinition & { id: string; code: string; version: string } =>
			Boolean(
				indicator?.id &&
					indicator.code &&
					indicator.version &&
					indicator.businessCategoryId &&
					String(indicator.status || "").toUpperCase() === "PUBLISHED",
			),
		);
	const sharedValue = (extract: (indicator: IndicatorDefinition) => string | null | undefined): string | null => {
		const values = new Set(selected.map(extract).filter((value): value is string => Boolean(value)));
		return values.size === 1 && selected.every((indicator) => Boolean(extract(indicator))) ? [...values][0] : null;
	};
	const businessCategoryId = sharedValue((indicator) => indicator.businessCategoryId);
	const dataDomainId = sharedValue((indicator) => indicator.dataDomainId);
	const businessProcessId = sharedValue((indicator) => indicator.businessProcessId);
	const domain = dataDomainId
		? selected.find((indicator) => indicator.dataDomainId === dataDomainId)?.domain || null
		: null;
	return {
		...values,
		businessCategoryId,
		dataDomainId,
		businessProcessId,
		domain,
		dependencyCodes: selected.map((indicator) => indicator.code),
		sourceRefs: selected.map((indicator) => ({
			sourceType: "INDICATOR_VERSION",
			sourceId: indicator.id,
			sourceVersion: indicator.version,
		})),
	};
}

export const selectedDependencyIds = (values: IndicatorEditValues): string[] =>
	(values.sourceRefs || [])
		.filter((ref) => ref.sourceType === "INDICATOR_VERSION")
		.map((ref) => ref.sourceId)
		.filter(Boolean);
