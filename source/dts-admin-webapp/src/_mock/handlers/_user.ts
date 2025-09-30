import { faker } from "@faker-js/faker";
import { HttpResponse, http } from "msw";
import { UserApi } from "@/api/services/userService";
import { ResultStatus } from "@/types/enum";
import { convertFlatToTree } from "@/utils/tree";
import { DB_MENU } from "../assets_backup";
import { setActiveAdmin, resetActiveAdmin } from "../utils/session";

const ADMIN_PASSWORD = "Devops123@";

const ADMIN_ACCOUNTS = [
	{
		id: "admin-sys",
		username: "sysadmin",
		email: "sysadmin@example.com",
		role: "SYSADMIN" as const,
		fullName: "系统管理员",
		permissions: ["user.create", "user.update", "org.manage", "config.manage"],
	},
	{
		id: "admin-auth",
		username: "authadmin",
		email: "authadmin@example.com",
		role: "AUTHADMIN" as const,
		fullName: "授权管理员",
		permissions: ["approval.review", "approval.assign"],
	},
	{
		id: "admin-audit",
		username: "auditadmin",
		email: "auditadmin@example.com",
		role: "AUDITADMIN" as const,
		fullName: "安全审计员",
		permissions: ["audit.read", "audit.export"],
	},
];

const baseMenu = convertFlatToTree(DB_MENU);

const signIn = http.post(`/api${UserApi.SignIn}`, async ({ request }) => {
	const { username, password } = (await request.json()) as Record<string, string>;

	const account = ADMIN_ACCOUNTS.find((item) => item.username === username);
	if (!account || password !== ADMIN_PASSWORD) {
		return HttpResponse.json(
			{
				status: ResultStatus.ERROR,
				message: "用户名或密码错误",
			},
			{ status: 401 },
		);
	}

	setActiveAdmin({
		allowed: true,
		role: account.role,
		username: account.username,
		email: account.email,
	});

	return HttpResponse.json({
		status: ResultStatus.SUCCESS,
		message: "登录成功",
		data: {
			user: {
				id: account.id,
				email: account.email,
				username: account.username,
				firstName: account.fullName,
				fullName: account.fullName,
				avatar: `https://avatars.dicebear.com/api/initials/${account.username}.svg`,
				enabled: true,
				roles: [account.role],
				permissions: account.permissions,
				menu: baseMenu,
			},
			accessToken: faker.string.uuid(),
			refreshToken: faker.string.uuid(),
		},
	});
});

const logout = http.post(`/api${UserApi.Logout}`, async () => {
	resetActiveAdmin();
	return HttpResponse.json({ status: ResultStatus.SUCCESS, message: "已退出", data: null });
});

const userList = http.get("/api/user", async () => {
	return HttpResponse.json(
		Array.from({ length: 10 }).map(() => ({
			fullname: faker.person.fullName(),
			email: faker.internet.email(),
			avatar: faker.image.avatarGitHub(),
			address: faker.location.streetAddress(),
		})),
		{
			status: 200,
		},
	);
});

export { signIn, logout, userList };
