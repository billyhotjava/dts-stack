import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { getPortalSessionStatus, type PortalSessionStatus } from "@/api/platformApi";
import { useLoginStateContext } from "@/pages/sys/login/providers/login-provider";
import { LOGIN_ROUTE } from "@/routes/constants";
import { useRouter } from "@/routes/hooks";
import { useSignOut, useUserInfo } from "@/store/userStore";
import { Button } from "@/ui/button";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/ui/dropdown-menu";
import { formatPortalSessionStatus, resolveSessionStatusTone } from "./sessionStatus.helpers";

/**
 * Account Dropdown
 */
export default function AccountDropdown() {
	const { replace } = useRouter();
	const { username, email, avatar } = useUserInfo();
	const signOut = useSignOut();
	const { backToLogin } = useLoginStateContext();
	const { t } = useTranslation();
	const [open, setOpen] = useState(false);
	const [status, setStatus] = useState<PortalSessionStatus | null>(null);
	const [loading, setLoading] = useState(false);

	const logout = async () => {
		try {
			await signOut();
			backToLogin();
		} catch (error) {
			console.log(error);
		} finally {
			replace(LOGIN_ROUTE);
		}
	};

	useEffect(() => {
		if (!open) return;
		let cancelled = false;

		const load = async () => {
			try {
				setLoading(true);
				const next = await getPortalSessionStatus();
				if (!cancelled) {
					setStatus(next);
				}
			} catch {
				if (!cancelled) {
					setStatus(null);
				}
			} finally {
				if (!cancelled) {
					setLoading(false);
				}
			}
		};

		void load();

		return () => {
			cancelled = true;
		};
	}, [open]);

	const statusTone = resolveSessionStatusTone(status);
	const statusClassName =
		statusTone === "danger"
			? "text-destructive"
			: statusTone === "warning"
				? "text-warning"
				: "text-text-secondary";

	return (
		<DropdownMenu open={open} onOpenChange={setOpen}>
			<DropdownMenuTrigger asChild>
				<Button
					variant="ghost"
					size="icon"
					className="h-10 w-10 rounded-2xl border border-border/70 bg-background shadow-[0_10px_24px_rgba(15,23,42,0.06)] hover:bg-accent"
				>
					<img className="h-7 w-7 rounded-xl" src={avatar} alt="" />
				</Button>
			</DropdownMenuTrigger>
			<DropdownMenuContent align="end" className="w-64 rounded-[20px] border border-border/70 bg-card/95 p-1 shadow-[0_18px_40px_rgba(15,23,42,0.12)]">
				<div className="flex items-center gap-3 rounded-2xl bg-background px-3 py-3">
					<img className="h-11 w-11 rounded-2xl" src={avatar} alt="" />
					<div className="flex min-w-0 flex-col items-start">
						<div className="truncate text-text-primary text-sm font-semibold">{username}</div>
						<div className="truncate text-text-secondary text-xs">{email}</div>
					</div>
				</div>
				<div className="px-3 py-2">
					<div className="text-[11px] uppercase tracking-[0.14em] text-text-tertiary">Session</div>
					<div className={`mt-1 text-xs ${statusClassName}`}>
						{loading ? "正在同步服务器会话状态..." : formatPortalSessionStatus(status)}
					</div>
					{status?.expiresAt ? (
						<div className="mt-1 text-[11px] text-text-tertiary">到期时间 {status.expiresAt}</div>
					) : null}
				</div>
				<DropdownMenuSeparator />
				<DropdownMenuItem className="font-bold text-warning" onClick={logout}>
					{t("sys.login.logout")}
				</DropdownMenuItem>
			</DropdownMenuContent>
		</DropdownMenu>
	);
}
