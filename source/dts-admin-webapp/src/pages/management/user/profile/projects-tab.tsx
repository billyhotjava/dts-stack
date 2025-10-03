import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { KeycloakUserService } from "@/api/services/keycloakService";
import { useUserInfo } from "@/store/userStore";
import { Alert, AlertDescription, AlertTitle } from "@/ui/alert";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Skeleton } from "@/ui/skeleton";
import { Text } from "@/ui/typography";
import type { KeycloakRole } from "#/keycloak";

export default function ProjectsTab() {
	const { id: userId, username } = useUserInfo();

	const { data, isLoading, isError, error } = useQuery({
		queryKey: ["keycloak", "user-roles", userId],
		queryFn: async () => {
			if (!userId) {
				return [] as KeycloakRole[];
			}
			const roles = await KeycloakUserService.getUserRoles(userId);
			return roles;
		},
		enabled: Boolean(userId),
	});

	const flattenedRoles = useMemo(() => {
		if (!data) return [] as KeycloakRole[];
		return data;
	}, [data]);

	if (!userId) {
		return (
			<Alert>
				<AlertTitle>无法识别当前用户</AlertTitle>
				<AlertDescription>请登录后再查看角色信息。</AlertDescription>
			</Alert>
		);
	}

	if (isLoading) {
		return (
			<div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
				{Array.from({ length: 4 }).map((_, index) => (
					<Card key={index}>
						<CardHeader className="space-y-2">
							<Skeleton className="h-5 w-40" />
							<Skeleton className="h-4 w-64" />
						</CardHeader>
						<CardContent className="space-y-2">
							<Skeleton className="h-3 w-32" />
							<Skeleton className="h-3 w-24" />
							<Skeleton className="h-3 w-20" />
						</CardContent>
					</Card>
				))}
			</div>
		);
	}

	if (isError) {
		return (
			<Alert variant="destructive">
				<AlertTitle>加载角色失败</AlertTitle>
				<AlertDescription>{error instanceof Error ? error.message : "请稍后重试。"}</AlertDescription>
			</Alert>
		);
	}

	if (!flattenedRoles.length) {
		return (
			<Alert>
				<AlertTitle>没有关联的角色</AlertTitle>
				<AlertDescription>
					{username ? `${username} 当前未关联任何角色。` : "当前用户未关联任何角色。"}
				</AlertDescription>
			</Alert>
		);
	}
	return (
		<div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
			{flattenedRoles.map((role) => (
				<Card key={role.id || role.name}>
					<CardHeader>
						<CardTitle className="flex items-center gap-2">
							<Text variant="body1" className="font-semibold">
								{role.name}
							</Text>
							{role.composite ? <Badge variant="outline">复合角色</Badge> : null}
						</CardTitle>
					</CardHeader>
					<CardContent className="space-y-2">
						<Text variant="body2" className="text-muted-foreground">
							{role.description || "该角色暂无描述。"}
						</Text>
						<div className="flex flex-wrap gap-2">
							{role.attributes
								? Object.entries(role.attributes).map(([key, value]) => (
									<Badge key={key} variant="outline">
										{key}: {Array.isArray(value) ? value.join("、") : String(value)}
									</Badge>
								))
								: null}
						</div>
					</CardContent>
				</Card>
			))}
		</div>
	);
}
