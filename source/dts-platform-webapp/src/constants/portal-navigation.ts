export interface PortalNavEntryDefinition {
	key: string;
	/** Route path segment relative to its parent section */
	path: string;
	/** i18n key for displaying the menu label */
	titleKey: string;
	/** Optional i18n key describing the entry */
	descriptionKey?: string;
}

export interface PortalNavSectionDefinition {
	key: string;
	/** Route path segment for the top-level section */
	path: string;
	/** Icon name understood by the <Icon /> component */
	icon: string;
	/** i18n key for the section label */
	titleKey: string;
	/** Nested menu entries rendered in the sidebar */
	children: PortalNavEntryDefinition[];
}

export const PORTAL_NAV_SECTIONS: PortalNavSectionDefinition[] = [
	{
		key: "catalog",
		path: "catalog",
    icon: "local:ic-management",
		titleKey: "sys.nav.portal.catalog",
		children: [{ key: "assets", path: "assets", titleKey: "sys.nav.portal.catalogAssetsDirectory" }],
	},
	{
		key: "modeling",
		path: "modeling",
    icon: "local:ic-dashboard",
		titleKey: "sys.nav.portal.modeling",
		children: [
			{
				key: "standards",
				path: "standards",
				titleKey: "sys.nav.portal.modelingStandards",
			},
		],
	},
	{
		key: "governance",
		path: "governance",
    icon: "local:ic-setting",
		titleKey: "sys.nav.portal.governance",
		children: [
			{ key: "rules", path: "rules", titleKey: "sys.nav.portal.governanceRules" },
			{ key: "compliance", path: "compliance", titleKey: "sys.nav.portal.governanceCompliance" },
		],
	},
	{
		key: "explore",
		path: "explore",
    icon: "local:ic-workbench",
		titleKey: "sys.nav.portal.explore",
		children: [
			{ key: "workbench", path: "workbench", titleKey: "sys.nav.portal.exploreWorkbench" },
			{ key: "savedQueries", path: "saved-queries", titleKey: "sys.nav.portal.exploreSavedQueries" },
		],
	},
];

// Use a unified welcome route for all users
export const DEFAULT_PORTAL_ROUTE = "/dashboard/workbench";
