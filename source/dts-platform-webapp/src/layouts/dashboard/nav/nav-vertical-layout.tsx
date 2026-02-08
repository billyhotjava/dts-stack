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
				"fixed inset-y-0 left-0 flex-col h-full bg-sidebar border-r border-dashed z-nav transition-[width] duration-300 ease-in-out",
				className,
			)}
			style={{
				width: navWidth,
				backgroundColor: sidebarBg,
			}}
		>
			<div
				className={cn("relative flex items-center py-4 px-3 h-[var(--layout-header-height)] select-none", {
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
					className="h-7 w-7 absolute right-0 translate-x-1/2"
				>
					{themeLayout === ThemeLayout.Mini ? (
						<Icon icon="lucide:arrow-right-to-line" size={12} />
					) : (
						<Icon icon="lucide:arrow-left-to-line" size={12} />
					)}
				</Button>
			</div>

			<hr className="border-t border-border/40 mx-3" />

			<ScrollArea className={cn("h-[calc(100vh-var(--layout-header-height))] px-2")}>
				<div className="pb-16">
					{themeLayout === ThemeLayout.Mini ? <NavMini data={data} /> : <NavVertical data={data} />}
				</div>
			</ScrollArea>
		</nav>
	);
}
