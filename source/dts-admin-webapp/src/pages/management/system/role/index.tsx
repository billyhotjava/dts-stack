import { Modal, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import type { KeycloakRole, RoleTableRow } from "#/keycloak";
import { KeycloakRoleService } from "@/api/services/keycloakService";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader } from "@/ui/card";
import { Input } from "@/ui/input";
import RoleModal from "./role-modal";
import RoleAssignModal from "./role-assign-modal";
import zhCN from "@/locales/lang/zh_CN";

export default function RolePage() {
	const [roles, setRoles] = useState<RoleTableRow[]>([]);
	const [loading, setLoading] = useState(false);
	const [searchValue, setSearchValue] = useState("");

	// Modal状态
	const [roleModal, setRoleModal] = useState<{
		open: boolean;
		mode: "create" | "edit";
		role?: KeycloakRole;
	}>({ open: false, mode: "create" });

	const [assignModal, setAssignModal] = useState<{
		open: boolean;
		role?: KeycloakRole;
	}>({ open: false });

	// 加载角色列表
	const loadRoles = useCallback(async () => {
		setLoading(true);
		try {
			const rolesData = await KeycloakRoleService.getAllRealmRoles();

			const tableData: RoleTableRow[] = rolesData
				.filter((role) => !role.clientRole)
				.filter((role) => {
					if (!searchValue) return true;
					return (
						role.name.toLowerCase().includes(searchValue.toLowerCase()) ||
						(role.description || "").toLowerCase().includes(searchValue.toLowerCase())
					);
				})
				.map((role) => ({
					...role,
					key: role.id || role.name,
				}));

			setRoles(tableData);
		} catch (error: any) {
			console.error("Error loading roles:", error);
			toast.error(`加载角色列表失败: ${error.message || "未知错误"}`);
		} finally {
			setLoading(false);
		}
	}, [searchValue]);

	// 搜索角色
	const handleSearch = () => {
		loadRoles();
	};

	// 删除角色
	const handleDelete = (role: KeycloakRole) => {
		if (role.name && BUILT_IN_ROLE_NAMES.has(role.name.trim().toUpperCase())) {
			toast.warning("内置角色不可删除");
			return;
		}
		Modal.confirm({
			title: "确认删除",
			content: `确定要删除角色 "${role.name}" 吗？此操作无法撤销。`,
			okText: "删除",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				try {
					if (!role.name) throw new Error("角色名称不存在");
					await KeycloakRoleService.deleteRole(role.name);
					toast.success("角色删除成功");
					loadRoles();
				} catch (error: any) {
					console.error("Error deleting role:", error);
					toast.error(`删除角色失败: ${error.message || "未知错误"}`);
				}
			},
		});
	};

	// 表格列定义
	const columns: ColumnsType<RoleTableRow> = [
		{
			title: "角色编号",
			dataIndex: "name",
			width: 200,
			render: (name: string, record) => {
				const normalizedName = name.trim().toUpperCase();
				const builtIn = BUILT_IN_ROLE_NAMES.has(normalizedName);
				return (
					<div className="flex items-center">
						<div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary font-medium">
							{name.charAt(0).toUpperCase()}
						</div>
						<div className="ml-3 space-y-1">
							<div className="flex items-center gap-2">
								<span className="font-medium">{name}</span>
								{builtIn ? (
									<Badge variant="outline" className="text-xs">
										内置
									</Badge>
								) : null}
							</div>
							<div className="flex flex-wrap gap-1">
								{record.composite ? (
									<Badge variant="secondary" className="text-xs">
										复合角色
									</Badge>
								) : null}
							</div>
						</div>
					</div>
				);
			},
		},
		{
			title: "角色名称",
			dataIndex: "name",
			width: 200,
			render: (_name: string, record) => {
				const code = (record.name ?? "").trim().toUpperCase();
				const zhMap = (zhCN as any)?.sys?.admin?.role ?? {};
				const zhLabel: string | undefined = zhMap[code];
				return zhLabel || record.description || record.name || "-";
			},
		},
		{
			title: "描述",
			dataIndex: "description",
			render: (description: string) => description || "-",
		},
		// 按要求移除角色“数据密级”展示列
		{
			title: "角色编号",
			dataIndex: "id",
			width: 120,
			render: (id: string) => (
				<span className="font-mono text-xs text-muted-foreground">{id ? `${id.substring(0, 8)}...` : "-"}</span>
			),
		},
		{
			title: "操作",
			key: "operation",
			align: "center",
			width: 180,
			fixed: "right",
			render: (_, record) => {
				const name = record.name ?? "";
				const normalizedName = name.trim().toUpperCase();
				const builtIn = BUILT_IN_ROLE_NAMES.has(normalizedName);
				const onEdit = () => {
					if (builtIn) {
						toast.warning("内置角色不可编辑");
						return;
					}
					setRoleModal({ open: true, mode: "edit", role: record });
				};
				const onDelete = () => handleDelete(record);
				const onAssign = () => {
					if (builtIn) {
						toast.warning("内置角色由平台维护，不支持在线分配");
						return;
					}
					if (!record.name) {
						toast.error("角色信息缺失，无法分配");
						return;
					}
					setAssignModal({ open: true, role: record });
				};
				return (
					<div className="flex items-center justify-center gap-1">
						<Button
							variant="ghost"
							size="sm"
							title="分配用户"
							onClick={onAssign}
							disabled={builtIn}
							className={builtIn ? "opacity-50" : undefined}
						>
							<Icon icon="mdi:account-plus" size={16} />
						</Button>
						<Button variant="ghost" size="sm" title="编辑角色" onClick={onEdit} disabled={builtIn}>
							<Icon icon="solar:pen-bold-duotone" size={16} />
						</Button>
						<Button
							variant="ghost"
							size="sm"
							title="删除角色"
							onClick={onDelete}
							disabled={builtIn}
							className={`text-red-600 hover:text-red-700 ${builtIn ? "opacity-50" : ""}`}
						>
							<Icon icon="mingcute:delete-2-fill" size={16} />
						</Button>
					</div>
				);
			},
		},
	];

	// 初始化加载
	useEffect(() => {
		loadRoles();
	}, [loadRoles]);

	// 搜索变化时重新加载
	useEffect(() => {
		const timeoutId = setTimeout(() => {
			loadRoles();
		}, 300);
		return () => clearTimeout(timeoutId);
	}, [loadRoles]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<div className="flex items-center justify-between">
						<div>
							<h2 className="text-2xl font-bold">角色管理</h2>
							<p className="text-muted-foreground">管理Keycloak角色权限</p>
						</div>
						<Button onClick={() => setRoleModal({ open: true, mode: "create" })}>
							<Icon icon="mdi:plus" size={16} className="mr-2" />
							新建角色
						</Button>
					</div>
				</CardHeader>
				<CardContent>
					{/* 搜索栏 */}
					<div className="flex items-center gap-2 mb-4">
						<Input
							placeholder="搜索角色编号或描述..."
							value={searchValue}
							onChange={(e) => setSearchValue(e.target.value)}
							className="max-w-sm"
						/>
						<Button onClick={handleSearch}>
							<Icon icon="mdi:magnify" size={16} className="mr-2" />
							搜索
						</Button>
						{searchValue && (
							<Button variant="outline" onClick={() => setSearchValue("")}>
								清除
							</Button>
						)}
					</div>

					{/* 角色表格 */}
					<Table
						rowKey="key"
						columns={columns}
						dataSource={roles}
						loading={loading}
						scroll={{ x: 800 }}
						pagination={{
							pageSize: 10,
							showSizeChanger: true,
							showQuickJumper: true,
							showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
						}}
					/>
				</CardContent>
			</Card>

			{/* 角色创建/编辑Modal */}
			<RoleModal
				open={roleModal.open}
				mode={roleModal.mode}
				role={roleModal.role}
				onCancel={() => setRoleModal({ open: false, mode: "create" })}
				onSuccess={() => {
					setRoleModal({ open: false, mode: "create" });
					loadRoles();
				}}
			/>

			<RoleAssignModal
				open={assignModal.open}
				role={assignModal.role}
				onCancel={() => setAssignModal({ open: false })}
				onSuccess={() => {
					setAssignModal({ open: false });
					loadRoles();
				}}
			/>
		</div>
	);
}
const BUILT_IN_ROLE_NAMES = new Set(["SYSADMIN", "OPADMIN", "AUTHADMIN", "AUDITADMIN"]);
