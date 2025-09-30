import { NavLink, Outlet } from "react-router";
import { getMenusByRole } from "@/admin/config/menus";
import { useAdminSession } from "@/admin/lib/session-context";
import { useAdminLocale } from "@/admin/lib/locale";
import Icon from "@/components/icon/icon";
import { Button } from "@/ui/button";
import { Text, Title } from "@/ui/typography";

export default function AdminLayout() {
	const session = useAdminSession();
	const menus = getMenusByRole(session.role);
	const { translateRole } = useAdminLocale();

	return (
		<div className="flex min-h-screen bg-background">
			<aside className="hidden w-64 flex-col border-r bg-card p-4 lg:flex">
				<div className="mb-6 flex flex-col gap-1">
					<Title as="h2" className="text-xl font-semibold">
						管理端控制台
					</Title>
				<Text variant="body3" className="text-muted-foreground">
					角色：{translateRole(session.role, session.role?.toLowerCase() || "--")}
				</Text>
					<Text variant="body3" className="text-muted-foreground">
						{session.username || session.email}
					</Text>
				</div>
				<nav className="flex-1 space-y-2">
					{menus.map((item) => (
						<NavLink
							key={item.key}
							to={item.path}
							className={({ isActive }) =>
								`flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
									isActive
										? "bg-primary text-primary-foreground"
										: "text-muted-foreground hover:bg-muted hover:text-foreground"
								}`
							}
						>
							{item.icon ? <Icon icon={item.icon} size={18} /> : null}
							<span>{item.label}</span>
						</NavLink>
					))}
				</nav>
			</aside>
			<main className="flex-1 overflow-y-auto p-4 lg:p-8">
				<div className="mb-6 flex items-center justify-between">
					<Title as="h1" className="text-2xl font-semibold">
						管理操作
					</Title>
					<Button variant="outline" size="sm" onClick={() => window.location.reload()}>
						刷新
					</Button>
				</div>
				<Outlet />
			</main>
		</div>
	);
}
