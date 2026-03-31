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
		// Clear synchronously: all callers that already `await refreshPromise` hold their
		// own reference and will receive the resolved value regardless of this assignment.
		// Clearing immediately ensures NEW callers start a fresh refresh instead of joining
		// a stale resolved promise.
		refreshPromise = null;
	});

	return refreshPromise;
}

export function currentRoutePath(): string {
	return resolveCurrentRoutePath("browser");
}

export function redirectToLoginWithReturn(): void {
	if (redirecting) return;
	redirecting = true;
	const loginPath = "/auth/login";
	const current = currentRoutePath();
	// If already on the login page, don't wrap the URL again — extract the existing
	// redirect target (if any) and reuse it to prevent ?redirect= parameter nesting
	// that causes 414 Request-URI Too Large.
	const pathOnly = current.split("?")[0];
	if (pathOnly === loginPath || pathOnly.endsWith(loginPath)) {
		// Already on login — keep the URL as-is (or preserve existing redirect param)
		const params = new URLSearchParams(current.split("?")[1] || "");
		const existingRedirect = params.get("redirect");
		if (existingRedirect) {
			window.location.replace(buildLoginRedirectHref(resolveAdminLoginHref(), existingRedirect));
		}
		// else: already on login with no redirect param — do nothing
		return;
	}
	window.location.replace(buildLoginRedirectHref(resolveAdminLoginHref(), current));
}

export function resetLoginRedirectFlag(): void {
	redirecting = false;
}
