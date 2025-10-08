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
		icon: "solar:book-bold-duotone",
		titleKey: "sys.nav.portal.catalog",
		children: [
			{ key: "assets", path: "assets", titleKey: "sys.nav.portal.catalogAssetsDirectory" },
			{ key: "accessPolicy", path: "access-policy", titleKey: "sys.nav.portal.catalogAccessPolicy" },
			{ key: "secureViews", path: "secure-views", titleKey: "sys.nav.portal.catalogSecureViews" },
		],
	},
	{
		key: "modeling",
		path: "modeling",
		icon: "solar:documents-bold-duotone",
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
		icon: "solar:shield-check-bold-duotone",
		titleKey: "sys.nav.portal.governance",
		children: [
			{ key: "rules", path: "rules", titleKey: "sys.nav.portal.governanceRules" },
			{ key: "compliance", path: "compliance", titleKey: "sys.nav.portal.governanceCompliance" },
		],
	},
	{
		key: "explore",
		path: "explore",
		icon: "solar:compass-bold-duotone",
		titleKey: "sys.nav.portal.explore",
		children: [
			{ key: "workbench", path: "workbench", titleKey: "sys.nav.portal.exploreWorkbench" },
			{ key: "savedQueries", path: "saved-queries", titleKey: "sys.nav.portal.exploreSavedQueries" },
		],
	},
	{
		key: "visualization",
		path: "visualization",
		icon: "solar:pie-chart-bold-duotone",
		titleKey: "sys.nav.portal.visualization",
		children: [
			{ key: "dashboards", path: "dashboards", titleKey: "sys.nav.portal.visualizationDashboards" },
			{ key: "cockpit", path: "cockpit", titleKey: "sys.nav.portal.visualizationCockpit" },
			{ key: "projects", path: "projects", titleKey: "sys.nav.portal.visualizationProjects" },
			{ key: "finance", path: "finance", titleKey: "sys.nav.portal.visualizationFinance" },
			{ key: "supplyChain", path: "supply-chain", titleKey: "sys.nav.portal.visualizationSupplyChain" },
			{ key: "hr", path: "hr", titleKey: "sys.nav.portal.visualizationHR" },
		],
	},
	{
		key: "services",
		path: "services",
		icon: "solar:server-bold-duotone",
		titleKey: "sys.nav.portal.services",
		children: [
			{ key: "api", path: "api", titleKey: "sys.nav.portal.servicesApi" },
			{ key: "products", path: "products", titleKey: "sys.nav.portal.servicesProducts" },
			{ key: "tokens", path: "tokens", titleKey: "sys.nav.portal.servicesTokens" },
		],
	},
	{
		key: "iam",
		path: "iam",
		icon: "solar:key-minimalistic-bold-duotone",
		titleKey: "sys.nav.portal.iam",
		children: [
			{
				key: "classification",
				path: "classification",
				titleKey: "sys.nav.portal.iamClassification",
			},
			{
				key: "authorization",
				path: "authorization",
				titleKey: "sys.nav.portal.iamAuthorization",
			},
			{
				key: "simulation",
				path: "simulation",
				titleKey: "sys.nav.portal.iamSimulation",
			},
			{
				key: "requests",
				path: "requests",
				titleKey: "sys.nav.portal.iamRequests",
			},
		],
	},
	// 基础数据维护
	{
		key: "foundation",
		path: "foundation",
		icon: "solar:database-bold-duotone",
		titleKey: "sys.nav.portal.foundation",
		children: [
			{ key: "dataSources", path: "data-sources", titleKey: "sys.nav.portal.foundationDataSources" },
			{ key: "dataStorage", path: "data-storage", titleKey: "sys.nav.portal.foundationDataStorage" },
			{ key: "taskScheduling", path: "task-scheduling", titleKey: "sys.nav.portal.foundationTaskScheduling" },
		],
	},
];

// Use a unified welcome route for all users
export const DEFAULT_PORTAL_ROUTE = "/dashboard/workbench";
