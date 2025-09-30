import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import type { KeycloakUser, PaginationParams, UserProfileConfig, UserTableRow } from "#/keycloak";
import { KeycloakUserProfileService, KeycloakUserService } from "@/api/services/keycloakService";
import type { CustomUserAttributeKey } from "@/constants/user";
import { Icon } from "@/components/icon";
import { PERSON_SECURITY_LEVELS } from "@/constants/governance";
import { usePathname, useRouter } from "@/routes/hooks";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader } from "@/ui/card";
import { Input } from "@/ui/input";
import ResetPasswordModal from "./reset-password-modal";
import UserModal from "./user-modal";
import { useBilingualText } from "@/hooks/useBilingualText";

const RESERVED_PROFILE_ATTRIBUTE_NAMES = new Set([
	"username",
	"email",
	"firstName",
	"lastName",
	"fullName",
	"locale",
	"department",
	"position",
	"person_security_level",
	"personnel_security_level",
	"person_level",
	"data_levels",
]);

const getSingleAttributeValue = (attributes: Record<string, string[]> | undefined, key: CustomUserAttributeKey) => {
	const values = attributes?.[key];
	if (!values || values.length === 0) {
		return "";
	}
	const nonEmpty = values.find((item) => item && item.trim());
	return nonEmpty ?? values[0] ?? "";
};

export default function UserPage() {
	const { push } = useRouter();
	const pathname = usePathname();
	const bilingual = useBilingualText();
	const translate = (key: string, fallback: string) => {
		const value = bilingual(key).trim();
		return value.length > 0 ? value : fallback;
	};

	const [users, setUsers] = useState<UserTableRow[]>([]);
	const [loading, setLoading] = useState(false);
	const [searchValue, setSearchValue] = useState("");
	const [pagination, setPagination] = useState<PaginationParams>({
		current: 1,
		pageSize: 10,
		total: 0,
	});

	// UserProfile配置状态
	const [userProfileConfig, setUserProfileConfig] = useState<UserProfileConfig | null>(null);
	const [_profileLoading, setProfileLoading] = useState(false);

	// Modal状态
	const [userModal, setUserModal] = useState<{
		open: boolean;
		mode: "create" | "edit";
		user?: KeycloakUser;
	}>({ open: false, mode: "create" });

	const [resetPasswordModal, setResetPasswordModal] = useState<{
		open: boolean;
		userId: string;
		username: string;
	}>({ open: false, userId: "", username: "" });

	const personLevelLabelMap = useMemo(() => {
		return new Map(PERSON_SECURITY_LEVELS.map((item) => [item.value, item.label]));
	}, []);

	// 加载用户列表
	const loadUsers = useCallback(async (params?: { current?: number; pageSize?: number; search?: string }) => {
		setLoading(true);
		try {
			const { current = 1, pageSize = 10, search } = params || {};

			let usersData: KeycloakUser[];
			if (search) {
				usersData = await KeycloakUserService.searchUsers(search);
			} else {
				usersData = await KeycloakUserService.getAllUsers({
					first: (current - 1) * pageSize,
					max: pageSize,
				});
			}

			const tableData: UserTableRow[] = usersData.map((user) => ({
				...user,
				key: user.id || user.username,
			}));

			setUsers(tableData);
			setPagination((prev) => ({
				...prev,
				current,
				pageSize,
				total: search ? tableData.length : Math.max(tableData.length, prev.total || 0),
			}));
		} catch (error: any) {
			console.error("Error loading users:", error);
			toast.error(`加载用户列表失败: ${error.message || "未知错误"}`);
		} finally {
			setLoading(false);
		}
	}, []);

	// 加载UserProfile配置
	const loadUserProfileConfig = useCallback(async () => {
		setProfileLoading(true);
		try {
			const config = await KeycloakUserProfileService.getUserProfileConfig();
			setUserProfileConfig(config);
		} catch (error: any) {
			console.error("Error loading user profile config:", error);
			// 不显示错误，因为可能没有配置UserProfile
		} finally {
			setProfileLoading(false);
		}
	}, []);

	// 搜索用户
	const handleSearch = () => {
		if (searchValue.trim()) {
			loadUsers({ search: searchValue.trim() });
		} else {
			loadUsers({ current: 1, pageSize: pagination.pageSize });
		}
	};

	// 表格列定义
	const columns: ColumnsType<UserTableRow> = [
		{
			title: "用户名",
			dataIndex: "username",
			width: 220,
			render: (_, record) => (
				<div className="flex items-center">
					<div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary font-medium">
						{record.username.charAt(0).toUpperCase()}
					</div>
					<div className="ml-3">
						<div className="font-medium flex flex-wrap items-center gap-2">
							<span>{record.username}</span>
							{record.firstName && <span className="text-sm text-muted-foreground">({record.firstName})</span>}
						</div>
						<div className="font-medium">{record.username}</div>
						<div className="text-sm text-muted-foreground">{record.email || "未填写邮箱"}</div>
					</div>
				</div>
			),
		},
		{
			title: "姓名",
			dataIndex: "firstName",
			width: 160,
			render: (_: string, record) => {
				const name = (record.firstName ?? record.lastName ?? "").trim();
				return name.length > 0 ? name : "-";
			},
		},
		{
			title: "部门",
			dataIndex: ["attributes", "department"],
			width: 180,
			render: (_: string[] | undefined, record) => {
				const department = getSingleAttributeValue(record.attributes, "department").trim();
				return department.length > 0 ? department : "-";
			},
		},
		{
			title: "职位",
			dataIndex: ["attributes", "position"],
			width: 180,
			render: (_: string[] | undefined, record) => {
				const position = getSingleAttributeValue(record.attributes, "position").trim();
				return position.length > 0 ? position : "-";
			},
		},
		{
			title: "人员密级",
			dataIndex: ["attributes", "personnel_security_level"],
			width: 150,
			render: (_: string[] | undefined, record) => {
				const primaryLevel = getSingleAttributeValue(record.attributes, "personnel_security_level").trim();
				const legacyLevel = record.attributes?.person_security_level?.[0]?.trim();
				const levelValue = primaryLevel || legacyLevel || "";
				if (!levelValue) return "-";
				const label = personLevelLabelMap.get(levelValue) ?? levelValue;
				return label !== levelValue ? `${label}（${levelValue}）` : label;
			},
		},
		{
			title: "状态",
			dataIndex: "enabled",
			align: "center",
			width: 100,
			render: (enabled: boolean) => (
				<Badge variant={enabled ? "success" : "destructive"}>{enabled ? "启用" : "禁用"}</Badge>
			),
		},

		// 动态添加UserProfile字段列
		...(userProfileConfig?.attributes
			?.filter((attr) => !RESERVED_PROFILE_ATTRIBUTE_NAMES.has(attr.name))
			.map((attr) => ({
				title: translate(attr.displayName.replace(/\$\{([^}]*)\}/g, "$1"), attr.name),
				dataIndex: ["attributes", attr.name],
				width: 150,
				render: (value: string[] | undefined) => {
					if (!value || value.length === 0) return "-";
					return <span>{attr.multivalued ? value.join(", ") : value[0]}</span>;
				},
			})) || []),
		{
			title: "创建时间",
			dataIndex: "createdTimestamp",
			width: 150,
			render: (timestamp: number) => {
				if (!timestamp) return "-";
				return new Date(timestamp).toLocaleString("zh-CN");
			},
		},
		{
			title: "操作",
			key: "operation",
			align: "center",
			width: 200,
			fixed: "right",
			render: (_, record) => (
				<div className="flex items-center justify-center gap-1">
					<Button variant="ghost" size="sm" title="查看详情" onClick={() => push(`${pathname}/${record.id}`)}>
						<Icon icon="mdi:eye" size={16} />
					</Button>
					<Button
						variant="ghost"
						size="sm"
						title="编辑用户"
						onClick={() => setUserModal({ open: true, mode: "edit", user: record })}
					>
						<Icon icon="solar:pen-bold-duotone" size={16} />
					</Button>
					<Button
						variant="ghost"
						size="sm"
						title="重置密码"
						onClick={() =>
							setResetPasswordModal({
								open: true,
								userId: record.id || "",
								username: record.username,
							})
						}
					>
						<Icon icon="mdi:key-variant" size={16} />
					</Button>
				</div>
			),
		},
	];

	// 初始化加载
	useEffect(() => {
		loadUsers();
		loadUserProfileConfig();
	}, [loadUsers, loadUserProfileConfig]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<div className="flex items-center justify-between">
						<div>
							<h2 className="text-2xl font-bold">用户管理</h2>
							<p className="text-muted-foreground">管理Keycloak用户账户</p>
						</div>
						<Button onClick={() => setUserModal({ open: true, mode: "create" })}>
							<Icon icon="mdi:plus" size={16} className="mr-2" />
							新建用户
						</Button>
					</div>
				</CardHeader>
				<CardContent>
					{/* 搜索栏 */}
					<div className="flex items-center gap-2 mb-4">
						<Input
							placeholder="搜索用户名..."
							value={searchValue}
							onChange={(e) => setSearchValue(e.target.value)}
							onKeyDown={(e) => e.key === "Enter" && handleSearch()}
							className="max-w-sm"
						/>
						<Button onClick={handleSearch}>
							<Icon icon="mdi:magnify" size={16} className="mr-2" />
							搜索
						</Button>
						{searchValue && (
							<Button
								variant="outline"
								onClick={() => {
									setSearchValue("");
									loadUsers({ current: 1, pageSize: pagination.pageSize });
								}}
							>
								清除
							</Button>
						)}
					</div>

					{/* 用户表格 */}
					<Table
						rowKey="key"
						columns={columns}
						dataSource={users}
						loading={loading}
						scroll={{ x: 1200 }}
						pagination={{
							current: pagination.current,
							pageSize: pagination.pageSize,
							total: pagination.total,
							showSizeChanger: true,
							showQuickJumper: true,
							showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
							onChange: (page, pageSize) => {
								loadUsers({ current: page, pageSize });
							},
						}}
					/>
				</CardContent>
			</Card>

			{/* 用户创建/编辑Modal */}
			<UserModal
				open={userModal.open}
				mode={userModal.mode}
				user={userModal.user}
				onCancel={() => setUserModal({ open: false, mode: "create" })}
				onSuccess={() => {
					setUserModal({ open: false, mode: "create" });
					loadUsers({ current: pagination.current, pageSize: pagination.pageSize });
				}}
			/>

			{/* 密码重置Modal */}
			<ResetPasswordModal
				open={resetPasswordModal.open}
				userId={resetPasswordModal.userId}
				username={resetPasswordModal.username}
				onCancel={() => setResetPasswordModal({ open: false, userId: "", username: "" })}
				onSuccess={() => {
					setResetPasswordModal({ open: false, userId: "", username: "" });
				}}
			/>
		</div>
	);
}
