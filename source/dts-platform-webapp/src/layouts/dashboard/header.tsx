import type { ReactNode } from "react";

import HelpCenter from "@/features/help-center/HelpCenter";
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
				"sticky top-0 left-0 right-0 z-app-bar grow-0 shrink-0",
			)}
		>
			<div
				className={cn(
					"flex min-h-[var(--layout-header-height)] items-center justify-between gap-4 border-b border-border/70 bg-card/95 px-4 py-2 backdrop-blur supports-[backdrop-filter]:bg-card/80 sm:px-6 md:px-8",
				)}
			>
				<div className="flex min-w-0 items-center gap-3">
					{leftSlot}
					<div className="hidden min-w-0 md:flex items-center rounded-full bg-background px-3 py-1.5 text-text-secondary">
						{breadCrumb && <BreadCrumb />}
					</div>
				</div>

				<div className="flex items-center gap-2">
					<SearchBar />
					<HelpCenter />
					<AccountDropdown />
				</div>
			</div>
		</header>
	);
}
