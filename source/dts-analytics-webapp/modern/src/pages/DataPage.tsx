import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { analyticsApi, type DatabaseListItem } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function DataPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<DatabaseListItem[]>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listDatabases()
			.then((r) => {
				if (cancelled) return;
				setState({ state: "loaded", value: r.data ?? [] });
			})
			.catch((e) => {
				if (cancelled) return;
				setState({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	return (
		<div className="page">
			<h1 className="pageTitle">{t(locale, "data.title")}</h1>
			<div className="pageSub">{t(locale, "data.subtitle")}</div>

			<div style={{ height: 16 }} />

			<div className="card">
				{state.state === "loading" && <div>{t(locale, "loading")}</div>}
				{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
				{state.state === "loaded" && (
					<table className="table">
						<thead>
							<tr>
								<th>{t(locale, "data.db")}</th>
								<th>{t(locale, "common.id")}</th>
								<th>Engine</th>
							</tr>
						</thead>
						<tbody>
							{state.value.map((db) => (
								<tr key={db.id}>
									<td>
										<Link to={`/data/${db.id}`}>{db.name ?? `db:${db.id}`}</Link>
									</td>
									<td>{db.id}</td>
									<td>{db.engine ?? "-"}</td>
								</tr>
							))}
						</tbody>
					</table>
				)}
			</div>
		</div>
	);
}

