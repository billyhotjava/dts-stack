import { useEffect, useMemo, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Badge } from "@/ui/badge";
import { useUserInfo } from "@/store/userStore";
import deptService from "@/api/services/deptService";

export default function ProfilePage() {
	const user = useUserInfo();
	const [departmentName, setDepartmentName] = useState<string>("-");

	const roles = useMemo(() => {
		if (!user || !Array.isArray(user.roles)) return [];
		const hiddenPrefixes = ["ROLE_DEFAULT", "ROLE_UMA_AUTHORIZATION", "ROLE_OFFLINE_ACCESS"];
		return (user.roles as string[])
			.filter(Boolean)
			.filter((role) => {
				const upper = role.toUpperCase();
				return hiddenPrefixes.every((prefix) => !upper.startsWith(prefix));
			});
	}, [user]);

	const displayName = useMemo(() => {
		if (!user) return "-";
		const attrs = (user as any)?.attributes ?? {};
		const first = (user as any)?.firstName || (Array.isArray(attrs?.firstName) ? attrs.firstName[0] : undefined);
		const last = (user as any)?.lastName || (Array.isArray(attrs?.lastName) ? attrs.lastName[0] : undefined);
		const full = [first, last].filter((part) => typeof part === "string" && part.trim().length).join("");
		if (full) return full;
		const candidateList: Array<unknown> = [
			(user as any)?.fullName,
			(user as any)?.displayName,
		];
		const pushAttr = (value: unknown) => {
			if (Array.isArray(value) && value.length) candidateList.push(value[0]);
			else if (value) candidateList.push(value);
		};
		pushAttr(attrs?.fullname);
		pushAttr(attrs?.full_name);
		pushAttr(attrs?.display_name);
		for (const candidate of candidateList) {
			const text = typeof candidate === "string" ? candidate.trim() : "";
			if (text) return text;
		}
		return "-";
	}, [user]);

	useEffect(() => {
		const rawCode =
			(user as any)?.deptName ||
			(user as any)?.dept_name ||
			(user as any)?.deptCode ||
			(user as any)?.dept_code ||
			(user as any)?.orgPath;
		if (!rawCode) {
			setDepartmentName("-");
			return;
		}
		const normalized = String(rawCode).trim();
		if (!normalized) {
			setDepartmentName("-");
			return;
		}
		// If the value already looks like a name (contains Chinese characters or letters), display it directly.
		const containsLetter = /[A-Za-z\u4e00-\u9fa5]/.test(normalized);
		if (containsLetter && !/^[0-9/]+$/.test(normalized)) {
			setDepartmentName(normalized);
			return;
		}
		const candidates = (() => {
			const list = new Set<string>();
			list.add(normalized);
			if (normalized.includes("/")) {
				normalized
					.split("/")
					.map((seg) => seg.trim())
					.filter(Boolean)
					.forEach((seg) => list.add(seg));
			}
			return Array.from(list);
		})();

		const fallback = candidates[candidates.length - 1] ?? normalized;
		let cancelled = false;
		void deptService
			.listDepartments(normalized)
			.then((list) => {
				if (cancelled) return;
				if (Array.isArray(list) && list.length) {
					for (const candidate of candidates) {
						const match = list.find(
							(item) =>
								item.code === candidate ||
								item.nameZh === candidate ||
								item.nameEn === candidate,
						);
						if (match) {
							setDepartmentName(match.nameZh || match.nameEn || match.code);
							return;
						}
					}
				}
				setDepartmentName(fallback);
			})
			.catch(() => {
				if (!cancelled) setDepartmentName(fallback);
			});
		return () => {
			cancelled = true;
		};
	}, [user]);

	return (
		<div className="mx-auto flex w-full max-w-5xl flex-col gap-4 p-6">
			<div>
				<h1 className="text-xl font-semibold text-foreground">个人信息</h1>
				<p className="mt-1 text-sm text-muted-foreground">查看账号资料、角色与联系信息。</p>
			</div>

			<Card>
				<CardHeader>
					<CardTitle>账号资料</CardTitle>
				</CardHeader>
				<CardContent className="grid gap-4 sm:grid-cols-2">
					<ProfileField label="用户名" value={user?.username ?? "-"} />
					<ProfileField label="姓名" value={displayName} />
					<ProfileField label="邮箱" value={user?.email ?? "-"} />
					<ProfileField label="手机号" value={(user as any)?.mobile ?? "-"} />
					<ProfileField label="所属部门" value={departmentName || "-"} />
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>角色与权限</CardTitle>
				</CardHeader>
				<CardContent>
					{roles.length ? (
						<div className="flex flex-wrap gap-2">
							{roles.map((role) => (
								<Badge key={role} variant="outline">
									{role}
								</Badge>
							))}
						</div>
					) : (
						<p className="text-sm text-muted-foreground">当前账号未分配角色。</p>
					)}
				</CardContent>
			</Card>

		</div>
	);
}

function ProfileField({ label, value }: { label: string; value: string }) {
	return (
		<div className="flex flex-col gap-1 text-sm">
			<span className="text-muted-foreground">{label}</span>
			<span className="text-foreground">{value || "-"}</span>
		</div>
	);
}
