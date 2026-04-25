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
}

export function renderComponentConfigSection({
    selectedComponent,
    theme,
    updateComponent,
    isSectionCollapsed,
    toggleSection,
}: ComponentConfigSectionOptions) {
    const isCollapsed = isSectionCollapsed('component-config');

    return (
        <div className="property-section py-3 border-b border-border-default">
            <div
                className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none"
                style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}
            >
                <button
                    type="button"
                    className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                    onClick={() => toggleSection('component-config')}
                >
                    {isCollapsed ? '▸' : '▾'} 组件配置
                </button>
            </div>

            {!isCollapsed ? renderComponentConfigBody(selectedComponent, theme, updateComponent) : null}
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
        />
    );
}
