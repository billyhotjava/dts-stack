import type {
	ModelSpecDimensionProfile,
	ModelSpecLoadStrategy,
	ModelSpecScdType,
} from "@/features/modeling/contracts/modelSpecV2Contract";
import type { ModelImplementationView } from "@/features/modeling/contracts/modelImplementationContract";

export function copyDimensionProfile(
	profile: ModelSpecDimensionProfile | null | undefined,
): ModelSpecDimensionProfile | null {
	return profile == null
		? null
		: {
				hierarchies: profile.hierarchies.map((hierarchy) => ({
					...hierarchy,
					levels: hierarchy.levels.map((level) => ({ ...level })),
				})),
				scdPolicy: { ...profile.scdPolicy },
			};
}

export function dimensionProfileForSave(draft: {
	scdType: ModelSpecScdType;
	dimensionProfile?: ModelSpecDimensionProfile | null;
	base?: { dimensionProfile?: ModelSpecDimensionProfile | null } | null;
}): ModelSpecDimensionProfile | null {
	const profile = copyDimensionProfile(
		draft.dimensionProfile === undefined ? draft.base?.dimensionProfile : draft.dimensionProfile,
	);
	if (profile === null && draft.scdType === "NONE") return null;
	return {
		hierarchies: profile?.hierarchies || [],
		scdPolicy: draft.scdType === "TYPE2" ? { ...profile?.scdPolicy, type: "TYPE2" } : { type: draft.scdType },
	};
}

export function implementationConfiguration(
	implementation: ModelImplementationView | null,
	legacy?: { loadStrategy?: ModelSpecLoadStrategy | null; partitionFields?: string[] | null } | null,
): { loadStrategy: ModelSpecLoadStrategy; partitionFields: string } {
	const settings = implementation?.settings || {};
	const owns = (key: string) => Object.prototype.hasOwnProperty.call(settings, key);
	const strategy = owns("loadStrategy") ? settings.loadStrategy : (legacy?.loadStrategy ?? "FULL");
	const partitions = owns("partitionFields") ? settings.partitionFields : (legacy?.partitionFields ?? []);
	return {
		// Preserve invalid explicit values for validation/repair; only absence permits legacy fallback.
		loadStrategy: (typeof strategy === "string" ? strategy : "") as ModelSpecLoadStrategy,
		partitionFields:
			Array.isArray(partitions) && partitions.every((value) => typeof value === "string")
				? partitions.join(",")
				: JSON.stringify(partitions) || "null",
	};
}
