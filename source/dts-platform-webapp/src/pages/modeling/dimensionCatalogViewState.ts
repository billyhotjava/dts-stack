import type { DimensionDefinitionStatus } from "./dimensionDefinitionContract";

export type DimensionCatalogEmptyState = {
	totalCount: number;
	visibleCount: number;
	search: string;
	canEdit: boolean;
};

export type DimensionDefinitionRandomSource = {
	randomUUID?: () => string;
	getRandomValues?: (values: Uint8Array) => Uint8Array;
};

type DimensionDefinitionErrorLike = {
	response?: {
		status?: number;
		data?: { code?: string };
	};
};

const hex = (value: number) => value.toString(16).padStart(2, "0");

export const dimensionCatalogEmptyText = ({
	totalCount,
	visibleCount,
	search,
	canEdit,
}: DimensionCatalogEmptyState): string => {
	if (totalCount > 0 && visibleCount === 0 && search.trim()) return "未找到匹配维度，请调整搜索条件";
	if (canEdit) return "还没有维度，点击“登记维度”开始";
	return "还没有可浏览的维度";
};

export const dimensionDefinitionStatusLabel = (status: DimensionDefinitionStatus): string => {
	if (status === "CURRENT") return "现行";
	if (status === "RETIRED") return "已退役";
	return "草稿";
};

export const canRetireDimensionDefinition = (
	status: DimensionDefinitionStatus,
	canEdit: boolean,
): boolean => canEdit && status === "CURRENT";

export const shouldApplyDimensionDefinitionReload = (
	requestId: number,
	latestRequestId: number,
	requestedDefinitionId: string,
	currentDefinitionId: string | undefined,
): boolean => requestId === latestRequestId && requestedDefinitionId === currentDefinitionId;

export const formatDimensionDefinitionUpdatedAt = (value: string): string => {
	const date = new Date(value);
	return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString("zh-CN", { hour12: false });
};

/** Generates a durable request key without a timestamp or pseudo-random fallback. */
export const createDimensionDefinitionIdempotencyKey = (
	randomSource: DimensionDefinitionRandomSource | undefined = globalThis.crypto as
		| DimensionDefinitionRandomSource
		| undefined,
): string => {
	if (typeof randomSource?.randomUUID === "function") return randomSource.randomUUID();
	if (typeof randomSource?.getRandomValues !== "function") {
		throw new Error("A cryptographic random source is required to register a dimension");
	}
	const bytes = randomSource.getRandomValues(new Uint8Array(16));
	bytes[6] = (bytes[6] & 0x0f) | 0x40;
	bytes[8] = (bytes[8] & 0x3f) | 0x80;
	const value = Array.from(bytes, hex).join("");
	return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`;
};

export const dimensionDefinitionErrorMessage = (error: unknown): string => {
	const candidate = error as DimensionDefinitionErrorLike;
	const status = candidate?.response?.status;
	const code = candidate?.response?.data?.code;
	if (status === 403) return "当前账号没有维护维度的权限，请联系管理员授权";
	if (status === 404) return "维度已不存在，请刷新目录后重试";
	if (status === 409 && code === "DIMENSION_DEFINITION_REVISION_CONFLICT") {
		return "维度已被其他人更新，当前输入已保留；请加载最新版本后再保存";
	}
	if (status === 409 && code === "DIMENSION_DEFINITION_NAME_CONFLICT") {
		return "同一业务分类中已存在同名维度，请调整维度名称";
	}
	if (status === 409) return "维度状态或业务约束已变化，当前输入已保留；请刷新目录后确认";
	if (status === 412) return "维度已被其他人更新，当前输入已保留；请加载最新版本后再保存";
	if (status === 428) return "维度版本信息已失效，请加载最新版本后再保存";
	if (status === 400 || status === 422) return "请检查标红字段；当前输入已保留";
	return "操作未完成，当前输入已保留，请稍后重试";
};

export const isDimensionDefinitionVersionConflict = (error: unknown): boolean => {
	const response = (error as DimensionDefinitionErrorLike)?.response;
	return (
		(response?.status === 409 && response.data?.code === "DIMENSION_DEFINITION_REVISION_CONFLICT") ||
		response?.status === 412 ||
		response?.status === 428
	);
};
