import type { ModelInputAvailability, ModelUpstreamPin } from "@/api/modelInputInspectionApi";
import { isUpstreamModelImplementationPinned } from "@/features/modeling/contracts/modelImplementationContract";
import type { ModelSpecDraft } from "./modelWorkbenchService";

const persistedInputs = (draft: ModelSpecDraft) =>
	draft.authoringImplementationInputs || draft.implementationBase?.inputs || [];

/**
 * Selected upstream models that still lack an implementation pin but whose current implementation matches the
 * selected design revision. Adopting these is what ticking the checkbox would have done; pins that already exist
 * or designs that drifted still require the explicit "更新引用" confirmation.
 */
export const adoptableUpstreamPins = (
	draft: ModelSpecDraft,
	items: Record<string, ModelInputAvailability>,
): ModelUpstreamPin[] =>
	draft.dependsOn.flatMap((ref) => {
		const item = items[ref.modelSpecId];
		const pin = item?.currentPin;
		if (!item?.selectable || !pin || pin.modelSpecId !== ref.modelSpecId || pin.revision !== ref.revision) return [];
		const persisted = persistedInputs(draft).find(
			(input) => "modelSpecId" in input && input.modelSpecId === ref.modelSpecId,
		);
		if (persisted && "modelSpecId" in persisted && isUpstreamModelImplementationPinned(persisted)) return [];
		return [pin];
	});

/** Records one upstream selection with its pin, replacing any earlier input for the same model. */
export const withUpstreamPin = (draft: ModelSpecDraft, pin: ModelUpstreamPin): ModelSpecDraft => {
	const previous = persistedInputs(draft);
	const dependsOn = draft.dependsOn.some((ref) => ref.modelSpecId === pin.modelSpecId)
		? draft.dependsOn.map((ref) =>
				ref.modelSpecId === pin.modelSpecId ? { modelSpecId: pin.modelSpecId, revision: pin.revision } : ref,
			)
		: [...draft.dependsOn, { modelSpecId: pin.modelSpecId, revision: pin.revision }];
	const authoringImplementationInputs = previous.some(
		(input) => "modelSpecId" in input && input.modelSpecId === pin.modelSpecId,
	)
		? previous.map((input) => ("modelSpecId" in input && input.modelSpecId === pin.modelSpecId ? pin : input))
		: [...previous, pin];
	return { ...draft, dependsOn, authoringImplementationInputs };
};
