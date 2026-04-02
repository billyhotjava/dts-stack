import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { toast } from "sonner";
import { adminApi } from "@/admin/api/adminApi";
import { AdminSessionContext } from "@/admin/lib/session-context";
import ForbiddenView from "@/admin/views/forbidden";
import { normalizeAdminRole } from "@/admin/types";
import { LineLoading } from "@/components/loading";
import { redirectToLoginWithReturn } from "@/auth/session-auth";
import { canAccessProtectedRoute, shouldBlockWhileSessionBootstraps } from "@/auth/session-state";
import { usePortalSession, useSignOut } from "@/store/userStore";

type GuardState = "idle" | "redirecting" | "forbidden";

interface Props {
	children: React.ReactNode;
}

export default function AdminGuard({ children }: Props) {
	const signOut = useSignOut();
	const session = usePortalSession();
	const queryClient = useQueryClient();
	const [guardState, setGuardState] = useState<GuardState>("idle");

	useEffect(() => {
		if (shouldBlockWhileSessionBootstraps(session)) {
			return;
		}
		if (!canAccessProtectedRoute(session)) {
			setGuardState("redirecting");
			queryClient.removeQueries({ queryKey: ["admin", "whoami"] });
			redirectToLoginWithReturn();
			return;
		}
		setGuardState((state) => (state === "redirecting" ? "idle" : state));
	}, [queryClient, session]);

	const { data, isLoading, isError, error } = useQuery({
		queryKey: ["admin", "whoami"],
		queryFn: adminApi.getWhoami,
		retry: false,
		enabled: guardState === "idle" && canAccessProtectedRoute(session),
	});

	useEffect(() => {
		if (!canAccessProtectedRoute(session) || isLoading) {
			return;
		}

		const normalizedRole = normalizeAdminRole(data?.role);
		if (!isError && data?.allowed && normalizedRole) {
			if (guardState !== "idle") {
				setGuardState("idle");
			}
			return;
		}

		if (!isError && data) {
			if (!data.allowed || !normalizedRole) {
				setGuardState("forbidden");
				toast.error("当前账号无权访问管理端，请联系管理员开通权限");
				void signOut();
			}
			return;
		}

		if (isError && isAxiosError(error)) {
			const status = error.response?.status;
			if (status === 403) {
				setGuardState("forbidden");
				return;
			}
			if (status === 401) {
				setGuardState("redirecting");
				redirectToLoginWithReturn();
			}
		}
	}, [data, error, guardState, isError, isLoading, session, signOut]);

	const adminSession = useMemo(() => {
		if (!data?.allowed) return null;
		const normalizedRole = normalizeAdminRole(data.role);
		if (!normalizedRole) return null;
		return {
			role: normalizedRole,
			username: data.username,
			email: data.email,
		};
	}, [data]);

	if (shouldBlockWhileSessionBootstraps(session)) {
		return (
			<div className="flex h-full min-h-60 items-center justify-center">
				<LineLoading />
			</div>
		);
	}

	if (!canAccessProtectedRoute(session) || guardState === "redirecting") {
		return null;
	}

	if ((isLoading || isError) && !adminSession) {
		return (
			<div className="flex h-full min-h-60 items-center justify-center">
				<LineLoading />
			</div>
		);
	}

	if (guardState === "forbidden") {
		return <ForbiddenView />;
	}

	if (!adminSession) {
		return null;
	}

	return <AdminSessionContext.Provider value={adminSession}>{children}</AdminSessionContext.Provider>;
}
