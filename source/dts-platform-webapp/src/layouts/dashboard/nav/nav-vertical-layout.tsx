import { useEffect } from "react";
import Logo from "@/components/logo";
import { NavVertical } from "@/components/nav";
import type { NavProps } from "@/components/nav/types";
import { GLOBAL_CONFIG } from "@/global-config";
import { Icon } from "@/components/icon";
import { useSettingActions, useSettings } from "@/store/settingStore";
import { ThemeLayout } from "@/types/enum";
import { ScrollArea } from "@/ui/scroll-area";
import { cn } from "@/utils";

type Props = {
	data: NavProps["data"];
	className?: string;
};

export function NavVerticalLayout({ data, className }: Props) {
	const { themeLayout } = useSettings();
	const { setThemeLayout } = useSettingActions();

	useEffect(() => {
		if (themeLayout === ThemeLayout.Mini) {
			setThemeLayout(ThemeLayout.Vertical);
		}
	}, [themeLayout, setThemeLayout]);
	return (
		<nav
			data-slot="slash-layout-nav"
			className={cn(
				"fixed inset-y-0 left-0 flex-col h-full bg-background border-r border-dashed z-nav transition-[width] duration-300 ease-in-out",
				className,
			)}
			style={{
				width: "var(--layout-nav-width)",
			}}
		>
			<div className="relative flex items-center gap-3 py-4 px-3 h-[var(--layout-header-height)] select-none">
				<Logo />
				<div className="flex items-start gap-2 whitespace-nowrap">
					<Icon icon="mdi:star" size={22} className="text-red-500" color="#ef4444" />
					<span className="flex flex-col leading-tight">
						<span className="text-base font-semibold text-foreground">
							{(GLOBAL_CONFIG.appName || "BI数智平台").replace("管理", "")}
						</span>
						<span className="text-sm font-bold text-red-600">机密</span>
					</span>
				</div>
			</div>

			<ScrollArea className={cn("h-[calc(100vh-var(--layout-header-height))] px-2 bg-background")}>
				<NavVertical data={data} />
			</ScrollArea>
		</nav>
	);
}
