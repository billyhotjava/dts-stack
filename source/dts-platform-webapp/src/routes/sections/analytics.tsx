import { lazy, Suspense } from "react";
import type { RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";
import LoginAuthGuard from "@/routes/components/login-auth-guard";

// ── Full-screen pages (no sidebar, outside DashboardLayout) ──
const ScreenDesignerPage = lazy(() => import("@/analytics/pages/screens/ScreenDesignerPage"));
const ScreenDesignerV2Page = lazy(() => import("@/analytics/pages/screens/v2/ScreenDesignerV2Page"));
const ScreenPreviewPage = lazy(() => import("@/analytics/pages/screens/ScreenPreviewPage"));
const ScreenExportPage = lazy(() => import("@/analytics/pages/screens/ScreenExportPage"));

// ── Public pages (no auth required) ──
const PublicScreenPage = lazy(() => import("@/analytics/pages/screens/PublicScreenPage"));
const PublicCardPage = lazy(() => import("@/analytics/pages/PublicCardPage"));
const PublicDashboardPage = lazy(() => import("@/analytics/pages/PublicDashboardPage"));

// ── Sprint-12 F1/T01 dev smoke (react-grid-layout + Chrome 95 兼容验证) ──
// 仅 dev 模式暴露，import.meta.env.DEV 为 false 时返回 404 占位。
const GridLayoutSmoke = lazy(() => import("@/analytics/pages/screens/v2/__dev__/GridLayoutSmoke"));

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
	{ path: "bi/public/screen/:uuid", element: <S><PublicScreenPage /></S> },
	{ path: "bi/public/card/:uuid", element: <S><PublicCardPage /></S> },
	{ path: "bi/public/dashboard/:uuid", element: <S><PublicDashboardPage /></S> },
	// Full-screen authenticated routes (screen designer — no sidebar)
	{
		path: "bi/screens/new",
		element: <LoginAuthGuard><S><ScreenDesignerPage /></S></LoginAuthGuard>,
	},
	{
		path: "bi/screens/:id/edit",
		element: <LoginAuthGuard><S><ScreenDesignerPage /></S></LoginAuthGuard>,
	},
	// Sprint-12 F3 — v2 自适应大屏编辑器（与 v1 并存，handleCreateV2 跳转到此）
	{
		path: "bi/screens/:id/designer-v2",
		element: <LoginAuthGuard><S><ScreenDesignerV2Page /></S></LoginAuthGuard>,
	},
	{
		path: "bi/screens/:id/preview",
		element: <LoginAuthGuard><S><ScreenPreviewPage /></S></LoginAuthGuard>,
	},
	{
		path: "bi/screens/:id/export",
		element: <LoginAuthGuard><S><ScreenExportPage /></S></LoginAuthGuard>,
	},
	// Dev-only smoke route for Sprint-12 F1/T01 (不挂 LoginAuthGuard 方便验证)
	...(import.meta.env.DEV
		? [
				{
					path: "bi/__dev__/grid-smoke",
					element: <S><GridLayoutSmoke /></S>,
				},
			]
		: []),
];
