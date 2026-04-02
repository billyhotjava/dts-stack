import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from "axios";
import { toast } from "sonner";
import type { Result } from "#/api";
import { ResultStatus } from "#/enum";
import { GLOBAL_CONFIG } from "@/global-config";
import { t } from "@/locales/i18n";
import { isLoginRouteActive } from "@/routes/constants";
import { fetchCurrentSession, redirectToLoginWithReturn, useRedirectIntentStore } from "@/auth/session-auth";
import { PLATFORM_LEGACY_SESSION_KEYS, PLATFORM_SESSION_KEYS } from "@/auth/session-keys";
import useContextStore from "@/store/contextStore";
import userStore from "@/store/userStore";
import { readStorageValue } from "@dts/session-core/storage";

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
	withCredentials: true,
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

axiosInstance.interceptors.request.use(
	(config) => {
		const url = config.url || "";
		const isAuthPath = url.includes("/keycloak/auth/");
		if (typeof FormData !== "undefined" && config.data instanceof FormData) {
			if (config.headers) {
				delete (config.headers as any)["Content-Type"];
			}
		}

		if (!isAuthPath) {
			try {
				const ctx = useContextStore.getState();
				ctx.actions.initDefaults();
				if (ctx.activeDept) {
					(config.headers as any)["X-Active-Dept"] = ctx.activeDept;
				} else {
					try {
						const ui: any = userStore.getState().userInfo || {};
						const pick = (value: any): string => {
							if (Array.isArray(value)) return String(value[0] ?? "").trim();
							if (value == null) return "";
							return String(value).trim();
						};
						const attrs: any = ui.attributes || {};
						const fromAttrs = pick(attrs.dept_code || attrs.deptCode || attrs.department);
						const fromTop = pick(ui.dept_code || ui.deptCode || ui.department);
						const dept = (fromAttrs || fromTop || "").trim();
						if (dept) {
							(config.headers as any)["X-Active-Dept"] = dept;
							try {
								ctx.actions.setActiveDept(dept);
							} catch {}
						}
					} catch {}
				}
			} catch (_error) {
				console.warn("Failed to inject active context headers", _error);
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

		if (res.config.url?.includes("/keycloak/")) {
			if (res.data && typeof res.data === "object" && "status" in res.data) {
				const { status, data, message } = res.data;
				if (isSuccessStatus(status)) return data;
				throw new Error(message || t("sys.api.apiRequestFailed"));
			}
			return res.data;
		}

		const { status, data, message } = res.data;
		if (isSuccessStatus(status)) return data;
		throw new Error(message || t("sys.api.apiRequestFailed"));
	},
	async (error: AxiosError<Result>) => {
		const { response, config: errorConfig, message } = error || {};
		const requestUrl = response?.config?.url ?? errorConfig?.url ?? "";
		const skipErrorToast = Boolean(
			(response?.config as any)?._skipErrorToast || (errorConfig as any)?._skipErrorToast,
		);
		const isLoginRequest =
			typeof requestUrl === "string" &&
			(requestUrl.includes("/keycloak/auth/login") || requestUrl.includes("/keycloak/auth/platform/login"));
		const shouldSuppressAuthHandling =
			typeof requestUrl === "string" &&
			(requestUrl.includes("/keycloak/localization/") || requestUrl.includes("/workbench/"));

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
		const sessionErrorByMessage =
			typeof combinedMsg === "string" &&
			/已在其他位置登录|会话已超时|重新登录|session.?conflict|session.?expired/i.test(combinedMsg);
		const shouldForceLogout = sessionExpiredHeader || sessionConflictHeader || sessionErrorByMessage;

		if (response?.status === 401 && !shouldSuppressAuthHandling && !isLoginRequest) {
			try {
				const loginTs = Number(
					readStorageValue(PLATFORM_SESSION_KEYS.loginTs, PLATFORM_LEGACY_SESSION_KEYS.loginTs, localStorage) || "0",
				);
				if (loginTs > 0 && Date.now() - loginTs < 5000) {
					console.warn("[auth] Suppressing auto-logout due to grace window after login");
					return Promise.reject(error);
				}
			} catch {}
			// Before forcing logout, re-probe the session — another tab (or this tab's
			// login flow) may have already established a valid session. This prevents the
			// race where a stale 401 from the OLD session triggers logout right after a
			// successful login that created a NEW session.
			try {
				const probe = await fetchCurrentSession();
				if (probe.authenticated) {
					console.warn("[auth] 401 received but session probe shows authenticated — suppressing logout");
					return Promise.reject(error);
				}
			} catch {}
			userStore.getState().actions.clearUserInfoAndToken(sessionConflictHeader ? "taken_over" : "expired");
			if (typeof window !== "undefined" && !isLoginRouteActive()) {
				redirectToLoginWithReturn();
			}
		} else if (shouldForceLogout) {
			userStore.getState().actions.clearUserInfoAndToken(sessionConflictHeader ? "taken_over" : "expired");
			if (typeof window !== "undefined" && !isLoginRouteActive()) {
				redirectToLoginWithReturn();
			}
		} else {
			const redirectInProgress = useRedirectIntentStore.getState().intent !== null;
			if (!shouldSuppressAuthHandling && !isLoginRequest && !redirectInProgress) {
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
