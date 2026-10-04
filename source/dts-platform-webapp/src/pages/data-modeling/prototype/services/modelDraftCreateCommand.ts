import type { ModelDraftOperationCommand } from "@/api/modelSpecApi";
import type { UpdateModelSpecCommand } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { ModelSpecDraft, ModelSaveContext } from "./modelWorkbenchService";

export const createCommandForDraft = (
	draft: ModelSpecDraft,
	update: UpdateModelSpecCommand,
	context: ModelSaveContext,
): ModelDraftOperationCommand["create"] => {
	const createBase = {
		planId: draft.planId,
		domainId: draft.domainId,
		name: draft.name.trim(),
		description: draft.description.trim() || null,
		warehouseLayerCode: update.warehouseLayerCode,
		businessProcessId: update.modelType === "FACT" ? update.businessProcessId : null,
		dataMartId: update.modelType === "APPLICATION" ? update.dataMartId : null,
		subjectDomainId: update.modelType === "APPLICATION" ? update.subjectDomainId : null,
		idempotencyKey: draft.creationOperationId || draft.implementationIdempotencyKey,
	};
	if (update.modelType !== "DIMENSION") return { ...createBase, modelType: update.modelType };
	const definition = context.dimensionDefinitions.find((item) => item.id === draft.dimensionDefinitionId);
	if (!definition) throw new Error("请选择一个当前有效的维度");
	return {
		...createBase,
		modelType: "DIMENSION",
		dimensionDefinitionRef: { dimensionDefinitionId: definition.id, revision: definition.revision },
	};
};
