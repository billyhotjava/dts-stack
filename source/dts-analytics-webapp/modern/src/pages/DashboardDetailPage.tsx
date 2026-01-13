import { Link, useParams } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type DashboardCard, type DashboardDetail, type DashboardQueryResponse } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { DataTable } from "../components/DataTable";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

type DashboardParam = {
	id: string;
	name?: string;
	slug?: string;
	type?: string;
};

export default function DashboardDetailPage() {
	const { id } = useParams();
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<DashboardDetail>>({ state: "loading" });
	const [paramOptions, setParamOptions] = useState<Record<string, string[]>>({});
	const [paramValues, setParamValues] = useState<Record<string, string>>({});
	const [dashcardResults, setDashcardResults] = useState<Record<number, LoadState<DashboardQueryResponse>>>({});
	const [showRaw, setShowRaw] = useState(false);

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
				setState({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [id]);

	const dashboardParams: DashboardParam[] = useMemo(() => {
		if (state.state !== "loaded") return [];
		const raw = state.value.parameters;
		if (!Array.isArray(raw)) return [];
		return raw
			.map((p: any) => ({
				id: String(p?.id ?? ""),
				name: typeof p?.name === "string" ? p.name : undefined,
				slug: typeof p?.slug === "string" ? p.slug : undefined,
				type: typeof p?.type === "string" ? p.type : undefined,
			}))
			.filter((p) => p.id);
	}, [state]);

	const dashcards: DashboardCard[] = useMemo(() => {
		if (state.state !== "loaded") return [];
		return Array.isArray(state.value.ordered_cards) ? (state.value.ordered_cards as DashboardCard[]) : [];
	}, [state]);

	useEffect(() => {
		let cancelled = false;
		if (!id) return;
		if (dashboardParams.length === 0) return;

		(async () => {
			const next: Record<string, string[]> = {};
			for (const p of dashboardParams) {
				try {
					next[p.id] = await analyticsApi.listDashboardParamValues(id, p.id);
				} catch {
					next[p.id] = [];
				}
			}
			if (!cancelled) setParamOptions(next);
		})();

		return () => {
			cancelled = true;
		};
	}, [id, dashboardParams]);

	const queryParametersPayload = useMemo(() => {
		const out: any[] = [];
		for (const p of dashboardParams) {
			const value = (paramValues[p.id] ?? "").trim();
			if (!value) continue;
			const tagName = (p.slug || p.name || p.id).trim();
			if (!tagName) continue;
			out.push({
				type: p.type || "category",
				target: ["variable", ["template-tag", tagName]],
				value,
			});
		}
		return out;
	}, [dashboardParams, paramValues]);

	useEffect(() => {
		let cancelled = false;
		if (!id) return;
		if (dashcards.length === 0) return;

		(async () => {
			const next: Record<number, LoadState<DashboardQueryResponse>> = {};
			for (const dc of dashcards) {
				next[dc.id] = { state: "loading" };
			}
			if (!cancelled) setDashcardResults(next);

			for (const dc of dashcards) {
				const card: any = dc.card as any;
				const cardId = dc.card_id ?? (card && typeof card.id === "number" ? card.id : undefined);
				if (!cardId) {
					next[dc.id] = { state: "error", error: new Error("Missing card_id") };
					continue;
				}
				try {
					const value = await analyticsApi.queryDashcard(id, dc.id, cardId, { parameters: queryParametersPayload });
					next[dc.id] = { state: "loaded", value };
				} catch (e) {
					next[dc.id] = { state: "error", error: e };
				}
				if (!cancelled) setDashcardResults({ ...next });
			}
		})();

		return () => {
			cancelled = true;
		};
	}, [id, dashcards, queryParametersPayload]);

	return (
		<div className="page">
			{state.state === "loading" && <div>{t(locale, "loading")}</div>}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && (
				<>
					<div className="row" style={{ justifyContent: "space-between", alignItems: "baseline" }}>
						<h1 className="pageTitle">{state.value.name ?? "-"}</h1>
						<Link className="btn" to={`/dashboards/${encodeURIComponent(String(state.value.id))}/edit`}>
							{t(locale, "dashboards.edit")}
						</Link>
					</div>
					<div className="pageSub">{state.value.description ?? ""}</div>
					<div style={{ height: 16 }} />

					{dashboardParams.length > 0 && (
						<div className="card">
							<div className="row" style={{ justifyContent: "space-between" }}>
								<div className="muted">Filters</div>
								<button className="btn" type="button" onClick={() => setParamValues({})}>
									Clear
								</button>
							</div>
							<div style={{ height: 12 }} />
							<div className="row" style={{ flexWrap: "wrap", gap: 12 }}>
								{dashboardParams.map((p) => (
									<label key={p.id} style={{ display: "flex", flexDirection: "column", gap: 6, minWidth: 240 }}>
										<span className="muted" style={{ fontSize: 12 }}>
											{p.name || p.slug || p.id}
										</span>
										<select
											value={paramValues[p.id] ?? ""}
											onChange={(e) => setParamValues((prev) => ({ ...prev, [p.id]: e.target.value }))}
											style={{ padding: "10px 12px", borderRadius: 10, border: "1px solid rgba(0,0,0,0.12)" }}
										>
											<option value="">(All)</option>
											{(paramOptions[p.id] ?? []).map((v) => (
												<option key={String(v)} value={String(v)}>
													{String(v)}
												</option>
											))}
										</select>
									</label>
								))}
							</div>
						</div>
					)}

					<div style={{ height: 16 }} />

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
							const card: any = dc.card as any;
							const cardId = dc.card_id ?? (card && typeof card.id === "number" ? card.id : undefined);
							const name = (card && typeof card.name === "string" && card.name) || `Card ${cardId ?? "-"}`;
							const gridColumn =
								typeof dc.col === "number" && typeof dc.size_x === "number" ? `${dc.col + 1} / span ${dc.size_x}` : "auto";
							const gridRow =
								typeof dc.row === "number" && typeof dc.size_y === "number" ? `${dc.row + 1} / span ${dc.size_y}` : "auto";
							const result = dashcardResults[dc.id];

							return (
								<div key={dc.id} className="card" style={{ gridColumn, gridRow, overflow: "hidden" }}>
									<div className="row" style={{ justifyContent: "space-between" }}>
										<div style={{ fontWeight: 600 }}>{cardId ? <Link to={`/questions/${cardId}`}>{String(name)}</Link> : String(name)}</div>
										<span className="tag">card</span>
									</div>
									<div style={{ height: 10 }} />
									{!result || result.state === "loading" ? (
										<div>{t(locale, "loading")}</div>
									) : result.state === "error" ? (
										<ErrorNotice locale={locale} error={result.error} />
									) : (
										<DataTable cols={result.value.data?.cols ?? []} rows={result.value.data?.rows ?? []} maxRows={50} />
									)}
								</div>
							);
						})}
					</div>

					<div style={{ height: 16 }} />

					<div className="card">
						<div className="row" style={{ justifyContent: "space-between" }}>
							<div className="muted">{t(locale, "dashboards.detailNote")}</div>
							<button className="btn" type="button" onClick={() => setShowRaw((v) => !v)}>
								{showRaw ? "Hide JSON" : "Show JSON"}
							</button>
						</div>
						{showRaw && (
							<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: "12px 0 0" }}>
								{JSON.stringify(state.value, null, 2)}
							</pre>
						)}
					</div>
				</>
			)}
		</div>
	);
}
