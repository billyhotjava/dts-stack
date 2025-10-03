import type { RouteObject } from "react-router";
import { Navigate } from "react-router";
import type { ReactElement } from "react";
import { PORTAL_NAV_SECTIONS } from "@/constants/portal-navigation";
import DatasetsPage from "@/pages/catalog/DatasetsPage";
import AccessPolicyPage from "@/pages/catalog/AccessPolicyPage";
import SecureViewsPage from "@/pages/catalog/SecureViewsPage";
import DatasetDetailPage from "@/pages/catalog/DatasetDetailPage";
import QueryWorkbenchPage from "@/pages/explore/QueryWorkbenchPage";
import SavedQueriesPage from "@/pages/explore/SavedQueriesPage";
import DataStandardsPage from "@/pages/modeling/DataStandardsPage";
import QualityRulesPage from "@/pages/governance/QualityRulesPage";
import CompliancePage from "@/pages/governance/CompliancePage";
import DataProductsPage from "@/pages/services/DataProductsPage";
import TokensPage from "@/pages/services/TokensPage";
import ApiServicesPage from "@/pages/services/ApiServicesPage";
import ApiServiceDetailPage from "@/pages/services/ApiServiceDetailPage";
import ClassificationMappingPage from "@/pages/iam/ClassificationMappingPage";
import AuthorizationPage from "@/pages/iam/AuthorizationPage";
import SimulationPage from "@/pages/iam/SimulationPage";
import RequestsPage from "@/pages/iam/RequestsPage";
import CockpitPage from "@/pages/visualization/CockpitPage";
import DashboardsPage from "@/pages/visualization/DashboardsPage";
import ProjectsSummaryPage from "@/pages/visualization/ProjectsSummaryPage";
import FinanceSummaryPage from "@/pages/visualization/FinanceSummaryPage";
import SupplyChainSummaryPage from "@/pages/visualization/SupplyChainSummaryPage";
import HRSummaryPage from "@/pages/visualization/HRSummaryPage";
import FeaturePlaceholder from "@/pages/common/FeaturePlaceholder";
import DataSourcesPage from "@/pages/foundation/DataSourcesPage";
import DataStoragePage from "@/pages/foundation/DataStoragePage";
import TaskSchedulingPage from "@/pages/foundation/TaskSchedulingPage";

export function getFrontendDashboardRoutes(): RouteObject[] {
	const sectionRoutes = PORTAL_NAV_SECTIONS.map((section) => {
		const childRoutes = section.children.map<RouteObject>((child) => {
			const FEATURE_COMPONENTS: Record<string, Record<string, () => ReactElement>> = {
				catalog: {
					assets: () => <DatasetsPage />,
					accessPolicy: () => <AccessPolicyPage />,
					secureViews: () => <SecureViewsPage />,
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
				services: {
					api: () => <ApiServicesPage />,
					products: () => <DataProductsPage />,
					tokens: () => <TokensPage />,
				},
				visualization: {
					cockpit: () => <CockpitPage />,
					dashboards: () => <DashboardsPage />,
					projects: () => <ProjectsSummaryPage />,
					finance: () => <FinanceSummaryPage />,
					supplyChain: () => <SupplyChainSummaryPage />,
					hr: () => <HRSummaryPage />,
				},
				iam: {
					classification: () => <ClassificationMappingPage />,
					authorization: () => <AuthorizationPage />,
					simulation: () => <SimulationPage />,
					requests: () => <RequestsPage />,
				},
				foundation: {
					dataSources: () => <DataSourcesPage />,
					dataStorage: () => <DataStoragePage />,
					taskScheduling: () => <TaskSchedulingPage />,
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
		if (section.key === "services") {
			extraChildren.push({ path: "apis/:id", element: <ApiServiceDetailPage /> });
		}
		if (section.key === "catalog") {
			extraChildren.push({ path: "datasets/:id", element: <DatasetDetailPage /> });
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

	return sectionRoutes;
}
