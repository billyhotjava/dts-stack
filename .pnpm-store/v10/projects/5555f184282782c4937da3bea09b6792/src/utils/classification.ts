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

const CLASSIFICATION_RANK: Record<ClassificationLevel, number> = {
	PUBLIC: 0,
	INTERNAL: 1,
	SECRET: 2,
	CONFIDENTIAL: 3,
};

const FILE_CLASSIFICATION_KEYWORDS: { keyword: string; level: ClassificationLevel }[] = [
	{ keyword: "机密", level: "CONFIDENTIAL" },
	{ keyword: "CONFIDENTIAL", level: "CONFIDENTIAL" },
	{ keyword: "秘密", level: "SECRET" },
	{ keyword: "SECRET", level: "SECRET" },
];

/**
 * SEC-001: Detect classification level from a file name.
 * Returns the highest detected level, or null if none found.
 */
export function detectFileClassification(fileName?: string): ClassificationLevel | null {
	if (!fileName) return null;
	const upper = fileName.toUpperCase();
	let highest: ClassificationLevel | null = null;
	for (const { keyword, level } of FILE_CLASSIFICATION_KEYWORDS) {
		if (upper.includes(keyword.toUpperCase())) {
			if (!highest || CLASSIFICATION_RANK[level] > CLASSIFICATION_RANK[highest]) {
				highest = level;
			}
		}
	}
	return highest;
}

/**
 * SEC-001: Check if a file can be uploaded in a non-secret module.
 * Returns an error message if rejected, null if allowed.
 */
export function checkFileUploadClassification(
	fileName: string,
	isSecretModule: boolean,
	userClassificationRank?: number,
): string | null {
	const detected = detectFileClassification(fileName);
	if (!detected) return null;
	const detectedRank = CLASSIFICATION_RANK[detected];
	if (!isSecretModule && detectedRank >= CLASSIFICATION_RANK.SECRET) {
		return `非密模块禁止上传涉密数据`;
	}
	if (isSecretModule && userClassificationRank != null && detectedRank > userClassificationRank) {
		return `您的密级不足，无法上传"${CLASSIFICATION_LABELS_ZH[detected]}"级别的附件`;
	}
	return null;
}
