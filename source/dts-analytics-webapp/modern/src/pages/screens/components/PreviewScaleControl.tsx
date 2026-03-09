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
    return (
        <div className="runtime-control-card preview-scale-control" title="快捷键: F 适配, 0 重置, +/- 缩放, Ctrl/Cmd+滚轮 连续缩放">
            <div className="runtime-control-card__title">缩放控制</div>
            <div className="runtime-control-row">
                <button type="button" className="runtime-control-btn" onClick={onFit}>适配</button>
                <button type="button" className="runtime-control-btn" onClick={onReset100}>100%</button>
                <button type="button" className="runtime-control-btn" onClick={onZoomOut}>-</button>
                <input
                    type="number"
                    min={20}
                    max={200}
                    value={scalePercent}
                    onChange={(event) => onSetScalePercent(Number(event.target.value))}
                    className="runtime-control-input preview-scale-control__value"
                    title="输入 20-200 之间的缩放百分比"
                />
                <span className="preview-scale-control__suffix">%</span>
                <button type="button" className="runtime-control-btn" onClick={onZoomIn}>+</button>
            </div>
        </div>
    );
}
