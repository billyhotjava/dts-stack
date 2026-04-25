import type { DataSourceConfig, FieldMapping, ScreenComponent } from '../../types';
import { FieldMappingPanel, isMappable } from '../FieldMappingPanel';
import { resolveDataSourceType } from './helpers';

interface FieldMappingSectionOptions {
    selectedComponent: ScreenComponent;
    handleConfigChange: (key: string, value: unknown) => void;
    isSectionCollapsed: (sectionKey: string) => boolean;
    toggleSection: (sectionKey: string) => void;
}

export function renderFieldMappingConfig({
    selectedComponent,
    handleConfigChange,
    isSectionCollapsed,
    toggleSection,
}: FieldMappingSectionOptions) {
    if (!isMappable(selectedComponent.type)) {
        return null;
    }

    const sourceColumns = selectedComponent.config._sourceColumns as Array<{ name: string; displayName: string; baseType?: string }> ?? [];
    const hasSource = resolveDataSourceType(selectedComponent.dataSource as DataSourceConfig | undefined) !== 'static';
    if (!hasSource || sourceColumns.length === 0) {
        return null;
    }

    const isCollapsed = isSectionCollapsed('field-mapping');
    const currentMapping = (selectedComponent.config._fieldMapping as FieldMapping) ?? {};
    const useFieldMapping = selectedComponent.config._useFieldMapping !== false;

    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                <button
                    type="button"
                    className="property-section-toggle text-[10px] text-text-muted transition-transform duration-200"
                    onClick={() => toggleSection('field-mapping')}
                >
                    {isCollapsed ? '▸' : '▾'} 字段映射
                </button>
                <label style={{ fontSize: 11, color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center', gap: 4, marginLeft: 'auto' }}>
                    <input
                        type="checkbox"
                        checked={useFieldMapping}
                        onChange={(e) => {
                            handleConfigChange('_useFieldMapping', e.target.checked);
                        }}
                    />
                    启用
                </label>
            </div>
            {!isCollapsed && useFieldMapping ? (
                <FieldMappingPanel
                    componentType={selectedComponent.type}
                    sourceColumns={sourceColumns}
                    mapping={currentMapping}
                    onChange={(newMapping) => handleConfigChange('_fieldMapping', newMapping)}
                />
            ) : !isCollapsed ? (
                <div style={{ fontSize: 11, color: 'var(--color-text-secondary)', padding: '4px 0' }}>
                    字段映射已关闭，使用高级模式直接编辑 config。
                </div>
            ) : null}
        </div>
    );
}
