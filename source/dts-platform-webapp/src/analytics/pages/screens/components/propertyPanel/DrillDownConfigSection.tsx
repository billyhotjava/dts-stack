import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { normalizeDrillLevel } from "../../drillRuntime";
import type { DataSourceConfig, DrillDownConfig, DrillLevel, ScreenComponent, ScreenGlobalVariable } from "../../types";
import { MappingEditor } from "./BehaviorConfigSection";
import { renderDataSourceConfig } from "./DataSourceConfigSection";
import { DRILL_CONFIGURABLE_TYPES, getActionSourcePathCandidates } from "./helpers";

const DRILL_TARGET_TYPES: DataSourceConfig["type"][] = ["sql", "api", "card", "dataset", "metric"];

function canConfirmDrillLevel(level: DrillLevel | null) {
	if (!level) return false;
	const normalized = normalizeDrillLevel(level);
	if (!normalized) return false;
	const targetKeys = normalized.mappings.map((mapping) => String(mapping.variableKey ?? "").trim());
	return new Set(targetKeys).size === targetKeys.length;
}

function DrillLevelEditor({
	component,
	level,
	index,
	globalVariables,
	sourcePathCandidates,
	onChange,
	onRemove,
}: {
	component: ScreenComponent;
	level: DrillLevel;
	index: number;
	globalVariables: ScreenGlobalVariable[];
	sourcePathCandidates: string[];
	onChange: (patch: Partial<DrillLevel>) => void;
	onRemove: () => void;
}) {
	const labelInputId = `drill-${component.id}-${index}-label`;
	const inheritInputId = `drill-${component.id}-${index}-inherit`;
	const legacyCardId = Number(level.cardId ?? 0);
	const legacyParamName = String(level.paramName ?? "").trim();
	const legacyDataSource: DataSourceConfig | undefined =
		legacyCardId > 0 ? { type: "card", sourceType: "card", cardConfig: { cardId: legacyCardId } } : undefined;
	const targetDataSource = level.dataSource ?? legacyDataSource;
	const mappings =
		level.mappings ??
		(legacyParamName ? [{ sourcePath: "name", variableKey: legacyParamName, transform: "string" as const }] : []);
	const promoteToGeneric = (patch: Partial<DrillLevel>) =>
		onChange({
			dataSource: targetDataSource,
			mappings,
			inheritContext: level.inheritContext ?? legacyCardId <= 0,
			cardId: undefined,
			paramName: undefined,
			...patch,
		});

	return (
		<div
			style={{
				border: "1px solid rgba(255,255,255,0.1)",
				borderRadius: 4,
				padding: 8,
				marginBottom: 8,
			}}
		>
			<div
				style={{
					display: "flex",
					justifyContent: "space-between",
					alignItems: "center",
					marginBottom: 4,
					fontSize: 13,
					color: "var(--color-text-secondary)",
				}}
			>
				<span>层级 {index + 1}</span>
				<button
					type="button"
					className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
					onClick={onRemove}
					style={{ background: "none", border: "none", color: "#ef4444", cursor: "pointer", fontSize: 13, gap: 4 }}
				>
					<Trash2 size={12} aria-hidden="true" />
					删除
				</button>
			</div>
			<div className="property-row flex items-center mb-3">
				<label htmlFor={labelInputId} className="property-label w-20 text-xs text-text-secondary">
					导航标签
				</label>
				<input
					id={labelInputId}
					type="text"
					className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
					value={level.label || ""}
					onChange={(event) => onChange({ label: event.target.value })}
					placeholder="明细"
				/>
			</div>
			{!String(level.label ?? "").trim() ? (
				<div role="alert" style={{ margin: "-6px 0 8px 80px", color: "#ef4444", fontSize: 11 }}>
					导航标签不能为空
				</div>
			) : null}
			<div style={{ fontSize: 12, color: "var(--color-text-secondary)", margin: "8px 0 6px" }}>下一层数据源</div>
			{renderDataSourceConfig(
				{ ...component, id: `${component.id}__drill_${index}`, dataSource: targetDataSource },
				(_id, updates) => promoteToGeneric({ dataSource: updates.dataSource }),
				globalVariables,
				{ allowedTypes: DRILL_TARGET_TYPES },
			)}
			<div style={{ fontSize: 12, color: "var(--color-text-secondary)", margin: "8px 0 6px" }}>字段映射</div>
			<MappingEditor
				keyPrefix={`drill-${component.id}-${index}`}
				mappings={mappings}
				sourcePathCandidates={sourcePathCandidates}
				onChange={(next) => promoteToGeneric({ mappings: next })}
			/>
			<div className="property-row flex items-center mt-3 mb-1">
				<label htmlFor={inheritInputId} className="property-label w-20 text-xs text-text-secondary">
					继承上层筛选
				</label>
				<input
					id={inheritInputId}
					type="checkbox"
					checked={level.inheritContext ?? legacyCardId <= 0}
					onChange={(event) => promoteToGeneric({ inheritContext: event.target.checked })}
				/>
			</div>
		</div>
	);
}

export function DrillDownConfigSection({
	component,
	updateComponent,
	globalVariables,
	embedded = false,
}: {
	component: ScreenComponent;
	updateComponent: (id: string, updates: Partial<ScreenComponent>) => void;
	globalVariables: ScreenGlobalVariable[];
	embedded?: boolean;
}) {
	const [draftLevel, setDraftLevel] = useState<DrillLevel | null>(null);
	const drillDown = component.drillDown;
	const enabled = drillDown?.enabled ?? false;
	const levels = drillDown?.levels ?? [];
	const sourcePathCandidates = getActionSourcePathCandidates(component.type);
	const enabledInputId = `drill-${component.id}-enabled`;

	const setDrillDown = (updates: Partial<DrillDownConfig>) => {
		updateComponent(component.id, {
			drillDown: { enabled, levels, ...drillDown, ...updates },
		});
	};
	const updateLevel = (index: number, patch: Partial<DrillLevel>) => {
		const nextLevels = [...levels];
		nextLevels[index] = { ...nextLevels[index], ...patch };
		setDrillDown({ levels: nextLevels });
	};
	const removeLevel = (index: number) => {
		setDrillDown({ levels: levels.filter((_, currentIndex) => currentIndex !== index) });
	};
	const beginLevelDraft = () => {
		setDraftLevel({
			label: "",
			dataSource: component.dataSource,
			mappings: [],
			inheritContext: true,
		});
	};
	const confirmLevelDraft = () => {
		if (!draftLevel || !canConfirmDrillLevel(draftLevel)) return;
		setDrillDown({ levels: [...levels, draftLevel] });
		setDraftLevel(null);
	};

	const content = (
		<>
			<div className="property-row flex items-center mb-3">
				<label htmlFor={enabledInputId} className="property-label w-20 text-xs text-text-secondary">
					启用下钻
				</label>
				<input
					id={enabledInputId}
					type="checkbox"
					checked={enabled}
					onChange={(event) => {
						if (!event.target.checked) setDraftLevel(null);
						setDrillDown({ enabled: event.target.checked });
					}}
				/>
			</div>

			{enabled ? (
				<>
					{levels.map((level, index) => (
						<DrillLevelEditor
							// biome-ignore lint/suspicious/noArrayIndexKey: drill level order is its persisted identity.
							key={index}
							component={component}
							level={level}
							index={index}
							globalVariables={globalVariables}
							sourcePathCandidates={sourcePathCandidates}
							onChange={(patch) => updateLevel(index, patch)}
							onRemove={() => removeLevel(index)}
						/>
					))}

					{draftLevel ? (
						<>
							<div style={{ marginBottom: 6, color: "#f59e0b", fontSize: 11 }}>
								未保存层级：补全导航标签和字段映射后再确认
							</div>
							<DrillLevelEditor
								component={component}
								level={draftLevel}
								index={levels.length}
								globalVariables={globalVariables}
								sourcePathCandidates={sourcePathCandidates}
								onChange={(patch) => setDraftLevel((current) => (current ? { ...current, ...patch } : current))}
								onRemove={() => setDraftLevel(null)}
							/>
							<button
								type="button"
								className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand disabled:opacity-45 disabled:cursor-not-allowed"
								disabled={!canConfirmDrillLevel(draftLevel)}
								onClick={confirmLevelDraft}
								style={{ width: "100%", textAlign: "center", color: "#2563eb" }}
							>
								保存此层级
							</button>
						</>
					) : (
						<button
							type="button"
							className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
							onClick={beginLevelDraft}
							style={{
								width: "100%",
								cursor: "pointer",
								textAlign: "center",
								color: "#6366f1",
								display: "inline-flex",
								alignItems: "center",
								justifyContent: "center",
								gap: 6,
							}}
						>
							<Plus size={13} aria-hidden="true" />
							添加下钻层级
						</button>
					)}
				</>
			) : null}
		</>
	);

	if (embedded) return content;
	return (
		<div className="property-section py-3 border-b border-border-default">
			<div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">
				下钻配置
			</div>
			{content}
		</div>
	);
}

export function renderDrillDownConfig(
	component: ScreenComponent,
	updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
	globalVariables: ScreenGlobalVariable[],
	options?: { embedded?: boolean },
) {
	const { type, dataSource } = component;
	const rootSourceType = String(dataSource?.sourceType ?? dataSource?.type ?? "")
		.trim()
		.toLowerCase();
	const hasExecutableSource = DRILL_TARGET_TYPES.includes(
		(rootSourceType === "database" ? "sql" : rootSourceType) as DataSourceConfig["type"],
	);
	if (!DRILL_CONFIGURABLE_TYPES.has(type) || !hasExecutableSource || !dataSource) return null;

	return (
		<DrillDownConfigSection
			key={component.id}
			component={component}
			updateComponent={updateComponent}
			globalVariables={globalVariables}
			embedded={options?.embedded}
		/>
	);
}
