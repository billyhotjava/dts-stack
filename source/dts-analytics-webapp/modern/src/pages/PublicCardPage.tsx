import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { analyticsApi, type CardQueryResponse, type PublicCardDetail } from "../api/analyticsApi";
import { ChartRenderer, type VisualizationType, type VisualizationSettings } from "../components/charts";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Card, CardBody } from "../ui/Card/Card";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function PublicCardPage() {
	const { uuid } = useParams();
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [card, setCard] = useState<LoadState<PublicCardDetail>>({ state: "loading" });
	const [query, setQuery] = useState<LoadState<CardQueryResponse>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		if (!uuid) return;
		analyticsApi
			.getPublicCard(uuid)
			.then((v) => {
				if (cancelled) return;
				setCard({ state: "loaded", value: v });
			})
			.catch((e) => {
				if (cancelled) return;
				setCard({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [uuid]);

	useEffect(() => {
		let cancelled = false;
		if (!uuid) return;
		analyticsApi
			.queryPublicCard(uuid)
			.then((v) => {
				if (cancelled) return;
				setQuery({ state: "loaded", value: v });
			})
			.catch((e) => {
				if (cancelled) return;
				setQuery({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [uuid]);

	return (
		<PageContainer>
			<PageHeader
				title={card.state === "loaded" ? card.value.name ?? "-" : t(locale, "loading")}
				breadcrumbs={
					<Breadcrumb items={[
						{ label: t(locale, "nav.analyze"), href: "/analyze" },
						{ label: "Share" }
					]} />
				}
			/>

			{card.state === "error" && <ErrorNotice locale={locale} error={card.error} />}
			{query.state === "error" && <ErrorNotice locale={locale} error={query.error} />}

			<Card style={{ marginBottom: "var(--spacing-lg)" }}>
				<CardBody>
					<p className="text-muted" style={{ margin: 0 }}>
						{t(locale, "share.note")}
					</p>
				</CardBody>
			</Card>

			{query.state === "loading" && (
				<Card>
					<CardBody>
						<div className="loading-container" style={{ padding: "var(--spacing-xl)" }}>
							<Spinner size="lg" />
						</div>
					</CardBody>
				</Card>
			)}
			{query.state === "loaded" && (
				<Card>
					<CardBody>
						{Array.isArray(query.value?.data?.cols) && Array.isArray(query.value?.data?.rows) ? (
							<ChartRenderer
								data={{
									cols: (query.value.data?.cols ?? []) as { name: string; display_name?: string; base_type?: string }[],
									rows: (query.value.data?.rows ?? []) as any[][]
								}}
								display={card.state === "loaded" ? (card.value.display as VisualizationType) || "table" : "table"}
								settings={card.state === "loaded" ? (card.value.visualization_settings as VisualizationSettings) || {} : {}}
							/>
						) : (
							<EmptyState title={t(locale, "common.empty")} />
						)}
					</CardBody>
				</Card>
			)}
		</PageContainer>
	);
}

