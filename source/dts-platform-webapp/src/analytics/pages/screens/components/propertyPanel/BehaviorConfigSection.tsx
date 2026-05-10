import { Plus, Trash2 } from 'lucide-react';
import type {
    ComponentInteractionConfig,
    ComponentInteractionMapping,
    DrillDownConfig,
    DrillLevel,
    ScreenComponent,
    ScreenComponentAction,
    ScreenGlobalVariable,
} from '../../types';
import { CardIdPicker } from '../CardIdPicker';
import {
    ACTION_COMPONENT_TYPES,
    DRILL_CONFIGURABLE_TYPES,
    INTERACTION_COMPONENT_TYPES,
    getActionSourcePathCandidates,
} from './helpers';
import { ScreenJumpPicker } from './ScreenJumpPicker';

export function renderInteractionConfig(
    component: ScreenComponent,
    globalVariables: ScreenGlobalVariable[],
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    options?: { embedded?: boolean },
) {
    if (!INTERACTION_COMPONENT_TYPES.has(component.type)) {
        return null;
    }

    const defaultInteraction: ComponentInteractionConfig = {
        enabled: false,
        mappings: [],
        jumpEnabled: false,
        jumpUrlTemplate: '',
        jumpOpenMode: 'new-tab',
    };
    const interaction: ComponentInteractionConfig = component.interaction ?? defaultInteraction;
    const mappings = interaction.mappings ?? [];
    const sourcePathCandidates = (() => {
        const t = component.type;
        if (t === 'pie-chart' || t === 'funnel-chart') return ['name', 'value', 'percent', 'data.name'];
        if (t === 'map-chart') return ['name', 'data.name', 'data.value', 'value'];
        if (t === 'table' || t === 'scroll-board') return ['row[0]', 'row[1]', 'row[2]', 'name', 'value'];
        if (t === 'scatter-chart') return ['name', 'value', 'data[0]', 'data[1]', 'seriesName'];
        if (t === 'treemap-chart' || t === 'sunburst-chart') return ['name', 'value', 'data.name', 'treePathInfo'];
        if (t === 'radar-chart') return ['name', 'seriesName', 'value', 'data.name'];
        return ['name', 'seriesName', 'value', 'data.name', 'data.value', 'data.code'];
    })();

    const setInteraction = (next: ComponentInteractionConfig) => {
        updateComponent(component.id, { interaction: next });
    };

    const updateMapping = (index: number, patch: Partial<ComponentInteractionMapping>) => {
        const next = [...mappings];
        next[index] = { ...next[index], ...patch };
        setInteraction({ ...interaction, mappings: next });
    };

    const content = (
        <>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">启用点击联动</label>
                <input
                    type="checkbox"
                    checked={interaction.enabled ?? false}
                    onChange={(e) => setInteraction({ ...interaction, enabled: e.target.checked })}
                />
            </div>

            {interaction.enabled && (
                <>
                    {globalVariables.length === 0 && (
                        <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
                            请先在顶部“变量”里创建全局变量。
                        </div>
                    )}

                    {mappings.map((mapping, index) => (
                        <div
                            key={`interaction-${index}`}
                            style={{
                                border: '1px solid rgba(255,255,255,0.06)',
                                borderRadius: 4,
                                padding: 8,
                                marginBottom: 8,
                            }}
                        >
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">目标变量</label>
                                <select
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={mapping.variableKey || ''}
                                    onChange={(e) => updateMapping(index, { variableKey: e.target.value })}
                                >
                                    <option value="">-- 请选择 --</option>
                                    {globalVariables.map((item) => (
                                        <option key={item.key} value={item.key}>
                                            {item.label || item.key} ({item.key})
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">取值路径</label>
                                <input
                                    list={`interaction-source-path-${index}`}
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={mapping.sourcePath || 'name'}
                                    onChange={(e) => updateMapping(index, { sourcePath: e.target.value })}
                                    placeholder="name / data.name / value"
                                />
                                <datalist id={`interaction-source-path-${index}`}>
                                    {sourcePathCandidates.map((item) => (
                                        <option key={item} value={item} />
                                    ))}
                                </datalist>
                            </div>

                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">值转换</label>
                                <select
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={String(mapping.transform || 'raw')}
                                    onChange={(e) => updateMapping(index, { transform: e.target.value as ComponentInteractionMapping['transform'] })}
                                >
                                    <option value="raw">原值</option>
                                    <option value="string">字符串</option>
                                    <option value="number">数值</option>
                                    <option value="lowercase">转小写</option>
                                    <option value="uppercase">转大写</option>
                                </select>
                            </div>

                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">默认值</label>
                                <input
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={mapping.fallbackValue || ''}
                                    onChange={(e) => updateMapping(index, { fallbackValue: e.target.value })}
                                    placeholder="取值为空时写入该值"
                                />
                            </div>

                            <button
                                type="button"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                onClick={() => setInteraction({ ...interaction, mappings: mappings.filter((_, i) => i !== index) })}
                                style={{ width: '100%', cursor: 'pointer', textAlign: 'center', color: '#ef4444', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                            >
                                <Trash2 size={13} aria-hidden="true" />
                                删除联动规则
                            </button>
                        </div>
                    ))}

                    <button
                        type="button"
                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                        onClick={() => setInteraction({
                            ...interaction,
                            mappings: [...mappings, {
                                variableKey: globalVariables[0]?.key ?? '',
                                sourcePath: 'name',
                                transform: 'raw',
                                fallbackValue: '',
                            }],
                        })}
                        style={{ width: '100%', cursor: 'pointer', textAlign: 'center', color: '#6366f1', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                    >
                        <Plus size={13} aria-hidden="true" />
                        添加联动规则
                    </button>
                    <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 6, lineHeight: 1.5 }}>
                        支持自定义路径，例如 <code>data.code</code>；可对值做数值/大小写转换，并设置空值回退。
                    </div>

                    <div className="property-row flex items-center mb-3" style={{ marginTop: 10 }}>
                        <label className="property-label w-20 text-xs text-text-secondary">启用点击跳转</label>
                        <input
                            type="checkbox"
                            checked={interaction.jumpEnabled === true}
                            onChange={(e) => setInteraction({ ...interaction, jumpEnabled: e.target.checked })}
                        />
                    </div>

                    {interaction.jumpEnabled === true && (
                        <>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">跳转链接模板</label>
                                <input
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={interaction.jumpUrlTemplate || ''}
                                    onChange={(e) => setInteraction({ ...interaction, jumpUrlTemplate: e.target.value })}
                                    placeholder="https://host/path?name={{name}}&value={{value}}"
                                />
                            </div>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">打开方式</label>
                                <select
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={interaction.jumpOpenMode || 'new-tab'}
                                    onChange={(e) => setInteraction({
                                        ...interaction,
                                        jumpOpenMode: e.target.value === 'self' ? 'self' : 'new-tab',
                                    })}
                                >
                                    <option value="new-tab">新窗口</option>
                                    <option value="self">当前窗口</option>
                                </select>
                            </div>
                            <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 4 }}>
                                支持占位符: {'{{name}} / {{seriesName}} / {{value}} / {{data.name}}'}
                            </div>
                        </>
                    )}
                </>
            )}
        </>
    );

    if (options?.embedded) {
        return content;
    }
    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">联动配置</div>
            {content}
        </div>
    );
}

export function renderActionConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    options?: { embedded?: boolean },
) {
    if (!ACTION_COMPONENT_TYPES.has(component.type)) {
        return null;
    }

    const actions: ScreenComponentAction[] = component.actions ?? [];
    const sourcePathCandidates = getActionSourcePathCandidates(component.type);

    const setActions = (next: ScreenComponentAction[]) => {
        updateComponent(component.id, { actions: next });
    };

    const updateAction = (index: number, patch: Partial<ScreenComponentAction>) => {
        const next = [...actions];
        next[index] = { ...next[index], ...patch };
        setActions(next);
    };

    const updateMappings = (index: number, nextMappings: ComponentInteractionMapping[]) => {
        updateAction(index, { mappings: nextMappings });
    };

    const content = (
        <>
            {actions.length === 0 ? (
                <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
                    当前组件还没有动作入口。适合配置详情面板、跳转、变量写入或意图事件。
                </div>
            ) : null}

            {actions.map((action, index) => {
                const actionType = action.type || 'set-variable';
                const mappings = action.mappings ?? [];
                const showMappings = actionType === 'set-variable' || actionType === 'jump-url' || actionType === 'emit-intent';
                return (
                    <div
                        key={`action-${index}`}
                        className="border border-border-default rounded-[10px] p-2.5 mb-2.5 bg-surface-muted/40"
                    >
                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">动作标题</label>
                            <input
                                type="text"
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={action.label || ''}
                                onChange={(e) => updateAction(index, { label: e.target.value })}
                                placeholder="查看详情 / 发起协调 / 跳转周报"
                            />
                        </div>

                        <div className="property-row flex items-center mb-3">
                            <label className="property-label w-20 text-xs text-text-secondary">动作类型</label>
                            <select
                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                value={actionType}
                                onChange={(e) => updateAction(index, { type: e.target.value as ScreenComponentAction['type'] })}
                            >
                                <option value="set-variable">写入变量</option>
                                <option value="drill-down">下钻</option>
                                <option value="drill-up">上卷返回</option>
                                <option value="jump-url">页面跳转</option>
                                <option value="open-panel">打开详情面板</option>
                                <option value="emit-intent">发出意图事件</option>
                            </select>
                        </div>

                        {showMappings ? (
                            <>
                                {mappings.map((mapping, mappingIndex) => (
                                    <div
                                        key={`action-${index}-mapping-${mappingIndex}`}
                                        className="border border-dashed border-border-default rounded-lg p-2 mb-2"
                                    >
                                        <div className="property-row flex items-center mb-3">
                                            <label className="property-label w-20 text-xs text-text-secondary">目标变量</label>
                                            <input
                                                type="text"
                                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                                value={mapping.variableKey || ''}
                                                onChange={(e) => {
                                                    const next = [...mappings];
                                                    next[mappingIndex] = { ...next[mappingIndex], variableKey: e.target.value };
                                                    updateMappings(index, next);
                                                }}
                                                placeholder="projectId"
                                            />
                                        </div>
                                        <div className="property-row flex items-center mb-3">
                                            <label className="property-label w-20 text-xs text-text-secondary">取值路径</label>
                                            <input
                                                list={`action-source-path-${index}-${mappingIndex}`}
                                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                                value={mapping.sourcePath || ''}
                                                onChange={(e) => {
                                                    const next = [...mappings];
                                                    next[mappingIndex] = { ...next[mappingIndex], sourcePath: e.target.value };
                                                    updateMappings(index, next);
                                                }}
                                                placeholder="name / row[0] / data.owner"
                                            />
                                            <datalist id={`action-source-path-${index}-${mappingIndex}`}>
                                                {sourcePathCandidates.map((item) => (
                                                    <option key={item} value={item} />
                                                ))}
                                            </datalist>
                                        </div>
                                        <div className="property-row flex items-center mb-3">
                                            <label className="property-label w-20 text-xs text-text-secondary">值转换</label>
                                            <select
                                                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                                value={String(mapping.transform || 'raw')}
                                                onChange={(e) => {
                                                    const next = [...mappings];
                                                    next[mappingIndex] = { ...next[mappingIndex], transform: e.target.value as ComponentInteractionMapping['transform'] };
                                                    updateMappings(index, next);
                                                }}
                                            >
                                                <option value="raw">原值</option>
                                                <option value="string">字符串</option>
                                                <option value="number">数值</option>
                                                <option value="lowercase">转小写</option>
                                                <option value="uppercase">转大写</option>
                                            </select>
                                        </div>
                                        <button
                                            type="button"
                                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                            onClick={() => updateMappings(index, mappings.filter((_, i) => i !== mappingIndex))}
                                            style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#ef4444', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                                        >
                                            <Trash2 size={13} aria-hidden="true" />
                                            删除映射
                                        </button>
                                    </div>
                                ))}

                                <button
                                    type="button"
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    onClick={() => updateMappings(index, [
                                        ...mappings,
                                        { variableKey: '', sourcePath: 'name', transform: 'raw', fallbackValue: '' },
                                    ])}
                                    style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#2563eb', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                                >
                                    <Plus size={13} aria-hidden="true" />
                                    添加变量映射
                                </button>
                            </>
                        ) : null}

                        {actionType === 'jump-url' ? (
                            <>
                                <ScreenJumpPicker
                                    value={action.jumpUrlTemplate || ''}
                                    onChange={(url) => updateAction(index, { jumpUrlTemplate: url })}
                                />
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">打开方式</label>
                                    <select
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={action.jumpOpenMode || 'new-tab'}
                                        onChange={(e) => updateAction(index, { jumpOpenMode: e.target.value === 'self' ? 'self' : 'new-tab' })}
                                    >
                                        <option value="new-tab">新窗口</option>
                                        <option value="self">当前窗口</option>
                                    </select>
                                </div>
                            </>
                        ) : null}

                        {actionType === 'open-panel' ? (
                            <>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">面板标题模板</label>
                                    <input
                                        type="text"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={action.panelTitle || ''}
                                        onChange={(e) => updateAction(index, { panelTitle: e.target.value })}
                                        placeholder="项目 {{name}}"
                                    />
                                </div>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">面板内容模板</label>
                                    <textarea
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        rows={4}
                                        value={action.panelBodyTemplate || ''}
                                        onChange={(e) => updateAction(index, { panelBodyTemplate: e.target.value })}
                                        placeholder={'负责人：{{责任人}}\n状态：{{状态}}\n建议：发起协调'}
                                    />
                                </div>
                            </>
                        ) : null}

                        {actionType === 'emit-intent' ? (
                            <>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">意图名称</label>
                                    <input
                                        type="text"
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        value={action.intentName || ''}
                                        onChange={(e) => updateAction(index, { intentName: e.target.value })}
                                        placeholder="project.follow-up"
                                    />
                                </div>
                                <div className="property-row flex items-center mb-3">
                                    <label className="property-label w-20 text-xs text-text-secondary">意图负载模板</label>
                                    <textarea
                                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                        rows={4}
                                        value={action.intentPayloadTemplate || ''}
                                        onChange={(e) => updateAction(index, { intentPayloadTemplate: e.target.value })}
                                        placeholder={'{"project":"{{name}}","owner":"{{责任人}}"}'}
                                    />
                                </div>
                            </>
                        ) : null}

                        {(actionType === 'drill-down' || actionType === 'drill-up') ? (
                            <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', marginTop: 6 }}>
                                {actionType === 'drill-down'
                                    ? '运行态会复用当前组件的下钻链路，并使用点击值推进到下一层。'
                                    : '运行态会从当前钻取层级返回上一层。'}
                            </div>
                        ) : null}

                        <button
                            type="button"
                            className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                            onClick={() => setActions(actions.filter((_, i) => i !== index))}
                            style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#ef4444', marginTop: 8, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                        >
                            <Trash2 size={13} aria-hidden="true" />
                            删除动作
                        </button>
                    </div>
                );
            })}

            <button
                type="button"
                className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                onClick={() => setActions([
                    ...actions,
                    { type: 'open-panel', label: '查看详情', panelTitle: '{{name}}', panelBodyTemplate: '{{name}}' },
                ])}
                style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#2563eb', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
            >
                <Plus size={13} aria-hidden="true" />
                添加动作入口
            </button>
        </>
    );

    if (options?.embedded) {
        return content;
    }
    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">动作入口</div>
            {content}
        </div>
    );
}

export function renderDrillDownConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    options?: { embedded?: boolean },
) {
    const { type, dataSource, drillDown } = component;
    const cardId = dataSource?.type === 'card' ? dataSource.cardConfig?.cardId : undefined;

    if (!DRILL_CONFIGURABLE_TYPES.has(type) || dataSource?.type !== 'card' || !cardId || cardId <= 0) {
        return null;
    }

    const enabled = drillDown?.enabled ?? false;
    const levels = drillDown?.levels ?? [];

    const setDrillDown = (updates: Partial<DrillDownConfig>) => {
        updateComponent(component.id, {
            drillDown: { enabled, levels, ...drillDown, ...updates },
        });
    };

    const updateLevel = (index: number, field: keyof DrillLevel, value: string | number) => {
        const newLevels = [...levels];
        newLevels[index] = { ...newLevels[index], [field]: value };
        setDrillDown({ levels: newLevels });
    };

    const removeLevel = (index: number) => {
        setDrillDown({ levels: levels.filter((_, i) => i !== index) });
    };

    const addLevel = () => {
        setDrillDown({ levels: [...levels, { cardId: 0, paramName: '', label: '' }] });
    };

    const content = (
        <>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">启用下钻</label>
                <input
                    type="checkbox"
                    checked={enabled}
                    onChange={(e) => setDrillDown({ enabled: e.target.checked })}
                />
            </div>

            {enabled && (
                <>
                    {levels.map((level, i) => (
                        <div key={i} style={{
                            border: '1px solid rgba(255,255,255,0.1)',
                            borderRadius: 4,
                            padding: 8,
                            marginBottom: 8,
                        }}>
                            <div style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                marginBottom: 4,
                                fontSize: 11,
                                color: 'var(--color-text-secondary)',
                            }}>
                                <span>层级 {i + 1}</span>
                                <button
                                    type="button"
                                    className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                                    onClick={() => removeLevel(i)}
                                    style={{
                                        background: 'none', border: 'none',
                                        color: '#ef4444', cursor: 'pointer', fontSize: 11, gap: 4,
                                    }}
                                >
                                    <Trash2 size={12} aria-hidden="true" />
                                    删除
                                </button>
                            </div>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">Card</label>
                                <CardIdPicker
                                    value={level.cardId || 0}
                                    onChange={(cardId) => updateLevel(i, 'cardId', cardId)}
                                    placeholder="-- 下钻目标 --"
                                />
                            </div>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">参数名</label>
                                <input
                                    type="text"
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={level.paramName}
                                    onChange={(e) => updateLevel(i, 'paramName', e.target.value)}
                                    placeholder="如: region"
                                />
                            </div>
                            <div className="property-row flex items-center mb-3">
                                <label className="property-label w-20 text-xs text-text-secondary">标签</label>
                                <input
                                    type="text"
                                    className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                                    value={level.label}
                                    onChange={(e) => updateLevel(i, 'label', e.target.value)}
                                    placeholder="如: 地区"
                                />
                            </div>
                        </div>
                    ))}

                    <button
                        type="button"
                        className="property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
                        onClick={addLevel}
                        style={{
                            width: '100%', cursor: 'pointer',
                            textAlign: 'center', color: '#6366f1',
                            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6,
                        }}
                    >
                        <Plus size={13} aria-hidden="true" />
                        添加下钻层级
                    </button>
                </>
            )}
        </>
    );

    if (options?.embedded) {
        return content;
    }
    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">下钻配置</div>
            {content}
        </div>
    );
}
