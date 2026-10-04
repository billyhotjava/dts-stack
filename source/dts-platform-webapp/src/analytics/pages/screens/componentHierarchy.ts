import type { ScreenComponent } from './types';

type ComponentMap = Map<string, ScreenComponent>;

export function buildComponentMap(components: ScreenComponent[]): ComponentMap {
    return new Map((components || []).map((item) => [item.id, item]));
}

export function isComponentEffectivelyVisible(
    component: ScreenComponent,
    componentMap: ComponentMap,
): boolean {
    if (!component?.visible) {
        return false;
    }
    const visited = new Set<string>();
    let current: ScreenComponent | undefined = component;
    while (current?.parentContainerId) {
        const parentId = current.parentContainerId;
        if (visited.has(parentId)) {
            return false;
        }
        visited.add(parentId);
        const parent = componentMap.get(parentId);
        if (!parent) {
            break;
        }
        if (!parent.visible) {
            return false;
        }
        current = parent;
    }
    return true;
}

export function wouldCreateParentCycle(
    components: ScreenComponent[],
    componentId: string,
    nextParentId?: string,
): boolean {
    const parentId = String(nextParentId || '').trim();
    if (!parentId) {
        return false;
    }
    if (parentId === componentId) {
        return true;
    }
    const componentMap = buildComponentMap(components);
    const visited = new Set<string>();
    let currentId: string | undefined = parentId;
    while (currentId) {
        if (currentId === componentId) {
            return true;
        }
        if (visited.has(currentId)) {
            return true;
        }
        visited.add(currentId);
        const current = componentMap.get(currentId);
        currentId = current?.parentContainerId;
    }
    return false;
}

export function collectContainerSubtreeIds(
    components: ScreenComponent[],
    seedIds: string[],
): string[] {
    const out = new Set(seedIds);
    if (!Array.isArray(components) || components.length === 0 || !Array.isArray(seedIds) || seedIds.length === 0) {
        return Array.from(out);
    }

    const byParent = new Map<string, ScreenComponent[]>();
    const byId = buildComponentMap(components);
    for (const item of components) {
        const parentId = item.parentContainerId;
        if (!parentId) continue;
        const list = byParent.get(parentId) ?? [];
        list.push(item);
        byParent.set(parentId, list);
    }

    const queue: string[] = [];
    for (const id of seedIds) {
        const item = byId.get(id);
        if (item?.type === 'container') {
            queue.push(id);
        }
    }

    while (queue.length > 0) {
        const currentId = queue.shift();
        if (!currentId) continue;
        const children = byParent.get(currentId) ?? [];
        for (const child of children) {
            if (out.has(child.id)) {
                continue;
            }
            out.add(child.id);
            if (child.type === 'container') {
                queue.push(child.id);
            }
        }
    }

    return Array.from(out);
}

export function sanitizeParentContainerIds(components: ScreenComponent[]): ScreenComponent[] {
    if (!Array.isArray(components) || components.length === 0) {
        return components;
    }
    const componentMap = buildComponentMap(components);
    let changed = false;
    const next = components.map((item) => {
        const parentId = String(item.parentContainerId || '').trim();
        if (!parentId) {
            return item;
        }
        const parent = componentMap.get(parentId);
        const invalidParent = !parent || parent.type !== 'container';
        const hasCycle = wouldCreateParentCycle(components, item.id, parentId);
        if (!invalidParent && !hasCycle) {
            return item;
        }
        changed = true;
        const { parentContainerId: _parentContainerId, ...rest } = item;
        return rest;
    });
    return changed ? next : components;
}
