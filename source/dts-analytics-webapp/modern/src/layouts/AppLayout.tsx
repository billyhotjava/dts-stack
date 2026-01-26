import { Link, Outlet } from "react-router";
import { ErrorBoundary } from "../components/ErrorBoundary";
import {
	SidebarProvider,
	SidebarNav,
	SidebarSection,
	SidebarItem,
	SidebarSearch,
	SidebarDivider,
} from "../components/SidebarNav/SidebarNav";
import { ThemeToggle } from "../ui/ThemeToggle/ThemeToggle";
import { Dropdown, DropdownItem, DropdownSeparator } from "../ui/Dropdown/Dropdown";
import { getEffectiveLocale, setEffectiveLocale, t, toggleLocale } from "../i18n";
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

const UserIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
		<circle cx="12" cy="7" r="4" />
	</svg>
);

const GlobeIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<circle cx="12" cy="12" r="10" />
		<path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20" />
		<path d="M2 12h20" />
	</svg>
);

export function AppLayout() {
	const locale = getEffectiveLocale();

	const handleLanguageToggle = () => {
		setEffectiveLocale(toggleLocale(locale));
		window.location.reload();
	};

	const Logo = (
		<Link to="/" className="sidebar-logo-link">
			<svg width="28" height="28" viewBox="0 0 32 32" fill="none">
				<rect width="32" height="32" rx="8" fill="var(--color-brand)" />
				<path d="M8 22V14l8-6 8 6v8" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
				<path d="M12 22v-6h8v6" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
			<span className="sidebar-logo-text">DTS Analytics</span>
		</Link>
	);

	const LogoCollapsed = (
		<Link to="/" className="sidebar-logo-link">
			<svg width="28" height="28" viewBox="0 0 32 32" fill="none">
				<rect width="32" height="32" rx="8" fill="var(--color-brand)" />
				<path d="M8 22V14l8-6 8 6v8" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
				<path d="M12 22v-6h8v6" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
		</Link>
	);

	const UserMenu = (
		<Dropdown
			trigger={
				<button className="user-menu-trigger" type="button">
					<UserIcon />
				</button>
			}
			placement="bottom-end"
		>
			<DropdownItem
				icon={<GlobeIcon />}
				onClick={handleLanguageToggle}
			>
				{locale === "en" ? t(locale, "lang.zh") : t(locale, "lang.en")}
			</DropdownItem>
			<DropdownSeparator />
			<DropdownItem>
				<ThemeToggle showLabel />
			</DropdownItem>
		</Dropdown>
	);

	return (
		<SidebarProvider>
			<div className="layout">
				<SidebarNav
					logo={Logo}
					logoCollapsed={LogoCollapsed}
					header={<SidebarSearch placeholder={t(locale, "nav.search")} />}
					footer={UserMenu}
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
						<SidebarItem to="/search" icon={<SearchIcon />} label={t(locale, "nav.search")} />
					</SidebarSection>
				</SidebarNav>

				<main className="main">
					<ErrorBoundary>
						<Outlet />
					</ErrorBoundary>
				</main>
			</div>
		</SidebarProvider>
	);
}
