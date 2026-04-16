import type { ReactNode, Dispatch, SetStateAction } from 'react';
import type { ScreenComponent } from '../types';
import type { ScreenThemeTokens } from '../screenThemes';
import { isSafeSrcUrl } from '../sanitize';
import { resolveTextColor } from './shared/chartUtils';
import { renderMarkdownToHtml } from './shared/markdownUtils';

/**
 * Props for basic renderer — all outer-scope values used by the basic display cases.
 */
export interface BasicRendererProps {
    c: Record<string, unknown>;
    t: ScreenThemeTokens;
    component: ScreenComponent;
    runtime: {
        values: Record<string, string>;
        setVariable: (key: string, value: string, source?: string) => void;
    };
    currentTime: Date;
    carouselItems: string[];
    carouselIndex: number;
    setCarouselIndex: Dispatch<SetStateAction<number>>;
    setCarouselPaused: Dispatch<SetStateAction<boolean>>;
    tabOptions: Array<{ label: string; value: string }>;
    tabRuntimeValue: string;
    tabDefaultValue: string;
    tabVariableKey: string;
}

/**
 * Render basic display components: number-card, title, markdown-text, richtext,
 * datetime, countdown, marquee, carousel, progress-bar, tab-switcher,
 * shape, container, image, video, iframe.
 * Extracted from ComponentRenderer.tsx
 */
export function renderBasic(
    type: string,
    props: BasicRendererProps,
): ReactNode | null {
    const {
        c, t, component, runtime,
        currentTime,
        carouselItems, carouselIndex, setCarouselIndex, setCarouselPaused,
        tabOptions, tabRuntimeValue, tabDefaultValue, tabVariableKey,
    } = props;

    switch (type) {
        case 'number-card':
            return (
                <div style={{
                    width: '100%',
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    alignItems: 'center',
                    background: (c.backgroundColor as string) || t.numberCard.background,
                    borderRadius: t.cardBorderRadius,
                    border: t.numberCard.border,
                    boxShadow: t.cardShadow,
                    fontFamily: c.fontFamily ? (c.fontFamily as string) : undefined,
                }}>
                    <div style={{
                        fontSize: (c.titleFontSize as number) || 16,
                        fontWeight: 600,
                        color: resolveTextColor(c.titleColor as string | undefined, t.numberCard.titleColor),
                        marginBottom: 8,
                    }}>
                        {c.title as string}
                    </div>
                    <div style={{
                        fontSize: (c.valueFontSize as number) || 34,
                        fontWeight: 'bold',
                        color: resolveTextColor(c.valueColor as string | undefined, t.numberCard.valueColor),
                    }}>
                        {c.prefix as string}
                        {c.value != null ? Number(c.value).toLocaleString('zh-CN') : '-'}
                        {c.suffix as string}
                    </div>
                </div>
            );

        case 'title':
            return (
                <div style={{
                    width: '100%',
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: c.textAlign as string,
                    fontSize: c.fontSize as number,
                    fontWeight: c.fontWeight as string,
                    fontFamily: c.fontFamily ? (c.fontFamily as string) : undefined,
                    color: resolveTextColor(c.color as string | undefined, t.textPrimary),
                }}>
                    {c.text as string}
                </div>
            );

        case 'markdown-text': {
            const markdown = String(c.markdown ?? '');
            const html = renderMarkdownToHtml(markdown);
            return (
                <div
                    style={{
                        width: '100%',
                        height: '100%',
                        overflow: 'auto',
                        color: resolveTextColor(c.color as string | undefined, t.textPrimary),
                        fontSize: (c.fontSize as number) || 14,
                        fontFamily: c.fontFamily ? (c.fontFamily as string) : undefined,
                        lineHeight: Number(c.lineHeight || 1.6),
                        padding: 8,
                    }}
                    dangerouslySetInnerHTML={{ __html: html }}
                />
            );
        }

        case 'richtext': {
            const rtContent = String(c.content ?? '');
            // Sanitize: strip script/iframe/style/on* attributes
            const sanitizedRtHtml = rtContent
                .replace(/<script[\s\S]*?<\/script>/gi, '')
                .replace(/<iframe[\s\S]*?<\/iframe>/gi, '')
                .replace(/<style[\s\S]*?<\/style>/gi, '')
                .replace(/\bon\w+\s*=\s*["'][^"']*["']/gi, '')
                .replace(/\bon\w+\s*=\s*\S+/gi, '')
                .replace(/<a\s/gi, '<a rel="noreferrer" target="_blank" ');
            const rtPadding = Number(c.padding ?? 12);
            const rtOverflow = String(c.overflow ?? 'hidden');
            const rtVAlign = String(c.verticalAlign ?? 'top');
            const alignMap: Record<string, string> = { top: 'flex-start', middle: 'center', bottom: 'flex-end' };
            return (
                <div
                    style={{
                        width: '100%',
                        height: '100%',
                        padding: rtPadding,
                        overflow: rtOverflow as 'hidden' | 'visible' | 'scroll',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: alignMap[rtVAlign] ?? 'flex-start',
                        boxSizing: 'border-box',
                    }}
                    dangerouslySetInnerHTML={{ __html: sanitizedRtHtml }}
                />
            );
        }

        case 'datetime': {
            const formatted = (c.format as string)
                .replace('YYYY', String(currentTime.getFullYear()))
                .replace('MM', String(currentTime.getMonth() + 1).padStart(2, '0'))
                .replace('DD', String(currentTime.getDate()).padStart(2, '0'))
                .replace('HH', String(currentTime.getHours()).padStart(2, '0'))
                .replace('mm', String(currentTime.getMinutes()).padStart(2, '0'))
                .replace('ss', String(currentTime.getSeconds()).padStart(2, '0'));
            return (
                <div style={{
                    width: '100%',
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: c.fontSize as number,
                    color: resolveTextColor(c.color as string | undefined, t.textPrimary),
                    fontFamily: c.fontFamily ? (c.fontFamily as string) : 'monospace',
                }}>
                    {formatted}
                </div>
            );
        }

        case 'countdown': {
            const targetVariableKey = String(c.targetVariableKey || '').trim();
            const runtimeTarget = targetVariableKey ? String(runtime.values[targetVariableKey] || '').trim() : '';
            const configuredTarget = String(c.targetTime || '').trim();
            const targetRaw = runtimeTarget || configuredTarget;
            const targetMillis = Date.parse(targetRaw);
            const hasTarget = Number.isFinite(targetMillis);
            const remaining = hasTarget ? Math.max(0, targetMillis - currentTime.getTime()) : 0;
            const dayMs = 24 * 3600 * 1000;
            const hourMs = 3600 * 1000;
            const minuteMs = 60 * 1000;
            const days = Math.floor(remaining / dayMs);
            const hours = Math.floor((remaining % dayMs) / hourMs);
            const minutes = Math.floor((remaining % hourMs) / minuteMs);
            const seconds = Math.floor((remaining % minuteMs) / 1000);
            const showDays = c.showDays !== false;
            const accentColor = (c.accentColor as string) || t.accentColor;
            const labelColor = resolveTextColor(c.color as string | undefined, t.textSecondary);
            return (
                <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 8 }}>
                    <div style={{ fontSize: 12, color: labelColor }}>
                        {String(c.title || '倒计时')}
                    </div>
                    {!hasTarget ? (
                        <div style={{ fontSize: 13, color: labelColor, opacity: 0.8 }}>
                            请配置目标时间或绑定目标时间变量
                        </div>
                    ) : null}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, color: accentColor, fontWeight: 700 }}>
                        {showDays ? <span style={{ fontSize: 26 }}>{String(days).padStart(2, '0')}天</span> : null}
                        <span style={{ fontSize: 26 }}>{String(hours).padStart(2, '0')}:</span>
                        <span style={{ fontSize: 26 }}>{String(minutes).padStart(2, '0')}:</span>
                        <span style={{ fontSize: 26 }}>{String(seconds).padStart(2, '0')}</span>
                    </div>
                </div>
            );
        }

        case 'marquee': {
            const text = String(c.text || '');
            const speed = Math.max(10, Number(c.speed || 40));
            const keyframesName = `dts_marquee_${component.id.replace(/[^a-zA-Z0-9_]/g, '_')}`;
            return (
                <div
                    style={{
                        width: '100%',
                        height: '100%',
                        overflow: 'hidden',
                        display: 'flex',
                        alignItems: 'center',
                        background: (c.backgroundColor as string) || 'transparent',
                        color: resolveTextColor(c.color as string | undefined, t.textPrimary),
                        fontSize: (c.fontSize as number) || 14,
                        whiteSpace: 'nowrap',
                        position: 'relative',
                    }}
                >
                    <style>{`@keyframes ${keyframesName} { from { transform: translateX(100%); } to { transform: translateX(-100%); } }`}</style>
                    <div style={{ display: 'inline-block', paddingLeft: '100%', animation: `${keyframesName} ${speed}s linear infinite` }}>
                        {text}
                    </div>
                </div>
            );
        }

        case 'carousel': {
            const items = carouselItems;
            const hasItems = items.length > 0;
            const index = hasItems ? (carouselIndex % items.length) : 0;
            const currentItem = hasItems ? items[index] : '暂无轮播内容';
            const cardTitle = String(c.title || '轮播卡片');
            const cardColor = resolveTextColor(c.color as string | undefined, t.textPrimary);
            const titleColor = resolveTextColor(c.titleColor as string | undefined, t.textSecondary);
            const backgroundColor = String(c.backgroundColor || t.cardBackground);
            const fontSize = Math.max(12, Number(c.fontSize || 24));
            const showDots = c.showDots !== false;
            const showControls = c.showControls !== false;
            const pauseOnHover = c.pauseOnHover !== false;
            const canFlip = hasItems && items.length > 1;
            return (
                <div
                    style={{
                        width: '100%',
                        height: '100%',
                        border: '1px solid rgba(148,163,184,0.3)',
                        borderRadius: 10,
                        background: backgroundColor,
                        padding: 12,
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'space-between',
                        boxSizing: 'border-box',
                        overflow: 'hidden',
                    }}
                    onMouseEnter={() => {
                        if (pauseOnHover) {
                            setCarouselPaused(true);
                        }
                    }}
                    onMouseLeave={() => {
                        if (pauseOnHover) {
                            setCarouselPaused(false);
                        }
                    }}
                >
                    <div style={{ fontSize: 12, color: titleColor, letterSpacing: 0.4 }}>
                        {cardTitle}
                    </div>
                    <div style={{ flex: 1, display: 'grid', gridTemplateColumns: showControls ? '28px 1fr 28px' : '1fr', alignItems: 'center', gap: 8 }}>
                        {showControls && (
                            <button
                                type="button"
                                onClick={() => {
                                    if (!canFlip) return;
                                    setCarouselIndex((prev) => (prev - 1 + items.length) % items.length);
                                }}
                                style={{
                                    width: 28,
                                    height: 28,
                                    borderRadius: '50%',
                                    border: '1px solid rgba(148,163,184,0.4)',
                                    background: 'rgba(15,23,42,0.45)',
                                    color: cardColor,
                                    cursor: canFlip ? 'pointer' : 'default',
                                    opacity: canFlip ? 1 : 0.45,
                                }}
                                title="上一条"
                            >
                                {'<'}
                            </button>
                        )}
                        <div
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                color: cardColor,
                                fontSize,
                                fontWeight: 600,
                                lineHeight: 1.35,
                                transition: 'opacity 0.2s ease',
                                wordBreak: 'break-word',
                                opacity: hasItems ? 1 : 0.7,
                            }}
                        >
                            {currentItem}
                        </div>
                        {showControls && (
                            <button
                                type="button"
                                onClick={() => {
                                    if (!canFlip) return;
                                    setCarouselIndex((prev) => (prev + 1) % items.length);
                                }}
                                style={{
                                    width: 28,
                                    height: 28,
                                    borderRadius: '50%',
                                    border: '1px solid rgba(148,163,184,0.4)',
                                    background: 'rgba(15,23,42,0.45)',
                                    color: cardColor,
                                    cursor: canFlip ? 'pointer' : 'default',
                                    opacity: canFlip ? 1 : 0.45,
                                }}
                                title="下一条"
                            >
                                {'>'}
                            </button>
                        )}
                    </div>
                    {showDots && hasItems && (
                        <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', alignItems: 'center' }}>
                            {items.map((_, dotIdx) => (
                                <span
                                    key={`dot-${dotIdx}`}
                                    style={{
                                        width: dotIdx === index ? 16 : 6,
                                        height: 6,
                                        borderRadius: 999,
                                        background: dotIdx === index ? '#38bdf8' : 'rgba(148,163,184,0.45)',
                                        transition: 'all 0.2s ease',
                                    }}
                                />
                            ))}
                        </div>
                    )}
                </div>
            );
        }

        case 'progress-bar': {
            const value = c.value as number;
            return (
                <div style={{
                    width: '100%',
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                }}>
                    <div style={{
                        flex: 1,
                        height: 12,
                        background: t.progressBar.trackBg,
                        borderRadius: 6,
                        overflow: 'hidden',
                    }}>
                        <div style={{
                            width: `${value}%`,
                            height: '100%',
                            background: `linear-gradient(90deg, ${t.progressBar.fillGradient[0]} 0%, ${t.progressBar.fillGradient[1]} 100%)`,
                            borderRadius: 6,
                            transition: 'width 0.3s ease',
                        }} />
                    </div>
                    {Boolean(c.showLabel) && (
                        <span style={{ color: t.progressBar.labelColor, fontSize: 12, minWidth: 40 }}>{value}%</span>
                    )}
                </div>
            );
        }

        case 'tab-switcher': {
            const options = tabOptions;
            const label = String(c.label ?? '切换');
            const activeValue = tabRuntimeValue || tabDefaultValue || options[0]?.value || '';
            const activeTextColor = String(c.activeTextColor || '#0f172a');
            const activeBackgroundColor = String(c.activeBackgroundColor || '#38bdf8');
            const inactiveTextColor = String(c.inactiveTextColor || t.textSecondary);
            const inactiveBackgroundColor = String(c.inactiveBackgroundColor || 'rgba(15,23,42,0.45)');
            const compact = c.compact === true;
            return (
                <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                    <div style={{ fontSize: 12, color: t.textSecondary }}>{label}</div>
                    <div style={{ display: 'flex', gap: compact ? 4 : 8, flexWrap: 'wrap', alignItems: 'center' }}>
                        {options.map((option) => {
                            const active = option.value === activeValue;
                            return (
                                <button
                                    key={option.value}
                                    type="button"
                                    onClick={() => {
                                        if (!tabVariableKey) return;
                                        runtime.setVariable(tabVariableKey, option.value, `tab-switcher:${component.id}`);
                                    }}
                                    style={{
                                        border: '1px solid rgba(148,163,184,0.3)',
                                        background: active ? activeBackgroundColor : inactiveBackgroundColor,
                                        color: active ? activeTextColor : inactiveTextColor,
                                        borderRadius: 999,
                                        padding: compact ? '3px 10px' : '6px 14px',
                                        fontSize: compact ? 11 : 12,
                                        cursor: tabVariableKey ? 'pointer' : 'default',
                                        whiteSpace: 'nowrap',
                                        transition: 'all 0.2s ease',
                                    }}
                                >
                                    {option.label}
                                </button>
                            );
                        })}
                        {options.length === 0 && (
                            <span style={{ fontSize: 12, color: t.textSecondary }}>请在属性中配置 Tab 选项</span>
                        )}
                    </div>
                </div>
            );
        }

        case 'shape': {
            const shapeType = String(c.shapeType || 'rect');
            const fillColor = String(c.fillColor || 'rgba(59,130,246,0.2)');
            const borderColor = String(c.borderColor || '#60a5fa');
            const borderWidth = Math.max(0, Number(c.borderWidth || 2));
            const radius = Math.max(0, Number(c.radius || 8));
            if (shapeType === 'line' || shapeType === 'arrow') {
                return (
                    <svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none">
                        {shapeType === 'arrow' ? (
                            <defs>
                                <marker id={`arrow_${component.id}`} markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                                    <path d="M0,0 L0,6 L6,3 z" fill={borderColor} />
                                </marker>
                            </defs>
                        ) : null}
                        <line
                            x1="8"
                            y1="50"
                            x2="92"
                            y2="50"
                            stroke={borderColor}
                            strokeWidth={borderWidth || 2}
                            markerEnd={shapeType === 'arrow' ? `url(#arrow_${component.id})` : undefined}
                        />
                    </svg>
                );
            }
            const bgImageStyle: React.CSSProperties = (c.backgroundImage && isSafeSrcUrl(c.backgroundImage))
                ? { backgroundImage: `url(${c.backgroundImage as string})`, backgroundSize: 'cover', backgroundPosition: 'center', backgroundRepeat: 'no-repeat' }
                : {};
            if (shapeType === 'circle') {
                return (
                    <div style={{ width: '100%', height: '100%', borderRadius: '50%', background: fillColor, border: `${borderWidth}px solid ${borderColor}`, ...bgImageStyle }} />
                );
            }
            return (
                <div style={{ width: '100%', height: '100%', borderRadius: radius, background: fillColor, border: `${borderWidth}px solid ${borderColor}`, ...bgImageStyle }} />
            );
        }

        case 'container':
            return (
                <div style={{
                    width: '100%',
                    height: '100%',
                    border: `${Math.max(0, Number(c.borderWidth || 1))}px solid ${String(c.borderColor || 'rgba(148,163,184,0.35)')}`,
                    borderRadius: Math.max(0, Number(c.radius || 10)),
                    background: String(c.backgroundColor || t.cardBackground),
                    padding: Math.max(0, Number(c.padding || 12)),
                    boxSizing: 'border-box',
                    color: resolveTextColor(c.titleColor as string | undefined, t.textPrimary),
                }}>
                    <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
                        {String(c.title || '容器')}
                    </div>
                    <div style={{ fontSize: 12, color: t.textSecondary }}>
                        容器组件：可用于分组布局与内容分区
                    </div>
                </div>
            );

        // ── 企业组件 ──

        case 'section-panel': {
            const title = String(c.title || '');
            const titleIcon = c.titleIcon ? String(c.titleIcon) : '';
            const showHeader = c.showHeader !== false;
            const headerHeight = Math.max(0, Number(c.headerHeight || 36));
            const borderStyle = String(c.borderStyle || 'solid');
            const borderWidth = Math.max(0, Number(c.borderWidth || 1));
            const borderRadius = Math.max(0, Number(c.borderRadius || 12));
            const borderColor = c.borderColor as string[] | string | undefined;
            const bgColor = String(c.backgroundColor || 'rgba(30, 41, 59, 0.85)');
            const backdropBlur = Math.max(0, Number(c.backdropBlur || 0));
            const shadow = String(c.shadow || 'none');
            const padding = Math.max(0, Number(c.padding || 16));
            const titleAlign = String(c.titleAlign || 'left') as 'left' | 'center';
            const titleColor = resolveTextColor(c.titleColor as string | undefined, t.textPrimary);
            const headerBg = String(c.headerBackground || 'transparent');

            const shadowMap: Record<string, string> = {
                none: 'none',
                subtle: '0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.06)',
                medium: '0 4px 12px rgba(0,0,0,0.12), 0 2px 4px rgba(0,0,0,0.08)',
                strong: '0 8px 24px rgba(0,0,0,0.2), 0 4px 8px rgba(0,0,0,0.12)',
            };

            // Border style resolution
            let borderCss = 'none';
            if (borderStyle === 'solid') {
                const clr = Array.isArray(borderColor) ? borderColor[0] : (borderColor || 'rgba(148,163,184,0.2)');
                borderCss = `${borderWidth}px solid ${clr}`;
            } else if (borderStyle === 'gradient' && Array.isArray(borderColor) && borderColor.length >= 2) {
                borderCss = `${borderWidth}px solid ${borderColor[0]}`;
            } else if (borderStyle === 'glow' && Array.isArray(borderColor)) {
                borderCss = `${borderWidth}px solid ${borderColor[0] || '#3b82f6'}`;
            }

            const glowShadow = borderStyle === 'glow'
                ? `0 0 12px ${(Array.isArray(borderColor) ? borderColor[0] : borderColor) || '#3b82f6'}40`
                : '';
            const finalShadow = [shadowMap[shadow] || 'none', glowShadow].filter(s => s && s !== 'none').join(', ') || 'none';

            // Gradient border via border-image
            const gradientBorderImage = borderStyle === 'gradient' && Array.isArray(borderColor) && borderColor.length >= 2
                ? `linear-gradient(135deg, ${borderColor[0]}, ${borderColor[1]}) 1`
                : undefined;

            return (
                <div style={{
                    width: '100%', height: '100%', boxSizing: 'border-box',
                    border: borderCss,
                    borderImage: gradientBorderImage,
                    borderRadius,
                    background: bgColor,
                    backdropFilter: backdropBlur > 0 ? `blur(${backdropBlur}px)` : undefined,
                    WebkitBackdropFilter: backdropBlur > 0 ? `blur(${backdropBlur}px)` : undefined,
                    boxShadow: finalShadow,
                    display: 'flex', flexDirection: 'column',
                    overflow: 'hidden',
                }}>
                    {showHeader && (
                        <div style={{
                            height: headerHeight, minHeight: headerHeight,
                            display: 'flex', alignItems: 'center',
                            justifyContent: titleAlign === 'center' ? 'center' : 'flex-start',
                            padding: '0 16px', gap: 8,
                            fontSize: 15, fontWeight: 600, color: titleColor,
                            background: headerBg,
                            borderBottom: `1px solid ${Array.isArray(borderColor) ? borderColor[0] + '30' : 'rgba(148,163,184,0.1)'}`,
                        }}>
                            {titleIcon && <span style={{ fontSize: 16 }}>{titleIcon}</span>}
                            {title}
                        </div>
                    )}
                    <div style={{ flex: 1, padding, overflow: 'hidden', position: 'relative' }}>
                        {/* Child components render here via canvas layer */}
                    </div>
                </div>
            );
        }

        case 'divider': {
            const direction = String(c.direction || 'horizontal');
            const lineStyle = String(c.lineStyle || 'solid');
            const lineColor = String(c.lineColor || 'rgba(148, 163, 184, 0.3)');
            const lineWidth = Math.max(1, Number(c.lineWidth || 1));
            const gradientColors = c.gradientColors as string[] | undefined;

            if (direction === 'vertical') {
                const bg = lineStyle === 'gradient' && gradientColors?.length
                    ? `linear-gradient(to bottom, ${gradientColors.join(', ')})`
                    : lineColor;
                return (
                    <div style={{
                        width: lineWidth, height: '100%', margin: '0 auto',
                        background: bg,
                        borderRadius: lineWidth,
                    }} />
                );
            }
            // horizontal
            const bg = lineStyle === 'gradient' && gradientColors?.length
                ? `linear-gradient(to right, ${gradientColors.join(', ')})`
                : lineColor;
            return (
                <div style={{
                    width: '100%', display: 'flex', alignItems: 'center', height: '100%',
                }}>
                    <div style={{
                        width: '100%', height: lineWidth,
                        background: bg,
                        borderRadius: lineWidth,
                        borderStyle: lineStyle === 'dashed' ? 'dashed' : lineStyle === 'dotted' ? 'dotted' : 'none',
                        borderWidth: lineStyle !== 'solid' && lineStyle !== 'gradient' ? lineWidth : 0,
                        borderColor: lineColor,
                    }} />
                </div>
            );
        }

        case 'stat-card': {
            const title = String(c.title || '指标');
            const value = String(c.value || '0');
            const suffix = String(c.suffix || '');
            const trend = String(c.trend || 'none');
            const trendValue = String(c.trendValue || '');
            const icon = c.icon ? String(c.icon) : '';
            const accentColor = String(c.accentColor || '#3b82f6');
            const showAccentBar = c.showAccentBar !== false;
            const borderRadius = Math.max(0, Number(c.borderRadius || 10));
            const bgColor = String(c.backgroundColor || t.cardBackground);
            const shadow = String(c.shadow || 'subtle');
            const valueColor = resolveTextColor(c.valueColor as string | undefined, t.textPrimary);
            const titleColor = resolveTextColor(c.titleColor as string | undefined, t.textSecondary);

            const shadowMap: Record<string, string> = {
                none: 'none',
                subtle: '0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.06)',
                medium: '0 4px 12px rgba(0,0,0,0.12)',
            };

            const trendIcon = trend === 'up' ? '↑' : trend === 'down' ? '↓' : '';
            const trendColor = trend === 'up' ? '#059669' : trend === 'down' ? '#dc2626' : t.textMuted;

            return (
                <div style={{
                    width: '100%', height: '100%', boxSizing: 'border-box',
                    display: 'flex', borderRadius,
                    background: bgColor,
                    border: `1px solid ${t.cardBorder?.replace(/^1px solid /, '') || 'rgba(148,163,184,0.2)'}`,
                    boxShadow: shadowMap[shadow] || 'none',
                    overflow: 'hidden',
                }}>
                    {showAccentBar && (
                        <div style={{ width: 4, background: accentColor, borderRadius: '4px 0 0 4px', flexShrink: 0 }} />
                    )}
                    <div style={{ flex: 1, padding: '12px 16px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 6, minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            {icon && <span style={{ fontSize: 14, flexShrink: 0 }}>{icon}</span>}
                            <span style={{ fontSize: 12, fontWeight: 500, color: titleColor, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{title}</span>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
                            <span style={{ fontSize: 28, fontWeight: 700, color: valueColor, lineHeight: 1.1 }}>{value}</span>
                            {suffix && <span style={{ fontSize: 13, color: titleColor }}>{suffix}</span>}
                        </div>
                        {trendValue && (
                            <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: trendColor }}>
                                {trendIcon && <span style={{ fontWeight: 600 }}>{trendIcon}</span>}
                                <span>{trendValue}</span>
                            </div>
                        )}
                    </div>
                </div>
            );
        }

        case 'image':
            return isSafeSrcUrl(c.src) ? (
                <img
                    src={c.src as string}
                    alt=""
                    style={{
                        width: '100%',
                        height: '100%',
                        objectFit: c.fit as 'cover' | 'contain' | 'fill',
                    }}
                />
            ) : (
                <div style={{
                    width: '100%',
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: t.placeholder.background,
                    border: t.placeholder.border,
                    borderRadius: 4,
                    color: t.placeholder.color,
                    fontSize: 14,
                }}>
                    图片
                </div>
            );

        case 'video':
            return isSafeSrcUrl(c.src) ? (
                <video
                    src={c.src as string}
                    autoPlay={c.autoplay as boolean}
                    loop={c.loop as boolean}
                    muted={c.muted as boolean}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                />
            ) : (
                <div style={{
                    width: '100%',
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: t.placeholder.background,
                    border: t.placeholder.border,
                    borderRadius: 4,
                    color: t.placeholder.color,
                    fontSize: 14,
                }}>
                    视频
                </div>
            );

        case 'iframe':
            return isSafeSrcUrl(c.src) ? (
                <iframe
                    src={c.src as string}
                    sandbox="allow-scripts allow-same-origin"
                    style={{ width: '100%', height: '100%', border: 'none' }}
                    title="嵌入内容"
                />
            ) : (
                <div style={{
                    width: '100%',
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: t.placeholder.background,
                    border: t.placeholder.border,
                    borderRadius: 4,
                    color: t.placeholder.color,
                    fontSize: 14,
                }}>
                    iframe
                </div>
            );

        default:
            return null;
    }
}
