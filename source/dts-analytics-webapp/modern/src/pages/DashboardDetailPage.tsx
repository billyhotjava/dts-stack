import { Link, useParams } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type DashboardCard, type DashboardDetail } from "../api/analyticsApi";
import { normalizeLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: string };

export default function DashboardDetailPage() {
	const { id } = useParams();
	const locale: Locale = useMemo(() => normalizeLocale(navigator.language), []);
	const [state, setState] = useState<LoadState<DashboardDetail>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		if (!id) return;
		analyticsApi
			.getDashboard(id)
			.then((value) => {
				if (cancelled) return;
				setState({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setState({ state: "error", error: String(e?.message ?? e) });
			});
		return () => {
			cancelled = true;
		};
	}, [id]);

	return (
		<div className="page">
			{state.state === "loading" && <div>{t(locale, "loading")}</div>}
			{state.state === "error" && <div>{t(locale, "error") + ": " + state.error}</div>}
			{state.state === "loaded" && (
				<>
					<h1 className="pageTitle">{state.value.name ?? "-"}</h1>
					<div className="pageSub">{state.value.description ?? ""}</div>
					<div style={{ height: 16 }} />
					<div className="card">
						<div className="muted">Dashcards</div>
						<div style={{ height: 8 }} />
						<table className="table">
							<thead>
								<tr>
									<th>{t(locale, "common.type")}</th>
									<th>{t(locale, "common.name")}</th>
									<th>{t(locale, "common.id")}</th>
								</tr>
							</thead>
							<tbody>
								{(state.value.ordered_cards ?? []).map((dc: DashboardCard) => {
									const card = dc.card as any;
									const cardId = dc.card_id ?? (card && typeof card.id === "number" ? card.id : undefined);
									const name = (card && typeof card.name === "string" && card.name) || `Card ${cardId ?? "-"}`;
									return (
										<tr key={dc.id}>
											<td>
												<span className="tag">card</span>
											</td>
											<td>{cardId ? <Link to={`/questions/${cardId}`}>{String(name)}</Link> : String(name)}</td>
											<td>{String(cardId ?? "-")}</td>
										</tr>
									);
								})}
							</tbody>
						</table>
						{(state.value.ordered_cards ?? []).length === 0 && <div className="muted">No dashcards.</div>}
					</div>
					<div style={{ height: 16 }} />
					<div className="card">
						<div className="muted">{t(locale, "dashboards.detailNote")}</div>
						<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: "12px 0 0" }}>
							{JSON.stringify(state.value, null, 2)}
						</pre>
					</div>
				</>
			)}
		</div>
	);
}
