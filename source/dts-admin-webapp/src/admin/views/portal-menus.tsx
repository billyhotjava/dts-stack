import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { PortalMenuCollection, PortalMenuItem } from "@/admin/types";
import { setPortalMenus } from "@/store/portalMenuStore";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
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
    hydrate(treeMenus);
    setNameDrafts(drafts);
  }, [treeMenus]);

  useEffect(() => {
    if (!isLoading) {
      setPortalMenus(activeMenus, treeMenus);
    }
  }, [activeMenus, treeMenus, isLoading]);

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
        <CardHeader>
          <CardTitle>菜单树</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <Text variant="body3">加载中..</Text>
          ) : treeMenus.length === 0 ? (
            <Text variant="body3">暂无菜单数据</Text>
          ) : (
            <MenuTree
              items={treeMenus}
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
  );
}

type MenuTreeProps = {
  items: PortalMenuItem[];
  nameDrafts: Record<number, string>;
  pending: Record<number, boolean>;
  onNameChange: (id: number, value: string) => void;
  onSave: (id: number) => void;
  onToggle: (id: number, currentlyDeleted: boolean) => void;
  level?: number;
};

function MenuTree({ items, nameDrafts, pending, onNameChange, onSave, onToggle, level = 0 }: MenuTreeProps) {
  if (!items.length) {
    return null;
  }

  return (
    <ul className={level === 0 ? "space-y-4" : "space-y-3"}>
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
        const isDeleted = Boolean(item.deleted);
        const isRoot = level === 0;

        return (
          <li
            key={id}
            className={
              (isRoot ? "rounded-lg border p-4" : "rounded-md border p-3") +
              " bg-background shadow-sm"
            }
          >
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <div className="flex-1 space-y-2">
                <Input value={currentName} onChange={(e) => onNameChange(id, e.target.value)} placeholder="菜单名称" />
                <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                  <span>编号：{id}</span>
                  {item.path ? <span>路径：{item.path}</span> : null}
                  <Badge variant={isDeleted ? "outline" : "success"}>{isDeleted ? "已禁用" : "已启用"}</Badge>
                </div>
              </div>
              <div className="flex flex-shrink-0 gap-2">
                <Button size="sm" onClick={() => onSave(id)} disabled={!changed || busy}>
                  保存
                </Button>
                <Button
                  size="sm"
                  variant={isDeleted ? "secondary" : "destructive"}
                  onClick={() => onToggle(id, isDeleted)}
                  disabled={busy}
                >
                  {isDeleted ? "启用" : "禁用"}
                </Button>
              </div>
            </div>
            {childMenus.length > 0 ? (
              <div className="mt-3 space-y-3 border-l pl-4">
                <MenuTree
                  items={childMenus}
                  nameDrafts={nameDrafts}
                  pending={pending}
                  onNameChange={onNameChange}
                  onSave={onSave}
                  onToggle={onToggle}
                  level={level + 1}
                />
              </div>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}
