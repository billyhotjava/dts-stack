import { useQuery } from "@tanstack/react-query";
import { KeycloakGroupService } from "@/api/services/keycloakService";
import { Icon } from "@/components/icon";
import { useUserInfo } from "@/store/userStore";
import { Alert, AlertDescription, AlertTitle } from "@/ui/alert";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Skeleton } from "@/ui/skeleton";
import { Text } from "@/ui/typography";

export default function TeamsTab() {
	const { id: userId, username } = useUserInfo();

	const { data, isLoading, isError, error } = useQuery({
		queryKey: ["keycloak", "user-groups", userId],
		queryFn: async () => {
			if (!userId) {
				return [];
			}
			const groups = await KeycloakGroupService.getUserGroups(userId);
			return groups;
		},
		enabled: Boolean(userId),
	});

	if (!userId) {
		return (
			<Alert>
				<AlertTitle>无法识别当前用户</AlertTitle>
				<AlertDescription>请重新登录后再查看团队信息。</AlertDescription>
			</Alert>
		);
	}

	if (isLoading) {
		return (
			<div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
				{Array.from({ length: 4 }).map((_, index) => (
					<Card key={index}>
						<CardHeader>
							<Skeleton className="h-6 w-48" />
						</CardHeader>
						<CardContent className="space-y-3">
							<Skeleton className="h-4 w-full" />
							<Skeleton className="h-4 w-3/4" />
							<div className="flex gap-2">
								<Skeleton className="h-5 w-16" />
								<Skeleton className="h-5 w-20" />
							</div>
						</CardContent>
					</Card>
				))}
			</div>
		);
	}

	if (isError) {
		return (
			<Alert variant="destructive">
				<AlertTitle>加载团队失败</AlertTitle>
				<AlertDescription>{error instanceof Error ? error.message : "请稍后重试。"}</AlertDescription>
			</Alert>
		);
	}

	const groups = data ?? [];

	if (!groups.length) {
		return (
			<Alert>
				<AlertTitle>没有关联的团队</AlertTitle>
				<AlertDescription>
					{username ? `${username} 当前未加入任何 Keycloak 组。` : "当前用户未加入任何 Keycloak 组。"}
				</AlertDescription>
			</Alert>
		);
	}

	return (
		<div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
			{groups.map((group) => (
				<Card key={group.id} className="flex w-full flex-col">
					<CardHeader className="flex flex-row items-center gap-3">
						<Icon icon="solar:users-group-rounded-bold-duotone" size={32} className="text-primary" />
						<div className="flex flex-col">
							<CardTitle>{group.name || group.path || "未命名组"}</CardTitle>
							<Text variant="body3" className="text-muted-foreground">
								{group.path}
							</Text>
						</div>
					</CardHeader>
					<CardContent className="space-y-3">
						{group.attributes ? (
							<div className="flex flex-wrap gap-2">
								{Object.entries(group.attributes).map(([key, value]) => (
									<Badge key={key} variant="outline">
										{key}: {Array.isArray(value) ? value.join("、") : value}
									</Badge>
								))}
							</div>
						) : null}
						{group.subGroups && group.subGroups.length > 0 ? (
							<div className="space-y-1">
								<Text variant="body3" className="text-muted-foreground">
									子组：
								</Text>
								<div className="flex flex-wrap gap-2">
									{group.subGroups.map((subGroup) => (
										<Badge key={subGroup.id}>{subGroup.name || subGroup.path}</Badge>
									))}
								</div>
							</div>
						) : null}
					</CardContent>
				</Card>
			))}
		</div>
	);
}
