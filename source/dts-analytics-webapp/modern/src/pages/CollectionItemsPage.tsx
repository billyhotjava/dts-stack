import { Link, useParams } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type CollectionItem } from "../api/analyticsApi";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function CollectionItemsPage() {
	const { id } = useParams();
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<CollectionItem[]>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.getCollectionItems(id ?? "root")
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

	return (
		<div className="page">
			<h1 className="pageTitle">{t(locale, "collections.itemsTitle")}</h1>
			<div className="pageSub">{t(locale, "collections.itemsSubtitle") + " " + (id ?? "root")}</div>

			<div style={{ height: 16 }} />

			<div className="card">
				{state.state === "loading" && <div>{t(locale, "loading")}</div>}
				{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
				{state.state === "loaded" && state.value.length === 0 ? (
					<EmptyState title={t(locale, "common.empty")} />
				) : null}
				{state.state === "loaded" && state.value.length > 0 && (
					<table className="table">
						<thead>
							<tr>
								<th>{t(locale, "common.type")}</th>
								<th>{t(locale, "common.name")}</th>
								<th>{t(locale, "common.id")}</th>
							</tr>
						</thead>
						<tbody>
							{state.value.map((item) => (
								<tr key={`${item.model}:${item.id}`}>
									<td>
										<span className="tag">{item.model}</span>
									</td>
									<td>
										{item.model === "dashboard" ? (
											<Link to={`/dashboards/${item.id}`}>{item.name ?? "-"}</Link>
										) : (
											<Link to={`/questions/${item.id}`}>{item.name ?? "-"}</Link>
										)}
									</td>
									<td>{item.id}</td>
								</tr>
							))}
						</tbody>
					</table>
				)}
			</div>
		</div>
	);
}
