import { lazy, Suspense } from "react";
import type { RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";

const DataSourcesPage = lazy(() => import("@/pages/foundation/DataSourcesPage"));
const DataSourceCreatePage = lazy(() => import("@/pages/foundation/DataSourceCreatePage"));
const DataSourceDetailPage = lazy(() => import("@/pages/foundation/DataSourceDetailPage"));
const TransformPage = lazy(() => import("@/pages/explore/etl/TransformPage"));
const TransformCreatePage = lazy(() => import("@/pages/explore/etl/TransformCreatePage"));
const TransformDetailPage = lazy(() => import("@/pages/explore/etl/TransformDetailPage"));
const TransformExecutionHistoryPage = lazy(() => import("@/pages/explore/etl/TransformExecutionHistoryPage"));

export const STATIC_DASHBOARD_ROUTES: RouteObject[] = [
	{
		path: "foundation/data-sources",
		element: (
			<Suspense fallback={<LineLoading />}>
				<DataSourcesPage />
			</Suspense>
		),
	},
	{
		path: "foundation/data-sources/new",
		element: (
			<Suspense fallback={<LineLoading />}>
				<DataSourceCreatePage />
			</Suspense>
		),
	},
	{
		path: "foundation/data-sources/:id",
		element: (
			<Suspense fallback={<LineLoading />}>
				<DataSourceDetailPage />
			</Suspense>
		),
	},
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
];
