import type { CustomUserAttributeKey } from "@/constants/user";
import { CUSTOM_USER_ATTRIBUTE_KEYS } from "@/constants/user";
import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import type { KeycloakGroup, KeycloakRole, KeycloakUser, UserProfileConfig } from "#/服务端";
import { KeycloakGroupService, KeycloakUserProfileService, KeycloakUserService } from "@/api/services/keycloakService";
import { Icon } from "@/components/icon";
import { useParams, useRouter } from "@/routes/hooks";
import { Alert, AlertDescription } from "@/ui/alert";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { getAttributeDisplayName } from "@/utils/translation";
// import ResetPasswordModal from "./reset-password-modal";
import UserModal from "./user-management.modal";
import { ApprovalStatus } from "./user-approval-status";

const RESERVED_PROFILE_ATTRIBUTES = new Set<string>([
  "username",
  "email",
  "firstName",
  "lastName",
  "locale",
  "fullname",
  ...CUSTOM_USER_ATTRIBUTE_KEYS,
]);

const getSingleAttributeValue = (attributes: Record<string, string[]> | undefined, key: CustomUserAttributeKey) => {
  const values = attributes?.[key];
  if (!values || values.length === 0) {
    return "";
  }
  const nonEmpty = values.find((item) => item && item.trim());
  return nonEmpty ?? values[0] ?? "";
};

export default function UserDetailView() {
  const { id } = useParams();
  const { back } = useRouter();

  const [user, setUser] = useState<KeycloakUser | null>(null);
  const [userRoles, setUserRoles] = useState<KeycloakRole[]>([]);
  const [userGroups, setUserGroups] = useState<KeycloakGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>("");

  // UserProfile相关状态
  const [userProfileConfig, setUserProfileConfig] = useState<UserProfileConfig | null>(null);
  const [_profileLoading, setProfileLoading] = useState(false);
  const [_profileError, setProfileError] = useState<string>("");

  // Modal状态
  const [editModal, setEditModal] = useState(false);
  // const [resetPasswordModal, setResetPasswordModal] = useState(false);

  // 加载UserProfile配置
  const loadUserProfileConfig = useCallback(async () => {
    setProfileLoading(true);
    setProfileError("");
    try {
      const config = await KeycloakUserProfileService.getUserProfileConfig();
      setUserProfileConfig(config);
    } catch (err) {
      console.error("Error loading user profile config:", err);
      setProfileError("加载用户配置文件失败");
    } finally {
      setProfileLoading(false);
    }
  }, []);

  // 加载用户详情
  const loadUserDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError("");
    try {
      const [userData, rolesData, groupsData] = await Promise.all([
        KeycloakUserService.getUserById(id),
        KeycloakUserService.getUserRoles(id),
        KeycloakGroupService.getUserGroups(id),
      ]);
      setUser(userData);
      setUserRoles(rolesData);
      setUserGroups(groupsData);
    } catch (err: any) {
      console.error("Error loading user detail:", err);
      setError(err.message || "加载用户信息失败");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadUserDetail();
    loadUserProfileConfig();
  }, [loadUserDetail, loadUserProfileConfig]);

  // 角色表格列定义
  const roleColumns: ColumnsType<KeycloakRole> = [
    { title: "角色名称", dataIndex: "name", key: "name" },
    { title: "描述", dataIndex: "description", key: "description", render: (desc: string) => desc || "-" },
    {
      title: "类型",
      dataIndex: "clientRole",
      key: "clientRole",
      align: "center",
      render: (clientRole: boolean) => <Badge variant={clientRole ? "secondary" : "default"}>{clientRole ? "客户端角色" : "Realm角色"}</Badge>,
    },
  ];

  // 组表格列定义
  const groupColumns: ColumnsType<KeycloakGroup> = [
    { title: "组名称", dataIndex: "name", key: "name" },
    { title: "路径", dataIndex: "path", key: "path" },
  ];

  const personnelSecurityLevel = getSingleAttributeValue(user?.attributes, "person_level");
  const department = getSingleAttributeValue(user?.attributes, "department");
  const position = getSingleAttributeValue(user?.attributes, "position");
  const fullName = user?.firstName || user?.lastName || user?.attributes?.fullname?.[0] || "";
  const email = user?.email || "";

  const getFilteredUserAttributes = () => {
    const attrs = user?.attributes || {};
    const entries = Object.entries(attrs).filter(([k]) => !RESERVED_PROFILE_ATTRIBUTES.has(k));
    return Object.fromEntries(entries) as Record<string, string[]>;
  };

  const getTranslatedAttributeDisplayName = (name: string) => getAttributeDisplayName(name) || name;

  if (loading) {
    return (
      <div className="p-4">
        <Alert variant="default">
          <AlertDescription>加载中...</AlertDescription>
        </Alert>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-4">
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      </div>
    );
  }

  if (!user) return null;

  return (
    <div className="mx-auto w-full max-w-[1200px] px-6 py-6 space-y-6">
      <div className="flex items-center gap-3">
        <Button variant="outline" onClick={back}>
          <Icon icon="mdi:arrow-left" className="mr-1" /> 返回
        </Button>
        <h1 className="text-xl font-semibold">用户详情</h1>
        <div className="ml-auto flex items-center gap-2">
          {/* <Button variant="outline" onClick={() => setResetPasswordModal(true)}>
            <Icon icon="solar:lock-password-unlocked-broken" className="mr-1" /> 重置密码
          </Button> */}
          <Button onClick={() => setEditModal(true)}>
            <Icon icon="solar:pen-new-square-broken" className="mr-1" /> 编辑
          </Button>
        </div>
      </div>

      {/* 基本信息 */}
      <Card>
        <CardHeader>
          <CardTitle>基本信息</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            <div>
              <span className="text-sm font-medium text-muted-foreground">用户名</span>
              <p className="mt-1 text-sm">{user.username || "-"}</p>
            </div>
            <div>
              <span className="text-sm font-medium text-muted-foreground">姓名</span>
              <p className={`mt-1 text-sm ${fullName ? "" : "text-muted-foreground"}`}>{fullName || "-"}</p>
            </div>
            <div>
              <span className="text-sm font-medium text-muted-foreground">邮箱</span>
              <p className={`mt-1 text-sm ${email ? "" : "text-muted-foreground"}`}>{email || "-"}</p>
            </div>
            <div>
              <span className="text-sm font-medium text-muted-foreground">状态</span>
              <div className="mt-1">
                <Badge variant={user.enabled ? "success" : "destructive"}>{user.enabled ? "启用" : "禁用"}</Badge>
              </div>
            </div>
            <div>
              <span className="text-sm font-medium text-muted-foreground">审批状态</span>
              <div className="mt-1">
                <ApprovalStatus userId={user.id || ""} />
              </div>
            </div>
            <div>
              <span className="text-sm font-medium text-muted-foreground">人员密级</span>
              <div className="mt-1">{personnelSecurityLevel ? <Badge variant="outline">{personnelSecurityLevel}</Badge> : <span className="text-muted-foreground text-sm">-</span>}</div>
            </div>
            <div>
              <span className="text-sm font-medium text-muted-foreground">部门</span>
              <p className={`mt-1 text-sm ${department ? "" : "text-muted-foreground"}`}>{department || "-"}</p>
            </div>
            <div>
              <span className="text-sm font-medium text-muted-foreground">职位</span>
              <p className={`mt-1 text-sm ${position ? "" : "text-muted-foreground"}`}>{position || "-"}</p>
            </div>

            {Object.entries(getFilteredUserAttributes()).map(([name, values]) => (
              <div key={name}>
                <span className="text-sm font-medium text-muted-foreground">{getTranslatedAttributeDisplayName(name)}</span>
                <div className="mt-1">
                  {values.length > 0 ? (
                    values.map((value) => (
                      <Badge key={value} variant="outline" className="mr-1 mb-1">
                        {value}
                      </Badge>
                    ))
                  ) : (
                    <span className="text-muted-foreground text-sm">-</span>
                  )}
                </div>
              </div>
            ))}

            {user.createdTimestamp && (
              <div>
                <span className="text-sm font-medium text-muted-foreground">创建时间</span>
                <p className="mt-1 text-sm">{new Date(user.createdTimestamp).toLocaleString("zh-CN")}</p>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {/* 用户角色 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>用户角色 ({userRoles.length})</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {userRoles.length > 0 ? <Table rowKey="id" columns={roleColumns} dataSource={userRoles} pagination={false} size="small" /> : (
            <div className="text-center py-8 text-muted-foreground">
              <Icon icon="mdi:account-group-outline" size={48} className="mx-auto mb-2 opacity-50" />
              <p>暂无分配角色</p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* 用户组（可隐藏） */}
      <Card className="hidden">
        <CardHeader>
          <CardTitle>所属组 ({userGroups.length})</CardTitle>
        </CardHeader>
        <CardContent>
          {userGroups.length > 0 ? <Table rowKey="id" columns={groupColumns} dataSource={userGroups} pagination={false} size="small" /> : (
            <div className="text-center py-8 text-muted-foreground">
              <Icon icon="mdi:account-group-outline" size={48} className="mx-auto mb-2 opacity-50" />
              <p>暂无所属组</p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* 编辑用户Modal */}
      <UserModal
        open={editModal}
        mode="edit"
        user={user}
        onCancel={() => setEditModal(false)}
        onSuccess={() => {
          setEditModal(false);
          loadUserDetail();
        }}
      />

      {/* 重置密码Modal */}
      {/* <ResetPasswordModal
        open={resetPasswordModal}
        userId={user?.id || ""}
        username={user?.username || ""}
        onCancel={() => setResetPasswordModal(false)}
        onSuccess={() => {}}
      /> */}
    </div>
  );
}
