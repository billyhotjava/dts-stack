import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("platform session management source contract", () => {
	it("keeps browser idle timeout out of login guard and login page", () => {
		const guardSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../routes/components/login-auth-guard.tsx"),
			"utf8",
		);
		const loginPageSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../pages/sys/login/index.tsx"),
			"utf8",
		);

		expect(guardSource.includes("function isSessionIdle")).toBe(false);
		expect(guardSource.includes("isSessionIdle()")).toBe(false);
		expect(loginPageSource.includes("function isSessionIdle")).toBe(false);
		expect(loginPageSource.includes("isSessionIdle()")).toBe(false);
	});

	it("lets the backend remain the single source of truth for portal session expiry", () => {
		const sessionManagerSource = fs.readFileSync(path.resolve(import.meta.dirname, "./session-manager.tsx"), "utf8");
		const guardSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../routes/components/login-auth-guard.tsx"),
			"utf8",
		);
		const loginPageSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../pages/sys/login/index.tsx"),
			"utf8",
		);

		expect(sessionManagerSource.includes("logoutDueToIdle")).toBe(false);
		expect(sessionManagerSource.includes("window.setTimeout(logoutDueToIdle")).toBe(false);
		expect(sessionManagerSource.includes("backend remains")).toBe(true);
		expect(sessionManagerSource.includes("PORTAL_SESSION_STORAGE_KEYS")).toBe(true);
		expect(sessionManagerSource.includes("wasPortalLogoutBroadcastRecently")).toBe(true);
		expect(sessionManagerSource.includes("isStillCurrentAccessToken")).toBe(true);
		expect(sessionManagerSource.includes("expectedAccessToken")).toBe(true);
		expect(guardSource.includes("requiresBackendSessionValidation(accessToken, token?.authenticated)")).toBe(true);
		expect(guardSource.includes("forceLogout()")).toBe(true);
		expect(sessionManagerSource.includes("FOLLOWER_RECHECK_MS")).toBe(true);
		expect(sessionManagerSource.includes("refreshPortalSessionIfPossible")).toBe(true);
		expect(guardSource.includes("tokenExpiresAt - 10_000")).toBe(false);
		expect(guardSource.includes("backend session probe unavailable, preserving local session")).toBe(true);
		expect(guardSource.includes("forceLogout();\n\t\t\t\treturn;\n\t\t\t}\n\t\t\tsetSessionAuthenticated(true);")).toBe(
			false,
		);
		expect(loginPageSource.includes("tokenExpiresAt - 10_000")).toBe(false);
		expect(guardSource.includes("getPortalSessionStatus()")).toBe(true);
	});

	it("does not treat cookie-only BFF sessions as expired just because accessToken is absent", () => {
		const guardSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../routes/components/login-auth-guard.tsx"),
			"utf8",
		);
		const loginPageSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../pages/sys/login/index.tsx"),
			"utf8",
		);

		for (const source of [guardSource, loginPageSource]) {
			expect(source.includes("function isTokenExpired(token?: string): boolean {\n\tif (!token) return false;")).toBe(
				true,
			);
			expect(source.includes("if (!token) return true;")).toBe(false);
		}
		expect(guardSource.includes("const hasSessionMarker = Boolean(token?.authenticated || accessToken);")).toBe(true);
		expect(guardSource.includes("requiresBackendSessionValidation(accessToken, token?.authenticated)")).toBe(true);
	});

	it("keeps deep-linked platform pages usable while menu bootstrap is recovering", () => {
		const guardSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../routes/components/login-auth-guard.tsx"),
			"utf8",
		);
		const dynamicResolverSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../routes/sections/dashboard/dynamic-resolver.tsx"),
			"utf8",
		);

		expect(guardSource.includes("const MENU_RETRY_MS = 3000;")).toBe(true);
		expect(guardSource.includes("retryTimer = window.setTimeout(loadMenuTree, MENU_RETRY_MS);")).toBe(true);
		expect(dynamicResolverSource.includes('"/foundation/connectors": "/pages/foundation/ConnectorRegistryPage"')).toBe(
			true,
		);
		expect(dynamicResolverSource.includes("const directOverridePath =")).toBe(true);
		expect(dynamicResolverSource.includes("if (directOverridePath)")).toBe(true);
		expect(dynamicResolverSource.includes("return <>{Component(directOverridePath)}</>;")).toBe(true);
	});

	it("does not let stale auth responses clear a newer login session", () => {
		const sessionManagerSource = fs.readFileSync(path.resolve(import.meta.dirname, "./session-manager.tsx"), "utf8");
		const apiClientSource = fs.readFileSync(path.resolve(import.meta.dirname, "../../api/apiClient.ts"), "utf8");
		const guardSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../routes/components/login-auth-guard.tsx"),
			"utf8",
		);
		const loginFormSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../pages/sys/login/login-form.tsx"),
			"utf8",
		);
		const loginPageSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../pages/sys/login/index.tsx"),
			"utf8",
		);
		const userStoreSource = fs.readFileSync(path.resolve(import.meta.dirname, "../../store/userStore.ts"), "utf8");
		const localizationServiceSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../api/services/keycloakLocalizationService.ts"),
			"utf8",
		);
		const devAuthSource = fs.readFileSync(path.resolve(import.meta.dirname, "../../utils/devAuthTokens.ts"), "utf8");

		expect(sessionManagerSource.includes("!isStillCurrentAccessToken(expectedAccessToken)")).toBe(true);
		expect(sessionManagerSource.includes("finishSession(clearUserInfoAndToken, logoutInProgressRef, reason)")).toBe(
			true,
		);
		expect(apiClientSource.includes("extractRequestAccessToken")).toBe(false);
		expect(apiClientSource.includes("withCredentials: true")).toBe(true);
		expect(apiClientSource.includes("forceLogoutToLogin()")).toBe(true);
		expect(apiClientSource.includes("config.headers.Authorization")).toBe(false);
		expect(apiClientSource.includes("X-Portal-Access-Token")).toBe(false);
		expect(apiClientSource.includes("shouldForceLogout && !TEST_SESSION_ENABLED && !isLoginRequest")).toBe(true);
		expect(apiClientSource.includes("!IS_PRODUCTION")).toBe(true);
		expect(apiClientSource.includes("IS_LOCAL_DEV_HOST")).toBe(true);
		expect(apiClientSource.includes("logApiResponseError")).toBe(true);
		expect(apiClientSource.includes("response?.data, error.message")).toBe(false);
		expect(apiClientSource.includes("isWithinPortalLoginGrace(15_000)")).toBe(true);
		expect(apiClientSource.includes("LOGIN_REQUEST_SUPPRESS_STALE_MS")).toBe(true);
		expect(apiClientSource.includes("captureRequestSessionSnapshot(config)")).toBe(true);
		expect(apiClientSource.includes("shouldIgnoreStaleSessionFailure(response?.config || error?.config)")).toBe(true);
		expect(apiClientSource.includes("[auth] Ignoring stale session failure from an older request")).toBe(true);
		expect(apiClientSource.includes('requestUrl.includes("/keycloak/localization/")')).toBe(true);
		expect(apiClientSource.includes("skipErrorToast")).toBe(true);
		expect(loginFormSource.includes("clearUserInfoAndToken();\n\t\tsetLoading(true);")).toBe(true);
		expect(loginFormSource.includes("void svc.default.getMenuTree().catch(() => undefined);")).toBe(true);
		expect(loginFormSource.includes("resolveAppHref")).toBe(true);
		expect(loginFormSource.includes("resolvePostLoginRedirect")).toBe(true);
		expect(loginFormSource.includes("redirectAfterLogin")).toBe(true);
		expect(loginFormSource.includes("window.location.replace(resolveAppHref(route))")).toBe(true);
		expect(loginPageSource.includes("resolvePostLoginRedirect")).toBe(true);
		expect(loginFormSource.includes("void KeycloakLocalizationService.getChineseTranslations()")).toBe(true);
		expect(loginPageSource.includes("getPortalSessionStatus()")).toBe(true);
		expect(loginPageSource.includes("token.authenticated")).toBe(true);
		expect(loginPageSource.includes("buildRecoveredUser")).toBe(true);
		expect(loginPageSource.includes("setUserToken({ authenticated: true, tokenExpiresAt })")).toBe(true);
		expect(loginPageSource.includes("setUserInfo(buildRecoveredUser(status))")).toBe(true);
		expect(userStoreSource.includes("authenticated: true")).toBe(true);
		expect(userStoreSource.includes("clearUserInfoAndToken();\n\t\ttry {")).toBe(true);
		expect(userStoreSource.includes("void KeycloakLocalizationService.getChineseTranslations()")).toBe(true);
		expect(userStoreSource.includes("isDevFallbackAllowedHost")).toBe(true);
		expect(userStoreSource.includes("buildDevFallbackToken")).toBe(true);
		expect(devAuthSource.includes("buildDevTokenPrefix")).toBe(true);
		expect(guardSource.includes("isDevFallbackAccessToken")).toBe(true);
		expect(loginPageSource.includes("isDevFallbackAccessToken")).toBe(true);
		for (const source of [userStoreSource, guardSource, loginPageSource, devAuthSource]) {
			expect(source.includes(`${["dev", "access"].join("-")}-`)).toBe(false);
			expect(source.includes(`${["dev", "refresh"].join("-")}-`)).toBe(false);
		}
		expect(localizationServiceSource.includes("_skipAuth: true")).toBe(true);
		expect(localizationServiceSource.includes("_skipErrorToast: true")).toBe(true);
	});

	it("keeps admin tokens out of the platform webapp state surface", () => {
		const userStoreSource = fs.readFileSync(path.resolve(import.meta.dirname, "../../store/userStore.ts"), "utf8");
		const entitySource = fs.readFileSync(path.resolve(import.meta.dirname, "../../types/entity.ts"), "utf8");
		const apiClientSource = fs.readFileSync(path.resolve(import.meta.dirname, "../../api/apiClient.ts"), "utf8");
		const sessionManagerSource = fs.readFileSync(path.resolve(import.meta.dirname, "./session-manager.tsx"), "utf8");

		for (const source of [userStoreSource, entitySource, apiClientSource, sessionManagerSource]) {
			expect(source.includes("adminAccessToken")).toBe(false);
			expect(source.includes("adminRefreshToken")).toBe(false);
		}
	});

	it("keeps analytics 401 handling aligned with the platform session refresh path", () => {
		const analyticsApiSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../analytics/api/analyticsApi.ts"),
			"utf8",
		);

		expect(analyticsApiSource.includes("refreshPortalSessionIfPossible")).toBe(true);
		expect(analyticsApiSource.includes("refreshed?.authenticated")).toBe(true);
		expect(analyticsApiSource.includes("redirect only after the shared platform refresh path also fails")).toBe(true);
		expect(analyticsApiSource.includes('credentials: "include"')).toBe(true);
	});
});
