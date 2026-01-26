import { Link, NavLink, Outlet } from "react-router";
import { ErrorBoundary } from "../components/ErrorBoundary";
import { getEffectiveLocale, setEffectiveLocale, t, toggleLocale } from "../i18n";
import "./layout.css";

export function AppLayout() {
	const locale = getEffectiveLocale();
	return (
		<div className="layout">
			<aside className="sidebar">
				<div className="brand">
					<Link className="brandLink" to="/">
						DTS Analytics
					</Link>
					<div className="brandSub">{t(locale, "subtitle")}</div>
				</div>
				<nav className="nav">
					<div className="navSectionTitle">{t(locale, "nav.section.core")}</div>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/">
						{t(locale, "nav.home")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/analyze">
						{t(locale, "nav.analyze")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/questions">
						{t(locale, "nav.questions")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/dashboards">
						{t(locale, "nav.dashboards")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/collections">
						{t(locale, "nav.collections")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/collections/root">
						{t(locale, "nav.myCollection")}
					</NavLink>

					<div className="navSectionTitle" style={{ marginTop: 10 }}>
						{t(locale, "nav.section.data")}
					</div>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/data">
						{t(locale, "nav.data")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/models">
						{t(locale, "nav.models")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/metrics">
						{t(locale, "nav.metrics")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/trash">
						{t(locale, "nav.trash")}
					</NavLink>

					<div className="navSectionTitle" style={{ marginTop: 10 }}>
						{t(locale, "nav.section.tools")}
					</div>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/search">
						{t(locale, "nav.search")}
					</NavLink>
				</nav>
				<div className="sidebarFooter">
					<button
						className="btn"
						type="button"
						onClick={() => {
							setEffectiveLocale(toggleLocale(locale));
							window.location.reload();
						}}
					>
						{locale === "en" ? t(locale, "lang.zh") : t(locale, "lang.en")}
					</button>
				</div>
			</aside>

			<main className="main">
				<ErrorBoundary>
					<Outlet />
				</ErrorBoundary>
			</main>
		</div>
	);
}
