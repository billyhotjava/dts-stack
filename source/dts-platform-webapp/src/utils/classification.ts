export type ClassificationLevel = "PUBLIC" | "INTERNAL" | "SECRET" | "CONFIDENTIAL";

export const CLASSIFICATION_LABELS_ZH: Record<ClassificationLevel, string> = {
	PUBLIC: "公开",
	INTERNAL: "内部",
	SECRET: "秘密",
	CONFIDENTIAL: "机密",
};

export const CLASSIFICATION_LABELS_EN: Record<ClassificationLevel, string> = {
	PUBLIC: "Public",
	INTERNAL: "Internal",
	SECRET: "Secret",
	CONFIDENTIAL: "Confidential",
};

const CLASSIFICATION_ALIAS_MAP: Record<string, ClassificationLevel> = {
	PUBLIC: "PUBLIC",
	DATA_PUBLIC: "PUBLIC",
	公开: "PUBLIC",
	INTERNAL: "INTERNAL",
	DATA_INTERNAL: "INTERNAL",
	内部: "INTERNAL",
	SECRET: "SECRET",
	DATA_SECRET: "SECRET",
	SECRET_LEVEL: "SECRET",
	秘密: "SECRET",
	CONFIDENTIAL: "CONFIDENTIAL",
	DATA_CONFIDENTIAL: "CONFIDENTIAL",
	CONFIDENTIAL_LEVEL: "CONFIDENTIAL",
	机密: "CONFIDENTIAL",
	TOP_SECRET: "CONFIDENTIAL",
	TOPSECRET: "CONFIDENTIAL",
	"TOP SECRET": "CONFIDENTIAL",
	"TOP-SECRET": "CONFIDENTIAL",
	DATA_TOP_SECRET: "CONFIDENTIAL",
};

export function normalizeClassification(value?: string): ClassificationLevel;
export function normalizeClassification(
	value: string | undefined,
	fallback: ClassificationLevel | undefined,
): ClassificationLevel | undefined;
export function normalizeClassification(
	value?: string,
	fallback: ClassificationLevel | undefined = "INTERNAL",
): ClassificationLevel | undefined {
	if (typeof value !== "string") {
		return fallback;
	}
	const raw = value.trim();
	if (!raw) {
		return fallback;
	}
	const upper = raw.toUpperCase();
	const upperNormalized = upper.replace(/[\s-]+/g, "_");
	const rawNormalized = raw.replace(/[\s-]+/g, "_");
	const candidates = new Set<string>([raw, upper, upperNormalized, rawNormalized]);
	const pushWithoutDataPrefix = (key: string) => {
		if (key.startsWith("DATA_")) {
			candidates.add(key.slice(5));
		}
	};
	pushWithoutDataPrefix(upper);
	pushWithoutDataPrefix(upperNormalized);
	pushWithoutDataPrefix(rawNormalized);
	for (const candidate of candidates) {
		const matched = CLASSIFICATION_ALIAS_MAP[candidate];
		if (matched) {
			return matched;
		}
	}
	return fallback;
}

export function classificationToLabelZh(
	value?: string,
	fallback: string = CLASSIFICATION_LABELS_ZH.INTERNAL,
): string {
	const normalized = normalizeClassification(value);
	return normalized ? CLASSIFICATION_LABELS_ZH[normalized] : fallback;
}

export function classificationToLabelEn(
	value?: string,
	fallback: string = CLASSIFICATION_LABELS_EN.INTERNAL,
): string {
	const normalized = normalizeClassification(value);
	return normalized ? CLASSIFICATION_LABELS_EN[normalized] : fallback;
}
