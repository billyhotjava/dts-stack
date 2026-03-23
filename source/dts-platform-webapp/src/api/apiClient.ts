import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from "axios";
import { toast } from "sonner";
import type { Result } from "#/api";
import { ResultStatus } from "#/enum";
import { GLOBAL_CONFIG } from "@/global-config";
import { t } from "@/locales/i18n";
import { isLoginRouteActive, resolveLoginHref } from "@/routes/constants";
import useContextStore from "@/store/contextStore";
import userStore from "@/store/userStore";

const axiosInstance = axios.create({
	baseURL: GLOBAL_CONFIG.apiBaseUrl,
	timeout: 15000,
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
const TEST_SESSION_REFRESH_MS = Number(import.meta.env.VITE_TEST_SESSION_PING_MS ?? 5 * 60 * 1000);
const TEST_SESSION_MAX_AGE_MS = Number(import.meta.env.VITE_TEST_SESSION_MAX_AGE_MS ?? 4 * 60 * 60 * 1000);

// ── Token refresh coordination ──
// Single in-flight refresh to avoid stampedes.
let refreshingPromise: Promise<boolean> | null = null;

async function refreshTokenIfPossible(): Promise<boolean> {
	const { userToken, actions } = userStore.getState() as any;
	const refresh = String(userToken?.refreshToken || "").trim();
	if (!refresh) return false;

	// Reuse in-flight refresh
	if (refreshingPromise) {
		return refreshingPromise;
	}

	refreshingPromise = (async (): Promise<boolean> => {
		try {
			const resp: any = await axiosInstance.post(
				"/keycloak/auth/refresh",
				{ refreshToken: refresh },
				// Bypass auth header — this IS the auth endpoint
				{ headers: { Authorization: undefined }, _isRefreshRequest: true } as any,
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
			if (!nextAccess) return false;
			actions.setUserToken({
				accessToken: nextAccess,
				refreshToken: nextRefresh || refresh,
				adminAccessToken: adminAccessToken || userToken?.adminAccessToken,
				adminRefreshToken: adminRefreshToken || userToken?.adminRefreshToken,
				adminAccessTokenExpiresAt: adminAccessTokenExpiresAt || userToken?.adminAccessTokenExpiresAt,
				adminRefreshTokenExpiresAt: adminRefreshTokenExpiresAt || userToken?.adminRefreshTokenExpiresAt,
			});
			return true;
		} catch {
			return false;
		} finally {
			// Clear after a tick so concurrent awaiters get the same result
			setTimeout(() => { refreshingPromise = null; }, 50);
		}
	})();

	return refreshingPromise;
}

// ── Keep-alive timer ──
const KEEP_ALIVE_INTERVAL_MS = TEST_SESSION_ENABLED
	? TEST_SESSION_REFRESH_MS
	: 4 * 60 * 1000; // 4 min — refresh before typical 5-min access token expiry

let keepAliveTimer: number | null = null;
function ensureKeepAliveTimer() {
	if (typeof window === "undefined") return;
	if (keepAliveTimer !== null) return;
	keepAliveTimer = window.setInterval(async () => {
		const { userToken } = userStore.getState();
		if (!userToken?.refreshToken) return;
		if (TEST_SESSION_ENABLED) {
			const loginTs = Number(localStorage.getItem("dts.session.loginTs") || "0");
			if (loginTs > 0 && Date.now() - loginTs > TEST_SESSION_MAX_AGE_MS) return;
		}
		try {
			await refreshTokenIfPossible();
		} catch {
			// keep-alive is best-effort, don't propagate
		}
	}, KEEP_ALIVE_INTERVAL_MS);
}

if (typeof window !== "undefined") {
	ensureKeepAliveTimer();
	window.addEventListener("focus", ensureKeepAliveTimer);
	// When tab becomes visible again, proactively refresh token.
	// Debounce to avoid rapid-fire refreshes on quick tab switches.
	let visibilityRefreshScheduled = false;
	document.addEventListener("visibilitychange", () => {
		if (document.visibilityState !== "visible" || visibilityRefreshScheduled) return;
		const { userToken } = userStore.getState();
		if (!userToken?.refreshToken) return;
		visibilityRefreshScheduled = true;
		setTimeout(() => {
			visibilityRefreshScheduled = false;
			refreshTokenIfPossible().catch(() => {});
		}, 300);
	});
}

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
							try { ctx.actions.setActiveDept(dept); } catch {}
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
		const isRefreshRequest = Boolean((response?.config as any)?._isRefreshRequest);
		const shouldSuppressAuthHandling = typeof requestUrl === "string" && (
			requestUrl.includes("/keycloak/localization/")
			|| requestUrl.includes("/workbench/")
		);

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
		const problemDetail =
			(typeof apiBody === "object" &&
				(apiBody.detail || apiBody.message || apiBody.title || apiBody.error_description || apiBody.error)) ||
			undefined;
		const fieldErrors: any[] = Array.isArray((apiBody as any)?.fieldErrors) ? (apiBody as any).fieldErrors : [];
		const fieldMsg = fieldErrors.length
			? `${fieldErrors[0]?.field ?? "字段"}: ${fieldErrors[0]?.message ?? "非法"}`
			: "";
		const errMsg = (problemDetail ? String(problemDetail) : "") || fieldMsg || message || t("sys.api.errorMessage");
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
				const loginTs = Number(localStorage.getItem("dts.session.loginTs") || "0");
				if (loginTs > 0 && Date.now() - loginTs < 2000) {
					console.warn("[auth] Suppressing auto-logout due to grace window after login");
					return Promise.reject(error);
				}
			} catch {}
			// Force logout only when server explicitly signals session issue
			if (shouldForceLogout) {
				userStore.getState().actions.clearUserInfoAndToken();
				try { localStorage.setItem("dts.session.logoutTs", String(Date.now())); } catch {}
				if (typeof window !== "undefined" && !isLoginRouteActive()) {
					location.replace(resolveLoginHref());
				}
			}
			// Otherwise: just reject — caller handles the error, don't destroy session
		} else if (shouldForceLogout && !TEST_SESSION_ENABLED) {
			userStore.getState().actions.clearUserInfoAndToken();
			try { localStorage.setItem("dts.session.logoutTs", String(Date.now())); } catch {}
			if (typeof window !== "undefined" && !isLoginRouteActive()) {
				location.replace(resolveLoginHref());
			}
		} else {
			if (!shouldSuppressAuthHandling && !isLoginRequest) {
				toast.error(combinedMsg, { position: "top-center" });
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
