import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from "axios";
import { toast } from "sonner";
import type { Result } from "#/api";
import { ResultStatus } from "#/enum";
import { GLOBAL_CONFIG } from "@/global-config";
import { t } from "@/locales/i18n";
import userStore from "@/store/userStore";

const isBusinessSuccess = (status: unknown): boolean => {
	if (typeof status === "number") {
		return status === 200;
	}
	if (typeof status === "string") {
		const normalized = status.trim().toUpperCase();
		if (!normalized) return false;
		if (normalized === ResultStatus.SUCCESS) {
			return true;
		}
		return normalized === "OK" || normalized === "200";
	}
	return false;
};

const hasHeaderFlag = (headers: Record<string, unknown> | undefined, key: string): boolean => {
	if (!headers) return false;
	const lower = key.toLowerCase();
	const value = headers[key] ?? headers[lower];
	if (value === undefined) return false;
	if (typeof value === "string") {
		return value.length === 0 || value.toLowerCase() === "true";
	}
	return Boolean(value);
};

const extractFieldErrorMessage = (payload: unknown): string | undefined => {
	if (!payload || typeof payload !== "object") {
		return undefined;
	}
	const data = payload as Record<string, unknown>;
	const pickMessage = (entry: unknown): string | undefined => {
		if (!entry || typeof entry !== "object") return undefined;
		const obj = entry as Record<string, unknown>;
		const field =
			typeof obj.field === "string" ? obj.field.trim() : typeof obj.name === "string" ? obj.name.trim() : "";
		const msg =
			typeof obj.message === "string"
				? obj.message.trim()
				: typeof obj.reason === "string"
					? obj.reason.trim()
					: typeof obj.description === "string"
						? obj.description.trim()
						: "";
		if (field && msg) return `${field}: ${msg}`;
		if (msg) return msg;
		return undefined;
	};
	const fieldErrors = Array.isArray(data.fieldErrors) ? data.fieldErrors : undefined;
	if (fieldErrors?.length) {
		const resolved = pickMessage(fieldErrors[0]);
		if (resolved) return resolved;
	}
	const violations = Array.isArray((data as any).violations) ? (data as any).violations : undefined;
	if (violations?.length) {
		const resolved = pickMessage(violations[0]);
		if (resolved) return resolved;
	}
	return undefined;
};

const extractProblemMessage = (payload: unknown): string | undefined => {
	if (!payload) {
		return undefined;
	}
	if (typeof payload === "string") {
		const trimmed = payload.trim();
		return trimmed ? trimmed : undefined;
	}
	if (typeof payload !== "object") {
		return undefined;
	}
	const data = payload as Record<string, unknown>;
	const candidateKeys = [
		"detail",
		"message",
		"msg",
		"description",
		"error_description",
		"errorMessage",
		"error",
		"title",
	];
	for (const key of candidateKeys) {
		const value = data[key];
		if (typeof value === "string") {
			const trimmed = value.trim();
			if (trimmed) return trimmed;
		}
	}
	const nestedData = (data as any).data;
	if (nestedData && typeof nestedData === "object" && nestedData !== payload) {
		const nestedMessage = extractProblemMessage(nestedData);
		if (nestedMessage) return nestedMessage;
	}
	return extractFieldErrorMessage(payload);
};

const axiosInstance = axios.create({
	baseURL: GLOBAL_CONFIG.apiBaseUrl,
	timeout: 50000,
	headers: { "Content-Type": "application/json;charset=utf-8" },
});

axiosInstance.interceptors.request.use(
	(config) => {
		// 从 userStore 获取访问令牌，但对无需鉴权或认证端点不附带令牌，避免无谓的鉴权失败
		const { userToken } = userStore.getState();
		const url = typeof config.url === "string" ? config.url : "";
		const isAuthPath = url.includes("/keycloak/auth/");
		const skipAuth = url.includes("/keycloak/localization/") || isAuthPath;
		if (!skipAuth && userToken.accessToken) {
			config.headers.Authorization = `Bearer ${userToken.accessToken}`;
		}

		// 添加请求日志
		console.log("API Request:", config.method?.toUpperCase(), config.baseURL, config.url, config);
		return config;
	},
	(error) => {
		console.error("API Request Error:", error);
		return Promise.reject(error);
	},
);

let isRefreshing = false;
let pendingQueue: Array<() => void> = [];

axiosInstance.interceptors.response.use(
	(res: AxiosResponse<Result<any>>) => {
		console.log("API Response:", res.status, res.config.url, res.data);

		if (!res.data) throw new Error(t("sys.api.apiRequestFailed"));

		// 特殊处理Keycloak API
		if (res.config.url?.includes("/keycloak/")) {
			// 检查是否是标准响应格式（包含status字段）
			if (res.data && typeof res.data === "object" && "status" in res.data) {
				// 对于标准响应格式，返回data字段
				const { status, data, message } = res.data;
				if (isBusinessSuccess(status)) {
					return data;
				}
				throw new Error(message || t("sys.api.apiRequestFailed"));
			} else {
				// 对于直接返回数据的API（如用户列表），直接返回响应数据
				return res.data;
			}
		}

		// 处理标准API响应格式
		const { status, data, message } = res.data;
		if (isBusinessSuccess(status)) {
			return data;
		}
		throw new Error(message || t("sys.api.apiRequestFailed"));
	},
	(error: AxiosError<Result>) => {
		const { response, message, code, config } = error || {};
		const requestUrl = typeof config?.url === "string" ? config.url : "";
		const isLoginRequest = typeof requestUrl === "string" && requestUrl.includes("/keycloak/auth/login");
		const isRefreshRequest = typeof requestUrl === "string" && requestUrl.includes("/keycloak/auth/refresh");
		const shouldSuppressAuthHandling = typeof requestUrl === "string" && requestUrl.includes("/keycloak/localization/");
		const isKeycloakAdminEndpoint =
			typeof requestUrl === "string" &&
			requestUrl.includes("/keycloak/") &&
			!requestUrl.includes("/keycloak/auth/") &&
			!requestUrl.includes("/keycloak/localization/");

		if (!response) {
			const fallbackBase = config?.baseURL ?? axiosInstance.defaults.baseURL ?? GLOBAL_CONFIG.apiBaseUrl ?? "";
			const resolveTarget = () => {
				const path = requestUrl || "";
				if (/^https?:\/\//i.test(path)) {
					return path;
				}

				const resolveWithBase = (base: string | undefined): string | undefined => {
					if (!base) {
						return undefined;
					}
					try {
						const origin =
							typeof window !== "undefined" && window.location?.origin ? window.location.origin : "http://localhost";
						const baseUrl = new URL(base, origin);
						if (path.startsWith("/")) {
							const basePath = baseUrl.pathname.endsWith("/") ? baseUrl.pathname.slice(0, -1) : baseUrl.pathname;
							baseUrl.pathname = `${basePath}${path}`;
						} else {
							const nested = new URL(path, baseUrl);
							baseUrl.pathname = nested.pathname;
							baseUrl.search = nested.search;
							baseUrl.hash = nested.hash;
						}
						return baseUrl.toString();
					} catch {
						return undefined;
					}
				};

				const resolvedFromBase = resolveWithBase(fallbackBase);
				if (resolvedFromBase) {
					return resolvedFromBase;
				}

				if (typeof window !== "undefined" && window.location?.origin) {
					try {
						return new URL(path || "", window.location.origin).toString();
					} catch {
						// ignore
					}
				}
				return fallbackBase ? `${fallbackBase}${path}` : path;
			};
			const resolvedTarget = resolveTarget();
			const normalizedMessage = String(message || "").toLowerCase();
			const isTimeout = code === "ECONNABORTED" || normalizedMessage.includes("timeout");
			const networkCodes = new Set(["ERR_NETWORK", "ERR_NETWORK_CHANGED", "ERR_CONNECTION_REFUSED"]);
			const isNetworkError =
				!isTimeout &&
				(networkCodes.has(code ?? "") ||
					normalizedMessage === "network error" ||
					normalizedMessage === "net::err_network_changed");
			const offline = typeof navigator !== "undefined" && navigator.onLine === false;
			const toastMsg = isTimeout
				? t("sys.api.apiTimeoutMessage")
				: offline
					? t("sys.api.networkExceptionMsg")
					: t("sys.api.networkExceptionMsg");
			(error as any).message = toastMsg;
			if (!shouldSuppressAuthHandling && !isLoginRequest) {
				toast.error(toastMsg, { id: "api-network-error", position: "top-center" });
			}
			console.error("API Network Error:", {
				code,
				requestUrl,
				baseURL: fallbackBase,
				target: resolvedTarget,
				originalMessage: message,
			});
			const original = config as (AxiosRequestConfig & { _networkRetry?: boolean }) | undefined;
			if (
				isNetworkError &&
				original &&
				!original._networkRetry &&
				["get", "head", "options"].includes(String(original.method || "get").toLowerCase())
			) {
				original._networkRetry = true;
				return new Promise((resolve, reject) => {
					setTimeout(() => {
						axiosInstance.request(original).then(resolve).catch(reject);
					}, 400);
				});
			}
			return Promise.reject(error);
		}

		if (!(isLoginRequest && response?.status === 401)) {
			console.error("API Response Error:", response?.status, response?.data, error.message);
		}

		const headers = response?.headers as Record<string, unknown> | undefined;
		const responseBody = response?.data;
		const resolvedMessage = extractProblemMessage(responseBody);
		const statusHint =
			typeof response?.status === "number" ? t(`sys.api.errMsg${response.status}`, { defaultValue: "" }) : "";
		const errMsg = resolvedMessage || statusHint || message || t("sys.api.errorMessage");
		(error as any).message = errMsg;
		if (!shouldSuppressAuthHandling) {
			toast.error(errMsg, { position: "top-center" });
		}

		if (response?.status === 401) {
			const state = userStore.getState();
			const conflictHeader = hasHeaderFlag(headers, "x-session-conflict");
			const expiredHeader = hasHeaderFlag(headers, "x-session-expired");
			if (conflictHeader || expiredHeader) {
				state.actions.clearUserInfoAndToken();
				return Promise.reject(error);
			}
			// In development, relax auto-logout to ease debugging
			if (import.meta.env?.DEV) {
				console.warn("[DEV] 401 received; skipping auto logout");
			} else {
				// 优先尝试使用 refreshToken 静默续期并重试原请求（最多一次）
				// accessToken is intentionally not read here; we'll use the refreshed token when available
				const { refreshToken } = (state.userToken || ({} as any)) as any;
				const original = error.config as AxiosRequestConfig & { _retry?: boolean };
				if (!isRefreshRequest && !isLoginRequest && refreshToken && !original._retry) {
					if (isRefreshing) {
						return new Promise((resolve) => {
							pendingQueue.push(() => resolve(axiosInstance.request(original)));
						});
					}
					original._retry = true;
					isRefreshing = true;
					return import("@/api/services/userService").then(({ default: userService }) =>
						userService
							.refresh(refreshToken)
							.then((res: any) => {
								const nextAccess = res?.accessToken as string | undefined;
								const nextRefresh = (res?.refreshToken as string | undefined) || refreshToken;
								if (nextAccess) {
									state.actions.setUserToken({ accessToken: nextAccess, refreshToken: nextRefresh });
									// 更新原请求的认证头后重试
									original.headers = original.headers || {};
									(original.headers as any).Authorization = `Bearer ${nextAccess}`;
									pendingQueue.forEach((cb) => cb());
									pendingQueue = [];
									return axiosInstance.request(original);
								}
								// 没有新token则直接抛出，走下方清理逻辑
								throw error;
							})
							.finally(() => {
								isRefreshing = false;
							}),
					);
				}
				// Only force logout for definite auth failures (refresh failure)
				// Do NOT auto-logout for login failures or Keycloak admin endpoints that may 401/403 by design
				if (isRefreshRequest) {
					userStore.getState().actions.clearUserInfoAndToken();
				} else if (isLoginRequest || isKeycloakAdminEndpoint || shouldSuppressAuthHandling) {
					// keep session; allow user to continue with permitted features
					console.warn("[PROD] 401 on non-auth admin endpoint; session preserved");
				} else {
					userStore.getState().actions.clearUserInfoAndToken();
				}
			}
		}
		return Promise.reject(error);
	},
);

class APIClient {
	get<T = unknown>(config: AxiosRequestConfig): Promise<T> {
		return this.request<T>({ ...config, method: "GET" });
	}
	post<T = unknown>(config: AxiosRequestConfig): Promise<T> {
		return this.request<T>({ ...config, method: "POST" });
	}
	put<T = unknown>(config: AxiosRequestConfig): Promise<T> {
		return this.request<T>({ ...config, method: "PUT" });
	}
	delete<T = unknown>(config: AxiosRequestConfig): Promise<T> {
		return this.request<T>({ ...config, method: "DELETE" });
	}
	request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
		return axiosInstance.request<any, T>(config);
	}
}

export default new APIClient();
