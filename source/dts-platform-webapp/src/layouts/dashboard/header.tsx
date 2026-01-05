import { Segmented, Space } from "antd";
import type { ReactNode } from "react";
import { ThemeColorPresets, ThemeMode } from "#/enum";
import { Icon } from "@/components/icon";

import { type SettingsType, useSettingActions, useSettings } from "@/store/settingStore";
import { Button } from "@/ui/button";
import { cn } from "@/utils";
import AccountDropdown from "../components/account-dropdown";
import BreadCrumb from "../components/bread-crumb";

import SearchBar from "../components/search-bar";

interface HeaderProps {
	leftSlot?: ReactNode;
}

export default function Header({ leftSlot }: HeaderProps) {
	const settings = useSettings();
	const { breadCrumb, themeMode } = settings;
	const { setSettings } = useSettingActions();

	const updateSettings = (partialSettings: Partial<SettingsType>) => {
		setSettings({
			...settings,
			...partialSettings,
		});
	};

	const mode = themeMode === ThemeMode.Dark ? "tech" : "bright";
	return (
		<header
			data-slot="slash-layout-header"
			className={cn(
				"sticky top-0 left-0 right-0 z-app-bar",
				"flex items-center justify-between px-2 grow-0 shrink-0",
				"bg-background/60 backdrop-blur-xl",
				"h-[var(--layout-header-height)] ",
			)}
		>
			<div className="flex items-center">
				{leftSlot}

				<div className="hidden md:block ml-4">{breadCrumb && <BreadCrumb />}</div>
			</div>

			<div className="flex items-center gap-2">
				<SearchBar />

				<Space size={6} className="inline-flex">
					<span className="hidden sm:inline text-xs text-muted-foreground">模式</span>
					<Segmented
						size="small"
						value={mode}
						onChange={(next) => {
							const nextMode = next as "bright" | "tech";
							if (nextMode === "bright") {
								updateSettings({
									themeMode: ThemeMode.Light,
									themeColorPresets: ThemeColorPresets.Default,
									darkSidebar: false,
								});
								return;
							}
							updateSettings({
								themeMode: ThemeMode.Dark,
								themeColorPresets: ThemeColorPresets.Cyan,
								darkSidebar: true,
							});
						}}
						options={[
							{
								label: (
									<span className="inline-flex items-center gap-1">
										<Icon icon="mdi:white-balance-sunny" size={16} />
										<span className="hidden sm:inline">明亮</span>
									</span>
								),
								value: "bright",
							},
							{
								label: (
									<span className="inline-flex items-center gap-1">
										<Icon icon="mdi:radar" size={16} />
										<span className="hidden sm:inline">科技</span>
									</span>
								),
								value: "tech",
							},
						]}
					/>
				</Space>

				<Button
					variant="ghost"
					size="icon"
					className="rounded-full hidden "
					onClick={() => window.open("https://github.com/d3george/slash-admin")}
				>
					<Icon icon="mdi:github" size={24} />
				</Button>
				<Button
					variant="ghost"
					size="icon"
					className="rounded-full hidden"
					onClick={() => window.open("https://discord.gg/fXemAXVNDa")}
				>
					<Icon icon="carbon:logo-discord" size={24} />
				</Button>

				<AccountDropdown />
			</div>
		</header>
	);
}
