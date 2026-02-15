import { useScreenRuntime } from '../ScreenRuntimeContext';

export function GlobalVariablePanel() {
    const { definitions, values, setVariable } = useScreenRuntime();

    if (!definitions.length) {
        return null;
    }

    return (
        <div
            style={{
                position: 'fixed',
                top: 12,
                right: 12,
                width: 320,
                maxWidth: '32vw',
                background: 'rgba(15,23,42,0.88)',
                border: '1px solid rgba(148,163,184,0.35)',
                borderRadius: 10,
                padding: 12,
                color: '#e5e7eb',
                zIndex: 1200,
                backdropFilter: 'blur(6px)',
            }}
        >
            <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>页面参数</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {definitions.map((item) => {
                    const key = item.key;
                    const value = values[key] ?? '';
                    return (
                        <label key={key} style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                            <span style={{ fontSize: 12, opacity: 0.9 }}>{item.label || key}</span>
                            <input
                                type={item.type === 'number' ? 'number' : item.type === 'date' ? 'date' : 'text'}
                                value={value}
                                onChange={(e) => setVariable(key, e.target.value, 'global-variable-panel')}
                                style={{
                                    height: 30,
                                    borderRadius: 6,
                                    border: '1px solid rgba(148,163,184,0.35)',
                                    background: 'rgba(15,23,42,0.7)',
                                    color: '#e5e7eb',
                                    padding: '0 8px',
                                    outline: 'none',
                                }}
                            />
                        </label>
                    );
                })}
            </div>
        </div>
    );
}
