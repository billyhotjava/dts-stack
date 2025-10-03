import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { PortalMenuCollection, PortalMenuItem } from "@/admin/types";
import { setPortalMenus } from "@/store/portalMenuStore";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { toast } from "sonner";

export default function PortalMenusView() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ["admin", "portal-menus"],
    queryFn: adminApi.getPortalMenus,
  });

  const treeMenus = useMemo(() => data?.allMenus ?? data?.menus ?? [], [data?.allMenus, data?.menus]);
  const activeMenus = useMemo(() => data?.menus ?? [], [data?.menus]);

  const [pending, setPending] = useState<Record<number, boolean>>({});
  const [resetting, setResetting] = useState(false);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  useEffect(() => {
    if (!isLoading) {
      setPortalMenus(activeMenus, treeMenus);
    }
  }, [activeMenus, treeMenus, isLoading]);

  // 默认展开第一层
  useEffect(() => {
    const next = new Set<number>();
    const roots = Array.isArray(treeMenus) ? treeMenus : [];
    for (const item of roots) {
      if (item?.id != null && item.children && item.children.length > 0) {
        next.add(item.id);
      }
    }
    setExpanded(next);
  }, [treeMenus]);

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });

  const updateCache = (next: PortalMenuCollection) => {
    queryClient.setQueryData(["admin", "portal-menus"], next);
  };

  const markPending = (id: number, value: boolean) => {
    setPending((prev) => {
      const next = { ...prev };
      if (value) {
        next[id] = true;
      } else {
        delete next[id];
      }
      return next;
    });
  };

  const handleToggle = async (id: number, currentlyDeleted: boolean) => {
    markPending(id, true);
    try {
      if (currentlyDeleted) {
        const result = await adminApi.updatePortalMenu(id, {
          deleted: false,
        } as unknown as PortalMenuItem);
        updateCache(result);
        toast.success("菜单已启用");
      } else {
        const result = await adminApi.deletePortalMenu(id);
        updateCache(result);
        toast.success("菜单已禁用");
      }
    } catch (error: any) {
      toast.error(error?.message || "操作失败，请稍后重试");
      await refresh();
    } finally {
      markPending(id, false);
    }
  };

  const handleReset = async () => {
    setResetting(true);
    try {
      const result = await adminApi.resetPortalMenus();
      updateCache(result);
      toast.success("默认菜单已恢复");
    } catch (error: any) {
      toast.error(error?.message || "恢复失败，请稍后再试");
      await refresh();
    } finally {
      setResetting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Text variant="body1" className="text-lg font-semibold">
          菜单管理
        </Text>
        <Button variant="secondary" onClick={handleReset} disabled={resetting}>
          {resetting ? "恢复中.." : "恢复默认菜单"}
        </Button>
      </div>

      <Card>
        <CardHeader className="pb-2">
          <div className="flex items-center gap-2">
            <CardTitle className="mr-2">菜单编辑器</CardTitle>
            <div className="ml-auto flex items-center gap-2">
              <Button
                size="sm"
                variant="outline"
                onClick={() => setExpanded(collectFolderIds(treeMenus))}
              >
                全部展开
              </Button>
              <Button size="sm" variant="outline" onClick={() => setExpanded(new Set())}>
                全部折叠
              </Button>
            </div>
          </div>
          <Text variant="body3" className="text-muted-foreground">
            说明：父节点仅用于分组，不提供启用/禁用按钮；叶子节点可切换状态
          </Text>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <Text variant="body3">加载中..</Text>
          ) : treeMenus.length === 0 ? (
            <Text variant="body3">暂无菜单数据</Text>
          ) : (
            <div className="space-y-2">
              {treeMenus.map((item) => (
                <MenuRow
                  key={item.id}
                  item={item}
                  level={0}
                  pathNames={[]}
                  expanded={expanded}
                  setExpanded={setExpanded}
                  pending={pending}
                  onToggle={handleToggle}
                />
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

type MenuRowProps = {
  item: PortalMenuItem;
  level: number;
  pathNames: string[];
  expanded: Set<number>;
  setExpanded: (next: Set<number>) => void;
  pending: Record<number, boolean>;
  onToggle: (id: number, currentlyDeleted: boolean) => void;
};

function MenuRow({ item, level, pathNames, expanded, setExpanded, pending, onToggle }: MenuRowProps) {
  const id = item.id as number | undefined;
  if (id == null) return null;
  const name = item.displayName ?? item.name ?? String(id);
  const children = Array.isArray(item.children) ? item.children : [];
  const isFolder = children.length > 0;
  const isExpanded = isFolder && expanded.has(id);
  const busy = pending[id];
  const isDeleted = Boolean(item.deleted);
  const fullPath = [
    ...pathNames,
    name,
  ];

  const toggleExpand = () => {
    if (!isFolder) return;
    const next = new Set(expanded);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setExpanded(next);
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 rounded-xl border bg-background px-3 py-2 hover:bg-accent/5">
        <button
          aria-label="toggle"
          className="h-6 w-6 shrink-0 rounded-md border text-xs"
          onClick={toggleExpand}
          disabled={!isFolder}
          title={isFolder ? "折叠/展开" : "无子节点"}
        >
          {isFolder ? (isExpanded ? "▾" : "▸") : "·"}
        </button>
        <div style={{ marginLeft: level * 14 }} className="-ml-1" />
        <div className="min-w-0 flex-1 truncate font-semibold">{name}</div>
        <div className="text-xs text-muted-foreground">/{fullPath.join("/")}</div>
        <div className="ml-2 flex items-center gap-2">
          {isFolder ? (
            <Badge variant="outline">目录</Badge>
          ) : (
            <>
              <Badge variant={isDeleted ? "error" : "success"}>{isDeleted ? "已禁用" : "已启用"}</Badge>
              <Button
                size="sm"
                variant="outline"
                onClick={() => onToggle(id, true)}
                disabled={busy || !isDeleted}
              >
                启用
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => onToggle(id, false)}
                disabled={busy || isDeleted}
              >
                禁用
              </Button>
            </>
          )}
        </div>
      </div>

      {isFolder && isExpanded && (
        <div className="space-y-2 pl-6">
          {children.map((c) => (
            <MenuRow
              key={c.id}
              item={c}
              level={level + 1}
              pathNames={[...pathNames, name]}
              expanded={expanded}
              setExpanded={setExpanded}
              pending={pending}
              onToggle={onToggle}
            />)
          )}
        </div>
      )}
    </div>
  );
}

function collectFolderIds(items: PortalMenuItem[]): Set<number> {
  const out = new Set<number>();
  const walk = (list: PortalMenuItem[]) => {
    for (const it of list) {
      if (it?.id == null) continue;
      if (it.children && it.children.length > 0) {
        out.add(it.id as number);
        walk(it.children);
      }
    }
  };
  walk(items ?? []);
  return out;
}
