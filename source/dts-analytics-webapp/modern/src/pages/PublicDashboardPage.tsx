import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { analyticsApi, type DashboardQueryResponse, type PublicDashboardDetail } from "../api/analyticsApi";
import { ChartRenderer, type VisualizationType, type VisualizationSettings } from "../components/charts";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Card, CardHeader, CardBody } from "../ui/Card/Card";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
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
		<PageContainer maxWidth="full">
			<PageHeader
				title={dashboard.state === "loaded" ? dashboard.value.name ?? "-" : t(locale, "loading")}
				breadcrumbs={
					<Breadcrumb items={[
						{ label: t(locale, "nav.analyze"), href: "/analyze" },
						{ label: "Share" }
					]} />
				}
			/>

			{dashboard.state === "error" && <ErrorNotice locale={locale} error={dashboard.error} />}

			<Card style={{ marginBottom: "var(--spacing-lg)" }}>
				<CardBody>
					<p className="text-muted" style={{ margin: 0 }}>{t(locale, "share.note")}</p>
				</CardBody>
			</Card>

			{dashboard.state === "loaded" && dashcards.length === 0 && <EmptyState title={t(locale, "common.empty")} />}
			{dashboard.state === "loaded" && dashcards.length > 0 && (
				<div
					className="dashboardGrid"
					style={{
						display: "grid",
						gridTemplateColumns: "repeat(24, minmax(0, 1fr))",
						gap: "var(--spacing-md)",
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
							<Card key={String(dc.id)} style={{ gridColumn, gridRow, overflow: "hidden" }}>
								<CardHeader
									title={cardId ? <Link to={`/questions/${cardId}`}>{String(name)}</Link> : String(name)}
									action={<Badge variant="default" size="sm">card</Badge>}
								/>
								<CardBody>
									{!result || result.state === "loading" ? (
										<div className="loading-container" style={{ padding: "var(--spacing-md)" }}>
											<Spinner size="sm" />
										</div>
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
								</CardBody>
							</Card>
						);
					})}
				</div>
			)}
		</PageContainer>
	);
}

