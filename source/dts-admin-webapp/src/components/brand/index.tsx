import { NavLink } from "react-router";
import { Icon } from "@/components/icon";
import { GLOBAL_CONFIG } from "@/global-config";
import { useBilingualText } from "@/hooks/useBilingualText";

export default function Brand() {
	const bilingual = useBilingualText();
	const classified = bilingual("sys.brand.classified") || "机密级";
	const appName = (GLOBAL_CONFIG.appName || "数智管理平台")
		.replace("系统端", "")
		.replace(/[（(]机密级[)）]/g, "")
		.trim();
	return (
		<NavLink to="/" className="inline-flex items-start gap-2 select-none">
			<Icon icon="mdi:star" size={22} className="text-red-500" color="#ef4444" />
			<span className="flex flex-col leading-tight">
				<span className="text-base font-semibold text-foreground">{appName}</span>
				<span className="text-red-600 font-bold">{classified}</span>
			</span>
		</NavLink>
	);
}
