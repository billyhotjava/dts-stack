import { ResultStatus } from "../types/enum";

const isStandardSuccessStatus = (status: unknown) => {
	if (status === ResultStatus.SUCCESS) return true;
	if (typeof status === "string") {
		const normalized = status.trim().toUpperCase();
		if (!normalized) return false;
		if (normalized === "SUCCESS" || normalized === "OK") return true;
		if (!Number.isNaN(Number(normalized))) return Number(normalized) === ResultStatus.SUCCESS;
	}
	return typeof status === "number" && status === ResultStatus.SUCCESS;
};

export const acceptsApiEnvelopeStatus = (status: unknown, explicitlyAccepted: readonly number[] = []) => {
	if (isStandardSuccessStatus(status)) return true;
	const numericStatus = typeof status === "number" ? status : Number(String(status || "").trim());
	return Number.isFinite(numericStatus) && explicitlyAccepted.includes(numericStatus);
};
