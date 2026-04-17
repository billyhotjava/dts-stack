import type { ReactNode, Dispatch, SetStateAction } from 'react';
import type { ScreenComponent, ScreenTheme } from '../types';
import type { ScreenThemeTokens } from '../screenThemes';
import { normalizeFilterDebounceMs } from './shared/chartUtils';

/**
 * Props for filter renderer — all outer-scope values used by the three filter cases.
 */
export interface FilterRendererProps {
    c: Record<string, unknown>;
    t: ScreenThemeTokens;
    theme: ScreenTheme | undefined;
    component: ScreenComponent;
    runtime: {
        values: Record<string, string>;
        setVariable: (key: string, value: string, source?: string) => void;
    };
    filterInputDraft: string;
    setFilterInputDraft: Dispatch<SetStateAction<string>>;
    filterSelectVariableKey: string;
    filterSelectOptions: Array<{ label: string; value: string }>;
    filterDateStartKey: string;
    filterDateEndKey: string;
    scheduleFilterVariableUpdate: (
        variableKey: string,
        value: string,
        source: string,
        debounceMs: number,
        immediate?: boolean,
    ) => void;
}

/**
 * Render filter components: filter-input, filter-select, filter-date-range.
 * Extracted from ComponentRenderer.tsx
 */
export function renderFilter(
    type: string,
    props: FilterRendererProps,
): ReactNode | null {
    const {
        c, t, theme, component, runtime,
        filterInputDraft, setFilterInputDraft,
        filterSelectVariableKey, filterSelectOptions,
        filterDateStartKey, filterDateEndKey,
        scheduleFilterVariableUpdate,
    } = props;

    switch (type) {
        case 'filter-input': {
            const label = String(c.label ?? '筛选');
            const scopeHint = String(c.scopeHint ?? '').trim();
            const variableKey = String(c.variableKey ?? '').trim();
            const placeholder = String(c.placeholder ?? '请输入');
            const value = variableKey ? filterInputDraft : '';
            const labelColor = String(c.labelColor || t.textSecondary);
            const inputTextColor = String(c.inputTextColor || t.textPrimary);
            const inputBorderColor = String(c.inputBorderColor || 'rgba(148,163,184,0.4)');
            const inputBackground = String(c.inputBackground || (theme === 'glacier' ? '#ffffff' : 'rgba(15,23,42,0.65)'));
            const debounceMs = normalizeFilterDebounceMs(c.debounceMs);
            return (
                <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                    <div style={{ fontSize: 12, color: labelColor }}>{label}</div>
                    {scopeHint ? <div style={{ fontSize: 10, color: t.textSecondary }}>{scopeHint}</div> : null}
                    <input
                        type="text"
                        value={value}
                        onChange={(e) => {
                            const nextValue = e.target.value;
                            setFilterInputDraft(nextValue);
                            if (!variableKey) return;
                            scheduleFilterVariableUpdate(variableKey, nextValue, `filter-input:${component.id}`, debounceMs);
                        }}
                        onBlur={() => {
                            if (!variableKey) return;
                            scheduleFilterVariableUpdate(variableKey, filterInputDraft, `filter-input:${component.id}`, debounceMs, true);
                        }}
                        placeholder={placeholder}
                        style={{
                            width: '100%',
                            height: 34,
                            borderRadius: 6,
                            border: `1px solid ${inputBorderColor}`,
                            background: inputBackground,
                            color: inputTextColor,
                            padding: '0 10px',
                            outline: 'none',
                        }}
                    />
                </div>
            );
        }

        case 'filter-select': {
            const label = String(c.label ?? '筛选');
            const scopeHint = String(c.scopeHint ?? '').trim();
            const variableKey = filterSelectVariableKey;
            const placeholder = String(c.placeholder ?? '请选择');
            const options = filterSelectOptions;
            const value = variableKey ? (runtime.values[variableKey] ?? '') : '';
            const labelColor = String(c.labelColor || t.textSecondary);
            const inputTextColor = String(c.inputTextColor || t.textPrimary);
            const inputBorderColor = String(c.inputBorderColor || 'rgba(148,163,184,0.4)');
            const inputBackground = String(c.inputBackground || (theme === 'glacier' ? '#ffffff' : 'rgba(15,23,42,0.65)'));
            const optionBackground = String(c.optionBackground || (theme === 'glacier' ? '#ffffff' : '#0b2652'));
            const optionTextColor = String(c.optionTextColor || (theme === 'glacier' ? '#1f2937' : '#ffffff'));
            const optionStyle = { background: optionBackground, color: optionTextColor };
            return (
                <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                    <div style={{ fontSize: 12, color: labelColor }}>{label}</div>
                    {scopeHint ? <div style={{ fontSize: 10, color: t.textSecondary }}>{scopeHint}</div> : null}
                    <select
                        value={value}
                        onChange={(e) => variableKey && runtime.setVariable(variableKey, e.target.value, `filter-select:${component.id}`)}
                        style={{
                            width: '100%',
                            height: 34,
                            borderRadius: 6,
                            border: `1px solid ${inputBorderColor}`,
                            background: inputBackground,
                            color: inputTextColor,
                            padding: '0 10px',
                            outline: 'none',
                        }}
                    >
                        <option value="" style={optionStyle}>{placeholder}</option>
                        {options.map((option) => (
                            <option key={option.value} value={option.value} style={optionStyle}>{option.label}</option>
                        ))}
                    </select>
                </div>
            );
        }

        case 'filter-date-range': {
            const label = String(c.label ?? '日期区间');
            const scopeHint = String(c.scopeHint ?? '').trim();
            const startKey = filterDateStartKey;
            const endKey = filterDateEndKey;
            const startValue = startKey ? (runtime.values[startKey] ?? '') : '';
            const endValue = endKey ? (runtime.values[endKey] ?? '') : '';
            const labelColor = String(c.labelColor || t.textSecondary);
            const inputTextColor = String(c.inputTextColor || t.textPrimary);
            const inputBorderColor = String(c.inputBorderColor || 'rgba(148,163,184,0.4)');
            const inputBackground = String(c.inputBackground || (theme === 'glacier' ? '#ffffff' : 'rgba(15,23,42,0.65)'));
            return (
                <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                    <div style={{ fontSize: 12, color: labelColor }}>{label}</div>
                    {scopeHint ? <div style={{ fontSize: 10, color: t.textSecondary }}>{scopeHint}</div> : null}
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 16px 1fr', alignItems: 'center', gap: 4 }}>
                        <input
                            type="date"
                            value={startValue}
                            onChange={(e) => startKey && runtime.setVariable(startKey, e.target.value, `filter-date-range:start:${component.id}`)}
                            style={{
                                width: '100%',
                                height: 34,
                                borderRadius: 6,
                                border: `1px solid ${inputBorderColor}`,
                                background: inputBackground,
                                color: inputTextColor,
                                padding: '0 8px',
                                outline: 'none',
                            }}
                        />
                        <span style={{ textAlign: 'center', color: t.textSecondary }}>~</span>
                        <input
                            type="date"
                            value={endValue}
                            onChange={(e) => endKey && runtime.setVariable(endKey, e.target.value, `filter-date-range:end:${component.id}`)}
                            style={{
                                width: '100%',
                                height: 34,
                                borderRadius: 6,
                                border: `1px solid ${inputBorderColor}`,
                                background: inputBackground,
                                color: inputTextColor,
                                padding: '0 8px',
                                outline: 'none',
                            }}
                        />
                    </div>
                </div>
            );
        }

        default:
            return null;
    }
}
