import { Link } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CardListItem, type DashboardListItem } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Card, CardHeader, CardBody } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { Spinner } from "../ui/Loading/Spinner";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const PlusIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="M12 5v14" />
	</svg>
);

const SearchIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<circle cx="11" cy="11" r="8" />
		<path d="m21 21-4.35-4.35" />
	</svg>
);

const ArrowRightIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="m12 5 7 7-7 7" />
	</svg>
);

export default function AnalyzePage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [dashboards, setDashboards] = useState<LoadState<DashboardListItem[]>>({ state: "loading" });
	const [cards, setCards] = useState<LoadState<CardListItem[]>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listDashboards()
			.then((value) => {
				if (cancelled) return;
				setDashboards({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setDashboards({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listCards()
			.then((value) => {
				if (cancelled) return;
				setCards({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setCards({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	const topDashboards = dashboards.state === "loaded" ? dashboards.value.slice(0, 10) : [];
	const topCards = cards.state === "loaded" ? cards.value.slice(0, 10) : [];

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "analyze.title")}
				subtitle={t(locale, "analyze.subtitle")}
			/>

			<Card style={{ marginBottom: "var(--spacing-lg)" }}>
				<CardBody>
					<div style={{ display: "flex", gap: "var(--spacing-sm)", flexWrap: "wrap" }}>
						<Link to="/questions/new">
							<Button variant="primary" icon={<PlusIcon />}>
								{t(locale, "questions.new")}
							</Button>
						</Link>
						<Link to="/dashboards/new">
							<Button variant="secondary" icon={<PlusIcon />}>
								{t(locale, "dashboards.new")}
							</Button>
						</Link>
						<Link to="/search">
							<Button variant="tertiary" icon={<SearchIcon />}>
								{t(locale, "nav.search")}
							</Button>
						</Link>
					</div>
				</CardBody>
			</Card>

			<div className="grid2">
				<Card>
					<CardHeader
						title={t(locale, "dashboards.title")}
						action={
							<Link to="/dashboards">
								<Button variant="tertiary" size="sm" icon={<ArrowRightIcon />} iconPosition="right">
									{t(locale, "common.open")}
								</Button>
							</Link>
						}
					/>
					<CardBody>
						{dashboards.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spinner size="md" />
							</div>
						)}
						{dashboards.state === "error" && <ErrorNotice locale={locale} error={dashboards.error} />}
						{dashboards.state === "loaded" && dashboards.value.length === 0 && (
							<EmptyState
								title={t(locale, "common.empty")}
								action={
									<Link to="/dashboards/new">
										<Button variant="primary" size="sm" icon={<PlusIcon />}>
											{t(locale, "dashboards.new")}
										</Button>
									</Link>
								}
							/>
						)}
						{dashboards.state === "loaded" && dashboards.value.length > 0 && (
							<table className="table">
								<thead>
									<tr>
										<th>{t(locale, "common.name")}</th>
										<th>{t(locale, "common.id")}</th>
									</tr>
								</thead>
								<tbody>
									{topDashboards.map((d) => (
										<tr key={String(d.id)}>
											<td>
												<Link to={`/dashboards/${d.id}`}>{d.name ?? "-"}</Link>
											</td>
											<td>{d.id}</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
					</CardBody>
				</Card>

				<Card>
					<CardHeader
						title={t(locale, "questions.title")}
						action={
							<Link to="/questions">
								<Button variant="tertiary" size="sm" icon={<ArrowRightIcon />} iconPosition="right">
									{t(locale, "common.open")}
								</Button>
							</Link>
						}
					/>
					<CardBody>
						{cards.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spinner size="md" />
							</div>
						)}
						{cards.state === "error" && <ErrorNotice locale={locale} error={cards.error} />}
						{cards.state === "loaded" && cards.value.length === 0 && (
							<EmptyState
								title={t(locale, "common.empty")}
								action={
									<Link to="/questions/new">
										<Button variant="primary" size="sm" icon={<PlusIcon />}>
											{t(locale, "questions.new")}
										</Button>
									</Link>
								}
							/>
						)}
						{cards.state === "loaded" && cards.value.length > 0 && (
							<table className="table">
								<thead>
									<tr>
										<th>{t(locale, "common.name")}</th>
										<th>{t(locale, "common.id")}</th>
									</tr>
								</thead>
								<tbody>
									{topCards.map((c) => (
										<tr key={String(c.id)}>
											<td>
												<Link to={`/questions/${c.id}`}>{c.name ?? "-"}</Link>
											</td>
											<td>{c.id}</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
					</CardBody>
				</Card>
			</div>
		</PageContainer>
	);
}
