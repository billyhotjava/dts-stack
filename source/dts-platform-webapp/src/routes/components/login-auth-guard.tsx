import { useCallback, useEffect } from "react";
import menuService from "@/api/services/menuService";
import { useMenuStore } from "@/store/menuStore";
import { useUserToken } from "@/store/userStore";
import { useRouter } from "../hooks";

type Props = {
	children: React.ReactNode;
};
export default function LoginAuthGuard({ children }: Props) {
    const router = useRouter();
    const { accessToken } = useUserToken();
    const menus = useMenuStore((s) => s.menus);

	const check = useCallback(() => {
		if (!accessToken) {
			router.replace("/auth/login");
		}
	}, [router, accessToken]);

    useEffect(() => {
        check();
    }, [check]);

    // After authenticated, if menus are empty, try to load backend menus once
    useEffect(() => {
        if (accessToken && (!Array.isArray(menus) || menus.length === 0)) {
            menuService
                .getMenuTree()
                .catch(() => {
                    /* ignore */
                });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [accessToken]);

	return <>{children}</>;
}
