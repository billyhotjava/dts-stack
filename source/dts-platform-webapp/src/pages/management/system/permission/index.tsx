import Table, { type ColumnsType } from "antd/es/table";
import { isNil } from "ramda";
import { useMemo, useState } from "react";
import type { Permission_Old } from "#/entity";
import { BasicStatus, PermissionType } from "#/enum";
import { Icon } from "@/components/icon";
import { useBilingualText } from "@/hooks/useBilingualText";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader } from "@/ui/card";
import PermissionModal, { type PermissionModalProps } from "./permission-modal";

const defaultPermissionValue: Permission_Old = {
	id: "",
	parentId: "",
	name: "",
	label: "",
	route: "",
	component: "",
	icon: "",
	hide: false,
	status: BasicStatus.ENABLE,
	type: PermissionType.CATALOGUE,
};

const mockPermissions: Permission_Old[] = [
	{
		id: "perm-dashboard",
		parentId: "",
		name: "工作台",
		label: "sys.permission.dashboard",
		route: "/workbench",
		component: "/pages/dashboard/workbench",
		icon: "ic-workbench",
		hide: false,
		status: BasicStatus.ENABLE,
		type: PermissionType.MENU,
		order: 1,
	},
	{
		id: "perm-security",
		parentId: "",
		name: "数据安全",
		label: "sys.permission.dataSecurity",
		route: "/security/assets",
		component: "/pages/security/data-security",
		icon: "solar:shield-keyhole-bold",
		hide: false,
		status: BasicStatus.ENABLE,
		type: PermissionType.MENU,
		order: 2,
	},
	{
		id: "perm-user",
		parentId: "perm-security",
		name: "账户中心",
		label: "sys.permission.user",
		route: "/management/system/user",
		component: "/pages/management/system/user",
		icon: "solar:users-group-rounded-bold",
		hide: false,
		status: BasicStatus.ENABLE,
		type: PermissionType.MENU,
		order: 1,
	},
	{
		id: "perm-group",
		parentId: "perm-security",
		name: "组织机构",
		label: "sys.permission.group",
		route: "/management/system/group",
		component: "/pages/management/system/group",
		icon: "solar:city-bold",
		hide: false,
		status: BasicStatus.ENABLE,
		type: PermissionType.MENU,
		order: 2,
	},
	{
		id: "perm-permission",
		parentId: "perm-security",
		name: "权限字典",
		label: "sys.permission.permission",
		route: "/management/system/permission",
		component: "/pages/management/system/permission",
		icon: "solar:list-bold",
		hide: false,
		status: BasicStatus.ENABLE,
		type: PermissionType.MENU,
		order: 3,
	},
];

const typeLabelMap: Record<number, string> = {
	[PermissionType.CATALOGUE]: "sys.permission.type.catalogue",
	[PermissionType.MENU]: "sys.permission.type.menu",
	[PermissionType.COMPONENT]: "sys.permission.type.component",
};

export default function PermissionPage() {
	const bilingual = useBilingualText();
	const translate = (key: string, fallback: string) => {
		const value = bilingual(key).trim();
		return value.length > 0 ? value : fallback;
	};

	const [permissionModalProps, setPermissionModalProps] = useState<PermissionModalProps>({
		formValue: { ...defaultPermissionValue },
		title: translate("sys.permission.modal.create", "新增权限"),
		show: false,
		onOk: () => setPermissionModalProps((prev) => ({ ...prev, show: false })),
		onCancel: () => setPermissionModalProps((prev) => ({ ...prev, show: false })),
	});

	const columns: ColumnsType<Permission_Old> = useMemo(
		() => [
			{
				title: translate("sys.permission.columns.name", "权限名称"),
				dataIndex: "name",
				width: 240,
				render: (_, record) => <div>{translate(record.label, record.name)}</div>,
			},
			{
				title: translate("sys.permission.columns.type", "类型"),
				dataIndex: "type",
				width: 100,
				render: (_, record) => <Badge variant="info">{translate(typeLabelMap[record.type], PermissionType[record.type])}</Badge>,
			},
			{
				title: translate("sys.permission.columns.icon", "图标"),
				dataIndex: "icon",
				width: 80,
				render: (icon: string) => {
					if (isNil(icon)) return "";
					if (icon.startsWith("ic")) {
						return <Icon icon={`local:${icon}`} size={18} className="ant-menu-item-icon" />;
					}
					return <Icon icon={icon} size={18} className="ant-menu-item-icon" />;
				},
			},
			{
				title: translate("sys.permission.columns.component", "页面组件"),
				dataIndex: "component",
			},
			{
				title: translate("sys.permission.columns.status", "启用状态"),
				dataIndex: "status",
				align: "center",
				width: 120,
				render: (status) => (
					<Badge variant={status === BasicStatus.DISABLE ? "error" : "success"}>
						{status === BasicStatus.DISABLE
							? translate("sys.permission.status.disable", "已禁用")
							: translate("sys.permission.status.enable", "已启用")}
					</Badge>
				),
			},
			{ title: translate("sys.permission.columns.order", "排序"), dataIndex: "order", width: 80 },
			{
				title: translate("sys.permission.columns.action", "操作"),
				key: "operation",
				align: "center",
				width: 120,
				render: (_, record) => (
					<div className="flex w-full justify-end text-gray">
						{record?.type === PermissionType.CATALOGUE && (
							<Button
								variant="ghost"
								size="icon"
								title={translate("sys.permission.action.addChild", "新增下级")}
								onClick={() => onCreate(record.id)}
							>
								<Icon icon="gridicons:add-outline" size={18} />
							</Button>
						)}
						<Button
							variant="ghost"
							size="icon"
							title={translate("sys.permission.action.edit", "编辑权限")}
							onClick={() => onEdit(record)}
						>
							<Icon icon="solar:pen-bold-duotone" size={18} />
						</Button>
						<Button variant="ghost" size="icon" title={translate("sys.permission.action.delete", "删除权限")}>
							<Icon icon="mingcute:delete-2-fill" size={18} className="text-error!" />
						</Button>
					</div>
				),
			},
		],
		[translate],
	);

	const onCreate = (parentId?: string) => {
		setPermissionModalProps((prev) => ({
			...prev,
			show: true,
			title: translate("sys.permission.modal.create", "新增权限"),
			formValue: { ...defaultPermissionValue, parentId: parentId ?? "" },
		}));
	};

	const onEdit = (formValue: Permission_Old) => {
		setPermissionModalProps((prev) => ({
			...prev,
			show: true,
			title: translate("sys.permission.modal.edit", "编辑权限"),
			formValue,
		}));
	};

	return (
		<Card>
			<CardHeader>
				<div className="flex items-center justify-between">
					<div className="text-base font-semibold">{translate("sys.permission.title", "权限列表")}</div>
					<Button onClick={() => onCreate()}>{translate("sys.permission.new", "新增权限")}</Button>
				</div>
			</CardHeader>
			<CardContent>
				<Table
					rowKey="id"
					size="small"
					scroll={{ x: "max-content" }}
					pagination={false}
					columns={columns}
					dataSource={mockPermissions}
				/>
			</CardContent>
			<PermissionModal {...permissionModalProps} />
		</Card>
	);
}
