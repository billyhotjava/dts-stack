import { useEffect, useMemo, useState } from "react";
import type { ReactElement } from "react";
import { toast } from "sonner";
import type { CreateRoleRequest, KeycloakRole, UpdateRoleRequest } from "#/keycloak";
import { KeycloakRoleService } from "@/api/services/keycloakService";
import { DATA_SECURITY_LEVEL_LABELS } from "@/constants/governance";
import type { MenuTree } from "#/entity";
import { PermissionType } from "#/enum";
import { adminApi } from "@/admin/api/adminApi";
import type { PortalMenuItem } from "@/admin/types";
// Removed mock i18n import; use fallback labels only
import { Alert, AlertDescription } from "@/ui/alert";
import { Button } from "@/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Switch } from "@/ui/switch";
import { Textarea } from "@/ui/textarea";
import { ScrollArea } from "@/ui/scroll-area";
import { Checkbox } from "@/ui/checkbox";

interface RoleModalProps {
	open: boolean;
	mode: "create" | "edit";
	role?: KeycloakRole;
	onCancel: () => void;
	onSuccess: () => void;
}

interface FormData {
    name: string;
    description: string;
    composite: boolean;
}

const ROLE_MENU_ATTRIBUTE_KEY = "menuIds";

export default function RoleModal({ open, mode, role, onCancel, onSuccess }: RoleModalProps) {
    const [formData, setFormData] = useState<FormData>({
        name: "",
        description: "",
        composite: false,
    });

	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string>("");
    const [menuTree, setMenuTree] = useState<MenuTree[]>([]);
    const [menuSecurityMap, setMenuSecurityMap] = useState<Map<string, string>>(new Map());
	const [menuLoading, setMenuLoading] = useState(false);
	const [selectedMenus, setSelectedMenus] = useState<Set<string>>(new Set());

	// 将门户菜单（菜单管理的数据源）映射为 MenuTree，以保证一致性
	const FALLBACK_PORTAL_ZH: Record<string, string> = {
		"sys.nav.portal.overview": "总览",
		"sys.nav.portal.overviewPlatform": "平台概览",
		"sys.nav.portal.overviewTasks": "我的待办",
		"sys.nav.portal.overviewShortcuts": "快捷入口",
		"sys.nav.portal.catalog": "数据目录",
		"sys.nav.portal.catalogDomains": "数据域与主题域",
		"sys.nav.portal.catalogDatasets": "数据集清单",
		"sys.nav.portal.catalogDictionary": "字段字典与业务术语",
		"sys.nav.portal.catalogTags": "标签管理",
		"sys.nav.portal.catalogClassification": "数据分级",
		"sys.nav.portal.catalogLineage": "数据血缘",
		"sys.nav.portal.catalogQuality": "数据质量概览",
		"sys.nav.portal.modeling": "模型与标准",
		"sys.nav.portal.modelingStandards": "数据标准",
		"sys.nav.portal.governance": "治理与质量",
		"sys.nav.portal.governanceRules": "质量规则",
		"sys.nav.portal.governanceTasks": "质量任务与结果",
		"sys.nav.portal.governanceMasking": "分级与脱敏策略",
		"sys.nav.portal.governanceCompliance": "合规检查",
		"sys.nav.portal.governanceImpact": "变更影响评估",
		"sys.nav.portal.explore": "数据开发",
		"sys.nav.portal.exploreWorkbench": "查询工作台",
		"sys.nav.portal.explorePreview": "数据预览",
		"sys.nav.portal.exploreSavedQueries": "已保存查询与模板",
		"sys.nav.portal.exploreSharing": "结果集分享",
		"sys.nav.portal.exploreExport": "导出中心",
		"sys.nav.portal.visualization": "数据可视化",
		"sys.nav.portal.visualizationReports": "数据报表",
		"sys.nav.portal.services": "数据服务",
		"sys.nav.portal.servicesApi": "API 服务",
		"sys.nav.portal.servicesProxy": "JDBC/ODBC Proxy",
		"sys.nav.portal.servicesProducts": "数据产品",
		"sys.nav.portal.servicesTokens": "令牌与密钥",
		"sys.nav.portal.servicesQuotas": "配额与限流",
		"sys.nav.portal.iam": "权限与策略",
		"sys.nav.portal.iamClassification": "密级模型映射",
		"sys.nav.portal.iamAuthorization": "资源授权",
		"sys.nav.portal.iamSimulation": "策略模拟与评估",
		"sys.nav.portal.iamRequests": "权限申请入口",
		"sys.nav.portal.settings": "个性化与设置",
		"sys.nav.portal.settingsPreferences": "个人偏好",
		"sys.nav.portal.settingsNotifications": "通知订阅",
		"sys.nav.portal.settingsGateway": "网关策略",
	};

	function safeParseMetadata(metadata?: string) {
		if (!metadata) return undefined as undefined | { icon?: string; titleKey?: string };
		try {
			return JSON.parse(metadata) as { icon?: string; titleKey?: string };
		} catch {
			return undefined;
		}
	}

    const mapPortalMenusToMenuTree = (items: PortalMenuItem[]): MenuTree[] => {
        const i18nMap: Record<string, string> = {};
        const walk = (list: PortalMenuItem[]): MenuTree[] => {
            return list.map((item) => {
                const meta = safeParseMetadata(item.metadata);
                const titleKey = meta?.titleKey;
                const displayName =
                    item.displayName && item.displayName.trim().length > 0
                        ? item.displayName
                        : titleKey && (i18nMap[titleKey] || FALLBACK_PORTAL_ZH[titleKey])
                            ? i18nMap[titleKey] || FALLBACK_PORTAL_ZH[titleKey]
                            : item.name;
                const id = String(item.id ?? item.path ?? item.name);
                return {
                    id,
                    parentId: item.parentId ? String(item.parentId) : "",
                    name: displayName,
                    code: item.path ?? item.name,
                    order: item.sortOrder,
                    type: PermissionType.MENU,
                    children: item.children ? walk(item.children) : undefined,
                };
            });
        };
        return walk(items);
    };

    const collectMenuSecurityMap = (items: PortalMenuItem[]): Map<string, string> => {
        const map = new Map<string, string>();
        const dfs = (list: PortalMenuItem[]) => {
            list.forEach((item) => {
                const id = String(item.id ?? item.path ?? item.name);
                const meta = safeParseMetadata(item.metadata);
                const secured = item.securityLevel ?? ((meta as any)?.securityLevel as string | undefined);
                if (secured) {
                    map.set(id, secured);
                }
                if (item.children && item.children.length > 0) dfs(item.children);
            });
        };
        dfs(items);
        return map;
    };

	// 初始化表单数据
	useEffect(() => {
        if (mode === "edit" && role) {
            setFormData({
                name: role.name || "",
                description: role.description || "",
                composite: role.composite ?? false,
            });
			const rawMenu = role.attributes?.[ROLE_MENU_ATTRIBUTE_KEY];
			if (rawMenu) {
				try {
					const parsed = JSON.parse(rawMenu);
					if (Array.isArray(parsed)) {
						setSelectedMenus(new Set(parsed.map(String)));
					} else {
						setSelectedMenus(new Set());
					}
				} catch (err) {
					console.warn("Failed to parse role menu attribute", err);
					setSelectedMenus(new Set());
				}
			} else {
				setSelectedMenus(new Set());
			}
        } else {
            setFormData({
                name: "",
                description: "",
                composite: false,
            });
            setSelectedMenus(new Set());
        }
		setError("");
	}, [mode, role]);

	useEffect(() => {
		if (!open) return;
		setMenuLoading(true);
        adminApi
            .getPortalMenus()
            .then((menus) => {
                const list = menus?.active ?? [];
                setMenuTree(mapPortalMenusToMenuTree(list));
                setMenuSecurityMap(collectMenuSecurityMap(list));
            })
            .catch((err: any) => {
                console.error("Failed to load portal menus for role editing", err);
                toast.error("加载菜单数据失败，请稍后重试");
            })
            .finally(() => setMenuLoading(false));
    }, [open]);

	const handleSubmit = async () => {
		if (!formData.name.trim()) {
			setError("角色名称不能为空");
			return;
		}

		// 角色名称验证
		if (!/^[a-zA-Z0-9_-]+$/.test(formData.name)) {
			setError("角色名称只能包含字母、数字、下划线和中划线");
			return;
		}

		setLoading(true);
		setError("");

        try {
            const menuIds = Array.from(selectedMenus);
            const baseAttributes: Record<string, string> = {};
            if (menuIds.length > 0) {
                baseAttributes[ROLE_MENU_ATTRIBUTE_KEY] = JSON.stringify(menuIds);
            }
            if (mode === "create") {
                const createData: CreateRoleRequest = {
                    name: formData.name,
                    description: formData.description,
                    composite: formData.composite,
                    attributes: baseAttributes,
                };

				await KeycloakRoleService.createRole(createData);
				toast.success("角色创建成功");
			} else if (mode === "edit" && role?.name) {
				const mergedAttributes: Record<string, string> = {
					...(role.attributes ?? {}),
					...baseAttributes,
				};
				if (menuIds.length > 0) {
					mergedAttributes[ROLE_MENU_ATTRIBUTE_KEY] = JSON.stringify(menuIds);
				} else {
					delete mergedAttributes[ROLE_MENU_ATTRIBUTE_KEY];
				}
				const updateData: UpdateRoleRequest = {
					name: formData.name,
					description: formData.description,
					composite: formData.composite,
					attributes: mergedAttributes,
				};

				await KeycloakRoleService.updateRole(role.name, updateData);
				toast.success("角色更新成功");
			}

			onSuccess();
		} catch (err: any) {
			setError(err.message || "操作失败");
			console.error("Error saving role:", err);
		} finally {
			setLoading(false);
		}
	};

    const renderMenuTree = useMemo(() => {
        const LEVEL_ORDER: Record<string, number> = {
            DATA_PUBLIC: 0,
            DATA_INTERNAL: 1,
            DATA_SECRET: 2,
            DATA_TOP_SECRET: 3,
        };
        // 从旧有属性推断角色密级（字段已取消展示，保留只读用于前端校验）
        const roleMaxLevel = (role?.attributes?.dataSecurityLevel as string) || "DATA_INTERNAL";
        const roleOrder = LEVEL_ORDER[roleMaxLevel] ?? 1;
        const buildNodes = (tree: MenuTree[], depth = 0): ReactElement[] => {
            return tree.flatMap((menu) => {
                const id = menu.id || menu.code;
                if (!id) return [];
                const checked = selectedMenus.has(id);
                const menuLevel = menuSecurityMap.get(id);
                const overLimit = menuLevel ? (LEVEL_ORDER[menuLevel] ?? 0) > roleOrder : false;
                const node = (
                    <div key={id} className="space-y-1">
                        <label className="flex items-center gap-2 text-sm" style={{ paddingLeft: `${depth * 16}px` }}>
                            <Checkbox
                                checked={checked}
                                disabled={overLimit}
                                onCheckedChange={(value) => {
                                    setSelectedMenus((prev) => {
                                        const next = new Set(prev);
                                        if (value) {
                                            next.add(id);
                                        } else {
                                            next.delete(id);
                                        }
                                        return next;
                                    });
                                }}
                            />
                            <span>
                                {menu.name}
                                {menuLevel ? (
                                    <span className="ml-2 rounded border px-1 py-0.5 text-[10px] text-muted-foreground">
                                        {DATA_SECURITY_LEVEL_LABELS[menuLevel as keyof typeof DATA_SECURITY_LEVEL_LABELS] || menuLevel}
                                    </span>
                                ) : null}
                                {overLimit ? (
                                    <span className="ml-2 text-xs text-amber-600">超出角色密级，无法分配</span>
                                ) : null}
                            </span>
                        </label>
                    </div>
                );
                const children = menu.children && menu.children.length > 0 ? buildNodes(menu.children, depth + 1) : [];
                return [node, ...children];
            });
        };
        return buildNodes(menuTree);
    }, [menuTree, selectedMenus, menuSecurityMap, role?.attributes?.dataSecurityLevel]);

	const title = mode === "create" ? "创建角色" : "编辑角色";

	return (
		<Dialog open={open} onOpenChange={onCancel}>
			<DialogContent className="max-w-md">
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
				</DialogHeader>

				<div className="space-y-4">
					{error && (
						<Alert variant="destructive">
							<AlertDescription>{error}</AlertDescription>
						</Alert>
					)}

					<div className="space-y-2">
						<Label htmlFor="name">角色名称 *</Label>
						<Input
							id="name"
							value={formData.name}
							onChange={(e) => setFormData((prev) => ({ ...prev, name: e.target.value }))}
							placeholder="请输入角色名称"
							disabled={mode === "edit"} // 编辑模式下不允许修改角色名称
						/>
						{mode === "edit" && <p className="text-xs text-muted-foreground">编辑模式下不可修改角色名称</p>}
					</div>

					<div className="space-y-2">
						<Label htmlFor="description">描述</Label>
						<Textarea
							id="description"
							value={formData.description}
							onChange={(e) => setFormData((prev) => ({ ...prev, description: e.target.value }))}
							placeholder="请输入角色描述"
							rows={3}
						/>
					</div>

        {/* 角色属性不再包含“数据密级”字段，依据菜单元数据与人员密级在后端进行控制 */}

					<div className="space-y-3">
						<div className="flex items-center space-x-2">
							<Switch
								id="composite"
								checked={formData.composite}
								onCheckedChange={(checked) => setFormData((prev) => ({ ...prev, composite: checked }))}
							/>
							<Label htmlFor="composite">复合角色</Label>
						</div>
						<p className="text-xs text-muted-foreground">复合角色可以包含其他角色</p>
					</div>

					<div className="space-y-2">
						<Label>可访问菜单</Label>
						<div className="rounded-md border">
							{menuLoading ? (
								<div className="p-4 text-sm text-muted-foreground">菜单加载中...</div>
							) : menuTree.length === 0 ? (
								<div className="p-4 text-sm text-muted-foreground">暂无菜单数据。</div>
							) : (
								<ScrollArea className="h-64 p-2">
									<div className="space-y-1">{renderMenuTree}</div>
								</ScrollArea>
							)}
						</div>
						<p className="text-xs text-muted-foreground">选择后该角色在前台可访问对应菜单项。</p>
					</div>
				</div>

				<DialogFooter>
					<Button variant="outline" onClick={onCancel}>
						取消
					</Button>
					<Button onClick={handleSubmit} disabled={loading}>
						{loading ? "处理中..." : "确定"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
