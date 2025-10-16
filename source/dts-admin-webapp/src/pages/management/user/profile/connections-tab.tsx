import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { KeycloakUserService } from "@/api/services/keycloakService";
import { Avatar, AvatarFallback, AvatarImage } from "@/ui/avatar";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Skeleton } from "@/ui/skeleton";
import { Text } from "@/ui/typography";
import { Alert, AlertDescription, AlertTitle } from "@/ui/alert";
import type { KeycloakUser } from "#/keycloak";

export default function ConnectionsTab() {
	const { data, isLoading, isError, error } = useQuery({
		queryKey: ["服务端", "recent-users"],
		queryFn: async () => {
			const users = await KeycloakUserService.getAllUsers({ max: 30 });
			return users;
		},
	});

	const users = useMemo(() => data ?? [], [data]);

	if (isLoading) {
		return (
			<div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
				{Array.from({ length: 6 }).map((_, index) => (
					<Card key={index}>
						<CardHeader className="flex flex-col items-center gap-3">
							<Skeleton className="h-20 w-20 rounded-full" />
							<Skeleton className="h-4 w-32" />
							<Skeleton className="h-3 w-24" />
						</CardHeader>
						<CardContent className="flex flex-col items-center gap-2">
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
				<AlertTitle>加载用户列表失败</AlertTitle>
				<AlertDescription>{error instanceof Error ? error.message : "请稍后重试。"}</AlertDescription>
			</Alert>
		);
	}

	if (!users.length) {
		return (
			<Alert>
				<AlertTitle>暂无其他用户</AlertTitle>
				<AlertDescription>系统中暂未检索到其他用户信息。</AlertDescription>
			</Alert>
		);
	}

	const normalizeDisplayName = (user: KeycloakUser) => {
		const attributefullName = Array.isArray(user.attributes?.fullName)
			? user.attributes?.fullName.find((item) => item?.trim()) || user.attributes?.fullName[0]
			: undefined;
		if (attributefullName) {
			return attributefullName;
		}
		const composedName = [user.firstName, user.lastName].filter(Boolean).join(" ").trim();
		if (composedName) {
			return composedName;
		}
		return user.username || "-";
	};

	const deriveInitial = (label: string) => {
		if (!label) return "?";
		const characters = Array.from(label.trim());
		return characters.slice(0, 2).join("");
	};

	return (
		<div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
			{users.map((user) => {
				const displayName = normalizeDisplayName(user);
				const email = user.email || "无邮箱信息";
				const username = user.username || "-";
				const tags: string[] = [];
				if (Array.isArray(user.realmRoles) && user.realmRoles.length) {
					tags.push(...user.realmRoles.slice(0, 3));
				}
				if (Array.isArray(user.groups) && user.groups.length) {
					tags.push(...user.groups.slice(0, 3));
				}
				const avatarAttribute = Array.isArray(user.attributes?.avatar)
					? user.attributes?.avatar.find((item) => item && item.trim()) || user.attributes?.avatar[0]
					: undefined;
				const avatarUrl = avatarAttribute || undefined;

				return (
					<Card className="flex flex-col items-center" key={`${user.id ?? username}-${username}`}>
						<CardHeader className="flex flex-col items-center gap-3">
							<Avatar className="h-20 w-20">
								<AvatarImage src={avatarUrl} />
								<AvatarFallback>{deriveInitial(displayName)}</AvatarFallback>
							</Avatar>
							<CardTitle className="text-center text-base font-semibold">
								{displayName}
							</CardTitle>
							<Text variant="body3" className="text-muted-foreground">
								{username}
							</Text>
						</CardHeader>
						<CardContent className="flex w-full flex-col items-center gap-3">
							<Text variant="body3" className="text-muted-foreground">
								{email}
							</Text>
							{tags.length ? (
								<div className="flex flex-wrap justify-center gap-2">
									{tags.map((tag) => (
										<Badge key={`${user.id}-${tag}`} variant="outline">
											{tag}
										</Badge>
									))}
								</div>
							) : (
								<Text variant="body3" className="text-muted-foreground">
									暂无角色或分组信息
								</Text>
							)}
						</CardContent>
					</Card>
				);
			})}
		</div>
	);
}
