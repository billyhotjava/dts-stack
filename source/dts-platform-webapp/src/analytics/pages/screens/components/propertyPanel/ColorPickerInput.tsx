import type { CSSProperties } from 'react';
import { ColorPicker } from 'antd';
import type { ColorPickerProps } from 'antd';

type AntdColorValue = Parameters<NonNullable<ColorPickerProps['onChange']>>[0];

interface ColorPickerInputProps {
    value: string | undefined;
    fallback: string;
    onChange: (value: string) => void;
    className?: string;
    style?: CSSProperties;
    ariaLabel?: string;
}

const PICKER_COLOR_RE = /^(#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})|(rgb|rgba|hsl|hsla)\s*\([^)]*\))$/i;

function normalizePickerValue(value: string | undefined, fallback: string): string {
    const text = typeof value === 'string' ? value.trim() : '';
    if (!text || text.toLowerCase() === 'transparent') {
        return fallback;
    }
    return PICKER_COLOR_RE.test(text) ? text : fallback;
}

function resolveColorString(color: AntdColorValue, css: string): string {
    if (typeof css === 'string' && css.trim()) {
        return css;
    }
    const candidate = color as {
        toHexString?: () => string;
        toRgbString?: () => string;
    };
    return candidate.toHexString?.() || candidate.toRgbString?.() || '';
}

export function toRgbaColor(value: string, alpha: number): string {
    const text = value.trim();
    if (text.startsWith('#') && (text.length === 7 || text.length === 9)) {
        const r = parseInt(text.slice(1, 3), 16);
        const g = parseInt(text.slice(3, 5), 16);
        const b = parseInt(text.slice(5, 7), 16);
        return `rgba(${r},${g},${b},${alpha})`;
    }
    const rgbMatch = text.match(/^rgba?\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i);
    if (rgbMatch) {
        return `rgba(${rgbMatch[1]},${rgbMatch[2]},${rgbMatch[3]},${alpha})`;
    }
    return toRgbaColor('#ff6b6b', alpha);
}

export function ColorPickerInput({
    value,
    fallback,
    onChange,
    className = 'property-color-input w-8 h-7 border border-border-default rounded cursor-pointer p-0',
    style,
    ariaLabel,
}: ColorPickerInputProps) {
    const pickerValue = normalizePickerValue(value, fallback);

    return (
        <span
            aria-label={ariaLabel}
            style={{ display: 'inline-flex', lineHeight: 0 }}
            onPointerDown={(event) => event.stopPropagation()}
            onMouseDown={(event) => event.stopPropagation()}
        >
            <ColorPicker
                size="small"
                value={pickerValue}
                onChange={(color, css) => onChange(resolveColorString(color, css))}
                showText={false}
                className={className}
                style={{ width: 32, ...style }}
                getPopupContainer={() => document.body}
            />
        </span>
    );
}
