import Logo from "@/components/logo";

import SettingButton from "./setting-button";
import ScopeSwitcher from "./scope-switcher";

export default function HeaderSimple() {
	return (
		<header className="flex h-16 w-full items-center justify-between px-6">
			<Logo size={30} />
			<div className="flex items-center gap-4">
				<ScopeSwitcher />
				<SettingButton />
			</div>
		</header>
	);
}
