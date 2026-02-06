import Logo from "@/components/logo";
import { NavMini, NavVertical } from "@/components/nav";
import type { NavProps } from "@/components/nav/types";
import { GLOBAL_CONFIG } from "@/global-config";
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
	const handleToggle = () => {
		setThemeLayout(themeLayout === ThemeLayout.Mini ? ThemeLayout.Vertical : ThemeLayout.Mini);
	};
	return (
		<nav
			data-slot="slash-layout-nav"
			data-dark-sidebar={isDarkSidebar ? "true" : undefined}
			className={cn(
				"fixed inset-y-0 left-0 flex-col h-full bg-background border-r border-dashed z-nav transition-[width] duration-300 ease-in-out",
				className,
			)}
			style={{
				width: navWidth,
			}}
		>
			<div
				className={cn("relative flex items-center py-4 px-3 h-[var(--layout-header-height)] select-none", {
					"justify-center": themeLayout === ThemeLayout.Mini,
				})}
			>
				{themeLayout === ThemeLayout.Mini ? (
					<Logo />
				) : (
					<div className="flex items-center gap-3">
						<Logo />
						<div className="flex items-start gap-2 whitespace-nowrap">
							<Icon icon="mdi:star" size={22} className="text-red-500" color="#ef4444" />
							<span className="flex flex-col leading-tight">
								<span className="text-base font-semibold text-foreground">
									{(GLOBAL_CONFIG.appName || "BI数智平台").replace("管理", "")}
								</span>
								{/* <span className="text-sm font-bold text-red-600">机密</span> */}
							</span>
						</div>
					</div>
				)}

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

			<ScrollArea className={cn("h-[calc(100vh-var(--layout-header-height))] px-2 bg-background")}>
				{themeLayout === ThemeLayout.Mini ? <NavMini data={data} /> : <NavVertical data={data} />}
			</ScrollArea>
		</nav>
	);
}
