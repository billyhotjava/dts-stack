import { lazy, Suspense } from "react";
import type { RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";

const TransformPage = lazy(() => import("@/pages/explore/etl/TransformPage"));
const TransformCreatePage = lazy(() => import("@/pages/explore/etl/TransformCreatePage"));
const TransformDetailPage = lazy(() => import("@/pages/explore/etl/TransformDetailPage"));
const TransformExecutionHistoryPage = lazy(() => import("@/pages/explore/etl/TransformExecutionHistoryPage"));

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
];
