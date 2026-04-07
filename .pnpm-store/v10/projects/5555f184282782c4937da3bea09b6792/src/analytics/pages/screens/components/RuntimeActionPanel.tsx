import { useScreenRuntime } from '../ScreenRuntimeContext';

export function RuntimeActionPanel() {
    const { panel, closePanel } = useScreenRuntime();

    if (!panel.open) {
        return null;
    }

    return (
        <div
            data-testid="analytics-screen-runtime-panel"
            style={{
                position: 'fixed',
                right: 24,
                top: 88,
                width: 380,
                maxWidth: 'calc(100vw - 32px)',
                maxHeight: 'calc(100vh - 120px)',
                zIndex: 1200,
                borderRadius: 24,
                border: '1px solid rgba(148,163,184,0.24)',
                background: 'rgba(255,255,255,0.96)',
                boxShadow: '0 24px 80px rgba(15,23,42,0.18)',
                backdropFilter: 'blur(14px)',
                overflow: 'hidden',
                color: '#0f172a',
            }}
        >
            <div
                style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    justifyContent: 'space-between',
                    gap: 12,
                    padding: '18px 20px 14px',
                    borderBottom: '1px solid rgba(148,163,184,0.18)',
                    background: 'linear-gradient(180deg, rgba(219,234,254,0.92) 0%, rgba(255,255,255,0.92) 100%)',
                }}
            >
                <div>
                    <div style={{ fontSize: 12, color: '#64748b', marginBottom: 6 }}>Detail Panel</div>
                    <div data-testid="analytics-screen-runtime-panel-title" style={{ fontSize: 18, fontWeight: 700, lineHeight: 1.3 }}>{panel.title}</div>
                    {panel.source ? (
                        <div style={{ fontSize: 12, color: '#64748b', marginTop: 6 }}>{panel.source}</div>
                    ) : null}
                </div>
                <button
                    type="button"
                    data-testid="analytics-screen-runtime-panel-close"
                    onClick={closePanel}
                    style={{
                        border: '1px solid rgba(148,163,184,0.3)',
                        background: '#ffffff',
                        color: '#334155',
                        borderRadius: 999,
                        padding: '6px 12px',
                        cursor: 'pointer',
                        fontSize: 12,
                    }}
                >
                    关闭
                </button>
            </div>
            <div
                data-testid="analytics-screen-runtime-panel-body"
                style={{
                    padding: 20,
                    overflow: 'auto',
                    maxHeight: 'calc(100vh - 220px)',
                    fontSize: 13,
                    lineHeight: 1.7,
                    whiteSpace: 'pre-wrap',
                    color: '#334155',
                }}
            >
                {panel.body || '暂无详情内容'}
            </div>
        </div>
    );
}
