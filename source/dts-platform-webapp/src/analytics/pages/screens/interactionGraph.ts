import type { ScreenComponent, ScreenConfig } from './types';

function trim(value: unknown): string {
    return typeof value === 'string' ? value.trim() : '';
}

function consumeVariables(component: ScreenComponent): string[] {
    const dataSource = component.dataSource;
    if (!dataSource) return [];
    const sourceType = trim((dataSource as { sourceType?: string }).sourceType || dataSource.type);
    const normalized = sourceType.toLowerCase() === 'database' ? 'sql' : sourceType.toLowerCase();
    const bindings =
        normalized === 'card'
            ? (dataSource.cardConfig?.parameterBindings ?? [])
            : normalized === 'sql'
                ? ((dataSource.sqlConfig?.parameterBindings ?? dataSource.databaseConfig?.parameterBindings) ?? [])
                : normalized === 'metric'
                    ? (dataSource.metricConfig?.parameterBindings ?? [])
                : [];
    const values: string[] = [];
    for (const item of bindings) {
        const key = trim(item.variableKey);
        if (key) values.push(key);
    }
    return Array.from(new Set(values));
}

function emitVariables(component: ScreenComponent): string[] {
    const directFilterEmit: string[] = [];
    if (component.type === 'filter-input' || component.type === 'filter-select') {
        const key = trim(component.config?.variableKey);
        if (key) directFilterEmit.push(key);
    } else if (component.type === 'filter-date-range') {
        const startKey = trim(component.config?.startKey);
        const endKey = trim(component.config?.endKey);
        if (startKey) directFilterEmit.push(startKey);
        if (endKey) directFilterEmit.push(endKey);
    }

    if (!component.interaction?.enabled) return Array.from(new Set(directFilterEmit));
    const mappings = component.interaction.mappings ?? [];
    const values: string[] = [];
    for (const mapping of mappings) {
        const key = trim(mapping.variableKey);
        if (key) values.push(key);
    }
    return Array.from(new Set([...directFilterEmit, ...values]));
}

function nodeLabel(node: string, componentNames: Map<string, string>): string {
    if (node.startsWith('c:')) {
        const id = node.slice(2);
        return `组件(${componentNames.get(id) ?? id})`;
    }
    if (node.startsWith('v:')) {
        return `变量(${node.slice(2)})`;
    }
    return node;
}

function cycleKey(cycle: string[]): string {
    return cycle.join('->');
}

export function detectInteractionCycles(config: ScreenConfig): string[] {
    const components = config.components ?? [];
    const adjacency = new Map<string, Set<string>>();
    const componentNames = new Map<string, string>();

    const addEdge = (from: string, to: string) => {
        if (!adjacency.has(from)) adjacency.set(from, new Set());
        adjacency.get(from)?.add(to);
    };

    for (const component of components) {
        componentNames.set(component.id, component.name || component.id);

        const emit = emitVariables(component);
        const consume = consumeVariables(component);
        const cNode = `c:${component.id}`;

        for (const v of emit) addEdge(cNode, `v:${v}`);
        for (const v of consume) addEdge(`v:${v}`, cNode);
    }

    const state = new Map<string, 0 | 1 | 2>();
    const stack: string[] = [];
    const seenCycles = new Set<string>();
    const cycles: string[] = [];

    const dfs = (node: string) => {
        state.set(node, 1);
        stack.push(node);

        for (const next of adjacency.get(node) ?? []) {
            const nextState = state.get(next) ?? 0;
            if (nextState === 0) {
                dfs(next);
                continue;
            }
            if (nextState === 1) {
                const idx = stack.indexOf(next);
                if (idx >= 0) {
                    const cycleNodes = [...stack.slice(idx), next];
                    const key = cycleKey(cycleNodes);
                    if (!seenCycles.has(key)) {
                        seenCycles.add(key);
                        cycles.push(cycleNodes.map((n) => nodeLabel(n, componentNames)).join(' -> '));
                    }
                }
            }
        }

        stack.pop();
        state.set(node, 2);
    };

    const allNodes = new Set<string>();
    for (const [from, tos] of adjacency.entries()) {
        allNodes.add(from);
        for (const to of tos) allNodes.add(to);
    }

    for (const node of allNodes) {
        if ((state.get(node) ?? 0) === 0) dfs(node);
        if (cycles.length >= 10) break;
    }

    return cycles;
}
