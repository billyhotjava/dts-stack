import React, { useMemo } from "react";
import { WidthProvider, Responsive } from "react-grid-layout";
import type { Layout } from "react-grid-layout";
import "../../components/DashboardGrid/DashboardGrid.css";
import { DashboardEditorCard } from "./DashboardEditorCard";
import type { DashboardCard, DashboardQueryResponse } from "../../api/analyticsApi";
import type { SeriesClickParams } from "../../components/charts";
import type { DrillFilter } from "../../hooks/useDrillFilter";
import type { Locale } from "../../i18n";

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
	onLayoutChange: (layout: Layout[]) => void;
	onRemoveCard: (dashcardIndex: number) => void;
	onSeriesClick?: (dashcardId: number, params: SeriesClickParams, event?: React.MouseEvent) => void;
	drillFilters?: DrillFilter[];
	onDrillClear?: () => void;
	onDrillRemoveFrom?: (index: number) => void;
}

const COLS = 12;
const ROW_HEIGHT = 80;
const MARGIN: [number, number] = [16, 16];

export function DashboardEditorGrid({
	dashcards,
	cardResults,
	isEditing,
	locale,
	onLayoutChange,
	onRemoveCard,
	onSeriesClick,
	drillFilters,
	onDrillClear,
	onDrillRemoveFrom,
}: DashboardEditorGridProps) {
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

	const breakpoints = { lg: 1200, md: 996, sm: 768, xs: 480, xxs: 0 };
	const colsConfig = { lg: COLS, md: COLS, sm: 6, xs: 6, xxs: 3 };

	const handleLayoutChange = (newLayout: Layout[]) => {
		if (isEditing) {
			onLayoutChange(newLayout);
		}
	};

	return (
		<div className={`relative min-h-[300px] rounded-lg ${isEditing ? "mb-dashboard-grid--editing" : ""}`}
			style={{ background: "var(--surface-page, #F4F7FB)" }}
		>
			<ResponsiveGridLayout
				className="min-h-[inherit]"
				layouts={{ lg: layout }}
				breakpoints={breakpoints}
				cols={colsConfig}
				rowHeight={ROW_HEIGHT}
				margin={MARGIN}
				containerPadding={[0, 0]}
				isDraggable={isEditing}
				isResizable={isEditing}
				onLayoutChange={handleLayoutChange}
				draggableHandle=".mb-dashboard-card__drag-handle"
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
								onRemove={() => onRemoveCard(idx)}
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
