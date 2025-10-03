import { useEffect, useState, useMemo } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { PortalMenuCollection, PortalMenuItem } from "@/admin/types";
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
  const deletedMenus = useMemo(() => buildMenuHierarchy(data?.deleted ?? []), [data?.deleted]);

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

  const updateCache = (next: PortalMenuCollection) => {
    queryClient.setQueryData(["admin", "portal-menus"], next);
  };

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
      const result = await adminApi.updatePortalMenu(id, {
        name,
      } as unknown as PortalMenuItem);
      updateCache(result);
      toast.success("菜单名称已保存");
    } catch (error: any) {
      toast.error(error?.message || "保存失败，请稍后重试");
      await refresh();
    } finally {
      markPending(id, false);
    }
  };

  const handleToggle = async (id: number, enable: boolean) => {
    markPending(id, true);
    try {
      if (enable) {
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
            {childMenus.length > 0 ? (
              <div className="mt-3 border-l pl-4">
                <MenuTree
                  items={childMenus}
                  mode={mode}
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

function buildMenuHierarchy(items: PortalMenuItem[]): PortalMenuItem[] {
  if (!items.length) {
    return [];
  }

  const nodes = new Map<number, PortalMenuItem>();
  const roots: PortalMenuItem[] = [];

  for (const item of items) {
    if (item.id == null) {
      continue;
    }
    nodes.set(item.id, { ...item, children: [] });
  }

  for (const item of items) {
    if (item.id == null) {
      continue;
    }
    const node = nodes.get(item.id);
    if (!node) {
      continue;
    }
    const parentId = item.parentId ?? null;
    if (parentId != null && nodes.has(parentId)) {
      const parent = nodes.get(parentId);
      if (parent) {
        if (!Array.isArray(parent.children)) {
          parent.children = [];
        }
        parent.children.push(node);
      }
    } else {
      roots.push(node);
    }
  }

  const sortBranch = (branch: PortalMenuItem[]) => {
    branch.sort((a, b) => {
      const orderA = a.sortOrder ?? Number.MAX_SAFE_INTEGER;
      const orderB = b.sortOrder ?? Number.MAX_SAFE_INTEGER;
      if (orderA !== orderB) {
        return orderA - orderB;
      }
      const idA = a.id ?? 0;
      const idB = b.id ?? 0;
      return idA - idB;
    });
    for (const node of branch) {
      if (Array.isArray(node.children) && node.children.length > 0) {
        sortBranch(node.children);
      }
    }
  };

  sortBranch(roots);
  return roots;
}
