import { useMemo } from 'react';
import { Modal } from '../../../ui/Modal/Modal';
import type { ScreenGlobalVariable } from '../types';

interface GlobalVariableManagerProps {
    open: boolean;
    variables: ScreenGlobalVariable[];
    cycleWarnings?: string[];
    onClose: () => void;
    onChange: (next: ScreenGlobalVariable[]) => void;
}

function makeVariable(index: number): ScreenGlobalVariable {
    return {
        key: `var_${index + 1}`,
        label: `变量${index + 1}`,
        type: 'string',
        defaultValue: '',
    };
}

export function GlobalVariableManager({ open, variables, cycleWarnings, onClose, onChange }: GlobalVariableManagerProps) {
    const keySet = useMemo(() => {
        const out = new Set<string>();
        for (const item of variables) {
            const key = (item.key || '').trim();
            if (key) out.add(key);
        }
        return out;
    }, [variables]);

    const updateAt = (index: number, patch: Partial<ScreenGlobalVariable>) => {
        const next = [...variables];
        next[index] = { ...next[index], ...patch };
        onChange(next);
    };

    const removeAt = (index: number) => {
        onChange(variables.filter((_, i) => i !== index));
    };

    const addOne = () => {
        const candidate = makeVariable(variables.length);
        let i = variables.length;
        while (keySet.has(candidate.key)) {
            i += 1;
            candidate.key = `var_${i + 1}`;
            candidate.label = `变量${i + 1}`;
        }
        onChange([...variables, candidate]);
    };

    return (
        <Modal isOpen={open} onClose={onClose} title="全局变量中心" size="xl">
            <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 10 }}>
                大屏级变量可用于 1-&gt;N 组件联动与页面级筛选，组件数据源参数可绑定这些变量。
            </div>

            {cycleWarnings && cycleWarnings.length > 0 && (
                <div style={{
                    border: '1px solid #f59e0b',
                    background: 'rgba(245,158,11,0.08)',
                    color: '#f59e0b',
                    borderRadius: 8,
                    padding: 10,
                    marginBottom: 12,
                    fontSize: 12,
                    lineHeight: 1.5,
                }}>
                    <div style={{ fontWeight: 600, marginBottom: 6 }}>检测到潜在联动循环</div>
                    {cycleWarnings.map((item, i) => (
                        <div key={`${item}-${i}`}>{item}</div>
                    ))}
                </div>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 120px 1fr 48px', gap: 8, fontSize: 12, marginBottom: 8 }}>
                <div>变量Key</div>
                <div>显示名称</div>
                <div>类型</div>
                <div>默认值</div>
                <div />
            </div>

            {variables.map((item, index) => {
                const key = (item.key || '').trim();
                const duplicate = key && variables.filter((x) => (x.key || '').trim() === key).length > 1;
                return (
                    <div key={`${item.key || 'var'}-${index}`} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 120px 1fr 48px', gap: 8, marginBottom: 8 }}>
                        <input
                            className="property-input"
                            value={item.key || ''}
                            onChange={(e) => updateAt(index, { key: e.target.value })}
                            placeholder="region"
                            style={duplicate ? { borderColor: '#ef4444' } : undefined}
                        />
                        <input
                            className="property-input"
                            value={item.label || ''}
                            onChange={(e) => updateAt(index, { label: e.target.value })}
                            placeholder="地区"
                        />
                        <select
                            className="property-input"
                            value={item.type || 'string'}
                            onChange={(e) => updateAt(index, { type: e.target.value as ScreenGlobalVariable['type'] })}
                        >
                            <option value="string">string</option>
                            <option value="number">number</option>
                            <option value="date">date</option>
                        </select>
                        <input
                            className="property-input"
                            value={item.defaultValue || ''}
                            onChange={(e) => updateAt(index, { defaultValue: e.target.value })}
                            placeholder="默认值"
                        />
                        <button
                            type="button"
                            className="header-btn"
                            onClick={() => removeAt(index)}
                            title="删除"
                            style={{ padding: '6px 0' }}
                        >
                            -
                        </button>
                    </div>
                );
            })}

            <div style={{ marginTop: 8, display: 'flex', gap: 8 }}>
                <button type="button" className="header-btn" onClick={addOne}>+ 添加变量</button>
            </div>
        </Modal>
    );
}
