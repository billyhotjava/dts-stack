import { useTranslation } from "react-i18next";
import { NavLink } from "react-router";
import { useLoginStateContext } from "@/pages/sys/login/providers/login-provider";
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

const USERNAME_FALLBACK_NAME: Record<string, string> = {
	sysadmin: "系统管理员",
	authadmin: "授权管理员",
	auditadmin: "安全审计员",
	opadmin: "业务运维管理员",
};

/**
 * Account Dropdown
 */
export default function AccountDropdown() {
	const { replace } = useRouter();
	const { username, email, avatar, fullName } = useUserInfo();
	const fallbackName = USERNAME_FALLBACK_NAME[username?.toLowerCase() ?? ""] || "";
	const signOut = useSignOut();
	const { backToLogin } = useLoginStateContext();
	const { t } = useTranslation();
	const isDefaultAvatar = avatar?.includes("/assets/icons/ic-user.svg");
	const avatarClassName = isDefaultAvatar ? "rounded-full dark:brightness-0 dark:invert" : "rounded-full";

	const logout = async () => {
		try {
			await signOut();
			backToLogin();
		} catch (error) {
			console.log(error);
		} finally {
			replace("/auth/login");
		}
	};

	return (
		<DropdownMenu>
			<DropdownMenuTrigger asChild>
				<Button
					variant="ghost"
					size="icon"
					className="h-10 w-10 rounded-2xl border border-border/70 bg-background shadow-[0_10px_24px_rgba(15,23,42,0.06)] hover:bg-accent"
				>
					<img className={`h-7 w-7 rounded-xl ${avatarClassName}`} src={avatar} alt="" />
				</Button>
			</DropdownMenuTrigger>
			<DropdownMenuContent align="end" className="w-72 rounded-[20px] border border-border/70 bg-card/95 p-1 shadow-[0_18px_40px_rgba(15,23,42,0.12)]">
				<div className="flex items-center gap-3 rounded-2xl bg-background px-3 py-3">
					<img className={`h-11 w-11 rounded-2xl ${avatarClassName}`} src={avatar} alt="" />
					<div className="flex min-w-0 flex-col items-start">
						<div className="truncate text-text-primary text-sm font-semibold">{fullName || fallbackName || username}</div>
						<div className="truncate text-text-secondary text-xs">
							{email}
							{username ? `（${username}）` : null}
						</div>
					</div>
				</div>
				<DropdownMenuSeparator />
				<DropdownMenuItem asChild>
					<NavLink to="/management/user/profile">{t("sys.nav.user.profile")}</NavLink>
				</DropdownMenuItem>
				<DropdownMenuSeparator />
				<DropdownMenuItem className="font-bold text-warning" onClick={logout}>
					{t("sys.login.logout")}
				</DropdownMenuItem>
			</DropdownMenuContent>
		</DropdownMenu>
	);
}
