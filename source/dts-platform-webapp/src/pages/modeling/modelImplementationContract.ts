import type { ModelSpecImplementationMode } from "./modelSpecV2Contract";

export type ModelImplementationInputMode = "PHYSICAL_ASSET" | "UPSTREAM_MODEL" | "GENERATED";

export type PhysicalAssetImplementationInput = {
	sourceBindingId: string;
	resolvedVersion: string;
};

export type UpstreamModelRevisionInput = {
	modelSpecId: string;
	revision: number;
	checksum: string;
};

export type PinnedUpstreamModelImplementationInput = UpstreamModelRevisionInput & {
	implementationRevision: number;
	implementationChecksum: string;
	dbtUniqueId: string;
};

export type UnpinnedUpstreamModelImplementationInput = UpstreamModelRevisionInput & {
	implementationRevision?: undefined;
	implementationChecksum?: undefined;
	dbtUniqueId?: undefined;
};

export type UpstreamModelImplementationInput =
	| PinnedUpstreamModelImplementationInput
	| UnpinnedUpstreamModelImplementationInput;

export const isUpstreamModelImplementationPinned = (
	input: UpstreamModelImplementationInput,
): input is PinnedUpstreamModelImplementationInput =>
	Number.isInteger(input.implementationRevision) &&
	(input.implementationRevision ?? 0) > 0 &&
	typeof input.implementationChecksum === "string" &&
	Boolean(input.implementationChecksum.trim()) &&
	typeof input.dbtUniqueId === "string" &&
	Boolean(input.dbtUniqueId.trim());

export type GeneratedImplementationInput = {
	generatorType: string;
	config?: Record<string, unknown>;
};

export type ModelImplementationInput =
	| PhysicalAssetImplementationInput
	| UpstreamModelImplementationInput
	| GeneratedImplementationInput;

export const resolvePhysicalAssetImplementationInputs = (
	sourceBindingIds: readonly string[],
	available: readonly PhysicalAssetImplementationInput[],
	persisted: readonly PhysicalAssetImplementationInput[],
	adoptCurrentIds: ReadonlySet<string> = new Set<string>(),
): PhysicalAssetImplementationInput[] => {
	const availableById = new Map(available.map((input) => [input.sourceBindingId, input]));
	const persistedById = new Map(persisted.map((input) => [input.sourceBindingId, input]));
	return sourceBindingIds.map((sourceBindingId) => {
		const input = adoptCurrentIds.has(sourceBindingId)
			? availableById.get(sourceBindingId) || persistedById.get(sourceBindingId)
			: persistedById.get(sourceBindingId) || availableById.get(sourceBindingId);
		return {
			sourceBindingId,
			resolvedVersion: input?.resolvedVersion || "",
		};
	});
};

export const resolveUpstreamModelImplementationInputs = (
	modelSpecIds: readonly string[],
	available: readonly UpstreamModelRevisionInput[],
	persisted: readonly PinnedUpstreamModelImplementationInput[],
	adoptCurrentIds: ReadonlySet<string> = new Set<string>(),
): UpstreamModelImplementationInput[] => {
	const availableById = new Map(available.map((input) => [input.modelSpecId, input]));
	const persistedById = new Map(persisted.map((input) => [input.modelSpecId, input]));
	return modelSpecIds.map((modelSpecId) => {
		const current = availableById.get(modelSpecId);
		const saved = persistedById.get(modelSpecId);
		if (adoptCurrentIds.has(modelSpecId)) {
			if (current && current.revision > 0 && current.checksum.trim()) return { ...current };
			return {
				modelSpecId,
				revision: 0,
				checksum: "",
			};
		}
		if (saved) {
			return {
				modelSpecId: saved.modelSpecId,
				revision: saved.revision,
				checksum: saved.checksum,
				implementationRevision: saved.implementationRevision,
				implementationChecksum: saved.implementationChecksum,
				dbtUniqueId: saved.dbtUniqueId,
			};
		}
		if (current) return { ...current };
		return {
			modelSpecId,
			revision: 0,
			checksum: "",
		};
	});
};

export type ModelImplementationFieldMapping = {
	sourceField: string;
	targetField: string;
};

type ModelImplementationWriteBase = {
	projectKey: string;
	dbtUniqueId: string;
	fieldMappings?: ModelImplementationFieldMapping[];
	settings?: Record<string, unknown>;
	ownership: ModelSpecImplementationMode;
	materialization: string;
	idempotencyKey: string;
};

export type ModelImplementationWriteCommand =
	| (ModelImplementationWriteBase & {
			inputMode: "PHYSICAL_ASSET";
			inputs: PhysicalAssetImplementationInput[];
		})
	| (ModelImplementationWriteBase & {
			inputMode: "UPSTREAM_MODEL";
			inputs: UpstreamModelImplementationInput[];
		})
	| (ModelImplementationWriteBase & {
			inputMode: "GENERATED";
			inputs: GeneratedImplementationInput[];
		});

export type ModelImplementationView = {
	id: string;
	modelSpecId: string;
	planId: string;
	revision: number;
	modelChecksum: string;
	ownership: ModelSpecImplementationMode;
	projectKey: string;
	dbtUniqueId: string;
	status: string;
	implementationRevision: number;
	implementationChecksum: string;
	inputMode: ModelImplementationInputMode;
	inputs: ModelImplementationInput[];
	fieldMappings: ModelImplementationFieldMapping[];
	settings: Record<string, unknown>;
	materialization: string;
};

export type ModelImplementationCasToken = Pick<
	ModelImplementationView,
	"modelSpecId" | "implementationRevision" | "implementationChecksum"
>;

export const toModelImplementationEtag = ({
	modelSpecId,
	implementationRevision,
	implementationChecksum,
}: ModelImplementationCasToken): string =>
	`"model-implementation:${modelSpecId}:${implementationRevision}:${implementationChecksum}"`;

export type ModelImplementationValidation = {
	valid: boolean;
	code: string | null;
};
