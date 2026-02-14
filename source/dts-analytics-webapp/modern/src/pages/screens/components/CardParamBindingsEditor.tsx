import type { CardParameterBinding, ScreenGlobalVariable } from '../types';

interface CardParamBindingsEditorProps {
    bindings: CardParameterBinding[];
    globalVariables: ScreenGlobalVariable[];
    onChange: (next: CardParameterBinding[]) => void;
}

export function CardParamBindingsEditor({ bindings, globalVariables, onChange }: CardParamBindingsEditorProps) {
    const variableOptions = (globalVariables ?? []).map((item) => ({
        key: item.key,
        label: item.label || item.key,
    }));

    const updateAt = (index: number, patch: Partial<CardParameterBinding>) => {
        const next = [...bindings];
        next[index] = { ...next[index], ...patch };
        onChange(next);
    };

    return (
        <>
            <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>参数绑定</div>

            {bindings.map((binding, index) => (
                <div
                    key={`binding-${index}`}
                    style={{
                        border: '1px solid rgba(255,255,255,0.06)',
                        borderRadius: 4,
                        padding: 6,
                        marginBottom: 6,
                    }}
                >
                    <div className="property-row">
                        <label className="property-label">参数名</label>
                        <input
                            type="text"
                            className="property-input"
                            value={binding.name || ''}
                            onChange={(e) => updateAt(index, { name: e.target.value })}
                            placeholder="region"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">变量</label>
                        <select
                            className="property-input"
                            value={binding.variableKey || ''}
                            onChange={(e) => updateAt(index, { variableKey: e.target.value || undefined })}
                        >
                            <option value="">-- 无 --</option>
                            {variableOptions.map((option) => (
                                <option key={option.key} value={option.key}>
                                    {option.label} ({option.key})
                                </option>
                            ))}
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">静态值</label>
                        <input
                            type="text"
                            className="property-input"
                            value={binding.value || ''}
                            onChange={(e) => updateAt(index, { value: e.target.value })}
                            placeholder="变量为空时可回退"
                        />
                    </div>
                    <button
                        className="property-input"
                        onClick={() => onChange(bindings.filter((_, i) => i !== index))}
                        style={{ width: '100%', cursor: 'pointer', textAlign: 'center', color: '#ef4444' }}
                    >
                        删除绑定
                    </button>
                </div>
            ))}

            <button
                className="property-input"
                onClick={() => onChange([...bindings, { name: '', variableKey: undefined, value: '' }])}
                style={{ width: '100%', cursor: 'pointer', textAlign: 'center', color: '#6366f1' }}
            >
                + 添加参数绑定
            </button>
        </>
    );
}
