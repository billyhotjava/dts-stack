type PreviewScaleControlProps = {
    scalePercent: number;
    onFit: () => void;
    onReset100: () => void;
    onZoomOut: () => void;
    onZoomIn: () => void;
    onSetScalePercent: (percent: number) => void;
};

export function PreviewScaleControl({
    scalePercent,
    onFit,
    onReset100,
    onZoomOut,
    onZoomIn,
    onSetScalePercent,
}: PreviewScaleControlProps) {
    const buttonStyle = {
        border: '1px solid var(--color-border, rgba(0,0,0,0.12))',
        background: 'var(--color-bg-primary, #ffffff)',
        color: 'var(--color-text-primary, rgba(0,0,0,0.85))',
        borderRadius: 6,
        padding: '2px 8px',
        fontSize: 12,
        lineHeight: '18px',
        cursor: 'pointer',
    } as const;

    return (
        <div
            style={{
                position: 'fixed',
                top: 12,
                left: 12,
                zIndex: 11000,
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                background: 'var(--color-bg-tertiary, #f5f5f5)',
                color: 'var(--color-text-primary, rgba(0,0,0,0.85))',
                border: '1px solid var(--color-border, rgba(0,0,0,0.12))',
                borderRadius: 8,
                padding: '6px 8px',
                fontSize: 12,
                backdropFilter: 'none',
                WebkitBackdropFilter: 'none',
            }}
            title="快捷键: F 适配, 0 重置, +/- 缩放, Ctrl/Cmd+滚轮 连续缩放"
        >
            <button type="button" onClick={onFit} style={buttonStyle}>适配</button>
            <button type="button" onClick={onReset100} style={buttonStyle}>100%</button>
            <button type="button" onClick={onZoomOut} style={buttonStyle}>-</button>
            <input
                type="number"
                min={20}
                max={200}
                value={scalePercent}
                onChange={(event) => onSetScalePercent(Number(event.target.value))}
                style={{
                    width: 60,
                    border: '1px solid var(--color-border, rgba(0,0,0,0.12))',
                    background: 'var(--color-bg-primary, #ffffff)',
                    color: 'var(--color-text-primary, rgba(0,0,0,0.85))',
                    borderRadius: 6,
                    padding: '2px 6px',
                    fontSize: 12,
                    lineHeight: '18px',
                    textAlign: 'right',
                }}
                title="输入 20-200 之间的缩放百分比"
            />
            <span style={{ minWidth: 12, textAlign: 'left' }}>%</span>
            <button type="button" onClick={onZoomIn} style={buttonStyle}>+</button>
        </div>
    );
}
