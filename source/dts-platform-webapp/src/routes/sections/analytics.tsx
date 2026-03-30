import { lazy, Suspense } from "react";
import type { RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";
import LoginAuthGuard from "@/routes/components/login-auth-guard";

// ── Full-screen pages (no sidebar, outside DashboardLayout) ──
const ScreenDesignerPage = lazy(() => import("@/analytics/pages/screens/ScreenDesignerPage"));
const ScreenPreviewPage = lazy(() => import("@/analytics/pages/screens/ScreenPreviewPage"));
const ScreenExportPage = lazy(() => import("@/analytics/pages/screens/ScreenExportPage"));

// ── Public pages (no auth required) ──
const PublicScreenPage = lazy(() => import("@/analytics/pages/screens/PublicScreenPage"));
const PublicCardPage = lazy(() => import("@/analytics/pages/PublicCardPage"));
const PublicDashboardPage = lazy(() => import("@/analytics/pages/PublicDashboardPage"));

const S = ({ children }: { children: React.ReactNode }) => (
	<Suspense fallback={<LineLoading />}>{children}</Suspense>
);

/**
 * Analytics routes that live OUTSIDE DashboardLayout:
 * - Public share links (no auth)
 * - Full-screen editors (screen designer, preview, export)
 *
 * Regular analytics pages (home, dashboards, questions, screens list, etc.)
 * are statically registered in static-routes.tsx inside DashboardLayout.
 */
export const analyticsStandaloneRoutes: RouteObject[] = [
	// Public routes — no auth required (shareable links)
	{ path: "analytics/public/screen/:uuid", element: <S><PublicScreenPage /></S> },
	{ path: "analytics/public/card/:uuid", element: <S><PublicCardPage /></S> },
	{ path: "analytics/public/dashboard/:uuid", element: <S><PublicDashboardPage /></S> },
	// Full-screen authenticated routes (screen designer — no sidebar)
	{
		path: "analytics/screens/new",
		element: <LoginAuthGuard><S><ScreenDesignerPage /></S></LoginAuthGuard>,
	},
	{
		path: "analytics/screens/:id/edit",
		element: <LoginAuthGuard><S><ScreenDesignerPage /></S></LoginAuthGuard>,
	},
	{
		path: "analytics/screens/:id/preview",
		element: <LoginAuthGuard><S><ScreenPreviewPage /></S></LoginAuthGuard>,
	},
	{
		path: "analytics/screens/:id/export",
		element: <LoginAuthGuard><S><ScreenExportPage /></S></LoginAuthGuard>,
	},
];
