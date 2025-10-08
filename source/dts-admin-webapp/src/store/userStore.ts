import { useMutation } from "@tanstack/react-query";
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

// Decode JWT payload (best-effort) and extract roles from common Keycloak/OIDC claims
const decodeJwtPayload = (token?: string): Record<string, unknown> | null => {
	if (!token) return null;
	try {
		const parts = token.split(".");
		if (parts.length < 2) return null;
		let payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
		while (payload.length % 4 !== 0) payload += "=";
		const json = atob(payload);
		return JSON.parse(json) as Record<string, unknown>;
	} catch {
		return null;
	}
};

const extractRolesFromClaims = (claims: Record<string, unknown> | null | undefined): string[] => {
	if (!claims || typeof claims !== "object") return [];
	const roles = new Set<string>();
	const pushAll = (arr: unknown) => {
		if (Array.isArray(arr)) {
			for (const item of arr) {
				const s = String(item || "").trim();
				if (s) roles.add(s);
			}
		}
	};
	// Keycloak realm roles
	const realm = (claims as any)?.realm_access;
	if (realm && typeof realm === "object") {
		pushAll((realm as any).roles);
	}
	// Keycloak resource (client) roles
	const resource = (claims as any)?.resource_access;
	if (resource && typeof resource === "object") {
		for (const key of Object.keys(resource as Record<string, unknown>)) {
			const entry = (resource as any)[key];
			if (entry && typeof entry === "object") {
				pushAll((entry as any).roles);
			}
		}
	}
	// Spring Security authorities (if present)
	pushAll((claims as any)?.authorities);
	// Flat roles claim (rare)
	pushAll((claims as any)?.roles);
	return Array.from(roles);
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

	const USERNAME_FALLBACK_NAME: Record<string, string> = {
		sysadmin: "系统管理员",
		authadmin: "授权管理员",
		auditadmin: "安全审计员",
		opadmin: "业务运维管理员",
	};

	const signIn = async (data: SignInReq) => {
		try {
			const res = await signInMutation.mutateAsync(data);
			const { user, accessToken, refreshToken } = res;

			// 适配后端数据格式：处理角色和权限信息
			const getAttr = (key: string) => {
				const attr = user.attributes as Record<string, string[]> | undefined;
				if (!attr) return "";
				const value = attr[key] || attr[key.toLowerCase()];
				if (Array.isArray(value) && value.length > 0) {
					const found = value.find((item) => item && item.trim());
					return (found || value[0] || "").trim();
				}
				return "";
			};
			const attributeFullName = getAttr("fullName");
			const usernameLower = user.username?.toLowerCase() ?? "";
			const fallbackName = USERNAME_FALLBACK_NAME[usernameLower] || "";
			const resolvedFullName =
				attributeFullName || user.fullName || user.firstName || fallbackName || user.lastName || user.username || "";

			const adaptedUser = {
				...user,
				attributes: (user.attributes as Record<string, string[]>) || {},
				// 处理角色/权限信息 - 统一为字符串数组
				roles: normalizeToStringArray(user.roles),
				permissions: normalizeToStringArray(user.permissions),
				// 为用户设置默认头像（使用 public 目录下的静态资源路径，兼容生产环境）
				avatar: resolveAvatar(user.avatar),
				// 确保必要的字段存在
				firstName: user.firstName || attributeFullName || resolvedFullName,
				fullName: resolvedFullName,
				lastName: user.lastName || "",
				email: user.email || "",
				enabled: user.enabled !== undefined ? user.enabled : true,
			};

			// Augment roles from access token claims when backend user lacks explicit roles
			try {
				const claims = decodeJwtPayload(accessToken);
				const tokenRoles = extractRolesFromClaims(claims);
				if (Array.isArray(adaptedUser.roles) && tokenRoles.length > 0) {
					const merged = new Set<string>([...adaptedUser.roles.map((r: any) => String(r || "")), ...tokenRoles]);
					adaptedUser.roles = Array.from(merged);
				} else if (tokenRoles.length > 0) {
					adaptedUser.roles = tokenRoles;
				}
			} catch {
				// ignore token parsing errors
			}

			// Helper to expand role synonyms and normalize naming
			const expandSynonyms = (roles: string[]): Set<string> => {
				const set = new Set<string>((roles || []).map((r) => String(r || "").toUpperCase()));
				if (set.has("SYSADMIN")) set.add("ROLE_SYS_ADMIN");
				if (set.has("AUTHADMIN")) set.add("ROLE_AUTH_ADMIN");
				if (set.has("AUDITADMIN")) set.add("ROLE_SECURITY_AUDITOR");
				if (set.has("SECURITYAUDITOR")) set.add("ROLE_SECURITY_AUDITOR");
				// Accept common prefixed variants from legacy realms
				if (set.has("ROLE_AUDITOR_ADMIN") || set.has("ROLE_AUDIT_ADMIN") || set.has("ROLE_SECURITYAUDITOR")) {
					set.add("ROLE_SECURITY_AUDITOR");
				}
				if (set.has("ROLE_SYSADMIN") || set.has("ROLE_SYSTEM_ADMIN")) {
					set.add("ROLE_SYS_ADMIN");
				}
				if (set.has("ROLE_AUTHADMIN") || set.has("ROLE_IAM_ADMIN")) {
					set.add("ROLE_AUTH_ADMIN");
				}
				if (set.has("OPADMIN")) set.add("ROLE_OP_ADMIN");
				return set;
			};

			const canonicalizeRoles = (roles: string[]): string[] => {
				const set = expandSynonyms(roles);
				// preserve originals + ensure canonical role codes exist
				const originals = (roles || []).map((r) => String(r || ""));
				const canonicals = ["ROLE_SYS_ADMIN", "ROLE_AUTH_ADMIN", "ROLE_SECURITY_AUDITOR", "ROLE_OP_ADMIN"].filter((r) =>
					set.has(r),
				);
				return Array.from(new Set([...originals, ...canonicals]));
			};

			// Normalize user roles canonically for downstream guards/menu auth
			adaptedUser.roles = canonicalizeRoles(Array.isArray(adaptedUser.roles) ? (adaptedUser.roles as string[]) : []);

			const FE_GUARD_ENABLED = String(import.meta.env.VITE_ENABLE_FE_GUARD || "false").toLowerCase() === "true";
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
				// Defense-in-depth: explicitly forbid platform-only role on admin console
				if (userSet.has("ROLE_OP_ADMIN")) {
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
		} catch (err) {
			toast.error(err.message, {
				position: "top-center",
			});
			throw err;
		}
	};

	return signIn;
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
