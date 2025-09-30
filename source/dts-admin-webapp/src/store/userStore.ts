import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { UserInfo, UserToken } from "#/entity";
import type { KeycloakTranslations } from "#/keycloak";
import { StorageEnum } from "#/enum";
import userService, { type SignInReq } from "@/api/services/userService";
import { KeycloakLocalizationService } from "@/api/services/keycloakLocalizationService";
import { updateLocalTranslations } from "@/utils/translation";

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

	const signIn = async (data: SignInReq) => {
		try {
			const res = await signInMutation.mutateAsync(data);
			const { user, accessToken, refreshToken } = res;

			// 适配后端数据格式：处理角色和权限信息
			const resolvedFullName = user.fullName || user.firstName || user.lastName || user.username || "";

			const adaptedUser = {
				...user,
				// 处理角色信息 - 保持字符串数组格式
				roles: Array.isArray(user.roles) ? user.roles : [],
				// 处理权限信息 - 保持字符串数组格式
				permissions: Array.isArray(user.permissions) ? user.permissions : [],
				// 为用户设置默认头像
				avatar: user.avatar || "/src/assets/icons/ic-user.svg",
				// 确保必要的字段存在
				firstName: user.firstName || resolvedFullName,
				fullName: resolvedFullName,
				lastName: user.lastName || "",
				email: user.email || "",
				enabled: user.enabled !== undefined ? user.enabled : true,
			};

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
