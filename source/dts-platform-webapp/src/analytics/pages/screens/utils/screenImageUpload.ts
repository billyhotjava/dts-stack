export const SCREEN_IMAGE_UPLOAD_LIMIT_BYTES = 10 * 1024 * 1024;
export const SCREEN_IMAGE_UPLOAD_LIMIT_LABEL = "10MB";

type UploadError = {
	response?: {
		status?: number;
		data?: unknown;
	};
	message?: string;
};

function readResponseMessage(data: unknown): string | undefined {
	if (typeof data === "string") {
		const message = normalizeResponseMessage(data);
		return message.length > 0 ? message : undefined;
	}
	if (!data || typeof data !== "object") return undefined;

	const record = data as Record<string, unknown>;
	for (const key of ["message", "detail", "error", "title"]) {
		const value = record[key];
		if (typeof value === "string" && value.trim().length > 0) {
			return normalizeResponseMessage(value);
		}
	}
	return undefined;
}

function normalizeResponseMessage(message: string): string {
	const trimmed = message.trim();
	const quotedProblemDetail = trimmed.match(/^\d{3}\s+[A-Z_]+\s+"(.+)"$/);
	return quotedProblemDetail?.[1]?.trim() || trimmed;
}

export function getScreenImageUploadErrorMessage(error: unknown): string {
	const uploadError = error as UploadError | undefined;
	let rawMessage: string | undefined;
	if (error instanceof Error) {
		rawMessage = error.message;
	} else if (typeof uploadError?.message === "string") {
		rawMessage = uploadError.message;
	}

	if (uploadError?.response?.status === 413 || rawMessage?.includes("status code 413")) {
		return `图片文件超过上传限制，请压缩到 ${SCREEN_IMAGE_UPLOAD_LIMIT_LABEL} 以内后重试`;
	}

	return readResponseMessage(uploadError?.response?.data) ?? rawMessage ?? "上传失败";
}
