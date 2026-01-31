import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import {
	analyticsApi,
	type CardListItem,
	type CollectionListItem,
	type DashboardCard,
	type DashboardDetail,
} from "../api/analyticsApi";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { Card, CardHeader, CardBody, CardFooter } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { Input } from "../ui/Input/Input";
import { NativeSelect } from "../ui/Input/Select";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
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

	// Icons
	const SaveIcon = () => (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
			<polyline points="17 21 17 13 7 13 7 21" />
			<polyline points="7 3 7 8 15 8" />
		</svg>
	);

	const PlusIcon = () => (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<path d="M5 12h14" />
			<path d="M12 5v14" />
		</svg>
	);

	const TrashIcon = () => (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<path d="M3 6h18" />
			<path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
			<path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
		</svg>
	);

	const collectionOptions = [
		{ value: "", label: `${t(locale, "collections.title")} (root)` },
		...(collections.state === "loaded"
			? collections.value
				.filter((c) => c.id !== "root")
				.map((c) => ({ value: String(c.id), label: c.name ?? String(c.id) }))
			: [])
	];

	const cardOptions = cards.state === "loaded"
		? cards.value.map((c) => ({ value: String(c.id), label: c.name ?? `card:${c.id}` }))
		: [];

	return (
		<PageContainer>
			<PageHeader
				title={dashboardId ? `${t(locale, "dashboards.edit")} #${dashboardId}` : t(locale, "dashboards.new")}
				breadcrumbs={
					<Breadcrumb items={[
						{ label: t(locale, "nav.dashboards"), href: "/dashboards" },
						{ label: dashboardId ? `#${dashboardId}` : t(locale, "dashboards.new") }
					]} />
				}
			/>

			{dashboard?.state === "error" && <ErrorNotice locale={locale} error={dashboard.error} />}
			{collections.state === "error" && <ErrorNotice locale={locale} error={collections.error} />}
			{cards.state === "error" && <ErrorNotice locale={locale} error={cards.error} />}
			{saveState?.state === "error" && <ErrorNotice locale={locale} error={saveState.error} />}

			<Card style={{ marginBottom: "var(--spacing-lg)" }}>
				<CardHeader title={t(locale, "dashboards.settings")} />
				<CardBody>
					<div className="form-grid" style={{ gridTemplateColumns: "1fr 260px" }}>
						<Input
							label={t(locale, "common.name")}
							value={name}
							onChange={(e) => setName(e.target.value)}
							placeholder="My Dashboard"
						/>

						<NativeSelect
							label={t(locale, "questions.collection")}
							value={collectionId ? String(collectionId) : ""}
							onChange={(e) => setCollectionId(e.target.value ? Number.parseInt(e.target.value, 10) || null : null)}
							options={collectionOptions}
							disabled={collections.state !== "loaded"}
						/>
					</div>

					<div style={{ marginTop: "var(--spacing-md)" }}>
						<Input
							label={t(locale, "common.description")}
							value={description}
							onChange={(e) => setDescription(e.target.value)}
							placeholder={t(locale, "common.descPlaceholder")}
						/>
					</div>
				</CardBody>
				<CardFooter>
					<Button
						variant="primary"
						icon={<SaveIcon />}
						onClick={save}
						disabled={!name.trim() || saveState?.state === "loading"}
						loading={saveState?.state === "loading"}
					>
						{t(locale, "dashboards.save")}
					</Button>
				</CardFooter>
			</Card>

			<Card>
				<CardHeader
					title={t(locale, "common.cards")}
					action={
						<Badge variant="default">{dashcards.length}</Badge>
					}
				/>
				<CardBody>
					<div style={{ display: "flex", gap: "var(--spacing-sm)", alignItems: "flex-end", marginBottom: "var(--spacing-md)" }}>
						<div style={{ flex: 1, maxWidth: 300 }}>
							<NativeSelect
								label={t(locale, "dashboards.selectCard")}
								value={selectedCardId ? String(selectedCardId) : ""}
								onChange={(e) => setSelectedCardId(Number.parseInt(e.target.value, 10) || null)}
								options={cardOptions}
								disabled={cards.state !== "loaded"}
							/>
						</div>
						<Button
							variant="secondary"
							icon={<PlusIcon />}
							onClick={addSelectedCard}
							disabled={!selectedCardId}
						>
							{t(locale, "dashboards.addCard")}
						</Button>
					</div>

					{dashcards.length === 0 && (
						<EmptyState title={t(locale, "common.empty")} description={t(locale, "dashboards.noCards")} />
					)}

					{dashcards.length > 0 && (
						<table className="table">
							<thead>
								<tr>
									<th>{t(locale, "common.id")}</th>
									<th>{t(locale, "common.name")}</th>
									<th style={{ width: 120 }}>{t(locale, "common.actions")}</th>
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
											<td>
												{dc.id && dc.id > 0 ? (
													<Badge variant="default" size="sm">{String(dc.id)}</Badge>
												) : (
													<span className="text-muted">-</span>
												)}
											</td>
											<td>{cardName}</td>
											<td>
												<Button
													variant="tertiary"
													size="sm"
													icon={<TrashIcon />}
													onClick={() => removeDashcardAt(idx)}
												>
													{t(locale, "dashboards.remove")}
												</Button>
											</td>
										</tr>
									);
								})}
							</tbody>
						</table>
					)}
				</CardBody>
			</Card>

			<style>{`
				.form-grid {
					display: grid;
					gap: var(--spacing-md);
				}

				@media (max-width: 768px) {
					.form-grid {
						grid-template-columns: 1fr !important;
					}
				}
			`}</style>
		</PageContainer>
	);
}

