export type LegacyModelRegistrationInput = {
	modelId: string;
	dbtUniqueId: string;
	path: string;
};

export type LegacyModelRegistration = LegacyModelRegistrationInput & {
	status: "LEGACY_READONLY";
	apiRoute: "/api/semantic/models";
};

export const registerLegacyDbtModel = (input: LegacyModelRegistrationInput): LegacyModelRegistration => {
	if (!input.modelId.trim() || !input.dbtUniqueId.trim() || !input.path.trim()) {
		throw new Error("legacy dbt model registration requires modelId, dbtUniqueId and path");
	}
	return { ...input, status: "LEGACY_READONLY", apiRoute: "/api/semantic/models" };
};

export const canEditLegacyAsset = (asset: LegacyModelRegistration): boolean => asset.status !== "LEGACY_READONLY";
