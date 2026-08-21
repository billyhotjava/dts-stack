import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import type { Layout } from "react-grid-layout";
import {
	analyticsApi,
	type CardListItem,
	type CollectionListItem,
	type DashboardCard,
	type DashboardDetail,
	type DashboardPublicationAudience,
	type DashboardPublicationValidation,
	type DashboardVersion,
	type PlatformOrgNode,
	type PlatformRole,
} from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { Alert, App, Button, Card, Drawer, Empty, Input, Modal, Select, Space, Spin, Tag, Typography } from "antd";
import { statusLabel } from "@/utils/customerDisplayLabels";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import { useDashboardCrossFilter } from "../hooks/useDashboardCrossFilter";
import { useDrillFilter } from "../hooks/useDrillFilter";
import { DashboardEditorGrid } from "./dashboard/DashboardEditorGrid";
import { DashboardFilterBar, type DashboardParameter } from "./dashboard/DashboardFilterBar";
import { CardPickerModal } from "./dashboard/CardPickerModal";
import { DashboardAnalysisLibrary } from "./dashboard/DashboardAnalysisLibrary";
import { DashboardComponentInspector } from "./dashboard/DashboardComponentInspector";
import type { SeriesClickParams } from "../components/charts";
import { CROSS_FILTER_ENABLED, CROSS_FILTER_TARGETS } from "./dashboard/InteractionSettingsPopover";
import type { ParameterMapping } from "./dashboard/ParameterMappingPopover";
import {
	flattenDepartmentOptions,
	hasRequiredPublicationAudience,
	isPublishedAnalysisCard,
	publicationErrorMessage,
	publicationIssueMessage,
	toDashboardParams,
	toEditableDashcards,
} from "./dashboard/dashboardEditorModel";
import { useDashboardCardQueries } from "./dashboard/useDashboardCardQueries";

const { Text } = Typography;

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export default function DashboardEditorPage() {
	const { message, modal } = App.useApp();
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const navigate = useNavigate();
	const params = useParams();
	const dashboardId = params.id ? String(params.id) : null;

	// Collections & cards for picker
	const [collections, setCollections] = useState<LoadState<CollectionListItem[]>>({ state: "loading" });
	const [allCards, setAllCards] = useState<LoadState<CardListItem[]>>({ state: "loading" });
	const [platformOrgs, setPlatformOrgs] = useState<LoadState<PlatformOrgNode[]>>({ state: "loading" });
	const [platformRoles, setPlatformRoles] = useState<LoadState<PlatformRole[]>>({ state: "loading" });
	const [directoryReloadKey, setDirectoryReloadKey] = useState(0);
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
	const [replacementIndex, setReplacementIndex] = useState<number | null>(null);
	const [selectedDashcardId, setSelectedDashcardId] = useState<number | null>(null);
	const [publishOpen, setPublishOpen] = useState(false);
	const [publicationBusy, setPublicationBusy] = useState(false);
	const [publicationError, setPublicationError] = useState<string | null>(null);
	const [publicationValidation, setPublicationValidation] = useState<DashboardPublicationValidation | null>(null);
	const [audience, setAudience] = useState<DashboardPublicationAudience>({
		deptCodes: [],
		roleCodes: [],
		classification: "DATA_INTERNAL",
		expiresAt: null,
	});
	const [versionsOpen, setVersionsOpen] = useState(false);
	const [versionsLoading, setVersionsLoading] = useState(false);
	const [versions, setVersions] = useState<DashboardVersion[]>([]);
	const nextTemporaryDashcardId = useRef(-1);

	const dashboardValue = dashboard?.state === "loaded" ? dashboard.value : null;
	const canModify = !dashboardValue?.lifecycle_status || dashboardValue.lifecycle_status === "DRAFT";
	const publishedAnalysisCards = useMemo(
		() => allCards.state === "loaded" ? allCards.value.filter(isPublishedAnalysisCard) : [],
		[allCards],
	);
	const departmentOptions = useMemo(
		() => platformOrgs.state === "loaded" ? flattenDepartmentOptions(platformOrgs.value) : [],
		[platformOrgs],
	);
	const roleOptions = useMemo(
		() => platformRoles.state === "loaded"
			? platformRoles.value.map((role) => ({
				value: role.name,
				label: role.description ? `${role.name} · ${role.description}` : role.name,
			}))
			: [],
		[platformRoles],
	);

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

	// Fetch governed publication audiences once per editor session.
	useEffect(() => {
		void directoryReloadKey;
		let cancelled = false;
		setPlatformOrgs({ state: "loading" });
		setPlatformRoles({ state: "loading" });
		analyticsApi
			.listPlatformOrgs()
			.then((value) => !cancelled && setPlatformOrgs({ state: "loaded", value }))
			.catch((error) => !cancelled && setPlatformOrgs({ state: "error", error }));
		analyticsApi
			.listPlatformRoles()
			.then((value) => !cancelled && setPlatformRoles({ state: "loaded", value }))
			.catch((error) => !cancelled && setPlatformRoles({ state: "error", error }));
		return () => { cancelled = true; };
	}, [directoryReloadKey]);

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
				const editableDashcards = toEditableDashcards(d);
				setDashcards(editableDashcards);
				setSelectedDashcardId(editableDashcards[0]?.id ?? null);
				setParameters(toDashboardParams(d));
				setIsEditing(!d.lifecycle_status || d.lifecycle_status === "DRAFT");
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

	const cardResults = useDashboardCardQueries({
		dashcards,
		dashboardId,
		queryParameters: queryParametersPayload,
		buildCrossFilterParams: crossFilter.buildCrossFilterParams,
	});

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
		if (dashcards[index]?.id === selectedDashcardId) setSelectedDashcardId(null);
		setDashcards((prev) => prev.filter((_, i) => i !== index));
	}, [dashcards, selectedDashcardId]);

	const onParameterMappingsChange = useCallback((dashcardId: number, mappings: ParameterMapping[]) => {
		setDashcards((prev) => prev.map((item) =>
			item.id === dashcardId ? { ...item, parameter_mappings: mappings } : item
		));
	}, []);

	const onInteractionSettingsChange = useCallback((dashcardId: number, settings: Record<string, unknown>) => {
		setDashcards((prev) => prev.map((item) =>
			item.id === dashcardId ? { ...item, visualization_settings: settings } : item
		));
	}, []);

	// Add published analyses from the picker or external drag source.
	const handleAddCards = useCallback((
		cards: CardListItem[],
		placement?: Pick<Layout, "x" | "y" | "w" | "h">,
	) => {
		const currentCardIds = new Set(dashcards.map((dashcard) => dashcard.card_id));
		const accepted = cards
			.filter((card) => isPublishedAnalysisCard(card) && !currentCardIds.has(card.id))
			.slice(0, Math.max(0, 50 - dashcards.length));
		if (accepted.length < cards.length) {
			message.warning(dashcards.length >= 50 ? "仪表板最多允许 50 个分析组件" : "该分析已在画布中");
		}
		if (accepted.length === 0) return;

		const maxBottom = dashcards.reduce(
			(bottom, dashcard) => Math.max(bottom, (dashcard.row ?? 0) + (dashcard.size_y ?? 4)),
			0,
		);
		const newCards: DashboardCard[] = accepted.map((card, index) => {
			const id = nextTemporaryDashcardId.current--;
			const row = placement && index === 0 ? placement.y : maxBottom + Math.floor(index / 2) * 4;
			const width = placement && index === 0 ? Math.max(3, Math.min(12, placement.w)) : 6;
			const col = placement && index === 0
				? Math.max(0, Math.min(12 - width, placement.x))
				: (index % 2) * 6;
			return {
				id,
				card_id: card.id,
				row,
				col,
				size_x: width,
				size_y: placement && index === 0 ? Math.max(2, Math.min(20, placement.h)) : 4,
				parameter_mappings: [],
				visualization_settings: {},
				card,
			};
		});
		setDashcards((current) => [...current, ...newCards]);
		setSelectedDashcardId(newCards[0].id);
	}, [dashcards, message]);

	const handleDropCard = useCallback((cardId: number, placement: Pick<Layout, "x" | "y" | "w" | "h">) => {
		const card = publishedAnalysisCards.find((candidate) => candidate.id === cardId);
		if (card) handleAddCards([card], placement);
	}, [handleAddCards, publishedAnalysisCards]);

	const handleReplaceCards = useCallback((cards: CardListItem[]) => {
		const replacement = cards.find(isPublishedAnalysisCard);
		if (replacementIndex == null || !replacement) return;
		setDashcards((current) => current.map((dashcard, index) => index === replacementIndex
			? {
				...dashcard,
				card_id: replacement.id,
				card: replacement,
				parameter_mappings: [],
				visualization_settings: {},
			}
			: dashcard));
		setSelectedDashcardId(dashcards[replacementIndex]?.id ?? null);
		setReplacementIndex(null);
		message.success("已替换分析，原位置和尺寸已保留");
	}, [dashcards, message, replacementIndex]);

	const handleSelectedLayoutChange = useCallback((
		patch: Partial<Pick<DashboardCard, "row" | "col" | "size_x" | "size_y">>,
	) => {
		if (selectedDashcardId == null) return;
		setDashcards((current) => current.map((dashcard) => {
			if (dashcard.id !== selectedDashcardId) return dashcard;
			const next = { ...dashcard, ...patch };
			const width = Math.max(3, Math.min(12, next.size_x ?? 6));
			return {
				...next,
				col: Math.max(0, Math.min(12 - width, next.col ?? 0)),
				row: Math.max(0, next.row ?? 0),
				size_x: width,
				size_y: Math.max(2, Math.min(20, next.size_y ?? 4)),
			};
		}));
	}, [selectedDashcardId]);

	// Series click handler (for cross-filter)
	const handleSeriesClick = useCallback(
		(dashcardId: number, params: SeriesClickParams, _event?: React.MouseEvent) => {
			if (isEditing) return;
			const source = dashcards.find((item) => item.id === dashcardId);
			const settings = source?.visualization_settings && typeof source.visualization_settings === "object"
				? source.visualization_settings as Record<string, unknown>
				: {};
			if (settings[CROSS_FILTER_ENABLED] !== true) return;
			const targetCardIds = Array.isArray(settings[CROSS_FILTER_TARGETS])
				? settings[CROSS_FILTER_TARGETS].filter((value): value is number =>
					typeof value === "number" && value !== dashcardId && dashcards.some((item) => item.id === value)
				)
				: [];
			if (targetCardIds.length === 0) return;
			crossFilter.setFilter({
				sourceCardId: dashcardId,
				column: params.dimensionName,
				value: params.dimensionValue,
				targetCardIds,
			});
		},
		[isEditing, dashcards, crossFilter],
	);

	// Param change
	const handleParamChange = useCallback((paramId: string, value: string) => {
		setParamValues((prev) => ({ ...prev, [paramId]: value }));
	}, []);

	// Add new parameter
	const handleAddParam = useCallback(() => {
		if (parameters.length >= 20) {
			message.warning("仪表板最多允许 20 个筛选参数");
			return;
		}
		const id = `param_${Date.now()}`;
		modal.confirm({
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
	}, [locale, message.warning, modal.confirm, parameters.length]);

	// Remove parameter
	const handleRemoveParam = useCallback((paramId: string) => {
		setParameters((prev) => prev.filter((p) => p.id !== paramId));
		setParamValues((prev) => {
			const next = { ...prev };
			delete next[paramId];
			return next;
		});
	}, []);

	// Save the exact draft that publication validation will inspect.
	const saveDraft = async ({ notify = true }: { notify?: boolean } = {}): Promise<DashboardDetail | null> => {
		if (!canModify) return null;
		const trimmedName = name.trim();
		if (!trimmedName) return null;
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
			const savedDashcards = toEditableDashcards(saved);
			const selectedCardId = dashcards.find((dashcard) => dashcard.id === selectedDashcardId)?.card_id;
			setSaveState({ state: "loaded", value: saved });
			setDashboard({ state: "loaded", value: saved });
			setDashcards(savedDashcards);
			setSelectedDashcardId(
				savedDashcards.find((dashcard) => dashcard.card_id === selectedCardId)?.id
					?? savedDashcards[0]?.id
					?? null,
			);
			if (notify) message.success(t(locale, "dashboards.save"));
			navigate(`/bi/dashboards/${saved.id}/edit`, { replace: true });
			return saved;
		} catch (e) {
			setSaveState({ state: "error", error: e });
			if (!notify) setPublicationError(publicationErrorMessage(e));
			return null;
		}
	};

	const publicationPayload = (): DashboardPublicationAudience => ({
		...audience,
		expiresAt: audience.expiresAt ? new Date(audience.expiresAt).toISOString() : null,
	});

	const validatePublication = async (targetId: string | number | null = dashboardId): Promise<DashboardPublicationValidation | null> => {
		if (!targetId) {
			setPublicationError("请先保存仪表板草稿，再进行发布校验。");
			return null;
		}
		if (!hasRequiredPublicationAudience(audience)) {
			setPublicationValidation(null);
			setPublicationError("请选择至少一个可见部门或可见角色。");
			return null;
		}
		setPublicationBusy(true);
		setPublicationError(null);
		try {
			const result = await analyticsApi.validateDashboardPublication(targetId, publicationPayload());
			setPublicationValidation(result);
			return result;
		} catch (error) {
			setPublicationError(publicationErrorMessage(error));
			return null;
		} finally {
			setPublicationBusy(false);
		}
	};

	const openPublication = async () => {
		setPublishOpen(true);
		setPublicationValidation(null);
		setPublicationError(null);
		setPublicationBusy(true);
		const saved = await saveDraft({ notify: false });
		if (!saved) {
			setPublicationBusy(false);
			return;
		}
		if (!hasRequiredPublicationAudience(audience)) {
			setPublicationError("请选择至少一个可见部门或可见角色。");
			setPublicationBusy(false);
			return;
		}
		await validatePublication(saved.id);
	};

	const publish = async () => {
		if (!dashboardId) return;
		const checked = await validatePublication(dashboardId);
		if (!checked?.valid) return;
		setPublicationBusy(true);
		try {
			const result = await analyticsApi.publishDashboard(dashboardId, publicationPayload());
			setDashboard((current) => current?.state === "loaded"
				? {
					state: "loaded",
					value: {
						...current.value,
						lifecycle_status: result.lifecycleStatus,
						published_revision_id: result.revisionId,
						registration_status: result.registrationStatus,
						version_no: result.versionNo,
					},
				}
				: current);
			setIsEditing(false);
			setPublishOpen(false);
			message.success(`仪表板已发布为 v${result.versionNo}，正在注册业务入口`);
		} catch (error) {
			setPublicationError(publicationErrorMessage(error));
		} finally {
			setPublicationBusy(false);
		}
	};

	const openVersions = async () => {
		if (!dashboardId) return;
		setVersionsOpen(true);
		setVersionsLoading(true);
		try {
			setVersions(await analyticsApi.listDashboardVersions(dashboardId));
		} catch (error) {
			message.error(publicationErrorMessage(error));
		} finally {
			setVersionsLoading(false);
		}
	};

	const createDraftFromVersion = async (revisionId: number) => {
		if (!dashboardId) return;
		setVersionsLoading(true);
		try {
			const draft = await analyticsApi.createDashboardDraftFromVersion(dashboardId, revisionId);
			message.success("已从历史版本创建独立草稿");
			navigate(`/bi/dashboards/${draft.id}/edit`);
		} catch (error) {
			message.error(publicationErrorMessage(error));
		} finally {
			setVersionsLoading(false);
		}
	};

	const retryRegistration = async () => {
		if (!dashboardId) return;
		try {
			const result = await analyticsApi.retryDashboardRegistration(dashboardId);
			if (!result.queued) {
				message.info("当前发布版本没有可重试的注册任务");
				return;
			}
			setDashboard((current) => current?.state === "loaded"
				? { state: "loaded", value: { ...current.value, registration_status: "PENDING_REGISTRATION" } }
				: current);
			message.success("已重新提交业务入口注册");
		} catch (error) {
			message.error(publicationErrorMessage(error));
		}
	};

	// Existing card ids for picker exclusion
	const existingCardIds = useMemo(() => {
		return new Set(dashcards.map((dc) => dc.card_id).filter((id): id is number => typeof id === "number"));
	}, [dashcards]);
	const selectedDashcardIndex = useMemo(
		() => dashcards.findIndex((dashcard) => dashcard.id === selectedDashcardId),
		[dashcards, selectedDashcardId],
	);
	const selectedDashcard = selectedDashcardIndex >= 0 ? dashcards[selectedDashcardIndex] : null;

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
			<div className="space-y-4">
				<div className="flex items-center justify-center min-h-[400px]">
					<Spin size="large" />
				</div>
			</div>
		);
	}

	if (dashboard?.state === "error") {
		return (
			<div className="space-y-4">
				<ErrorNotice locale={locale} error={dashboard.error} />
			</div>
		);
	}

	return (
		<div className="space-y-4">
			<div data-testid="analytics-dashboard-editor">
				{/* Top toolbar */}
				<div className="mb-4 flex flex-col gap-3">
					<div className="flex min-w-0 items-center gap-3">
						<Link className="shrink-0" to="/bi/dashboards">
							<Button type="text">
								{t(locale, "dashboards.backToList")}
							</Button>
						</Link>

						<div className="flex min-w-0 flex-1 items-center gap-2">
							{isEditing && canModify ? (
								<Input
									value={name}
									onChange={(e) => setName(e.target.value)}
									placeholder={t(locale, "dashboards.untitled")}
									variant="borderless"
									className="min-w-0 flex-1 text-lg font-semibold"
									style={{ fontSize: 18, fontWeight: 600 }}
								/>
							) : (
								<h2 className="m-0 min-w-0 flex-1 truncate text-lg font-semibold">{name || t(locale, "dashboards.untitled")}</h2>
							)}
							<div className="flex shrink-0 flex-wrap items-center justify-end gap-1">
								{dashboardValue?.lifecycle_status && <Tag>{statusLabel(dashboardValue.lifecycle_status)}</Tag>}
								{dashboardValue?.registration_status && (
									<Tag color={dashboardValue.registration_status === "AVAILABLE" ? "green" : dashboardValue.registration_status === "REGISTRATION_FAILED" ? "red" : "gold"}>
										{dashboardValue.registration_status}
									</Tag>
								)}
							</div>
						</div>
					</div>

					<div className="flex flex-wrap items-center gap-3">
					{isEditing && canModify && (
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
						onClick={() => { setReplacementIndex(null); setCardPickerOpen(true); }}
						disabled={allCards.state !== "loaded" || !isEditing || !canModify || dashcards.length >= 50}
					>
						{t(locale, "dashboards.addCard")}
					</Button>

					<Button
						onClick={() => setIsEditing(!isEditing)}
						disabled={!canModify}
					>
						{isEditing ? t(locale, "dashboards.preview") : t(locale, "dashboards.editing")}
					</Button>

					{dashboardId && <Button onClick={() => void openVersions()}>版本历史</Button>}
					{dashboardId && canModify && <Button onClick={() => void openPublication()}>校验</Button>}
					{dashboardId && canModify && <Button type="primary" ghost onClick={() => void openPublication()}>发布</Button>}
					{dashboardValue?.registration_status === "REGISTRATION_FAILED" && (
						<Button danger onClick={() => void retryRegistration()}>重试注册</Button>
					)}

					<Button
						type="primary"
						onClick={() => void saveDraft()}
						disabled={!canModify || !name.trim() || saveState?.state === "loading"}
						loading={saveState?.state === "loading"}
					>
						保存草稿
					</Button>
					</div>
				</div>

				{/* Errors */}
				{collections.state === "error" && <div className="mb-3"><ErrorNotice locale={locale} error={collections.error} /></div>}
				{allCards.state === "error" && <div className="mb-3"><ErrorNotice locale={locale} error={allCards.error} /></div>}
				{saveState?.state === "error" && <div className="mb-3"><ErrorNotice locale={locale} error={saveState.error} /></div>}
				{!canModify && (
					<Alert className="mb-3" type="info" showIcon message="已发布仪表板不可原位修改" description="需要调整时，请从版本历史创建独立草稿。" />
				)}
				{dashboardValue?.registration_status === "PENDING_REGISTRATION" && (
					<Alert className="mb-3" type="warning" showIcon message="业务入口正在注册" description="注册完成前，普通消费者不会看到或执行该仪表板。" />
				)}
				{dashboardValue?.registration_status === "REGISTRATION_FAILED" && (
					<Alert className="mb-3" type="error" showIcon message="业务入口注册失败" description="发布版本仍保留，可重试注册且不会重复创建入口。" />
				)}

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
					isEditing={isEditing && canModify}
					locale={locale}
					onAddParam={handleAddParam}
					onRemoveParam={handleRemoveParam}
				/>

				{/* Governed analysis composer */}
				{isEditing && canModify ? (
					<div className="mb-dashboard-composer">
						<DashboardAnalysisLibrary
							cards={publishedAnalysisCards}
							existingCardIds={existingCardIds}
							disabled={dashcards.length >= 50}
							loading={allCards.state === "loading"}
							onAdd={(card) => handleAddCards([card])}
						/>
						<section className="mb-dashboard-composer__panel mb-dashboard-composer__canvas" aria-label="看板编排画布">
							<div className="mb-dashboard-composer__panel-title">
								<div>
									<strong>12 列编排画布</strong>
									<div className="mb-dashboard-composer__panel-caption">拖动标题条移动，拖动右下角调整尺寸</div>
								</div>
								<Tag>{dashcards.length} / 50</Tag>
							</div>
							{dashcards.length === 0 ? (
								<Alert
									className="mb-3"
									type="info"
									showIcon
									message="从左侧拖入已发布分析"
									description="也可以点击分析项右侧的添加按钮，组件将进入 12 列网格。"
								/>
							) : null}
							<DashboardEditorGrid
								dashcards={dashcards}
								cardResults={cardResults}
								isEditing
								locale={locale}
								parameters={parameters}
								onLayoutChange={handleLayoutChange}
								onRemoveCard={handleRemoveCard}
								onParameterMappingsChange={onParameterMappingsChange}
								onInteractionSettingsChange={onInteractionSettingsChange}
								onSeriesClick={handleSeriesClick}
								drillFilters={drill.filters}
								onDrillClear={drill.clearAll}
								onDrillRemoveFrom={drill.removeFiltersFrom}
								selectedDashcardId={selectedDashcardId}
								onSelectCard={setSelectedDashcardId}
								onReplaceCard={(index) => { setReplacementIndex(index); setCardPickerOpen(true); }}
								onDropCard={handleDropCard}
							/>
						</section>
						<DashboardComponentInspector
							dashcard={selectedDashcard}
							onLayoutChange={handleSelectedLayoutChange}
							onReplace={() => {
								if (selectedDashcardIndex >= 0) {
									setReplacementIndex(selectedDashcardIndex);
									setCardPickerOpen(true);
								}
							}}
							onDelete={() => {
								if (selectedDashcardIndex >= 0) handleRemoveCard(selectedDashcardIndex);
							}}
						/>
					</div>
				) : dashcards.length === 0 ? (
					<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t(locale, "dashboards.noCards")} />
				) : (
					<DashboardEditorGrid
						dashcards={dashcards}
						cardResults={cardResults}
						isEditing={false}
						locale={locale}
						parameters={parameters}
						onLayoutChange={handleLayoutChange}
						onRemoveCard={handleRemoveCard}
						onParameterMappingsChange={onParameterMappingsChange}
						onInteractionSettingsChange={onInteractionSettingsChange}
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
						onClose={() => { setCardPickerOpen(false); setReplacementIndex(null); }}
						onAdd={replacementIndex == null ? handleAddCards : handleReplaceCards}
						allCards={publishedAnalysisCards}
						existingCardIds={existingCardIds}
						title={replacementIndex == null ? "添加已发布分析" : "替换为已发布分析"}
						singleSelect={replacementIndex != null}
					/>
				)}

				<Drawer
					title="发布仪表板"
					open={publishOpen}
					width={580}
					onClose={() => setPublishOpen(false)}
					extra={(
						<Space>
							<Button
								loading={publicationBusy}
								disabled={!hasRequiredPublicationAudience(audience)}
								onClick={() => void validatePublication()}
							>
								重新校验
							</Button>
							<Button
								type="primary"
								loading={publicationBusy}
								disabled={!hasRequiredPublicationAudience(audience) || !publicationValidation?.valid}
								onClick={() => void publish()}
							>
								确认发布
							</Button>
						</Space>
					)}
				>
					<Space direction="vertical" size={16} style={{ width: "100%" }}>
						<Alert
							type="info"
							showIcon
							message="发布后将钉定分析版本、参数映射与查询预算"
							description={`当前 ${dashcards.length}/50 个分析组件，${parameters.length}/20 个筛选参数。业务入口注册成功后才会对消费者开放。`}
						/>
						<Text strong>发布范围（必填）</Text>
						<div>
							<Text strong>可见部门</Text>
							<Select
								mode="multiple"
								value={audience.deptCodes}
								onChange={(deptCodes) => {
									setAudience((value) => ({ ...value, deptCodes }));
									setPublicationValidation(null);
									setPublicationError(null);
								}}
								options={departmentOptions}
								loading={platformOrgs.state === "loading"}
								disabled={platformOrgs.state !== "loaded"}
								showSearch
								optionFilterProp="label"
								placeholder="选择可见部门"
								style={{ width: "100%" }}
							/>
						</div>
						<div>
							<Text strong>可见角色</Text>
							<Select
								mode="multiple"
								value={audience.roleCodes}
								onChange={(roleCodes) => {
									setAudience((value) => ({ ...value, roleCodes }));
									setPublicationValidation(null);
									setPublicationError(null);
								}}
								options={roleOptions}
								loading={platformRoles.state === "loading"}
								disabled={platformRoles.state !== "loaded"}
								showSearch
								optionFilterProp="label"
								placeholder="选择可见角色"
								style={{ width: "100%" }}
							/>
						</div>
						{platformOrgs.state === "error" ? (
							<Alert type="error" showIcon message="部门目录加载失败" action={<Button size="small" onClick={() => setDirectoryReloadKey((value) => value + 1)}>重试</Button>} />
						) : null}
						{platformRoles.state === "error" ? (
							<Alert type="error" showIcon message="角色目录加载失败" action={<Button size="small" onClick={() => setDirectoryReloadKey((value) => value + 1)}>重试</Button>} />
						) : null}
						{platformOrgs.state === "loaded" && platformRoles.state === "loaded" && departmentOptions.length === 0 && roleOptions.length === 0 ? (
							<Alert type="warning" showIcon message="目录暂无可选部门或角色" action={<Button size="small" onClick={() => setDirectoryReloadKey((value) => value + 1)}>重新加载</Button>} />
						) : null}
						<div>
							<Text strong>发布密级</Text>
							<Select
								value={audience.classification}
								onChange={(classification) => { setAudience((value) => ({ ...value, classification })); setPublicationValidation(null); }}
								options={[
									{ label: "公开", value: "DATA_PUBLIC" },
									{ label: "内部", value: "DATA_INTERNAL" },
									{ label: "保密", value: "DATA_CONFIDENTIAL" },
									{ label: "敏感", value: "DATA_SENSITIVE" },
									{ label: "秘密", value: "DATA_SECRET" },
								]}
								style={{ width: "100%" }}
							/>
						</div>
						<div>
							<Text strong>有效期（可选）</Text>
							<Input
								type="datetime-local"
								value={audience.expiresAt ?? ""}
								onChange={(event) => { setAudience((value) => ({ ...value, expiresAt: event.target.value || null })); setPublicationValidation(null); }}
							/>
						</div>
						{publicationError && <Alert type="error" showIcon message="发布校验未通过" description={publicationError} />}
						{publicationValidation && (
							<>
								<Alert
									type={publicationValidation.valid ? "success" : "error"}
									showIcon
									message={publicationValidation.valid ? "校验通过，可以发布" : "存在发布阻断项"}
								/>
								{publicationValidation.blockers.map((blocker) => (
									<Alert key={`${blocker.code}-${blocker.path}`} type="error" showIcon message={publicationIssueMessage(blocker, dashcards)} />
								))}
								{publicationValidation.warnings.map((warning) => (
									<Alert key={`${warning.code}-${warning.path}`} type="warning" showIcon message={publicationIssueMessage(warning, dashcards)} />
								))}
								<Card size="small" title="依赖快照">
									<pre style={{ margin: 0, whiteSpace: "pre-wrap", wordBreak: "break-all", maxHeight: 260, overflow: "auto" }}>
										{JSON.stringify(publicationValidation.dependencySnapshot, null, 2)}
									</pre>
								</Card>
							</>
						)}
					</Space>
				</Drawer>

				<Modal
					title="版本历史"
					open={versionsOpen}
					footer={null}
					width={720}
					onCancel={() => setVersionsOpen(false)}
				>
					<Spin spinning={versionsLoading}>
						<Space direction="vertical" size={8} style={{ width: "100%" }}>
							{versions.length === 0 && !versionsLoading ? <Empty description="暂无发布版本" /> : null}
							{versions.map((version) => (
								<Card
									key={version.revisionId}
									size="small"
									title={<Space><Text strong>v{version.versionNo}</Text><Tag>{statusLabel(version.status)}</Tag></Space>}
									extra={<Button size="small" onClick={() => void createDraftFromVersion(version.revisionId)}>基于此版本创建草稿</Button>}
								>
									<Text type="secondary">
										{version.publishedAt ? new Date(version.publishedAt).toLocaleString() : new Date(version.createdAt).toLocaleString()}
										{" · "}{version.contractChecksum ?? "无校验值"}
									</Text>
								</Card>
							))}
						</Space>
					</Spin>
				</Modal>
			</div>
		</div>
	);
}
