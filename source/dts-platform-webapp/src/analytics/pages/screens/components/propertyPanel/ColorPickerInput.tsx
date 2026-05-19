import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { ColorPicker, Input } from 'antd';
import type { ColorPickerProps } from 'antd';
import { useScreenOptional } from '../../ScreenContext';
import { getThemeTokens } from '../../screenThemes';

type AntdColorValue = Parameters<NonNullable<ColorPickerProps['onChange']>>[0];

interface ColorPickerInputProps {
    value: string | undefined;
    fallback: string;
    onChange: (value: string) => void;
    className?: string;
    style?: CSSProperties;
    ariaLabel?: string;
    /**
     * 是否在色块右侧附加 hex/rgba 文本输入框。
     * 默认 true: 与统一的"色块 + hex 输入"体验一致。
     * 兼容传 false 仅显示色块。
     */
    showInput?: boolean;
    placeholder?: string;
}

const PICKER_COLOR_RE = /^(#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})|(rgb|rgba|hsl|hsla)\s*\([^)]*\))$/i;

function normalizePickerValue(value: string | undefined, fallback: string): string {
    const text = typeof value === 'string' ? value.trim() : '';
    if (!text || text.toLowerCase() === 'transparent') {
        return fallback;
    }
    return PICKER_COLOR_RE.test(text) ? text : fallback;
}

function isValidColor(text: string): boolean {
    if (!text) return false;
    const t = text.trim().toLowerCase();
    if (t === 'transparent') return true;
    return PICKER_COLOR_RE.test(t);
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
    showInput = true,
    placeholder,
}: ColorPickerInputProps) {
    const pickerValue = normalizePickerValue(value, fallback);
    const [draft, setDraft] = useState<string>(value ?? '');

    useEffect(() => {
        setDraft(value ?? '');
    }, [value]);

    // 大屏主题色板 — 让色盘弹出时显示当前主题的常用色,提升取色效率
    // 在非 ScreenProvider 环境(测试/插件预览)下 useScreenOptional 返回 null,直接跳过 presets
    const screenCtx = useScreenOptional();
    const themePresets = useMemo<ColorPickerProps['presets']>(() => {
        if (!screenCtx) return undefined;
        const cfg = screenCtx.state.config;
        const tokens = getThemeTokens(cfg.theme, cfg.customTheme);
        const palette = tokens.echarts.colorPalette || [];
        const semantic = [
            tokens.accentColor,
            tokens.textPrimary,
            tokens.textSecondary,
            tokens.cardBackground,
        ].filter(Boolean);
        const presets: ColorPickerProps['presets'] = [];
        if (palette.length > 0) {
            presets.push({ label: '主题色板', colors: palette });
        }
        if (semantic.length > 0) {
            presets.push({ label: '语义色', colors: semantic });
        }
        return presets;
    }, [screenCtx]);

    const commitText = () => {
        const next = draft.trim();
        if (!next) {
            onChange('');
            return;
        }
        if (isValidColor(next)) {
            onChange(next);
        } else {
            setDraft(value ?? '');
        }
    };

    const picker = (
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
                /* 关闭 allowClear 避免色块上出现红色斜线;清除由右侧 hex 输入框的 × 提供 */
                allowClear={false}
                /* 大屏主题预设色板 — 提升大屏配色一致性 */
                presets={themePresets && themePresets.length > 0 ? themePresets : undefined}
                className={className}
                style={{ width: 32, ...style }}
                getPopupContainer={() => document.body}
            />
        </span>
    );

    if (!showInput) {
        return picker;
    }

    return (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
            {picker}
            <Input
                size="small"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onBlur={commitText}
                onPressEnter={commitText}
                placeholder={placeholder ?? fallback}
                allowClear
                aria-label={ariaLabel ? `${ariaLabel} hex 值` : undefined}
                style={{ flex: 1, minWidth: 0 }}
            />
        </span>
    );
}
