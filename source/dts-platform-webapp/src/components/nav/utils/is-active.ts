const stripTrailingSlash = (value: string): string =>
	value.length > 1 && value.endsWith("/") ? value.slice(0, -1) : value;

/**
 * 判断菜单项是否处于激活状态。
 *
 * 之前使用 `pathname.includes(menuPath)` 会出现兄弟菜单互相误命中的问题：
 * 当 "我的概览"(/workbench) 与 "待办事项"(/workbench/todo) 平级时，
 * 访问 /workbench/todo 会让两个菜单同时高亮。
 *
 * 现在改为路径段感知的匹配：
 *  - 容器菜单（hasChild）允许前缀匹配，以便父级菜单展开。
 *  - 叶子菜单只允许精确匹配，避免兄弟菜单互相覆盖。
 */
export function isNavItemActive(pathname: string, menuPath: string, hasChild: boolean, search = ""): boolean {
	if (!menuPath) return false;
	const current = stripTrailingSlash(pathname || "");
	if (current === "/data-architecture" && menuPath.startsWith("/data-architecture?")) {
		return menuPath === dataArchitecturePath(resolveDataArchitectureView(new URLSearchParams(search).get("view")));
	}
	const target = stripTrailingSlash(menuPath);
	if (current === target) return true;
	if (hasChild) {
		return current.startsWith(`${target}/`);
	}
	return false;
}

import { dataArchitecturePath, resolveDataArchitectureView } from "@/pages/data-architecture/navigation";
