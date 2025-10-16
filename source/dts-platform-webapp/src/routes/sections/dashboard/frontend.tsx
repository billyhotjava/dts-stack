import type { RouteObject } from "react-router";
import { Navigate } from "react-router";
import type { ReactElement } from "react";
import Workbench from "@/pages/dashboard/workbench";
import { PORTAL_NAV_SECTIONS } from "@/constants/portal-navigation";
import DatasetsPage from "@/pages/catalog/DatasetsPage";
import DatasetDetailPage from "@/pages/catalog/DatasetDetailPage";
import QueryWorkbenchPage from "@/pages/explore/QueryWorkbenchPage";
import SavedQueriesPage from "@/pages/explore/SavedQueriesPage";
import DataStandardsPage from "@/pages/modeling/DataStandardsPage";
import DataStandardDetailPage from "@/pages/modeling/DataStandardDetailPage";
import QualityRulesPage from "@/pages/governance/QualityRulesPage";
import CompliancePage from "@/pages/governance/CompliancePage";
import FeaturePlaceholder from "@/pages/common/FeaturePlaceholder";
import ProfilePage from "@/pages/account/ProfilePage";

export function getFrontendDashboardRoutes(): RouteObject[] {
    const sectionRoutes = PORTAL_NAV_SECTIONS.map((section) => {
		const childRoutes = section.children.map<RouteObject>((child) => {
			const FEATURE_COMPONENTS: Record<string, Record<string, () => ReactElement>> = {
				catalog: {
					assets: () => <DatasetsPage />,
				},
				modeling: {
					standards: () => <DataStandardsPage />,
				},
				governance: {
					rules: () => <QualityRulesPage />,
					compliance: () => <CompliancePage />,
				},
				explore: {
					workbench: () => <QueryWorkbenchPage />,
					savedQueries: () => <SavedQueriesPage />,
				},
			};

			const renderFeature = FEATURE_COMPONENTS[section.key]?.[child.key];
			let element: ReactElement;
			if (renderFeature) {
				element = renderFeature();
			} else {
				element = <FeaturePlaceholder titleKey={child.titleKey} descriptionKey={child.descriptionKey} />;
			}
			return {
				path: child.path,
				element,
			};
		});

		if (!childRoutes.length) {
			return { path: section.path };
		}

		// Extra non-menu routes
		const extraChildren: RouteObject[] = [];
		if (section.key === "catalog") {
			extraChildren.push({ path: "datasets/:id", element: <DatasetDetailPage /> });
		}
		if (section.key === "modeling") {
			extraChildren.push({ path: "standards/:id", element: <DataStandardDetailPage /> });
		}

		return {
			path: section.path,
			children: [
				{ index: true, element: <Navigate to={section.children[0].path} replace /> },
				...childRoutes,
				...extraChildren,
			],
		};
	});

    // Add a static dashboard/workbench welcome route available regardless of menus
    sectionRoutes.push({
        path: "dashboard",
        children: [
            { index: true, element: <Navigate to="workbench" replace /> },
            { path: "workbench", element: <Workbench /> },
        ],
    });

    sectionRoutes.push({
        path: "settings",
        children: [
            { index: true, element: <Navigate to="profile" replace /> },
            { path: "profile", element: <ProfilePage /> },
        ],
    });

    return sectionRoutes;
}
