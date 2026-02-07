import { NavLink } from "react-router";
import { Icon } from "@/components/icon";
import { GLOBAL_CONFIG } from "@/global-config";
// import { useBilingualText } from "@/hooks/useBilingualText";

export default function Brand() {
	// const bilingual = useBilingualText();
	// const classified = bilingual("sys.brand.classified") || "机密";
	const appName = "BI数智平台";

	return (
		<NavLink to="/" className="inline-flex items-center gap-3 select-none px-2">
			<Icon icon="local:ic-logo-sci-fi-data" size={32} className="text-primary" color="var(--colors-palette-primary-default)" />
			<div className="flex flex-col leading-none">
				<span className="text-lg font-bold tracking-tight text-foreground">{appName}</span>
				<span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider mt-1 opacity-80">机密 (Confidential)</span>
			</div>
		</NavLink>
	);
}
