import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { analyticsApi, type CurrentUser, type DashboardListItem, type CardListItem, type ScreenListItem } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { PageContainer } from "../components/PageContainer/PageContainer";
import { Card, CardHeader, CardBody } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const DashboardIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="7" height="9" x="3" y="3" rx="1" />
		<rect width="7" height="5" x="14" y="3" rx="1" />
		<rect width="7" height="9" x="14" y="12" rx="1" />
		<rect width="7" height="5" x="3" y="16" rx="1" />
	</svg>
);

const QuestionIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="18" height="18" x="3" y="3" rx="2" />
		<path d="M3 9h18" />
		<path d="M9 21V9" />
	</svg>
);

const DatabaseIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<ellipse cx="12" cy="5" rx="9" ry="3" />
		<path d="M3 5v14a9 3 0 0 0 18 0V5" />
		<path d="M3 12a9 3 0 0 0 18 0" />
	</svg>
);

const ScreenIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect x="3" y="4" width="18" height="12" rx="2" />
		<path d="M8 20h8" />
		<path d="M12 16v4" />
	</svg>
);

const PlusIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="M12 5v14" />
	</svg>
);

const ArrowRightIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="m12 5 7 7-7 7" />
	</svg>
);

export default function HomePage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [user, setUser] = useState<LoadState<CurrentUser>>({ state: "loading" });
	const [health, setHealth] = useState<LoadState<string>>({ state: "loading" });
	const [dashboards, setDashboards] = useState<LoadState<DashboardListItem[]>>({ state: "loading" });
	const [questions, setQuestions] = useState<LoadState<CardListItem[]>>({ state: "loading" });
	const [screens, setScreens] = useState<LoadState<ScreenListItem[]>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;

		analyticsApi
			.getCurrentUser()
			.then((value) => {
				if (cancelled) return;
				setUser({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setUser({ state: "error", error: e });
			});

		analyticsApi
			.getHealth()
			.then((v) => {
				if (cancelled) return;
				setHealth({ state: "loaded", value: v?.status ?? "unknown" });
			})
			.catch((e) => {
				if (cancelled) return;
				setHealth({ state: "error", error: e });
			});

		analyticsApi
			.listDashboards()
			.then((value) => {
				if (cancelled) return;
				setDashboards({ state: "loaded", value: value.slice(0, 5) });
			})
			.catch((e) => {
				if (cancelled) return;
				setDashboards({ state: "error", error: e });
			});

		analyticsApi
			.listCards()
			.then((value) => {
				if (cancelled) return;
				setQuestions({ state: "loaded", value: value.slice(0, 5) });
			})
			.catch((e) => {
				if (cancelled) return;
				setQuestions({ state: "error", error: e });
			});

		analyticsApi
			.listScreens()
			.then((value) => {
				if (cancelled) return;
				const published = value
					.filter((item) => Number(item.publishedVersionNo || 0) > 0)
					.sort((a, b) => {
						const ta = new Date(a.publishedAt || a.updatedAt || 0).getTime();
						const tb = new Date(b.publishedAt || b.updatedAt || 0).getTime();
						return tb - ta;
					})
					.slice(0, 5);
				setScreens({ state: "loaded", value: published });
			})
			.catch((e) => {
				if (cancelled) return;
				setScreens({ state: "error", error: e });
			});

		return () => {
			cancelled = true;
		};
	}, []);

	const userName = (() => {
		if (user.state !== "loaded") return "";
		const value = user.value;
		return value.common_name || [value.first_name, value.last_name].filter(Boolean).join(" ") || value.email || "";
	})();

	const healthStatus = health.state === "loaded" ? health.value : "loading";

	return (
		<PageContainer>
			{/* Welcome Banner */}
			<div className="welcome-banner">
				<div className="welcome-banner__content">
					<h1 className="welcome-banner__title">
						{t(locale, "home.welcome")}
					</h1>
				</div>
				<div className="welcome-banner__status">
					<Badge variant={healthStatus === "ok" ? "success" : healthStatus === "loading" ? "default" : "error"}>
						{t(locale, "health")}: {healthStatus === "ok" ? "Healthy" : healthStatus}
					</Badge>
				</div>
			</div>

			{/* Error Notices */}
			{user.state === "error" && <ErrorNotice locale={locale} error={user.error} />}
			{health.state === "error" && <ErrorNotice locale={locale} error={health.error} />}

			{/* Quick Actions */}
			<div className="section">
				<h2 className="sectionTitle">{t(locale, "home.quickActions")}</h2>
				<div className="grid3">
					<Link to="/questions/new" className="quick-action-card">
						<div className="quick-action-card__icon">
							<QuestionIcon />
						</div>
						<div className="quick-action-card__content">
							<h3>{t(locale, "questions.new")}</h3>
							<p>{t(locale, "home.newQuestionDesc")}</p>
						</div>
					</Link>
					<Link to="/dashboards/new" className="quick-action-card">
						<div className="quick-action-card__icon">
							<DashboardIcon />
						</div>
						<div className="quick-action-card__content">
							<h3>{t(locale, "dashboards.new")}</h3>
							<p>{t(locale, "home.newDashboardDesc")}</p>
						</div>
					</Link>
					<Link to="/data" className="quick-action-card">
						<div className="quick-action-card__icon">
							<DatabaseIcon />
						</div>
						<div className="quick-action-card__content">
							<h3>{t(locale, "nav.data")}</h3>
							<p>{t(locale, "home.browseDataDesc")}</p>
						</div>
					</Link>
				</div>
			</div>

			{/* Recent Items */}
			<div className="grid3" style={{ marginTop: "var(--spacing-xl)" }}>
				{/* Recent Dashboards */}
				<Card>
					<CardHeader
						title={t(locale, "home.recentDashboards")}
						action={
							<Link to="/dashboards">
								<Button variant="tertiary" size="sm" icon={<ArrowRightIcon />} iconPosition="right">
									{t(locale, "common.viewAll")}
								</Button>
							</Link>
						}
					/>
					<CardBody>
						{dashboards.state === "loading" && (
							<div className="loading-state">
								<Spinner size="md" />
							</div>
						)}
						{dashboards.state === "error" && (
							<div className="error-state">{t(locale, "error")}</div>
						)}
						{dashboards.state === "loaded" && dashboards.value.length === 0 && (
							<div className="empty-state-small">
								<p>{t(locale, "common.empty")}</p>
								<Link to="/dashboards/new">
									<Button variant="primary" size="sm" icon={<PlusIcon />}>
										{t(locale, "dashboards.new")}
									</Button>
								</Link>
							</div>
						)}
						{dashboards.state === "loaded" && dashboards.value.length > 0 && (
							<ul className="item-list">
								{dashboards.value.map((d) => (
									<li key={d.id}>
										<Link to={`/dashboards/${d.id}`} className="item-list__link">
											<DashboardIcon />
											<span>{d.name || t(locale, "common.untitled")}</span>
										</Link>
									</li>
								))}
							</ul>
						)}
					</CardBody>
				</Card>

				{/* Recent Questions */}
				<Card>
					<CardHeader
						title={t(locale, "home.recentQuestions")}
						action={
							<Link to="/questions">
								<Button variant="tertiary" size="sm" icon={<ArrowRightIcon />} iconPosition="right">
									{t(locale, "common.viewAll")}
								</Button>
							</Link>
						}
					/>
					<CardBody>
						{questions.state === "loading" && (
							<div className="loading-state">
								<Spinner size="md" />
							</div>
						)}
						{questions.state === "error" && (
							<div className="error-state">{t(locale, "error")}</div>
						)}
						{questions.state === "loaded" && questions.value.length === 0 && (
							<div className="empty-state-small">
								<p>{t(locale, "common.empty")}</p>
								<Link to="/questions/new">
									<Button variant="primary" size="sm" icon={<PlusIcon />}>
										{t(locale, "questions.new")}
									</Button>
								</Link>
							</div>
						)}
						{questions.state === "loaded" && questions.value.length > 0 && (
							<ul className="item-list">
								{questions.value.map((q) => (
									<li key={q.id}>
										<Link to={`/questions/${q.id}`} className="item-list__link">
											<QuestionIcon />
											<span>{q.name || t(locale, "common.untitled")}</span>
										</Link>
									</li>
								))}
							</ul>
						)}
					</CardBody>
				</Card>

				{/* Published Screens */}
				<Card>
					<CardHeader
						title={t(locale, "home.publishedScreens")}
						action={
							<Link to="/screens">
								<Button variant="tertiary" size="sm" icon={<ArrowRightIcon />} iconPosition="right">
									{t(locale, "common.viewAll")}
								</Button>
							</Link>
						}
					/>
					<CardBody>
						{screens.state === "loading" && (
							<div className="loading-state">
								<Spinner size="md" />
							</div>
						)}
						{screens.state === "error" && (
							<div className="error-state">{t(locale, "error")}</div>
						)}
						{screens.state === "loaded" && screens.value.length === 0 && (
							<div className="empty-state-small">
								<p>{t(locale, "home.noPublishedScreens")}</p>
								<Link to="/screens">
									<Button variant="primary" size="sm" icon={<PlusIcon />}>
										{t(locale, "home.openScreenCenter")}
									</Button>
								</Link>
							</div>
						)}
						{screens.state === "loaded" && screens.value.length > 0 && (
							<ul className="item-list">
								{screens.value.map((s) => (
									<li key={s.id}>
										<div className="item-list__row">
											<a
												href={`/analytics/screens/${encodeURIComponent(String(s.id))}/preview`}
												className="item-list__link"
												target="_blank"
												rel="noreferrer"
											>
												<ScreenIcon />
												<span>{s.name || t(locale, "common.untitled")}</span>
											</a>
											<div className="item-list__meta">v{s.publishedVersionNo || "-"}</div>
										</div>
									</li>
								))}
							</ul>
						)}
					</CardBody>
				</Card>
			</div>

			<style>{`
				.welcome-banner {
					display: flex;
					align-items: center;
					justify-content: space-between;
					padding: var(--spacing-lg) var(--spacing-xl);
					background: linear-gradient(135deg, var(--color-brand) 0%, var(--color-brand-dark) 100%);
					border-radius: var(--radius-lg);
					color: var(--color-text-inverse);
					margin-bottom: var(--spacing-xl);
					min-height: 64px;
				}

				.welcome-banner__title {
					margin: 0;
					font-size: var(--font-size-xl);
					font-weight: var(--font-weight-bold);
					line-height: 1;
				}

				.quick-action-card {
					display: flex;
					align-items: flex-start;
					gap: var(--spacing-md);
					padding: var(--spacing-lg);
					background: var(--color-bg-primary);
					border: 1px solid var(--color-border);
					border-radius: var(--radius-md);
					text-decoration: none;
					color: inherit;
					transition: box-shadow var(--transition-fast), border-color var(--transition-fast);
				}

				.quick-action-card:hover {
					border-color: var(--color-brand);
					box-shadow: var(--shadow-md);
				}

				.quick-action-card__icon {
					display: flex;
					align-items: center;
					justify-content: center;
					width: 48px;
					height: 48px;
					border-radius: var(--radius-md);
					background: var(--color-bg-hover);
					color: var(--color-brand);
					flex-shrink: 0;
				}

				.quick-action-card__content h3 {
					margin: 0;
					font-size: var(--font-size-md);
					font-weight: var(--font-weight-semibold);
					color: var(--color-text-primary);
				}

				.quick-action-card__content p {
					margin: var(--spacing-xs) 0 0;
					font-size: var(--font-size-sm);
					color: var(--color-text-secondary);
				}

				.loading-state {
					display: flex;
					justify-content: center;
					padding: var(--spacing-lg);
				}

				.error-state {
					padding: var(--spacing-md);
					text-align: center;
					color: var(--color-error);
				}

				.empty-state-small {
					display: flex;
					flex-direction: column;
					align-items: center;
					gap: var(--spacing-md);
					padding: var(--spacing-lg);
					text-align: center;
					color: var(--color-text-secondary);
				}

				.empty-state-small p {
					margin: 0;
				}

				.item-list {
					list-style: none;
					margin: 0;
					padding: 0;
				}

				.item-list li {
					border-bottom: 1px solid var(--color-border);
				}

				.item-list li:last-child {
					border-bottom: none;
				}

				.item-list__link {
					display: flex;
					align-items: center;
					gap: var(--spacing-sm);
					padding: var(--spacing-sm) var(--spacing-xs);
					color: var(--color-text-primary);
					text-decoration: none;
					transition: background-color var(--transition-fast);
					flex: 1 1 auto;
					min-width: 0;
				}

				.item-list__link:hover {
					background: var(--color-bg-hover);
				}

				.item-list__link svg {
					width: 16px;
					height: 16px;
					color: var(--color-text-tertiary);
					flex-shrink: 0;
				}

				.item-list__link span {
					min-width: 0;
					overflow: hidden;
					text-overflow: ellipsis;
					white-space: nowrap;
				}

				.item-list__row {
					display: flex;
					align-items: center;
					justify-content: space-between;
					gap: var(--spacing-sm);
				}

				.item-list__meta {
					padding-right: var(--spacing-xs);
					font-size: var(--font-size-xs);
					color: var(--color-text-tertiary);
					white-space: nowrap;
					flex: 0 0 auto;
				}
			`}</style>
		</PageContainer>
	);
}
