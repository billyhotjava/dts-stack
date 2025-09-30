import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { AdminUser } from "@/admin/types";
import { useAdminLocale } from "@/admin/lib/locale";
import { ChangeRequestForm } from "@/admin/components/change-request-form";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { ScrollArea } from "@/ui/scroll-area";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

const STATUS_COLORS: Record<string, "default" | "secondary" | "outline" | "destructive"> = {
	ACTIVE: "secondary",
	PENDING: "outline",
	DISABLED: "destructive",
};

export default function UserManagementView() {
	const { data: users = [], isLoading } = useQuery({
		queryKey: ["admin", "users"],
		queryFn: adminApi.getAdminUsers,
	});
	const { translateRole, translateStatus } = useAdminLocale();
	const [keyword, setKeyword] = useState("");
	const [roleFilter, setRoleFilter] = useState("");
	const [statusFilter, setStatusFilter] = useState("");

	const filtered = useMemo(() => {
		return users.filter((user) => {
			const matchKeyword = keyword
				? [user.username, user.displayName, user.email, user.orgPath?.join("/")]
					.filter(Boolean)
					.some((field) => field?.toLowerCase().includes(keyword.toLowerCase()))
				: true;
			const matchRole = roleFilter ? user.roles.includes(roleFilter) : true;
			const matchStatus = statusFilter ? user.status === statusFilter : true;
			return matchKeyword && matchRole && matchStatus;
		});
	}, [keyword, roleFilter, statusFilter, users]);

	const roleOptions = useMemo(() => {
		const set = new Set<string>();
		for (const user of users) {
			user.roles.forEach((role) => set.add(role));
		}
		return Array.from(set);
	}, [users]);

	const notifyChangeRequest = (action: string, user: AdminUser) => {
		toast.info("请在右侧表单补充详情后提交", {
			description: `${action} · ${user.username}`,
			position: "top-center",
		});
	};

	return (
		<div className="grid gap-6 xl:grid-cols-[minmax(0,0.65fr)_minmax(0,1fr)]">
			<Card>
				<CardHeader className="space-y-3">
					<CardTitle>用户总览</CardTitle>
					<div className="grid gap-3 md:grid-cols-2">
						<Input placeholder="搜索用户名/姓名/邮箱" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
						<div className="flex gap-2">
							<select
								className="h-10 flex-1 rounded-md border border-border bg-background px-3 text-sm"
								value={roleFilter}
								onChange={(event) => setRoleFilter(event.target.value)}
							>
								<option value="">全部角色</option>
								{roleOptions.map((role) => (
									<option key={role} value={role}>
										{translateRole(role, role)}
									</option>
								))}
							</select>
							<select
								className="h-10 flex-1 rounded-md border border-border bg-background px-3 text-sm"
								value={statusFilter}
								onChange={(event) => setStatusFilter(event.target.value)}
							>
								<option value="">全部状态</option>
								<option value="ACTIVE">已启用</option>
								<option value="PENDING">待审核</option>
								<option value="DISABLED">已禁用</option>
							</select>
						</div>
					</div>
					<div className="flex flex-wrap gap-3 text-sm text-muted-foreground">
						<span>总用户：{users.length}</span>
						<span>筛选后：{filtered.length}</span>
						<span>待审批：{users.filter((user) => user.status === "PENDING").length}</span>
					</div>
				</CardHeader>
				<CardContent className="h-[560px] p-0">
					{isLoading ? (
						<Text variant="body3" className="p-4">
							加载中...
						</Text>
					) : (
						<ScrollArea className="h-full">
							<table className="min-w-full table-fixed text-sm">
								<thead className="sticky top-0 z-10 bg-muted/70 backdrop-blur">
									<tr className="text-left">
										<th className="px-4 py-3 font-medium">用户</th>
										<th className="px-4 py-3 font-medium">角色</th>
										<th className="px-4 py-3 font-medium">组织</th>
										<th className="px-4 py-3 font-medium">安全级别</th>
										<th className="px-4 py-3 font-medium">状态</th>
										<th className="px-4 py-3 font-medium">最近登录</th>
									</tr>
								</thead>
								<tbody>
									{filtered.map((user) => (
										<tr key={user.id} className="border-b last:border-b-0">
											<td className="px-4 py-3">
												<div className="flex flex-col">
													<span className="font-medium">{user.displayName || user.username}</span>
													<Text variant="body3" className="text-muted-foreground">
														{user.email}
													</Text>
												</div>
											</td>
											<td className="px-4 py-3">
												<div className="flex flex-wrap gap-1">
													{user.roles.map((role) => (
														<Badge key={role} variant="outline">
															{translateRole(role, role)}
														</Badge>
													))}
												</div>
											</td>
											<td className="px-4 py-3">
												<Text variant="body3" className="text-muted-foreground">
													{user.orgPath?.join(" / ") || "--"}
												</Text>
											</td>
											<td className="px-4 py-3">
												<Badge variant="outline">{user.securityLevel}</Badge>
											</td>
											<td className="px-4 py-3">
												<Badge variant={STATUS_COLORS[user.status] ?? "default"}>
													{translateStatus(user.status, statusText(user.status))}
												</Badge>
											</td>
											<td className="px-4 py-3">
												<Text variant="body3" className="text-muted-foreground">
													{user.lastLoginAt || "--"}
												</Text>
												<div className="mt-2 flex gap-2">
													<Button
														variant="outline"
														size="sm"
														onClick={() => notifyChangeRequest("BIND_ROLE", user)}
													>
														角色调整
													</Button>
													<Button
														variant="ghost"
														size="sm"
														onClick={() => notifyChangeRequest("DISABLE", user)}
													>
														停用申请
													</Button>
												</div>
											</td>
										</tr>
									))}
								</tbody>
							</table>
						</ScrollArea>
					)}
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>发起用户变更</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<Text variant="body3" className="text-muted-foreground">
						可通过表单提交新增/修改/禁用用户、角色绑定等请求。
					</Text>
					<ChangeRequestForm initialTab="user" />
				</CardContent>
			</Card>
		</div>
	);
}

function statusText(status: string) {
	switch (status) {
		case "ACTIVE":
			return "已启用";
		case "PENDING":
			return "待审批";
		case "DISABLED":
			return "已禁用";
		default:
			return status;
	}
}
