import { useEffect, useMemo, useState, type CSSProperties } from 'react';

import SchemaConfigRenderer from '../configSchema/editors/SchemaConfigRenderer';
import { COMPONENT_CONFIG_SCHEMAS } from '../configSchema/schemas';
import { CardIdPicker } from '../components/CardIdPicker';
import { CardParamBindingsEditor } from '../components/CardParamBindingsEditor';
import { DatabaseIdPicker } from '../components/DatabaseIdPicker';
import { GlobalVariableManager } from '../components/GlobalVariableManager';
import { MetricBindingEditor } from '../components/MetricBindingEditor';
import { ScreenJumpPicker } from '../components/propertyPanel/ScreenJumpPicker';
import {
	ACTION_COMPONENT_TYPES,
	DRILL_CONFIGURABLE_TYPES,
	getActionSourcePathCandidates,
	INTERACTION_COMPONENT_TYPES,
} from '../components/propertyPanel/helpers';
import type {
	CardParameterBinding,
	CarouselConfig,
	ComponentInteractionConfig,
	ComponentInteractionMapping,
	ComponentType,
	DataSourceConfig,
	DrillDownConfig,
	DrillLevel,
	ScreenComponentAction,
	ScreenGlobalVariable,
	ScreenTheme,
} from '../types';
import type { ComponentV2, ScreenConfigV2 } from './types';

interface PropertyPanelV2Props {
	screen: ScreenConfigV2;
	selected: ComponentV2 | null;
	onUpdateSelected: (patch: Partial<ComponentV2>) => void;
	onScreenChange: (next: ScreenConfigV2) => void;
	onDeleteSelected?: () => void;
}

const sectionTitle: CSSProperties = {
	fontSize: 12,
	fontWeight: 600,
	color: 'rgba(226,232,240,0.6)',
	letterSpacing: 0.5,
	textTransform: 'uppercase',
	margin: '18px 0 10px',
};

const row: CSSProperties = {
	display: 'grid',
	gridTemplateColumns: '96px minmax(0, 1fr)',
	gap: 8,
	alignItems: 'center',
	marginBottom: 8,
};

const twoColRow: CSSProperties = {
	display: 'grid',
	gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
	gap: 8,
	marginBottom: 8,
};

const label: CSSProperties = {
	fontSize: 12,
	color: 'rgba(226,232,240,0.75)',
};

const inputStyle: CSSProperties = {
	width: '100%',
	padding: '6px 8px',
	background: 'rgba(255,255,255,0.06)',
	border: '1px solid rgba(255,255,255,0.12)',
	borderRadius: 4,
	color: '#e2e8f0',
	fontSize: 13,
	boxSizing: 'border-box',
};

const buttonStyle: CSSProperties = {
	padding: '6px 12px',
	background: 'rgba(255,255,255,0.06)',
	border: '1px solid rgba(255,255,255,0.12)',
	borderRadius: 4,
	color: '#e2e8f0',
	cursor: 'pointer',
	fontSize: 12,
};

const panelCardStyle: CSSProperties = {
	border: '1px solid rgba(255,255,255,0.08)',
	borderRadius: 8,
	padding: 10,
	background: 'rgba(255,255,255,0.03)',
	marginBottom: 10,
};

const mutedTextStyle: CSSProperties = {
	fontSize: 12,
	color: 'rgba(148,163,184,0.85)',
	lineHeight: 1.6,
};

const actionTypeOptions: Array<{ value: ScreenComponentAction['type']; label: string }> = [
	{ value: 'set-variable', label: '写入变量' },
	{ value: 'drill-down', label: '下钻' },
	{ value: 'drill-up', label: '上卷返回' },
	{ value: 'drill-view', label: '切换钻取视图' },
	{ value: 'jump-url', label: '页面跳转' },
	{ value: 'open-panel', label: '打开详情面板' },
	{ value: 'emit-intent', label: '发出意图事件' },
];

const themeOptions: Array<{ value: ScreenTheme; label: string }> = [
	{ value: 'enterprise-dark', label: '企业暗色' },
	{ value: 'enterprise-light', label: '企业浅色' },
	{ value: 'legacy-dark', label: '经典暗色' },
	{ value: 'titanium', label: '钛金属' },
	{ value: 'glacier', label: '冰川' },
	{ value: 'light-business', label: '商务浅色' },
	{ value: 'dark-command', label: '指挥暗色' },
	{ value: 'brand-custom', label: '自定义主题' },
];

function setByPath(
	obj: Record<string, unknown> | undefined | null,
	path: string,
	value: unknown,
): Record<string, unknown> {
	const base = (obj && typeof obj === 'object') ? obj : {};
	if (!path) return { ...base };
	const parts = path.split('.');
	if (parts.length === 1) return { ...base, [path]: value };
	const [head, ...rest] = parts;
	const prev = base[head];
	const prevObj = (prev && typeof prev === 'object' && !Array.isArray(prev))
		? (prev as Record<string, unknown>)
		: {};
	return { ...base, [head]: setByPath(prevObj, rest.join('.'), value) };
}

function clampInt(value: number, min: number, max: number): number {
	if (!Number.isFinite(value)) return min;
	return Math.max(min, Math.min(max, Math.floor(value)));
}

function updateCarouselConfig(
	current: CarouselConfig | undefined,
	patch: Partial<CarouselConfig>,
): CarouselConfig {
	return {
		enabled: current?.enabled ?? false,
		intervalSeconds: current?.intervalSeconds ?? 30,
		transition: current?.transition ?? 'fade',
		transitionDuration: current?.transitionDuration ?? 800,
		loop: current?.loop ?? true,
		autoPlay: current?.autoPlay ?? false,
		...patch,
	};
}

function variablePreview(variables: ScreenGlobalVariable[] | undefined): string {
	if (!variables || variables.length === 0) {
		return '暂无变量';
	}
	const preview = variables.slice(0, 4).map((item) => item.key).join('、');
	return variables.length > 4 ? `${preview} 等 ${variables.length} 个` : preview;
}

function safeJsonStringify(value: unknown): string {
	try {
		return JSON.stringify(value ?? {}, null, 2);
	} catch {
		return '{}';
	}
}

function safeJsonParse(text: string): unknown | null {
	try {
		return JSON.parse(text);
	} catch {
		return null;
	}
}

function resolveDataSourceType(dataSource?: DataSourceConfig): 'static' | 'card' | 'api' | 'sql' | 'dataset' | 'metric' {
	const normalized = String(dataSource?.sourceType ?? dataSource?.type ?? 'static').trim().toLowerCase();
	if (normalized === 'database') return 'sql';
	if (normalized === 'card' || normalized === 'api' || normalized === 'sql' || normalized === 'dataset' || normalized === 'metric') {
		return normalized;
	}
	return 'static';
}

function resolveSqlConfig(dataSource?: DataSourceConfig): DataSourceConfig['sqlConfig'] | DataSourceConfig['databaseConfig'] | undefined {
	if (!dataSource) return undefined;
	return dataSource.sqlConfig ?? dataSource.databaseConfig;
}

function normalizeBindings(bindings: CardParameterBinding[] | undefined): CardParameterBinding[] {
	if (!Array.isArray(bindings)) return [];
	return bindings.map((item) => ({
		name: String(item?.name ?? ''),
		variableKey: item?.variableKey || undefined,
		value: item?.value ?? '',
	}));
}

function JsonEditor({
	value,
	onChange,
	rows = 8,
	placeholder,
}: {
	value: unknown;
	onChange: (next: unknown) => void;
	rows?: number;
	placeholder?: string;
}) {
	const [draft, setDraft] = useState(() => safeJsonStringify(value));

	useEffect(() => {
		setDraft(safeJsonStringify(value));
	}, [value]);

	return (
		<textarea
			value={draft}
			onChange={(e) => {
				const nextText = e.target.value;
				setDraft(nextText);
				const parsed = safeJsonParse(nextText);
				if (parsed !== null || nextText.trim() === 'null') {
					onChange(parsed);
				}
			}}
			rows={rows}
			placeholder={placeholder}
			style={{
				...inputStyle,
				fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
				fontSize: 12,
				minHeight: rows * 18,
			}}
		/>
	);
}

function getInteractionSourcePathCandidates(type: ComponentType): string[] {
	if (type === 'pie-chart' || type === 'funnel-chart') return ['name', 'value', 'percent', 'data.name'];
	if (type === 'map-chart') return ['name', 'data.name', 'data.value', 'value'];
	if (type === 'table' || type === 'scroll-board') return ['row[0]', 'row[1]', 'row[2]', 'name', 'value'];
	if (type === 'scatter-chart') return ['name', 'value', 'data[0]', 'data[1]', 'seriesName'];
	if (type === 'treemap-chart' || type === 'sunburst-chart') return ['name', 'value', 'data.name', 'treePathInfo'];
	if (type === 'radar-chart') return ['name', 'seriesName', 'value', 'data.name'];
	return ['name', 'seriesName', 'value', 'data.name', 'data.value', 'data.code'];
}

function InteractionEditorV2({
	componentType,
	globalVariables,
	value,
	onChange,
}: {
	componentType: ComponentType;
	globalVariables: ScreenGlobalVariable[];
	value?: ComponentInteractionConfig;
	onChange: (next: ComponentInteractionConfig | undefined) => void;
}) {
	if (!INTERACTION_COMPONENT_TYPES.has(componentType)) {
		return null;
	}

	const interaction: ComponentInteractionConfig = {
		enabled: false,
		mappings: value?.mappings ? [...value.mappings] : [],
		jumpEnabled: false,
		jumpUrlTemplate: '',
		jumpOpenMode: 'new-tab',
		...(value ?? {}),
	};
	const sourcePathCandidates = getInteractionSourcePathCandidates(componentType);

	const setInteraction = (next: ComponentInteractionConfig) => onChange(next);
	const updateMapping = (index: number, patch: Partial<ComponentInteractionMapping>) => {
		const nextMappings = [...interaction.mappings];
		nextMappings[index] = { ...nextMappings[index], ...patch };
		setInteraction({ ...interaction, mappings: nextMappings });
	};

	return (
		<>
			<div style={{ ...label, marginBottom: 8, color: '#cbd5f5', fontWeight: 600 }}>联动配置</div>
			<div style={row}>
				<span style={label}>启用点击联动</span>
				<label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
					<input
						type="checkbox"
						checked={interaction.enabled ?? false}
						onChange={(e) => setInteraction({ ...interaction, enabled: e.target.checked })}
					/>
					<span>开启</span>
				</label>
			</div>

			{interaction.enabled ? (
				<>
					{globalVariables.length === 0 ? (
						<div style={{ ...mutedTextStyle, marginBottom: 8 }}>
							请先在上方“变量与轮播”里创建全局变量，再把点击值映射到变量。
						</div>
					) : null}

					{interaction.mappings.map((mapping, index) => (
						<div key={`interaction-${index}`} style={panelCardStyle}>
							<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
								<span style={{ fontSize: 12, color: '#cbd5e1' }}>联动规则 {index + 1}</span>
								<button
									type="button"
									onClick={() => setInteraction({
										...interaction,
										mappings: interaction.mappings.filter((_, itemIndex) => itemIndex !== index),
									})}
									style={{ ...buttonStyle, padding: '4px 8px', color: '#fca5a5', borderColor: 'rgba(248,113,113,0.4)' }}
								>
									删除
								</button>
							</div>
							<div style={row}>
								<span style={label}>目标变量</span>
								<select
									value={mapping.variableKey || ''}
									onChange={(e) => updateMapping(index, { variableKey: e.target.value })}
									style={inputStyle}
								>
									<option value="">-- 请选择 --</option>
									{globalVariables.map((item) => (
										<option key={item.key} value={item.key}>
											{item.label || item.key} ({item.key})
										</option>
									))}
								</select>
							</div>
							<div style={row}>
								<span style={label}>取值路径</span>
								<div>
									<input
										list={`interaction-source-path-${index}`}
										value={mapping.sourcePath || 'name'}
										onChange={(e) => updateMapping(index, { sourcePath: e.target.value })}
										placeholder="name / data.name / value"
										style={inputStyle}
									/>
									<datalist id={`interaction-source-path-${index}`}>
										{sourcePathCandidates.map((item) => (
											<option key={item} value={item} />
										))}
									</datalist>
								</div>
							</div>
							<div style={twoColRow}>
								<div>
									<div style={{ ...label, marginBottom: 4 }}>值转换</div>
									<select
										value={String(mapping.transform || 'raw')}
										onChange={(e) => updateMapping(index, {
											transform: e.target.value as ComponentInteractionMapping['transform'],
										})}
										style={inputStyle}
									>
										<option value="raw">原值</option>
										<option value="string">字符串</option>
										<option value="number">数值</option>
										<option value="lowercase">转小写</option>
										<option value="uppercase">转大写</option>
									</select>
								</div>
								<div>
									<div style={{ ...label, marginBottom: 4 }}>默认值</div>
									<input
										type="text"
										value={mapping.fallbackValue || ''}
										onChange={(e) => updateMapping(index, { fallbackValue: e.target.value })}
										placeholder="取值为空时写入该值"
										style={inputStyle}
									/>
								</div>
							</div>
						</div>
					))}

					<button
						type="button"
						onClick={() => setInteraction({
							...interaction,
							mappings: [
								...interaction.mappings,
								{
									variableKey: globalVariables[0]?.key ?? '',
									sourcePath: 'name',
									transform: 'raw',
									fallbackValue: '',
								},
							],
						})}
						style={{ ...buttonStyle, width: '100%', textAlign: 'center', marginBottom: 8 }}
					>
						+ 添加联动规则
					</button>
					<div style={{ ...mutedTextStyle, marginBottom: 8 }}>
						支持路径示例：`name`、`seriesName`、`data.code`、`row[0]`。
					</div>

					<div style={row}>
						<span style={label}>启用点击跳转</span>
						<label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
							<input
								type="checkbox"
								checked={interaction.jumpEnabled === true}
								onChange={(e) => setInteraction({ ...interaction, jumpEnabled: e.target.checked })}
							/>
							<span>开启</span>
						</label>
					</div>

					{interaction.jumpEnabled === true ? (
						<>
							<ScreenJumpPicker
								value={interaction.jumpUrlTemplate || ''}
								onChange={(jumpUrlTemplate) => setInteraction({ ...interaction, jumpUrlTemplate })}
							/>
							<div style={row}>
								<span style={label}>打开方式</span>
								<select
									value={interaction.jumpOpenMode || 'new-tab'}
									onChange={(e) => setInteraction({
										...interaction,
										jumpOpenMode: e.target.value === 'self' ? 'self' : 'new-tab',
									})}
									style={inputStyle}
								>
									<option value="new-tab">新窗口</option>
									<option value="self">当前窗口</option>
								</select>
							</div>
								<div style={{ ...mutedTextStyle, marginBottom: 8 }}>
									{'支持占位符：{{name}} / {{seriesName}} / {{value}} / {{data.name}}。'}
								</div>
						</>
					) : null}
				</>
			) : null}
		</>
	);
}

function ActionEditorV2({
	componentType,
	value,
	onChange,
}: {
	componentType: ComponentType;
	value?: ScreenComponentAction[];
	onChange: (next: ScreenComponentAction[] | undefined) => void;
}) {
	if (!ACTION_COMPONENT_TYPES.has(componentType)) {
		return null;
	}

	const actions = [...(value ?? [])];
	const sourcePathCandidates = getActionSourcePathCandidates(componentType);

	const setActions = (next: ScreenComponentAction[]) => onChange(next);
	const updateAction = (index: number, patch: Partial<ScreenComponentAction>) => {
		const next = [...actions];
		next[index] = { ...next[index], ...patch };
		setActions(next);
	};
	const updateMappings = (index: number, nextMappings: ComponentInteractionMapping[]) => {
		updateAction(index, { mappings: nextMappings });
	};

	return (
		<>
			<div style={{ ...label, marginTop: 12, marginBottom: 8, color: '#cbd5f5', fontWeight: 600 }}>动作入口</div>
			{actions.length === 0 ? (
				<div style={{ ...mutedTextStyle, marginBottom: 8 }}>
					当前组件还没有动作入口。可配置详情面板、跳转、变量写入或意图事件。
				</div>
			) : null}

			{actions.map((action, index) => {
				const actionType = action.type || 'set-variable';
				const mappings = [...(action.mappings ?? [])];
				const showMappings = actionType === 'set-variable' || actionType === 'jump-url' || actionType === 'emit-intent';
				return (
					<div key={`action-${index}`} style={panelCardStyle}>
						<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
							<span style={{ fontSize: 12, color: '#cbd5e1' }}>动作 {index + 1}</span>
							<button
								type="button"
								onClick={() => setActions(actions.filter((_, itemIndex) => itemIndex !== index))}
								style={{ ...buttonStyle, padding: '4px 8px', color: '#fca5a5', borderColor: 'rgba(248,113,113,0.4)' }}
							>
								删除
							</button>
						</div>
						<div style={row}>
							<span style={label}>动作标题</span>
							<input
								type="text"
								value={action.label || ''}
								onChange={(e) => updateAction(index, { label: e.target.value })}
								placeholder="查看详情 / 发起协调 / 跳转周报"
								style={inputStyle}
							/>
						</div>
						<div style={row}>
							<span style={label}>动作类型</span>
							<select
								value={actionType}
								onChange={(e) => updateAction(index, {
									type: e.target.value as ScreenComponentAction['type'],
								})}
								style={inputStyle}
							>
								{actionTypeOptions.map((option) => (
									<option key={option.value} value={option.value}>
										{option.label}
									</option>
								))}
							</select>
						</div>

						{showMappings ? (
							<>
								{mappings.map((mapping, mappingIndex) => (
									<div
										key={`action-${index}-mapping-${mappingIndex}`}
										style={{ ...panelCardStyle, marginBottom: 8, background: 'rgba(255,255,255,0.02)' }}
									>
										<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
											<span style={{ fontSize: 12, color: '#cbd5e1' }}>变量映射 {mappingIndex + 1}</span>
											<button
												type="button"
												onClick={() => updateMappings(index, mappings.filter((_, itemIndex) => itemIndex !== mappingIndex))}
												style={{ ...buttonStyle, padding: '4px 8px', color: '#fca5a5', borderColor: 'rgba(248,113,113,0.4)' }}
											>
												删除
											</button>
										</div>
										<div style={row}>
											<span style={label}>目标变量</span>
											<input
												type="text"
												value={mapping.variableKey || ''}
												onChange={(e) => {
													const nextMappings = [...mappings];
													nextMappings[mappingIndex] = { ...nextMappings[mappingIndex], variableKey: e.target.value };
													updateMappings(index, nextMappings);
												}}
												placeholder="projectId"
												style={inputStyle}
											/>
										</div>
										<div style={row}>
											<span style={label}>取值路径</span>
											<div>
												<input
													list={`action-source-path-${index}-${mappingIndex}`}
													value={mapping.sourcePath || 'name'}
													onChange={(e) => {
														const nextMappings = [...mappings];
														nextMappings[mappingIndex] = { ...nextMappings[mappingIndex], sourcePath: e.target.value };
														updateMappings(index, nextMappings);
													}}
													placeholder="name / row[0] / data.owner"
													style={inputStyle}
												/>
												<datalist id={`action-source-path-${index}-${mappingIndex}`}>
													{sourcePathCandidates.map((item) => (
														<option key={item} value={item} />
													))}
												</datalist>
											</div>
										</div>
										<div style={twoColRow}>
											<div>
												<div style={{ ...label, marginBottom: 4 }}>值转换</div>
												<select
													value={String(mapping.transform || 'raw')}
													onChange={(e) => {
														const nextMappings = [...mappings];
														nextMappings[mappingIndex] = {
															...nextMappings[mappingIndex],
															transform: e.target.value as ComponentInteractionMapping['transform'],
														};
														updateMappings(index, nextMappings);
													}}
													style={inputStyle}
												>
													<option value="raw">原值</option>
													<option value="string">字符串</option>
													<option value="number">数值</option>
													<option value="lowercase">转小写</option>
													<option value="uppercase">转大写</option>
												</select>
											</div>
											<div>
												<div style={{ ...label, marginBottom: 4 }}>默认值</div>
												<input
													type="text"
													value={mapping.fallbackValue || ''}
													onChange={(e) => {
														const nextMappings = [...mappings];
														nextMappings[mappingIndex] = {
															...nextMappings[mappingIndex],
															fallbackValue: e.target.value,
														};
														updateMappings(index, nextMappings);
													}}
													placeholder="取值为空时写入该值"
													style={inputStyle}
												/>
											</div>
										</div>
									</div>
								))}

								<button
									type="button"
									onClick={() => updateMappings(index, [
										...mappings,
										{ variableKey: '', sourcePath: 'name', transform: 'raw', fallbackValue: '' },
									])}
									style={{ ...buttonStyle, width: '100%', textAlign: 'center', marginBottom: 8 }}
								>
									+ 添加变量映射
								</button>
							</>
						) : null}

						{actionType === 'jump-url' ? (
							<>
								<ScreenJumpPicker
									value={action.jumpUrlTemplate || ''}
									onChange={(jumpUrlTemplate) => updateAction(index, { jumpUrlTemplate })}
								/>
								<div style={row}>
									<span style={label}>打开方式</span>
									<select
										value={action.jumpOpenMode || 'new-tab'}
										onChange={(e) => updateAction(index, {
											jumpOpenMode: e.target.value === 'self' ? 'self' : 'new-tab',
										})}
										style={inputStyle}
									>
										<option value="new-tab">新窗口</option>
										<option value="self">当前窗口</option>
									</select>
								</div>
							</>
						) : null}

						{actionType === 'open-panel' ? (
							<>
								<div style={row}>
									<span style={label}>面板标题</span>
									<input
										type="text"
										value={action.panelTitle || ''}
										onChange={(e) => updateAction(index, { panelTitle: e.target.value })}
										placeholder="项目 {{name}}"
										style={inputStyle}
									/>
								</div>
								<div style={row}>
									<span style={label}>面板内容</span>
									<textarea
										rows={4}
										value={action.panelBodyTemplate || ''}
										onChange={(e) => updateAction(index, { panelBodyTemplate: e.target.value })}
										placeholder={'负责人：{{责任人}}\n状态：{{状态}}\n建议：发起协调'}
										style={{ ...inputStyle, minHeight: 96 }}
									/>
								</div>
							</>
						) : null}

						{actionType === 'emit-intent' ? (
							<>
								<div style={row}>
									<span style={label}>意图名称</span>
									<input
										type="text"
										value={action.intentName || ''}
										onChange={(e) => updateAction(index, { intentName: e.target.value })}
										placeholder="project.follow-up"
										style={inputStyle}
									/>
								</div>
								<div style={row}>
									<span style={label}>意图负载</span>
									<textarea
										rows={4}
										value={action.intentPayloadTemplate || ''}
										onChange={(e) => updateAction(index, { intentPayloadTemplate: e.target.value })}
										placeholder={'{"project":"{{name}}","owner":"{{责任人}}"}'}
										style={{ ...inputStyle, minHeight: 96 }}
									/>
								</div>
							</>
						) : null}

						{actionType === 'drill-view' ? (
							<>
								<div style={row}>
									<span style={label}>视图 ID</span>
									<input
										type="text"
										value={action.drillViewId || ''}
										onChange={(e) => updateAction(index, { drillViewId: e.target.value })}
										placeholder="detail-view"
										style={inputStyle}
									/>
								</div>
								<div style={row}>
									<span style={label}>面包屑标签</span>
									<input
										type="text"
										value={action.drillViewLabel || ''}
										onChange={(e) => updateAction(index, { drillViewLabel: e.target.value })}
										placeholder="项目详情"
										style={inputStyle}
									/>
								</div>
							</>
						) : null}

						{actionType === 'drill-down' || actionType === 'drill-up' ? (
							<div style={mutedTextStyle}>
								{actionType === 'drill-down'
									? '运行态会复用当前组件的下钻链路，并使用点击值推进到下一层。'
									: '运行态会从当前钻取层级返回上一层。'}
							</div>
						) : null}
					</div>
				);
			})}

			<button
				type="button"
				onClick={() => setActions([
					...actions,
					{ type: 'open-panel', label: '查看详情', panelTitle: '{{name}}', panelBodyTemplate: '' },
				])}
				style={{ ...buttonStyle, width: '100%', textAlign: 'center' }}
			>
				+ 添加动作入口
			</button>
		</>
	);
}

function DrillDownEditorV2({
	componentType,
	dataSource,
	value,
	onChange,
}: {
	componentType: ComponentType;
	dataSource?: DataSourceConfig;
	value?: DrillDownConfig;
	onChange: (next: DrillDownConfig | undefined) => void;
}) {
	if (!DRILL_CONFIGURABLE_TYPES.has(componentType)) {
		return null;
	}

	const cardId = dataSource?.type === 'card' ? dataSource.cardConfig?.cardId : undefined;
	if (dataSource?.type !== 'card' || !cardId || cardId <= 0) {
		return (
			<>
				<div style={{ ...label, marginTop: 12, marginBottom: 8, color: '#cbd5f5', fontWeight: 600 }}>下钻配置</div>
				<div style={mutedTextStyle}>
					下钻仅支持绑定 Card 数据源的可下钻图表。先把当前组件的数据源切到 Card，并选择有效 Card。
				</div>
			</>
		);
	}

	const enabled = value?.enabled ?? false;
	const levels = [...(value?.levels ?? [])];

	const setDrillDown = (next: DrillDownConfig) => onChange(next);
	const updateLevel = (index: number, field: keyof DrillLevel, fieldValue: string | number) => {
		const nextLevels = [...levels];
		nextLevels[index] = { ...nextLevels[index], [field]: fieldValue };
		setDrillDown({ enabled, levels: nextLevels });
	};

	return (
		<>
			<div style={{ ...label, marginTop: 12, marginBottom: 8, color: '#cbd5f5', fontWeight: 600 }}>下钻配置</div>
			<div style={row}>
				<span style={label}>启用下钻</span>
				<label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
					<input
						type="checkbox"
						checked={enabled}
						onChange={(e) => setDrillDown({ enabled: e.target.checked, levels })}
					/>
					<span>开启</span>
				</label>
			</div>

			{enabled ? (
				<>
					{levels.map((level, index) => (
						<div key={`drill-${index}`} style={panelCardStyle}>
							<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
								<span style={{ fontSize: 12, color: '#cbd5e1' }}>层级 {index + 1}</span>
								<button
									type="button"
									onClick={() => setDrillDown({
										enabled,
										levels: levels.filter((_, itemIndex) => itemIndex !== index),
									})}
									style={{ ...buttonStyle, padding: '4px 8px', color: '#fca5a5', borderColor: 'rgba(248,113,113,0.4)' }}
								>
									删除
								</button>
							</div>
							<div style={row}>
								<span style={label}>Card</span>
								<CardIdPicker
									value={level.cardId || 0}
									onChange={(nextCardId) => updateLevel(index, 'cardId', nextCardId)}
									placeholder="-- 下钻目标 --"
								/>
							</div>
							<div style={row}>
								<span style={label}>参数名</span>
								<input
									type="text"
									value={level.paramName}
									onChange={(e) => updateLevel(index, 'paramName', e.target.value)}
									placeholder="如: region"
									style={inputStyle}
								/>
							</div>
							<div style={row}>
								<span style={label}>标签</span>
								<input
									type="text"
									value={level.label}
									onChange={(e) => updateLevel(index, 'label', e.target.value)}
									placeholder="如: 地区"
									style={inputStyle}
								/>
							</div>
						</div>
					))}

					<button
						type="button"
						onClick={() => setDrillDown({
							enabled,
							levels: [...levels, { cardId: 0, paramName: '', label: '' }],
						})}
						style={{ ...buttonStyle, width: '100%', textAlign: 'center' }}
					>
						+ 添加下钻层级
					</button>
				</>
			) : null}
		</>
	);
}

function DataSourceEditor({
	dataSource,
	globalVariables,
	onChange,
}: {
	dataSource?: DataSourceConfig;
	globalVariables: ScreenGlobalVariable[];
	onChange: (next: DataSourceConfig | undefined) => void;
}) {
	const dsType = resolveDataSourceType(dataSource);
	const sqlConfig = resolveSqlConfig(dataSource);
	const cardBindings = dsType === 'card' ? normalizeBindings(dataSource?.cardConfig?.parameterBindings) : [];
	const metricBindings = dsType === 'metric' ? normalizeBindings(dataSource?.metricConfig?.parameterBindings) : [];
	const sqlBindings = dsType === 'sql' ? normalizeBindings(sqlConfig?.parameterBindings) : [];

	const setDataSource = (next: DataSourceConfig | undefined) => onChange(next);

	const updateCardBindings = (bindings: CardParameterBinding[]) => {
		setDataSource({
			type: 'card',
			sourceType: 'card',
			cardConfig: {
				...(dsType === 'card' ? dataSource?.cardConfig : {}),
				cardId: dsType === 'card' ? (dataSource?.cardConfig?.cardId ?? 0) : 0,
				parameterBindings: bindings,
			},
		});
	};

	const updateMetricBindings = (bindings: CardParameterBinding[]) => {
		const currentMetricConfig = dsType === 'metric' ? dataSource?.metricConfig : undefined;
		setDataSource({
			type: 'metric',
			sourceType: 'metric',
			refreshInterval: dsType === 'metric' ? dataSource?.refreshInterval : undefined,
			metricConfig: {
				...(currentMetricConfig ?? {}),
				cardId: currentMetricConfig?.cardId ?? 0,
				parameterBindings: bindings,
			},
		});
	};

	const updateSqlBindings = (bindings: CardParameterBinding[]) => {
		const base = resolveSqlConfig(dataSource);
		setDataSource({
			type: 'sql',
			sourceType: 'sql',
			refreshInterval: dsType === 'sql' ? dataSource?.refreshInterval : undefined,
			sqlConfig: {
				...(base ?? { query: '' }),
				query: base?.query ?? '',
				parameterBindings: bindings,
			},
		});
	};

	return (
		<>
			<div style={row}>
				<span style={label}>数据源</span>
				<select
					value={dsType}
					onChange={(e) => {
						const nextType = e.target.value as 'static' | 'card' | 'api' | 'sql' | 'dataset' | 'metric';
						if (nextType === 'static') {
							setDataSource(undefined);
							return;
						}
						if (nextType === 'card') {
							setDataSource({
								type: 'card',
								sourceType: 'card',
								cardConfig: {
									cardId: 0,
									refreshInterval: undefined,
									metricId: undefined,
									metricVersion: undefined,
									parameterBindings: [],
								},
							});
							return;
						}
						if (nextType === 'api') {
							setDataSource({
								type: 'api',
								sourceType: 'api',
								apiConfig: { url: '', method: 'GET', body: undefined },
							});
							return;
						}
						if (nextType === 'sql') {
							setDataSource({
								type: 'sql',
								sourceType: 'sql',
								sqlConfig: { query: '', databaseId: undefined, parameterBindings: [] },
							});
							return;
						}
						if (nextType === 'dataset') {
							setDataSource({
								type: 'dataset',
								sourceType: 'dataset',
								datasetConfig: { queryBody: {} },
							});
							return;
						}
						setDataSource({
							type: 'metric',
							sourceType: 'metric',
							metricConfig: { cardId: 0, parameterBindings: [] },
						});
					}}
					style={inputStyle}
				>
					<option value="static">静态</option>
					<option value="card">Card</option>
					<option value="metric">Metric</option>
					<option value="sql">SQL</option>
					<option value="dataset">Dataset</option>
					<option value="api">API</option>
				</select>
			</div>

			{dsType === 'card' ? (
				<>
					<div style={row}>
						<span style={label}>Card</span>
						<CardIdPicker
							value={dataSource?.cardConfig?.cardId ?? 0}
							onChange={(cardId) => setDataSource({
								type: 'card',
								sourceType: 'card',
								cardConfig: {
									...(dataSource?.cardConfig ?? {}),
									cardId,
								},
							})}
						/>
					</div>
					<MetricBindingEditor
						metricId={dataSource?.cardConfig?.metricId}
						metricVersion={dataSource?.cardConfig?.metricVersion}
						onMetricIdChange={(metricId) => setDataSource({
							type: 'card',
							sourceType: 'card',
							cardConfig: {
								...(dataSource?.cardConfig ?? {}),
								cardId: dataSource?.cardConfig?.cardId ?? 0,
								metricId,
								metricVersion: metricId ? dataSource?.cardConfig?.metricVersion : undefined,
							},
						})}
						onMetricVersionChange={(metricVersion) => setDataSource({
							type: 'card',
							sourceType: 'card',
							cardConfig: {
								...(dataSource?.cardConfig ?? {}),
								cardId: dataSource?.cardConfig?.cardId ?? 0,
								metricId: dataSource?.cardConfig?.metricId,
								metricVersion,
							},
						})}
					/>
					<CardParamBindingsEditor
						bindings={cardBindings}
						globalVariables={globalVariables}
						onChange={updateCardBindings}
					/>
					<div style={row}>
						<span style={label}>刷新(秒)</span>
						<input
							type="number"
							min={0}
							step={10}
							value={dataSource?.cardConfig?.refreshInterval ?? 0}
							onChange={(e) => {
								const refreshInterval = Number(e.target.value);
								setDataSource({
									type: 'card',
									sourceType: 'card',
									cardConfig: {
										...(dataSource?.cardConfig ?? {}),
										cardId: dataSource?.cardConfig?.cardId ?? 0,
										refreshInterval: refreshInterval > 0 ? refreshInterval : undefined,
									},
								});
							}}
							style={inputStyle}
						/>
					</div>
				</>
			) : null}

			{dsType === 'metric' ? (
				<>
					<div style={row}>
						<span style={label}>Card</span>
						<CardIdPicker
							value={dataSource?.metricConfig?.cardId ?? 0}
							onChange={(cardId) => setDataSource({
								type: 'metric',
								sourceType: 'metric',
								refreshInterval: dataSource?.refreshInterval,
								metricConfig: {
									...(dataSource?.metricConfig ?? {}),
									cardId,
								},
							})}
						/>
					</div>
					<MetricBindingEditor
						metricId={dataSource?.metricConfig?.metricId}
						metricVersion={dataSource?.metricConfig?.metricVersion}
						onMetricIdChange={(metricId) => setDataSource({
							type: 'metric',
							sourceType: 'metric',
							refreshInterval: dataSource?.refreshInterval,
							metricConfig: {
								...(dataSource?.metricConfig ?? {}),
								cardId: dataSource?.metricConfig?.cardId ?? 0,
								metricId,
								metricVersion: metricId ? dataSource?.metricConfig?.metricVersion : undefined,
							},
						})}
						onMetricVersionChange={(metricVersion) => setDataSource({
							type: 'metric',
							sourceType: 'metric',
							refreshInterval: dataSource?.refreshInterval,
							metricConfig: {
								...(dataSource?.metricConfig ?? {}),
								cardId: dataSource?.metricConfig?.cardId ?? 0,
								metricId: dataSource?.metricConfig?.metricId,
								metricVersion,
							},
						})}
					/>
					<CardParamBindingsEditor
						bindings={metricBindings}
						globalVariables={globalVariables}
						onChange={updateMetricBindings}
					/>
					<div style={row}>
						<span style={label}>刷新(秒)</span>
						<input
							type="number"
							min={0}
							step={10}
							value={dataSource?.refreshInterval ?? 0}
							onChange={(e) => {
								const refreshInterval = Number(e.target.value);
								setDataSource({
									type: 'metric',
									sourceType: 'metric',
									refreshInterval: refreshInterval > 0 ? refreshInterval : undefined,
									metricConfig: {
										...(dataSource?.metricConfig ?? {}),
										cardId: dataSource?.metricConfig?.cardId ?? 0,
									},
								});
							}}
							style={inputStyle}
						/>
					</div>
				</>
			) : null}

			{dsType === 'api' ? (
				<>
					<div style={row}>
						<span style={label}>URL</span>
						<input
							type="text"
							value={dataSource?.apiConfig?.url ?? ''}
							onChange={(e) => setDataSource({
								type: 'api',
								sourceType: 'api',
								refreshInterval: dataSource?.refreshInterval,
								apiConfig: {
									...(dataSource?.apiConfig ?? { method: 'GET' }),
									url: e.target.value,
									method: dataSource?.apiConfig?.method ?? 'GET',
								},
							})}
							placeholder="/bi/api/card/1/query 或 https://..."
							style={inputStyle}
						/>
					</div>
					<div style={row}>
						<span style={label}>方法</span>
						<select
							value={dataSource?.apiConfig?.method ?? 'GET'}
							onChange={(e) => setDataSource({
								type: 'api',
								sourceType: 'api',
								refreshInterval: dataSource?.refreshInterval,
								apiConfig: {
									...(dataSource?.apiConfig ?? {}),
									url: dataSource?.apiConfig?.url ?? '',
									method: e.target.value as 'GET' | 'POST',
								},
							})}
							style={inputStyle}
						>
							<option value="GET">GET</option>
							<option value="POST">POST</option>
						</select>
					</div>
					<div style={row}>
						<span style={label}>Body</span>
						<textarea
							value={dataSource?.apiConfig?.body ?? ''}
							onChange={(e) => setDataSource({
								type: 'api',
								sourceType: 'api',
								refreshInterval: dataSource?.refreshInterval,
								apiConfig: {
									...(dataSource?.apiConfig ?? {}),
									url: dataSource?.apiConfig?.url ?? '',
									method: dataSource?.apiConfig?.method ?? 'GET',
									body: e.target.value,
								},
							})}
							rows={4}
							style={{ ...inputStyle, minHeight: 96 }}
						/>
					</div>
					<div style={row}>
						<span style={label}>刷新(秒)</span>
						<input
							type="number"
							min={0}
							step={10}
							value={dataSource?.refreshInterval ?? 0}
							onChange={(e) => {
								const refreshInterval = Number(e.target.value);
								setDataSource({
									type: 'api',
									sourceType: 'api',
									refreshInterval: refreshInterval > 0 ? refreshInterval : undefined,
									apiConfig: {
										...(dataSource?.apiConfig ?? {}),
										url: dataSource?.apiConfig?.url ?? '',
										method: dataSource?.apiConfig?.method ?? 'GET',
									},
								});
							}}
							style={inputStyle}
						/>
					</div>
				</>
			) : null}

			{dsType === 'sql' ? (
				<>
					<div style={row}>
						<span style={label}>数据库</span>
						<DatabaseIdPicker
							value={sqlConfig?.databaseId ?? 0}
							onChange={(databaseId) => setDataSource({
								type: 'sql',
								sourceType: 'sql',
								refreshInterval: dataSource?.refreshInterval,
								sqlConfig: {
									...(sqlConfig ?? { query: '' }),
									databaseId: databaseId > 0 ? databaseId : undefined,
									query: sqlConfig?.query ?? '',
								},
							})}
						/>
					</div>
					<div style={row}>
						<span style={label}>数据库ID</span>
						<input
							type="number"
							min={0}
							value={sqlConfig?.databaseId ?? 0}
							onChange={(e) => {
								const n = Number(e.target.value);
								setDataSource({
									type: 'sql',
									sourceType: 'sql',
									refreshInterval: dataSource?.refreshInterval,
									sqlConfig: {
										...(sqlConfig ?? { query: '' }),
										databaseId: Number.isFinite(n) && n > 0 ? n : undefined,
										query: sqlConfig?.query ?? '',
									},
								});
							}}
							style={inputStyle}
						/>
					</div>
					<div style={row}>
						<span style={label}>SQL</span>
						<textarea
							value={sqlConfig?.query ?? ''}
							onChange={(e) => setDataSource({
								type: 'sql',
								sourceType: 'sql',
								refreshInterval: dataSource?.refreshInterval,
								sqlConfig: {
									...(sqlConfig ?? { query: '' }),
									query: e.target.value,
								},
							})}
							rows={6}
							style={{ ...inputStyle, minHeight: 140 }}
						/>
					</div>
					<div style={twoColRow}>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>最大行数</div>
							<input
								type="number"
								min={1}
								step={100}
								value={sqlConfig?.maxRows ?? 2000}
								onChange={(e) => {
									const maxRows = Number(e.target.value);
									setDataSource({
										type: 'sql',
										sourceType: 'sql',
										refreshInterval: dataSource?.refreshInterval,
										sqlConfig: {
											...(sqlConfig ?? { query: '' }),
											query: sqlConfig?.query ?? '',
											maxRows: Number.isFinite(maxRows) && maxRows > 0 ? maxRows : undefined,
										},
									});
								}}
								style={inputStyle}
							/>
						</div>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>超时(秒)</div>
							<input
								type="number"
								min={1}
								step={5}
								value={sqlConfig?.queryTimeoutSeconds ?? 60}
								onChange={(e) => {
									const timeout = Number(e.target.value);
									setDataSource({
										type: 'sql',
										sourceType: 'sql',
										refreshInterval: dataSource?.refreshInterval,
										sqlConfig: {
											...(sqlConfig ?? { query: '' }),
											query: sqlConfig?.query ?? '',
											queryTimeoutSeconds: Number.isFinite(timeout) && timeout > 0 ? timeout : undefined,
										},
									});
								}}
								style={inputStyle}
							/>
						</div>
					</div>
					<CardParamBindingsEditor
						bindings={sqlBindings}
						globalVariables={globalVariables}
						onChange={updateSqlBindings}
					/>
					<div style={row}>
						<span style={label}>刷新(秒)</span>
						<input
							type="number"
							min={0}
							step={10}
							value={dataSource?.refreshInterval ?? 0}
							onChange={(e) => {
								const refreshInterval = Number(e.target.value);
								setDataSource({
									type: 'sql',
									sourceType: 'sql',
									refreshInterval: refreshInterval > 0 ? refreshInterval : undefined,
									sqlConfig: sqlConfig
										? { ...sqlConfig, query: sqlConfig.query ?? '' }
										: { query: '' },
								});
							}}
							style={inputStyle}
						/>
					</div>
				</>
			) : null}

			{dsType === 'dataset' ? (
				<>
					<div style={row}>
						<span style={label}>QueryBody</span>
							<JsonEditor
								value={dataSource?.datasetConfig?.queryBody ?? {}}
								onChange={(queryBody) => setDataSource({
									type: 'dataset',
									sourceType: 'dataset',
									refreshInterval: dataSource?.refreshInterval,
									datasetConfig: {
										queryBody: (queryBody && typeof queryBody === 'object')
											? queryBody as Record<string, unknown>
											: {},
									},
								})}
							rows={8}
							placeholder='{"database":1,"type":"native","native":{"query":"select 1"}}'
						/>
					</div>
					<div style={row}>
						<span style={label}>刷新(秒)</span>
						<input
							type="number"
							min={0}
							step={10}
							value={dataSource?.refreshInterval ?? 0}
							onChange={(e) => {
								const refreshInterval = Number(e.target.value);
								setDataSource({
									type: 'dataset',
									sourceType: 'dataset',
									refreshInterval: refreshInterval > 0 ? refreshInterval : undefined,
									datasetConfig: dataSource?.datasetConfig ?? { queryBody: {} },
								});
							}}
							style={inputStyle}
						/>
					</div>
				</>
			) : null}
		</>
	);
}

export function PropertyPanelV2({
	screen,
	selected,
	onUpdateSelected,
	onScreenChange,
	onDeleteSelected,
}: PropertyPanelV2Props) {
	const [showVariableManager, setShowVariableManager] = useState(false);

	const cols = screen.layout?.cols ?? 12;
	const schema = useMemo(
		() => (selected ? COMPONENT_CONFIG_SCHEMAS[selected.type as ComponentType] : undefined),
		[selected],
	);
	const isPageScoped = (screen.pages?.length ?? 0) > 0;

	return (
		<div
			style={{
				flex: 1,
				minHeight: 0,
				padding: '12px 16px',
				color: '#e2e8f0',
				background: '#161922',
				overflow: 'auto',
			}}
		>
			<div style={sectionTitle}>大屏</div>
			<div style={row}>
				<span style={label}>主题</span>
				<select
					value={screen.theme ?? 'enterprise-dark'}
					onChange={(e) => onScreenChange({ ...screen, theme: e.target.value as ScreenTheme })}
					style={inputStyle}
				>
					{themeOptions.map((option) => (
						<option key={option.value} value={option.value}>
							{option.label}
						</option>
					))}
				</select>
			</div>
			<div style={row}>
				<span style={label}>列数 (cols)</span>
				<input
					type="number"
					min={1}
					max={48}
					step={1}
					value={cols}
					onChange={(e) => {
						const nextCols = clampInt(Number(e.target.value) || 12, 1, 48);
						onScreenChange({
							...screen,
							layout: { ...screen.layout, cols: nextCols },
							components: screen.components.map((component) => {
								const nextX = Math.min(component.layout.x, Math.max(0, nextCols - 1));
								const nextW = Math.min(component.layout.w, Math.max(1, nextCols - nextX));
								return {
									...component,
									layout: { ...component.layout, x: nextX, w: nextW },
								};
							}),
						});
					}}
					style={inputStyle}
				/>
			</div>
			<div style={row}>
				<span style={label}>行高</span>
				<select
					value={screen.layout?.rowHeight === 'auto' ? 'auto' : 'fixed'}
					onChange={(e) => {
						const mode = e.target.value;
						onScreenChange({
							...screen,
							layout: {
								...screen.layout,
								rowHeight: mode === 'auto' ? 'auto' : 40,
							},
						});
					}}
					style={inputStyle}
				>
					<option value="auto">auto（按 viewport 铺满）</option>
					<option value="fixed">固定像素</option>
				</select>
			</div>
			{screen.layout?.rowHeight !== 'auto' ? (
				<div style={row}>
					<span style={label}>行高 (px)</span>
					<input
						type="number"
						min={12}
						max={240}
						step={4}
						value={Number(screen.layout?.rowHeight ?? 40)}
						onChange={(e) => {
							const rowHeight = clampInt(Number(e.target.value) || 40, 12, 240);
							onScreenChange({ ...screen, layout: { ...screen.layout, rowHeight } });
						}}
						style={inputStyle}
					/>
				</div>
			) : null}
			<div style={row}>
				<span style={label}>间距 (px)</span>
				<input
					type="number"
					min={0}
					max={48}
					value={screen.layout?.gap ?? 12}
					onChange={(e) => {
						const gap = clampInt(Number(e.target.value) || 0, 0, 48);
						onScreenChange({ ...screen, layout: { ...screen.layout, gap } });
					}}
					style={inputStyle}
				/>
			</div>
			<div style={row}>
				<span style={label}>{isPageScoped ? '当前页背景色' : '背景色'}</span>
				<input
					type="text"
					value={screen.backgroundColor ?? ''}
					placeholder="#1e1f26"
					onChange={(e) => onScreenChange({ ...screen, backgroundColor: e.target.value })}
					style={inputStyle}
				/>
			</div>
			<div style={row}>
				<span style={label}>{isPageScoped ? '当前页背景图' : '背景图'}</span>
				<input
					type="text"
					value={screen.backgroundImage ?? ''}
					placeholder="https://..."
					onChange={(e) => onScreenChange({ ...screen, backgroundImage: e.target.value || undefined })}
					style={inputStyle}
				/>
			</div>
			{screen.referenceViewport ? (
				<div style={{ fontSize: 12, color: 'rgba(148,163,184,0.85)', lineHeight: 1.6 }}>
					参考视口：{screen.referenceViewport.width} × {screen.referenceViewport.height}
				</div>
			) : null}

			<div style={sectionTitle}>变量与轮播</div>
			<div style={row}>
				<span style={label}>全局变量</span>
				<div>
					<div style={{ fontSize: 12, color: 'rgba(148,163,184,0.85)', marginBottom: 6 }}>
						{variablePreview(screen.globalVariables)}
					</div>
					<button type="button" onClick={() => setShowVariableManager(true)} style={buttonStyle}>
						管理变量
					</button>
				</div>
			</div>
			<div style={row}>
				<span style={label}>启用轮播</span>
				<select
					value={screen.carouselConfig?.enabled === true ? 'true' : 'false'}
					onChange={(e) => onScreenChange({
						...screen,
						carouselConfig: updateCarouselConfig(screen.carouselConfig, { enabled: e.target.value === 'true' }),
					})}
					style={inputStyle}
				>
					<option value="false">关闭</option>
					<option value="true">开启</option>
				</select>
			</div>
			{screen.carouselConfig?.enabled ? (
				<>
					<div style={twoColRow}>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>自动播放</div>
							<select
								value={screen.carouselConfig?.autoPlay === true ? 'true' : 'false'}
								onChange={(e) => onScreenChange({
									...screen,
									carouselConfig: updateCarouselConfig(screen.carouselConfig, { autoPlay: e.target.value === 'true' }),
								})}
								style={inputStyle}
							>
								<option value="false">手动</option>
								<option value="true">自动</option>
							</select>
						</div>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>循环</div>
							<select
								value={screen.carouselConfig?.loop === false ? 'false' : 'true'}
								onChange={(e) => onScreenChange({
									...screen,
									carouselConfig: updateCarouselConfig(screen.carouselConfig, { loop: e.target.value === 'true' }),
								})}
								style={inputStyle}
							>
								<option value="true">循环</option>
								<option value="false">到末页停止</option>
							</select>
						</div>
					</div>
					<div style={twoColRow}>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>轮播间隔 (s)</div>
							<input
								type="number"
								min={1}
								max={3600}
								value={screen.carouselConfig?.intervalSeconds ?? 30}
								onChange={(e) => onScreenChange({
									...screen,
									carouselConfig: updateCarouselConfig(
										screen.carouselConfig,
										{ intervalSeconds: clampInt(Number(e.target.value) || 30, 1, 3600) },
									),
								})}
								style={inputStyle}
							/>
						</div>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>过渡动画</div>
							<select
								value={screen.carouselConfig?.transition ?? 'fade'}
								onChange={(e) => onScreenChange({
									...screen,
									carouselConfig: updateCarouselConfig(
										screen.carouselConfig,
										{ transition: e.target.value as CarouselConfig['transition'] },
									),
								})}
								style={inputStyle}
							>
								<option value="fade">fade</option>
								<option value="slide-left">slide-left</option>
								<option value="slide-up">slide-up</option>
								<option value="none">none</option>
							</select>
						</div>
					</div>
					<div style={row}>
						<span style={label}>过渡时长 (ms)</span>
						<input
							type="number"
							min={0}
							max={10000}
							step={50}
							value={screen.carouselConfig?.transitionDuration ?? 800}
							onChange={(e) => onScreenChange({
								...screen,
								carouselConfig: updateCarouselConfig(
									screen.carouselConfig,
									{ transitionDuration: clampInt(Number(e.target.value) || 800, 0, 10000) },
								),
							})}
							style={inputStyle}
						/>
					</div>
				</>
			) : null}

			{selected ? (
				<>
					<div style={sectionTitle}>组件 · {selected.name || selected.type}</div>
					<div style={row}>
						<span style={label}>名称</span>
						<input
							type="text"
							value={selected.name ?? ''}
							onChange={(e) => onUpdateSelected({ name: e.target.value })}
							style={inputStyle}
						/>
					</div>
					<div style={row}>
						<span style={label}>可见</span>
						<select
							value={selected.visible === false ? 'false' : 'true'}
							onChange={(e) => onUpdateSelected({ visible: e.target.value === 'true' })}
							style={inputStyle}
						>
							<option value="true">显示</option>
							<option value="false">隐藏</option>
						</select>
					</div>
					<div style={row}>
						<span style={label}>锁定</span>
						<select
							value={selected.static === true ? 'true' : 'false'}
							onChange={(e) => onUpdateSelected({ static: e.target.value === 'true' })}
							style={inputStyle}
						>
							<option value="false">可拖拽/缩放</option>
							<option value="true">锁定</option>
						</select>
					</div>

					<div style={sectionTitle}>设备可见性</div>
					<div style={twoColRow}>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>PC</div>
							<select
								value={selected.visibleByDevice?.pc === false ? 'false' : 'true'}
								onChange={(e) => onUpdateSelected({
									visibleByDevice: {
										...selected.visibleByDevice,
										pc: e.target.value === 'true',
									},
								})}
								style={inputStyle}
							>
								<option value="true">显示</option>
								<option value="false">隐藏</option>
							</select>
						</div>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>Tablet</div>
							<select
								value={selected.visibleByDevice?.tablet === false ? 'false' : 'true'}
								onChange={(e) => onUpdateSelected({
									visibleByDevice: {
										...selected.visibleByDevice,
										tablet: e.target.value === 'true',
									},
								})}
								style={inputStyle}
							>
								<option value="true">显示</option>
								<option value="false">隐藏</option>
							</select>
						</div>
					</div>
					<div style={row}>
						<span style={label}>Mobile</span>
						<select
							value={selected.visibleByDevice?.mobile === false ? 'false' : 'true'}
							onChange={(e) => onUpdateSelected({
								visibleByDevice: {
									...selected.visibleByDevice,
									mobile: e.target.value === 'true',
								},
							})}
							style={inputStyle}
						>
							<option value="true">显示</option>
							<option value="false">隐藏</option>
						</select>
					</div>

					<div style={sectionTitle}>位置（grid units）</div>
					<div style={twoColRow}>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>X</div>
							<input
								type="number"
								min={0}
								max={Math.max(0, cols - 1)}
								value={selected.layout.x}
								onChange={(e) => {
									const nextX = clampInt(Number(e.target.value) || 0, 0, Math.max(0, cols - 1));
									const nextW = Math.min(selected.layout.w, Math.max(1, cols - nextX));
									onUpdateSelected({
										layout: { ...selected.layout, x: nextX, w: nextW },
									});
								}}
								style={inputStyle}
							/>
						</div>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>Y</div>
							<input
								type="number"
								min={0}
								value={selected.layout.y}
								onChange={(e) => onUpdateSelected({
									layout: { ...selected.layout, y: Math.max(0, Number(e.target.value) || 0) },
								})}
								style={inputStyle}
							/>
						</div>
					</div>
					<div style={twoColRow}>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>W</div>
							<input
								type="number"
								min={1}
								max={Math.max(1, cols - selected.layout.x)}
								value={selected.layout.w}
								onChange={(e) => onUpdateSelected({
									layout: {
										...selected.layout,
										w: clampInt(Number(e.target.value) || 1, 1, Math.max(1, cols - selected.layout.x)),
									},
								})}
								style={inputStyle}
							/>
						</div>
						<div>
							<div style={{ ...label, marginBottom: 4 }}>H</div>
							<input
								type="number"
								min={1}
								value={selected.layout.h}
								onChange={(e) => onUpdateSelected({
									layout: { ...selected.layout, h: Math.max(1, Number(e.target.value) || 1) },
								})}
								style={inputStyle}
							/>
						</div>
					</div>

					<div style={sectionTitle}>数据源</div>
						<DataSourceEditor
							dataSource={selected.dataSource}
							globalVariables={screen.globalVariables ?? []}
							onChange={(dataSource) => onUpdateSelected({ dataSource })}
						/>

						<div style={sectionTitle}>交互 / 动作 / 下钻</div>
						<InteractionEditorV2
							componentType={selected.type as ComponentType}
							globalVariables={screen.globalVariables ?? []}
							value={selected.interaction}
							onChange={(interaction) => onUpdateSelected({ interaction })}
						/>
						<ActionEditorV2
							componentType={selected.type as ComponentType}
							value={selected.actions}
							onChange={(actions) => onUpdateSelected({ actions })}
						/>
						<DrillDownEditorV2
							componentType={selected.type as ComponentType}
							dataSource={selected.dataSource}
							value={selected.drillDown}
							onChange={(drillDown) => onUpdateSelected({ drillDown })}
						/>
						{!INTERACTION_COMPONENT_TYPES.has(selected.type as ComponentType)
							&& !ACTION_COMPONENT_TYPES.has(selected.type as ComponentType)
							&& !DRILL_CONFIGURABLE_TYPES.has(selected.type as ComponentType) ? (
								<div style={mutedTextStyle}>
									当前组件没有专用的联动、动作或下钻面板，下方保留原始 JSON 兜底编辑。
								</div>
							) : null}

						<div style={{ ...label, marginTop: 12, marginBottom: 6, color: '#cbd5f5', fontWeight: 600 }}>原始行为 JSON</div>
						<div style={{ ...label, marginBottom: 6 }}>interaction</div>
						<JsonEditor
							value={selected.interaction ?? {}}
							onChange={(interaction) => onUpdateSelected({
								interaction: (interaction && typeof interaction === 'object')
									? interaction as ComponentInteractionConfig
									: undefined,
							})}
							rows={6}
						/>
						<div style={{ ...label, marginTop: 8, marginBottom: 6 }}>actions</div>
						<JsonEditor
							value={selected.actions ?? []}
							onChange={(actions) => onUpdateSelected({
								actions: Array.isArray(actions) ? actions as ScreenComponentAction[] : undefined,
							})}
							rows={6}
						/>
						<div style={{ ...label, marginTop: 8, marginBottom: 6 }}>drillDown</div>
						<JsonEditor
							value={selected.drillDown ?? {}}
							onChange={(drillDown) => onUpdateSelected({
								drillDown: (drillDown && typeof drillDown === 'object')
									? drillDown as DrillDownConfig
									: undefined,
							})}
							rows={5}
						/>

					<div style={sectionTitle}>组件配置</div>
					{schema ? (
						<div style={{ marginBottom: 12 }}>
							<SchemaConfigRenderer
								schema={schema}
								config={(selected.config as Record<string, unknown>) ?? {}}
								onChange={(key, value) => {
									onUpdateSelected({
										config: setByPath(
											selected.config as Record<string, unknown>,
											key,
											value,
										),
									});
								}}
								theme={screen.theme}
							/>
						</div>
					) : (
						<div style={{ fontSize: 12, color: 'rgba(148,163,184,0.8)', marginBottom: 8 }}>
							当前组件还没有 schema 化属性，下面保留 JSON 兜底编辑。
						</div>
					)}

					<div style={sectionTitle}>配置 JSON</div>
					<textarea
						value={JSON.stringify(selected.config, null, 2)}
						onChange={(e) => {
							try {
								const parsed = JSON.parse(e.target.value);
								if (parsed && typeof parsed === 'object') {
									onUpdateSelected({ config: parsed });
								}
							} catch {
								// ignore invalid intermediate input
							}
						}}
						rows={10}
						style={{
							...inputStyle,
							fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
							fontSize: 12,
							minHeight: 180,
						}}
					/>

					{onDeleteSelected ? (
						<button
							type="button"
							onClick={onDeleteSelected}
							style={{
								...buttonStyle,
								marginTop: 12,
								background: 'rgba(248, 113, 113, 0.15)',
								color: '#fecaca',
								border: '1px solid rgba(248, 113, 113, 0.4)',
							}}
						>
							删除组件
						</button>
					) : null}
				</>
			) : (
				<div style={{ marginTop: 24, fontSize: 12, color: 'rgba(148,163,184,0.8)', lineHeight: 1.8 }}>
					当前未选中组件。左侧拖入组件后，可在这里编辑布局、变量、轮播和组件属性。
				</div>
			)}

			<GlobalVariableManager
				open={showVariableManager}
				variables={screen.globalVariables ?? []}
				onClose={() => setShowVariableManager(false)}
				onChange={(next) => onScreenChange({ ...screen, globalVariables: next })}
			/>
		</div>
	);
}
