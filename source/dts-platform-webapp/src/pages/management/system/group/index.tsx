import { Modal, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import type { GroupTableRow, KeycloakGroup } from "#/keycloak";
import { KeycloakGroupService } from "@/api/services/keycloakService";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader } from "@/ui/card";
import { Input } from "@/ui/input";
import GroupMembersModal from "./group-members-modal";
import GroupModal from "./group-modal";

const ORG_TYPE_LABELS: Record<string, string> = {
	COMPANY: "公司",
	DEPARTMENT: "部门",
	TEAM: "团队",
	PROJECT: "项目",
};

export default function GroupPage() {
	const [groups, setGroups] = useState<GroupTableRow[]>([]);
	const [loading, setLoading] = useState(false);
	const [searchValue, setSearchValue] = useState("");

	// Modal状态
	const [groupModal, setGroupModal] = useState<{
		open: boolean;
		mode: "create" | "edit";
		group?: KeycloakGroup;
	}>({ open: false, mode: "create" });

	const [membersModal, setMembersModal] = useState<{
		open: boolean;
		groupId: string;
		groupName: string;
	}>({ open: false, groupId: "", groupName: "" });

	// 加载组列表
	const loadGroups = useCallback(async () => {
		setLoading(true);
		try {
			const groupsData = await KeycloakGroupService.getAllGroups();

			// 为每个组加载成员数量
			const groupsWithMemberCount = await Promise.all(
				groupsData.map(async (group) => {
					try {
						const members = group.id ? await KeycloakGroupService.getGroupMembers(group.id) : [];
						return {
							...group,
							key: group.id || group.name,
							memberCount: members.length,
						};
					} catch (error) {
						console.warn(`Failed to load members for group ${group.name}:`, error);
						return {
							...group,
							key: group.id || group.name,
							memberCount: 0,
						};
					}
				}),
			);

			const normalizedSearch = searchValue.toLowerCase();
			const filteredData = groupsWithMemberCount.filter((group) => {
				if (!normalizedSearch) return true;
				const orgCode = group.attributes?.orgCode?.[0] || "";
				const orgTypeKey = group.attributes?.orgType?.[0] || "";
				const orgTypeLabel = ORG_TYPE_LABELS[orgTypeKey] || orgTypeKey;
				return (
					group.name.toLowerCase().includes(normalizedSearch) ||
					(group.path || "").toLowerCase().includes(normalizedSearch) ||
					orgCode.toLowerCase().includes(normalizedSearch) ||
					orgTypeLabel.toLowerCase().includes(normalizedSearch)
				);
			});

			setGroups(filteredData);
		} catch (error: any) {
			console.error("Error loading groups:", error);
			toast.error(`加载组列表失败: ${error.message || "未知错误"}`);
		} finally {
			setLoading(false);
		}
	}, [searchValue]);

	// 搜索组
	const handleSearch = () => {
		loadGroups();
	};

	// 删除组
	const handleDelete = (group: KeycloakGroup) => {
		Modal.confirm({
			title: "确认删除",
			content: `确定要删除组 "${group.name}" 吗？此操作无法撤销。`,
			okText: "删除",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				try {
					if (!group.id) throw new Error("组ID不存在");
					await KeycloakGroupService.deleteGroup(group.id);
					toast.success("组删除成功");
					loadGroups();
				} catch (error: any) {
					console.error("Error deleting group:", error);
					toast.error(`删除组失败: ${error.message || "未知错误"}`);
				}
			},
		});
	};

	// 表格列定义
	const columns: ColumnsType<GroupTableRow> = [
		{
			title: "组信息",
			dataIndex: "name",
			width: 250,
			render: (name: string, record) => (
				<div className="flex items-center">
					<div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary font-medium">
						{name.charAt(0).toUpperCase()}
					</div>
					<div className="ml-3">
						<div className="font-medium">{name}</div>
						<div className="text-sm text-muted-foreground">{record.path || "-"}</div>
					</div>
				</div>
			),
		},
		{
			title: "成员数量",
			dataIndex: "memberCount",
			align: "center",
			width: 100,
			render: (count: number) => <Badge variant="secondary">{count} 人</Badge>,
		},
		{
			title: "组织编码",
			dataIndex: "orgCode",
			width: 160,
			render: (_: unknown, record) => record.attributes?.orgCode?.[0] || "-",
		},
		{
			title: "组织类型",
			dataIndex: "orgType",
			width: 120,
			render: (_: unknown, record) => {
				const typeKey = record.attributes?.orgType?.[0] || "";
				if (!typeKey) return "-";
				return ORG_TYPE_LABELS[typeKey] || typeKey;
			},
		},
		{
			title: "子组数量",
			dataIndex: "subGroups",
			align: "center",
			width: 100,
			render: (subGroups?: KeycloakGroup[]) => <Badge variant="outline">{subGroups?.length || 0} 个</Badge>,
		},
		{
			title: "组ID",
			dataIndex: "id",
			width: 120,
			render: (id: string) => (
				<span className="font-mono text-xs text-muted-foreground">{id ? `${id.substring(0, 8)}...` : "-"}</span>
			),
		},
		{
			title: "操作",
			key: "operation",
			align: "center",
			width: 180,
			fixed: "right",
			render: (_, record) => (
				<div className="flex items-center justify-center gap-1">
					<Button
						variant="ghost"
						size="sm"
						title="查看成员"
						onClick={() =>
							setMembersModal({
								open: true,
								groupId: record.id!,
								groupName: record.name,
							})
						}
					>
						<Icon icon="mdi:account-group" size={16} />
					</Button>
					<Button
						variant="ghost"
						size="sm"
						title="编辑组"
						onClick={() => setGroupModal({ open: true, mode: "edit", group: record })}
					>
						<Icon icon="solar:pen-bold-duotone" size={16} />
					</Button>
					<Button
						variant="ghost"
						size="sm"
						title="删除组"
						onClick={() => handleDelete(record)}
						className="text-red-600 hover:text-red-700"
					>
						<Icon icon="mingcute:delete-2-fill" size={16} />
					</Button>
				</div>
			),
		},
	];

	// 初始化加载
	useEffect(() => {
		loadGroups();
	}, [loadGroups]);

	// 搜索变化时重新加载
	useEffect(() => {
		const timeoutId = setTimeout(() => {
			loadGroups();
		}, 300);
		return () => clearTimeout(timeoutId);
	}, [loadGroups]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<div className="flex items-center justify-between">
						<div>
							<h2 className="text-2xl font-bold">组管理</h2>
							<p className="text-muted-foreground">管理Keycloak用户组</p>
						</div>
						<Button onClick={() => setGroupModal({ open: true, mode: "create" })}>
							<Icon icon="mdi:plus" size={16} className="mr-2" />
							新建组
						</Button>
					</div>
				</CardHeader>
				<CardContent>
					{/* 搜索栏 */}
					<div className="flex items-center gap-2 mb-4">
						<Input
							placeholder="搜索组名称或路径..."
							value={searchValue}
							onChange={(e) => setSearchValue(e.target.value)}
							className="max-w-sm"
						/>
						<Button onClick={handleSearch}>
							<Icon icon="mdi:magnify" size={16} className="mr-2" />
							搜索
						</Button>
						{searchValue && (
							<Button variant="outline" onClick={() => setSearchValue("")}>
								清除
							</Button>
						)}
					</div>

					{/* 组表格 */}
					<Table
						rowKey="key"
						columns={columns}
						dataSource={groups}
						loading={loading}
						scroll={{ x: 900 }}
						pagination={{
							pageSize: 10,
							showSizeChanger: true,
							showQuickJumper: true,
							showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
						}}
					/>
				</CardContent>
			</Card>

			{/* 组创建/编辑Modal */}
			<GroupModal
				open={groupModal.open}
				mode={groupModal.mode}
				group={groupModal.group}
				onCancel={() => setGroupModal({ open: false, mode: "create" })}
				onSuccess={() => {
					setGroupModal({ open: false, mode: "create" });
					loadGroups();
				}}
			/>

			{/* 组成员管理Modal */}
			<GroupMembersModal
				open={membersModal.open}
				groupId={membersModal.groupId}
				groupName={membersModal.groupName}
				onCancel={() => setMembersModal({ open: false, groupId: "", groupName: "" })}
				onSuccess={() => {
					loadGroups(); // 重新加载成员数量
				}}
			/>
		</div>
	);
}
