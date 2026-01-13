import { Link } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type DashboardListItem } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function DashboardsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<DashboardListItem[]>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listDashboards()
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

	return (
		<div className="page">
			<h1 className="pageTitle">{t(locale, "dashboards.title")}</h1>
			<div className="pageSub">{t(locale, "dashboards.subtitle")}</div>

			<div style={{ height: 16 }} />

			<div className="card">
				<div className="row">
					<Link className="btn" to="/dashboards/new">
						{t(locale, "dashboards.new")}
					</Link>
				</div>
			</div>

			<div style={{ height: 16 }} />

			<div className="card">
				{state.state === "loading" && <div>{t(locale, "loading")}</div>}
				{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
				{state.state === "loaded" && (
					<table className="table">
						<thead>
							<tr>
								<th>{t(locale, "common.name")}</th>
								<th>{t(locale, "common.id")}</th>
							</tr>
						</thead>
						<tbody>
							{state.value.map((d) => (
								<tr key={String(d.id)}>
									<td>
										<Link to={`/dashboards/${d.id}`}>{d.name ?? "-"}</Link>
									</td>
									<td>{d.id}</td>
								</tr>
							))}
						</tbody>
					</table>
				)}
			</div>
		</div>
	);
}
