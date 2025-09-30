import { Icon } from "@/components/icon";
import type { NavProps } from "@/components/nav";
import { PORTAL_NAV_SECTIONS } from "@/constants/portal-navigation";

export const frontendNavData: NavProps["data"] = PORTAL_NAV_SECTIONS.map((section) => ({
	items: [
		{
			title: section.titleKey,
			path: `/${section.path}`,
			icon: <Icon icon={section.icon} size="24" />,
			children: section.children.map((child) => ({
				title: child.titleKey,
				path: `/${section.path}/${child.path}`,
			})),
		},
	],
}));
