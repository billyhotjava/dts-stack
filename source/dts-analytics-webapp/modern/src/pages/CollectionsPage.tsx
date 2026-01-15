import { Link } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CollectionListItem } from "../api/analyticsApi";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function CollectionsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<CollectionListItem[]>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listCollections()
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
			<h1 className="pageTitle">{t(locale, "collections.title")}</h1>
			<div className="pageSub">{t(locale, "collections.subtitle")}</div>

				<div style={{ height: 16 }} />

				{state.state === "loading" && <div className="card">{t(locale, "loading")}</div>}
				{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
				{state.state === "loaded" && state.value.length === 0 && <EmptyState title={t(locale, "common.empty")} />}
				{state.state === "loaded" && state.value.length > 0 && (
					<div className="card">
						<table className="table">
							<thead>
								<tr>
									<th>{t(locale, "common.name")}</th>
									<th>{t(locale, "common.id")}</th>
								</tr>
							</thead>
							<tbody>
								{state.value.map((c) => (
									<tr key={String(c.id)}>
										<td>
											<Link to={`/collections/${encodeURIComponent(String(c.id))}`}>{c.name ?? "-"}</Link>
										</td>
										<td>{String(c.id)}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				)}
			</div>
		);
	}
