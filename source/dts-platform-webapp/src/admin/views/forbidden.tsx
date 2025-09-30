import { Button } from "@/ui/button";
import { Title, Text } from "@/ui/typography";
import { useRouter } from "@/routes/hooks";

export default function ForbiddenView() {
	const router = useRouter();

	return (
		<div className="flex min-h-[50vh] w-full flex-col items-center justify-center gap-4 text-center">
			<Title as="h1" className="text-3xl font-bold">
				403 · 无权限访问
			</Title>
			<Text variant="body2" className="max-w-md text-muted-foreground">
				当前账户不在管理端白名单或未分配正确的管理员角色，请联系系统管理员获取访问权限。
			</Text>
			<Button onClick={() => router.replace("/auth/login")}>返回登录</Button>
		</div>
	);
}
