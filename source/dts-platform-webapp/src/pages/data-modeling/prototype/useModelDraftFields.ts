import type { Dispatch, RefObject, SetStateAction } from "react";
import type { ModelSpecField } from "@/features/modeling/contracts/modelSpecV2Contract";
import { isBlankModelField } from "./ModelFieldEditorTable";
import { applyModelDraftFieldPatch, isModelSpecDraft, type ModelDraft } from "./services/modelWorkbenchService";
export function useModelDraftFields(
	draft: ModelDraft | null,
	savingRef: RefObject<boolean>,
	setDraft: Dispatch<SetStateAction<ModelDraft | null>>,
	setFieldRowIds: Dispatch<SetStateAction<string[]>>,
) {
	const updateField = (index: number, patch: Partial<ModelSpecField>) => {
		if (savingRef.current) return;
		setDraft((current) =>
			current && isModelSpecDraft(current) ? applyModelDraftFieldPatch(current, index, patch) : current,
		);
	};
	const addFields = (count: number) => {
		if (savingRef.current || !draft || !isModelSpecDraft(draft)) return;
		const additionCount = Number.isFinite(count) ? Math.max(1, Math.min(20, Math.floor(count))) : 1;
		const nextFields = Array.from({ length: additionCount }, () => ({
			name: "",
			displayName: "",
			dataType: "STRING",
			nullable: true,
			role: "ATTRIBUTE" as const,
			dimensionAttributeCode: null,
		}));
		const nextRowIds = nextFields.map(() => crypto.randomUUID());
		setDraft((current) =>
			current && isModelSpecDraft(current) ? { ...current, fields: [...current.fields, ...nextFields] } : current,
		);
		setFieldRowIds((current) => [...current, ...nextRowIds]);
	};
	const removeBlankFields = () => {
		if (savingRef.current || !draft || !isModelSpecDraft(draft)) return;
		const keepIndexes = draft.fields
			.map((field, index) => (isBlankModelField(field) ? -1 : index))
			.filter((index) => index >= 0);
		const fields = keepIndexes.map((index) => draft.fields[index]);
		const fieldNames = new Set(fields.map((field) => field.name));
		setDraft({
			...draft,
			fields,
			standardBindings: draft.standardBindings.filter((binding) => fieldNames.has(binding.fieldName)),
			timeSemanticsFields: draft.timeSemanticsFields.filter((fieldName) => fieldNames.has(fieldName)),
		});
		setFieldRowIds((current) => keepIndexes.map((index) => current[index] || crypto.randomUUID()));
	};
	const deleteField = (index: number) => {
		if (savingRef.current) return;
		setFieldRowIds((current) => current.filter((_, row) => row !== index));
		setDraft((current) => {
			if (!current || !isModelSpecDraft(current)) return current;
			const fieldName = current.fields[index]?.name;
			return {
				...current,
				fields: current.fields.filter((_, row) => row !== index),
				standardBindings: current.standardBindings.filter((binding) => binding.fieldName !== fieldName),
				timeSemanticsFields: current.timeSemanticsFields.filter((item) => item !== fieldName),
			};
		});
	};
	const updateStandardBinding = (index: number, value: string) => {
		if (savingRef.current) return;
		setDraft((current) => {
			if (!current || !isModelSpecDraft(current)) return current;
			const fieldName = current.fields[index]?.name || "";
			const remaining = current.standardBindings.filter((binding) => binding.fieldName !== fieldName);
			if (!value || !fieldName) return { ...current, standardBindings: remaining };
			const [standardElementId, version] = value.split("@");
			return {
				...current,
				standardBindings: [...remaining, { fieldName, standardElementId, standardElementVersion: Number(version) }],
			};
		});
	};
	return { updateField, addFields, removeBlankFields, deleteField, updateStandardBinding };
}
