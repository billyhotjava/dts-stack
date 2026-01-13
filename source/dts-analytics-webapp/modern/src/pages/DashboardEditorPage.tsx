import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import {
	analyticsApi,
	type CardListItem,
	type CollectionListItem,
	type DashboardCard,
	type DashboardDetail,
} from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

function toEditableDashcards(d: DashboardDetail): DashboardCard[] {
	const raw = d.ordered_cards;
	return Array.isArray(raw) ? raw : [];
}

export default function DashboardEditorPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const navigate = useNavigate();
	const params = useParams();
	const dashboardId = params.id ? String(params.id) : null;

	const [collections, setCollections] = useState<LoadState<CollectionListItem[]>>({ state: "loading" });
	const [cards, setCards] = useState<LoadState<CardListItem[]>>({ state: "loading" });
	const [dashboard, setDashboard] = useState<LoadState<DashboardDetail> | null>(dashboardId ? { state: "loading" } : null);

	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [collectionId, setCollectionId] = useState<number | null>(null);
	const [dashcards, setDashcards] = useState<DashboardCard[]>([]);
	const [selectedCardId, setSelectedCardId] = useState<number | null>(null);

	const [saveState, setSaveState] = useState<LoadState<DashboardDetail> | null>(null);

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listCollections()
			.then((r) => {
				if (cancelled) return;
				setCollections({ state: "loaded", value: r });
			})
			.catch((e) => {
				if (cancelled) return;
				setCollections({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listCards()
			.then((r) => {
				if (cancelled) return;
				setCards({ state: "loaded", value: r });
				if (!selectedCardId && r.length > 0) setSelectedCardId(r[0].id);
			})
			.catch((e) => {
				if (cancelled) return;
				setCards({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		let cancelled = false;
		if (!dashboardId) return;
		analyticsApi
			.getDashboard(dashboardId)
			.then((d) => {
				if (cancelled) return;
				setDashboard({ state: "loaded", value: d });
				setName(d.name ?? "");
				setDescription(d.description ?? "");
				setCollectionId(typeof d.collection_id === "number" ? d.collection_id : null);
				setDashcards(toEditableDashcards(d));
			})
			.catch((e) => {
				if (cancelled) return;
				setDashboard({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [dashboardId]);

	const addSelectedCard = () => {
		if (!selectedCardId) return;
		const next: DashboardCard = {
			id: 0,
			card_id: selectedCardId,
			row: 0,
			col: 0,
			size_x: 12,
			size_y: 6,
		};
		setDashcards((prev) => [...prev, next]);
	};

	const removeDashcardAt = (idx: number) => {
		setDashcards((prev) => prev.filter((_, i) => i !== idx));
	};

	const save = async () => {
		const trimmedName = name.trim();
		if (!trimmedName) return;
		setSaveState({ state: "loading" });
		try {
			let id = dashboardId;
			if (!id) {
				const created = await analyticsApi.createDashboard({
					name: trimmedName,
					description: description.trim() || null,
					collection_id: collectionId,
				});
				id = String(created.id);
			}

			const body = {
				dashboard: {
					id: Number.parseInt(id, 10),
					name: trimmedName,
					description: description.trim() || null,
					collection_id: collectionId,
				},
				dashcards: dashcards.map((dc, index) => ({
					id: dc.id && dc.id > 0 ? dc.id : undefined,
					card_id: dc.card_id,
					row: dc.row ?? 0,
					col: dc.col ?? 0,
					size_x: dc.size_x ?? 12,
					size_y: dc.size_y ?? 6,
					parameter_mappings: dc.parameter_mappings ?? [],
					visualization_settings: dc.visualization_settings ?? {},
					_seriesIndex: index,
				})),
			};
			const saved = await analyticsApi.saveDashboard(body);
			setSaveState({ state: "loaded", value: saved });
			navigate(`/dashboards/${saved.id}`, { replace: true });
		} catch (e) {
			setSaveState({ state: "error", error: e });
		}
	};

	return (
		<div className="page">
			<h1 className="pageTitle">
				{dashboardId ? `${t(locale, "dashboards.edit")} #${dashboardId}` : t(locale, "dashboards.new")}
			</h1>
			<div className="pageSub">
				<Link to="/dashboards">{t(locale, "nav.dashboards")}</Link>
			</div>

			<div style={{ height: 16 }} />

			{dashboard?.state === "error" && <ErrorNotice locale={locale} error={dashboard.error} />}
			{collections.state === "error" && <ErrorNotice locale={locale} error={collections.error} />}
			{cards.state === "error" && <ErrorNotice locale={locale} error={cards.error} />}
			{saveState?.state === "error" && <ErrorNotice locale={locale} error={saveState.error} />}

			<div className="card">
				<div className="row">
					<label style={{ flex: 1 }}>
						<div className="muted">{t(locale, "common.name")}</div>
						<input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="..." />
					</label>

					<label style={{ width: 260 }}>
						<div className="muted">{t(locale, "questions.collection")}</div>
						<select
							className="input"
							value={collectionId ?? ""}
							onChange={(e) => setCollectionId(e.target.value ? Number.parseInt(e.target.value, 10) || null : null)}
							disabled={collections.state !== "loaded"}
						>
							<option value="">{t(locale, "collections.title")} (root)</option>
							{collections.state === "loaded" &&
								collections.value
									.filter((c) => c.id !== "root")
									.map((c) => (
										<option key={String(c.id)} value={String(c.id)}>
											{c.name ?? String(c.id)}
										</option>
									))}
						</select>
					</label>
				</div>

				<div style={{ height: 12 }} />

				<label style={{ display: "block" }}>
					<div className="muted">Description</div>
					<input className="input" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="..." />
				</label>

				<div style={{ height: 12 }} />

				<div className="row">
					<button className="btn" type="button" onClick={save} disabled={!name.trim() || saveState?.state === "loading"}>
						{t(locale, "dashboards.save")}
					</button>
					{saveState?.state === "loading" && <span className="muted">{t(locale, "loading")}</span>}
				</div>
			</div>

			<div style={{ height: 16 }} />

			<div className="card">
				<div className="row" style={{ justifyContent: "space-between" }}>
					<strong>Cards</strong>
					<div className="row">
						<select
							className="input"
							value={selectedCardId ?? ""}
							onChange={(e) => setSelectedCardId(Number.parseInt(e.target.value, 10) || null)}
							disabled={cards.state !== "loaded"}
						>
							{cards.state === "loaded" &&
								cards.value.map((c) => (
									<option key={c.id} value={c.id}>
										{c.name ?? `card:${c.id}`}
									</option>
								))}
						</select>
						<button className="btn" type="button" onClick={addSelectedCard} disabled={!selectedCardId}>
							{t(locale, "dashboards.addCard")}
						</button>
					</div>
				</div>

				<div style={{ height: 12 }} />

				{dashcards.length === 0 && <div className="muted">—</div>}
				{dashcards.length > 0 && (
					<table className="table">
						<thead>
							<tr>
								<th>{t(locale, "common.id")}</th>
								<th>{t(locale, "common.name")}</th>
								<th>{t(locale, "dashboards.remove")}</th>
							</tr>
						</thead>
						<tbody>
							{dashcards.map((dc, idx) => {
								const cardName =
									typeof (dc as any)?.card?.name === "string"
										? String((dc as any).card.name)
										: cards.state === "loaded"
											? cards.value.find((c) => c.id === dc.card_id)?.name ?? `card:${dc.card_id ?? "-"}`
											: `card:${dc.card_id ?? "-"}`;
								return (
									<tr key={`${dc.id}:${idx}`}>
										<td>{dc.id && dc.id > 0 ? dc.id : "-"}</td>
										<td>{cardName}</td>
										<td>
											<button className="btn" type="button" onClick={() => removeDashcardAt(idx)}>
												{t(locale, "dashboards.remove")}
											</button>
										</td>
									</tr>
								);
							})}
						</tbody>
					</table>
				)}
			</div>
		</div>
	);
}

