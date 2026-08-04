export const DEFAULT_FILE_PREVIEW_LIMIT = 10;
export const MAX_FILE_PREVIEW_LIMIT = 20;

export const normalizeFilePreviewLimit = (value: unknown): number => {
	const numeric = typeof value === "number" ? value : Number.NaN;
	if (!Number.isFinite(numeric)) return DEFAULT_FILE_PREVIEW_LIMIT;
	return Math.min(MAX_FILE_PREVIEW_LIMIT, Math.max(1, Math.floor(numeric)));
};

export const buildFilePreviewRows = (preview: string[][] | undefined, limit: unknown): string[][] =>
	Array.isArray(preview) ? preview.slice(0, normalizeFilePreviewLimit(limit)) : [];
