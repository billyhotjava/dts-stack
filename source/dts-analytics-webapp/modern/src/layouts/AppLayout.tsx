import { Link, Outlet, useLocation } from "react-router";
import { ErrorBoundary } from "../components/ErrorBoundary";
import {
	SidebarProvider,
	SidebarNav,
	SidebarSection,
	SidebarItem,
	SidebarDivider,
} from "../components/SidebarNav/SidebarNav";
import { ThemeToggle } from "../ui/ThemeToggle/ThemeToggle";
import { Dropdown, DropdownItem, DropdownSeparator } from "../ui/Dropdown/Dropdown";
import { getEffectiveLocale, t } from "../i18n";
import "./layout.css";

// Icons
const HomeIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
		<polyline points="9 22 9 12 15 12 15 22" />
	</svg>
);

const AnalyzeIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<circle cx="12" cy="12" r="10" />
		<path d="M12 16v-4" />
		<path d="M12 8h.01" />
	</svg>
);

const QuestionIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="18" height="18" x="3" y="3" rx="2" />
		<path d="M3 9h18" />
		<path d="M9 21V9" />
	</svg>
);

const DashboardIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="7" height="9" x="3" y="3" rx="1" />
		<rect width="7" height="5" x="14" y="3" rx="1" />
		<rect width="7" height="9" x="14" y="12" rx="1" />
		<rect width="7" height="5" x="3" y="16" rx="1" />
	</svg>
);

const CollectionIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z" />
	</svg>
);

const DatabaseIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<ellipse cx="12" cy="5" rx="9" ry="3" />
		<path d="M3 5v14a9 3 0 0 0 18 0V5" />
		<path d="M3 12a9 3 0 0 0 18 0" />
	</svg>
);

const ModelIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M12 2L2 7l10 5 10-5-10-5Z" />
		<path d="m2 17 10 5 10-5" />
		<path d="m2 12 10 5 10-5" />
	</svg>
);

const MetricIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M3 3v18h18" />
		<path d="m19 9-5 5-4-4-3 3" />
	</svg>
);

const TrashIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M3 6h18" />
		<path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
		<path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
	</svg>
);

const SearchIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<circle cx="11" cy="11" r="8" />
		<path d="m21 21-4.35-4.35" />
	</svg>
);

const ScreenIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
		<path d="M8 21h8" />
		<path d="M12 17v4" />
	</svg>
);

const ExploreSessionIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M3 4h18v14H3z" />
		<path d="M7 8h10" />
		<path d="M7 12h7" />
		<path d="M7 16h5" />
	</svg>
);

const ReportFactoryIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
		<path d="M14 2v6h6" />
		<path d="M8 13h8" />
		<path d="M8 17h6" />
	</svg>
);

const MetricLensIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<circle cx="11" cy="11" r="7" />
		<path d="m21 21-4.35-4.35" />
		<path d="M8 11h6" />
		<path d="M11 8v6" />
	</svg>
);

const Nl2SqlEvalIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect x="3" y="4" width="18" height="16" rx="2" />
		<path d="M7 8h10" />
		<path d="M7 12h10" />
		<path d="M7 16h6" />
	</svg>
);

const UserIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
		<circle cx="12" cy="7" r="4" />
	</svg>
);

const LogoutIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
		<polyline points="16 17 21 12 16 7" />
		<line x1="21" y1="12" x2="9" y2="12" />
	</svg>
);

const ChevronRightIcon = () => (
	<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="m9 18 6-6-6-6" />
	</svg>
);

// Read user info from platform's shared localStorage store
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

// Route → [sectionKey, navKey] mapping for breadcrumb
const ROUTE_NAV_MAP: { path: string; section: string; nav?: string }[] = [
	{ path: "/", section: "nav.section.core" },
	{ path: "/analyze", section: "nav.section.core", nav: "nav.analyze" },
	{ path: "/questions", section: "nav.section.core", nav: "nav.questions" },
	{ path: "/dashboards", section: "nav.section.core", nav: "nav.dashboards" },
	{ path: "/collections", section: "nav.section.core", nav: "nav.collections" },
	{ path: "/data", section: "nav.section.data", nav: "nav.data" },
	{ path: "/models", section: "nav.section.data", nav: "nav.models" },
	{ path: "/metrics", section: "nav.section.data", nav: "nav.metrics" },
	{ path: "/trash", section: "nav.section.data", nav: "nav.trash" },
	{ path: "/screens", section: "nav.section.tools", nav: "nav.screens" },
	{ path: "/explore-sessions", section: "nav.section.tools", nav: "nav.exploreSessions" },
	{ path: "/report-factory", section: "nav.section.tools", nav: "nav.reportFactory" },
	{ path: "/metric-lens", section: "nav.section.tools", nav: "nav.metricLens" },
	{ path: "/nl2sql-eval", section: "nav.section.tools", nav: "nav.nl2sqlEval" },
	{ path: "/search", section: "nav.section.tools", nav: "nav.search" },
];

function HeaderBreadcrumb() {
	const locale = getEffectiveLocale();
	const location = useLocation();
	const path = location.pathname;

	let matched: { section: string; nav?: string } | null = null;
	for (const route of ROUTE_NAV_MAP) {
		if (route.path === "/") {
			if (path === "/") { matched = route; break; }
			continue;
		}
		if (path === route.path || path.startsWith(route.path + "/")) {
			matched = route;
			break;
		}
	}

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

export function AppLayout() {
	const locale = getEffectiveLocale();
	const userInfo = getUserInfo();
	const displayName = userInfo.fullName || userInfo.username || "用户";

	const handleLogout = () => {
		try {
			// Clear tokens from userStore
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
		// Redirect to platform root (which will trigger login redirect)
		window.location.href = "/";
	};

	const Logo = (
		<Link to="/" className="sidebar-logo-link" style={{ display: 'flex', alignItems: 'center', gap: '12px', textDecoration: 'none', paddingLeft: '8px' }}>
			<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none" width="32" height="32" style={{ color: 'var(--color-brand)', flexShrink: 0 }}>
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
			<div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1 }}>
				<span style={{ fontSize: '18px', fontWeight: 700, letterSpacing: '-0.025em', color: 'var(--color-sidebar-text)' }}>BI数智平台</span>
				<span style={{ fontSize: '10px', fontWeight: 500, color: 'var(--color-sidebar-text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', marginTop: '4px', opacity: 0.8 }}>机密 (Confidential)</span>
			</div>
		</Link>
	);

	const LogoCollapsed = (
		<Link to="/" className="sidebar-logo-link" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
			<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none" width="32" height="32" style={{ color: 'var(--color-brand)' }}>
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
		</Link>
	);

	const UserMenu = (
		<Dropdown
			trigger={
				<button className="header-user-trigger" type="button">
					<UserIcon />
				</button>
			}
			placement="bottom-end"
		>
			<div className="header-user-info">
				<div className="header-user-info__avatar">
					<UserIcon />
				</div>
				<div className="header-user-info__details">
					<div className="header-user-info__name">{displayName}</div>
					{userInfo.email && (
						<div className="header-user-info__email">
							{userInfo.email}
							{userInfo.username ? `（${userInfo.username}）` : null}
						</div>
					)}
				</div>
			</div>
			<DropdownSeparator />
			<DropdownItem
				icon={<LogoutIcon />}
				danger
				onClick={handleLogout}
			>
				退出
			</DropdownItem>
		</Dropdown>
	);

	return (
		<SidebarProvider>
			<div className="layout">
				<SidebarNav
					logo={Logo}
					logoCollapsed={LogoCollapsed}
					footer={null}
				>
					<SidebarSection title={t(locale, "nav.section.core")}>
						<SidebarItem to="/" icon={<HomeIcon />} label={t(locale, "nav.home")} end />
						<SidebarItem to="/analyze" icon={<AnalyzeIcon />} label={t(locale, "nav.analyze")} />
						<SidebarItem to="/questions" icon={<QuestionIcon />} label={t(locale, "nav.questions")} />
						<SidebarItem to="/dashboards" icon={<DashboardIcon />} label={t(locale, "nav.dashboards")} />
						<SidebarItem to="/collections" icon={<CollectionIcon />} label={t(locale, "nav.collections")} end />
						<SidebarItem to="/collections/root" icon={<CollectionIcon />} label={t(locale, "nav.myCollection")} />
					</SidebarSection>

					<SidebarDivider />

					<SidebarSection title={t(locale, "nav.section.data")}>
						<SidebarItem to="/data" icon={<DatabaseIcon />} label={t(locale, "nav.data")} end />
						<SidebarItem to="/models" icon={<ModelIcon />} label={t(locale, "nav.models")} />
						<SidebarItem to="/metrics" icon={<MetricIcon />} label={t(locale, "nav.metrics")} />
						<SidebarItem to="/trash" icon={<TrashIcon />} label={t(locale, "nav.trash")} />
					</SidebarSection>

					<SidebarDivider />

					<SidebarSection title={t(locale, "nav.section.tools")}>
						<SidebarItem to="/screens" icon={<ScreenIcon />} label={t(locale, "nav.screens")} />
						<SidebarItem to="/explore-sessions" icon={<ExploreSessionIcon />} label={t(locale, "nav.exploreSessions")} />
						<SidebarItem to="/report-factory" icon={<ReportFactoryIcon />} label={t(locale, "nav.reportFactory")} />
						<SidebarItem to="/metric-lens" icon={<MetricLensIcon />} label={t(locale, "nav.metricLens")} />
						<SidebarItem to="/nl2sql-eval" icon={<Nl2SqlEvalIcon />} label={t(locale, "nav.nl2sqlEval")} />
						<SidebarItem to="/search" icon={<SearchIcon />} label={t(locale, "nav.search")} />
					</SidebarSection>
				</SidebarNav>

				<main className="main">
					<header className="main-header">
						<div className="main-header__left">
							<HeaderBreadcrumb />
						</div>
						<div className="main-header__right">
							<ThemeToggle showLabel={false} />
							{UserMenu}
						</div>
					</header>
					<div className="main-content">
						<ErrorBoundary>
							<Outlet />
						</ErrorBoundary>
					</div>
				</main>
			</div>
		</SidebarProvider>
	);
}
