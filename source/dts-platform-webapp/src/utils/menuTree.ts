import type { MenuTree } from "#/entity";
import { PermissionType } from "#/enum";

export type MenuMetadata = Record<string, any> | null;

const parseLegacyJavaMapString = (raw: string): Record<string, any> | null => {
	const text = raw.trim();
	if (!text.startsWith("{") || !text.endsWith("}") || !text.includes("=")) {
		return null;
	}
	const inner = text.slice(1, -1).trim();
	if (!inner) {
		return {};
	}
	const out: Record<string, any> = {};
	// Typical format: "{key=value, key2=value2}"
	// Note: This is a best-effort parser for legacy rows; values containing ", " may not round-trip perfectly.
	for (const part of inner.split(/,\s*/g)) {
		const idx = part.indexOf("=");
		if (idx <= 0) continue;
		const key = part.slice(0, idx).trim();
		const value = part.slice(idx + 1).trim();
		if (!key) continue;
		out[key] = value;
	}
	return Object.keys(out).length ? out : null;
};

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

	const tryParse = (text: string): unknown => {
		try {
			return JSON.parse(text);
		} catch {
			return parseLegacyJavaMapString(text);
		}
	};

	const parsed = tryParse(trimmed);
	// Handle double-encoded JSON string: "\"{\\\"externalLink\\\":\\\"...\\\"}\""
	if (typeof parsed === "string") {
		const nested = parsed.trim();
		if (nested.startsWith("{") || nested.startsWith("[")) {
			const parsed2 = tryParse(nested);
			if (parsed2 && typeof parsed2 === "object") {
				return parsed2 as Record<string, any>;
			}
		}
		return null;
	}
	if (parsed && typeof parsed === "object") {
		return parsed as Record<string, any>;
	}
	return null;
};

export const isExternalPath = (path: string): boolean => {
	const value = String(path || "").trim();
	if (!value) return false;
	if (/^(https?:|mailto:|tel:)/i.test(value)) return true;
	// Reverse-proxied tools should be treated like external navigation (full page load).
	const lower = value.toLowerCase();
	return (
		lower.startsWith("/dashboards") ||
		lower.startsWith("/screen") ||
		lower.startsWith("/dashboard/hetu")
	);
};

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

const joinRelativeMenuPath = (parentPath: string, childPath: string): string => {
	const normalizedParent = normalizeMenuPath(parentPath);
	const normalizedChild = normalizeMenuPath(childPath);
	if (!normalizedParent) return normalizedChild;
	if (!normalizedChild) return normalizedParent;
	if (normalizedChild === "/") return normalizedParent;
	return normalizeMenuPath(`${normalizedParent}/${normalizedChild.replace(/^\/+/, "")}`);
};

export const resolveMenuPath = (node: MenuTree, meta: MenuMetadata, parentPath?: string): string => {
	// Prefer explicit external link in metadata so navigation can open the real URL directly (avoids popup blockers).
	const external =
		(meta as any)?.externalLink ??
		(meta as any)?.external_link ??
		(meta as any)?.url ??
		(meta as any)?.href ??
		(meta as any)?.link ??
		(meta as any)?.src;
	if (typeof external === "string" && external.trim()) {
		return normalizeMenuPath(external);
	}
	const rawPath = node?.path ?? (typeof (meta as any)?.path === "string" ? (meta as any).path : undefined);
	const normalized = normalizeMenuPath(rawPath);
	if (!normalized) {
		return parentPath ? normalizeMenuPath(parentPath) : "";
	}
	if (!parentPath) {
		return normalized;
	}
	const raw = String(rawPath ?? "").trim();
	if (raw.startsWith("/")) {
		return normalized;
	}
	return joinRelativeMenuPath(parentPath, raw);
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
	const queue: Array<{ node: MenuTree; parentPath?: string }> = Array.isArray(menus)
		? menus.map((node) => ({ node }))
		: [];
	while (queue.length) {
		const current = queue.shift()!;
		const node = current.node;
		const meta = parseMenuMetadata(node?.metadata);
		if (shouldSkipMenu(node, meta)) {
			continue;
		}
		const path = resolveMenuPath(node, meta, current.parentPath);
		if (path === target) {
			return node;
		}
		if (Array.isArray(node.children) && node.children.length > 0) {
			for (const child of node.children as MenuTree[]) {
				queue.push({ node: child, parentPath: path || current.parentPath });
			}
		}
	}
	return null;
};

export const firstAccessibleMenuPath = (menus: MenuTree[]): string | null => {
	const queue: Array<{ node: MenuTree; parentPath?: string }> = Array.isArray(menus)
		? menus.map((node) => ({ node }))
		: [];
	while (queue.length) {
		const current = queue.shift()!;
		const node = current.node;
		const meta = parseMenuMetadata(node?.metadata);
		if (shouldSkipMenu(node, meta)) {
			continue;
		}
		const path = resolveMenuPath(node, meta, current.parentPath);
		const typeValue = menuTypeOf(node);
		const isLeafType = typeValue === undefined || typeValue >= PermissionType.MENU;
		if (path && !isExternalPath(path) && (hasMenuComponent(node) || isLeafType)) {
			return path;
		}
		if (Array.isArray(node.children) && node.children.length > 0) {
			for (const child of node.children as MenuTree[]) {
				queue.push({ node: child, parentPath: path || current.parentPath });
			}
		}
	}
	return null;
};

export const firstAccessibleChildPath = (node: MenuTree | null | undefined): string | null => {
	if (!node || !Array.isArray(node.children)) {
		return null;
	}
	const meta = parseMenuMetadata(node.metadata);
	const parentPath = resolveMenuPath(node, meta);
	return firstAccessibleMenuPath(
		(node.children as MenuTree[]).map((child) => ({
			...child,
			path: resolveMenuPath(child, parseMenuMetadata(child.metadata), parentPath),
		})),
	);
};

export const findBestMenuMatch = (menus: MenuTree[], targetPath: string): MenuTree | null => {
	const normalized = normalizeMenuPath(targetPath);
	if (!normalized) return null;
	let best: { node: MenuTree; length: number } | null = null;
	const stack: Array<{ node: MenuTree; parentPath?: string }> = Array.isArray(menus)
		? menus.map((node) => ({ node }))
		: [];
	while (stack.length) {
		const current = stack.pop()!;
		const node = current.node;
		const meta = parseMenuMetadata(node?.metadata);
		if (shouldSkipMenu(node, meta)) {
			continue;
		}
		const menuPath = resolveMenuPath(node, meta, current.parentPath);
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
			for (const child of node.children as MenuTree[]) {
				stack.push({ node: child, parentPath: menuPath || current.parentPath });
			}
		}
	}
	return best?.node ?? null;
};
