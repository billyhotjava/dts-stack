import { getModelSpecRevision } from "@/api/modelSpecApi";
import { type ModelSpecView, modelSpecRevisionRefKey } from "./modelSpecV2Contract";

export type ModelSpecReferenceResolution = {
	targets: Record<string, ModelSpecView | null>;
	failed: boolean;
};

export const resolveModelSpecReferenceTargets = async (model: ModelSpecView): Promise<ModelSpecReferenceResolution> => {
	const references =
		model.compatibilityMode === "CANONICAL"
			? Array.from(
					new Map(
						[...model.dependsOn, ...model.dimensionRefs].map((reference) => [
							modelSpecRevisionRefKey(reference),
							reference,
						]),
					).values(),
				)
			: [];
	const resolved = await Promise.all(
		references.map(async (reference) => {
			const key = modelSpecRevisionRefKey(reference);
			try {
				const target = await getModelSpecRevision(reference.modelSpecId, reference.revision);
				if (target.id !== reference.modelSpecId || target.revision !== reference.revision) {
					return { key, target: null, failed: true };
				}
				return { key, target, failed: false };
			} catch {
				return { key, target: null, failed: true };
			}
		}),
	);
	return {
		targets: Object.fromEntries(resolved.map(({ key, target }) => [key, target])),
		failed: resolved.some((item) => item.failed),
	};
};
