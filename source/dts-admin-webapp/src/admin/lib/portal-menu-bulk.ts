import type { PortalMenuItem } from "@/admin/types";

export type BulkMenuRoleMode = "APPEND" | "REMOVE" | "REPLACE";
export type MenuSelectionState = "checked" | "indeterminate" | "unchecked";

export function collectSelectableMenuIds(items: PortalMenuItem[] | PortalMenuItem | null | undefined): number[] {
	const roots = Array.isArray(items) ? items : items ? [items] : [];
	const ids: number[] = [];
	const walk = (nodes: PortalMenuItem[]) => {
		for (const node of nodes) {
			if (!node || node.id == null) {
				continue;
			}
			const children = Array.isArray(node.children) ? node.children : [];
			if (children.length > 0) {
				walk(children);
				continue;
			}
			if (!node.deleted) {
				ids.push(Number(node.id));
			}
		}
	};
	walk(roots);
	return ids;
}

export function resolveMenuSelectionState(
	menu: PortalMenuItem | null | undefined,
	selectedIds: Set<number>,
): MenuSelectionState {
	const selectableIds = collectSelectableMenuIds(menu);
	if (selectableIds.length === 0) {
		return "unchecked";
	}
	const selectedCount = selectableIds.filter((id) => selectedIds.has(id)).length;
	if (selectedCount === 0) {
		return "unchecked";
	}
	if (selectedCount === selectableIds.length) {
		return "checked";
	}
	return "indeterminate";
}

export function mergeMenuRoles(
	currentRoles: string[] | null | undefined,
	selectedRoles: string[] | null | undefined,
	mode: BulkMenuRoleMode,
): string[] {
	const current = new Set((currentRoles ?? []).filter(Boolean));
	const selected = (selectedRoles ?? []).filter(Boolean);
	if (mode === "REPLACE") {
		return Array.from(new Set(selected));
	}
	if (mode === "REMOVE") {
		for (const role of selected) {
			current.delete(role);
		}
		return Array.from(current);
	}
	for (const role of selected) {
		current.add(role);
	}
	return Array.from(current);
}
