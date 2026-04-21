import { lazy, Suspense } from "react";
import { Outlet, type RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";
import SimpleLayout from "@/layouts/simple";
import LoginAuthGuard from "@/routes/components/login-auth-guard";

const Page403 = lazy(() => import("@/pages/sys/error/Page403"));
const Page404 = lazy(() => import("@/pages/sys/error/Page404"));
const Page500 = lazy(() => import("@/pages/sys/error/Page500"));
const ExternalRedirectPage = lazy(() => import("@/pages/sys/ExternalRedirectPage"));

export const mainRoutes: RouteObject[] = [
	{
		path: "/",
		element: (
			<SimpleLayout>
				<Suspense fallback={<LineLoading />}>
					<Outlet />
				</Suspense>
			</SimpleLayout>
		),
		children: [
			{ path: "500", element: <Page500 /> },
			{ path: "404", element: <Page404 /> },
			{ path: "403", element: <Page403 /> },
			{
				path: "external-redirect",
				element: (
					<LoginAuthGuard>
						<ExternalRedirectPage />
					</LoginAuthGuard>
				),
			},
		],
	},
];
