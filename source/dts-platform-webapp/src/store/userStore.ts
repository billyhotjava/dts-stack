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
import { updateLocalTranslations } from "@/utils/translation";
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
					} catch {
						// ignore store access errors (e.g., during SSR)
					}
				},
			},
		}),
		{
			name: "userStore", // name of the item in the storage (must be unique)
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
	const { setUserToken, setUserInfo } = useUserActions();

	const signInMutation = useMutation({
		mutationFn: userService.signin,
	});

	const signIn = async (data: SignInReq): Promise<SignInResult> => {
		try {
			const res = await signInMutation.mutateAsync(data);
			const rawUser = (res as any)?.user ?? (res as any)?.userInfo ?? {};

			const pickToken = (value: unknown): string => {
				if (!value) return "";
				if (typeof value === "string") return value;
				if (typeof value === "object") {
					const obj = value as Record<string, unknown>;
					for (const key of ["token", "accessToken", "value"]) {
						const candidate = obj[key];
						if (typeof candidate === "string" && candidate) {
							return candidate;
						}
					}
				}
				return "";
			};

			const accessToken =
				pickToken((res as any)?.accessToken) || pickToken((res as any)?.access_token) || pickToken((res as any)?.token);
			const refreshToken = pickToken((res as any)?.refreshToken) || pickToken((res as any)?.refresh_token);
			if (!accessToken) {
				throw new Error("登录响应缺少访问令牌");
			}

			// 适配后端数据格式：处理角色和权限信息
			const adaptedUser = {
				...rawUser,
				// 处理角色/权限信息 - 统一为字符串数组
				roles: normalizeToStringArray(rawUser.roles),
				permissions: normalizeToStringArray(rawUser.permissions),
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

			setUserToken({ accessToken, refreshToken });
			setUserInfo(adaptedUser);

			// 登录成功后获取并更新Keycloak翻译词条
			try {
				const translations: KeycloakTranslations = await KeycloakLocalizationService.getChineseTranslations();
				updateLocalTranslations(translations);
			} catch (translationError) {
				console.warn("Failed to load Keycloak translations:", translationError);
				// 不阻塞登录流程，即使翻译加载失败也继续
			}
			return { mode: "backend" as const, user: adaptedUser, token: { accessToken, refreshToken } };
		} catch (err) {
			const fallback = handleDevFallback({ error: err, payload: data, setUserToken, setUserInfo });
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
	token: UserToken;
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

	const accessToken = `dev-access-${normalized}-${Date.now()}`;
	const refreshToken = `dev-refresh-${normalized}-${Date.now()}`;
	setUserToken({ accessToken, refreshToken });
	setUserInfo(user);
	return { mode: "fallback", user, token: { accessToken, refreshToken } };
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

export const useSignOut = () => {
	const { clearUserInfoAndToken } = useUserActions();
	const { userToken } = useUserStore.getState();

	const signOut = async () => {
		try {
			// 如果有refreshToken，调用后端登出接口
			if (userToken.refreshToken) {
				await userService.logout(userToken.refreshToken);
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
