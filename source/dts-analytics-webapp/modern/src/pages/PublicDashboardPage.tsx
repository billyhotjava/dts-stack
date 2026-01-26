import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { analyticsApi, type DashboardQueryResponse, type PublicDashboardDetail } from "../api/analyticsApi";
import { ChartRenderer, type VisualizationType, type VisualizationSettings } from "../components/charts";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function PublicDashboardPage() {
	const { uuid } = useParams();
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [dashboard, setDashboard] = useState<LoadState<PublicDashboardDetail>>({ state: "loading" });
	const [dashcardResults, setDashcardResults] = useState<Record<number, LoadState<DashboardQueryResponse>>>({});

	useEffect(() => {
		let cancelled = false;
		if (!uuid) return;
		analyticsApi
			.getPublicDashboard(uuid)
			.then((v) => {
				if (cancelled) return;
				setDashboard({ state: "loaded", value: v });
			})
			.catch((e) => {
				if (cancelled) return;
				setDashboard({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [uuid]);

	useEffect(() => {
		let cancelled = false;
		if (!uuid) return;
		if (dashboard.state !== "loaded") return;

		const dashcards = Array.isArray(dashboard.value.ordered_cards) ? dashboard.value.ordered_cards : [];
		const next: Record<number, LoadState<DashboardQueryResponse>> = {};
		for (const dc of dashcards) {
			if (typeof dc.id === "number") next[dc.id] = { state: "loading" };
		}
		setDashcardResults(next);

		(async () => {
			for (const dc of dashcards) {
				if (cancelled) return;
				const dashcardId = dc.id;
				const cardId = dc.card_id ?? dc.card?.id;
				if (!dashcardId || !cardId) {
					next[dashcardId] = { state: "error", error: new Error("Missing dashcard/card id") };
					setDashcardResults({ ...next });
					continue;
				}
				try {
					const value = await analyticsApi.queryPublicDashboardDashcard(uuid, dashcardId, cardId, { parameters: [] });
					next[dashcardId] = { state: "loaded", value };
				} catch (e) {
					next[dashcardId] = { state: "error", error: e };
				}
				setDashcardResults({ ...next });
			}
		})();

		return () => {
			cancelled = true;
		};
	}, [uuid, dashboard]);

	const dashcards = useMemo(() => {
		if (dashboard.state !== "loaded") return [];
		return Array.isArray(dashboard.value.ordered_cards) ? dashboard.value.ordered_cards : [];
	}, [dashboard]);

	return (
		<div className="page">
			<h1 className="pageTitle">{dashboard.state === "loaded" ? dashboard.value.name ?? "-" : t(locale, "loading")}</h1>
			<div className="pageSub">
				<Link to="/analyze">{t(locale, "nav.analyze")}</Link>
				<span className="muted"> · </span>
				<span className="muted">Share</span>
			</div>

			<div style={{ height: 16 }} />

			{dashboard.state === "error" && <ErrorNotice locale={locale} error={dashboard.error} />}
			<div className="card">
				<div className="muted">{t(locale, "share.note")}</div>
			</div>

			<div style={{ height: 16 }} />

			{dashboard.state === "loaded" && dashcards.length === 0 && <EmptyState title={t(locale, "common.empty")} />}
			{dashboard.state === "loaded" && dashcards.length > 0 && (
				<div
					className="dashboardGrid"
					style={{
						display: "grid",
						gridTemplateColumns: "repeat(24, minmax(0, 1fr))",
						gap: 12,
						alignItems: "stretch",
					}}
				>
					{dashcards.map((dc) => {
						const cardId = dc.card_id ?? dc.card?.id;
						const name = dc.card?.name ?? (cardId ? `Card ${cardId}` : "Card");
						const gridColumn =
							typeof dc.col === "number" && typeof dc.size_x === "number" ? `${dc.col + 1} / span ${dc.size_x}` : "auto";
						const gridRow =
							typeof dc.row === "number" && typeof dc.size_y === "number" ? `${dc.row + 1} / span ${dc.size_y}` : "auto";
						const result = dashcardResults[dc.id];
						return (
							<div key={String(dc.id)} className="card" style={{ gridColumn, gridRow, overflow: "hidden" }}>
								<div className="row" style={{ justifyContent: "space-between" }}>
									<div style={{ fontWeight: 600 }}>{cardId ? <Link to={`/questions/${cardId}`}>{String(name)}</Link> : String(name)}</div>
									<span className="tag">card</span>
								</div>
								<div style={{ height: 10 }} />
								{!result || result.state === "loading" ? (
									<div>{t(locale, "loading")}</div>
								) : result.state === "error" ? (
									<ErrorNotice locale={locale} error={result.error} />
								) : Array.isArray(result.value?.data?.cols) && Array.isArray(result.value?.data?.rows) ? (
									<ChartRenderer
										data={{
											cols: (result.value.data?.cols ?? []) as { name: string; display_name?: string; base_type?: string }[],
											rows: (result.value.data?.rows ?? []) as any[][]
										}}
										display={(dc.card?.display as VisualizationType) || "table"}
										settings={((dc.card as any)?.visualization_settings as VisualizationSettings) || {}}
									/>
								) : (
									<EmptyState title={t(locale, "common.empty")} />
								)}
							</div>
						);
					})}
				</div>
			)}
		</div>
	);
}

