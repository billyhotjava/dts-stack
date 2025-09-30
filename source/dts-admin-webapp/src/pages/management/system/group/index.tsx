import { Modal, Tree } from "antd";
import type { DataNode } from "antd/es/tree";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { Key } from "react";
import { toast } from "sonner";
import type { GroupTableRow, KeycloakGroup } from "#/keycloak";
import { KeycloakGroupService } from "@/api/services/keycloakService";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { ScrollArea } from "@/ui/scroll-area";
import { Text } from "@/ui/typography";
import GroupMembersModal from "./group-members-modal";
import GroupModal from "./group-modal";

const ORG_TYPE_LABELS: Record<string, string> = {
	COMPANY: "公司",
	DEPARTMENT: "部门",
	TEAM: "团队",
	PROJECT: "项目",
};

interface GroupTreeNode extends DataNode {
	key: string;
	group: GroupTableRow;
	children?: GroupTreeNode[];
}

export default function GroupPage() {
	const [treeData, setTreeData] = useState<GroupTreeNode[]>([]);
	const [filteredTree, setFilteredTree] = useState<GroupTreeNode[]>([]);
	const [expandedKeys, setExpandedKeys] = useState<Key[]>([]);
	const [autoExpandParent, setAutoExpandParent] = useState(true);
	const [selectedKey, setSelectedKey] = useState<Key | null>(null);
	const [selectedGroup, setSelectedGroup] = useState<GroupTableRow | null>(null);
	const [loading, setLoading] = useState(false);
	const [searchValue, setSearchValue] = useState("");

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

	const stats = useMemo(() => {
		const flattened = flattenTreeNodes(treeData);
		const totalGroups = flattened.length;
		const totalMembers = flattened.reduce((sum, node) => sum + (node.group.memberCount ?? 0), 0);
		return { totalGroups, totalMembers };
	}, [treeData]);

	const loadGroups = useCallback(async () => {
		setLoading(true);
		const currentSelectedKey = selectedKey ?? null;
		try {
			const groupsData = await KeycloakGroupService.getAllGroups();
			const allGroups = flattenGroups(groupsData);
			const memberEntries = await Promise.all(
				allGroups.map(async (group) => {
					const key = getGroupKey(group);
					if (!group.id) {
						return [key, 0] as const;
					}
					try {
						const members = await KeycloakGroupService.getGroupMembers(group.id);
						return [key, members.length] as const;
					} catch (error) {
						console.warn(`Failed to load members for group ${group.name}:`, error);
						return [key, 0] as const;
					}
				}),
			);
			const memberMap = new Map(memberEntries);
			const tree = buildGroupTree(groupsData, memberMap);
			setTreeData(tree);
			setFilteredTree(tree);
			setExpandedKeys(tree.map((node) => node.key));

			if (currentSelectedKey) {
				const found = findNodeByKey(tree, currentSelectedKey);
				if (found) {
					setSelectedKey((prev) => (prev === found.key ? prev : found.key));
					setSelectedGroup(found.group);
				} else if (tree[0]) {
					setSelectedKey(tree[0].key);
					setSelectedGroup(tree[0].group);
				} else {
					setSelectedKey(null);
					setSelectedGroup(null);
				}
			} else if (tree[0]) {
				setSelectedKey(tree[0].key);
				setSelectedGroup(tree[0].group);
			} else {
				setSelectedKey(null);
				setSelectedGroup(null);
			}
		} catch (error: any) {
			console.error("Error loading groups:", error);
			toast.error(`加载组列表失败: ${error.message || "未知错误"}`);
		} finally {
			setLoading(false);
		}
	}, [selectedKey]);

	useEffect(() => {
		if (!searchValue.trim()) {
			setFilteredTree(treeData);
			setExpandedKeys(treeData.map((node) => node.key));
			setAutoExpandParent(false);
			return;
		}
		const keyword = searchValue.trim().toLowerCase();
		const { nodes, expanded } = filterTree(treeData, keyword);
		setFilteredTree(nodes);
		setExpandedKeys(expanded);
		setAutoExpandParent(true);
	}, [searchValue, treeData]);

	const handleDelete = (group: KeycloakGroup) => {
		Modal.confirm({
			title: "确认删除",
			content: `确定要删除组 "${group.name}" 吗？此操作无法撤销。`,
			okText: "删除",
			cancelText: "取消",
			okButtonProps: { danger: true },
			async onOk() {
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

	useEffect(() => {
		loadGroups();
	}, [loadGroups]);

	return (
		<div className="grid gap-6 xl:grid-cols-[minmax(0,0.55fr)_minmax(0,1fr)]">
			<Card>
				<CardHeader className="space-y-4">
					<div className="flex flex-wrap items-start justify-between gap-4">
						<div className="space-y-1">
							<div className="flex items-center gap-2">
								<Icon icon="mdi:account-tree" size={20} />
								<CardTitle>组织机构</CardTitle>
							</div>
							<p className="text-sm text-muted-foreground">查看和管理组织结构，支持按树形层级展开。</p>
						</div>
						<Button onClick={() => setGroupModal({ open: true, mode: "create" })}>
							<Icon icon="mdi:plus" size={16} className="mr-2" />
							创建组织
						</Button>
					</div>
					<Input
						placeholder="搜索组织名称 / 路径 / 编码"
						value={searchValue}
						onChange={(event) => setSearchValue(event.target.value)}
					/>
					<div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
						<span>组织数：{stats.totalGroups}</span>
						<span>成员总数：{stats.totalMembers}</span>
					</div>
				</CardHeader>
				<CardContent className="h-[600px] p-0">
					{loading ? (
						<div className="flex h-full items-center justify-center text-sm text-muted-foreground">加载中...</div>
					) : (
						<ScrollArea className="h-full">
							<div className="p-4">
								{filteredTree.length > 0 ? (
									<Tree<GroupTreeNode>
										blockNode
										showLine={{ showLeafIcon: false }}
										treeData={filteredTree}
										expandedKeys={expandedKeys}
										autoExpandParent={autoExpandParent}
										onExpand={(keys) => {
											setExpandedKeys(keys);
											setAutoExpandParent(false);
										}}
										selectedKeys={selectedKey ? [selectedKey] : []}
										onSelect={(keys, info) => {
											const nextKey = (keys[0] as Key | undefined) ?? null;
											setSelectedKey(nextKey);
											setSelectedGroup(info.node.group ?? null);
										}}
									/>
								) : (
									<Text variant="body3" className="text-muted-foreground">
										未找到匹配的组织。
									</Text>
								)}
							</div>
						</ScrollArea>
					)}
				</CardContent>
			</Card>

			<Card className="space-y-4">
				<CardHeader className="flex flex-wrap items-center justify-between gap-3">
					<CardTitle>组织详情</CardTitle>
					<div className="flex flex-wrap gap-2">
						<Button
							variant="outline"
							size="sm"
							onClick={() =>
								selectedGroup &&
								setMembersModal({
									open: true,
									groupId: selectedGroup.id || "",
									groupName: selectedGroup.name,
								})
							}
							disabled={!selectedGroup?.id}
						>
							<Icon icon="mdi:account-group" size={16} className="mr-1" />
							查看成员
						</Button>
						<Button
							variant="outline"
							size="sm"
							onClick={() => selectedGroup && setGroupModal({ open: true, mode: "edit", group: selectedGroup })}
							disabled={!selectedGroup}
						>
							<Icon icon="solar:pen-bold-duotone" size={16} className="mr-1" />
							编辑
						</Button>
						<Button
							variant="ghost"
							size="sm"
							className="text-destructive hover:text-destructive"
							onClick={() => selectedGroup && handleDelete(selectedGroup)}
							disabled={!selectedGroup?.id}
						>
							<Icon icon="mingcute:delete-2-fill" size={16} className="mr-1" />
							删除
						</Button>
					</div>
				</CardHeader>
				<CardContent>
					{selectedGroup ? (
						<div className="space-y-4 text-sm">
							<div className="space-y-2">
								<div className="flex flex-wrap items-center gap-3">
									<span className="text-lg font-semibold">{selectedGroup.name}</span>
									<Badge variant="secondary">{selectedGroup.memberCount ?? 0} 人</Badge>
									{renderOrgTypeBadge(selectedGroup)}
								</div>
								<p className="text-xs text-muted-foreground">路径：{selectedGroup.path || "-"}</p>
							</div>
							<div className="grid gap-4 sm:grid-cols-2">
								<div className="space-y-1">
									<p className="text-xs text-muted-foreground">组织编码</p>
									<p className="font-medium text-foreground">{getAttributeValue(selectedGroup, "orgCode") || "-"}</p>
								</div>
								<div className="space-y-1">
									<p className="text-xs text-muted-foreground">组ID</p>
									<p className="font-mono text-xs text-muted-foreground">{selectedGroup.id ?? "-"}</p>
								</div>
							</div>
							<div className="space-y-1">
								<p className="text-xs text-muted-foreground">描述</p>
								<p className="text-sm text-foreground">{getAttributeValue(selectedGroup, "description") || "-"}</p>
							</div>
							{selectedGroup.subGroups && selectedGroup.subGroups.length > 0 ? (
								<div className="space-y-2">
									<p className="text-xs text-muted-foreground">下级组织</p>
									<div className="flex flex-wrap gap-2">
										{selectedGroup.subGroups.map((group) => (
											<Badge key={group.id || group.name} variant="outline">
												{group.name}
											</Badge>
										))}
									</div>
								</div>
							) : null}
						</div>
					) : (
						<Text variant="body3" className="text-muted-foreground">
							请选择左侧组织查看详情。
						</Text>
					)}
				</CardContent>
			</Card>

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

			<GroupMembersModal
				open={membersModal.open}
				groupId={membersModal.groupId}
				groupName={membersModal.groupName}
				onCancel={() => setMembersModal({ open: false, groupId: "", groupName: "" })}
				onSuccess={() => {
					setMembersModal({ open: false, groupId: "", groupName: "" });
					loadGroups();
				}}
			/>
		</div>
	);
}

function getGroupKey(group: KeycloakGroup): string {
	return group.id || group.path || group.name;
}

function flattenGroups(groups: KeycloakGroup[]): KeycloakGroup[] {
	const result: KeycloakGroup[] = [];
	const walk = (nodes: KeycloakGroup[]) => {
		for (const node of nodes) {
			result.push(node);
			if (node.subGroups?.length) {
				walk(node.subGroups);
			}
		}
	};
	walk(groups);
	return result;
}

function buildGroupTree(groups: KeycloakGroup[], memberMap: Map<string, number>): GroupTreeNode[] {
	return groups.map((group) => {
		const key = getGroupKey(group);
		const children = group.subGroups ? buildGroupTree(group.subGroups, memberMap) : [];
		const groupWithMeta: GroupTableRow = {
			...group,
			key,
			memberCount: memberMap.get(key) ?? 0,
			subGroups: children.map((child) => child.group),
		};
		const node: GroupTreeNode = {
			key,
			title: <GroupTreeTitle group={groupWithMeta} />,
			group: groupWithMeta,
		};
		if (children.length > 0) {
			node.children = children;
		}
		return node;
	});
}

function flattenTreeNodes(nodes: GroupTreeNode[]): GroupTreeNode[] {
	const result: GroupTreeNode[] = [];
	const walk = (items: GroupTreeNode[]) => {
		for (const item of items) {
			result.push(item);
			if (item.children?.length) {
				walk(item.children);
			}
		}
	};
	walk(nodes);
	return result;
}

function findNodeByKey(nodes: GroupTreeNode[], key: Key): GroupTreeNode | null {
	for (const node of nodes) {
		if (node.key === key) {
			return node;
		}
		if (node.children?.length) {
			const found = findNodeByKey(node.children, key);
			if (found) {
				return found;
			}
		}
	}
	return null;
}

function filterTree(nodes: GroupTreeNode[], keyword: string): { nodes: GroupTreeNode[]; expanded: Key[] } {
	const filtered: GroupTreeNode[] = [];
	const expandedKeys: Key[] = [];

	for (const node of nodes) {
		const { nodes: childNodes, expanded: childExpanded } = node.children
			? filterTree(node.children, keyword)
			: { nodes: [], expanded: [] };
		const match = matchGroup(node.group, keyword);
		if (match || childNodes.length > 0) {
			const nextNode: GroupTreeNode = {
				...node,
				children: childNodes.length > 0 ? childNodes : undefined,
			};
			filtered.push(nextNode);
			expandedKeys.push(...childExpanded);
			if (childNodes.length > 0 || match) {
				expandedKeys.push(node.key);
			}
		}
	}

	return { nodes: filtered, expanded: Array.from(new Set(expandedKeys)) };
}

function matchGroup(group: GroupTableRow, keyword: string) {
	const orgCode = getAttributeValue(group, "orgCode");
	const orgTypeKey = getAttributeValue(group, "orgType");
	const orgTypeLabel = ORG_TYPE_LABELS[orgTypeKey] || orgTypeKey;
	const description = getAttributeValue(group, "description");
	return [group.name, group.path ?? "", orgCode, orgTypeLabel, description]
		.filter(Boolean)
		.some((value) => value.toLowerCase().includes(keyword));
}

function getAttributeValue(group: KeycloakGroup, key: string): string {
	const value = group.attributes?.[key];
	return value && value.length > 0 ? value[0] : "";
}

function renderOrgTypeBadge(group: GroupTableRow) {
	const orgTypeKey = getAttributeValue(group, "orgType");
	if (!orgTypeKey) {
		return null;
	}
	return <Badge variant="outline">{ORG_TYPE_LABELS[orgTypeKey] || orgTypeKey}</Badge>;
}

function GroupTreeTitle({ group }: { group: GroupTableRow }) {
	const orgTypeKey = getAttributeValue(group, "orgType");
	return (
		<div className="flex w-full items-center justify-between gap-2 pr-2">
			<div className="min-w-0">
				<div className="truncate font-medium">{group.name}</div>
				<div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
					{group.path ? <span className="truncate">{group.path}</span> : null}
					{orgTypeKey ? <span>{ORG_TYPE_LABELS[orgTypeKey] || orgTypeKey}</span> : null}
				</div>
			</div>
			<Badge variant="secondary">{group.memberCount ?? 0} 人</Badge>
		</div>
	);
}
