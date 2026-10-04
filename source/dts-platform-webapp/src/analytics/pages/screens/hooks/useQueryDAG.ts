/**
 * useQueryDAG — Analyze component data source dependencies and determine
 * execution order. Components referencing other components' data (via cardConfig)
 * should execute after their dependencies.
 *
 * Returns a topologically sorted list of component IDs.
 */
import { useMemo } from 'react';
import type { ScreenComponent } from '../types';

interface DAGNode {
    id: string;
    dependsOn: string[];
}

/**
 * Build dependency graph from components' cardConfig references.
 */
function buildDependencyGraph(components: ScreenComponent[]): DAGNode[] {
    const componentIds = new Set(components.map((c) => c.id));
    return components.map((c) => {
        const deps: string[] = [];
        const cardId = c.dataSource?.cardConfig?.cardId;
        if (cardId != null) {
            const refId = String(cardId);
            if (componentIds.has(refId) && refId !== c.id) {
                deps.push(refId);
            }
        }
        return { id: c.id, dependsOn: deps };
    });
}

/**
 * Topological sort using Kahn's algorithm.
 * Returns component IDs in execution order.
 * Components with no dependencies come first.
 */
function topoSort(nodes: DAGNode[]): string[] {
    const inDegree = new Map<string, number>();
    const adjacency = new Map<string, string[]>();

    for (const node of nodes) {
        if (!inDegree.has(node.id)) inDegree.set(node.id, 0);
        if (!adjacency.has(node.id)) adjacency.set(node.id, []);
        for (const dep of node.dependsOn) {
            if (!adjacency.has(dep)) adjacency.set(dep, []);
            adjacency.get(dep)!.push(node.id);
            inDegree.set(node.id, (inDegree.get(node.id) ?? 0) + 1);
        }
    }

    const queue: string[] = [];
    for (const [id, degree] of inDegree) {
        if (degree === 0) queue.push(id);
    }

    const sorted: string[] = [];
    while (queue.length > 0) {
        const current = queue.shift()!;
        sorted.push(current);
        for (const neighbor of (adjacency.get(current) ?? [])) {
            const newDegree = (inDegree.get(neighbor) ?? 1) - 1;
            inDegree.set(neighbor, newDegree);
            if (newDegree === 0) queue.push(neighbor);
        }
    }

    // Append any remaining (cyclic) nodes at the end
    for (const node of nodes) {
        if (!sorted.includes(node.id)) {
            sorted.push(node.id);
        }
    }

    return sorted;
}

/**
 * Hook: returns component IDs in dependency-resolved execution order.
 */
export function useQueryDAG(components: ScreenComponent[]): string[] {
    return useMemo(() => {
        const graph = buildDependencyGraph(components);
        return topoSort(graph);
    }, [components]);
}
