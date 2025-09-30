import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import type { KeycloakUser } from "#/keycloak";
import { KeycloakGroupService, KeycloakUserService } from "@/api/services/keycloakService";
import { Icon } from "@/components/icon";
import { Alert, AlertDescription } from "@/ui/alert";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";

interface GroupMembersModalProps {
	open: boolean;
	groupId: string;
	groupName: string;
	onCancel: () => void;
	onSuccess: () => void;
}

interface UserTableRow extends KeycloakUser {
	key: string;
	isGroupMember: boolean;
}

export default function GroupMembersModal({ open, groupId, groupName, onCancel, onSuccess }: GroupMembersModalProps) {
	const [users, setUsers] = useState<UserTableRow[]>([]);
	const [loading, setLoading] = useState(false);
	const [searchValue, setSearchValue] = useState("");
	const [error, setError] = useState<string>("");

	// 加载用户和组成员信息
	const loadUsersAndMembers = useCallback(async () => {
		if (!groupId) return;

		setLoading(true);
		setError("");

		try {
			// 并行加载所有用户和组成员
			const [allUsers, groupMembers] = await Promise.all([
				KeycloakUserService.getAllUsers({ first: 0, max: 1000 }),
				KeycloakGroupService.getGroupMembers(groupId),
			]);

			// 标记哪些用户是组成员
			const usersWithMemberStatus: UserTableRow[] = allUsers
				.filter((user) => {
					if (!searchValue) return true;
					return (
						user.username.toLowerCase().includes(searchValue.toLowerCase()) ||
						(user.email || "").toLowerCase().includes(searchValue.toLowerCase()) ||
						(user.firstName || "").toLowerCase().includes(searchValue.toLowerCase()) ||
						(user.lastName || "").toLowerCase().includes(searchValue.toLowerCase())
					);
				})
				.map((user) => ({
					...user,
					key: user.id || user.username,
					isGroupMember: groupMembers.includes(user.id || ""),
				}));

			setUsers(usersWithMemberStatus);
		} catch (err: any) {
			console.error("Error loading users and members:", err);
			setError(err.message || "加载用户信息失败");
		} finally {
			setLoading(false);
		}
	}, [groupId, searchValue]);

	// 添加用户到组
	const handleAddUser = async (user: KeycloakUser) => {
		if (!user.id) return;

		try {
			await KeycloakGroupService.addUserToGroup(groupId, user.id);
			toast.success(`已将用户 ${user.username} 添加到组`);
			loadUsersAndMembers();
			onSuccess();
		} catch (error: any) {
			console.error("Error adding user to group:", error);
			toast.error(`添加用户失败: ${error.message || "未知错误"}`);
		}
	};

	// 从组中移除用户
	const handleRemoveUser = async (user: KeycloakUser) => {
		if (!user.id) return;

		try {
			await KeycloakGroupService.removeUserFromGroup(groupId, user.id);
			toast.success(`已将用户 ${user.username} 从组中移除`);
			loadUsersAndMembers();
			onSuccess();
		} catch (error: any) {
			console.error("Error removing user from group:", error);
			toast.error(`移除用户失败: ${error.message || "未知错误"}`);
		}
	};

	// 表格列定义
	const columns: ColumnsType<UserTableRow> = [
		{
			title: "用户信息",
			dataIndex: "username",
			width: 250,
			render: (_, record) => (
				<div className="flex items-center">
					<div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary font-medium">
						{record.username.charAt(0).toUpperCase()}
					</div>
					<div className="ml-3">
						<div className="font-medium">{record.username}</div>
						<div className="text-sm text-muted-foreground">{record.email}</div>
					</div>
				</div>
			),
		},
		{
			title: "姓名",
			dataIndex: "name",
			width: 150,
			render: (_, record) => {
				const fullName = [record.firstName, record.lastName].filter(Boolean).join(" ");
				return fullName || "-";
			},
		},
		{
			title: "状态",
			dataIndex: "enabled",
			align: "center",
			width: 100,
			render: (enabled: boolean) => (
				<Badge variant={enabled ? "success" : "destructive"}>{enabled ? "启用" : "禁用"}</Badge>
			),
		},
		{
			title: "成员状态",
			dataIndex: "isGroupMember",
			align: "center",
			width: 120,
			render: (isGroupMember: boolean) => (
				<Badge variant={isGroupMember ? "default" : "outline"}>{isGroupMember ? "组成员" : "非成员"}</Badge>
			),
		},
		{
			title: "操作",
			key: "operation",
			align: "center",
			width: 100,
			render: (_, record) => (
				<div className="flex items-center justify-center">
					{record.isGroupMember ? (
						<Button
							variant="ghost"
							size="sm"
							onClick={() => handleRemoveUser(record)}
							className="text-red-600 hover:text-red-700"
							title="移出组"
						>
							<Icon icon="mdi:account-minus" size={16} />
						</Button>
					) : (
						<Button
							variant="ghost"
							size="sm"
							onClick={() => handleAddUser(record)}
							className="text-green-600 hover:text-green-700"
							title="加入组"
						>
							<Icon icon="mdi:account-plus" size={16} />
						</Button>
					)}
				</div>
			),
		},
	];

	useEffect(() => {
		if (open && groupId) {
			loadUsersAndMembers();
		}
	}, [open, groupId, loadUsersAndMembers]);

	// 搜索变化时重新加载
	useEffect(() => {
		if (!open) return;

		const timeoutId = setTimeout(() => {
			loadUsersAndMembers();
		}, 300);
		return () => clearTimeout(timeoutId);
	}, [loadUsersAndMembers, open]);

	const memberCount = users.filter((user) => user.isGroupMember).length;
	const nonMemberCount = users.filter((user) => !user.isGroupMember).length;

	return (
		<Dialog open={open} onOpenChange={onCancel}>
			<DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
				<DialogHeader>
					<DialogTitle>组成员管理 - {groupName}</DialogTitle>
				</DialogHeader>

				<div className="space-y-4">
					{error && (
						<Alert variant="destructive">
							<AlertDescription>{error}</AlertDescription>
						</Alert>
					)}

					{/* 统计信息 */}
					<div className="flex items-center gap-4 p-4 bg-muted/50 rounded-lg">
						<div className="flex items-center gap-2">
							<Icon icon="mdi:account-group" size={20} />
							<span className="font-medium">成员统计：</span>
						</div>
						<Badge variant="default">{memberCount} 名成员</Badge>
						<Badge variant="outline">{nonMemberCount} 名非成员</Badge>
					</div>

					{/* 搜索栏 */}
					<div className="flex items-center gap-2">
						<Input
							placeholder="搜索用户名、邮箱或姓名..."
							value={searchValue}
							onChange={(e) => setSearchValue(e.target.value)}
							className="max-w-sm"
						/>
						<Button variant="outline" onClick={() => setSearchValue("")} disabled={!searchValue}>
							清除
						</Button>
					</div>

					{/* 用户表格 */}
					<Table
						rowKey="key"
						columns={columns}
						dataSource={users}
						loading={loading}
						scroll={{ x: 800, y: 400 }}
						pagination={{
							pageSize: 20,
							showSizeChanger: true,
							showQuickJumper: true,
							showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
						}}
					/>
				</div>

				<DialogFooter>
					<Button variant="outline" onClick={onCancel}>
						关闭
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
