import { useEffect, useMemo, useState } from "react";
import { Button, Drawer, Empty, Input, Modal, Popconfirm, Select, Space, Tag, Tree, message } from "antd";
import {
	analyticsApi,
	type DashboardListItem,
	type DataPortalContentType,
	type ScreenListItem,
} from "../../api/analyticsApi";
import {
	buildDataPortalTree,
	type DataPortalBinding,
	type DataPortalDirectory,
	type DataPortalTreeNode,
} from "./dataPortalTree";

type Props = {
	open: boolean;
	onClose: () => void;
	directories: DataPortalDirectory[];
	bindings: DataPortalBinding[];
	screens: ScreenListItem[];
	dashboards: DashboardListItem[];
	onChanged: () => Promise<void>;
};

type DirectoryDialog = {
	mode: "create" | "edit";
	directoryId?: number;
	name: string;
	parentId: number | null;
};

function allBranchKeys(nodes: DataPortalTreeNode[]): string[] {
	const keys: string[] = [];
	for (const node of nodes) {
		if (node.nodeType === "DIRECTORY") keys.push(node.key);
		if (node.children) keys.push(...allBranchKeys(node.children));
	}
	return keys;
}

function findNode(nodes: DataPortalTreeNode[], key: string): DataPortalTreeNode | null {
	for (const node of nodes) {
		if (node.key === key) return node;
		const child = node.children ? findNode(node.children, key) : null;
		if (child) return child;
	}
	return null;
}

function directoryLabel(directory: DataPortalDirectory, byId: Map<number, DataPortalDirectory>): string {
	const names = [directory.name];
	let cursor = directory.parent_id;
	const visited = new Set<number>([directory.id]);
	while (cursor != null && !visited.has(cursor)) {
		visited.add(cursor);
		const parent = byId.get(cursor);
		if (!parent) break;
		names.unshift(parent.name);
		cursor = parent.parent_id;
	}
	return names.join(" / ");
}

function descendantIds(directories: DataPortalDirectory[], directoryId: number): Set<number> {
	const result = new Set<number>([directoryId]);
	let changed = true;
	while (changed) {
		changed = false;
		for (const directory of directories) {
			if (directory.parent_id != null && result.has(directory.parent_id) && !result.has(directory.id)) {
				result.add(directory.id);
				changed = true;
			}
		}
	}
	return result;
}

export function DataPortalEditorDrawer({
	open,
	onClose,
	directories,
	bindings,
	screens,
	dashboards,
	onChanged,
}: Props) {
	const [selectedKey, setSelectedKey] = useState<string>("");
	const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
	const [directoryDialog, setDirectoryDialog] = useState<DirectoryDialog | null>(null);
	const [contentDialogOpen, setContentDialogOpen] = useState(false);
	const [contentType, setContentType] = useState<DataPortalContentType>("SCREEN");
	const [contentId, setContentId] = useState<number | undefined>();
	const [busy, setBusy] = useState(false);

	const tree = useMemo(
		() => buildDataPortalTree(directories, bindings, screens, dashboards, "", { includeUnavailable: true }),
		[bindings, dashboards, directories, screens],
	);
	const selected = useMemo(() => findNode(tree, selectedKey), [selectedKey, tree]);
	const directoryById = useMemo(() => new Map(directories.map((item) => [item.id, item])), [directories]);

	useEffect(() => {
		if (!open) return;
		setExpandedKeys(allBranchKeys(tree));
		if (selectedKey && !findNode(tree, selectedKey)) setSelectedKey("");
	}, [open, selectedKey, tree]);

	const parentExclusions = directoryDialog?.directoryId
		? descendantIds(directories, directoryDialog.directoryId)
		: new Set<number>();
	const parentOptions = [
		{ value: 0, label: "一级目录" },
		...directories
			.filter((directory) => !parentExclusions.has(directory.id))
			.map((directory) => ({ value: directory.id, label: directoryLabel(directory, directoryById) })),
	];
	const candidateContent = contentType === "SCREEN" ? screens : dashboards;
	const boundIds = new Set(
		bindings
			.filter((binding) => binding.directory_id === selected?.directoryId && binding.content_type === contentType)
			.map((binding) => String(binding.content_id)),
	);
	const contentOptions = candidateContent
		.filter((item) => !boundIds.has(String(item.id)))
		.map((item) => ({ value: Number(item.id), label: item.name?.trim() || `${contentType === "SCREEN" ? "大屏" : "看板"} ${item.id}` }));

	const runMutation = async (operation: () => Promise<unknown>, success: string) => {
		setBusy(true);
		try {
			await operation();
			await onChanged();
			message.success(success);
			return true;
		} catch {
			message.error("操作失败，请检查目录层级或内容发布状态后重试。");
			return false;
		} finally {
			setBusy(false);
		}
	};

	const saveDirectory = async () => {
		if (!directoryDialog?.name.trim()) {
			message.warning("请输入目录名称。");
			return;
		}
		const body = {
			name: directoryDialog.name.trim(),
			parent_id: directoryDialog.parentId,
		};
		const operation = directoryDialog.mode === "create"
			? () => analyticsApi.createDataPortalDirectory(body)
			: () => analyticsApi.updateDataPortalDirectory(directoryDialog.directoryId as number, body);
		const saved = await runMutation(operation, directoryDialog.mode === "create" ? "目录已创建" : "目录已更新");
		if (saved) setDirectoryDialog(null);
	};

	const bindContent = async () => {
		if (selected?.nodeType !== "DIRECTORY" || selected.directoryId == null || contentId == null) {
			message.warning("请先选择目录和要添加的内容。");
			return;
		}
		const saved = await runMutation(
			() => analyticsApi.createDataPortalBinding({
				directory_id: selected.directoryId as number,
				content_type: contentType,
				content_id: contentId,
			}),
			"内容已加入门户",
		);
		if (saved) {
			setContentDialogOpen(false);
			setContentId(undefined);
		}
	};

	return (
		<Drawer
			open={open}
			onClose={onClose}
			title="门户编排"
			aria-label="门户编排"
			width={760}
			styles={{ body: { padding: 16 }, wrapper: { maxWidth: "100vw" } }}
			extra={<Button type="primary" onClick={() => setDirectoryDialog({ mode: "create", name: "", parentId: null })}>新建一级目录</Button>}
		>
			<div className="mb-4 rounded-lg bg-bg-layout px-3 py-2 text-sm text-text-secondary">
				先建立月份、业务主题等多级目录，再把已发布的大屏或看板加入目标目录。门户编排不会改变原内容的发布与权限配置。
			</div>
			<div className="grid min-h-[480px] grid-cols-1 gap-4 md:grid-cols-[minmax(260px,0.9fr)_minmax(300px,1.1fr)]">
				<section className="min-w-0 rounded-lg border border-solid border-border p-3">
					<div className="mb-3 flex items-center justify-between">
						<strong>门户菜单</strong>
						<span className="text-xs text-text-secondary">{directories.length} 个目录 · {bindings.length} 项内容</span>
					</div>
					{tree.length > 0 ? (
						<Tree
							blockNode
							showLine={{ showLeafIcon: false }}
							treeData={tree}
							expandedKeys={expandedKeys}
							selectedKeys={selectedKey ? [selectedKey] : []}
							onExpand={setExpandedKeys}
							onSelect={(keys) => setSelectedKey(String(keys[0] ?? ""))}
							titleRender={(node) => {
								const portalNode = node as DataPortalTreeNode;
								return (
									<span className={portalNode.availability === "UNAVAILABLE" ? "text-text-disabled" : ""}>
										{portalNode.title}
										{portalNode.nodeType === "CONTENT" ? (
											<Tag className="ml-2" color={portalNode.availability === "UNAVAILABLE" ? "default" : portalNode.contentType === "SCREEN" ? "blue" : "purple"}>
												{portalNode.availability === "UNAVAILABLE" ? "不可用" : portalNode.contentType === "SCREEN" ? "大屏" : "看板"}
											</Tag>
										) : null}
									</span>
								);
							}}
						/>
					) : (
						<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未建立门户目录" />
					)}
				</section>

				<section className="min-w-0 rounded-lg border border-solid border-border p-4">
					{!selected ? (
						<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择左侧目录或内容后进行维护" />
					) : selected.nodeType === "DIRECTORY" ? (
						<div>
							<div className="text-xs text-text-secondary">当前目录</div>
							<h3 className="mb-4 mt-1 text-lg">{selected.title}</h3>
							<Space wrap>
								<Button onClick={() => setDirectoryDialog({ mode: "create", name: "", parentId: selected.directoryId as number })}>新建下级目录</Button>
								<Button onClick={() => {
									const directory = directoryById.get(selected.directoryId as number);
									if (directory) setDirectoryDialog({ mode: "edit", directoryId: directory.id, name: directory.name, parentId: directory.parent_id });
								}}>重命名或移动</Button>
								<Button type="primary" onClick={() => {
									setContentType("SCREEN");
									setContentId(undefined);
									setContentDialogOpen(true);
								}}>添加大屏或看板</Button>
								<Popconfirm
									title="确认删除该目录？"
									description="仅空目录可以删除。"
									onConfirm={async () => {
										const deleted = await runMutation(() => analyticsApi.deleteDataPortalDirectory(selected.directoryId as number), "目录已删除");
										if (deleted) setSelectedKey("");
									}}
								>
									<Button danger>删除目录</Button>
								</Popconfirm>
							</Space>
							<p className="mt-4 text-sm text-text-secondary">下级目录和已绑定内容会按编排顺序显示在门户左侧菜单中。</p>
						</div>
					) : (
						<div>
							<div className="text-xs text-text-secondary">门户内容</div>
							<h3 className="mb-2 mt-1 text-lg">{selected.title}</h3>
							<Tag color={selected.availability === "UNAVAILABLE" ? "default" : selected.contentType === "SCREEN" ? "blue" : "purple"}>
								{selected.availability === "UNAVAILABLE" ? "原内容不可用" : selected.contentType === "SCREEN" ? "已发布大屏" : "已发布看板"}
							</Tag>
							<p className="mt-4 text-sm text-text-secondary">移除只会取消门户中的菜单绑定，不会删除原大屏或看板。</p>
							<Popconfirm title="确认从门户移除？" onConfirm={async () => {
								const removed = await runMutation(() => analyticsApi.deleteDataPortalBinding(selected.bindingId as number), "内容已从门户移除");
								if (removed) setSelectedKey("");
							}}>
								<Button danger>从门户移除</Button>
							</Popconfirm>
						</div>
					)}
				</section>
			</div>

			<Modal
				open={directoryDialog !== null}
				title={directoryDialog?.mode === "edit" ? "维护门户目录" : "新建门户目录"}
				onCancel={() => setDirectoryDialog(null)}
				onOk={() => void saveDirectory()}
				confirmLoading={busy}
				okText="保存"
			>
				<label className="mb-1 block text-sm text-text-secondary" htmlFor="data-portal-directory-name">目录名称</label>
				<Input
					id="data-portal-directory-name"
					maxLength={64}
					value={directoryDialog?.name ?? ""}
					onChange={(event) => setDirectoryDialog((current) => current ? { ...current, name: event.target.value } : current)}
					placeholder="例如：一月、项目管理"
				/>
				<label className="mb-1 mt-4 block text-sm text-text-secondary">上级目录</label>
				<Select
					aria-label="上级目录"
					className="w-full"
					value={directoryDialog?.parentId ?? 0}
					options={parentOptions}
					onChange={(value) => setDirectoryDialog((current) => current ? { ...current, parentId: value === 0 ? null : value } : current)}
				/>
			</Modal>

			<Modal
				open={contentDialogOpen}
				title={`向“${selected?.title ?? "当前目录"}”添加内容`}
				onCancel={() => setContentDialogOpen(false)}
				onOk={() => void bindContent()}
				confirmLoading={busy}
				okButtonProps={{ disabled: contentId == null }}
				okText="加入门户"
			>
				<label className="mb-1 block text-sm text-text-secondary">内容类型</label>
				<Select
					aria-label="内容类型"
					className="w-full"
					value={contentType}
					options={[{ value: "SCREEN", label: "大屏" }, { value: "DASHBOARD", label: "看板" }]}
					onChange={(value) => {
						setContentType(value);
						setContentId(undefined);
					}}
				/>
				<label className="mb-1 mt-4 block text-sm text-text-secondary">已发布内容</label>
				<Select
					aria-label="已发布内容"
					showSearch
					className="w-full"
					value={contentId}
					options={contentOptions}
					optionFilterProp="label"
					placeholder={contentOptions.length > 0 ? "请选择内容" : "暂无可加入的已发布内容"}
					onChange={setContentId}
				/>
			</Modal>
		</Drawer>
	);
}
