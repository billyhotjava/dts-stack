import { Link } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CardListItem, type DashboardListItem } from "../api/analyticsApi";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

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
		<div className="page">
			<h1 className="pageTitle">{t(locale, "analyze.title")}</h1>
			<div className="pageSub">{t(locale, "analyze.subtitle")}</div>

			<div style={{ height: 16 }} />

			<div className="card">
				<div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
					<Link className="btn" to="/questions/new">
						{t(locale, "questions.new")}
					</Link>
					<Link className="btn" to="/dashboards/new">
						{t(locale, "dashboards.new")}
					</Link>
					<Link className="btn" to="/search">
						{t(locale, "nav.search")}
					</Link>
				</div>
			</div>

			<div style={{ height: 16 }} />

			<div className="grid2">
				<div className="card">
					<div className="row" style={{ justifyContent: "space-between" }}>
						<strong>{t(locale, "dashboards.title")}</strong>
						<Link className="btn" to="/dashboards">
							{t(locale, "common.open")}
						</Link>
					</div>
					<div style={{ height: 12 }} />
					{dashboards.state === "loading" && <div className="muted">{t(locale, "loading")}</div>}
					{dashboards.state === "error" && <ErrorNotice locale={locale} error={dashboards.error} />}
					{dashboards.state === "loaded" && dashboards.value.length === 0 && (
						<EmptyState
							title={t(locale, "common.empty")}
							action={
								<Link className="btn" to="/dashboards/new">
									{t(locale, "dashboards.new")}
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
				</div>

				<div className="card">
					<div className="row" style={{ justifyContent: "space-between" }}>
						<strong>{t(locale, "questions.title")}</strong>
						<Link className="btn" to="/questions">
							{t(locale, "common.open")}
						</Link>
					</div>
					<div style={{ height: 12 }} />
					{cards.state === "loading" && <div className="muted">{t(locale, "loading")}</div>}
					{cards.state === "error" && <ErrorNotice locale={locale} error={cards.error} />}
					{cards.state === "loaded" && cards.value.length === 0 && (
						<EmptyState
							title={t(locale, "common.empty")}
							action={
								<Link className="btn" to="/questions/new">
									{t(locale, "questions.new")}
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
				</div>
			</div>
		</div>
	);
}

