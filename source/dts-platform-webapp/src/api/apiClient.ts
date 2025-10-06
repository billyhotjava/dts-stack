import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from "axios";
import { toast } from "sonner";
import type { Result } from "#/api";
import { ResultStatus } from "#/enum";
import { GLOBAL_CONFIG } from "@/global-config";
import { t } from "@/locales/i18n";
import { isLoginRouteActive, resolveLoginHref } from "@/routes/constants";
import userStore from "@/store/userStore";

const axiosInstance = axios.create({
	baseURL: GLOBAL_CONFIG.apiBaseUrl,
	timeout: 50000,
	headers: { "Content-Type": "application/json;charset=utf-8" },
});

axiosInstance.interceptors.request.use(
	(config) => {
		const { userToken } = userStore.getState();
		const url = config.url || "";
		const isAuthPath = url.includes("/keycloak/auth/");
		if (userToken.accessToken && !isAuthPath) {
			const raw = String(userToken.accessToken).trim();
			const token = raw.startsWith("Bearer ") ? raw.slice(7).trim() : raw;
			if (token) {
				config.headers.Authorization = `Bearer ${token}`;
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
				if (status === ResultStatus.SUCCESS) {
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
		if (status === ResultStatus.SUCCESS) {
			return data;
		}
		throw new Error(message || t("sys.api.apiRequestFailed"));
	},
    (error: AxiosError<Result>) => {
		const { response, message } = error || {};
		const requestUrl = response?.config?.url ?? "";
		const isLoginRequest = typeof requestUrl === "string" && requestUrl.includes("/keycloak/auth/login");
		const shouldSuppressAuthHandling =
			typeof requestUrl === "string" && requestUrl.includes("/keycloak/localization/");
		if (!(isLoginRequest && response?.status === 401)) {
			console.error("API Response Error:", response?.status, response?.data, error.message);
		}
		const errMsg = response?.data?.message || message || t("sys.api.errorMessage");
		if (!shouldSuppressAuthHandling && !isLoginRequest) {
			toast.error(errMsg, { position: "top-center" });
		}
		if (response?.status === 401 && !shouldSuppressAuthHandling && !isLoginRequest) {
			// In development, relax auto-logout to ease debugging
			if (import.meta.env?.DEV) {
				console.warn("[DEV] 401 received; skipping auto logout & redirect");
			} else {
				userStore.getState().actions.clearUserInfoAndToken();
				try {
					localStorage.setItem("dts.session.logoutTs", String(Date.now()));
				} catch {}
				if (typeof window !== "undefined" && !isLoginRouteActive()) {
					location.replace(resolveLoginHref());
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
