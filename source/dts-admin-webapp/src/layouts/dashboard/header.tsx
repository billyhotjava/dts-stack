import type { ReactNode } from "react";

import { useSettings } from "@/store/settingStore";
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
				"flex items-center justify-between px-4 grow-0 shrink-0",
				"bg-background border-b border-border/60 shadow-sm",
				"h-[var(--layout-header-height)] ",
			)}
		>
			<div className="flex items-center gap-3">
				{leftSlot}
				<div className="hidden md:flex items-center">
					{breadCrumb && <BreadCrumb />}
				</div>
			</div>

			<div className="flex items-center gap-1 ">
				<SearchBar />
				<AccountDropdown />
			</div>
		</header>
	);
}
