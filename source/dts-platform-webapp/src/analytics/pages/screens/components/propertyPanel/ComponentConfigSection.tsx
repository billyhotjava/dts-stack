import SchemaConfigRenderer from '../../configSchema/editors/SchemaConfigRenderer';
import { COMPONENT_CONFIG_SCHEMAS } from '../../configSchema/schemas';
import type { ScreenComponent, ScreenConfig } from '../../types';
import { setByPath } from './helpers';

interface ComponentConfigSectionOptions {
    selectedComponent: ScreenComponent;
    theme?: ScreenConfig['theme'];
    updateComponent: (
        id: string,
        updates: Partial<ScreenComponent> | ((prev: ScreenComponent) => Partial<ScreenComponent>),
    ) => void;
    isSectionCollapsed: (sectionKey: string) => boolean;
    toggleSection: (sectionKey: string) => void;
    /** 仅渲染指定分组(白名单),用于 advanced Tab */
    onlyGroups?: string[];
    /** 排除指定分组(黑名单),用于 style Tab 隐藏 advanced */
    hideGroups?: string[];
}

export function renderComponentConfigSection({
    selectedComponent,
    theme,
    updateComponent,
    isSectionCollapsed: _isSectionCollapsed,
    toggleSection: _toggleSection,
    onlyGroups,
    hideGroups,
}: ComponentConfigSectionOptions) {
    // 2026-05 UI 重构: 去除"组件配置"这层 wrapper,让 SchemaConfigRenderer 的子分组
    // (图表/标题/图例/坐标轴/外观 等)直接作为顶级 Collapse 渲染,减少一层嵌套。
    // 同时支持 onlyGroups/hideGroups: 样式 Tab 隐藏 advanced,高级 Tab 仅显示 advanced。
    return (
        <div className="property-section py-3 border-b border-border-default">
            {renderComponentConfigBody(selectedComponent, theme, updateComponent, onlyGroups, hideGroups)}
        </div>
    );
}

function renderComponentConfigBody(
    selectedComponent: ScreenComponent,
    theme: ScreenConfig['theme'] | undefined,
    updateComponent: (
        id: string,
        updates: Partial<ScreenComponent> | ((prev: ScreenComponent) => Partial<ScreenComponent>),
    ) => void,
    onlyGroups?: string[],
    hideGroups?: string[],
) {
    const schema = COMPONENT_CONFIG_SCHEMAS[selectedComponent.type];
    if (!schema) {
        // Schema 缺失通常意味着组件注册了渲染器但忘记注册可编辑 schema。
        if (typeof window !== 'undefined' && !((window as { __dtsSchemaWarned__?: Record<string, boolean> }).__dtsSchemaWarned__?.[selectedComponent.type])) {
            console.warn(
                `[PropertyPanel] missing config schema for component type "${selectedComponent.type}";`
                    + ' add an entry under analytics/pages/screens/configSchema/schemas to enable editing.',
            );
            const w = window as { __dtsSchemaWarned__?: Record<string, boolean> };
            w.__dtsSchemaWarned__ = w.__dtsSchemaWarned__ ?? {};
            w.__dtsSchemaWarned__[selectedComponent.type] = true;
        }
        return (
            <div className="text-xs py-3 px-2 rounded bg-warning/10 text-warning border border-warning/30">
                <div className="font-medium mb-1">该组件类型暂无可配置项</div>
                <div className="opacity-80">
                    类型: <code>{selectedComponent.type}</code>
                    <br />
                    如需在此处编辑,请在 <code>configSchema/schemas</code> 中补一份 schema。
                </div>
            </div>
        );
    }

    return (
        <SchemaConfigRenderer
            schema={schema}
            config={(selectedComponent.config as Record<string, unknown>) ?? {}}
            onChange={(key, value) => {
                updateComponent(selectedComponent.id, (prev) => ({
                    config: setByPath(
                        prev.config as Record<string, unknown>,
                        key,
                        value,
                    ),
                }));
            }}
            theme={theme}
            onlyGroups={onlyGroups}
            hideGroups={hideGroups}
        />
    );
}
