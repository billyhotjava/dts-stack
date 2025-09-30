import Table, { type ColumnsType } from "antd/es/table";
import { isNil } from "ramda";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import type { Permission_Old } from "#/entity";
import { BasicStatus, PermissionType } from "#/enum";
import { Icon } from "@/components/icon";
import { useBilingualText } from "@/hooks/useBilingualText";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader } from "@/ui/card";
import { adminApi } from "@/admin/api/adminApi";
import type { PortalMenuItem } from "@/admin/types";
// Synced from dts-platform-webapp zh_CN i18n by scripts/sync-portal-menus.mjs
// Map: { "sys.nav.portal.xxx": "中文名" }
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore - JSON import
import portalI18nZh from "@/_mock/data/portal-i18n-zh.json";

// Fallback Chinese names when external portal i18n is not provided
const FALLBACK_PORTAL_ZH: Record<string, string> = {
	// overview
	"sys.nav.portal.overview": "总览",
	"sys.nav.portal.overviewPlatform": "平台概览",
	"sys.nav.portal.overviewTasks": "我的待办",
	"sys.nav.portal.overviewShortcuts": "快捷入口",
	// catalog
	"sys.nav.portal.catalog": "数据目录",
	"sys.nav.portal.catalogDomains": "数据域与主题域",
	"sys.nav.portal.catalogDatasets": "数据集清单",
	"sys.nav.portal.catalogDictionary": "字段字典与业务术语",
	"sys.nav.portal.catalogTags": "标签管理",
	"sys.nav.portal.catalogClassification": "数据分级",
	"sys.nav.portal.catalogLineage": "数据血缘",
	"sys.nav.portal.catalogQuality": "数据质量概览",
	// modeling
	"sys.nav.portal.modeling": "模型与标准",
	"sys.nav.portal.modelingStandards": "数据标准",
	// governance
	"sys.nav.portal.governance": "治理与质量",
	"sys.nav.portal.governanceRules": "质量规则",
	"sys.nav.portal.governanceTasks": "质量任务与结果",
	"sys.nav.portal.governanceMasking": "分级与脱敏策略",
	"sys.nav.portal.governanceCompliance": "合规检查",
	"sys.nav.portal.governanceImpact": "变更影响评估",
	// explore
	"sys.nav.portal.explore": "数据开发",
	"sys.nav.portal.exploreWorkbench": "查询工作台",
	"sys.nav.portal.explorePreview": "数据预览",
	"sys.nav.portal.exploreSavedQueries": "已保存查询与模板",
	"sys.nav.portal.exploreSharing": "结果集分享",
	"sys.nav.portal.exploreExport": "导出中心",
	// visualization
	"sys.nav.portal.visualization": "数据可视化",
	"sys.nav.portal.visualizationReports": "数据报表",
	// services
	"sys.nav.portal.services": "数据服务",
	"sys.nav.portal.servicesApi": "API 服务",
	"sys.nav.portal.servicesProxy": "JDBC/ODBC Proxy",
	"sys.nav.portal.servicesProducts": "数据产品",
	"sys.nav.portal.servicesTokens": "令牌与密钥",
	"sys.nav.portal.servicesQuotas": "配额与限流",
	// iam
	"sys.nav.portal.iam": "权限与策略",
	"sys.nav.portal.iamClassification": "密级模型映射",
	"sys.nav.portal.iamAuthorization": "资源授权",
	"sys.nav.portal.iamSimulation": "策略模拟与评估",
	"sys.nav.portal.iamRequests": "权限申请入口",
	// settings
	"sys.nav.portal.settings": "个性化与设置",
	"sys.nav.portal.settingsPreferences": "个人偏好",
	"sys.nav.portal.settingsNotifications": "通知订阅",
	"sys.nav.portal.settingsGateway": "网关策略",
};
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

// Transform portal menu tree into a tree of Permission_Old for Table tree rendering
function mapPortalMenusToPermissions(items: PortalMenuItem[]): Permission_Old[] {
	const walk = (nodes: PortalMenuItem[], parentId: string): Permission_Old[] => {
		return nodes.map((node) => {
			const meta = safeParseMetadata(node.metadata);
			const hasChildren = Array.isArray(node.children) && node.children.length > 0;
			const id = String(node.id ?? `${parentId}/${node.path}`);
			const titleKey = meta?.titleKey;
			const i18nMap = portalI18nZh as Record<string, string>;
			const displayName =
				titleKey && (i18nMap[titleKey] || FALLBACK_PORTAL_ZH[titleKey])
					? i18nMap[titleKey] || FALLBACK_PORTAL_ZH[titleKey]
					: node.name;

			const current: Permission_Old = {
				id,
				parentId,
				name: displayName,
				label: titleKey ?? node.name,
				route: node.path,
				component: node.component ?? "",
				icon: meta?.icon ?? "",
				hide: false,
				status: BasicStatus.ENABLE,
				type: hasChildren ? PermissionType.CATALOGUE : PermissionType.MENU,
				order: node.sortOrder,
			};

			if (hasChildren) {
				current.children = walk(node.children!, id);
			}
			return current;
		});
	};

	return walk(items, "");
}

function safeParseMetadata(metadata?: string) {
	if (!metadata) return undefined as undefined | { icon?: string; titleKey?: string };
	try {
		return JSON.parse(metadata) as { icon?: string; titleKey?: string };
	} catch {
		return undefined;
	}
}

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
		title: "添加菜单",
		show: false,
		onOk: () => setPermissionModalProps((prev) => ({ ...prev, show: false })),
		onCancel: () => setPermissionModalProps((prev) => ({ ...prev, show: false })),
	});

	const columns: ColumnsType<Permission_Old> = useMemo(
		() => [
			{
				title: "菜单名称",
				dataIndex: "name",
				width: 240,
				render: (_, record) => <div>{record.name}</div>,
			},
			{
				title: "类型",
				dataIndex: "type",
				width: 100,
				render: (_, record) => (
					<Badge variant="info">{translate(typeLabelMap[record.type], PermissionType[record.type])}</Badge>
				),
			},
			{
				title: "图标",
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
				title: "启用状态",
				dataIndex: "status",
				align: "center",
				width: 120,
				render: (status) => (
					<Badge variant={status === BasicStatus.DISABLE ? "error" : "success"}>
						{status === BasicStatus.DISABLE ? "已禁用" : "已启用"}
					</Badge>
				),
			},
			{ title: "排序", dataIndex: "order", width: 80 },
			{
				title: "操作",
				key: "operation",
				align: "center",
				width: 120,
				render: (_, record) => (
					<div className="flex w-full justify-end text-gray">
						{record?.type === PermissionType.CATALOGUE && (
							<Button variant="ghost" size="icon" title="新增下级" onClick={() => onCreate(record.id)}>
								<Icon icon="gridicons:add-outline" size={18} />
							</Button>
						)}
						<Button variant="ghost" size="icon" title="编辑菜单" onClick={() => onEdit(record)}>
							<Icon icon="solar:pen-bold-duotone" size={18} />
						</Button>
						<Button variant="ghost" size="icon" title="删除菜单">
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
			title: "添加菜单",
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

	// Fetch portal menus from admin API (MSW serves dts-platform-webapp export)
	const { data: portalMenus = [], isLoading } = useQuery({
		queryKey: ["admin", "portal-menus", "as-permissions"],
		queryFn: adminApi.getPortalMenus,
	});

	const dataSource = useMemo(() => mapPortalMenusToPermissions(portalMenus), [portalMenus]);

	return (
		<Card>
			<CardHeader>
				<div className="flex items-center justify-between">
					<div className="text-base font-semibold">菜单列表</div>
				</div>
			</CardHeader>
			<CardContent>
				<Table
					rowKey="id"
					size="small"
					scroll={{ x: "max-content" }}
					pagination={false}
					expandable={{ defaultExpandAllRows: true }}
					columns={columns}
					loading={isLoading}
					dataSource={dataSource}
				/>
			</CardContent>
			<PermissionModal {...permissionModalProps} />
		</Card>
	);
}
