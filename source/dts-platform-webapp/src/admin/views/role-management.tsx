import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { adminApi } from "@/admin/api/adminApi";
import type { AdminRoleDetail, PermissionCatalogSection } from "@/admin/types";
import { ChangeRequestForm } from "@/admin/components/change-request-form";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";

export default function RoleManagementView() {
	const { data: roles = [] } = useQuery<AdminRoleDetail[]>({
		queryKey: ["admin", "roles"],
		queryFn: adminApi.getAdminRoles,
	});
	const { data: catalog = [] } = useQuery<PermissionCatalogSection[]>({
		queryKey: ["admin", "permission-catalog"],
		queryFn: adminApi.getPermissionCatalog,
	});

	const [selectedId, setSelectedId] = useState<number | null>(null);

	useEffect(() => {
		if (roles.length > 0 && !selectedId) {
			setSelectedId(roles[0].id);
		}
	}, [roles, selectedId]);

	const selected = useMemo(() => roles.find((role) => role.id === selectedId) ?? null, [roles, selectedId]);

	return (
		<div className="grid gap-6 xl:grid-cols-[minmax(0,0.55fr)_minmax(0,1fr)]">
			<Card>
				<CardHeader>
					<CardTitle>角色列表</CardTitle>
				</CardHeader>
				<CardContent className="space-y-3">
					{roles.length === 0 ? <Text variant="body3">暂无角色数据。</Text> : null}
					<ul className="space-y-2">
						{roles.map((role) => {
							const isActive = role.id === selectedId;
							return (
								<li key={role.id}>
									<button
										type="button"
										onClick={() => setSelectedId(role.id)}
										className={`w-full rounded-md border px-4 py-3 text-left transition ${
											isActive ? "border-primary bg-primary/10" : "hover:border-primary/40"
										}`}
									>
										<div className="flex items-center justify-between gap-2">
											<span className="font-medium">{role.name}</span>
											<Badge variant="outline">{role.securityLevel}</Badge>
										</div>
										<Text variant="body3" className="text-muted-foreground">
											{role.description || "--"}
										</Text>
										<div className="mt-1 flex flex-wrap gap-1">
											<Badge variant="secondary">成员：{role.memberCount}</Badge>
											<Badge variant="secondary">审批链：{role.approvalFlow}</Badge>
										</div>
									</button>
								</li>
							);
						})}
					</ul>
				</CardContent>
			</Card>

			<div className="space-y-6">
				<Card>
					<CardHeader>
						<CardTitle>角色详情</CardTitle>
					</CardHeader>
					<CardContent className="space-y-4 text-sm">
						{selected ? (
							<>
								<div>
									<Text variant="body2" className="font-semibold">
										{selected.name}
									</Text>
									<Text variant="body3" className="text-muted-foreground">
										安全级别：{selected.securityLevel}
									</Text>
								</div>
								{selected.description ? <p className="text-muted-foreground">{selected.description}</p> : null}
								<div className="space-y-2">
									<Text variant="body3" className="font-semibold">
										拥有权限
									</Text>
									<div className="flex flex-wrap gap-2">
										{selected.permissions.map((code) => (
											<Badge key={code} variant="outline">
												{code}
											</Badge>
										))}
									</div>
								</div>
								<div className="space-y-2">
									<Text variant="body3" className="font-semibold">
										最新变更
									</Text>
									<p className="text-muted-foreground">{selected.updatedAt}</p>
								</div>
							</>
						) : (
							<Text variant="body3" className="text-muted-foreground">
								请选择左侧角色查看详情。
							</Text>
						)}
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle>权限矩阵</CardTitle>
					</CardHeader>
					<CardContent className="space-y-4 text-sm">
						{catalog.length === 0 ? <Text variant="body3">暂无权限定义。</Text> : null}
						{catalog.map((section) => (
							<div key={section.category} className="rounded-lg border p-3">
								<div className="mb-2 flex items-center justify-between">
									<Text variant="body3" className="font-semibold">
										{section.category}
									</Text>
									<Text variant="body3" className="text-muted-foreground">
										{section.description || ""}
									</Text>
								</div>
								<ul className="space-y-1">
									{section.permissions.map((permission) => {
										const attached = selected?.permissions.includes(permission.code);
										return (
											<li key={permission.code} className="flex items-center justify-between gap-3 rounded-md px-2 py-1">
												<span>
													{permission.name}
													<Text variant="body3" className="text-muted-foreground">
														{permission.code}
													</Text>
												</span>
												<div className="flex items-center gap-2">
													<Badge variant={permission.securityLevel ? "outline" : "secondary"}>
														{permission.securityLevel || "公共"}
													</Badge>
													{attached ? <Badge variant="secondary">已赋予</Badge> : null}
												</div>
											</li>
										);
									})}
								</ul>
							</div>
						))}
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle>发起角色变更</CardTitle>
					</CardHeader>
					<CardContent>
						<ChangeRequestForm initialTab="role" />
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
