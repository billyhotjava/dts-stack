import { estimateVisualTextWidth } from './chartUtils';

export type ChartTitlePosition = 'left' | 'center' | 'right';

type ResolveChartTitleLayoutInput = {
    text: string;
    width: number;
    height: number;
    fontSize: number;
    color: string;
    positionRaw?: unknown;
    offsetX?: unknown;
    offsetY?: unknown;
    defaultPosition?: ChartTitlePosition;
};

function normalizeChartTitlePosition(raw: unknown, fallback: ChartTitlePosition): ChartTitlePosition {
    const value = String(raw ?? '').trim().toLowerCase();
    if (value === 'left' || value === 'center' || value === 'right') {
        return value;
    }
    return fallback;
}

function clamp(value: number, min: number, max: number): number {
    return Math.min(max, Math.max(min, value));
}

export function resolveChartTitleLayout(input: ResolveChartTitleLayoutInput) {
    const width = Math.max(120, Math.round(Number(input.width) || 0));
    const height = Math.max(60, Math.round(Number(input.height) || 0));
    const text = String(input.text ?? '').trim();
    const fontSize = Math.max(10, Math.min(40, Math.round(Number(input.fontSize) || 14)));
    const defaultPosition = input.defaultPosition ?? 'left';
    const position = normalizeChartTitlePosition(input.positionRaw, defaultPosition);
    const boundX = Math.max(120, Math.round(width * 0.45));
    const boundY = Math.max(80, Math.round(height * 0.35));
    const offsetX = clamp(Number(input.offsetX) || 0, -boundX, boundX);
    const offsetY = clamp(Number(input.offsetY) || 0, -boundY, boundY);
    const titleWidth = Math.min(width - 24, Math.max(88, estimateVisualTextWidth(text, fontSize) + 20));
    const titleHeight = Math.max(20, fontSize + 12);
    const baseTop = 6;
    const baseLeft = position === 'left'
        ? 12
        : (position === 'right'
            ? width - titleWidth - 12
            : (width - titleWidth) / 2);
    const left = clamp(Math.round(baseLeft + offsetX), 0, Math.max(0, width - titleWidth));
    const top = clamp(Math.round(baseTop + offsetY), 0, Math.max(0, height - titleHeight));

    return {
        position,
        titleOption: {
            show: text.length > 0,
            text,
            left,
            top,
            width: titleWidth,
            textAlign: position,
            textStyle: {
                color: input.color,
                fontSize,
                fontWeight: 'bold',
            },
        },
        handleRect: {
            left,
            top,
            width: titleWidth,
            height: titleHeight,
        },
        offsetX,
        offsetY,
    };
}
