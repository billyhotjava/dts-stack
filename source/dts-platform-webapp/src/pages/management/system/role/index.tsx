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

	// 加载角色列表
	const loadRoles = useCallback(async () => {
		setLoading(true);
		try {
			const rolesData = await KeycloakRoleService.getAllRealmRoles();

			const tableData: RoleTableRow[] = rolesData
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
			title: "角色名称",
			dataIndex: "name",
			width: 200,
			render: (name: string, record) => (
				<div className="flex items-center">
					<div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary font-medium">
						{name.charAt(0).toUpperCase()}
					</div>
					<div className="ml-3">
						<div className="font-medium">{name}</div>
						{record.composite && (
							<Badge variant="secondary" className="text-xs">
								复合角色
							</Badge>
						)}
					</div>
				</div>
			),
		},
		{
			title: "描述",
			dataIndex: "description",
			render: (description: string) => description || "-",
		},
		{
			title: "类型",
			dataIndex: "clientRole",
			align: "center",
			width: 120,
			render: (clientRole: boolean) => (
				<Badge variant={clientRole ? "secondary" : "default"}>{clientRole ? "客户端角色" : "Realm角色"}</Badge>
			),
		},
		{
			title: "角色ID",
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
			width: 120,
			fixed: "right",
			render: (_, record) => (
				<div className="flex items-center justify-center gap-1">
					<Button
						variant="ghost"
						size="sm"
						title="编辑角色"
						onClick={() => setRoleModal({ open: true, mode: "edit", role: record })}
					>
						<Icon icon="solar:pen-bold-duotone" size={16} />
					</Button>
					<Button
						variant="ghost"
						size="sm"
						title="删除角色"
						onClick={() => handleDelete(record)}
						className="text-red-600 hover:text-red-700"
					>
						<Icon icon="mingcute:delete-2-fill" size={16} />
					</Button>
				</div>
			),
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
							placeholder="搜索角色名称或描述..."
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
		</div>
	);
}
