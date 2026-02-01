import { cn } from "@/utils";
import type { NavProps } from "../types";
import { NavGroup } from "./nav-group";

export function NavVertical({ data, className, ...props }: NavProps) {
	return (
		<nav className={cn("flex w-full flex-col gap-1", className)} {...props}>
			{data.map((group, index) => (
				<div key={group.name || index}>
					{index > 0 && <hr className="border-t border-border/40 mx-3 my-2" />}
					<NavGroup name={group.name} items={group.items} />
				</div>
			))}
		</nav>
	);
}
