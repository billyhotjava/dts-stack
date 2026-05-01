import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from "axios";
import { toast } from "sonner";
import type { Result } from "#/api";
import { ResultStatus } from "#/enum";
import { GLOBAL_CONFIG } from "@/global-config";
import { t } from "@/locales/i18n";
import { isLoginRouteActive, resolveCurrentAppPath, resolveLoginHref } from "@/routes/constants";
import useContextStore from "@/store/contextStore";
import userStore from "@/store/userStore";
import {
	isWithinPortalLoginGrace,
	markPortalSessionLogout,
	PORTAL_SESSION_STORAGE_KEYS,
	readPortalSessionTimestamp,
} from "@/utils/portalSessionStorage";
import { resolvePortalTokenExpiresAt } from "@/utils/sessionExpiry";

const axiosInstance = axios.create({
	baseURL: GLOBAL_CONFIG.apiBaseUrl,
	timeout: 50000,
	withCredentials: true,
	headers: { "Content-Type": "application/json;charset=utf-8" },
});

/** 后台服务不可用类错误（需要跳登录） */
const SERVICE_UNAVAILABLE_STATUSES = new Set([502, 503, 504]);
const IS_PRODUCTION = import.meta.env.PROD;
const IS_LOCAL_DEV_HOST =
	typeof window !== "undefined" &&
	(["localhost", "127.0.0.1", "::1"].includes(window.location.hostname || "") ||
		(window.location.hostname || "").endsWith(".local"));

const isSuccessStatus = (status: unknown): boolean => {
	if (status === ResultStatus.SUCCESS) return true;
	if (typeof status === "string") {
		const normalized = status.trim().toUpperCase();
		if (!normalized) return false;
		if (normalized === "SUCCESS" || normalized === "OK") return true;
		if (!Number.isNaN(Number(normalized))) {
			return Number(normalized) === ResultStatus.SUCCESS;
		}
	}
	if (typeof status === "number") {
		return status === ResultStatus.SUCCESS;
	}
	return false;
};

const TEST_SESSION_ENABLED =
	!IS_PRODUCTION &&
	IS_LOCAL_DEV_HOST &&
	String(import.meta.env.VITE_TEST_LONG_SESSION ?? import.meta.env.VITE_TEST_SESSION ?? "false").toLowerCase() ===
		"true";
const TEST_SESSION_REFRESH_MS = Number(import.meta.env.VITE_TEST_SESSION_PING_MS ?? 5 * 60 * 1000);
const TEST_SESSION_MAX_AGE_MS = Number(import.meta.env.VITE_TEST_SESSION_MAX_AGE_MS ?? 4 * 60 * 60 * 1000);
const LOGIN_REQUEST_SUPPRESS_STALE_MS = 15_000;

export type PortalRefreshResult = {
	authenticated: boolean;
	tokenExpiresAt?: number;
	expiresIn?: number;
	portalExpiresAt?: string;
	portalExpiresIn?: number;
};

type PortalSessionProbe = {
	authenticated?: boolean;
	remainingSeconds?: number | null;
	reason?: "CONCURRENT" | "EXPIRED" | "LOGOUT";
};

// Token refresh coordination to avoid stampedes
let refreshingPromise: Promise<PortalRefreshResult | null> | null = null;
let loginRequestGraceUntil = 0;

function hasRecentLoginGraceWindow(): boolean {
	return isWithinPortalLoginGrace(15_000);
}

/**
 * 登录后的"安全窗"：用于 session/status 探活场景。portal_session 刚写入后，
 * 并发的探活请求可能在不同事务里查不到它。把前 5 秒视为可信窗口，三处探活（LoginPage、
 * SessionManager、LoginAuthGuard）统一使用本 helper 跳过探活、直接信任本地 token。
 * 此窗口外才会让探活正常工作以支持异地登录顶掉、session 失效等场景。
 */
export function isWithinLoginProbeGrace(): boolean {
	return isWithinPortalLoginGrace(5000);
}

function isDefinitivePortalSessionInactive(status?: PortalSessionProbe | null): boolean {
	if (!status || status.authenticated !== false) {
		return false;
	}
	return (
		status.reason === "CONCURRENT" ||
		status.reason === "EXPIRED" ||
		status.reason === "LOGOUT" ||
		status.remainingSeconds === 0
	);
}

async function probePortalSessionStatus(): Promise<PortalSessionProbe | null> {
	try {
		return await axiosInstance.get("/session/status", {
			_skipAuth: true,
		} as any);
	} catch {
		return null;
	}
}

function forceLogoutToLogin() {
	userStore.getState().actions.clearUserInfoAndToken();
	markPortalSessionLogout(Date.now());
	if (typeof window !== "undefined" && !isLoginRouteActive()) {
		redirectToLoginWithCurrentPath();
	}
}

function captureRequestSessionSnapshot(config: AxiosRequestConfig) {
	const now = Date.now();
	(config as any)._portalSessionRequestTs = now;
	(config as any)._portalSessionLoginTs = readPortalSessionTimestamp(PORTAL_SESSION_STORAGE_KEYS.LOGIN_TS);
}

function shouldIgnoreStaleSessionFailure(config?: AxiosRequestConfig | null): boolean {
	const requestTs = Number((config as any)?._portalSessionRequestTs || 0);
	const requestLoginTs = Number((config as any)?._portalSessionLoginTs || 0);
	const currentLoginTs = readPortalSessionTimestamp(PORTAL_SESSION_STORAGE_KEYS.LOGIN_TS);
	if (currentLoginTs > 0 && (requestLoginTs <= 0 || currentLoginTs > requestLoginTs)) {
		return true;
	}
	if (requestTs > 0 && Date.now() < loginRequestGraceUntil) {
		return true;
	}
	return isLoginRouteActive();
}

function normalizeDate(value: unknown): string | undefined {
	if (typeof value === "string" && value.trim()) return value.trim();
	if (value instanceof Date) return value.toISOString();
	if (typeof value === "number" && Number.isFinite(value)) {
		try {
			return new Date(value).toISOString();
		} catch {
			return String(value);
		}
	}
	return undefined;
}

export async function refreshPortalSessionIfPossible(): Promise<PortalRefreshResult | null> {
	const { userToken, actions } = userStore.getState() as any;
	if (!userToken?.authenticated && !userToken?.accessToken) return null;
	// Only one refresh at a time
	if (!refreshingPromise) {
		const refreshTask = (async (): Promise<PortalRefreshResult | null> => {
			try {
				const resp: any = await axiosInstance.post("/keycloak/auth/refresh", {});
				const expiresIn = Number(resp?.expiresIn ?? resp?.data?.expiresIn ?? 0);
				const portalExpiresAt = normalizeDate(resp?.portalExpiresAt ?? resp?.data?.portalExpiresAt);
				const portalExpiresIn = Number(resp?.portalExpiresIn ?? resp?.data?.portalExpiresIn ?? 0);
				const authenticated = Boolean(resp?.authenticated ?? resp?.data?.authenticated);
				const tokenExpiresAt = resolvePortalTokenExpiresAt(
					{
						portalExpiresAt,
						portalExpiresIn,
						expiresIn,
						tokenExpiresAt: userToken?.tokenExpiresAt,
					},
					userToken?.tokenExpiresAt,
				);
				if (!authenticated) throw new Error("not_authenticated");
				const nextUserToken = {
					authenticated: true,
					tokenExpiresAt,
				};
				actions.setUserToken(nextUserToken);
				return {
					...nextUserToken,
					expiresIn: portalExpiresIn > 0 ? portalExpiresIn : expiresIn > 0 ? expiresIn : undefined,
					portalExpiresAt,
					portalExpiresIn: portalExpiresIn > 0 ? portalExpiresIn : undefined,
				};
			} catch {
				return null;
			}
		})();
		const coordinatedTask = refreshTask.finally(() => {
			if (refreshingPromise === coordinatedTask) {
				refreshingPromise = null;
			}
		});
		refreshingPromise = coordinatedTask;
	}
	return refreshingPromise;
}

export async function refreshTokenIfPossible(): Promise<boolean> {
	const refreshed = await refreshPortalSessionIfPossible();
	return Boolean(refreshed?.authenticated);
}

let keepAliveTimer: number | null = null;
function ensureKeepAliveTimer() {
	if (!TEST_SESSION_ENABLED || typeof window === "undefined") {
		return;
	}
	if (keepAliveTimer !== null) {
		return;
	}
	keepAliveTimer = window.setInterval(async () => {
		const { userToken } = userStore.getState();
		if (!userToken?.authenticated && !userToken?.accessToken) {
			return;
		}
		const loginTs = readPortalSessionTimestamp(PORTAL_SESSION_STORAGE_KEYS.LOGIN_TS);
		if (loginTs > 0 && Date.now() - loginTs > TEST_SESSION_MAX_AGE_MS) {
			return;
		}
		try {
			await refreshTokenIfPossible();
		} catch (error) {
			console.warn("[session] keep-alive refresh failed", error);
		}
	}, TEST_SESSION_REFRESH_MS);
}

if (typeof window !== "undefined") {
	ensureKeepAliveTimer();
	window.addEventListener("focus", ensureKeepAliveTimer);
}

function redirectToLoginWithCurrentPath() {
	if (typeof window === "undefined") {
		return;
	}
	location.replace(resolveLoginHref(resolveCurrentAppPath()));
}

function sanitizeHeadersForLog(headers: unknown): Record<string, unknown> | undefined {
	if (!headers || typeof headers !== "object") {
		return undefined;
	}
	const safeHeaders: Record<string, unknown> = {};
	for (const [key, value] of Object.entries(headers as Record<string, unknown>)) {
		const normalized = key.toLowerCase();
		safeHeaders[key] = normalized === "authorization" || normalized === "x-portal-access-token" ? "[redacted]" : value;
	}
	return safeHeaders;
}

function logApiRequest(config: AxiosRequestConfig) {
	if (IS_PRODUCTION) {
		return;
	}
	console.debug("API Request:", {
		method: config.method?.toUpperCase(),
		baseURL: config.baseURL,
		url: config.url,
		headers: sanitizeHeadersForLog(config.headers),
	});
}

function logApiResponse(res: AxiosResponse<unknown>) {
	if (IS_PRODUCTION) {
		return;
	}
	console.debug("API Response:", {
		status: res.status,
		url: res.config.url,
	});
}

function logApiRequestError(error: unknown) {
	if (IS_PRODUCTION) {
		return;
	}
	const message = error instanceof Error ? error.message : String(error ?? "unknown");
	console.warn("API Request Error:", message);
}

function logApiResponseError(error: AxiosError<Result>) {
	if (IS_PRODUCTION) {
		return;
	}
	console.warn("API Response Error:", {
		status: error.response?.status,
		url: error.response?.config?.url ?? error.config?.url,
		message: error.message,
	});
}

axiosInstance.interceptors.request.use(
	(config) => {
		const url = config.url || "";
		const isAuthPath = url.includes("/keycloak/auth/");
		const isLoginPath = url.includes("/keycloak/auth/login") || url.includes("/keycloak/auth/platform/login");
		captureRequestSessionSnapshot(config);
		if (isLoginPath) {
			loginRequestGraceUntil = Date.now() + LOGIN_REQUEST_SUPPRESS_STALE_MS;
		}
		// For FormData uploads, let the browser set the proper multipart boundary
		if (typeof FormData !== "undefined" && config.data instanceof FormData) {
			if (config.headers) {
				delete (config.headers as any)["Content-Type"];
			}
		}

		// Inject active department header for ABAC gates (non-auth endpoints)
		if (!isAuthPath) {
			try {
				const ctx = useContextStore.getState();
				// Initialize defaults from user profile once
				ctx.actions.initDefaults();
				if (ctx.activeDept) {
					(config.headers as any)["X-Active-Dept"] = ctx.activeDept;
				} else {
					// Fallback: derive dept from user profile when store hasn't been hydrated yet
					try {
						const ui: any = userStore.getState().userInfo || {};
						const pick = (v: any): string => {
							if (Array.isArray(v)) return String(v[0] ?? "").trim();
							if (v == null) return "";
							return String(v).trim();
						};
						const attrs: any = ui.attributes || {};
						const fromAttrs = pick(attrs.dept_code || attrs.deptCode || attrs.department);
						const fromTop = pick(ui.dept_code || ui.deptCode);
						const dept = (fromAttrs || fromTop || "").trim();
						if (dept) {
							(config.headers as any)["X-Active-Dept"] = dept;
							try {
								ctx.actions.setActiveDept(dept);
							} catch {}
						}
					} catch (_e) {
						// ignore
					}
				}
			} catch (_e) {
				console.warn("Failed to inject active context headers", _e);
			}
		}

		logApiRequest(config);
		return config;
	},
	(error) => {
		logApiRequestError(error);
		return Promise.reject(error);
	},
);

axiosInstance.interceptors.response.use(
	(res: AxiosResponse<Result<any>>) => {
		logApiResponse(res);

		const isBlobResponse =
			(res.config as any)?.responseType === "blob" || (typeof Blob !== "undefined" && res.data instanceof Blob);
		if (isBlobResponse) {
			return res.data as any;
		}

		if (!res.data) throw new Error(t("sys.api.apiRequestFailed"));

		// 特殊处理Keycloak API
		if (res.config.url?.includes("/keycloak/")) {
			// 检查是否是标准响应格式（包含status字段）
			if (res.data && typeof res.data === "object" && "status" in res.data) {
				// 对于标准响应格式，返回data字段
				const { status, data, message } = res.data;
				if (isSuccessStatus(status)) {
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
		if (isSuccessStatus(status)) {
			return data;
		}
		throw new Error(message || t("sys.api.apiRequestFailed"));
	},
	async (error: AxiosError<Result>) => {
		const { response, message } = error || {};
		const requestUrl = response?.config?.url ?? "";
		const isLoginRequest =
			typeof requestUrl === "string" &&
			(requestUrl.includes("/keycloak/auth/login") || requestUrl.includes("/keycloak/auth/platform/login"));
		const isRefreshRequest = typeof requestUrl === "string" && requestUrl.includes("/keycloak/auth/refresh");
		const shouldSuppressAuthHandling = typeof requestUrl === "string" && requestUrl.includes("/keycloak/localization/");
		const skipErrorToast =
			(response?.config as any)?._skipErrorToast === true || (error?.config as any)?._skipErrorToast === true;
		// SQL IDE tab race condition: if another window/session already closed the tab,
		// the backend returns 404 "tab not found". Frontend useTabStore reconciles via
		// its idRemap chain on the next hydrate; surfacing a toast here only confuses users.
		// We still reject the promise so callers can handle it; only the toast is suppressed.
		const isSqlTabRaceCondition =
			typeof requestUrl === "string" && requestUrl.includes("/sql/v2/tabs") && response?.status === 404;
		if (!(isLoginRequest && response?.status === 401)) {
			logApiResponseError(error);
		}
		const apiBody: any = response?.data || {};
		const headers = response?.headers || {};
		const sessionExpiredHeader =
			typeof headers?.["x-session-expired"] === "string"
				? headers["x-session-expired"].toLowerCase() === "true"
				: false;
		const sessionConflictHeader =
			typeof headers?.["x-session-conflict"] === "string"
				? headers["x-session-conflict"].toLowerCase() === "true"
				: false;
		const errCode: string | undefined = (apiBody && (apiBody as any).code) || undefined;
		// Prefer Problem Details fields when present; then fall back to common keys
		const problemDetail =
			(typeof apiBody === "object" &&
				(apiBody.detail || apiBody.message || apiBody.title || apiBody.error_description || apiBody.error)) ||
			undefined;
		// Attach first field error if available
		const fieldErrors: any[] = Array.isArray((apiBody as any)?.fieldErrors) ? (apiBody as any).fieldErrors : [];
		const fieldMsg = fieldErrors.length
			? `${fieldErrors[0]?.field ?? "字段"}: ${fieldErrors[0]?.message ?? "非法"}`
			: "";
		const httpStatusMsg = response?.status
			? t(`sys.api.errMsg${response.status}`, { defaultValue: "" })
			: !response
				? t("sys.api.networkExceptionMsg")
				: "";
		const errMsg =
			(problemDetail ? String(problemDetail) : "") || fieldMsg || httpStatusMsg || message || t("sys.api.errorMessage");
		// Friendly hints for security codes
		let hint = "";
		switch (String(errCode || "")) {
			case "dts-sec-0001":
				hint = "动作权限不足，请联系管理员申请更高权限";
				break;
			case "dts-sec-0002":
				hint = "作用域/部门不匹配，请在右上角切换上下文后重试";
				break;
			case "dts-sec-0003":
				hint = "权限不足，当前密级不可访问";
				break;
			case "dts-sec-0004":
				hint = "需要审批授权后才能访问数据内容";
				break;
			case "dts-sec-0007":
				hint = "资源不存在或不可见";
				break;
			case "dts-sec-0005":
			case "dts-sec-0006":
				hint = "缺少或非法上下文，请设置作用域/部门后重试";
				break;
			default:
				break;
		}
		const combinedMsg = hint ? `${errMsg}（${hint}）` : errMsg;
		const isSqlTabOptimisticConflict =
			typeof requestUrl === "string" &&
			requestUrl.includes("/sql/v2/tabs") &&
			response?.status === 409 &&
			/stale updatedAt/i.test(combinedMsg);
		(error as any).message = combinedMsg;
		const sessionErrorByMessage =
			typeof combinedMsg === "string" && /已在其他位置登录|会话已超时|重新登录|session/i.test(combinedMsg);
		const shouldForceLogout = sessionExpiredHeader || sessionConflictHeader || sessionErrorByMessage;
		if (shouldForceLogout && !isLoginRequest && shouldIgnoreStaleSessionFailure(response?.config || error?.config)) {
			console.warn("[auth] Ignoring stale session failure from an older request", {
				url: requestUrl,
				status: response?.status,
			});
			return Promise.reject(error);
		}
		if (response?.status === 401 && !shouldSuppressAuthHandling && isRefreshRequest) {
			// Let SessionManager / route guards make the final decision for refresh failures.
			// This avoids a single transient upstream refresh error forcing the SPA into a dead state.
			return Promise.reject(error);
		}
		// Attempt silent refresh on 401 (non-auth endpoints) and retry once
		if (response?.status === 401 && !shouldSuppressAuthHandling && !isLoginRequest && !isRefreshRequest) {
			const cfg = response.config || {};
			// prevent infinite loop
			if (!(cfg as any)._retry) {
				const refreshed = await refreshTokenIfPossible();
				if (refreshed) {
					(cfg.headers as any) = (cfg.headers as any) || {};
					delete (cfg.headers as any).Authorization;
					delete (cfg.headers as any).authorization;
					(cfg as any)._retry = true;
					try {
						return await axiosInstance.request(cfg as any);
					} catch (_e) {
						// fallthrough to logout handling below
					}
				}
			}
			// Grace window just after login to avoid kicking user out on in-flight 401s
			if (hasRecentLoginGraceWindow()) {
				console.warn("[auth] Suppressing auto-logout due to grace window after login");
				return Promise.reject(error);
			}
			const portalStatus = await probePortalSessionStatus();
			const mustForceLogout = shouldForceLogout || isDefinitivePortalSessionInactive(portalStatus);
			if (!mustForceLogout) {
				return Promise.reject(error);
			}
			forceLogoutToLogin();
		} else if (shouldForceLogout && !TEST_SESSION_ENABLED && !isLoginRequest) {
			if (hasRecentLoginGraceWindow()) {
				console.warn("[auth] Suppressing forced logout due to grace window after login");
				return Promise.reject(error);
			}
			forceLogoutToLogin();
		} else {
			if (
				!skipErrorToast &&
				!shouldSuppressAuthHandling &&
				!isLoginRequest &&
				!isSqlTabRaceCondition &&
				!isSqlTabOptimisticConflict
			) {
				const isServiceUnavailable = !response || SERVICE_UNAVAILABLE_STATUSES.has(response.status ?? 0);
				if (isServiceUnavailable) {
					toast.error(combinedMsg, { id: "service-error", duration: 5000, position: "top-center" });
				} else {
					toast.error(combinedMsg, { id: "api-error", position: "top-center" });
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
	patch<T = unknown>(config: AxiosRequestConfig): Promise<T> {
		return this.request<T>({ ...config, method: "PATCH" });
	}
	delete<T = unknown>(config: AxiosRequestConfig): Promise<T> {
		return this.request<T>({ ...config, method: "DELETE" });
	}
	request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
		return axiosInstance.request<any, T>(config);
	}
}

export default new APIClient();
