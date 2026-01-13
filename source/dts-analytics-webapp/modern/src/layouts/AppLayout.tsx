import { Link, NavLink, Outlet } from "react-router";
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
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/">
						{t(locale, "nav.home")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/collections">
						{t(locale, "nav.collections")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/dashboards">
						{t(locale, "nav.dashboards")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/questions">
						{t(locale, "nav.questions")}
					</NavLink>
					<NavLink className={({ isActive }) => (isActive ? "navItem active" : "navItem")} to="/data">
						{t(locale, "nav.data")}
					</NavLink>
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
					<a className="btn" href="/analytics/legacy" target="_blank" rel="noreferrer">
						{t(locale, "openLegacy")}
					</a>
				</div>
			</aside>

			<main className="main">
				<Outlet />
			</main>
		</div>
	);
}
