import type { DbtImplementationDraft } from "@/api/dbtImplementationDraftApi";

type AuthoringDraftState = DbtImplementationDraft["state"];

export const shouldPersistBeforeAuthoringValidation = (
	draftState: AuthoringDraftState | null | undefined,
	modelDirty: boolean,
	codeDirty: boolean,
): boolean => draftState !== "VALIDATED" || modelDirty || codeDirty;
