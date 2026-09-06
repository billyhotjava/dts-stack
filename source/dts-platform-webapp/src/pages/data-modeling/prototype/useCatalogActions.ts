import { type MutableRefObject, useCallback } from "react";
import {
	createDimensionDefinition,
	deleteDimensionDefinition,
	retireDimensionDefinition,
} from "@/api/dimensionDefinitionApi";
import { archiveModelSpec, deleteModelSpec } from "@/api/modelSpecApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { dataModelingPath } from "../navigation";
import { conceptDimensionDraftFromView, type ModelDraft } from "./services/modelWorkbenchService";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

type WorkbenchFailure = { kind: "permission" | "request"; message: string } | null;

export type CatalogActionOptions = {
	navigate: (to: string) => void;
	canMaintain: boolean;
	ownerId: string;
	savingRef: MutableRefObject<boolean>;
	confirmDiscard: () => boolean;
	selectedModelId: string;
	selectedDimensionId: string;
	replaceDraft: (draft: ModelDraft | null) => void;
	syncWorkbenchUrl: (mutate: (params: URLSearchParams) => void) => void;
	requestedModelIdRef: MutableRefObject<string>;
	requestedDimensionIdRef: MutableRefObject<string>;
	show: (message: string) => void;
	setSaving: (saving: boolean) => void;
	setFailure: (failure: WorkbenchFailure) => void;
	reload: () => Promise<void>;
};

export function useCatalogActions(options: CatalogActionOptions) {
	const {
		navigate,
		canMaintain,
		ownerId,
		savingRef,
		confirmDiscard,
		selectedModelId,
		selectedDimensionId,
		replaceDraft,
		syncWorkbenchUrl,
		requestedModelIdRef,
		requestedDimensionIdRef,
		show,
		setSaving,
		setFailure,
		reload,
	} = options;

	const goToGraph = useCallback(
		(model: ModelSpecView) => {
			if (savingRef.current || !confirmDiscard()) return;
			navigate(`${dataModelingPath("graphs", "models")}?query=${encodeURIComponent(model.name)}`);
		},
		[confirmDiscard, navigate, savingRef],
	);

	const removeModel = useCallback(
		async (model: ModelSpecView) => {
			if (savingRef.current || !canMaintain || model.status !== "DRAFT" || model.compatibilityMode !== "CANONICAL")
				return;
			if (!window.confirm(`确认永久删除草稿模型「${model.name}」？该操作不可恢复，但不会删除数据源或已存在的物理表。`))
				return;
			setFailure(null);
			savingRef.current = true;
			setSaving(true);
			let action = "删除";
			try {
				let archived = false;
				try {
					await deleteModelSpec({ id: model.id, revision: model.revision, checksum: model.checksum });
				} catch (error) {
					const failure = normalizeModelingRequestFailure(error, "模型删除失败。");
					if (failure.code !== "MODEL_SPEC_DELETE_IN_USE") throw error;
					if (
						!window.confirm(
							`模型「${model.name}」已有实现或运行记录，无法永久删除。是否改为归档？归档后从默认列表隐藏，保留历史记录和物理表，可通过“已归档”筛选查看。`,
						)
					)
						return;
					action = "归档";
					await archiveModelSpec({ id: model.id, revision: model.revision, checksum: model.checksum });
					archived = true;
				}
				if (selectedModelId === model.id) {
					requestedModelIdRef.current = "";
					syncWorkbenchUrl((params) => params.delete("modelSpecId"));
				}
				show(`模型已${archived ? "归档" : "删除"}：${model.name}`);
				await reload();
			} catch (error) {
				setFailure(normalizeModelingRequestFailure(error, `模型${action}失败。`));
			} finally {
				savingRef.current = false;
				setSaving(false);
			}
		},
		[
			canMaintain,
			reload,
			requestedModelIdRef,
			savingRef,
			selectedModelId,
			setFailure,
			setSaving,
			show,
			syncWorkbenchUrl,
		],
	);

	const archiveModel = useCallback(
		async (model: ModelSpecView) => {
			if (
				savingRef.current ||
				!canMaintain ||
				model.status === "DRAFT" ||
				model.status === "ARCHIVED" ||
				model.compatibilityMode !== "CANONICAL"
			)
				return;
			if (
				!window.confirm(
					`确认归档模型「${model.name}」？归档后将从默认列表隐藏，不影响已存在的物理表，可通过“已归档”筛选查看。`,
				)
			)
				return;
			setFailure(null);
			savingRef.current = true;
			setSaving(true);
			try {
				await archiveModelSpec({ id: model.id, revision: model.revision, checksum: model.checksum });
				if (selectedModelId === model.id) {
					requestedModelIdRef.current = "";
					syncWorkbenchUrl((params) => params.delete("modelSpecId"));
				}
				show(`模型已归档：${model.name}`);
				await reload();
			} catch (error) {
				setFailure(normalizeModelingRequestFailure(error, "模型归档失败。"));
			} finally {
				savingRef.current = false;
				setSaving(false);
			}
		},
		[
			canMaintain,
			reload,
			requestedModelIdRef,
			savingRef,
			selectedModelId,
			setFailure,
			setSaving,
			show,
			syncWorkbenchUrl,
		],
	);

	const goToDimensionGraph = useCallback(
		(definition: DimensionDefinitionView) => {
			if (savingRef.current || !confirmDiscard()) return;
			navigate(`${dataModelingPath("graphs", "models")}?query=${encodeURIComponent(definition.name)}`);
		},
		[confirmDiscard, navigate, savingRef],
	);

	const cloneDimension = useCallback(
		async (definition: DimensionDefinitionView) => {
			if (savingRef.current || !canMaintain) return;
			setFailure(null);
			savingRef.current = true;
			setSaving(true);
			try {
				const cloned = await createDimensionDefinition({
					domainId: definition.domainId,
					name: `${definition.name}（副本）`,
					definition: definition.definition,
					ownerId,
					reuseScope: definition.reuseScope,
					scopeType: definition.scopeType,
					dataMartId: definition.dataMartId,
					hierarchies: definition.hierarchies,
					attributes: definition.attributes,
					idempotencyKey: crypto.randomUUID(),
				});
				replaceDraft(conceptDimensionDraftFromView(cloned));
				requestedModelIdRef.current = "";
				requestedDimensionIdRef.current = cloned.id;
				syncWorkbenchUrl((params) => {
					params.set("dimensionDefinitionId", cloned.id);
					params.delete("modelSpecId");
				});
				show(`已克隆维度：${cloned.name}`);
				await reload();
			} catch (error) {
				setFailure(normalizeModelingRequestFailure(error, "维度克隆失败。"));
			} finally {
				savingRef.current = false;
				setSaving(false);
			}
		},
		[
			canMaintain,
			ownerId,
			reload,
			replaceDraft,
			requestedDimensionIdRef,
			requestedModelIdRef,
			savingRef,
			setFailure,
			setSaving,
			show,
			syncWorkbenchUrl,
		],
	);

	const removeDimension = useCallback(
		async (definition: DimensionDefinitionView) => {
			if (savingRef.current || !canMaintain) return;
			const action = definition.status === "DRAFT" ? "删除" : "退役";
			if (!window.confirm(`确认${action}维度「${definition.name}」？`)) return;
			setFailure(null);
			savingRef.current = true;
			setSaving(true);
			try {
				const token = { id: definition.id, revision: definition.revision, checksum: definition.checksum };
				if (definition.status === "DRAFT") {
					await deleteDimensionDefinition(token);
				} else {
					await retireDimensionDefinition(token);
				}
				if (selectedDimensionId === definition.id) {
					requestedDimensionIdRef.current = "";
					syncWorkbenchUrl((params) => params.delete("dimensionDefinitionId"));
				}
				show(`维度已${action}：${definition.name}`);
				await reload();
			} catch (error) {
				setFailure(normalizeModelingRequestFailure(error, `${action}维度失败。`));
			} finally {
				savingRef.current = false;
				setSaving(false);
			}
		},
		[
			canMaintain,
			reload,
			requestedDimensionIdRef,
			savingRef,
			selectedDimensionId,
			setFailure,
			setSaving,
			show,
			syncWorkbenchUrl,
		],
	);

	return { goToGraph, removeModel, archiveModel, goToDimensionGraph, cloneDimension, removeDimension };
}
