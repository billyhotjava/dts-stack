import { useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router";
import { ErrorBoundary } from "../components/ErrorBoundary";
import { Layout, Menu, Button, Dropdown as AntDropdown, Space, Tooltip } from "antd";
import type { MenuProps } from "antd";
import {
	HomeOutlined,
	LineChartOutlined,
	QuestionCircleOutlined,
	DashboardOutlined,
	FolderOutlined,
	DatabaseOutlined,
	AppstoreOutlined,
	BarChartOutlined,
	DesktopOutlined,
	ProjectOutlined,
	SearchOutlined,
	UserOutlined,
	LogoutOutlined,
	MenuFoldOutlined,
	MenuUnfoldOutlined,
	DeleteOutlined,
} from "@ant-design/icons";
import { getEffectiveLocale, t } from "../i18n";
import "./layout.css";

const { Header, Sider, Content } = Layout;

// ── Route-to-nav metadata for breadcrumb ──

type RouteNavMeta = {
	path: string;
	section: string;
	nav?: string;
};

const ROUTE_NAV_MAP: RouteNavMeta[] = [
	{ path: "/collections/root", section: "nav.section.core", nav: "nav.myCollection" },
	{ path: "/", section: "nav.section.core", nav: "nav.home" },
	{ path: "/analyze", section: "nav.section.core", nav: "nav.analyze" },
	{ path: "/questions", section: "nav.section.core", nav: "nav.questions" },
	{ path: "/dashboards", section: "nav.section.core", nav: "nav.dashboards" },
	{ path: "/collections", section: "nav.section.core", nav: "nav.collections" },
	{ path: "/data", section: "nav.section.data", nav: "nav.data" },
	{ path: "/models", section: "nav.section.data", nav: "nav.models" },
	{ path: "/metrics", section: "nav.section.data", nav: "nav.metrics" },
	{ path: "/trash", section: "nav.section.data", nav: "nav.trash" },
	{ path: "/screens", section: "nav.section.tools", nav: "nav.screens" },
	{ path: "/project-cockpit", section: "nav.section.tools", nav: "项目看板" },
	{ path: "/search", section: "nav.section.tools", nav: "nav.search" },
];

function matchRouteMeta(path: string): RouteNavMeta | null {
	for (const route of ROUTE_NAV_MAP) {
		if (route.path === "/") {
			if (path === "/") return route;
			continue;
		}
		if (path === route.path || path.startsWith(route.path + "/")) {
			return route;
		}
	}
	return null;
}

// ── Breadcrumb icons ──

const ChevronRightIcon = () => (
	<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="m9 18 6-6-6-6" />
	</svg>
);

// ── Read user info from platform's shared localStorage store ──

function getUserInfo(): { username: string; fullName: string; email: string } {
	try {
		const raw = localStorage.getItem("userStore");
		if (!raw) return { username: "", fullName: "", email: "" };
		const store = JSON.parse(raw);
		const state = store?.state;
		const userInfo = state?.userInfo;
		if (!userInfo || typeof userInfo !== "object") return { username: "", fullName: "", email: "" };
		return {
			username: String(userInfo.username ?? ""),
			fullName: String(userInfo.fullName ?? ""),
			email: String(userInfo.email ?? ""),
		};
	} catch {
		return { username: "", fullName: "", email: "" };
	}
}

// ── Header sub-components ──

function HeaderBreadcrumb() {
	const locale = getEffectiveLocale();
	const location = useLocation();
	const matched = matchRouteMeta(location.pathname);
	const sectionLabel = matched ? t(locale, matched.section) : null;
	const navLabel = matched?.nav ? t(locale, matched.nav) : null;

	return (
		<nav className="header-breadcrumb">
			{sectionLabel && (
				<span className={navLabel ? "header-breadcrumb__link" : "header-breadcrumb__current"}>{sectionLabel}</span>
			)}
			{navLabel && (
				<>
					<span className="header-breadcrumb__separator"><ChevronRightIcon /></span>
					<span className="header-breadcrumb__current">{navLabel}</span>
				</>
			)}
		</nav>
	);
}

function HeaderIntro() {
	return (
		<div className="main-header__intro">
			<HeaderBreadcrumb />
		</div>
	);
}

// ── Determine which menu key is active based on current path ──

const MENU_PATHS = [
	"/collections/root",
	"/analyze",
	"/questions",
	"/dashboards",
	"/collections",
	"/data",
	"/models",
	"/metrics",
	"/trash",
	"/screens",
	"/project-cockpit",
	"/search",
];

function resolveSelectedKey(pathname: string): string {
	// Exact match for home
	if (pathname === "/") return "/";
	// Longest prefix match
	for (const p of MENU_PATHS) {
		if (pathname === p || pathname.startsWith(p + "/")) return p;
	}
	return pathname;
}

// ── Main Layout ──

export function AppLayout() {
	const locale = getEffectiveLocale();
	const navigate = useNavigate();
	const location = useLocation();
	const userInfo = getUserInfo();
	const displayName = userInfo.fullName || userInfo.username || "用户";
	const [collapsed, setCollapsed] = useState(false);

	const handleLogout = () => {
		try {
			const raw = localStorage.getItem("userStore");
			if (raw) {
				const store = JSON.parse(raw);
				if (store?.state?.userToken) {
					store.state.userToken = {};
				}
				localStorage.setItem("userStore", JSON.stringify(store));
			}
		} catch {
			// ignore
		}
		window.location.href = "/";
	};

	const menuItems: MenuProps["items"] = [
		{
			key: "core-group",
			type: "group" as const,
			label: t(locale, "nav.section.core"),
			children: [
				{ key: "/", icon: <HomeOutlined />, label: t(locale, "nav.home") },
				{ key: "/analyze", icon: <LineChartOutlined />, label: t(locale, "nav.analyze") },
				{ key: "/questions", icon: <QuestionCircleOutlined />, label: t(locale, "nav.questions") },
				{ key: "/dashboards", icon: <DashboardOutlined />, label: t(locale, "nav.dashboards") },
				{ key: "/collections", icon: <FolderOutlined />, label: t(locale, "nav.collections") },
				{ key: "/collections/root", icon: <FolderOutlined />, label: t(locale, "nav.myCollection") },
			],
		},
		{ type: "divider" },
		{
			key: "data-group",
			type: "group" as const,
			label: t(locale, "nav.section.data"),
			children: [
				{ key: "/data", icon: <DatabaseOutlined />, label: t(locale, "nav.data") },
				{ key: "/models", icon: <AppstoreOutlined />, label: t(locale, "nav.models") },
				{ key: "/metrics", icon: <BarChartOutlined />, label: t(locale, "nav.metrics") },
				{ key: "/trash", icon: <DeleteOutlined />, label: t(locale, "nav.trash") },
			],
		},
		{ type: "divider" },
		{
			key: "tools-group",
			type: "group" as const,
			label: t(locale, "nav.section.tools"),
			children: [
				{ key: "/screens", icon: <DesktopOutlined />, label: t(locale, "nav.screens") },
				{ key: "/project-cockpit", icon: <ProjectOutlined />, label: "项目看板" },
				{ key: "/search", icon: <SearchOutlined />, label: t(locale, "nav.search") },
			],
		},
	];

	const selectedKey = resolveSelectedKey(location.pathname);

	return (
		<Layout style={{ minHeight: "100vh" }}>
			<Sider
				collapsible
				collapsed={collapsed}
				onCollapse={setCollapsed}
				trigger={null}
				width={220}
				collapsedWidth={64}
				theme="light"
				style={{
					overflow: "auto",
					height: "100vh",
					position: "sticky",
					top: 0,
					left: 0,
					borderRight: "1px solid #f0f0f0",
				}}
			>
				{/* Logo area */}
				<div style={{ height: 48, display: "flex", alignItems: "center", justifyContent: "center", padding: collapsed ? 0 : "0 16px" }}>
					<Link to="/" className="sidebar-logo-link analytics-logo" style={{ justifyContent: collapsed ? "center" : undefined }}>
						<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none" width="32" height="32" className="analytics-logo__mark">
							<circle cx="32" cy="32" r="29" stroke="currentColor" strokeOpacity="0.35" strokeWidth="2" />
							<circle cx="32" cy="32" r="22" stroke="currentColor" strokeOpacity="0.25" strokeWidth="2" strokeDasharray="5 4" />
							<circle cx="32" cy="32" r="14" stroke="currentColor" strokeOpacity="0.25" strokeWidth="2" />
							<path d="M10 30 C18 18, 46 18, 54 30" stroke="currentColor" strokeOpacity="0.35" strokeWidth="2" fill="none" />
							<path d="M10 34 C18 46, 46 46, 54 34" stroke="currentColor" strokeOpacity="0.35" strokeWidth="2" fill="none" />
							<line x1="32" y1="32" x2="32" y2="8" stroke="currentColor" strokeOpacity="0.6" strokeWidth="2" />
							<line x1="32" y1="32" x2="54" y2="32" stroke="currentColor" strokeOpacity="0.6" strokeWidth="2" />
							<line x1="32" y1="32" x2="10" y2="32" stroke="currentColor" strokeOpacity="0.4" strokeWidth="2" />
							<line x1="32" y1="32" x2="45" y2="19" stroke="currentColor" strokeOpacity="0.4" strokeWidth="2" />
							<circle cx="32" cy="32" r="4.5" fill="currentColor" fillOpacity="0.95" />
							<circle cx="32" cy="8" r="3" fill="currentColor" fillOpacity="0.9" />
							<circle cx="54" cy="32" r="2.6" fill="currentColor" fillOpacity="0.85" />
							<circle cx="10" cy="32" r="2.6" fill="currentColor" fillOpacity="0.75" />
							<circle cx="45" cy="19" r="2.4" fill="currentColor" fillOpacity="0.8" />
							<path d="M26 12 l2 -2 m-2 6 l3 -3" stroke="currentColor" strokeOpacity="0.6" strokeWidth="2" />
							<path d="M50 40 l2 -2 m-4 0 l3 -3" stroke="currentColor" strokeOpacity="0.5" strokeWidth="2" />
						</svg>
						{!collapsed && (
							<div className="analytics-logo__text">
								<span className="analytics-logo__title">BI数智平台</span>
								<span className="analytics-logo__subtitle">Analytics Modern</span>
							</div>
						)}
					</Link>
				</div>

				<Menu
					mode="inline"
					selectedKeys={[selectedKey]}
					items={menuItems}
					onClick={({ key }) => navigate(key)}
					style={{ borderRight: 0 }}
				/>

				{/* Collapse toggle at bottom */}
				<div style={{ position: "absolute", bottom: 0, width: "100%", borderTop: "1px solid #f0f0f0", padding: 8, textAlign: "center" }}>
					<Button
						type="text"
						icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
						onClick={() => setCollapsed(!collapsed)}
					/>
				</div>
			</Sider>

			<Layout>
				<Header
					style={{
						display: "flex",
						alignItems: "center",
						justifyContent: "space-between",
						padding: "0 16px",
						background: "#fff",
						borderBottom: "1px solid #f0f0f0",
						height: 48,
						lineHeight: "48px",
					}}
				>
					<div style={{ flex: 1 }}>
						<HeaderIntro />
					</div>

					<Space size={8}>
						<Tooltip title={t(locale, "nav.search")}>
							<Button
								type="text"
								icon={<SearchOutlined />}
								onClick={() => navigate("/search")}
							/>
						</Tooltip>

						<Tooltip title="切换应用">
							<Button
								type="text"
								icon={<AppstoreOutlined />}
								onClick={() => {
									localStorage.removeItem("dts.portal.preferredApp");
									window.location.href = "/#/portal";
								}}
							/>
						</Tooltip>

						<AntDropdown
							menu={{
								items: [
									{
										key: "user-info",
										label: displayName,
										disabled: true,
									},
									{ type: "divider" },
									{
										key: "logout",
										icon: <LogoutOutlined />,
										label: "退出登录",
										onClick: handleLogout,
									},
								],
							}}
							placement="bottomRight"
						>
							<Button type="text" icon={<UserOutlined />} />
						</AntDropdown>
					</Space>
				</Header>
				<Content style={{ padding: "0 16px 16px", overflow: "auto" }}>
					<ErrorBoundary>
						<Outlet />
					</ErrorBoundary>
				</Content>
			</Layout>
		</Layout>
	);
}
