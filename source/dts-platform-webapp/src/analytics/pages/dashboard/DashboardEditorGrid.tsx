import type React from "react";
import { useMemo, useRef } from "react";
import { WidthProvider, Responsive } from "react-grid-layout";
import type { Layout } from "react-grid-layout";
import "../../components/DashboardGrid/DashboardGrid.css";
import { DashboardEditorCard } from "./DashboardEditorCard";
import type { DashboardCard, DashboardQueryResponse } from "../../api/analyticsApi";
import type { SeriesClickParams } from "../../components/charts";
import type { DrillFilter } from "../../hooks/useDrillFilter";
import type { Locale } from "../../i18n";
import type { DashboardParameter } from "./DashboardFilterBar";
import type { ParameterMapping } from "./ParameterMappingPopover";
import { DASHBOARD_ANALYSIS_DRAG_TYPE } from "./DashboardAnalysisLibrary";

const ResponsiveGridLayout = WidthProvider(Responsive);

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export interface DashboardEditorGridProps {
	dashcards: DashboardCard[];
	cardResults: Record<number, LoadState<DashboardQueryResponse>>;
	isEditing: boolean;
	locale: Locale;
	parameters: DashboardParameter[];
	onLayoutChange: (layout: Layout[]) => void;
	onRemoveCard: (dashcardIndex: number) => void;
	onParameterMappingsChange: (dashcardId: number, mappings: ParameterMapping[]) => void;
	onInteractionSettingsChange: (dashcardId: number, settings: Record<string, unknown>) => void;
	onSeriesClick?: (dashcardId: number, params: SeriesClickParams, event?: React.MouseEvent) => void;
	drillFilters?: DrillFilter[];
	onDrillClear?: () => void;
	onDrillRemoveFrom?: (index: number) => void;
	selectedDashcardId?: number | null;
	onSelectCard?: (dashcardId: number) => void;
	onReplaceCard?: (dashcardIndex: number) => void;
	onDropCard?: (cardId: number, layout: Pick<Layout, "x" | "y" | "w" | "h">) => void;
}

const COLS = 12;
const ROW_HEIGHT = 80;
const MARGIN: [number, number] = [16, 16];

export function DashboardEditorGrid({
	dashcards,
	cardResults,
	isEditing,
	locale,
	parameters,
	onLayoutChange,
	onRemoveCard,
	onParameterMappingsChange,
	onInteractionSettingsChange,
	onSeriesClick,
	drillFilters,
	onDrillClear,
	onDrillRemoveFrom,
	selectedDashcardId,
	onSelectCard,
	onReplaceCard,
	onDropCard,
}: DashboardEditorGridProps) {
	const activeColumns = useRef(COLS);
	const layout = useMemo<Layout[]>(() => {
		return dashcards.map((dc, idx) => ({
			i: String(dc.id || `new-${idx}`),
			x: dc.col ?? 0,
			y: dc.row ?? 0,
			w: dc.size_x ?? 6,
			h: dc.size_y ?? 4,
			minW: 3,
			minH: 2,
			static: !isEditing,
		}));
	}, [dashcards, isEditing]);
	const responsiveLayouts = useMemo(() => {
		const stacked = (columns: number): Layout[] => {
			let nextRow = 0;
			return layout.map((item) => {
				const stackedItem = {
					...item,
					x: 0,
					y: nextRow,
					w: columns,
					minW: Math.min(3, columns),
				};
				nextRow += item.h;
				return stackedItem;
			});
		};
		return {
			lg: layout,
			md: layout,
			sm: stacked(6),
			xs: stacked(6),
			xxs: stacked(3),
		};
	}, [layout]);

	// Breakpoints are based on the canvas width, not the browser viewport.
	// The three-column desktop composer leaves roughly 560px for the grid.
	const breakpoints = { lg: 520, md: 480, sm: 400, xs: 300, xxs: 0 };
	const colsConfig = { lg: COLS, md: COLS, sm: 6, xs: 6, xxs: 3 };

	const handleLayoutChange = (newLayout: Layout[]) => {
		if (isEditing && activeColumns.current === COLS) {
			onLayoutChange(newLayout);
		}
	};

	const handleDrop = (_layout: Layout[], item: Layout, event: Event) => {
		if (!isEditing || !onDropCard) return;
		const transfer = (event as DragEvent).dataTransfer;
		const rawCardId = transfer?.getData(DASHBOARD_ANALYSIS_DRAG_TYPE) || transfer?.getData("text/plain");
		const cardId = Number.parseInt(rawCardId || "", 10);
		if (Number.isFinite(cardId) && cardId > 0) {
			const scale = COLS / activeColumns.current;
			onDropCard(cardId, {
				x: Math.round(item.x * scale),
				y: item.y,
				w: Math.round(item.w * scale),
				h: item.h,
			});
		}
	};

	return (
		<div className={`relative min-h-[300px] rounded-lg ${isEditing ? "mb-dashboard-grid--editing" : ""}`}
			style={{ background: "var(--surface-page, #F4F7FB)" }}
		>
			<ResponsiveGridLayout
				className="min-h-[inherit]"
				layouts={responsiveLayouts}
				breakpoints={breakpoints}
				cols={colsConfig}
				rowHeight={ROW_HEIGHT}
				margin={MARGIN}
				containerPadding={[0, 0]}
				isDraggable={isEditing}
				isResizable={isEditing}
				isDroppable={isEditing && Boolean(onDropCard)}
				droppingItem={{ i: "__dts_analysis_drop__", w: 6, h: 4 }}
				onLayoutChange={handleLayoutChange}
				onBreakpointChange={(_breakpoint, columns) => { activeColumns.current = columns; }}
				onDrop={handleDrop}
				draggableHandle=".mb-dashboard-card__drag-handle"
				draggableCancel=".ant-btn,.ant-select,.mb-dashboard-card__action"
				resizeHandles={["se"]}
				useCSSTransforms
			>
				{dashcards.map((dc, idx) => {
					const key = String(dc.id || `new-${idx}`);
					const result = cardResults[dc.id] ?? cardResults[dc.card_id ?? -1];
					return (
						<div key={key}>
							<DashboardEditorCard
								dashcard={dc}
								result={result}
								isEditing={isEditing}
									locale={locale}
									parameters={parameters}
									dashcards={dashcards}
									onRemove={() => onRemoveCard(idx)}
									selected={selectedDashcardId === dc.id}
									onSelect={() => onSelectCard?.(dc.id)}
									onReplace={() => onReplaceCard?.(idx)}
									onParameterMappingsChange={(mappings) => onParameterMappingsChange(dc.id, mappings)}
									onInteractionSettingsChange={(settings) => onInteractionSettingsChange(dc.id, settings)}
								onSeriesClick={
									onSeriesClick
										? (params, event) => onSeriesClick(dc.id, params, event)
										: undefined
								}
								drillFilters={drillFilters}
								onDrillClear={onDrillClear}
								onDrillRemoveFrom={onDrillRemoveFrom}
							/>
						</div>
					);
				})}
			</ResponsiveGridLayout>
		</div>
	);
}
