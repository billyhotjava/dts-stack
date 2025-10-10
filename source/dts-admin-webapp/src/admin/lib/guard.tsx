import { useCallback, useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import { AdminSessionContext } from "@/admin/lib/session-context";
import ForbiddenView from "@/admin/views/forbidden";
import { normalizeAdminRole } from "@/admin/types";
import { useRouter } from "@/routes/hooks";
import { useSignOut, useUserToken, useUserActions } from "@/store/userStore";
import { LineLoading } from "@/components/loading";
import userService from "@/api/services/userService";

interface Props {
	children: React.ReactNode;
}

export default function AdminGuard({ children }: Props) {
    const router = useRouter();
    const signOut = useSignOut();
    const token = useUserToken();
    const { setUserToken } = useUserActions();
    const [blocked, setBlocked] = useState(false);
    const [triedRefresh, setTriedRefresh] = useState(false);

	const { data, isLoading, isError } = useQuery({
		queryKey: ["admin", "whoami"],
		queryFn: adminApi.getWhoami,
		retry: false,
	});

    const handleForbidden = useCallback(async () => {
        if (blocked) return;
        setBlocked(true);
        try {
            await signOut();
        } finally {
            router.replace("/403");
        }
    }, [blocked, router, signOut]);

    useEffect(() => {
        if (isLoading) return;
        const normalizedRole = normalizeAdminRole(data?.role);
        if (!isError && data?.allowed && normalizedRole) return;

        // whoami 失败时，尝试一次基于 refreshToken 的静默续期，然后重试
        (async () => {
            try {
                if (!triedRefresh && token?.refreshToken) {
                    setTriedRefresh(true);
                    const res: any = await userService.refresh(token.refreshToken);
                    const nextAccess = res?.accessToken as string | undefined;
                    const nextRefresh = (res?.refreshToken as string | undefined) || token.refreshToken;
                    if (nextAccess) {
                        setUserToken({ accessToken: nextAccess, refreshToken: nextRefresh });
                        // 触发 whoami 重新获取
                        await adminApi.getWhoami();
                        // 成功后，交由 React Query 重新渲染（外层 useQuery 会重新跑）
                        return;
                    }
                }
            } catch {
                // ignore and fall through to forbidden
            }
            handleForbidden();
        })();
    }, [data, isError, isLoading, handleForbidden, token?.refreshToken, setUserToken, triedRefresh]);

	const session = useMemo(() => {
		if (!data?.allowed) return null;
		const normalizedRole = normalizeAdminRole(data.role);
		if (!normalizedRole) return null;
		return {
			role: normalizedRole,
			username: data.username,
			email: data.email,
		};
	}, [data]);

	if (isLoading && !session && !blocked) {
		return (
			<div className="flex h-full min-h-60 items-center justify-center">
				<LineLoading />
			</div>
		);
	}

	if (blocked) {
		return <ForbiddenView />;
	}

	if (!session) {
		return null;
	}

	return <AdminSessionContext.Provider value={session}>{children}</AdminSessionContext.Provider>;
}
