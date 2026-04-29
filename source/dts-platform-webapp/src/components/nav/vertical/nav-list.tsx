import { useState } from "react";
import { useLocation } from "react-router";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/ui/collapsible";
import type { NavListProps } from "../types";
import { isNavItemActive } from "../utils/is-active";
import { NavItem } from "./nav-item";

export function NavList({ data, depth = 1 }: NavListProps) {
	const location = useLocation();
	const hasChild = Boolean(data.children && data.children.length > 0);
	const isActive = isNavItemActive(location.pathname, data.path, hasChild);
	const [open, setOpen] = useState(isActive);

	const handleClick = () => {
		if (hasChild) {
			setOpen(!open);
		}
	};

	if (data.hidden) {
		return null;
	}

	const navItem = (
		<NavItem
			// data
			title={data.title}
			path={data.path}
			icon={data.icon}
			info={data.info}
			caption={data.caption}
			auth={data.auth}
			// state
			open={open}
			active={isActive}
			disabled={data.disabled}
			// options
			hasChild={hasChild}
			depth={depth}
			// event
			onClick={handleClick}
		/>
	);

	const renderLeafItem = () => navItem;

	const renderCollapsibleItem = () => (
		<Collapsible open={open} onOpenChange={setOpen} data-nav-type="list">
			<CollapsibleTrigger asChild>{navItem}</CollapsibleTrigger>
			<CollapsibleContent>
				<div className="ml-4 mt-1 flex flex-col gap-0.5">
					{data.children?.map((child) => (
						<NavList key={child.title} data={child} depth={depth + 1} />
					))}
				</div>
			</CollapsibleContent>
		</Collapsible>
	);

	return <li className="list-none">{hasChild ? renderCollapsibleItem() : renderLeafItem()}</li>;
}
