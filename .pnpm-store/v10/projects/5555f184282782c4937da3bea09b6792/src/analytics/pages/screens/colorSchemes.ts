/**
 * Predefined color schemes for ECharts visualizations.
 * Each scheme provides a palette of colors suitable for chart series,
 * plus metadata for smart recommendation.
 */

export interface ColorScheme {
    id: string;
    name: string;
    /** ECharts color palette (series colors). */
    colors: string[];
    /** Suitable theme backgrounds. */
    suitableThemes: Array<'legacy-dark' | 'titanium' | 'glacier' | 'any'>;
    /** Component types this scheme is particularly good for. */
    suitableCharts?: string[];
    /** Whether this scheme has good contrast for accessibility. */
    highContrast?: boolean;
}

export const COLOR_SCHEMES: ColorScheme[] = [
    {
        id: 'business-blue',
        name: '商务蓝',
        colors: ['#3b82f6', '#60a5fa', '#93c5fd', '#2563eb', '#1d4ed8', '#1e40af', '#3730a3', '#4f46e5'],
        suitableThemes: ['legacy-dark', 'titanium'],
    },
    {
        id: 'tech-purple',
        name: '科技紫',
        colors: ['#8b5cf6', '#a78bfa', '#c4b5fd', '#7c3aed', '#6d28d9', '#5b21b6', '#4c1d95', '#6366f1'],
        suitableThemes: ['legacy-dark'],
    },
    {
        id: 'nature-green',
        name: '自然绿',
        colors: ['#22c55e', '#4ade80', '#86efac', '#16a34a', '#15803d', '#166534', '#14532d', '#10b981'],
        suitableThemes: ['legacy-dark', 'glacier'],
    },
    {
        id: 'warm',
        name: '暖色调',
        colors: ['#f59e0b', '#f97316', '#ef4444', '#ec4899', '#fb923c', '#fbbf24', '#e11d48', '#d946ef'],
        suitableThemes: ['legacy-dark', 'titanium'],
    },
    {
        id: 'cool',
        name: '冷色调',
        colors: ['#06b6d4', '#0891b2', '#0e7490', '#14b8a6', '#2dd4bf', '#5eead4', '#0d9488', '#0f766e'],
        suitableThemes: ['legacy-dark', 'titanium'],
    },
    {
        id: 'high-contrast',
        name: '高对比度',
        colors: ['#ef4444', '#3b82f6', '#22c55e', '#f59e0b', '#8b5cf6', '#06b6d4', '#ec4899', '#f97316'],
        suitableThemes: ['any'],
        highContrast: true,
    },
    {
        id: 'gradient-blue',
        name: '蓝色渐变',
        colors: ['#1e3a5f', '#1e4d8c', '#2563eb', '#3b82f6', '#60a5fa', '#93c5fd', '#bfdbfe', '#dbeafe'],
        suitableThemes: ['legacy-dark'],
        suitableCharts: ['map-chart', 'treemap-chart'],
    },
    {
        id: 'gradient-heat',
        name: '热力渐变',
        colors: ['#1a1a2e', '#16213e', '#0f3460', '#533483', '#e94560', '#f59e0b', '#fbbf24', '#fef3c7'],
        suitableThemes: ['legacy-dark'],
        suitableCharts: ['map-chart', 'scatter-chart'],
    },
    {
        id: 'pastel',
        name: '柔和马卡龙',
        colors: ['#fca5a5', '#fdba74', '#fcd34d', '#86efac', '#67e8f9', '#a5b4fc', '#f0abfc', '#fda4af'],
        suitableThemes: ['glacier'],
        suitableCharts: ['pie-chart', 'funnel-chart'],
    },
    {
        id: 'monochrome-grey',
        name: '灰度',
        colors: ['#1e293b', '#334155', '#475569', '#64748b', '#94a3b8', '#cbd5e1', '#e2e8f0', '#f1f5f9'],
        suitableThemes: ['any'],
    },
];

/**
 * Get a color scheme by ID.
 */
export function getColorScheme(id: string): ColorScheme | undefined {
    return COLOR_SCHEMES.find(s => s.id === id);
}

/**
 * Recommend color schemes for a given context.
 * Returns schemes sorted by relevance.
 */
export function recommendColorSchemes(
    theme?: string,
    chartType?: string,
): ColorScheme[] {
    const scored = COLOR_SCHEMES.map(scheme => {
        let score = 0;
        // Theme match
        if (scheme.suitableThemes.includes('any')) {
            score += 1;
        } else if (theme && scheme.suitableThemes.includes(theme as 'legacy-dark' | 'titanium' | 'glacier')) {
            score += 3;
        }
        // Chart type match
        if (chartType && scheme.suitableCharts?.includes(chartType)) {
            score += 5;
        }
        return { scheme, score };
    });

    return scored
        .sort((a, b) => b.score - a.score)
        .map(item => item.scheme);
}

/* ---------- Chart type recommendation ---------- */

export interface ChartRecommendation {
    type: string;
    name: string;
    reason: string;
    confidence: number; // 0-1
}

interface DataProfile {
    /** Total row count. */
    rowCount: number;
    /** Column metadata. */
    columns: Array<{
        name: string;
        type: 'string' | 'number' | 'date' | 'boolean' | 'unknown';
        uniqueCount?: number;
    }>;
}

/**
 * Rule-based chart type recommendation given a data profile.
 * Returns up to 3 recommendations sorted by confidence.
 */
export function recommendChartTypes(profile: DataProfile): ChartRecommendation[] {
    const results: ChartRecommendation[] = [];
    const dims = profile.columns.filter(c => c.type === 'string');
    const measures = profile.columns.filter(c => c.type === 'number');
    const dates = profile.columns.filter(c => c.type === 'date');
    const hasGeo = dims.some(d =>
        /province|city|region|country|area|地区|省|市|国|区域/i.test(d.name),
    );

    // Single value → gauge / number-card
    if (measures.length === 1 && dims.length === 0 && dates.length === 0) {
        results.push({ type: 'gauge-chart', name: '仪表盘', reason: '单一数值指标适合仪表盘展示', confidence: 0.9 });
        results.push({ type: 'number-card', name: 'KPI 卡片', reason: '单一数值适合 KPI 卡片', confidence: 0.85 });
    }

    // Date + measure → line chart
    if (dates.length >= 1 && measures.length >= 1) {
        results.push({ type: 'line-chart', name: '折线图', reason: '时间序列数据适合折线图展示趋势', confidence: 0.9 });
        if (measures.length >= 2) {
            results.push({ type: 'combo-chart', name: '组合图', reason: '多度量时间序列适合组合图对比', confidence: 0.8 });
        }
    }

    // Geo dimension → map
    if (hasGeo && measures.length >= 1) {
        results.push({ type: 'map-chart', name: '地图', reason: '包含地理维度，适合地图展示分布', confidence: 0.85 });
    }

    // Category + measure → bar or pie
    if (dims.length === 1 && measures.length >= 1 && dates.length === 0) {
        const uniqueCount = dims[0].uniqueCount ?? profile.rowCount;
        if (uniqueCount <= 8) {
            results.push({ type: 'pie-chart', name: '饼图', reason: '少量类别适合饼图展示占比', confidence: 0.8 });
        }
        results.push({ type: 'bar-chart', name: '柱状图', reason: '分类对比适合柱状图', confidence: 0.85 });
    }

    // 2 measures → scatter
    if (measures.length >= 2) {
        results.push({ type: 'scatter-chart', name: '散点图', reason: '两个度量适合散点图分析相关性', confidence: 0.7 });
    }

    // Hierarchical → treemap/sunburst
    if (dims.length >= 2 && measures.length >= 1) {
        results.push({ type: 'treemap-chart', name: '矩形树图', reason: '多层级维度适合矩形树图', confidence: 0.65 });
    }

    // Funnel (stage-like dimension names)
    const hasStage = dims.some(d =>
        /stage|step|phase|阶段|步骤|环节|漏斗/i.test(d.name),
    );
    if (hasStage && measures.length >= 1) {
        results.push({ type: 'funnel-chart', name: '漏斗图', reason: '阶段数据适合漏斗图展示转化', confidence: 0.8 });
    }

    // Radar (multiple measures, few dimensions)
    if (measures.length >= 3 && dims.length <= 1) {
        results.push({ type: 'radar-chart', name: '雷达图', reason: '多维度度量适合雷达图展示能力模型', confidence: 0.6 });
    }

    // Sort by confidence and deduplicate
    const seen = new Set<string>();
    return results
        .sort((a, b) => b.confidence - a.confidence)
        .filter(r => {
            if (seen.has(r.type)) return false;
            seen.add(r.type);
            return true;
        })
        .slice(0, 3);
}
