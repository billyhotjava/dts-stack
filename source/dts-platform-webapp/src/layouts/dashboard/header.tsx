import type { ReactNode } from "react";
import { Icon } from "@/components/icon";

import { useSettings } from "@/store/settingStore";
import { Button } from "@/ui/button";
import { cn } from "@/utils";
import AccountDropdown from "../components/account-dropdown";
import BreadCrumb from "../components/bread-crumb";

import SearchBar from "../components/search-bar";

interface HeaderProps {
	leftSlot?: ReactNode;
}

export default function Header({ leftSlot }: HeaderProps) {
	const { breadCrumb } = useSettings();
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

			<div className="flex items-center gap-1 ">
				<SearchBar />

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
