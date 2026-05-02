/**
 * Sprint-12 F4/T02 — 文字/KPI 响应式渲染组件。
 *
 * 把原来 BasicRenderer.tsx 里 title / number-card / stat-card 三个 case 分支
 * 抽成独立组件，这样才能调用 `useContainerFontSize` hook（renderBasic 是普通
 * 函数，switch/case 里不能直接用 hook）。
 *
 * 字号策略：
 * - 如果组件配置里给了 fontSizeRatio > 0 → 走自适应（min/max/ratio 来自 c 或默认）
 * - 否则 → 用固定 fontSize（c.fontSize / c.titleFontSize / c.valueFontSize）
 *
 * 默认参数（在 schema/默认配置里也应暴露同名字段）：
 * - title:            min=14, max=40, ratio=0.05
 * - number-card title:min=12, max=20, ratio=0.03
 * - number-card value:min=16, max=96, ratio=0.15
 * - stat-card  title: min=12, max=18, ratio=0.025
 * - stat-card  value: min=16, max=64, ratio=0.12
 */

import { useRef, type CSSProperties } from "react";
import type { ScreenThemeTokens } from "../../screenThemes";
import { resolveTextColor } from "../shared/chartUtils";
import { useContainerFontSize } from "../../hooks/useContainerFontSize";

function pickFontSize(
    c: Record<string, unknown>,
    fallbackPx: number,
    ratioKey: string,
    minKey: string,
    maxKey: string,
    defaults: { min: number; max: number; ratio: number },
) {
    const ratioRaw = c[ratioKey];
    const ratio = typeof ratioRaw === "number" && ratioRaw > 0 ? ratioRaw : 0;
    const min = typeof c[minKey] === "number" ? (c[minKey] as number) : defaults.min;
    const max = typeof c[maxKey] === "number" ? (c[maxKey] as number) : defaults.max;
    return { ratio, min, max, fallback: fallbackPx };
}

export function TitleBasic({
    c,
    t,
}: {
    c: Record<string, unknown>;
    t: ScreenThemeTokens;
}) {
    const ref = useRef<HTMLDivElement>(null);
    const cfg = pickFontSize(c, (c.fontSize as number) || 20, "fontSizeRatio", "fontSizeMin", "fontSizeMax", {
        min: 14,
        max: 40,
        ratio: 0.05,
    });
    const adaptive = useContainerFontSize(ref, { min: cfg.min, max: cfg.max, ratio: cfg.ratio || 0.05 });
    const effectiveFontSize = cfg.ratio > 0 ? adaptive : cfg.fallback;

    const style: CSSProperties = {
        width: "100%",
        height: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: (c.textAlign as string) || "left",
        fontSize: effectiveFontSize,
        fontWeight: (c.fontWeight as string) || "normal",
        fontFamily: c.fontFamily ? (c.fontFamily as string) : undefined,
        color: resolveTextColor(c.color as string | undefined, t.textPrimary),
    };

    return (
        <div ref={ref} style={style}>
            {(c.text as string) || ""}
        </div>
    );
}

export function NumberCardBasic({
    c,
    t,
}: {
    c: Record<string, unknown>;
    t: ScreenThemeTokens;
}) {
    const ref = useRef<HTMLDivElement>(null);
    // 用数字本体的自适应驱动整卡；标题字号按 50% 比例从数字字号派生
    const valueCfg = pickFontSize(
        c,
        (c.valueFontSize as number) || 34,
        "valueFontSizeRatio",
        "valueFontSizeMin",
        "valueFontSizeMax",
        { min: 16, max: 96, ratio: 0.15 },
    );
    const titleCfg = pickFontSize(
        c,
        (c.titleFontSize as number) || 16,
        "titleFontSizeRatio",
        "titleFontSizeMin",
        "titleFontSizeMax",
        { min: 12, max: 20, ratio: 0.03 },
    );
    const valueAdaptive = useContainerFontSize(ref, {
        min: valueCfg.min,
        max: valueCfg.max,
        ratio: valueCfg.ratio || 0.15,
    });
    const titleAdaptive = useContainerFontSize(ref, {
        min: titleCfg.min,
        max: titleCfg.max,
        ratio: titleCfg.ratio || 0.03,
    });
    const valueFontSize = valueCfg.ratio > 0 ? valueAdaptive : valueCfg.fallback;
    const titleFontSize = titleCfg.ratio > 0 ? titleAdaptive : titleCfg.fallback;

    return (
        <div
            ref={ref}
            style={{
                width: "100%",
                height: "100%",
                display: "flex",
                flexDirection: "column",
                justifyContent: "center",
                alignItems: "center",
                background: (c.backgroundColor as string) || t.numberCard.background,
                borderRadius: t.cardBorderRadius,
                border: t.numberCard.border,
                boxShadow: t.cardShadow,
                fontFamily: c.fontFamily ? (c.fontFamily as string) : undefined,
                overflow: "hidden",
            }}
        >
            <div
                style={{
                    fontSize: titleFontSize,
                    fontWeight: 600,
                    color: resolveTextColor(c.titleColor as string | undefined, t.numberCard.titleColor),
                    marginBottom: 8,
                }}
            >
                {c.title as string}
            </div>
            <div
                style={{
                    fontSize: valueFontSize,
                    fontWeight: "bold",
                    color: resolveTextColor(c.valueColor as string | undefined, t.numberCard.valueColor),
                    lineHeight: 1.1,
                }}
            >
                {(c.prefix as string) || ""}
                {c.value != null ? Number(c.value).toLocaleString("zh-CN") : "-"}
                {(c.suffix as string) || ""}
            </div>
        </div>
    );
}

export function StatCardBasic({
    c,
    t,
}: {
    c: Record<string, unknown>;
    t: ScreenThemeTokens;
}) {
    const ref = useRef<HTMLDivElement>(null);

    const title = String(c.title || "指标");
    const value = String(c.value || "0");
    const prefix = String(c.prefix || "");
    const suffix = String(c.suffix || "");
    const trend = String(c.trend || "none");
    const trendValue = String(c.trendValue || "");
    const icon = c.icon ? String(c.icon) : "";
    const accentColor = String(c.accentColor || "#3b82f6");
    const showAccentBar = c.showAccentBar !== false;
    const borderRadius = Math.max(0, Number(c.borderRadius || 10));
    const bgColor = String(c.backgroundColor || t.cardBackground);
    const shadow = String(c.shadow || "subtle");
    const valueColor = resolveTextColor(c.valueColor as string | undefined, t.textPrimary);
    const titleColor = resolveTextColor(c.titleColor as string | undefined, t.textSecondary);

    const shadowMap: Record<string, string> = {
        none: "none",
        subtle: "0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.06)",
        medium: "0 4px 12px rgba(0,0,0,0.12)",
    };

    const trendIcon = trend === "up" ? "↑" : trend === "down" ? "↓" : "";
    const trendColor = trend === "up" ? "#059669" : trend === "down" ? "#dc2626" : t.textMuted;

    const valueCfg = pickFontSize(
        c,
        28,
        "valueFontSizeRatio",
        "valueFontSizeMin",
        "valueFontSizeMax",
        { min: 16, max: 64, ratio: 0.12 },
    );
    const titleCfg = pickFontSize(
        c,
        12,
        "titleFontSizeRatio",
        "titleFontSizeMin",
        "titleFontSizeMax",
        { min: 11, max: 18, ratio: 0.025 },
    );
    const valueAdaptive = useContainerFontSize(ref, {
        min: valueCfg.min,
        max: valueCfg.max,
        ratio: valueCfg.ratio || 0.12,
    });
    const titleAdaptive = useContainerFontSize(ref, {
        min: titleCfg.min,
        max: titleCfg.max,
        ratio: titleCfg.ratio || 0.025,
    });
    const valueFontSize = valueCfg.ratio > 0 ? valueAdaptive : valueCfg.fallback;
    const titleFontSize = titleCfg.ratio > 0 ? titleAdaptive : titleCfg.fallback;

    return (
        <div
            ref={ref}
            style={{
                width: "100%",
                height: "100%",
                boxSizing: "border-box",
                display: "flex",
                borderRadius,
                background: bgColor,
                border: `1px solid ${t.cardBorder?.replace(/^1px solid /, "") || "rgba(148,163,184,0.2)"}`,
                boxShadow: shadowMap[shadow] || "none",
                overflow: "hidden",
            }}
        >
            {showAccentBar && (
                <div
                    style={{ width: 4, background: accentColor, borderRadius: "4px 0 0 4px", flexShrink: 0 }}
                />
            )}
            <div
                style={{
                    flex: 1,
                    padding: "12px 16px",
                    display: "flex",
                    flexDirection: "column",
                    justifyContent: "center",
                    gap: 6,
                    minWidth: 0,
                }}
            >
                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    {icon && <span style={{ fontSize: Math.max(12, titleFontSize + 2), flexShrink: 0 }}>{icon}</span>}
                    <span
                        style={{
                            fontSize: titleFontSize,
                            fontWeight: 500,
                            color: titleColor,
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                        }}
                    >
                        {title}
                    </span>
                </div>
                <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
                    {prefix && <span style={{ fontSize: Math.max(12, titleFontSize + 1), color: titleColor }}>{prefix}</span>}
                    <span
                        style={{
                            fontSize: valueFontSize,
                            fontWeight: 700,
                            color: valueColor,
                            lineHeight: 1.1,
                        }}
                    >
                        {value}
                    </span>
                    {suffix && <span style={{ fontSize: Math.max(12, titleFontSize + 1), color: titleColor }}>{suffix}</span>}
                </div>
                {trendValue && (
                    <div
                        style={{
                            display: "flex",
                            alignItems: "center",
                            gap: 4,
                            fontSize: titleFontSize,
                            color: trendColor,
                        }}
                    >
                        {trendIcon && <span style={{ fontWeight: 600 }}>{trendIcon}</span>}
                        <span>{trendValue}</span>
                    </div>
                )}
            </div>
        </div>
    );
}
