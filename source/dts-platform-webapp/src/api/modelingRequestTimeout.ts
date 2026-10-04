import type { AxiosRequestConfig } from "axios";

export const MODELING_REQUEST_TIMEOUT_MS = 60_000;

export function withModelingRequestTimeout<T extends AxiosRequestConfig>(config: T): T & { timeout: number } {
	const rawTimeout = Number(config.timeout);
	const currentTimeout = Number.isFinite(rawTimeout) ? rawTimeout : 0;
	return {
		...config,
		timeout: currentTimeout > MODELING_REQUEST_TIMEOUT_MS ? currentTimeout : MODELING_REQUEST_TIMEOUT_MS,
	};
}
