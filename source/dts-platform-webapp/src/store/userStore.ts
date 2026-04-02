import { useMutation } from "@tanstack/react-query";
import { isAxiosError } from "axios";
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
import { PLATFORM_LEGACY_SESSION_KEYS, PLATFORM_LEGACY_USER_STORE_KEYS, PLATFORM_SESSION_KEYS } from "@/auth/session-keys";
import { GLOBAL_CONFIG } from "@/global-config";
import { updateLocalTranslations } from "@/utils/translation";
import { useMenuStore } from "./menuStore";
import useContextStore from "./contextStore";

// Normalize possibly mixed arrays (objects or strings) to string[] by picking
// common identity fields such as `code` or `name` when present.
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
				continue;
			}
		}
		// Fallback stringification (rare)
		const s = String(item ?? "");
		if (s) out.push(s);
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
		&& left.department === right.department
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
	const nextAttributes = {
		...(currentUserInfo.attributes ?? {}),
		...(session.deptCode ? { deptCode: [session.deptCode], dept_code: [session.deptCode] } : {}),
		...(session.personnelLevel ? { personnel_level: [session.personnelLevel] } : {}),
	};

	return {
		...currentUserInfo,
		username,
		email: currentUserInfo.email || "",
		firstName: currentUserInfo.firstName || displayName,
		lastName: currentUserInfo.lastName || "",
		fullName: displayName,
		enabled: currentUserInfo.enabled ?? true,
		department: session.deptCode || currentUserInfo.department,
		roles: session.roles ?? normalizeToStringArray(currentUserInfo.roles),
		permissions: session.permissions ?? normalizeToStringArray(currentUserInfo.permissions),
		avatar: resolveAvatar(currentUserInfo.avatar),
		attributes: nextAttributes,
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
					try {
						useMenuStore.getState().clearMenus();
						// Reset scoped context so the next user doesn't inherit prior dept/scope
						const ctx = useContextStore.getState();
						ctx.actions.setActiveDept(undefined);
						removeStorageKeys(
							[
								PLATFORM_SESSION_KEYS.loginTs,
								PLATFORM_SESSION_KEYS.lastActivity,
								PLATFORM_SESSION_KEYS.sessionId,
								PLATFORM_SESSION_KEYS.sessionUser,
								PLATFORM_SESSION_KEYS.logoutTs,
								...PLATFORM_LEGACY_SESSION_KEYS.loginTs,
								...PLATFORM_LEGACY_SESSION_KEYS.lastActivity,
								...PLATFORM_LEGACY_SESSION_KEYS.sessionId,
								...PLATFORM_LEGACY_SESSION_KEYS.sessionUser,
								...PLATFORM_LEGACY_SESSION_KEYS.logoutTs,
							],
							localStorage,
						);
					} catch {
						// ignore store access errors (e.g., during SSR)
					}
				},
			},
		}),
		{
			name: PLATFORM_SESSION_KEYS.userStore,
			storage: createJSONStorage(() => ({
				getItem: (name) => readStorageValue(name, PLATFORM_LEGACY_USER_STORE_KEYS, localStorage),
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

export const useSignIn = () => {
	const { setAuthenticatedSession } = useUserActions();

	const signInMutation = useMutation({
		mutationFn: userService.signin,
	});

	const signIn = async (data: SignInReq): Promise<SignInResult> => {
		try {
			const res = await signInMutation.mutateAsync(data);
			const rawUser = (res as any)?.user ?? (res as any)?.userInfo ?? {};

			const rawNotice = typeof (res as any)?.sessionNotice === "string" ? ((res as any).sessionNotice as string).trim() : "";
			const takeoverFlag = Boolean((res as any)?.sessionTakeover);
			const takeoverMessage = rawNotice || (takeoverFlag ? "已切换到当前登录，其他会话已下线" : "");

			// 适配后端数据格式：处理角色和权限信息
			const adaptedUser = {
				...rawUser,
				// 处理角色/权限信息 - 统一为字符串数组
				roles: normalizeToStringArray(rawUser.roles),
				permissions: normalizeToStringArray(rawUser.permissions),
				department:
					typeof rawUser.department === "string" && rawUser.department.trim()
						? rawUser.department.trim()
						: undefined,
				// 为用户设置默认头像（使用 public 目录下的静态资源路径，兼容生产环境）
				avatar: resolveAvatar(rawUser.avatar),
				// 确保必要的字段存在
				username: rawUser.username || data.username || "",
				firstName: rawUser.firstName || "",
				lastName: rawUser.lastName || "",
				email: rawUser.email || "",
				enabled: rawUser.enabled !== undefined ? rawUser.enabled : true,
			};

			// Normalize roles and expand synonyms
			const expandSynonyms = (roles: string[]): Set<string> => {
				const set = new Set<string>((roles || []).map((r) => String(r || "").toUpperCase()));
				if (set.has("SYSADMIN")) set.add("ROLE_SYS_ADMIN");
				if (set.has("AUTHADMIN")) set.add("ROLE_AUTH_ADMIN");
				if (set.has("AUDITADMIN")) set.add("ROLE_SECURITY_AUDITOR");
				if (set.has("SECURITYAUDITOR")) set.add("ROLE_SECURITY_AUDITOR");
				if (set.has("OPADMIN")) set.add("ROLE_OP_ADMIN");
				return set;
			};

			const FE_GUARD_ENABLED = String(import.meta.env.VITE_ENABLE_FE_GUARD ?? "true").toLowerCase() === "true";
			if (FE_GUARD_ENABLED) {
				const allowed = Array.isArray(GLOBAL_CONFIG.allowedLoginRoles) ? GLOBAL_CONFIG.allowedLoginRoles : [];
				const allowedSet = expandSynonyms(allowed);
				const userRoles: string[] = Array.isArray(adaptedUser.roles) ? (adaptedUser.roles as string[]) : [];
				const userSet = expandSynonyms(userRoles);
				if (allowedSet.size > 0) {
					const hasAllowed = Array.from(userSet).some((r) => allowedSet.has(r));
					if (!hasAllowed) {
						throw new Error("您无权登录该系统");
					}
				}
				// Defense-in-depth: explicitly forbid admin-console roles on platform
				if (userSet.has("ROLE_SYS_ADMIN") || userSet.has("ROLE_AUTH_ADMIN") || userSet.has("ROLE_SECURITY_AUDITOR")) {
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

			if (takeoverMessage) {
				toast.info(takeoverMessage, {
					position: "top-center",
					closeButton: true,
				});
			}

			// Mark login timestamp for downstream grace handling on initial 401s
			try {
				localStorage.setItem(PLATFORM_SESSION_KEYS.loginTs, String(Date.now()));
			} catch {}

			// 登录成功后获取并更新Keycloak翻译词条
			try {
				const translations: KeycloakTranslations = await KeycloakLocalizationService.getChineseTranslations();
				updateLocalTranslations(translations);
			} catch (translationError) {
				console.warn("Failed to load Keycloak translations:", translationError);
				// 不阻塞登录流程，即使翻译加载失败也继续
			}
			return {
				mode: "backend" as const,
				user: adaptedUser,
				notice: takeoverMessage || undefined,
				takeover: takeoverFlag,
			};
		} catch (err) {
			const fallback = handleDevFallback({ error: err, payload: data, setAuthenticatedSession });
			if (fallback) {
				return fallback;
			}
			toast.error(err.message, {
				position: "top-center",
			});
			throw err;
		}
	};

	return signIn;
};

type SignInResult = {
	mode: "backend" | "fallback";
	user: UserInfo;
	notice?: string;
	takeover?: boolean;
};

type DevFallbackContext = {
	error: unknown;
	payload: SignInReq;
	setAuthenticatedSession: (session: CurrentSessionPayload, userInfo?: Partial<UserInfo>) => void;
};

const handleDevFallback = ({ error, payload, setAuthenticatedSession }: DevFallbackContext): SignInResult | null => {
    const enabled = String(import.meta.env.VITE_DEV_LOGIN_FALLBACK || "false").toLowerCase() === "true";
    if (!enabled) {
        return null;
    }
    if (!(import.meta.env.DEV && isAxiosError(error) && error.response?.status === 401)) {
        return null;
    }
	const username = (payload.username || "").trim();
	if (!username) {
		return null;
	}
	const normalized = username.toLowerCase();
	if (["sysadmin", "authadmin", "auditadmin"].includes(normalized)) {
		return null;
	}

	const roles = buildRoles(normalized);
	const permissions = buildPermissions(normalized);
	const user: UserInfo = {
		id: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`,
		email: `${normalized}@example.com`,
		username,
		firstName: username,
		lastName: "",
		enabled: true,
		roles,
		permissions,
		// 使用 public 目录下的静态资源路径，生产环境可直接访问
		avatar: DEFAULT_AVATAR,
	};

	// Enforce allowed roles even in dev fallback: only proceed if user has at least one allowed role
	const expandSynonyms = (list: string[]): Set<string> => {
		const set = new Set<string>((list || []).map((r) => String(r || "").toUpperCase()));
		if (set.has("OPADMIN")) set.add("ROLE_OP_ADMIN");
		return set;
	};
	const allowed = Array.isArray(GLOBAL_CONFIG.allowedLoginRoles) ? GLOBAL_CONFIG.allowedLoginRoles : [];
	const allowedSet = expandSynonyms(allowed);
	const userSet = expandSynonyms(roles);
	if (allowedSet.size > 0 && !Array.from(userSet).some((r) => allowedSet.has(r))) {
		return null;
	}

	setAuthenticatedSession(
		{
			authenticated: true,
			username: user.username,
			displayName: user.username,
			roles,
			permissions,
		},
		user,
	);
	return { mode: "fallback", user, notice: undefined, takeover: false };
};

const buildRoles = (normalizedUsername: string): string[] => {
	const baseRoles = new Set<string>(["ROLE_USER"]);
	if (normalizedUsername === "opadmin") {
		baseRoles.add("ROLE_OP_ADMIN");
	}
	return Array.from(baseRoles);
};

const buildPermissions = (normalizedUsername: string): string[] => {
	const perms = new Set<string>(["portal.view"]);
	if (normalizedUsername === "opadmin") {
		perms.add("portal.manage");
		perms.add("catalog.manage");
		perms.add("governance.manage");
		perms.add("iam.manage");
	}
	if (normalizedUsername.endsWith("catalog")) {
		perms.add("catalog.manage");
	}
	if (normalizedUsername.endsWith("governance")) {
		perms.add("governance.manage");
	}
	if (normalizedUsername.endsWith("iam")) {
		perms.add("iam.manage");
	}
	return Array.from(perms);
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
			// 即使登出接口失败，也要清理本地信息
		} finally {
			// 清理本地存储的用户信息和token
			clearUserInfoAndToken();
		}
	};

	return signOut;
};

export default useUserStore;
