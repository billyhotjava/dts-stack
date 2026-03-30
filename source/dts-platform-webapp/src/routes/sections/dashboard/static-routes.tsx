import { lazy, Suspense } from "react";
import type { RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";

const TransformPage = lazy(() => import("@/pages/explore/etl/TransformPage"));
const TransformCreatePage = lazy(() => import("@/pages/explore/etl/TransformCreatePage"));
const TransformDetailPage = lazy(() => import("@/pages/explore/etl/TransformDetailPage"));
const TransformExecutionHistoryPage = lazy(() => import("@/pages/explore/etl/TransformExecutionHistoryPage"));
const AssetOwnershipPage = lazy(() => import("@/pages/governance/AssetOwnershipPage"));
const AssetGrantPage = lazy(() => import("@/pages/governance/AssetGrantPage"));
const MyGrantsPage = lazy(() => import("@/pages/governance/MyGrantsPage"));
const PermissionAuditPage = lazy(() => import("@/pages/governance/PermissionAuditPage"));

// ── Analytics pages with dynamic params (cannot use PATH_COMPONENT_OVERRIDES) ──
const DashboardDetailPage = lazy(() => import("@/analytics/pages/DashboardDetailPage"));
const DashboardEditorPage = lazy(() => import("@/analytics/pages/DashboardEditorPage"));
const CardDetailPage = lazy(() => import("@/analytics/pages/CardDetailPage"));
const CardEditorPage = lazy(() => import("@/analytics/pages/CardEditorPage"));
const DatabaseDetailPage = lazy(() => import("@/analytics/pages/DatabaseDetailPage"));
const DatabaseEditPage = lazy(() => import("@/analytics/pages/DatabaseEditPage"));
const TableDetailPage = lazy(() => import("@/analytics/pages/TableDetailPage"));
const FieldDetailPage = lazy(() => import("@/analytics/pages/FieldDetailPage"));
const CollectionItemsPage = lazy(() => import("@/analytics/pages/CollectionItemsPage"));
const GpmcDrillPage = lazy(() => import("@/analytics/pages/gpmc/GpmcDrillPage"));
const GpmcPage = lazy(() => import("@/analytics/pages/gpmc/GpmcPage"));

const S = ({ children }: { children: React.ReactNode }) => (
	<Suspense fallback={<LineLoading />}>{children}</Suspense>
);

export const STATIC_DASHBOARD_ROUTES: RouteObject[] = [
	{
		path: "explore/etl/transform",
		element: (
			<Suspense fallback={<LineLoading />}>
				<TransformPage />
			</Suspense>
		),
	},
	{
		path: "explore/etl/transform/new",
		element: (
			<Suspense fallback={<LineLoading />}>
				<TransformCreatePage />
			</Suspense>
		),
	},
	{
		path: "explore/etl/transform/:id",
		element: (
			<Suspense fallback={<LineLoading />}>
				<TransformDetailPage />
			</Suspense>
		),
	},
	{
		path: "explore/etl/transform/:id/edit",
		element: (
			<Suspense fallback={<LineLoading />}>
				<TransformCreatePage />
			</Suspense>
		),
	},
	{
		path: "explore/etl/transform/:id/executions",
		element: (
			<Suspense fallback={<LineLoading />}>
				<TransformExecutionHistoryPage />
			</Suspense>
		),
	},
	{
		path: "governance/asset-ownership",
		element: (
			<Suspense fallback={<LineLoading />}>
				<AssetOwnershipPage />
			</Suspense>
		),
	},
	{
		path: "governance/asset-grants",
		element: (
			<Suspense fallback={<LineLoading />}>
				<AssetGrantPage />
			</Suspense>
		),
	},
	{
		path: "my/asset-grants",
		element: (
			<Suspense fallback={<LineLoading />}>
				<MyGrantsPage />
			</Suspense>
		),
	},
	{
		path: "governance/permission-audit",
		element: (
			<Suspense fallback={<LineLoading />}>
				<PermissionAuditPage />
			</Suspense>
		),
	},
	// ── Analytics dynamic-param routes (inside DashboardLayout) ──
	{ path: "analytics/dashboards/:id", element: <S><DashboardDetailPage /></S> },
	{ path: "analytics/dashboards/:id/edit", element: <S><DashboardEditorPage /></S> },
	{ path: "analytics/questions/:id", element: <S><CardDetailPage /></S> },
	{ path: "analytics/questions/:id/edit", element: <S><CardEditorPage /></S> },
	{ path: "analytics/data/:dbId", element: <S><DatabaseDetailPage /></S> },
	{ path: "analytics/data/:dbId/edit", element: <S><DatabaseEditPage /></S> },
	{ path: "analytics/data/:dbId/tables/:tableId", element: <S><TableDetailPage /></S> },
	{ path: "analytics/data/:dbId/tables/:tableId/fields/:fieldId", element: <S><FieldDetailPage /></S> },
	{ path: "analytics/collections/:id", element: <S><CollectionItemsPage /></S> },
	{ path: "analytics/gpmc/drill/:domain", element: <S><GpmcDrillPage /></S> },
	{ path: "analytics/gpmc/:screenId", element: <S><GpmcPage /></S> },
];
