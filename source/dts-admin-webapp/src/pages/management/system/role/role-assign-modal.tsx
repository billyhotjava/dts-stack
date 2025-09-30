import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import type { KeycloakRole, KeycloakUser } from "#/keycloak";
import { KeycloakUserService } from "@/api/services/keycloakService";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Text } from "@/ui/typography";

const LOCKED_ADMIN_ROLES = new Set(["SYSADMIN", "OPADMIN", "AUTHADMIN", "AUDITADMIN"]);

interface RoleAssignModalProps {
	open: boolean;
	role?: KeycloakRole;
	onCancel: () => void;
	onSuccess: () => void;
}

export default function RoleAssignModal({ open, role, onCancel, onSuccess }: RoleAssignModalProps) {
	const [users, setUsers] = useState<KeycloakUser[]>([]);
	const [loading, setLoading] = useState(false);
	const [submitting, setSubmitting] = useState(false);
	const [search, setSearch] = useState("");
	const [selectedUserId, setSelectedUserId] = useState<string>("");

	const normalizedRoleName = role?.name?.trim().toUpperCase() ?? "";
	const lockedAdminRole = LOCKED_ADMIN_ROLES.has(normalizedRoleName);

	const loadUsers = useCallback(async () => {
		setLoading(true);
		try {
			const list = await KeycloakUserService.getAllUsers({ max: 200 });
			setUsers(list);
		} catch (error: any) {
			console.error("Error loading users for role assignment:", error);
			toast.error(`加载用户列表失败: ${error?.message || "未知错误"}`);
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		if (!open) {
			return;
		}
		if (lockedAdminRole) {
			toast.warning("内置管理员角色由平台维护，不支持在线分配成员");
			onCancel();
			return;
		}
		setSearch("");
		setSelectedUserId("");
		void loadUsers();
	}, [loadUsers, lockedAdminRole, onCancel, open]);

	const filteredUsers = useMemo(() => {
		if (!search.trim()) {
			return users;
		}
		const keyword = search.trim().toLowerCase();
		return users.filter((user) => {
			return [user.username, user.firstName, user.lastName, user.email]
				.filter(Boolean)
				.some((field) => field?.toLowerCase().includes(keyword));
		});
	}, [search, users]);

	const selectedUser = useMemo(() => {
		if (!selectedUserId) return null;
		return users.find((user) => user.id === selectedUserId || user.username === selectedUserId) ?? null;
	}, [selectedUserId, users]);

	const handleAssign = async () => {
		if (!role?.name) {
			toast.error("角色信息缺失，无法分配");
			return;
		}
		if (lockedAdminRole) {
			toast.warning("内置管理员角色不支持在线分配");
			return;
		}
		if (!selectedUser) {
			toast.error("请选择需要分配的用户");
			return;
		}
		setSubmitting(true);
		try {
			await KeycloakUserService.assignRolesToUser(selectedUser.id ?? selectedUser.username, [
				{ id: role.id, name: role.name },
			]);
			toast.success(`已为 ${selectedUser.username} 分配角色 ${role.name}`);
			onSuccess();
		} catch (error: any) {
			console.error("Error assigning role:", error);
			toast.error(`角色分配失败: ${error?.message || "未知错误"}`);
		} finally {
			setSubmitting(false);
		}
	};

	const handleOpenChange = (value: boolean) => {
		if (!value && !submitting) {
			onCancel();
		}
	};

	if (lockedAdminRole) {
		return null;
	}

	return (
		<Dialog open={open} onOpenChange={handleOpenChange}>
			<DialogContent className="max-w-xl">
				<DialogHeader>
					<DialogTitle>角色分配</DialogTitle>
				</DialogHeader>
				<div className="space-y-4">
					<div className="space-y-1">
						<Label>待分配角色</Label>
						<div className="rounded-md border border-dashed border-muted-foreground/30 bg-muted/30 px-3 py-2 text-sm">
							{role?.name ?? "未选择角色"}
						</div>
					</div>
					<div className="space-y-2">
						<Label htmlFor="role-assign-search">搜索用户</Label>
						<Input
							id="role-assign-search"
							placeholder="输入用户名/姓名/邮箱"
							value={search}
							onChange={(event) => setSearch(event.target.value)}
							disabled={loading}
						/>
					</div>
					<div className="space-y-2">
						<Label>选择用户</Label>
						<div className="rounded-md border">
							<ScrollArea className="h-60">
								{loading ? (
									<div className="flex h-full items-center justify-center text-sm text-muted-foreground">加载中...</div>
								) : filteredUsers.length === 0 ? (
									<div className="p-4 text-sm text-muted-foreground">未找到匹配的用户。</div>
								) : (
									<ul className="divide-y">
										{filteredUsers.map((user) => {
											const key = user.id ?? user.username;
											const isActive = selectedUserId === (user.id ?? user.username);
											return (
												<li key={key}>
													<button
														type="button"
														onClick={() => setSelectedUserId(user.id ?? user.username)}
														className={`flex w-full items-start justify-between gap-4 px-4 py-3 text-left text-sm transition ${
															isActive ? "bg-primary/10" : "hover:bg-muted"
														}`}
													>
														<div className="flex-1 space-y-1">
															<div className="font-medium">{user.username}</div>
															<Text variant="body3" className="text-muted-foreground">
																{[user.firstName, user.lastName].filter(Boolean).join(" ") || "--"}
															</Text>
															<Text variant="body3" className="text-muted-foreground">
																{user.email || "未填写邮箱"}
															</Text>
															{user.realmRoles && user.realmRoles.length > 0 ? (
																<div className="flex flex-wrap gap-1 pt-1">
																	{user.realmRoles.map((roleName) => (
																		<Badge key={roleName} variant="outline">
																			{roleName}
																		</Badge>
																	))}
																</div>
															) : null}
														</div>
														<div className="pt-1 text-xs text-muted-foreground">{isActive ? "已选择" : "点击选择"}</div>
													</button>
												</li>
											);
										})}
									</ul>
								)}
							</ScrollArea>
						</div>
					</div>
				</div>
				<DialogFooter>
					<Button variant="outline" onClick={onCancel} disabled={submitting}>
						取消
					</Button>
					<Button onClick={handleAssign} disabled={!selectedUser || submitting}>
						{submitting ? "分配中..." : "确认分配"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
