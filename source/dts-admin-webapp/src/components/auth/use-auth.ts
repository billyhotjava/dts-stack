import { usePortalSession, useUserInfo } from "@/store/userStore";

export const useAuthCheck = (baseOn: "role" | "permission" = "permission") => {
	const session = usePortalSession();
	const { permissions = [], roles = [] } = useUserInfo();
	const resourcePool = baseOn === "role" ? roles : permissions;

	const check = (item: string): boolean => {
		if (!session.authenticated) {
			return false;
		}
		return resourcePool.some((p) => {
			if (typeof p === "string") {
				return p === item;
			}
			return p.code === item;
		});
	};

	const checkAny = (items: string[]) => {
		if (items.length === 0) {
			return true;
		}
		return items.some((item) => check(item));
	};

	const checkAll = (items: string[]) => {
		if (items.length === 0) {
			return true;
		}
		return items.every((item) => check(item));
	};

	return { check, checkAny, checkAll };
};
