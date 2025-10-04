import { useCallback, useEffect, useMemo, useState } from "react";
import type { ColumnsType } from "antd/es/table";
import { Table } from "antd";
import type { KeycloakUser } from "#/keycloak";
import { KeycloakUserService } from "@/api/services/keycloakService";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Text } from "@/ui/typography";
import { Icon } from "@/components/icon";
import UserModal from "./user-management.modal";
import { toast } from "sonner";
import { useRouter } from "@/routes/hooks";

function collectRoleNames(user: KeycloakUser): string[] {
  const names = new Set<string>();
  (user.realmRoles || []).forEach((r: string) => r && names.add(r));
  if (user.clientRoles) {
    (Object.values(user.clientRoles) as string[][]).forEach((arr: string[]) => arr?.forEach((r: string) => r && names.add(r)));
  }
  return Array.from(names);
}

export default function UserManagementView() {
  const { push } = useRouter();
  const [list, setList] = useState<KeycloakUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchValue, setSearchValue] = useState("");
  const [modalState, setModalState] = useState<{
    open: boolean;
    mode: "create" | "edit";
    target?: KeycloakUser;
  }>({ open: false, mode: "create" });
  // no external toggling action here; enable/停用统一放入编辑弹窗

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = searchValue.trim()
        ? await KeycloakUserService.searchUsers(searchValue.trim())
        : await KeycloakUserService.getAllUsers({ first: 0, max: 100 });
      setList(data || []);
    } catch (e: any) {
      toast.error(e?.message || "加载用户失败");
    } finally {
      setLoading(false);
    }
  }, [searchValue]);

  useEffect(() => {
    load();
  }, [load]);

  const toggleEnabled = useCallback(async () => {}, []);

  const columns: ColumnsType<KeycloakUser> = useMemo(
    () => [
      { title: "用户名", dataIndex: "username", key: "username", width: 180 },
      { title: "姓名", dataIndex: "fullName", key: "fullName", width: 180 },
      { title: "邮箱", dataIndex: "email", key: "email", width: 220 },
      {
        title: "角色",
        key: "roles",
        render: (_, record) => {
          const roles = collectRoleNames(record);
          return roles.length ? (
            <div className="flex flex-wrap gap-1">
              {roles.slice(0, 5).map((r) => (
                <Badge key={r} variant="outline">{r}</Badge>
              ))}
              {roles.length > 5 ? <Badge variant="secondary">+{roles.length - 5}</Badge> : null}
            </div>
          ) : (
            <span className="text-muted-foreground">--</span>
          );
        },
      },
      {
        title: "状态",
        dataIndex: "enabled",
        key: "enabled",
        width: 140,
        render: (val?: boolean) => (
          <div className="flex items-center gap-2">
            <span className={val ? "h-2 w-2 rounded-full bg-emerald-500" : "h-2 w-2 rounded-full bg-red-500"} />
            <span className={val ? "text-emerald-600" : "text-red-600"}>{val ? "已启用" : "已停用"}</span>
          </div>
        ),
      },
      {
        title: "操作",
        key: "actions",
        width: 180,
        fixed: "right" as const,
        render: (_, record) => (
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={() => setModalState({ open: true, mode: "edit", target: record })}
            >
              编辑
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                const id = record.id || record.username;
                if (!id) return;
                push(`/admin/users/${id}`);
              }}
            >
              详情
            </Button>
          </div>
        ),
      },
    ],
    [toggleEnabled],
  );

  return (
    <div className="mx-auto w-full max-w-[1400px] px-6 py-6 space-y-6">
      {/* 页面标题与操作区：老布局形态 */}
      <div className="flex flex-wrap items-center gap-3">
        <Text variant="body1" className="text-lg font-semibold">
          用户管理
        </Text>
        <div className="ml-auto flex items-center gap-2">
          <Input
            placeholder="按用户名搜索"
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") load();
            }}
            className="w-[240px]"
          />
          <Button variant="outline" onClick={load}>
            <Icon icon="solar:magnifer-linear" className="mr-1 h-4 w-4" />
            搜索
          </Button>
          <Button onClick={() => setModalState({ open: true, mode: "create" })}>
            <Icon icon="solar:add-circle-bold" className="mr-1 h-4 w-4" />
            新建用户
          </Button>
        </div>
      </div>

      {/* 列表区：保持老的“表格主体”布局，但采用新样式卡片包裹 */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle>用户列表</CardTitle>
        </CardHeader>
        <CardContent>
          <Table
            rowKey={(r) => String(r.id ?? r.username)}
            columns={columns}
            dataSource={list}
            loading={loading}
            pagination={{
              pageSize: 10,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50, 100],
              showQuickJumper: true,
              showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
            }}
            scroll={{ x: 1200 }}
          />
        </CardContent>
      </Card>

      {/* 创建/编辑弹窗：复用旧页面Modal但使用新样式组件 */}
      <UserModal
        open={modalState.open}
        mode={modalState.mode}
        user={modalState.target}
        onCancel={() => setModalState((s) => ({ ...s, open: false, target: undefined }))}
        onSuccess={() => {
          setModalState((s) => ({ ...s, open: false, target: undefined }));
          load();
        }}
      />
    </div>
  );
}
