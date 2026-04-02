import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { readStorageValue, removeStorageKeys } from "@dts-session-core/storage";
import type { UserInfo } from "#/entity";
import type { KeycloakTranslations } from "#/keycloak";
import { KeycloakLocalizationService } from "@/api/services/keycloakLocalizationService";
import type { CurrentSessionPayload, PortalSessionState, SessionReason } from "@/auth/session-state";
import {
	createAnonymousSessionState,
	createAuthenticatedSessionState,
	createBootstrappingSessionState,
} from "@/auth/session-state";
import userService, { type SignInReq } from "@/api/services/userService";
import { ADMIN_LEGACY_SESSION_KEYS, ADMIN_LEGACY_USER_STORE_KEYS, ADMIN_SESSION_KEYS } from "@/auth/session-keys";
import { GLOBAL_CONFIG } from "@/global-config";
import { updateLocalTranslations } from "@/utils/translation";

const normalizeToStringArray = (value: unknown): string[] => {
	if (!Array.isArray(value)) return [];
	const out: string[] = [];
	for (const item of value) {
		if (typeof item === "string") {
			if (item) out.push(item);
			continue;
		}
		if (item && typeof item === "object") {
			const obj = item as Record<string, unknown>;
			const candidate = obj.code ?? obj.name ?? obj.value ?? "";
			if (typeof candidate === "string" && candidate) {
				out.push(candidate);
			}
		}
	}
	return out;
};

const DEFAULT_AVATAR = "/assets/icons/ic-user.svg";
const resolveAvatar = (raw: unknown): string => {
	const s = typeof raw === "string" ? raw.trim() : "";
	if (!s) return DEFAULT_AVATAR;
	if (s.startsWith("/src/assets/")) return s.replace("/src/assets/", "/assets/");
	return s;
};

type UserStore = {
	userInfo: Partial<UserInfo>;
	session: PortalSessionState;
	actions: {
		setUserInfo: (userInfo: UserInfo) => void;
		setSession: (session: PortalSessionState) => void;
		markSessionChecking: () => void;
		setAuthenticatedSession: (session: CurrentSessionPayload, userInfo?: Partial<UserInfo>) => void;
		clearUserInfoAndToken: (reason?: SessionReason) => void;
	};
};

function sameStringArray(left: string[] | undefined, right: string[] | undefined): boolean {
	if (left === right) return true;
	if (!left || !right) return (!left || left.length === 0) && (!right || right.length === 0);
	if (left.length !== right.length) return false;
	return left.every((item, index) => item === right[index]);
}

function sameUserInfo(left: Partial<UserInfo>, right: Partial<UserInfo>): boolean {
	return left.username === right.username
		&& left.email === right.email
		&& left.firstName === right.firstName
		&& left.lastName === right.lastName
		&& left.fullName === right.fullName
		&& left.enabled === right.enabled
		&& left.avatar === right.avatar
		&& JSON.stringify(left.attributes ?? {}) === JSON.stringify(right.attributes ?? {})
		&& sameStringArray(normalizeToStringArray(left.roles), normalizeToStringArray(right.roles))
		&& sameStringArray(normalizeToStringArray(left.permissions), normalizeToStringArray(right.permissions));
}

function sameSessionState(left: PortalSessionState, right: PortalSessionState): boolean {
	return left.initialized === right.initialized
		&& left.checking === right.checking
		&& left.authenticated === right.authenticated
		&& left.reason === right.reason
		&& left.browserId === right.browserId
		&& left.expiresAt === right.expiresAt;
}

function toPersistedSession(session: PortalSessionState): PortalSessionState {
	return {
		initialized: session.initialized,
		checking: session.checking,
		authenticated: session.authenticated,
		reason: session.reason,
		browserId: session.browserId,
		expiresAt: session.expiresAt,
	};
}

function buildSessionUserInfo(
	session: CurrentSessionPayload,
	currentUserInfo: Partial<UserInfo> = {},
): Partial<UserInfo> {
	const username = session.username?.trim() || currentUserInfo.username || "";
	const displayName = session.displayName?.trim() || currentUserInfo.fullName || currentUserInfo.firstName || username;
	return {
		...currentUserInfo,
		username,
		email: currentUserInfo.email || "",
		firstName: currentUserInfo.firstName || displayName,
		lastName: currentUserInfo.lastName || "",
		fullName: currentUserInfo.fullName || displayName,
		enabled: currentUserInfo.enabled ?? true,
		roles: session.roles ?? normalizeToStringArray(currentUserInfo.roles),
		permissions: session.permissions ?? normalizeToStringArray(currentUserInfo.permissions),
		avatar: resolveAvatar(currentUserInfo.avatar),
		attributes: currentUserInfo.attributes ?? {},
	};
}

const useUserStore = create<UserStore>()(
	persist(
		(set) => ({
			userInfo: {},
			session: createBootstrappingSessionState(),
			actions: {
				setUserInfo: (userInfo) => {
					set({ userInfo });
				},
				setSession: (session) => {
					set({ session });
				},
				markSessionChecking: () => {
					set((state) => ({
						session: {
							...state.session,
							checking: true,
							reason: state.session.initialized ? state.session.reason : "bootstrapping",
						},
					}));
				},
				setAuthenticatedSession(sessionPayload, userInfo = {}) {
					set((state) => {
						const nextUserInfo = buildSessionUserInfo(sessionPayload, {
							...state.userInfo,
							...userInfo,
						});
						const nextSession = createAuthenticatedSessionState({
							browserId: sessionPayload.browserId,
							expiresAt: sessionPayload.expiresAt,
						});
						if (sameUserInfo(state.userInfo, nextUserInfo) && sameSessionState(state.session, nextSession)) {
							return state;
						}
						return {
							userInfo: nextUserInfo,
							session: nextSession,
						};
					});
				},
				clearUserInfoAndToken(reason = "logged_out") {
					set({
						userInfo: {},
						session: createAnonymousSessionState(reason),
					});
					removeStorageKeys(
						[
							ADMIN_SESSION_KEYS.loginTs,
							ADMIN_SESSION_KEYS.lastActivity,
							ADMIN_SESSION_KEYS.sessionId,
							ADMIN_SESSION_KEYS.sessionUser,
							ADMIN_SESSION_KEYS.logoutTs,
							...ADMIN_LEGACY_SESSION_KEYS.loginTs,
							...ADMIN_LEGACY_SESSION_KEYS.lastActivity,
							...ADMIN_LEGACY_SESSION_KEYS.sessionId,
							...ADMIN_LEGACY_SESSION_KEYS.sessionUser,
							...ADMIN_LEGACY_SESSION_KEYS.logoutTs,
						],
						localStorage,
					);
				},
			},
		}),
		{
			name: ADMIN_SESSION_KEYS.userStore,
			storage: createJSONStorage(() => ({
				getItem: (name) => readStorageValue(name, ADMIN_LEGACY_USER_STORE_KEYS, localStorage),
				setItem: (name, value) => localStorage.setItem(name, value),
				removeItem: (name) => localStorage.removeItem(name),
			})),
			partialize: (state) => ({
				userInfo: state.userInfo,
				session: toPersistedSession(state.session),
			}),
		},
	),
);

export const useUserInfo = () => useUserStore((state) => state.userInfo);
export const usePortalSession = () => useUserStore((state) => state.session);
export const useUserPermissions = () => useUserStore((state) => state.userInfo.permissions || []);
export const useUserRoles = () => useUserStore((state) => state.userInfo.roles || []);
export const useUserActions = () => useUserStore((state) => state.actions);

const expandAdminRoles = (roles: string[]): Set<string> => {
	const set = new Set<string>((roles || []).map((role) => String(role || "").trim().toUpperCase()));
	if (set.has("SYSADMIN") || set.has("SYS_ADMIN") || set.has("ROLE_SYSADMIN") || set.has("ROLE_SYSTEM_ADMIN")) {
		set.add("ROLE_SYS_ADMIN");
	}
	if (set.has("AUTHADMIN") || set.has("AUTH_ADMIN") || set.has("IAM_ADMIN") || set.has("ROLE_AUTHADMIN")) {
		set.add("ROLE_AUTH_ADMIN");
	}
	if (
		set.has("AUDITADMIN") ||
		set.has("AUDIT_ADMIN") ||
		set.has("SECURITYAUDITOR") ||
		set.has("SECURITY_AUDITOR") ||
		set.has("ROLE_SECURITYAUDITOR") ||
		set.has("ROLE_AUDITOR_ADMIN") ||
		set.has("ROLE_AUDIT_ADMIN")
	) {
		set.add("ROLE_SECURITY_AUDITOR");
	}
	if (set.has("OPADMIN") || set.has("OP_ADMIN")) {
		set.add("ROLE_OP_ADMIN");
	}
	return set;
};

export const useSignIn = () => {
	const { setAuthenticatedSession } = useUserActions();
	const queryClient = useQueryClient();
	const signInMutation = useMutation({
		mutationFn: userService.signin,
	});

	const signIn = async (data: SignInReq): Promise<UserInfo> => {
		try {
			const res = await signInMutation.mutateAsync(data);
			const rawUser = (res as any)?.user ?? (res as any)?.userInfo ?? {};
			const rawNotice = typeof (res as any)?.sessionNotice === "string" ? ((res as any).sessionNotice as string).trim() : "";
			const takeoverFlag = Boolean((res as any)?.sessionTakeover);
			const takeoverMessage = rawNotice || (takeoverFlag ? "已切换到当前登录，其他会话已下线" : "");

			const adaptedUser: UserInfo = {
				...rawUser,
				id: typeof rawUser.id === "string" ? rawUser.id : data.username,
				username: rawUser.username || data.username || "",
				firstName: rawUser.firstName || rawUser.fullName || rawUser.username || data.username || "",
				fullName: rawUser.fullName || rawUser.firstName || rawUser.username || data.username || "",
				lastName: rawUser.lastName || "",
				email: rawUser.email || "",
				enabled: rawUser.enabled !== undefined ? rawUser.enabled : true,
				avatar: resolveAvatar(rawUser.avatar),
				attributes: (rawUser.attributes as Record<string, string[]>) || {},
				roles: normalizeToStringArray(rawUser.roles),
				permissions: normalizeToStringArray(rawUser.permissions),
			};

			const roleSet = expandAdminRoles(Array.isArray(adaptedUser.roles) ? (adaptedUser.roles as string[]) : []);
			adaptedUser.roles = Array.from(
				new Set([
					...(Array.isArray(adaptedUser.roles) ? (adaptedUser.roles as string[]) : []),
					...["ROLE_SYS_ADMIN", "ROLE_AUTH_ADMIN", "ROLE_SECURITY_AUDITOR", "ROLE_OP_ADMIN"].filter((role) =>
						roleSet.has(role),
					),
				]),
			);

			const FE_GUARD_ENABLED = String(import.meta.env.VITE_ENABLE_FE_GUARD ?? "true").toLowerCase() === "true";
			if (FE_GUARD_ENABLED) {
				const allowed = Array.isArray(GLOBAL_CONFIG.allowedLoginRoles) ? GLOBAL_CONFIG.allowedLoginRoles : [];
				const allowedSet = expandAdminRoles(allowed);
				if (allowedSet.size > 0 && !Array.from(roleSet).some((role) => allowedSet.has(role))) {
					throw new Error("您无权登录该系统");
				}
				if (roleSet.has("ROLE_OP_ADMIN")) {
					throw new Error("您无权登录该系统");
				}
			}

			setAuthenticatedSession(
				{
					authenticated: true,
					username: adaptedUser.username,
					displayName: adaptedUser.fullName || adaptedUser.firstName || adaptedUser.username,
					browserId:
						typeof (res as any)?.browserId === "string" && (res as any).browserId.trim()
							? (res as any).browserId.trim()
							: undefined,
					roles: normalizeToStringArray(adaptedUser.roles),
					permissions: normalizeToStringArray(adaptedUser.permissions),
				},
				adaptedUser,
			);

			queryClient.removeQueries({ queryKey: ["admin", "whoami"], exact: true });
			try {
				localStorage.setItem(ADMIN_SESSION_KEYS.loginTs, String(Date.now()));
			} catch {
				// ignore storage errors
			}

			try {
				const translations: KeycloakTranslations = await KeycloakLocalizationService.getChineseTranslations();
				updateLocalTranslations(translations);
			} catch (translationError) {
				console.warn("Failed to load Keycloak translations:", translationError);
			}

			if (takeoverMessage) {
				toast.info(takeoverMessage, {
					position: "top-center",
					closeButton: true,
				});
			}

			return adaptedUser;
		} catch (err) {
			const message = err instanceof Error ? err.message : "登录失败";
			toast.error(message, {
				position: "top-center",
			});
			throw err;
		}
	};

	return signIn;
};

const resolveUsernameForLogout = (info: Partial<UserInfo> | undefined): string | undefined => {
	if (!info) return undefined;
	const bag = info as Record<string, unknown>;
	const candidates: unknown[] = [
		info.username,
		bag["preferredUsername"],
		bag["preferred_username"],
		bag["user"],
		bag["principal"],
	];
	for (const candidate of candidates) {
		if (typeof candidate === "string") {
			const trimmed = candidate.trim();
			if (trimmed) return trimmed;
		}
	}
	return undefined;
};

export const useSignOut = () => {
	const { clearUserInfoAndToken } = useUserActions();

	const signOut = async () => {
		const { userInfo } = useUserStore.getState();
		try {
			await userService.logout(undefined, resolveUsernameForLogout(userInfo));
		} catch (error) {
			console.error("Logout error:", error);
		} finally {
			clearUserInfoAndToken();
		}
	};

	return signOut;
};

export default useUserStore;
