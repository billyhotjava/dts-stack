import { useMemo } from "react";
import { useLocation, Navigate } from "react-router";
import { useMenuStore } from "@/store/menuStore";
import type { MenuTree } from "#/entity";
import { Component } from "./utils";

type Props = { base?: string };

function findByPath(items: MenuTree[], fullPath: string): MenuTree | null {
  const stack: MenuTree[] = [...items];
  while (stack.length) {
    const item = stack.shift()!;
    if (item.path === fullPath) return item;
    if (Array.isArray(item.children) && item.children.length) {
      stack.push(...item.children);
    }
  }
  return null;
}

export function DynamicMenuResolver({ base }: Props) {
  const location = useLocation();
  const menus = useMenuStore((s) => s.menus);

  const match = useMemo(() => {
    const pathname = location.pathname || "/";
    if (base && !pathname.startsWith(base)) return null;
    return findByPath(menus || [], pathname);
  }, [location.pathname, base, menus]);

  if (!match) {
    // No matching menu/component; fall back to 404
    return <Navigate to="/404" replace />;
  }
  if (!match.component) {
    // Menu present but no component mapped; treat as 404
    return <Navigate to="/404" replace />;
  }
  return <>{Component(match.component)}</>;
}

