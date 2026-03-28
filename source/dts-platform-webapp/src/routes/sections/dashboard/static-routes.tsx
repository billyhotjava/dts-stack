import { lazy, Suspense } from "react";
import type { RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";

const TransformPage = lazy(() => import("@/pages/explore/etl/TransformPage"));
const TransformCreatePage = lazy(() => import("@/pages/explore/etl/TransformCreatePage"));
const TransformDetailPage = lazy(() => import("@/pages/explore/etl/TransformDetailPage"));
const TransformExecutionHistoryPage = lazy(() => import("@/pages/explore/etl/TransformExecutionHistoryPage"));
const ProjectCockpitImportsPage = lazy(() => import("@/pages/foundation/ProjectCockpitImportsPage"));
const TopicBindingCenterPage = lazy(() => import("@/pages/foundation/TopicBindingCenterPage"));
const AssetOwnershipPage = lazy(() => import("@/pages/governance/AssetOwnershipPage"));
const AssetGrantPage = lazy(() => import("@/pages/governance/AssetGrantPage"));
const MyGrantsPage = lazy(() => import("@/pages/governance/MyGrantsPage"));
const PermissionAuditPage = lazy(() => import("@/pages/governance/PermissionAuditPage"));

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
		path: "foundation/project-cockpit-imports",
		element: (
			<Suspense fallback={<LineLoading />}>
				<ProjectCockpitImportsPage />
			</Suspense>
		),
	},
	{
		path: "foundation/topic-bindings",
		element: (
			<Suspense fallback={<LineLoading />}>
				<TopicBindingCenterPage />
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
];
