import { useParams } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type DashboardDetail } from "../api/analyticsApi";
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

