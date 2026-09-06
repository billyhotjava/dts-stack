import { saveModelDraftOperation } from "@/api/modelSpecApi";
import { resolveDefaultModelingContextId } from "@/api/services/modelingImportContextService";
import { validateModelSpecUpdate } from "@/features/modeling/contracts/modelSpecV2Contract";
import { createCommandForDraft } from "./modelDraftCreateCommand";
import {
	modelDraftToUpdateCommand,
	prepareModelDraftForSave,
	validateModelDraftInput,
	type ModelSpecDraft,
	type ModelSaveContext,
	type ModelDraftSaveResult,
} from "./modelWorkbenchService";

export function validateModelDefinitionInput(draft: ModelSpecDraft) {
	const errors = validateModelDraftInput({
		...draft,
		implementationInputMode: "",
		loadStrategy: "FULL",
		materialization: "table",
	});
	delete errors.implementationInputMode;
	delete errors.physicalName;
	delete errors.partitionFields;
	delete errors.transformations;
	if (!draft.name.trim()) errors.name = "请填写模型名称";
	return errors;
}

export async function saveModelDefinitionDraft(
	draft: ModelSpecDraft,
	context: ModelSaveContext,
): Promise<ModelDraftSaveResult> {
	if (draft.base) throw new Error("首次保存设计仅适用于新建模型");
	const prepared = prepareModelDraftForSave(draft, context.dimensionDefinitions);
	const errors = Object.values(validateModelDefinitionInput(prepared));
	if (errors.length) throw new Error(errors.join("；"));
	const planId = prepared.planId || (await resolveDefaultModelingContextId());
	if (!planId) throw new Error("服务端尚未提供可写建模上下文，请联系管理员初始化");
	const writable = { ...prepared, planId };
	const modelSpec = modelDraftToUpdateCommand(writable);
	const issues = validateModelSpecUpdate(modelSpec).filter((issue) => issue.code !== "MODEL_SPEC_UPSTREAM_REQUIRED");
	if (issues.length)
		throw new Error(issues.map((issue) => `${issue.field}：${issue.message || issue.code}`).join("；"));
	const saved = await saveModelDraftOperation({
		create: createCommandForDraft(writable, modelSpec, context),
		modelSpec,
		implementation: null,
		saveMode: "DEFINITION_ONLY",
	});
	return { model: saved.model, implementation: saved.implementation };
}
