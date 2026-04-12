import { Card, Descriptions, Tag } from "antd";
import { UserOutlined, MailOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import { useUserInfo } from "@/store/userStore";
import { resolveProfileName } from "./profile-utils";

const DEFAULT_AVATAR = "/assets/icons/ic-user.svg";

function pickAttributeValue(
	attributes: Record<string, string[]> | undefined,
	keys: string[],
): string {
	if (!attributes) return "";
	for (const key of keys) {
		const values = attributes[key];
		if (Array.isArray(values) && values.length > 0 && values[0]) {
			return String(values[0]).trim();
		}
	}
	return "";
}

export default function ProfilePage() {
	const userInfo = useUserInfo();

	const username = userInfo.username || "";
	const email = userInfo.email || "";
	const avatar = userInfo.avatar || DEFAULT_AVATAR;
	const roles: string[] = Array.isArray(userInfo.roles) ? (userInfo.roles as string[]) : [];
	const permissions: string[] = Array.isArray(userInfo.permissions) ? (userInfo.permissions as string[]) : [];
	const attributes = (userInfo as any)?.attributes as Record<string, string[]> | undefined;

	const displayName = resolveProfileName({
		storeFullName: (userInfo as any)?.fullName || (userInfo as any)?.name,
		storeFirstName: (userInfo as any)?.firstName,
		username,
		fallbackName: null,
		attributeSources: [attributes],
		pickAttributeValue,
	});

	const deptCode = pickAttributeValue(attributes, ["dept_code", "deptCode", "department"]);
	const personnelLevel = pickAttributeValue(attributes, [
		"personnel_level",
		"person_security_level",
		"person_level",
	]);

	return (
		<div className="mx-auto max-w-3xl space-y-6 p-6">
			<Card>
				<div className="flex items-center gap-6">
					<img
						className="h-20 w-20 rounded-2xl border border-border/50 object-cover"
						src={avatar}
						alt={username}
					/>
					<div>
						<h2 className="text-xl font-semibold">{displayName}</h2>
						<p className="text-sm text-muted-foreground">@{username}</p>
					</div>
				</div>
			</Card>

			<Card title="基本信息">
				<Descriptions column={1} colon={false} labelStyle={{ width: 120 }}>
					<Descriptions.Item label={<><UserOutlined className="mr-1" />用户名</>}>
						{username || "-"}
					</Descriptions.Item>
					<Descriptions.Item label={<><UserOutlined className="mr-1" />姓名</>}>
						{displayName || "-"}
					</Descriptions.Item>
					<Descriptions.Item label={<><MailOutlined className="mr-1" />邮箱</>}>
						{email || "-"}
					</Descriptions.Item>
					{deptCode && (
						<Descriptions.Item label="部门编码">
							{deptCode}
						</Descriptions.Item>
					)}
					{personnelLevel && (
						<Descriptions.Item label="密级">
							{personnelLevel}
						</Descriptions.Item>
					)}
				</Descriptions>
			</Card>

			<Card title={<><SafetyCertificateOutlined className="mr-1" />角色与权限</>}>
				<div className="space-y-4">
					<div>
						<div className="mb-2 text-sm font-medium text-foreground">角色</div>
						<div className="flex flex-wrap gap-2">
							{roles.length > 0
								? roles.map((role) => (
										<Tag key={role} color="blue">
											{role}
										</Tag>
									))
								: <span className="text-sm text-muted-foreground">暂无角色</span>
							}
						</div>
					</div>
					{permissions.length > 0 && (
						<div>
							<div className="mb-2 text-sm font-medium text-foreground">权限</div>
							<div className="flex flex-wrap gap-2">
								{permissions.map((perm) => (
									<Tag key={perm} color="green">
										{perm}
									</Tag>
								))}
							</div>
						</div>
					)}
				</div>
			</Card>
		</div>
	);
}
