import { useEffect } from "react";
import Logo from "@/components/logo";
import { NavVertical } from "@/components/nav";
import type { NavProps } from "@/components/nav/types";
import { GLOBAL_CONFIG } from "@/global-config";
import { useSettingActions, useSettings } from "@/store/settingStore";
import { ThemeLayout } from "@/types/enum";
import { Badge } from "@/ui/badge";
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
			<div className="relative flex items-center py-4 px-2 h-[var(--layout-header-height)]">
				<div className="flex items-center justify-center">
					<Logo />
					<div className="flex flex-col ml-2 whitespace-nowrap">
						<span className="inline-flex items-center gap-2 text-xl font-bold">
							<span aria-hidden className="text-red-500 text-2xl">
								★
							</span>
							{(GLOBAL_CONFIG.appName || "BI数智平台").replace("管理", "", 1)}
						</span>
						<Badge variant="destructive" className="mt-1 !text-[13.2px] leading-none bg-red-500">
							机密
						</Badge>
					</div>
				</div>
			</div>

			<ScrollArea className={cn("h-[calc(100vh-var(--layout-header-height))] px-2 bg-background")}>
				<NavVertical data={data} />
			</ScrollArea>
		</nav>
	);
}
