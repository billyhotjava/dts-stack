import type React from "react";
import { Button, Spin, Tag } from "antd";
import { DeleteOutlined, EditOutlined, HolderOutlined, SwapOutlined } from "@ant-design/icons";
import { ChartRenderer, type VisualizationType, type VisualizationSettings, type SeriesClickParams } from "../../components/charts";
import { ErrorNotice } from "../../components/ErrorNotice";
import type { DashboardCard, DashboardQueryResponse } from "../../api/analyticsApi";
import type { DrillFilter } from "../../hooks/useDrillFilter";
import type { Locale } from "../../i18n";
import { t } from "../../i18n";
import { ParameterMappingPopover, type ParameterMapping } from "./ParameterMappingPopover";
import { InteractionSettingsPopover } from "./InteractionSettingsPopover";
import type { DashboardParameter } from "./DashboardFilterBar";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

export interface DashboardEditorCardProps {
	dashcard: DashboardCard;
	result: LoadState<DashboardQueryResponse> | undefined;
	isEditing: boolean;
	locale: Locale;
	parameters: DashboardParameter[];
	dashcards: DashboardCard[];
	onRemove?: () => void;
	onParameterMappingsChange?: (mappings: ParameterMapping[]) => void;
	onInteractionSettingsChange?: (settings: Record<string, unknown>) => void;
	onSeriesClick?: (params: SeriesClickParams, event?: React.MouseEvent) => void;
	drillFilters?: DrillFilter[];
	onDrillClear?: () => void;
	onDrillRemoveFrom?: (index: number) => void;
	selected?: boolean;
	onSelect?: () => void;
	onReplace?: () => void;
}

export function DashboardEditorCard({
	dashcard,
	result,
	isEditing,
	locale,
	parameters,
	dashcards,
	onRemove,
	onParameterMappingsChange,
	onInteractionSettingsChange,
	onSeriesClick,
	drillFilters,
	onDrillClear,
	onDrillRemoveFrom,
	selected = false,
	onSelect,
	onReplace,
}: DashboardEditorCardProps) {
	const card: any = dashcard.card as any;
	const cardName = (card && typeof card.name === "string" && card.name) || `Card ${dashcard.card_id ?? "-"}`;
	const display: VisualizationType = (card?.display as VisualizationType) || "table";
	const cardSettings: VisualizationSettings = (card?.visualization_settings as VisualizationSettings) || {};
	const dashcardSettings: VisualizationSettings =
		dashcard.visualization_settings && typeof dashcard.visualization_settings === "object"
			? dashcard.visualization_settings as VisualizationSettings
			: {};
	const vizSettings: VisualizationSettings = { ...cardSettings, ...dashcardSettings };
	const currentMappings = Array.isArray(dashcard.parameter_mappings)
		? dashcard.parameter_mappings as ParameterMapping[]
		: [];

	const hasDrill = drillFilters && drillFilters.length > 0;

	return (
		<div
			data-testid={`dashboard-card-${dashcard.id}`}
			className={`mb-dashboard-card flex flex-col h-full rounded-md border shadow-sm overflow-hidden${selected ? " mb-dashboard-card--selected" : ""}`}
			style={{ background: "var(--surface-card, #fff)" }}
		>
			{/* Header bar - always show in editing mode, minimal in preview */}
			{isEditing && (
				<div className="flex items-center justify-between px-3 py-1.5 border-b border-border-default shrink-0"
					style={{ background: "var(--surface-muted, #f7f9fc)" }}
				>
					<button
						type="button"
						className="flex min-w-0 items-center gap-2 border-0 bg-transparent p-0 text-left"
						onClick={onSelect}
					>
						<span className="mb-dashboard-card__drag-handle cursor-grab active:cursor-grabbing text-gray-500 hover:text-gray-700" title="拖动组件">
							<HolderOutlined />
						</span>
						<span className="text-sm font-medium truncate">{cardName}</span>
						{card?.type !== "analysis" || card?.lifecycle_status !== "PUBLISHED" || typeof card?.published_revision_id !== "number"
							? <Tag color="error">需替换</Tag>
							: null}
					</button>
					<div className="mb-dashboard-card__action flex items-center gap-1">
						<Button
							type="text"
							size="small"
							icon={<SwapOutlined />}
							aria-label="替换分析"
							title="替换分析"
							onClick={(event) => { event.stopPropagation(); onReplace?.(); }}
						/>
						<Button
							type="link"
							size="small"
							icon={<EditOutlined />}
							aria-label="打开分析"
							title="打开分析"
							href={dashcard.card_id ? `/bi/questions/${encodeURIComponent(String(dashcard.card_id))}/edit` : undefined}
							target="_blank"
							onClick={(event) => event.stopPropagation()}
						/>
						{dashcard.card_id && onParameterMappingsChange ? (
							<ParameterMappingPopover
								parameters={parameters}
								currentMappings={currentMappings}
								cardId={dashcard.card_id}
								locale={locale}
								onSave={onParameterMappingsChange}
							/>
						) : null}
						{onInteractionSettingsChange && dashcard.id > 0 ? (
							<InteractionSettingsPopover
								currentDashcardId={dashcard.id}
								dashcards={dashcards}
								settings={dashcardSettings}
								onSave={onInteractionSettingsChange}
							/>
						) : (
							<Button type="text" size="small" disabled title="请先保存仪表板，再配置组件联动">联动</Button>
						)}
						<Button
							type="text"
							size="small"
							danger
							icon={<DeleteOutlined />}
							aria-label="删除组件"
							title="删除组件"
							onClick={(event) => { event.stopPropagation(); onRemove?.(); }}
						/>
					</div>
				</div>
			)}

			{/* Drill breadcrumbs */}
			{!isEditing && hasDrill && (
				<div className="flex items-center gap-1 px-3 py-1 border-b border-border-default text-xs shrink-0 flex-wrap"
					style={{ background: "var(--status-info-soft, #e8f2fd)" }}
				>
					<span className="text-gray-500 mr-1">{t(locale, "dashboards.drillBreadcrumb")}:</span>
					{drillFilters?.map((df, idx) => (
						<Tag
							key={`${df.column}:${df.value}`}
							closable
							onClose={() => onDrillRemoveFrom?.(idx)}
							className="text-xs m-0"
						>
							{df.displayLabel}
						</Tag>
					))}
					<Button type="link" size="small" className="text-xs p-0 h-auto" onClick={onDrillClear}>
						{t(locale, "dashboards.clearDrill")}
					</Button>
				</div>
			)}

			{/* Card name in preview mode */}
			{!isEditing && (
				<div className="px-3 py-1.5 border-b border-border-default shrink-0">
					<span className="text-sm font-medium" style={{ color: "var(--text-primary)" }}>{cardName}</span>
				</div>
			)}

			{/* Chart body */}
			<div className="flex-1 min-h-0 overflow-hidden p-2">
				{!result || result.state === "loading" ? (
					<div className="flex items-center justify-center w-full h-full min-h-[160px]">
						<Spin />
					</div>
				) : result.state === "error" ? (
					<ErrorNotice locale={locale} error={result.error} />
				) : (
					<ChartRenderer
						data={{
							cols: (result.value.data?.cols ?? []) as { name: string; display_name?: string; base_type?: string }[],
							rows: (result.value.data?.rows ?? []) as any[][],
						}}
						display={display}
						settings={vizSettings}
						onSeriesClick={onSeriesClick}
					/>
				)}
			</div>
		</div>
	);
}
