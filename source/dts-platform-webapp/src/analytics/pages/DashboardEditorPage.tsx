import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import type { Layout } from "react-grid-layout";
import {
	analyticsApi,
	type CardListItem,
	type CollectionListItem,
	type DashboardCard,
	type DashboardDetail,
	type DashboardQueryResponse,
} from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { PageContainer } from "../components/PageContainer/PageContainer";
import { Button, Input, Select, Modal, message, Spin } from "antd";
import {
	ArrowLeftOutlined,
	PlusOutlined,
	SaveOutlined,
	EyeOutlined,
	EditOutlined,
} from "@ant-design/icons";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import { useDashboardCrossFilter } from "../hooks/useDashboardCrossFilter";
import { useDrillFilter } from "../hooks/useDrillFilter";
import { DashboardEditorGrid } from "./dashboard/DashboardEditorGrid";
import { DashboardFilterBar, type DashboardParameter } from "./dashboard/DashboardFilterBar";
import { CardPickerModal } from "./dashboard/CardPickerModal";
import type { SeriesClickParams } from "../components/charts";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

function toEditableDashcards(d: DashboardDetail): DashboardCard[] {
	const raw = d.ordered_cards;
	return Array.isArray(raw) ? raw : [];
}

function toDashboardParams(d: DashboardDetail): DashboardParameter[] {
	const raw = d.parameters;
	if (!Array.isArray(raw)) return [];
	return raw
		.map((p: any) => ({
			id: String(p?.id ?? ""),
			name: typeof p?.name === "string" ? p.name : undefined,
			slug: typeof p?.slug === "string" ? p.slug : undefined,
			type: typeof p?.type === "string" ? p.type : undefined,
		}))
		.filter((p) => p.id);
}

export default function DashboardEditorPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const navigate = useNavigate();
	const params = useParams();
	const dashboardId = params.id ? String(params.id) : null;

	// Collections & cards for picker
	const [collections, setCollections] = useState<LoadState<CollectionListItem[]>>({ state: "loading" });
	const [allCards, setAllCards] = useState<LoadState<CardListItem[]>>({ state: "loading" });
	const [dashboard, setDashboard] = useState<LoadState<DashboardDetail> | null>(dashboardId ? { state: "loading" } : null);

	// Dashboard state
	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [collectionId, setCollectionId] = useState<number | null>(null);
	const [dashcards, setDashcards] = useState<DashboardCard[]>([]);
	const [parameters, setParameters] = useState<DashboardParameter[]>([]);
	const [paramValues, setParamValues] = useState<Record<string, string>>({});
	const [paramOptions, setParamOptions] = useState<Record<string, string[]>>({});
	const [isEditing, setIsEditing] = useState(true);
	const [saveState, setSaveState] = useState<LoadState<DashboardDetail> | null>(null);
	const [cardPickerOpen, setCardPickerOpen] = useState(false);

	// Card query results
	const [cardResults, setCardResults] = useState<Record<number, LoadState<DashboardQueryResponse>>>({});

	// Cross-filter and drill
	const crossFilter = useDashboardCrossFilter();
	const drill = useDrillFilter();

	// Fetch collections
	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listCollections()
			.then((r) => !cancelled && setCollections({ state: "loaded", value: r }))
			.catch((e) => !cancelled && setCollections({ state: "error", error: e }));
		return () => { cancelled = true; };
	}, []);

	// Fetch all cards
	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.listCards()
			.then((r) => !cancelled && setAllCards({ state: "loaded", value: r }))
			.catch((e) => !cancelled && setAllCards({ state: "error", error: e }));
		return () => { cancelled = true; };
	}, []);

	// Fetch dashboard if editing existing
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
				setParameters(toDashboardParams(d));
			})
			.catch((e) => !cancelled && setDashboard({ state: "error", error: e }));
		return () => { cancelled = true; };
	}, [dashboardId]);

	// Fetch parameter options
	useEffect(() => {
		let cancelled = false;
		if (!dashboardId || parameters.length === 0) return;
		(async () => {
			const next: Record<string, string[]> = {};
			for (const p of parameters) {
				try {
					next[p.id] = await analyticsApi.listDashboardParamValues(dashboardId, p.id);
				} catch {
					next[p.id] = [];
				}
			}
			if (!cancelled) setParamOptions(next);
		})();
		return () => { cancelled = true; };
	}, [dashboardId, parameters]);

	// Build query parameters payload from param values
	const queryParametersPayload = useMemo(() => {
		const out: any[] = [];
		for (const p of parameters) {
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
	}, [parameters, paramValues]);

	// Query all dashcards when they change or params change or cross-filter changes
	const queryGenRef = useRef(0);
	useEffect(() => {
		const gen = ++queryGenRef.current;
		if (dashcards.length === 0) {
			setCardResults({});
			return;
		}

		const next: Record<number, LoadState<DashboardQueryResponse>> = {};
		for (const dc of dashcards) {
			next[dc.id] = { state: "loading" };
		}
		setCardResults({ ...next });

		(async () => {
			for (const dc of dashcards) {
				if (gen !== queryGenRef.current) return;
				const card: any = dc.card as any;
				const cardId = dc.card_id ?? (card && typeof card.id === "number" ? card.id : undefined);
				if (!cardId) {
					next[dc.id] = { state: "error", error: new Error("Missing card_id") };
					continue;
				}

				// Build params with cross-filter
				const params = crossFilter.buildCrossFilterParams(dc.id, queryParametersPayload);

				try {
					let value: DashboardQueryResponse;
					if (dashboardId) {
						value = await analyticsApi.queryDashcard(dashboardId, dc.id, cardId, { parameters: params });
					} else {
						value = await analyticsApi.queryCard(cardId, { parameters: params });
					}
					next[dc.id] = { state: "loaded", value };
				} catch (e) {
					next[dc.id] = { state: "error", error: e };
				}
				if (gen === queryGenRef.current) {
					setCardResults({ ...next });
				}
			}
		})();
	}, [dashcards, dashboardId, queryParametersPayload, crossFilter.activeFilter]);

	// Layout change handler
	const handleLayoutChange = useCallback((newLayout: Layout[]) => {
		setDashcards((prev) => {
			const updated = [...prev];
			for (const item of newLayout) {
				const idx = updated.findIndex((dc) => String(dc.id || `new-${prev.indexOf(dc)}`) === item.i);
				if (idx >= 0) {
					updated[idx] = {
						...updated[idx],
						col: item.x,
						row: item.y,
						size_x: item.w,
						size_y: item.h,
					};
				}
			}
			return updated;
		});
	}, []);

	// Remove card
	const handleRemoveCard = useCallback((index: number) => {
		setDashcards((prev) => prev.filter((_, i) => i !== index));
	}, []);

	// Add cards from picker
	const handleAddCards = useCallback((cards: CardListItem[]) => {
		setDashcards((prev) => {
			let maxBottom = 0;
			for (const dc of prev) {
				const bottom = (dc.row ?? 0) + (dc.size_y ?? 4);
				if (bottom > maxBottom) maxBottom = bottom;
			}

			const newCards: DashboardCard[] = cards.map((c, i) => ({
				id: -(Date.now() + i), // Negative temp id
				card_id: c.id,
				row: maxBottom,
				col: (i * 6) % 12,
				size_x: 6,
				size_y: 4,
				parameter_mappings: [],
				visualization_settings: {},
				card: c,
			}));

			// Adjust row for cards that would overlap
			for (let i = 1; i < newCards.length; i++) {
				if (newCards[i].col === 0 && i > 0) {
					maxBottom += 4;
					newCards[i] = { ...newCards[i], row: maxBottom };
				}
			}

			return [...prev, ...newCards];
		});
	}, []);

	// Series click handler (for cross-filter)
	const handleSeriesClick = useCallback(
		(dashcardId: number, params: SeriesClickParams, _event?: React.MouseEvent) => {
			if (isEditing) return;
			crossFilter.setFilter({
				sourceCardId: dashcardId,
				column: params.dimensionName,
				value: params.dimensionValue,
			});
		},
		[isEditing, crossFilter],
	);

	// Param change
	const handleParamChange = useCallback((paramId: string, value: string) => {
		setParamValues((prev) => ({ ...prev, [paramId]: value }));
	}, []);

	// Add new parameter
	const handleAddParam = useCallback(() => {
		const id = `param_${Date.now()}`;
		Modal.confirm({
			title: t(locale, "dashboards.addFilter"),
			content: (
				<div className="space-y-2 mt-2">
					<div>
						<span className="text-xs text-gray-500">{t(locale, "dashboards.paramName")}</span>
						<Input id="__new_param_name" placeholder="e.g. department" />
					</div>
					<div>
						<span className="text-xs text-gray-500">{t(locale, "dashboards.paramType")}</span>
						<Select
							id="__new_param_type"
							defaultValue="category"
							options={[
								{ value: "category", label: "Category" },
								{ value: "date", label: "Date" },
								{ value: "string", label: "String" },
							]}
							style={{ width: "100%" }}
						/>
					</div>
				</div>
			),
			onOk: () => {
				const nameEl = document.getElementById("__new_param_name") as HTMLInputElement | null;
				const paramName = nameEl?.value?.trim() || `Filter ${parameters.length + 1}`;
				setParameters((prev) => [
					...prev,
					{ id, name: paramName, slug: paramName.toLowerCase().replace(/\s+/g, "_"), type: "category" },
				]);
			},
		});
	}, [locale, parameters.length]);

	// Remove parameter
	const handleRemoveParam = useCallback((paramId: string) => {
		setParameters((prev) => prev.filter((p) => p.id !== paramId));
		setParamValues((prev) => {
			const next = { ...prev };
			delete next[paramId];
			return next;
		});
	}, []);

	// Save
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
					parameters: parameters.map((p) => ({
						id: p.id,
						name: p.name || p.id,
						slug: p.slug || p.id,
						type: p.type || "category",
					})),
				},
				dashcards: dashcards.map((dc, index) => ({
					id: dc.id > 0 ? dc.id : undefined,
					card_id: dc.card_id,
					row: dc.row ?? 0,
					col: dc.col ?? 0,
					size_x: dc.size_x ?? 6,
					size_y: dc.size_y ?? 4,
					parameter_mappings: dc.parameter_mappings ?? [],
					visualization_settings: dc.visualization_settings ?? {},
					_seriesIndex: index,
				})),
			};
			const saved = await analyticsApi.saveDashboard(body);
			setSaveState({ state: "loaded", value: saved });
			message.success(t(locale, "dashboards.save"));
			navigate(`/bi/dashboards/${saved.id}`, { replace: true });
		} catch (e) {
			setSaveState({ state: "error", error: e });
		}
	};

	// Existing card ids for picker exclusion
	const existingCardIds = useMemo(() => {
		return new Set(dashcards.map((dc) => dc.card_id).filter((id): id is number => typeof id === "number"));
	}, [dashcards]);

	// Collection options
	const collectionOptions = useMemo(() => [
		{ value: "", label: `${t(locale, "collections.title")} (root)` },
		...(collections.state === "loaded"
			? collections.value
				.filter((c) => c.id !== "root")
				.map((c) => ({ value: String(c.id), label: c.name ?? String(c.id) }))
			: []),
	], [collections, locale]);

	// Loading state
	if (dashboard?.state === "loading") {
		return (
			<PageContainer maxWidth="full">
				<div className="flex items-center justify-center min-h-[400px]">
					<Spin size="large" />
				</div>
			</PageContainer>
		);
	}

	if (dashboard?.state === "error") {
		return (
			<PageContainer maxWidth="full">
				<ErrorNotice locale={locale} error={dashboard.error} />
			</PageContainer>
		);
	}

	return (
		<PageContainer maxWidth="full">
			<div data-testid="analytics-dashboard-editor">
				{/* Top toolbar */}
				<div className="flex items-center gap-3 mb-4 flex-wrap">
					<Link to="/bi/dashboards">
						<Button type="text" icon={<ArrowLeftOutlined />}>
							{t(locale, "dashboards.backToList")}
						</Button>
					</Link>

					<div className="flex-1 min-w-0">
						{isEditing ? (
							<Input
								value={name}
								onChange={(e) => setName(e.target.value)}
								placeholder={t(locale, "dashboards.untitled")}
								variant="borderless"
								className="text-lg font-semibold"
								style={{ fontSize: 18, fontWeight: 600 }}
							/>
						) : (
							<h2 className="text-lg font-semibold truncate m-0">{name || t(locale, "dashboards.untitled")}</h2>
						)}
					</div>

					{isEditing && (
						<Select
							value={collectionId ? String(collectionId) : ""}
							onChange={(value) => setCollectionId(value ? Number.parseInt(value, 10) || null : null)}
							options={collectionOptions}
							disabled={collections.state !== "loaded"}
							style={{ width: 200 }}
							size="small"
							placeholder={t(locale, "questions.collection")}
						/>
					)}

					<Button
						icon={<PlusOutlined />}
						onClick={() => setCardPickerOpen(true)}
						disabled={allCards.state !== "loaded" || !isEditing}
					>
						{t(locale, "dashboards.addCard")}
					</Button>

					<Button
						icon={isEditing ? <EyeOutlined /> : <EditOutlined />}
						onClick={() => setIsEditing(!isEditing)}
					>
						{isEditing ? t(locale, "dashboards.preview") : t(locale, "dashboards.editing")}
					</Button>

					<Button
						type="primary"
						icon={<SaveOutlined />}
						onClick={save}
						disabled={!name.trim() || saveState?.state === "loading"}
						loading={saveState?.state === "loading"}
					>
						{t(locale, "dashboards.save")}
					</Button>
				</div>

				{/* Errors */}
				{collections.state === "error" && <div className="mb-3"><ErrorNotice locale={locale} error={collections.error} /></div>}
				{allCards.state === "error" && <div className="mb-3"><ErrorNotice locale={locale} error={allCards.error} /></div>}
				{saveState?.state === "error" && <div className="mb-3"><ErrorNotice locale={locale} error={saveState.error} /></div>}

				{/* Cross-filter indicator */}
				{crossFilter.activeFilter && !isEditing && (
					<div className="flex items-center gap-2 mb-3 px-3 py-2 bg-blue-50 dark:bg-blue-900/20 rounded-md text-sm">
						<span>
							{t(locale, "filter.crossFilterActive")
								.replace("{column}", crossFilter.activeFilter.column)
								.replace("{value}", crossFilter.activeFilter.value)}
						</span>
						<Button type="link" size="small" onClick={crossFilter.clearFilter}>
							{t(locale, "filter.clearCrossFilter")}
						</Button>
					</div>
				)}

				{/* Filter bar */}
				<DashboardFilterBar
					parameters={parameters}
					paramValues={paramValues}
					paramOptions={paramOptions}
					onParamChange={handleParamChange}
					isEditing={isEditing}
					locale={locale}
					onAddParam={handleAddParam}
					onRemoveParam={handleRemoveParam}
				/>

				{/* Grid layout */}
				{dashcards.length === 0 ? (
					<div className="flex flex-col items-center justify-center min-h-[300px] border-2 border-dashed border-gray-300 rounded-lg text-gray-400">
						<PlusOutlined className="text-3xl mb-2" />
						<p className="text-sm">{t(locale, "dashboards.noCards")}</p>
						{isEditing && (
							<Button
								type="primary"
								ghost
								icon={<PlusOutlined />}
								onClick={() => setCardPickerOpen(true)}
								disabled={allCards.state !== "loaded"}
								className="mt-2"
							>
								{t(locale, "dashboards.addCard")}
							</Button>
						)}
					</div>
				) : (
					<DashboardEditorGrid
						dashcards={dashcards}
						cardResults={cardResults}
						isEditing={isEditing}
						locale={locale}
						onLayoutChange={handleLayoutChange}
						onRemoveCard={handleRemoveCard}
						onSeriesClick={handleSeriesClick}
						drillFilters={drill.filters}
						onDrillClear={drill.clearAll}
						onDrillRemoveFrom={drill.removeFiltersFrom}
					/>
				)}

				{/* Card picker modal */}
				{allCards.state === "loaded" && (
					<CardPickerModal
						open={cardPickerOpen}
						onClose={() => setCardPickerOpen(false)}
						onAdd={handleAddCards}
						allCards={allCards.value}
						existingCardIds={existingCardIds}
					/>
				)}
			</div>
		</PageContainer>
	);
}
