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
	"0": "PUBLIC",
	PUBLIC: "PUBLIC",
	DATA_PUBLIC: "PUBLIC",
	NON_SECRET: "PUBLIC",
	NONE_SECRET: "PUBLIC",
	NS: "PUBLIC",
	非密: "PUBLIC",
	公开: "PUBLIC",
	公开级: "PUBLIC",
	"1": "INTERNAL",
	INTERNAL: "INTERNAL",
	DATA_INTERNAL: "INTERNAL",
	GENERAL: "INTERNAL",
	一般: "INTERNAL",
	内部: "INTERNAL",
	内部级: "INTERNAL",
	"2": "SECRET",
	SECRET: "SECRET",
	DATA_SECRET: "SECRET",
	SECRET_LEVEL: "SECRET",
	IMPORTANT: "SECRET",
	重要: "SECRET",
	秘密: "SECRET",
	秘密级: "SECRET",
	// Legacy tokens from the BI publication path. SENSITIVE was never a separate level;
	// it is collapsed onto SECRET here to match SecurityLevelCatalog on the backend.
	SENSITIVE: "SECRET",
	DATA_SENSITIVE: "SECRET",
	敏感: "SECRET",
	敏感级: "SECRET",
	"3": "CONFIDENTIAL",
	CONFIDENTIAL: "CONFIDENTIAL",
	DATA_CONFIDENTIAL: "CONFIDENTIAL",
	CONFIDENTIAL_LEVEL: "CONFIDENTIAL",
	CORE: "CONFIDENTIAL",
	CORE_SECRET: "CONFIDENTIAL",
	核心: "CONFIDENTIAL",
	机密: "CONFIDENTIAL",
	机密级: "CONFIDENTIAL",
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
	...fallbackArgs: [] | [ClassificationLevel | undefined]
): ClassificationLevel | undefined {
	const resolvedFallback: ClassificationLevel | undefined = fallbackArgs.length ? fallbackArgs[0] : "INTERNAL";
	if (typeof value !== "string") {
		return resolvedFallback;
	}
	const raw = value.trim();
	if (!raw) {
		return resolvedFallback;
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
	return resolvedFallback;
}

export function classificationToLabelZh(value?: string, fallback: string = CLASSIFICATION_LABELS_ZH.INTERNAL): string {
	const normalized = normalizeClassification(value);
	return normalized ? CLASSIFICATION_LABELS_ZH[normalized] : fallback;
}

export function classificationToLabelEn(value?: string, fallback: string = CLASSIFICATION_LABELS_EN.INTERNAL): string {
	const normalized = normalizeClassification(value);
	return normalized ? CLASSIFICATION_LABELS_EN[normalized] : fallback;
}

const CLASSIFICATION_RANK: Record<ClassificationLevel, number> = {
	PUBLIC: 0,
	INTERNAL: 1,
	SECRET: 2,
	CONFIDENTIAL: 3,
};

export function classificationRank(value?: string): number | undefined {
	const normalized = normalizeClassification(value, undefined);
	return normalized ? CLASSIFICATION_RANK[normalized] : undefined;
}

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
