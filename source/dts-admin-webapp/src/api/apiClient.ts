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
		console.error("API Response Error:", error.response?.status, error.response?.data, error.message);

		const { response, message } = error || {};
		const requestUrl = response?.config?.url ?? "";
		const isLoginRequest = typeof requestUrl === "string" && requestUrl.includes("/keycloak/auth/login");
		const isRefreshRequest = typeof requestUrl === "string" && requestUrl.includes("/keycloak/auth/refresh");
		const shouldSuppressAuthHandling =
			typeof requestUrl === "string" && requestUrl.includes("/keycloak/localization/");
		const isKeycloakAdminEndpoint =
			typeof requestUrl === "string" && requestUrl.includes("/keycloak/") &&
			!requestUrl.includes("/keycloak/auth/") && !requestUrl.includes("/keycloak/localization/");

		const errMsg = response?.data?.message || message || t("sys.api.errorMessage");
		if (!shouldSuppressAuthHandling) {
			toast.error(errMsg, { position: "top-center" });
		}

		if (response?.status === 401) {
			// In development, relax auto-logout to ease debugging
			if (import.meta.env?.DEV) {
				console.warn("[DEV] 401 received; skipping auto logout");
			} else {
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
