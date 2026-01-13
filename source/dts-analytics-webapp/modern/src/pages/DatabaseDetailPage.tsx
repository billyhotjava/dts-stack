import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { analyticsApi, type DatabaseMetadataResponse } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function DatabaseDetailPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const { dbId } = useParams();
	const [state, setState] = useState<LoadState<DatabaseMetadataResponse>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		if (!dbId) return;
		analyticsApi
			.getDatabaseMetadata(dbId)
			.then((v) => {
				if (cancelled) return;
				setState({ state: "loaded", value: v });
			})
			.catch((e) => {
				if (cancelled) return;
				setState({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [dbId]);

	const tables: any[] = useMemo(() => {
		if (state.state !== "loaded") return [];
		const v: any = state.value;
		return Array.isArray(v?.tables) ? v.tables : [];
	}, [state]);

	return (
		<div className="page">
			<h1 className="pageTitle">
				{t(locale, "data.db")} #{dbId}
			</h1>
			<div className="pageSub">
				<Link to="/data">{t(locale, "data.title")}</Link>
			</div>

			<div style={{ height: 16 }} />

			<div className="card">
				{state.state === "loading" && <div>{t(locale, "loading")}</div>}
				{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
				{state.state === "loaded" && (
					<>
						<div className="row">
							<strong>{t(locale, "data.tables")}</strong>
							<span className="muted">({tables.length})</span>
						</div>
						<div style={{ height: 12 }} />
						<table className="table">
							<thead>
								<tr>
									<th>{t(locale, "common.name")}</th>
									<th>{t(locale, "common.id")}</th>
									<th>Schema</th>
								</tr>
							</thead>
							<tbody>
								{tables.map((tb) => (
									<tr key={String(tb?.id ?? tb?.name ?? Math.random())}>
										<td>{tb?.name ?? "-"}</td>
										<td>{String(tb?.id ?? "-")}</td>
										<td>{tb?.schema_name ?? "-"}</td>
									</tr>
								))}
							</tbody>
						</table>
						<div style={{ height: 12 }} />
						<details>
							<summary className="muted">Raw JSON</summary>
							<pre style={{ whiteSpace: "pre-wrap", fontSize: 12, margin: "12px 0 0" }}>
								{JSON.stringify(state.value, null, 2)}
							</pre>
						</details>
					</>
				)}
			</div>
		</div>
	);
}

