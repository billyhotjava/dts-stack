import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { analyticsApi, type CardQueryResponse, type PublicCardDetail } from "../api/analyticsApi";
import { DataTable } from "../components/DataTable";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
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
		<div className="page">
			<h1 className="pageTitle">{card.state === "loaded" ? card.value.name ?? "-" : t(locale, "loading")}</h1>
			<div className="pageSub">
				<Link to="/analyze">{t(locale, "nav.analyze")}</Link>
				<span className="muted"> · </span>
				<span className="muted">Share</span>
			</div>

			<div style={{ height: 16 }} />

			{card.state === "error" && <ErrorNotice locale={locale} error={card.error} />}
			{query.state === "error" && <ErrorNotice locale={locale} error={query.error} />}

			<div className="card">
				<div className="muted">
					{t(locale, "share.note")}
				</div>
			</div>

			<div style={{ height: 16 }} />

			{query.state === "loading" && <div className="card">{t(locale, "loading")}</div>}
			{query.state === "loaded" &&
				(Array.isArray(query.value?.data?.cols) && Array.isArray(query.value?.data?.rows) ? (
					<DataTable cols={query.value.data?.cols ?? []} rows={query.value.data?.rows ?? []} maxRows={200} />
				) : (
					<EmptyState title={t(locale, "common.empty")} />
				))}
		</div>
	);
}

