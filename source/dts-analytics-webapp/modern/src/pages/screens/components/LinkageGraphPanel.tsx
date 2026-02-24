import { useMemo } from 'react';
import type { ScreenConfig, ScreenComponent } from '../types';

interface LinkageGraphPanelProps {
    config: ScreenConfig;
    selectedIds: string[];
    onClose: () => void;
}

interface GraphNode {
    id: string;
    label: string;
    kind: 'component' | 'variable';
    type?: string; // component type
    x: number;
    y: number;
}

interface GraphEdge {
    from: string;
    to: string;
    isCycle?: boolean;
}

function emitVariables(c: ScreenComponent): string[] {
    const keys: string[] = [];
    if (c.type === 'filter-input' || c.type === 'filter-select') {
        const k = String(c.config?.variableKey ?? '').trim();
        if (k) keys.push(k);
    } else if (c.type === 'filter-date-range') {
        const s = String(c.config?.startKey ?? '').trim();
        const e = String(c.config?.endKey ?? '').trim();
        if (s) keys.push(s);
        if (e) keys.push(e);
    }
    if (c.interaction?.enabled) {
        for (const m of c.interaction.mappings ?? []) {
            const k = String(m.variableKey ?? '').trim();
            if (k) keys.push(k);
        }
    }
    return [...new Set(keys)];
}

function consumeVariables(c: ScreenComponent): string[] {
    const ds = c.dataSource;
    if (!ds) return [];
    const st = String((ds as unknown as Record<string, unknown>).sourceType ?? (ds as unknown as Record<string, unknown>).type ?? '').toLowerCase();
    const normalized = st === 'database' ? 'sql' : st;
    const bindings = normalized === 'card'
        ? (ds.cardConfig?.parameterBindings ?? [])
        : normalized === 'sql'
            ? ((ds.sqlConfig?.parameterBindings ?? ds.databaseConfig?.parameterBindings) ?? [])
            : normalized === 'metric'
                ? (ds.metricConfig?.parameterBindings ?? [])
                : [];
    const keys: string[] = [];
    for (const b of bindings) {
        const k = String((b as unknown as Record<string, unknown>).variableKey ?? '').trim();
        if (k) keys.push(k);
    }
    return [...new Set(keys)];
}

const TYPE_ICONS: Record<string, string> = {
    'line-chart': '📈', 'bar-chart': '📊', 'pie-chart': '🥧', 'gauge-chart': '🎯',
    'map-chart': '🗺', 'table': '📋', 'number-card': '🔢', 'filter-input': '🔍',
    'filter-select': '📝', 'filter-date-range': '📅', 'title': 'T',
};

export function LinkageGraphPanel({ config, selectedIds, onClose }: LinkageGraphPanelProps) {
    const { nodes, edges, hasCycles } = useMemo(() => {
        const components = config.components ?? [];
        const variableSet = new Set<string>();
        const edgeList: GraphEdge[] = [];
        const compEmit = new Map<string, string[]>();
        const compConsume = new Map<string, string[]>();

        for (const c of components) {
            const em = emitVariables(c);
            const co = consumeVariables(c);
            compEmit.set(c.id, em);
            compConsume.set(c.id, co);
            em.forEach(v => variableSet.add(v));
            co.forEach(v => variableSet.add(v));
        }

        // Only show components that participate in linkage
        const linkedCompIds = new Set<string>();
        for (const c of components) {
            if ((compEmit.get(c.id)?.length ?? 0) > 0 || (compConsume.get(c.id)?.length ?? 0) > 0) {
                linkedCompIds.add(c.id);
            }
        }

        // Layout: components on left, variables in center, consuming components on right
        const compNodes: GraphNode[] = [];
        const varNodes: GraphNode[] = [];
        const linkedComps = components.filter(c => linkedCompIds.has(c.id));
        const variables = Array.from(variableSet);

        // Simple force-free layout
        const leftX = 50;
        const midX = 260;
        const rightX = 470;

        // Emitters on left
        const emitters = linkedComps.filter(c => (compEmit.get(c.id)?.length ?? 0) > 0);
        const consumers = linkedComps.filter(c => (compConsume.get(c.id)?.length ?? 0) > 0 && !emitters.some(e => e.id === c.id));

        emitters.forEach((c, i) => {
            compNodes.push({
                id: `c:${c.id}`, label: c.name || c.id, kind: 'component',
                type: c.type, x: leftX, y: 40 + i * 56,
            });
        });

        variables.forEach((v, i) => {
            varNodes.push({
                id: `v:${v}`, label: v, kind: 'variable',
                x: midX, y: 40 + i * 56,
            });
        });

        consumers.forEach((c, i) => {
            compNodes.push({
                id: `c:${c.id}`, label: c.name || c.id, kind: 'component',
                type: c.type, x: rightX, y: 40 + i * 56,
            });
        });

        // Build edges: comp → variable (emit) and variable → comp (consume)
        for (const c of linkedComps) {
            for (const v of compEmit.get(c.id) ?? []) {
                edgeList.push({ from: `c:${c.id}`, to: `v:${v}` });
            }
            for (const v of compConsume.get(c.id) ?? []) {
                edgeList.push({ from: `v:${v}`, to: `c:${c.id}` });
            }
        }

        // Check for cycles (simple: if comp both emits and consumes same var chain)
        let foundCycle = false;
        for (const c of linkedComps) {
            const em = new Set(compEmit.get(c.id) ?? []);
            const co = new Set(compConsume.get(c.id) ?? []);
            for (const v of em) {
                if (co.has(v)) { foundCycle = true; break; }
            }
            if (foundCycle) break;
        }

        return {
            nodes: [...compNodes, ...varNodes],
            edges: edgeList,
            hasCycles: foundCycle,
        };
    }, [config.components]);

    const nodeMap = useMemo(() => new Map(nodes.map(n => [n.id, n])), [nodes]);
    const svgWidth = 580;
    const svgHeight = Math.max(200, nodes.length * 30 + 60);

    const isSelected = (nodeId: string) => {
        if (!nodeId.startsWith('c:')) return false;
        return selectedIds.includes(nodeId.slice(2));
    };

    return (
        <div className="linkage-graph-overlay" onClick={onClose}>
            <div className="linkage-graph-panel" onClick={(e) => e.stopPropagation()}>
                <div className="linkage-graph-header">
                    <h3 style={{ margin: 0, fontSize: 14 }}>联动关系图</h3>
                    {hasCycles && <span style={{ color: '#f87171', fontSize: 11 }}>⚠ 检测到循环依赖</span>}
                    <button className="linkage-graph-close" onClick={onClose}>×</button>
                </div>
                <div className="linkage-graph-body">
                    {nodes.length === 0 ? (
                        <div style={{ padding: 20, color: '#94a3b8', textAlign: 'center' }}>暂无联动关系</div>
                    ) : (
                        <svg width={svgWidth} height={svgHeight} style={{ display: 'block' }}>
                            <defs>
                                <marker id="arrowhead" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
                                    <polygon points="0 0, 8 3, 0 6" fill="#64748b" />
                                </marker>
                            </defs>
                            {edges.map((e, i) => {
                                const from = nodeMap.get(e.from);
                                const to = nodeMap.get(e.to);
                                if (!from || !to) return null;
                                return (
                                    <line
                                        key={i}
                                        x1={from.x + 80} y1={from.y + 14}
                                        x2={to.x - 4} y2={to.y + 14}
                                        stroke={e.isCycle ? '#f87171' : '#475569'}
                                        strokeWidth={1.5}
                                        markerEnd="url(#arrowhead)"
                                    />
                                );
                            })}
                            {nodes.map((n) => {
                                const selected = isSelected(n.id);
                                const fill = n.kind === 'variable' ? '#1e293b' : (selected ? '#0c4a6e' : '#1e293b');
                                const stroke = n.kind === 'variable' ? '#facc15' : (selected ? '#00d4ff' : '#334155');
                                return (
                                    <g key={n.id} transform={`translate(${n.x}, ${n.y})`}>
                                        <rect
                                            width={160} height={28} rx={6}
                                            fill={fill} stroke={stroke} strokeWidth={selected ? 2 : 1}
                                        />
                                        <text x={8} y={18} fill={n.kind === 'variable' ? '#fde68a' : '#e2e8f0'} fontSize={11}>
                                            {n.kind === 'component'
                                                ? `${TYPE_ICONS[n.type ?? ''] ?? '◼'} ${n.label.slice(0, 14)}`
                                                : `⚡ ${n.label}`
                                            }
                                        </text>
                                    </g>
                                );
                            })}
                        </svg>
                    )}
                </div>
            </div>
            <style>{`
                .linkage-graph-overlay {
                    position: fixed;
                    top: 0; right: 0; bottom: 0;
                    width: 640px;
                    background: rgba(2, 6, 23, 0.92);
                    z-index: 900;
                    display: flex;
                    flex-direction: column;
                    border-left: 1px solid rgba(148, 163, 184, 0.2);
                }
                .linkage-graph-panel {
                    display: flex;
                    flex-direction: column;
                    height: 100%;
                }
                .linkage-graph-header {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    padding: 12px 16px;
                    border-bottom: 1px solid rgba(148, 163, 184, 0.2);
                    color: #e2e8f0;
                }
                .linkage-graph-close {
                    margin-left: auto;
                    background: none;
                    border: none;
                    color: #94a3b8;
                    font-size: 18px;
                    cursor: pointer;
                }
                .linkage-graph-body {
                    flex: 1;
                    overflow: auto;
                    padding: 16px;
                }
            `}</style>
        </div>
    );
}
