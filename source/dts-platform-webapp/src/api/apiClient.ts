import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from "axios";
import { toast } from "sonner";
import type { Result } from "#/api";
import { ResultStatus } from "#/enum";
import { GLOBAL_CONFIG } from "@/global-config";
import { t } from "@/locales/i18n";
import { isLoginRouteActive, resolveCurrentAppPath, resolveLoginHref } from "@/routes/constants";
import useContextStore from "@/store/contextStore";
import userStore from "@/store/userStore";

const axiosInstance = axios.create({
	baseURL: GLOBAL_CONFIG.apiBaseUrl,
	timeout: 50000,
	headers: { "Content-Type": "application/json;charset=utf-8" },
});

/** 后台服务不可用类错误（需要跳登录） */
const SERVICE_UNAVAILABLE_STATUSES = new Set([502, 503, 504]);

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
	String(import.meta.env.VITE_TEST_LONG_SESSION ?? import.meta.env.VITE_TEST_SESSION ?? "false").toLowerCase() ===
	"true";
const TEST_SESSION_REFRESH_MS = Number(import.meta.env.VITE_TEST_SESSION_PING_MS ?? 5 * 60 * 1000);
const TEST_SESSION_MAX_AGE_MS = Number(import.meta.env.VITE_TEST_SESSION_MAX_AGE_MS ?? 4 * 60 * 60 * 1000);

// Token refresh coordination to avoid stampedes
let refreshingPromise: Promise<void> | null = null;
async function refreshTokenIfPossible(): Promise<boolean> {
	const { userToken, actions } = userStore.getState() as any;
	const refresh = String(userToken?.refreshToken || "").trim();
	if (!refresh) return false;
	// Only one refresh at a time
	if (!refreshingPromise) {
		refreshingPromise = (async () => {
			try {
				const resp: any = await axiosInstance.post(
					"/keycloak/auth/refresh",
					{ refreshToken: refresh },
					{ headers: { Authorization: undefined } },
				);
				const nextAccess = String(resp?.accessToken || resp?.data?.accessToken || "").trim();
				const nextRefresh = String(resp?.refreshToken || resp?.data?.refreshToken || "").trim();
				const adminAccessToken = String(resp?.adminAccessToken || resp?.data?.adminAccessToken || "").trim();
				const adminRefreshToken = String(resp?.adminRefreshToken || resp?.data?.adminRefreshToken || "").trim();
				const adminAccessTokenExpiresAt = String(
					resp?.adminAccessTokenExpiresAt || resp?.data?.adminAccessTokenExpiresAt || "",
				).trim();
				const adminRefreshTokenExpiresAt = String(
					resp?.adminRefreshTokenExpiresAt || resp?.data?.adminRefreshTokenExpiresAt || "",
				).trim();
				if (!nextAccess) throw new Error("no_access_token");
				actions.setUserToken({
					accessToken: nextAccess,
					refreshToken: nextRefresh || refresh,
					adminAccessToken: adminAccessToken || userToken?.adminAccessToken,
					adminRefreshToken: adminRefreshToken || userToken?.adminRefreshToken,
					adminAccessTokenExpiresAt: adminAccessTokenExpiresAt || userToken?.adminAccessTokenExpiresAt,
					adminRefreshTokenExpiresAt: adminRefreshTokenExpiresAt || userToken?.adminRefreshTokenExpiresAt,
				});
			} finally {
				// Allow subsequent refresh attempts
				const p = refreshingPromise;
				refreshingPromise = null;
				// Wait a tick to let store update propagate
				try {
					await p;
				} catch {}
			}
		})();
	}
	try {
		await refreshingPromise;
		return true;
	} catch {
		return false;
	}
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
		if (!userToken?.refreshToken) {
			return;
		}
		const loginTs = Number(localStorage.getItem("dts.platform.session.loginTs") || "0");
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

axiosInstance.interceptors.request.use(
	(config) => {
		const { userToken } = userStore.getState();
		const url = config.url || "";
		const isAuthPath = url.includes("/keycloak/auth/");
		const skipAuth = (config as any)._skipAuth === true;
		// For FormData uploads, let the browser set the proper multipart boundary
		if (typeof FormData !== "undefined" && config.data instanceof FormData) {
			if (config.headers) {
				delete (config.headers as any)["Content-Type"];
			}
		}
		if (userToken.accessToken && !isAuthPath && !skipAuth) {
			const raw = String(userToken.accessToken).trim();
			const token = raw.startsWith("Bearer ") ? raw.slice(7).trim() : raw;
			if (token) {
				config.headers.Authorization = `Bearer ${token}`;
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

		console.log("API Request:", config.method?.toUpperCase(), config.baseURL, config.url, config);
		return config;
	},
	(error) => {
		console.error("API Request Error:", error);
		return Promise.reject(error);
	},
);

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
		const shouldSuppressAuthHandling = typeof requestUrl === "string" && requestUrl.includes("/keycloak/localization/");
		// SQL IDE tab race condition: if another window/session already closed the tab,
		// the backend returns 404 "tab not found". Frontend useTabStore reconciles via
		// its idRemap chain on the next hydrate; surfacing a toast here only confuses users.
		// We still reject the promise so callers can handle it; only the toast is suppressed.
		const isSqlTabRaceCondition =
			typeof requestUrl === "string" &&
			requestUrl.includes("/sql/v2/tabs") &&
			response?.status === 404;
		if (!(isLoginRequest && response?.status === 401)) {
			console.error("API Response Error:", response?.status, response?.data, error.message);
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
			: (!response ? t("sys.api.networkExceptionMsg") : "");
		const errMsg = (problemDetail ? String(problemDetail) : "") || fieldMsg || httpStatusMsg || message || t("sys.api.errorMessage");
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
		(error as any).message = combinedMsg;
		const sessionErrorByMessage =
			typeof combinedMsg === "string" && /已在其他位置登录|会话已超时|重新登录|session/i.test(combinedMsg);
		const shouldForceLogout = sessionExpiredHeader || sessionConflictHeader || sessionErrorByMessage;
		// Attempt silent refresh on 401 (non-auth endpoints) and retry once
		if (response?.status === 401 && !shouldSuppressAuthHandling && !isLoginRequest) {
			const cfg = response.config || {};
			// prevent infinite loop
			if (!(cfg as any)._retry) {
				// Race-safe: SessionManager may have already refreshed (or is about to).
				// Check if the store has a different token than what this request used.
				const requestAuth = String((cfg.headers as any)?.Authorization || "");
				const requestToken = requestAuth.startsWith("Bearer ") ? requestAuth.slice(7).trim() : "";
				const checkStoreForNewerToken = (): string | null => {
					const current = String(userStore.getState().userToken?.accessToken || "").trim();
					return (requestToken && current && requestToken !== current) ? current : null;
				};
				// Immediate check
				let newerToken = checkStoreForNewerToken();
				// If not yet updated, wait briefly for SessionManager to finish
				if (!newerToken && (sessionExpiredHeader || sessionConflictHeader)) {
					await new Promise((r) => setTimeout(r, 500));
					newerToken = checkStoreForNewerToken();
				}
				if (newerToken) {
					(cfg.headers as any).Authorization = `Bearer ${newerToken}`;
					(cfg as any)._retry = true;
					try {
						return await axiosInstance.request(cfg as any);
					} catch (_e) {
						// fallthrough to normal refresh below
					}
				}
				const refreshed = await refreshTokenIfPossible();
				if (refreshed) {
					// retry original request with updated access token
					const { userToken } = userStore.getState();
					(cfg.headers as any) = (cfg.headers as any) || {};
					(cfg.headers as any).Authorization = userToken?.accessToken ? `Bearer ${userToken.accessToken}` : undefined;
					(cfg as any)._retry = true;
					try {
						return await axiosInstance.request(cfg as any);
					} catch (_e) {
						// fallthrough to logout handling below
					}
				}
			}
			// Grace window just after login to avoid kicking user out on in-flight 401s
			try {
					const loginTs = Number(localStorage.getItem("dts.platform.session.loginTs") || "0");
				if (loginTs > 0 && Date.now() - loginTs < 2000) {
					console.warn("[auth] Suppressing auto-logout due to grace window after login");
					return Promise.reject(error);
				}
			} catch {}
			if (!TEST_SESSION_ENABLED || shouldForceLogout) {
				userStore.getState().actions.clearUserInfoAndToken();
				try {
						localStorage.setItem("dts.platform.session.logoutTs", String(Date.now()));
				} catch {}
				if (typeof window !== "undefined" && !isLoginRouteActive()) {
					redirectToLoginWithCurrentPath();
				}
			} else {
				console.warn("[DEV/TEST] 401 after refresh; skipping auto logout");
			}
		} else if (shouldForceLogout && !TEST_SESSION_ENABLED) {
			userStore.getState().actions.clearUserInfoAndToken();
			try {
				localStorage.setItem("dts.platform.session.logoutTs", String(Date.now()));
			} catch {}
			if (typeof window !== "undefined" && !isLoginRouteActive()) {
				redirectToLoginWithCurrentPath();
			}
		} else {
			if (!shouldSuppressAuthHandling && !isLoginRequest && !isSqlTabRaceCondition) {
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
	delete<T = unknown>(config: AxiosRequestConfig): Promise<T> {
		return this.request<T>({ ...config, method: "DELETE" });
	}
	request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
		return axiosInstance.request<any, T>(config);
	}
}

export default new APIClient();
