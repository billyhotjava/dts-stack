import { useMutation } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { toast } from "sonner";
import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { UserInfo, UserToken } from "#/entity";
import { StorageEnum } from "#/enum";
import type { KeycloakTranslations } from "#/keycloak";
import { KeycloakLocalizationService } from "@/api/services/keycloakLocalizationService";
import userService, { type SignInReq } from "@/api/services/userService";
import { GLOBAL_CONFIG } from "@/global-config";
import { buildDevFallbackToken, isDevFallbackAllowedHost } from "@/utils/devAuthTokens";
import { clearPortalSessionLoginMarkers, markPortalSessionLogin } from "@/utils/portalSessionStorage";
import { resolvePortalTokenExpiresAt } from "@/utils/sessionExpiry";
import { updateLocalTranslations } from "@/utils/translation";
import useContextStore from "./contextStore";
import { useMenuStore } from "./menuStore";

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
	userToken: UserToken;

	actions: {
		setUserInfo: (userInfo: UserInfo) => void;
		setUserToken: (token: UserToken) => void;
		clearUserInfoAndToken: () => void;
	};
};

const useUserStore = create<UserStore>()(
	persist(
		(set) => ({
			userInfo: {},
			userToken: {},
			actions: {
				setUserInfo: (userInfo) => {
					set({ userInfo });
				},
				setUserToken: (userToken) => {
					set({ userToken });
				},
				clearUserInfoAndToken() {
					set({ userInfo: {}, userToken: {} });
					try {
						useMenuStore.getState().clearMenus();
						// Reset scoped context so the next user doesn't inherit prior dept/scope
						const ctx = useContextStore.getState();
						ctx.actions.setActiveDept(undefined);
						clearPortalSessionLoginMarkers();
					} catch {
						// ignore store access errors (e.g., during SSR)
					}
				},
			},
		}),
		{
			name: "dts.platform.userStore", // name of the item in the storage (must be unique)
			storage: createJSONStorage(() => localStorage), // (optional) by default, 'localStorage' is used
			partialize: (state) => ({
				[StorageEnum.UserInfo]: state.userInfo,
				[StorageEnum.UserToken]: state.userToken,
			}),
		},
	),
);

export const useUserInfo = () => useUserStore((state) => state.userInfo);
export const useUserToken = () => useUserStore((state) => state.userToken);
export const useUserPermissions = () => useUserStore((state) => state.userInfo.permissions || []);
export const useUserRoles = () => useUserStore((state) => state.userInfo.roles || []);
export const useUserActions = () => useUserStore((state) => state.actions);

export const useSignIn = () => {
	const { setUserToken, setUserInfo, clearUserInfoAndToken } = useUserActions();

	const signInMutation = useMutation({
		mutationFn: userService.signin,
	});

	const signIn = async (data: SignInReq): Promise<SignInResult> => {
		clearUserInfoAndToken();
		try {
			const res = await signInMutation.mutateAsync(data);
			const rawUser = (res as any)?.user ?? (res as any)?.userInfo ?? {};
			if ((res as any)?.authenticated === false) {
				throw new Error("登录失败，请重新登录");
			}
			const rawNotice =
				typeof (res as any)?.sessionNotice === "string" ? ((res as any).sessionNotice as string).trim() : "";
			const takeoverFlag = Boolean((res as any)?.sessionTakeover);
			const takeoverMessage = rawNotice || (takeoverFlag ? "已切换到当前登录，其他会话已下线" : "");

			// 解析部门代码：rawUser.deptCode 优先；
			// 否则从 attributes.department / attributes.dept_code（可能为数组）中取首项。
			const resolveDeptCodeFromRaw = (raw: Record<string, unknown>): string | undefined => {
				const direct = raw.deptCode;
				if (typeof direct === "string" && direct.trim()) return direct.trim();
				const attrs = raw.attributes;
				if (attrs && typeof attrs === "object") {
					const attrMap = attrs as Record<string, unknown>;
					const candidate = attrMap.department ?? attrMap.dept_code;
					if (Array.isArray(candidate)) {
						const first = candidate[0];
						if (typeof first === "string" && first.trim()) return first.trim();
					} else if (typeof candidate === "string" && candidate.trim()) {
						return candidate.trim();
					}
				}
				return undefined;
			};

			// Sprint-17 hotfix — same precedence as deptCode, but for the human-readable name.
			const resolveDeptNameFromRaw = (raw: Record<string, unknown>): string | undefined => {
				const direct =
					(raw as { deptName?: unknown; dept_name?: unknown }).deptName ?? (raw as { dept_name?: unknown }).dept_name;
				if (typeof direct === "string" && direct.trim()) return direct.trim();
				const attrs = raw.attributes;
				if (attrs && typeof attrs === "object") {
					const attrMap = attrs as Record<string, unknown>;
					const candidate = attrMap.dept_name ?? attrMap.deptName ?? attrMap.org_name ?? attrMap.orgName;
					if (Array.isArray(candidate)) {
						const first = candidate[0];
						if (typeof first === "string" && first.trim()) return first.trim();
					} else if (typeof candidate === "string" && candidate.trim()) {
						return candidate.trim();
					}
				}
				return undefined;
			};

			// 适配后端数据格式：处理角色和权限信息
			const adaptedUser = {
				...rawUser,
				// 处理角色/权限信息 - 统一为字符串数组
				roles: normalizeToStringArray(rawUser.roles),
				permissions: normalizeToStringArray(rawUser.permissions),
				department:
					typeof rawUser.department === "string" && rawUser.department.trim() ? rawUser.department.trim() : undefined,
				deptCode: resolveDeptCodeFromRaw(rawUser as Record<string, unknown>),
				deptName: resolveDeptNameFromRaw(rawUser as Record<string, unknown>),
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

			const tokenExpiresAt = resolvePortalTokenExpiresAt({
				portalExpiresAt: (res as any)?.portalExpiresAt,
				portalExpiresIn: (res as any)?.portalExpiresIn,
				expiresIn: (res as any)?.expiresIn,
			});

			markPortalSessionLogin();
			setUserToken({
				authenticated: true,
				tokenExpiresAt,
			});
			setUserInfo(adaptedUser);

			if (takeoverMessage) {
				toast.info(takeoverMessage, {
					position: "top-center",
					closeButton: true,
				});
			}

			// 登录成功后异步获取并更新 Keycloak 翻译词条，不阻塞跳转。
			void KeycloakLocalizationService.getChineseTranslations()
				.then((translations: KeycloakTranslations) => updateLocalTranslations(translations))
				.catch((translationError) => {
					console.warn("Failed to load Keycloak translations:", translationError);
				});
			return {
				mode: "backend" as const,
				user: adaptedUser,
				token: {
					authenticated: true,
					tokenExpiresAt,
				},
				notice: takeoverMessage || undefined,
				takeover: takeoverFlag,
			};
		} catch (err: unknown) {
			const fallback = handleDevFallback({ error: err, payload: data, setUserToken, setUserInfo });
			if (fallback) {
				return fallback;
			}
			const msg = err instanceof Error ? err.message : "登录失败，请重试";
			toast.error(msg, {
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
	token: UserToken;
	notice?: string;
	takeover?: boolean;
};

type DevFallbackContext = {
	error: unknown;
	payload: SignInReq;
	setUserToken: (token: UserToken) => void;
	setUserInfo: (userInfo: UserInfo) => void;
};

const handleDevFallback = ({ error, payload, setUserToken, setUserInfo }: DevFallbackContext): SignInResult | null => {
	const enabled = String(import.meta.env.VITE_DEV_LOGIN_FALLBACK || "false").toLowerCase() === "true";
	if (!enabled) {
		return null;
	}
	if (!(import.meta.env.DEV && isDevFallbackAllowedHost() && isAxiosError(error) && error.response?.status === 401)) {
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

	const accessToken = buildDevFallbackToken("access", normalized);
	const refreshToken = buildDevFallbackToken("refresh", normalized);
	setUserToken({ authenticated: true, accessToken, refreshToken });
	setUserInfo(user);
	return {
		mode: "fallback",
		user,
		token: { authenticated: true, accessToken, refreshToken },
		notice: undefined,
		takeover: false,
	};
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
	const candidates: unknown[] = [info.username, bag.preferredUsername, bag.preferred_username, bag.user, bag.principal];
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
		const { userToken, userInfo } = useUserStore.getState();
		try {
			// 后端通过 HttpOnly portal_session cookie 定位并撤销当前会话。
			if (userToken?.authenticated) {
				await userService.logout(resolveUsernameForLogout(userInfo));
			}
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
