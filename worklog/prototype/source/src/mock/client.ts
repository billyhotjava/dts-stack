import { type Result, ResultStatus } from "@/types/api";

/** 是否走 mock；置 0 时由 services 改走真实 axios（预留 seam）。 */
export const USE_MOCK = String(import.meta.env.VITE_USE_MOCK ?? "1") !== "0";

const BASE_DELAY = Number(import.meta.env.VITE_MOCK_DELAY ?? 240);

function delay(ms: number): Promise<void> {
	return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 模拟一次成功响应，带可调网络延迟，返回与现网同构的 Result<T>。 */
export async function ok<T>(data: T, message = "成功"): Promise<Result<T>> {
	await delay(BASE_DELAY + Math.random() * 140);
	return { status: ResultStatus.SUCCESS, message, data };
}

/** 模拟一次失败响应。 */
export async function fail<T>(message: string, data: T): Promise<Result<T>> {
	await delay(BASE_DELAY);
	return { status: ResultStatus.ERROR, message, data };
}

/** 从 Result 解包 data；失败抛错，供 react-query/调用方处理。 */
export function unwrap<T>(result: Result<T>): T {
	if (result.status !== ResultStatus.SUCCESS) {
		throw new Error(result.message || "请求失败");
	}
	return result.data;
}
