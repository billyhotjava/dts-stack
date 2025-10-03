import { useEffect, useState, useMemo } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { PortalMenuItem } from "@/admin/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Text } from "@/ui/typography";
import { Button } from "@/ui/button";
import { toast } from "sonner";

export default function PortalMenusView() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ["admin", "portal-menus"],
    queryFn: adminApi.getPortalMenus,
  });

  const activeMenus = useMemo(() => data?.active ?? [], [data?.active]);
  const deletedMenus = useMemo(() => data?.deleted ?? [], [data?.deleted]);

  const [nameDrafts, setNameDrafts] = useState<Record<number, string>>({});
  const [pending, setPending] = useState<Record<number, boolean>>({});
  const [resetting, setResetting] = useState(false);

  useEffect(() => {
    const drafts: Record<number, string> = {};
    const hydrate = (items: PortalMenuItem[]) => {
      items.forEach((item) => {
        if (item.id != null) {
          drafts[item.id] = item.displayName ?? item.name ?? "";
        }
        if (item.children && item.children.length > 0) {
          hydrate(item.children);
        }
      });
    };
    hydrate(activeMenus);
    hydrate(deletedMenus);
    setNameDrafts(drafts);
  }, [activeMenus, deletedMenus]);

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ["admin", "portal-menus"] });

  const updateDraftName = (id: number, value: string) => {
    setNameDrafts((prev) => ({ ...prev, [id]: value }));
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

  const handleSaveName = async (id: number) => {
    const name = (nameDrafts[id] ?? "").trim();
    if (!name) {
      toast.error("菜单名称不能为空");
      return;
    }
    markPending(id, true);
    try {
      await adminApi.draftUpdateMenu(id, {
        name,
      } as unknown as PortalMenuItem);
      // Optimistic: update local cache
      queryClient.setQueryData(["admin", "portal-menus"], (prev: any) => {
        if (!prev) return prev;
        const { active = [], deleted = [] } = prev as {
          active: PortalMenuItem[];
          deleted: PortalMenuItem[];
        };
        return {
          active: updateNameInTree(active, id, name),
          deleted: updateNameInList(deleted, id, name),
        };
      });
      toast.success("已提交名称变更");
      await refresh();
    } catch (error) {
      toast.error("提交失败，请稍后重试");
    } finally {
      markPending(id, false);
    }
  };

  const handleToggle = async (id: number, enable: boolean) => {
    markPending(id, true);
    try {
      if (enable) {
        await adminApi.draftUpdateMenu(id, {
          deleted: false,
        } as unknown as PortalMenuItem);
        // optimistic: move from deleted -> active
        queryClient.setQueryData(["admin", "portal-menus"], (prev: any) =>
          moveMenu(prev, id, true),
        );
        toast.success("已提交启用请求");
      } else {
        await adminApi.draftDeleteMenu(id);
        // optimistic: move from active -> deleted (include subtree)
        queryClient.setQueryData(["admin", "portal-menus"], (prev: any) =>
          moveMenu(prev, id, false),
        );
        toast.success("已提交禁用请求");
      }
      await refresh();
    } catch (error) {
      toast.error("提交失败，请稍后重试");
    } finally {
      markPending(id, false);
    }
  };

  const handleReset = async () => {
    setResetting(true);
    try {
      await adminApi.resetPortalMenus();
      toast.success("已提交恢复默认菜单请求");
      await refresh();
    } catch (error) {
      toast.error("恢复失败，请稍后再试");
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

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>启用菜单</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Text variant="body3">加载中..</Text>
            ) : activeMenus.length === 0 ? (
              <Text variant="body3">暂无启用的菜单</Text>
            ) : (
              <MenuTree
                items={activeMenus}
                mode="active"
                nameDrafts={nameDrafts}
                pending={pending}
                onNameChange={updateDraftName}
                onSave={handleSaveName}
                onToggle={handleToggle}
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>禁用菜单</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Text variant="body3">加载中..</Text>
            ) : deletedMenus.length === 0 ? (
              <Text variant="body3">暂无禁用的菜单</Text>
            ) : (
              <MenuTree
                items={deletedMenus}
                mode="disabled"
                nameDrafts={nameDrafts}
                pending={pending}
                onNameChange={updateDraftName}
                onSave={handleSaveName}
                onToggle={handleToggle}
              />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

type MenuTreeProps = {
  items: PortalMenuItem[];
  mode: "active" | "disabled";
  nameDrafts: Record<number, string>;
  pending: Record<number, boolean>;
  onNameChange: (id: number, value: string) => void;
  onSave: (id: number) => void;
  onToggle: (id: number, enabled: boolean) => void;
};

function MenuTree({ items, mode, nameDrafts, pending, onNameChange, onSave, onToggle }: MenuTreeProps) {
  if (!items.length) {
    return null;
  }

  return (
    <ul className="space-y-3">
      {items.map((item) => {
        if (item.id == null) {
          return null;
        }
        const id = item.id;
        const originalName = item.displayName ?? item.name ?? "";
        const currentName = nameDrafts[id] ?? originalName;
        const changed = currentName.trim() !== originalName;
        const busy = pending[id];
        const childMenus = Array.isArray(item.children) ? item.children : [];

        return (
          <li key={id} className="rounded-md border p-3">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div className="flex-1 space-y-2">
                <Input value={currentName} onChange={(e) => onNameChange(id, e.target.value)} placeholder="菜单名称" />
                <div className="text-xs text-muted-foreground">
                  编号：{id}
                  {item.path ? ` · 路径：${item.path}` : ""}
                </div>
              </div>
              <div className="flex flex-shrink-0 gap-2">
                <Button size="sm" onClick={() => onSave(id)} disabled={!changed || busy}>
                  保存
                </Button>
                <Button
                  size="sm"
                  variant={mode === "active" ? "destructive" : "secondary"}
                  onClick={() => onToggle(id, mode === "disabled")}
                  disabled={busy}
                >
                  {mode === "active" ? "禁用" : "启用"}
                </Button>
              </div>
            </div>
            {mode === "active" && childMenus.length > 0 ? (
              <div className="mt-3 border-l pl-4">
                <MenuTree
                  items={childMenus}
                  mode="active"
                  nameDrafts={nameDrafts}
                  pending={pending}
                  onNameChange={onNameChange}
                  onSave={onSave}
                  onToggle={onToggle}
                />
              </div>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

// ---------- helpers for optimistic updates ----------

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v));
}

function updateNameInTree(list: PortalMenuItem[], id: number, name: string): PortalMenuItem[] {
  const next = clone(list);
  const dfs = (nodes?: PortalMenuItem[]) => {
    if (!nodes) return;
    for (const n of nodes) {
      if (n.id === id) {
        n.displayName = name;
        n.name = n.name || name;
        return;
      }
      if (n.children && n.children.length) dfs(n.children);
    }
  };
  dfs(next);
  return next;
}

function updateNameInList(list: PortalMenuItem[], id: number, name: string): PortalMenuItem[] {
  const next = clone(list);
  for (const n of next) {
    if (n.id === id) {
      n.displayName = name;
      n.name = n.name || name;
      break;
    }
  }
  return next;
}

function removeFromTreeById(list: PortalMenuItem[], id: number): { nextTree: PortalMenuItem[]; removed?: PortalMenuItem } {
  const next = clone(list);
  let removed: PortalMenuItem | undefined;
  const walk = (nodes: PortalMenuItem[]) => {
    for (let i = 0; i < nodes.length; i++) {
      const n = nodes[i];
      if (n.id === id) {
        removed = n;
        nodes.splice(i, 1);
        return true;
      }
      if (n.children && n.children.length) {
        const hit = walk(n.children);
        if (hit) return true;
      }
    }
    return false;
  };
  walk(next);
  return { nextTree: next, removed };
}

function flattenTree(node?: PortalMenuItem): PortalMenuItem[] {
  if (!node) return [];
  const out: PortalMenuItem[] = [];
  const stack: PortalMenuItem[] = [node];
  while (stack.length) {
    const n = stack.pop()!;
    const copy = { ...n, children: undefined } as PortalMenuItem;
    out.push(copy);
    if (n.children && n.children.length) {
      for (const c of n.children) stack.push(c);
    }
  }
  return out;
}

function addToTree(list: PortalMenuItem[], node: PortalMenuItem): PortalMenuItem[] {
  const next = clone(list);
  const parentId = node.parentId ?? null;
  if (parentId == null) {
    next.push({ ...node, children: node.children ?? [] });
    return next;
  }
  const dfs = (nodes?: PortalMenuItem[]): boolean => {
    if (!nodes) return false;
    for (const n of nodes) {
      if (n.id === parentId) {
        const children = Array.isArray(n.children) ? n.children : (n.children = []);
        children.push({ ...node, children: node.children ?? [] });
        return true;
      }
      if (n.children && n.children.length) {
        if (dfs(n.children)) return true;
      }
    }
    return false;
  };
  if (!dfs(next)) {
    next.push({ ...node, children: node.children ?? [] });
  }
  return next;
}

function moveMenu(prev: any, id: number, enable: boolean) {
  if (!prev) return prev;
  const data = prev as { active: PortalMenuItem[]; deleted: PortalMenuItem[] };
  if (enable) {
    const idx = data.deleted.findIndex((m) => m.id === id);
    if (idx >= 0) {
      const [item] = data.deleted.splice(idx, 1);
      return { active: addToTree(data.active, item), deleted: data.deleted };
    }
    return prev;
  } else {
    const { nextTree, removed } = removeFromTreeById(data.active, id);
    const moved = flattenTree(removed);
    return { active: nextTree, deleted: [...data.deleted, ...moved] };
  }
}

