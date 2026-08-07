import { useCallback, useEffect, type Dispatch, type MutableRefObject, type SetStateAction } from "react";
import { listDimensionDefinitions } from "@/api/dimensionDefinitionApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import {
	conceptDimensionDraftFromView,
	confirmDimensionDefinitionDraft,
	loadCurrentDimensionDefinitions,
	type ConceptDimensionDraft,
	type ModelDraft,
} from "./services/modelWorkbenchService";
import {
	normalizeModelingRequestFailure,
	type ModelingRequestFailure,
} from "./services/planningProjectionService";

type WorkbenchFailure = { kind: "permission" | "request"; message: string } | null;

export type ConceptDimensionWorkflowOptions = {
	savingRef: MutableRefObject<boolean>;
	canMaintain: boolean;
	conceptDraft: ConceptDimensionDraft | null;
	draftBase: ModelSpecView | null;
	draftCreateKind: string | null;
	draftDimensionDefinitionId: string;
	draftDomainId: string;
	replaceDraft: (draft: ModelDraft | null) => void;
	setDraft: Dispatch<SetStateAction<ModelDraft | null>>;
	show: (message: string) => void;
	setSaving: (saving: boolean) => void;
	setFailure: (failure: WorkbenchFailure) => void;
	setDimensionDefinitions: (items: DimensionDefinitionView[]) => void;
	setDimensionDefinitionFailure: (message: string) => void;
};

export function useConceptDimensionWorkflow({
	savingRef,
	canMaintain,
	conceptDraft,
	draftBase,
	draftCreateKind,
	draftDimensionDefinitionId,
	draftDomainId,
	replaceDraft,
	setDraft,
	show,
	setSaving,
	setFailure,
	setDimensionDefinitions,
	setDimensionDefinitionFailure,
}: ConceptDimensionWorkflowOptions) {
	const confirmConceptVersion = useCallback(async () => {
		if (savingRef.current || !conceptDraft?.definitionBase || !canMaintain) return;
		setFailure(null);
		savingRef.current = true;
		setSaving(true);
		try {
			const confirmed = await confirmDimensionDefinitionDraft(conceptDraft);
			replaceDraft(confirmed);
			show(`维度定义已确认：${confirmed.definitionBase?.systemCode}`);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "维度定义确认失败。"));
		} finally {
			savingRef.current = false;
			setSaving(false);
		}
	}, [canMaintain, conceptDraft, replaceDraft, savingRef, setFailure, setSaving, show]);

	const recoverConflictingDimensionDefinition = useCallback(
		async (draft: ConceptDimensionDraft, failure: ModelingRequestFailure): Promise<boolean> => {
			const conflictMessage = "该数据域下已存在同名维度，请更换名称，或直接使用已有维度。";
			try {
				const definitions = await listDimensionDefinitions({
					domainId: draft.domainId,
					offset: 0,
					limit: 100,
				});
				const match = definitions.find((item) => item.name.trim() === draft.name.trim());
				if (!match) {
					setFailure({ ...failure, message: conflictMessage });
					return false;
				}
				replaceDraft(conceptDimensionDraftFromView(match));
				setFailure(null);
				show(`该数据域下已存在同名维度，已为你打开：${match.systemCode}`);
				return true;
			} catch {
				setFailure({ ...failure, message: conflictMessage });
				return false;
			}
		},
		[replaceDraft, setFailure, show],
	);

	useEffect(() => {
		if (draftCreateKind !== "dimension-table" || !draftDomainId) {
			setDimensionDefinitions([]);
			setDimensionDefinitionFailure("");
			return;
		}
		let active = true;
		setDimensionDefinitionFailure("");
		void loadCurrentDimensionDefinitions(draftDomainId)
			.then((items) => {
				if (!active) return;
				setDimensionDefinitions(items);
				if (!draftBase && draftCreateKind === "dimension-table" && !draftDimensionDefinitionId && items[0]) {
					setDraft((current) =>
						current && current.createKind === "dimension-table"
							? { ...current, dimensionDefinitionId: items[0].id }
							: current,
					);
				}
			})
			.catch((error) => {
				if (active) {
					setDimensionDefinitions([]);
					setDimensionDefinitionFailure(
						normalizeModelingRequestFailure(error, "维度目录读取失败。").message,
					);
				}
			});
		return () => {
			active = false;
		};
	}, [
		draftBase,
		draftCreateKind,
		draftDimensionDefinitionId,
		draftDomainId,
		setDraft,
		setDimensionDefinitionFailure,
		setDimensionDefinitions,
	]);

	return { confirmConceptVersion, recoverConflictingDimensionDefinition };
}
