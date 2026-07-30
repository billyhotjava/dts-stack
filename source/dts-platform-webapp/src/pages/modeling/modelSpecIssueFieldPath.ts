import type { ModelSpecDraft } from "./modelSpecWorkbench";

type ModelSpecFieldProperty = keyof ModelSpecDraft["fields"][number];

export type ModelSpecIssueFormPath = keyof ModelSpecDraft | ["fields", number, ModelSpecFieldProperty];

type ModelFieldIdentity = {
	name?: string;
};

type ModelSpecValidationForm = {
	scrollToField: (name: Array<string | number>) => void;
};

const fieldProperties = new Set<ModelSpecFieldProperty>([
	"name",
	"displayName",
	"dataType",
	"nullable",
	"sourceFieldRef",
	"role",
	"securityLevel",
	"dimensionAttributeCode",
	"redundant",
	"redundancySourceRef",
]);

export const stableModelSpecFormValue = (value: unknown): unknown => {
	if (Array.isArray(value)) return value.map(stableModelSpecFormValue);
	if (value && typeof value === "object") {
		return Object.fromEntries(
			Object.entries(value as Record<string, unknown>)
				.sort(([left], [right]) => left.localeCompare(right))
				.map(([key, item]) => [key, stableModelSpecFormValue(item)]),
		);
	}
	return value;
};

const topLevelPath = (field: string): keyof ModelSpecDraft => {
	if (field === "grain") return "grainStatement";
	if (field === "sourceRefs") return "sources";
	if (field === "dependsOn") return "upstreamIds";
	if (field === "dimensionRefs") return "dimensionRefIds";
	if (field === "timeSemantics") return "timeSemanticsType";
	if (field === "dimensionProfile.reuseScope") return "dimensionReuseScope";
	if (field === "dimensionProfile.scdPolicy") return "dimensionScdType";
	if (field === "dimensionProfile.hierarchies") return "dimensionHierarchies";
	if (field === "dimensionProfile") return "dimensionCode";
	if (field === "generationStrategy") return "generationStrategyType";
	return field as keyof ModelSpecDraft;
};

export const modelSpecIssueFieldPath = (
	field: string,
	fields: readonly ModelFieldIdentity[],
): ModelSpecIssueFormPath => {
	if (field === "fields") return "fields";
	const match = field.match(/^fields(?:\[([^\]]+)\]|\.([^.]+))\.([A-Za-z][A-Za-z0-9]*)$/);
	if (!match) return topLevelPath(field);
	const token = match[1] || match[2];
	const property = match[3] as ModelSpecFieldProperty;
	if (!fieldProperties.has(property)) return "fields";
	const numericIndex = /^\d+$/.test(token) ? Number(token) : -1;
	const index = numericIndex >= 0 ? numericIndex : fields.findIndex((item) => item?.name === token);
	if (index < 0 || index >= fields.length) return "fields";
	return ["fields", index, property];
};

export const handleModelSpecFormValidationError = (
	error: unknown,
	form: ModelSpecValidationForm,
	onVisibleError: (message: string) => void,
): boolean => {
	if (!error || typeof error !== "object" || !("errorFields" in error)) return false;
	const firstError = (
		error as {
			errorFields?: Array<{ name?: Array<string | number> }>;
		}
	).errorFields?.[0];
	onVisibleError("请补齐标红字段后再保存");
	if (firstError?.name) {
		const scrollToError = () => form.scrollToField(firstError.name as Array<string | number>);
		if (typeof requestAnimationFrame === "function") requestAnimationFrame(scrollToError);
		else scrollToError();
	}
	return true;
};
