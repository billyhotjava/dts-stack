import Logo from "@/components/logo";
import Brand from "@/components/brand";
import { NavMini, NavVertical } from "@/components/nav";
import type { NavProps } from "@/components/nav/types";
import { Icon } from "@/components/icon";
import { useSettingActions, useSettings } from "@/store/settingStore";
import { ThemeLayout, ThemeMode } from "@/types/enum";
import { Button } from "@/ui/button";
import { ScrollArea } from "@/ui/scroll-area";
import { cn } from "@/utils";

type Props = {
	data: NavProps["data"];
	className?: string;
};

export function NavVerticalLayout({ data, className }: Props) {
	const { themeLayout, darkSidebar, themeMode } = useSettings();
	const { setThemeLayout } = useSettingActions();
	const isDarkSidebar = darkSidebar && themeMode !== ThemeMode.Dark;

	const navWidth = themeLayout === ThemeLayout.Vertical ? "var(--layout-nav-width)" : "var(--layout-nav-width-mini)";
	const isDark = themeMode === ThemeMode.Dark;
	const sidebarBg = isDark ? "#161616" : isDarkSidebar ? "hsla(205, 19%, 23%, 1)" : "#F5F5F5";
	const handleToggle = () => {
		setThemeLayout(themeLayout === ThemeLayout.Mini ? ThemeLayout.Vertical : ThemeLayout.Mini);
	};
	return (
		<nav
			data-slot="slash-layout-nav"
			data-dark-sidebar={isDarkSidebar ? "true" : undefined}
			className={cn(
				"fixed inset-y-0 left-0 z-nav flex h-full flex-col border-r transition-[width] duration-300 ease-in-out",
				isDark || isDarkSidebar
					? "border-white/10 shadow-[0_24px_48px_rgba(15,23,42,0.22)]"
					: "border-border/70 shadow-[0_18px_40px_rgba(15,23,42,0.08)]",
				className,
			)}
			style={{
				width: navWidth,
				backgroundColor: sidebarBg,
			}}
		>
			<div
				className={cn("relative flex h-[calc(var(--layout-header-height)+12px)] items-center px-4 py-5 select-none", {
					"justify-center": themeLayout === ThemeLayout.Mini,
				})}
			>
				<div className="flex items-center justify-center">
					{themeLayout === ThemeLayout.Mini ? <Logo /> : <Brand />}
				</div>

				<Button
					variant="outline"
					size="icon"
					onClick={handleToggle}
					className={cn(
						"h-8 w-8 absolute right-0 translate-x-1/2 rounded-full border shadow-sm",
						isDark || isDarkSidebar
							? "border-white/10 bg-white text-slate-900 hover:bg-slate-100"
							: "border-border/70 bg-background text-text-primary hover:bg-accent",
					)}
				>
					{themeLayout === ThemeLayout.Mini ? (
						<Icon icon="lucide:arrow-right-to-line" size={12} />
					) : (
						<Icon icon="lucide:arrow-left-to-line" size={12} />
					)}
				</Button>
			</div>

			<hr className={cn("mx-4 border-t", isDark || isDarkSidebar ? "border-white/10" : "border-border/60")} />

			<ScrollArea className={cn("h-[calc(100vh-var(--layout-header-height)-12px)] px-3 pb-4")}>
				<div className="pb-20 pt-3">
					{themeLayout === ThemeLayout.Mini ? <NavMini data={data} /> : <NavVertical data={data} />}
				</div>
			</ScrollArea>
		</nav>
	);
}
