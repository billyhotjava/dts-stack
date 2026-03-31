import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from "axios";
import { toast } from "sonner";
import type { Result } from "#/api";
import { ResultStatus } from "#/enum";
import { GLOBAL_CONFIG } from "@/global-config";
import { t } from "@/locales/i18n";
import { isLoginRouteActive } from "@/routes/constants";
import { refreshAccessToken, redirectToLoginWithReturn } from "@/auth/session-auth";
import { PLATFORM_LEGACY_SESSION_KEYS, PLATFORM_SESSION_KEYS } from "@/auth/session-keys";
import useContextStore from "@/store/contextStore";
import userStore from "@/store/userStore";
import { readStorageValue } from "@dts-session-core/storage";

/** 将 Axios 内部英文错误消息转换为中文，避免"Network Error"等直接透传给用户 */
function normalizeAxiosErrorMessage(msg: string | undefined): string {
	if (!msg) return "";
	const lower = msg.toLowerCase();
	if (lower === "network error" || lower.includes("err_network") || lower.includes("network changed")) {
		return "网络异常，请检查网络连接后重试";
	}
	if (lower.includes("timeout") || lower.includes("econnaborted")) {
		return "请求超时，请稍后重试";
	}
	if (lower.includes("econnrefused") || lower.includes("connection refused")) {
		return "无法连接到服务器，请稍后重试";
	}
	if (lower === "request failed with status code 0") {
		return "网络异常，请检查网络连接后重试";
	}
	return msg;
}

const axiosInstance = axios.create({
	baseURL: GLOBAL_CONFIG.apiBaseUrl,
	timeout: 30000,
	headers: { "Content-Type": "application/json;charset=utf-8" },
});

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
// TEST_SESSION_REFRESH_MS / TEST_SESSION_MAX_AGE_MS removed — keep-alive timer no longer lives here.

// ── Token refresh coordination ──
// Delegate to the shared single-flight refresh in session-auth.ts.
// This ensures apiClient, SessionManager, and analyticsApi all share one lock.
async function refreshTokenIfPossible(): Promise<boolean> {
	const result = await refreshAccessToken();
	return result !== null;
}

// ── Keep-alive timer — REMOVED ──
// The keep-alive timer (4-min interval + visibility change handler) has been removed.
// SessionManager now owns the sole proactive refresh schedule (JWT exp − 60s).
// This eliminates the dual-timer race where both timers could consume a single-use
// refresh token concurrently, causing one to fail and trigger a forced logout.

// ── Request interceptor ──
axiosInstance.interceptors.request.use(
	(config) => {
		const { userToken } = userStore.getState();
		const url = config.url || "";
		const isAuthPath = url.includes("/keycloak/auth/");
		// For FormData uploads, let the browser set the proper multipart boundary
		if (typeof FormData !== "undefined" && config.data instanceof FormData) {
			if (config.headers) {
				delete (config.headers as any)["Content-Type"];
			}
		}
		if (userToken.accessToken && !isAuthPath) {
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
				ctx.actions.initDefaults();
				if (ctx.activeDept) {
					(config.headers as any)["X-Active-Dept"] = ctx.activeDept;
				} else {
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
					} catch {}
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

// ── Response interceptor ──
axiosInstance.interceptors.response.use(
	(res: AxiosResponse<Result<any>>) => {
		console.log("API Response:", res.status, res.config.url, res.data);

		if (!res.data) throw new Error(t("sys.api.apiRequestFailed"));

		// Keycloak API: may or may not wrap in {status, data}
		if (res.config.url?.includes("/keycloak/")) {
			if (res.data && typeof res.data === "object" && "status" in res.data) {
				const { status, data, message } = res.data;
				if (isSuccessStatus(status)) return data;
				throw new Error(message || t("sys.api.apiRequestFailed"));
			}
			return res.data;
		}

		// Standard API response: {status, data, message}
		const { status, data, message } = res.data;
		if (isSuccessStatus(status)) return data;
		throw new Error(message || t("sys.api.apiRequestFailed"));
	},
	async (error: AxiosError<Result>) => {
		const { response, message } = error || {};
		const requestUrl = response?.config?.url ?? "";
		const isLoginRequest =
			typeof requestUrl === "string" &&
			(requestUrl.includes("/keycloak/auth/login") || requestUrl.includes("/keycloak/auth/platform/login"));
		// _isRefreshRequest is no longer set — refreshTokenIfPossible now uses raw fetch
		// via session-auth.ts and never passes through this axios interceptor.
		// The guard is kept as defense-in-depth in case a direct axiosInstance.post to
		// the refresh endpoint is introduced in the future.
		const isRefreshRequest =
			Boolean((response?.config as any)?._isRefreshRequest) ||
			(typeof requestUrl === "string" && requestUrl.includes("/keycloak/auth/refresh"));
		const shouldSuppressAuthHandling =
			typeof requestUrl === "string" &&
			(requestUrl.includes("/keycloak/localization/") || requestUrl.includes("/workbench/"));

		if (!(isLoginRequest && response?.status === 401)) {
			console.error("API Response Error:", response?.status, response?.data, error.message);
		}

		const apiBody: any = response?.data || {};
		const headers = response?.headers || {};
		const skipErrorToast = Boolean((response?.config as any)?._skipErrorToast);
		const sessionExpiredHeader =
			typeof headers?.["x-session-expired"] === "string"
				? headers["x-session-expired"].toLowerCase() === "true"
				: false;
		const sessionConflictHeader =
			typeof headers?.["x-session-conflict"] === "string"
				? headers["x-session-conflict"].toLowerCase() === "true"
				: false;
		const errCode: string | undefined = (apiBody && (apiBody as any).code) || undefined;
		const problemDetail =
			(typeof apiBody === "object" &&
				(apiBody.detail || apiBody.message || apiBody.title || apiBody.error_description || apiBody.error)) ||
			undefined;
		const fieldErrors: any[] = Array.isArray((apiBody as any)?.fieldErrors) ? (apiBody as any).fieldErrors : [];
		const fieldMsg = fieldErrors.length
			? `${fieldErrors[0]?.field ?? "字段"}: ${fieldErrors[0]?.message ?? "非法"}`
			: "";
		const normalizedMessage = normalizeAxiosErrorMessage(message);
		const errMsg = (problemDetail ? String(problemDetail) : "") || fieldMsg || normalizedMessage || t("sys.api.errorMessage");
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
		// Match explicit session-conflict / session-expired messages from the backend.
		// Deliberately excludes bare "session" — Keycloak errors like "Session not active"
		// should NOT trigger a force-logout (they're handled by the 401 refresh path).
		const sessionErrorByMessage =
			typeof combinedMsg === "string" &&
			/已在其他位置登录|会话已超时|重新登录|session.?conflict|session.?expired/i.test(combinedMsg);
		const shouldForceLogout = sessionExpiredHeader || sessionConflictHeader || sessionErrorByMessage;

		// ── 401 handling ──
		// Never intercept refresh requests themselves — avoid infinite loops
		if (response?.status === 401 && !shouldSuppressAuthHandling && !isLoginRequest && !isRefreshRequest) {
			const cfg = response.config || {};
			if (!(cfg as any)._retry) {
				const refreshed = await refreshTokenIfPossible();
				if (refreshed) {
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
			// Grace window just after login
			try {
				const loginTs = Number(
					readStorageValue(PLATFORM_SESSION_KEYS.loginTs, PLATFORM_LEGACY_SESSION_KEYS.loginTs, localStorage) || "0",
				);
				if (loginTs > 0 && Date.now() - loginTs < 2000) {
					console.warn("[auth] Suppressing auto-logout due to grace window after login");
					return Promise.reject(error);
				}
			} catch {}
			// Force logout only when server explicitly signals session issue
			if (shouldForceLogout) {
				userStore.getState().actions.clearUserInfoAndToken();
				try {
					localStorage.setItem(PLATFORM_SESSION_KEYS.logoutTs, String(Date.now()));
				} catch {}
				if (typeof window !== "undefined" && !isLoginRouteActive()) {
					redirectToLoginWithReturn();
				}
			}
			// Otherwise: just reject — caller handles the error, don't destroy session
		} else if (shouldForceLogout && !TEST_SESSION_ENABLED) {
			userStore.getState().actions.clearUserInfoAndToken();
			try {
				localStorage.setItem(PLATFORM_SESSION_KEYS.logoutTs, String(Date.now()));
			} catch {}
			if (typeof window !== "undefined" && !isLoginRouteActive()) {
				redirectToLoginWithReturn();
			}
		} else {
			if (!shouldSuppressAuthHandling && !isLoginRequest) {
				if (!skipErrorToast) {
					toast.error(combinedMsg, { position: "top-center" });
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
