import { useParams } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CardDetail, type CardQueryResponse } from "../api/analyticsApi";
import { DataTable } from "../components/DataTable";
import { normalizeLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: string };

export default function CardDetailPage() {
	const { id } = useParams();
	const locale: Locale = useMemo(() => normalizeLocale(navigator.language), []);
	const [state, setState] = useState<LoadState<CardDetail>>({ state: "loading" });
	const [queryState, setQueryState] = useState<LoadState<CardQueryResponse> | null>(null);
	const [showRaw, setShowRaw] = useState(false);

	useEffect(() => {
		let cancelled = false;
		if (!id) return;
		analyticsApi
			.getCard(id)
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

	useEffect(() => {
		let cancelled = false;
		if (!id) return;
		setQueryState({ state: "loading" });
		analyticsApi
			.queryCard(id)
			.then((value) => {
				if (cancelled) return;
				setQueryState({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setQueryState({ state: "error", error: String(e?.message ?? e) });
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
						<div className="muted">{t(locale, "questions.detailNote")}</div>
						<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: "12px 0 0" }}>
							{JSON.stringify(state.value, null, 2)}
						</pre>
					</div>
					<div style={{ height: 16 }} />
					<div className="card">
						<div className="row" style={{ justifyContent: "space-between" }}>
							<div className="muted">{t(locale, "questions.queryResult")}</div>
							<button className="btn" onClick={() => setShowRaw((v) => !v)} type="button">
								{t(locale, "questions.queryRaw")}
							</button>
						</div>
						{queryState?.state === "loading" && <div style={{ marginTop: 8 }}>{t(locale, "loading")}</div>}
						{queryState?.state === "error" && <div style={{ marginTop: 8 }}>{t(locale, "error") + ": " + queryState.error}</div>}
						{queryState?.state === "loaded" && (
							<div style={{ marginTop: 12 }}>
								{queryState.value?.data?.native_form?.query && (
									<div style={{ marginBottom: 12 }}>
										<div className="muted">{t(locale, "questions.querySql")}</div>
										<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: "8px 0 0" }}>
											{String(queryState.value.data.native_form.query)}
										</pre>
									</div>
								)}

								{Array.isArray(queryState.value?.data?.cols) && Array.isArray(queryState.value?.data?.rows) ? (
									<DataTable cols={queryState.value.data?.cols ?? []} rows={queryState.value.data?.rows ?? []} maxRows={200} />
								) : (
									<div className="muted">No tabular result.</div>
								)}

								{showRaw && (
									<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: "12px 0 0" }}>
										{JSON.stringify(queryState.value, null, 2)}
									</pre>
								)}
							</div>
						)}
					</div>
				</>
			)}
		</div>
	);
}
