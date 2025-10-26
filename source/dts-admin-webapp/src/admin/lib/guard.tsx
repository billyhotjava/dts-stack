import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { toast } from "sonner";
import { adminApi } from "@/admin/api/adminApi";
import { AdminSessionContext } from "@/admin/lib/session-context";
import ForbiddenView from "@/admin/views/forbidden";
import { normalizeAdminRole } from "@/admin/types";
import { useRouter } from "@/routes/hooks";
import { useSignOut, useUserToken, useUserActions } from "@/store/userStore";
import { LineLoading } from "@/components/loading";
import userService from "@/api/services/userService";

type GuardState = "idle" | "refreshing" | "redirecting" | "forbidden";

const extractRequestToken = (err: unknown): string | null => {
	if (!isAxiosError(err)) return null;
	const headers = err.config?.headers as Record<string, unknown> | undefined;
	if (!headers) return null;
	const candidate = headers["Authorization"] ?? headers["authorization"];
	const raw =
		typeof candidate === "string"
			? candidate
			: Array.isArray(candidate) && candidate.length > 0
				? String(candidate[0] ?? "")
				: "";
	if (!raw) return null;
	const trimmed = raw.trim();
	if (!trimmed) return null;
	if (/^Bearer\s+/i.test(trimmed)) {
		return trimmed.replace(/^Bearer\s+/i, "").trim();
	}
	return trimmed;
};

const hasResponseHeader = (err: unknown, header: string): boolean => {
	if (!isAxiosError(err)) return false;
	const headers = err.response?.headers as Record<string, unknown> | undefined;
	if (!headers) return false;
	const lower = header.toLowerCase();
	const candidate = headers[header] ?? headers[lower];
	if (candidate === undefined) return false;
	const value = Array.isArray(candidate) ? candidate[0] : candidate;
	if (typeof value === "string") {
		return value.length === 0 || value.toLowerCase() === "true";
	}
	return Boolean(value);
};

interface Props {
	children: React.ReactNode;
}

export default function AdminGuard({ children }: Props) {
	const router = useRouter();
	const signOut = useSignOut();
	const token = useUserToken();
	const { setUserToken } = useUserActions();
	const queryClient = useQueryClient();
	const [guardState, setGuardState] = useState<GuardState>("idle");
	const [triedRefresh, setTriedRefresh] = useState(false);
	const redirectingRef = useRef(false);
	const lastReasonRef = useRef<"session-expired" | "concurrent-login" | "signed-out" | null>(null);

	const { data, isLoading, isError, error } = useQuery({
		queryKey: ["admin", "whoami"],
		queryFn: adminApi.getWhoami,
		retry: false,
		enabled: guardState === "idle",
	});

	const redirectToLogin = useCallback(
		async (reason: "session-expired" | "concurrent-login" | "signed-out") => {
			if (redirectingRef.current && lastReasonRef.current === reason) {
				return;
			}
			redirectingRef.current = true;
			lastReasonRef.current = reason;
			setGuardState("redirecting");
			queryClient.cancelQueries({ queryKey: ["admin", "whoami"] });
			if (reason === "concurrent-login") {
				toast.error("账号已在其他浏览器登录，本会话已退出", { id: "session-conflict" });
			} else if (reason === "session-expired") {
				toast.error("会话已过期，请重新登录", { id: "session-expired" });
			}
			try {
				await signOut();
			} finally {
				const params = new URLSearchParams();
				params.set("reason", reason);
				router.replace(`/auth/login?${params.toString()}`);
			}
		},
		[queryClient, router, signOut],
	);

	useEffect(() => {
		if (guardState !== "idle") return;
		if (isLoading) return;

		const normalizedRole = normalizeAdminRole(data?.role);
		if (!isError && data?.allowed && normalizedRole) {
			return;
		}

		// 成功返回但身份不合法：展示403，并清理本地信息
		if (!isError && data) {
			if (!data.allowed || !normalizedRole) {
				setGuardState("forbidden");
				toast.error("当前账号无权访问管理端，请联系管理员开通权限");
				void signOut();
				return;
			}
		}

		const requestToken = extractRequestToken(error);
		if (requestToken && token?.accessToken && requestToken !== token.accessToken) {
			// 忽略使用旧 accessToken 的响应，等待新的 whoami 结果
			return;
		}

		const status = isAxiosError(error) ? error.response?.status : undefined;
		const errorMessage =
			isAxiosError(error) && typeof error.response?.data === "object" && error.response?.data !== null
				? (error.response?.data as any).message
				: undefined;
		const hasConflictHeader = hasResponseHeader(error, "x-session-conflict");
		const hasExpiredHeader = hasResponseHeader(error, "x-session-expired");
		const conflictByMessage = Boolean(errorMessage?.includes("其他位置登录") || errorMessage?.includes("会话已失效"));
		const expiredByMessage = Boolean(errorMessage?.includes("已过期") || errorMessage?.includes("已超时"));

		if (hasConflictHeader || conflictByMessage) {
			redirectToLogin("concurrent-login");
			return;
		}
		if (hasExpiredHeader || expiredByMessage) {
			redirectToLogin("session-expired");
			return;
		}

		const shouldTryRefresh = !triedRefresh && token?.refreshToken && status === 401;

		if (shouldTryRefresh) {
			setTriedRefresh(true);
			setGuardState("refreshing");
			void (async () => {
				try {
					await queryClient.cancelQueries({ queryKey: ["admin", "whoami"] });
					const res: any = await userService.refresh(token.refreshToken!);
					const nextAccess = res?.accessToken as string | undefined;
					const nextRefresh = (res?.refreshToken as string | undefined) || token.refreshToken;
					if (nextAccess) {
						setUserToken({ accessToken: nextAccess, refreshToken: nextRefresh });
						try {
							const whoami = await queryClient.fetchQuery({
								queryKey: ["admin", "whoami"],
								queryFn: adminApi.getWhoami,
							});
							const refreshedRole = normalizeAdminRole(whoami?.role);
							if (whoami?.allowed && refreshedRole) {
								queryClient.setQueryData(["admin", "whoami"], whoami);
								setGuardState("idle");
								setTriedRefresh(false);
								return;
							}
						} catch {
							// fall through to redirect
						}
					}
				} catch {
					// ignore and fall through to redirect
				}
				redirectToLogin("session-expired");
			})();
			return;
		}

		if (status === 401) {
			redirectToLogin("session-expired");
		}
	}, [
		data,
		error,
		isError,
		isLoading,
		redirectToLogin,
		setUserToken,
		signOut,
		token?.refreshToken,
		token?.accessToken,
		triedRefresh,
		guardState,
		queryClient,
	]);

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

	if (isLoading && !session && guardState === "idle") {
		return (
			<div className="flex h-full min-h-60 items-center justify-center">
				<LineLoading />
			</div>
		);
	}

	if (guardState === "forbidden") {
		return <ForbiddenView />;
	}

	if (guardState === "refreshing") {
		return (
			<div className="flex h-full min-h-60 items-center justify-center">
				<LineLoading />
			</div>
		);
	}

	if (guardState === "redirecting") {
		return null;
	}

	if (!session) {
		return null;
	}

	return <AdminSessionContext.Provider value={session}>{children}</AdminSessionContext.Provider>;
}
