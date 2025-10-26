import type { MenuTree } from "#/entity";
import { PermissionType } from "#/enum";

export type MenuMetadata = Record<string, any> | null;

export const parseMenuMetadata = (metadata: unknown): MenuMetadata => {
	if (!metadata) return null;
	if (typeof metadata === "object") {
		return metadata as Record<string, any>;
	}
	if (typeof metadata !== "string") {
		return null;
	}
	const trimmed = metadata.trim();
	if (!trimmed) {
		return null;
	}
	try {
		return JSON.parse(trimmed);
	} catch {
		return null;
	}
};

export const isExternalPath = (path: string): boolean => /^(https?:|mailto:|tel:)/i.test(path);

export const normalizeMenuPath = (path?: string | null): string => {
	if (!path) return "";
	const trimmed = String(path).trim();
	if (!trimmed) return "";
	if (isExternalPath(trimmed)) {
		return trimmed;
	}
	const withoutHash = trimmed.replace(/^#+/, "");
	if (!withoutHash) return "";
	if (withoutHash.startsWith("/")) {
		const normalized = withoutHash.replace(/\/{2,}/g, "/");
		return normalized === "" ? "/" : normalized;
	}
	const normalized = `/${withoutHash.replace(/^\/+/, "")}`;
	return normalized === "" ? "/" : normalized;
};

export const resolveMenuPath = (node: MenuTree, meta: MenuMetadata): string => {
	const rawPath = node?.path ?? (typeof meta?.path === "string" ? meta.path : undefined);
	return normalizeMenuPath(rawPath);
};

export const isMenuDeleted = (node: MenuTree): boolean => Boolean((node as unknown as { deleted?: boolean })?.deleted);

export const isMenuHidden = (node: MenuTree, meta: MenuMetadata): boolean =>
	meta?.hidden === true || meta?.hide === true || meta?.visible === false || Boolean((node as any)?.hidden);

export const isMenuDisabled = (node: MenuTree, meta: MenuMetadata): boolean =>
	meta?.disabled === true || meta?.status === "DISABLED" || Boolean((node as any)?.disabled);

export const hasMenuComponent = (node: MenuTree): boolean =>
	typeof node.component === "string" && node.component.trim().length > 0;

export const menuTypeOf = (node: MenuTree): number | undefined =>
	typeof node.type === "number" ? node.type : undefined;

export const isContainerMenu = (node: MenuTree): boolean => {
	const typeValue = menuTypeOf(node);
	return typeValue !== undefined && typeValue < PermissionType.MENU;
};

const shouldSkipMenu = (node: MenuTree, meta: MenuMetadata): boolean =>
	isMenuDeleted(node) || isMenuHidden(node, meta) || isMenuDisabled(node, meta);

export const findMenuByPath = (menus: MenuTree[], targetPath: string): MenuTree | null => {
	const target = normalizeMenuPath(targetPath);
	if (!target) return null;
	const queue: MenuTree[] = Array.isArray(menus) ? [...menus] : [];
	while (queue.length) {
		const node = queue.shift()!;
		const meta = parseMenuMetadata(node?.metadata);
		if (shouldSkipMenu(node, meta)) {
			continue;
		}
		const path = resolveMenuPath(node, meta);
		if (path === target) {
			return node;
		}
		if (Array.isArray(node.children) && node.children.length > 0) {
			queue.push(...(node.children as MenuTree[]));
		}
	}
	return null;
};

export const firstAccessibleMenuPath = (menus: MenuTree[]): string | null => {
	const queue: MenuTree[] = Array.isArray(menus) ? [...menus] : [];
	while (queue.length) {
		const node = queue.shift()!;
		const meta = parseMenuMetadata(node?.metadata);
		if (shouldSkipMenu(node, meta)) {
			continue;
		}
		const path = resolveMenuPath(node, meta);
		const typeValue = menuTypeOf(node);
		const isLeafType = typeValue === undefined || typeValue >= PermissionType.MENU;
		if (path && !isExternalPath(path) && (hasMenuComponent(node) || isLeafType)) {
			return path;
		}
		if (Array.isArray(node.children) && node.children.length > 0) {
			queue.push(...(node.children as MenuTree[]));
		}
	}
	return null;
};

export const firstAccessibleChildPath = (node: MenuTree | null | undefined): string | null => {
	if (!node || !Array.isArray(node.children)) {
		return null;
	}
	return firstAccessibleMenuPath(node.children as MenuTree[]);
};

export const findBestMenuMatch = (menus: MenuTree[], targetPath: string): MenuTree | null => {
	const normalized = normalizeMenuPath(targetPath);
	if (!normalized) return null;
	let best: { node: MenuTree; length: number } | null = null;
	const stack: MenuTree[] = Array.isArray(menus) ? [...menus] : [];
	while (stack.length) {
		const node = stack.pop()!;
		const meta = parseMenuMetadata(node?.metadata);
		if (shouldSkipMenu(node, meta)) {
			continue;
		}
		const menuPath = resolveMenuPath(node, meta);
		if (menuPath) {
			const base = menuPath.endsWith("/") ? menuPath.slice(0, -1) : menuPath;
			const target = normalized.endsWith("/") ? normalized.slice(0, -1) : normalized;
			const isExact = target === menuPath || target === base;
			const isPrefix =
				menuPath !== "/" &&
				(target.startsWith(menuPath.endsWith("/") ? menuPath : `${menuPath}/`) ||
					target.startsWith(base === "" ? "/" : `${base}/`));
			if (isExact || isPrefix) {
				const length = menuPath.length;
				if (!best || length > best.length || (length === best.length && isExact)) {
					best = { node, length };
				}
			}
		}
		if (Array.isArray(node.children) && node.children.length > 0) {
			stack.push(...(node.children as MenuTree[]));
		}
	}
	return best?.node ?? null;
};
