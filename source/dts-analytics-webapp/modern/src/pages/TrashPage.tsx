import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { analyticsApi, type TrashResponse } from "../api/analyticsApi";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function TrashPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<TrashResponse>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.getTrash()
			.then((value) => {
				if (cancelled) return;
				setState({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setState({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	const items = useMemo(() => {
		if (state.state !== "loaded") return [];
		return [...(state.value.dashboards ?? []), ...(state.value.cards ?? [])];
	}, [state]);

	return (
		<div className="page">
			<h1 className="pageTitle">{t(locale, "trash.title")}</h1>
			<div className="pageSub">{t(locale, "trash.subtitle")}</div>

			<div style={{ height: 16 }} />

			{state.state === "loading" && <div className="card">{t(locale, "loading")}</div>}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && items.length === 0 && <EmptyState title={t(locale, "common.empty")} />}
			{state.state === "loaded" && items.length > 0 && (
				<div className="card">
					<table className="table">
						<thead>
							<tr>
								<th>{t(locale, "common.type")}</th>
								<th>{t(locale, "common.name")}</th>
								<th>{t(locale, "common.id")}</th>
							</tr>
						</thead>
						<tbody>
							{items.map((it) => (
								<tr key={`${it.model}:${it.id}`}>
									<td>
										<span className="tag">{String(it.model)}</span>
									</td>
									<td>
										{it.model === "dashboard" ? (
											<Link to={`/dashboards/${it.id}`}>{it.name ?? "-"}</Link>
										) : it.model === "card" ? (
											<Link to={`/questions/${it.id}`}>{it.name ?? "-"}</Link>
										) : (
											<span>{it.name ?? "-"}</span>
										)}
									</td>
									<td>{it.id}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</div>
	);
}
