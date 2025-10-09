import { type RouteObject } from "react-router";
import { useEffect, useMemo } from "react";
import { useMenuStore } from "@/store/menuStore";
import useUserStore from "@/store/userStore";
import { useRouter } from "@/routes/hooks";
import DashboardLayout from "@/layouts/dashboard";
import LoginAuthGuard from "@/routes/components/login-auth-guard";
import { getFrontendDashboardRoutes } from "./frontend";

const DASHBOARD_CHILD_ROUTES = getFrontendDashboardRoutes();

export const dashboardRoutes: RouteObject[] = [
    {
        element: (
            <LoginAuthGuard>
                <DashboardLayout />
            </LoginAuthGuard>
        ),
        children: [{ index: true, element: <FallbackDashboardIndex /> }, ...DASHBOARD_CHILD_ROUTES],
    },
];

function FallbackDashboardIndex() {
    const router = useRouter();
    const menus = useMenuStore((s) => s.menus);
    const roles = useUserStore((s) => s.userInfo.roles || []);
    const hasAdmin = useMemo(() => {
        return roles.some((r: any) => {
            const code = typeof r === "string" ? r : r?.code;
            const upper = (code || "").toString().toUpperCase();
            return upper === "ROLE_OP_ADMIN" || upper === "ROLE_SYS_ADMIN" || upper === "ROLE_ADMIN";
        });
    }, [roles]);

    useEffect(() => {
        // Router 已设置 basename，这里必须用“路由内路径”
        const target = "/dashboard/workbench";
        if (Array.isArray(menus) && menus.length > 0) {
            router.replace(target);
        } else if (hasAdmin) {
            // Admins also land on welcome page
            router.replace(target);
        }
    }, [menus, hasAdmin, router]);

    return null; // render nothing while waiting; guard will load menus
}
