import { buildLoginRedirectHref, currentRoutePath as resolveCurrentRoutePath } from "@dts-session-core/route";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";

export type RefreshResult = {
	accessToken: string;
	refreshToken: string;
} | null;

let refreshPromise: Promise<RefreshResult> | null = null;
let redirecting = false;

function resolveAdminLoginHref(): string {
	const loginRoute = "/auth/login";
	const publicPath = String(GLOBAL_CONFIG.publicPath || "/").trim() || "/";
	if (/^[a-zA-Z][a-zA-Z\d+.-]*:\/\//.test(publicPath) || publicPath.startsWith("//")) {
		return `${publicPath.replace(/\/+$/, "")}${loginRoute}`;
	}
	if (publicPath === "/") {
		return loginRoute;
	}
	return `${publicPath.replace(/\/+$/, "")}${loginRoute}`;
}

export async function refreshAccessToken(): Promise<RefreshResult> {
	const refreshToken = String(userStore.getState().userToken?.refreshToken || "").trim();
	if (!refreshToken) return null;
	if (refreshPromise) return refreshPromise;

	refreshPromise = (async (): Promise<RefreshResult> => {
		try {
			const response = await fetch(`${GLOBAL_CONFIG.apiBaseUrl}/keycloak/auth/refresh`, {
				method: "POST",
				credentials: "include",
				headers: { "content-type": "application/json", accept: "application/json" },
				body: JSON.stringify({ refreshToken }),
			});
			if (!response.ok) return null;

			const body = await response.json().catch(() => null);
			const data = body?.data ?? body?.result ?? body?.payload ?? body;
			const nextAccess = String(data?.accessToken || data?.access_token || data?.token || "").trim();
			if (!nextAccess) return null;
			const nextRefresh = String(data?.refreshToken || data?.refresh_token || "").trim() || refreshToken;
			userStore.getState().actions.setUserToken({ accessToken: nextAccess, refreshToken: nextRefresh });
			return { accessToken: nextAccess, refreshToken: nextRefresh };
		} catch {
			return null;
		}
	})().finally(() => {
		setTimeout(() => {
			refreshPromise = null;
		}, 50);
	});

	return refreshPromise;
}

export function currentRoutePath(): string {
	return resolveCurrentRoutePath("browser");
}

export function redirectToLoginWithReturn(): void {
	if (redirecting) return;
	redirecting = true;
	window.location.replace(buildLoginRedirectHref(resolveAdminLoginHref(), currentRoutePath()));
}

export function resetLoginRedirectFlag(): void {
	redirecting = false;
}
