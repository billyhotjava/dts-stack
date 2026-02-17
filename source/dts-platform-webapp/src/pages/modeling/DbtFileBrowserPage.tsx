import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Editor from "@monaco-editor/react";
import { toast } from "sonner";
import {
	Button,
	Dropdown,
	Form,
	Input,
	Modal,
	Select,
	Spin,
	Tooltip,
	Tree,
	Typography,
} from "antd";
import {
	FileOutlined,
	FileTextOutlined,
	FolderOutlined,
	PlusOutlined,
	DeleteOutlined,
	SaveOutlined,
	ReloadOutlined,
	EditOutlined,
	ExclamationCircleOutlined,
	RocketOutlined,
} from "@ant-design/icons";
import type { DataNode } from "antd/es/tree";
import {
	getDbtFileTree,
	getDbtFileContent,
	saveDbtFileContent,
	createDbtFile,
	deleteDbtFile,
	renameDbtFile,
	triggerDbtRun,
	getDbtSyncStatus,
	triggerDbtCompile,
	triggerDbtTest,
	triggerDbtDocs,
} from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";

const { Text } = Typography;

// ── Types ─────────────────────────────────────────────────────

interface FileNode {
	name: string;
	path: string;
	type: "file" | "directory";
	size: number;
	lastModified?: string;
	readOnly?: boolean;
	children?: FileNode[];
}

interface FileContent {
	path: string;
	content: string;
	language: string;
	size: number;
	readOnly: boolean;
}

interface DbtRunFailure {
	uniqueId?: string;
	name?: string;
	message?: string;
}

interface DbtRunSummary {
	present?: boolean;
	status?: string;
	command?: string;
	generatedAt?: string;
	failed?: number;
	failures?: DbtRunFailure[];
}

interface DbtSyncStatus {
	latestRun?: DbtRunSummary | null;
}

// ── Helpers ───────────────────────────────────────────────────

function fileIcon(node: FileNode) {
	if (node.type === "directory") return <FolderOutlined />;
	const name = node.name.toLowerCase();
	if (name.endsWith(".sql")) return <FileTextOutlined style={{ color: "#1677ff" }} />;
	if (name.endsWith(".yml") || name.endsWith(".yaml")) return <FileTextOutlined style={{ color: "#d48806" }} />;
	if (name.endsWith(".csv")) return <FileTextOutlined style={{ color: "#389e0d" }} />;
	return <FileOutlined />;
}

function fileNodeToTreeData(node: FileNode): DataNode {
	return {
		key: node.path || "__root__",
		title: node.name,
		icon: fileIcon(node),
		isLeaf: node.type === "file",
		children: node.children?.map(fileNodeToTreeData),
		// Stash the full node for context menu / other operations
		...(({ children: _, ...rest }) => ({ data: rest }))(node),
	} as DataNode & { data: FileNode };
}

// ── Component ─────────────────────────────────────────────────

export default function DbtFileBrowserPage() {
	const router = useRouter();
	// Tree state
	const [treeData, setTreeData] = useState<FileNode | null>(null);
	const [treeLoading, setTreeLoading] = useState(false);
	const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
	const [selectedKey, setSelectedKey] = useState<string>("");

	// Editor state
	const [activeFile, setActiveFile] = useState<FileContent | null>(null);
	const [editorValue, setEditorValue] = useState("");
	const [dirty, setDirty] = useState(false);
	const [saving, setSaving] = useState(false);
	const [fileLoading, setFileLoading] = useState(false);

	// Create file/dir modal
	const [createOpen, setCreateOpen] = useState(false);
	const [createType, setCreateType] = useState<"file" | "directory">("file");
	const [createPath, setCreatePath] = useState("");

	// Rename modal
	const [renameOpen, setRenameOpen] = useState(false);
	const [renameOldPath, setRenameOldPath] = useState("");
	const [renameName, setRenameName] = useState("");

	// Right click context
	const [contextNode, setContextNode] = useState<FileNode | null>(null);
	const [runConfigOpen, setRunConfigOpen] = useState(false);
	const [runSubmitting, setRunSubmitting] = useState(false);
	const [buildSubmitting, setBuildSubmitting] = useState<"compile" | "test" | "docs" | null>(null);
	const [syncStatus, setSyncStatus] = useState<DbtSyncStatus | null>(null);
	const [runForm] = Form.useForm();

	const editorRef = useRef<any>(null);

	// ── Load Tree ─────────────────────────────────────────────

	const loadTree = useCallback(async () => {
		setTreeLoading(true);
		try {
			const data = (await getDbtFileTree()) as any as FileNode;
			setTreeData(data);
			// Auto expand root + first level
			const keys: string[] = ["__root__"];
			if (data?.children) {
				for (const child of data.children) {
					if (child.type === "directory") keys.push(child.path);
				}
			}
			setExpandedKeys((prev) => {
				const merged = new Set([...prev, ...keys]);
				return Array.from(merged);
			});
		} catch (err: any) {
			toast.error("加载目录树失败: " + (err?.message || "未知错误"));
		} finally {
			setTreeLoading(false);
		}
	}, []);

	const loadSyncStatus = useCallback(async () => {
		try {
			const data = (await getDbtSyncStatus()) as DbtSyncStatus;
			setSyncStatus(data || null);
		} catch {
			setSyncStatus(null);
		}
	}, []);

	useEffect(() => {
		loadTree();
		loadSyncStatus();
	}, [loadTree, loadSyncStatus]);

	// ── Load File Content ─────────────────────────────────────

	const loadFile = useCallback(
		async (path: string) => {
			if (dirty) {
				const confirmed = await new Promise<boolean>((resolve) =>
					Modal.confirm({
						title: "未保存的更改",
						content: `文件 "${activeFile?.path}" 有未保存的更改，是否放弃？`,
						okText: "放弃更改",
						cancelText: "取消",
						onOk: () => resolve(true),
						onCancel: () => resolve(false),
					}),
				);
				if (!confirmed) return;
			}
			setFileLoading(true);
			try {
				const content = (await getDbtFileContent(path)) as any as FileContent;
				setActiveFile(content);
				setEditorValue(content.content);
				setDirty(false);
				setSelectedKey(path);
			} catch (err: any) {
				toast.error("读取文件失败: " + (err?.message || "未知错误"));
			} finally {
				setFileLoading(false);
			}
		},
		[dirty, activeFile?.path],
	);

	// ── Save File ─────────────────────────────────────────────

	const saveFile = useCallback(async () => {
		if (!activeFile || !dirty) return;
		setSaving(true);
		try {
			await saveDbtFileContent({ path: activeFile.path, content: editorValue });
			setDirty(false);
			toast.success("已保存");
		} catch (err: any) {
			toast.error("保存失败: " + (err?.message || "未知错误"));
		} finally {
			setSaving(false);
		}
	}, [activeFile, dirty, editorValue]);

	// Ctrl+S keyboard shortcut
	useEffect(() => {
		const handler = (e: KeyboardEvent) => {
			if ((e.ctrlKey || e.metaKey) && e.key === "s") {
				e.preventDefault();
				saveFile();
			}
		};
		window.addEventListener("keydown", handler);
		return () => window.removeEventListener("keydown", handler);
	}, [saveFile]);

	// ── Create File/Dir ───────────────────────────────────────

	const handleCreate = useCallback(async () => {
		if (!createPath.trim()) {
			toast.error("请输入路径");
			return;
		}
		try {
			const content = createType === "file" ? "" : undefined;
			await createDbtFile({ path: createPath.trim(), type: createType, content });
			toast.success(`已创建${createType === "directory" ? "目录" : "文件"}: ${createPath}`);
			setCreateOpen(false);
			setCreatePath("");
			loadTree();
		} catch (err: any) {
			toast.error("创建失败: " + (err?.message || "未知错误"));
		}
	}, [createPath, createType, loadTree]);

	// ── Delete ────────────────────────────────────────────────

	const handleDelete = useCallback(
		(path: string) => {
			Modal.confirm({
				title: "确认删除",
				icon: <ExclamationCircleOutlined />,
				content: `确定要删除 "${path}" 吗？此操作不可恢复。`,
				okText: "删除",
				okButtonProps: { danger: true },
				cancelText: "取消",
				onOk: async () => {
					try {
						await deleteDbtFile(path);
						toast.success("已删除: " + path);
						if (activeFile?.path === path) {
							setActiveFile(null);
							setEditorValue("");
							setDirty(false);
						}
						loadTree();
					} catch (err: any) {
						toast.error("删除失败: " + (err?.message || "未知错误"));
					}
				},
			});
		},
		[activeFile?.path, loadTree],
	);

	// ── Rename ────────────────────────────────────────────────

	const handleRename = useCallback(async () => {
		if (!renameName.trim() || !renameOldPath) return;
		const parts = renameOldPath.split("/");
		parts[parts.length - 1] = renameName.trim();
		const newPath = parts.join("/");
		try {
			await renameDbtFile({ oldPath: renameOldPath, newPath });
			toast.success("已重命名");
			setRenameOpen(false);
			if (activeFile?.path === renameOldPath) {
				loadFile(newPath);
			}
			loadTree();
		} catch (err: any) {
			toast.error("重命名失败: " + (err?.message || "未知错误"));
		}
	}, [renameName, renameOldPath, activeFile?.path, loadFile, loadTree]);

	// ── Tree Data ─────────────────────────────────────────────

	const antTreeData = useMemo(() => {
		if (!treeData) return [];
		return [fileNodeToTreeData(treeData)];
	}, [treeData]);

	// ── Context Menu ──────────────────────────────────────────

	const contextMenuItems = useMemo(() => {
		if (!contextNode) return [];
		const items: any[] = [];
		if (contextNode.type === "directory") {
			items.push({
				key: "newFile",
				label: "新建文件",
				icon: <PlusOutlined />,
				onClick: () => {
					setCreateType("file");
					setCreatePath(contextNode.path ? contextNode.path + "/" : "");
					setCreateOpen(true);
				},
			});
			items.push({
				key: "newDir",
				label: "新建目录",
				icon: <FolderOutlined />,
				onClick: () => {
					setCreateType("directory");
					setCreatePath(contextNode.path ? contextNode.path + "/" : "");
					setCreateOpen(true);
				},
			});
		}
		if (!contextNode.readOnly && contextNode.path) {
			items.push({
				key: "rename",
				label: "重命名",
				icon: <EditOutlined />,
				onClick: () => {
					setRenameOldPath(contextNode.path);
					setRenameName(contextNode.name);
					setRenameOpen(true);
				},
			});
			items.push({
				key: "delete",
				label: "删除",
				icon: <DeleteOutlined />,
				danger: true,
				onClick: () => handleDelete(contextNode.path),
			});
		}
		return items;
	}, [contextNode, handleDelete]);

	// ── Trigger dbt run ───────────────────────────────────────

	const openDbtRunConfig = useCallback(() => {
		runForm.setFieldsValue({
			models: "all",
			target: "dev",
			operation: "run",
		});
		setRunConfigOpen(true);
	}, [runForm]);

	const handleDbtRun = useCallback(async () => {
		setRunSubmitting(true);
		try {
			const values = await runForm.validateFields();
			await triggerDbtRun({
				models: String(values.models || "all").trim(),
				target: String(values.target || "dev").trim(),
				operation: String(values.operation || "run").trim(),
			});
			toast.success(`dbt ${values.operation || "run"} 已触发`);
			setRunConfigOpen(false);
			await loadSyncStatus();
		} catch (err: any) {
			if (!err?.errorFields) {
				toast.error("触发失败: " + (err?.message || "未知错误"));
			}
		} finally {
			setRunSubmitting(false);
		}
	}, [runForm, loadSyncStatus]);

	const handleQuickBuild = useCallback(
		async (operation: "compile" | "test" | "docs") => {
			setBuildSubmitting(operation);
			try {
				const payload = { models: "all", target: "dev" };
				if (operation === "compile") {
					await triggerDbtCompile(payload);
				} else if (operation === "test") {
					await triggerDbtTest(payload);
				} else {
					await triggerDbtDocs(payload);
				}
				toast.success(`dbt ${operation} 已触发`);
				await loadSyncStatus();
			} catch (err: any) {
				toast.error("触发失败: " + (err?.message || "未知错误"));
			} finally {
				setBuildSubmitting(null);
			}
		},
		[loadSyncStatus],
	);

	// ── Render ────────────────────────────────────────────────

	const latestRun = syncStatus?.latestRun || null;
	const latestStatus = String(latestRun?.status || "UNKNOWN").toUpperCase();
	const latestStatusColor = latestStatus === "SUCCESS" ? "green" : latestStatus === "FAILED" ? "red" : "gold";

	return (
		<div style={{ display: "flex", flexDirection: "column", height: "100%", overflow: "hidden" }}>
			{/* Toolbar */}
			<div
				style={{
					display: "flex",
					alignItems: "center",
					gap: 8,
					padding: "8px 12px",
					borderBottom: "1px solid #f0f0f0",
					flexShrink: 0,
				}}
			>
				<Dropdown
					menu={{
						items: [
							{
								key: "file",
								label: "新建文件",
								icon: <FileOutlined />,
								onClick: () => {
									setCreateType("file");
									setCreatePath(selectedKey && !selectedKey.includes(".") ? selectedKey + "/" : "models/");
									setCreateOpen(true);
								},
							},
							{
								key: "dir",
								label: "新建目录",
								icon: <FolderOutlined />,
								onClick: () => {
									setCreateType("directory");
									setCreatePath(selectedKey && !selectedKey.includes(".") ? selectedKey + "/" : "models/");
									setCreateOpen(true);
								},
							},
						],
					}}
				>
					<Button icon={<PlusOutlined />}>新建</Button>
				</Dropdown>
				<Button icon={<ReloadOutlined />} onClick={loadTree}>
					刷新
				</Button>
				<div style={{ flex: 1 }} />
				{activeFile && dirty && (
					<Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={saveFile}>
						保存
					</Button>
				)}
				<Tooltip title="触发 dbt run">
					<Button icon={<RocketOutlined />} onClick={openDbtRunConfig}>
						运行 dbt
					</Button>
				</Tooltip>
				<Button
					size="small"
					onClick={() => handleQuickBuild("compile")}
					loading={buildSubmitting === "compile"}
				>
					编译
				</Button>
				<Button
					size="small"
					onClick={() => handleQuickBuild("test")}
					loading={buildSubmitting === "test"}
				>
					测试
				</Button>
				<Button
					size="small"
					onClick={() => handleQuickBuild("docs")}
					loading={buildSubmitting === "docs"}
				>
					文档
				</Button>
				</div>

				<div
					style={{
						display: "flex",
						alignItems: "center",
						justifyContent: "space-between",
						gap: 8,
						padding: "8px 12px",
						borderBottom: "1px solid #f0f0f0",
						background: "#fafafa",
						fontSize: 12,
						flexShrink: 0,
					}}
				>
					<div>
						<strong>下一步建议:</strong> 在逻辑建模页完成 ODS 一键生成后，这里用于模型微调与运行验证，不再提供 ZIP 导入。
						<span style={{ marginLeft: 12 }}>
							最近构建: <Text style={{ color: latestStatusColor }}>{latestStatus}</Text>
							{latestRun?.command ? ` (${latestRun.command})` : ""}
							{latestRun?.generatedAt ? ` @ ${new Date(latestRun.generatedAt).toLocaleString()}` : ""}
						</span>
					</div>
					<div style={{ display: "flex", alignItems: "center", gap: 6 }}>
						<Button size="small" onClick={() => router.push("/modeling/sql")}>
							回到逻辑建模
						</Button>
						<Button size="small" type="link" onClick={() => router.push("/foundation/data-sources")}>
							去 ODS 接入
						</Button>
					</div>
				</div>

				{/* Main content */}
				<div style={{ display: "flex", flex: 1, overflow: "hidden" }}>
				{/* File tree */}
				<div
					style={{
						width: 260,
						minWidth: 200,
						borderRight: "1px solid #f0f0f0",
						overflow: "auto",
						padding: "8px 0",
						flexShrink: 0,
					}}
				>
					<Spin spinning={treeLoading} size="small">
						{antTreeData.length > 0 ? (
							<Dropdown
								menu={{ items: contextMenuItems }}
								trigger={["contextMenu"]}
							>
								<div>
									<Tree
										showIcon
										blockNode
										treeData={antTreeData}
										expandedKeys={expandedKeys}
										selectedKeys={selectedKey ? [selectedKey] : []}
										onExpand={(keys) => setExpandedKeys(keys)}
										onSelect={(_keys, info) => {
											const node = (info.node as any)?.data as FileNode | undefined;
											if (node?.type === "file") {
												loadFile(node.path);
											}
										}}
										onRightClick={({ node }) => {
											const data = (node as any)?.data as FileNode | undefined;
											if (data) setContextNode(data);
										}}
									/>
								</div>
							</Dropdown>
						) : (
							!treeLoading && (
								<div style={{ padding: 16, textAlign: "center", color: "#999" }}>
									暂无文件，请先新建目录或文件
								</div>
							)
						)}
					</Spin>
				</div>

				{/* Editor area */}
				<div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden" }}>
					{activeFile ? (
						<>
							{/* File path header */}
							<div
								style={{
									padding: "6px 12px",
									borderBottom: "1px solid #f0f0f0",
									display: "flex",
									alignItems: "center",
									gap: 8,
									flexShrink: 0,
									background: "#fafafa",
								}}
							>
								<Text style={{ fontSize: 13, fontFamily: "monospace" }}>{activeFile.path}</Text>
								<div style={{ flex: 1 }} />
								{activeFile.readOnly && (
									<Text type="secondary" style={{ fontSize: 12 }}>
										只读
									</Text>
								)}
								{dirty && (
									<Text type="warning" style={{ fontSize: 12 }}>
										已修改
									</Text>
								)}
								<Text type="secondary" style={{ fontSize: 12 }}>
									{activeFile.language.toUpperCase()}
								</Text>
							</div>
							{/* Monaco editor */}
							<div style={{ flex: 1, overflow: "hidden" }}>
								<Spin spinning={fileLoading} size="small" style={{ height: "100%" }}>
									<Editor
										height="100%"
										language={activeFile.language}
										value={editorValue}
										onChange={(val) => {
											setEditorValue(val || "");
											setDirty(val !== activeFile.content);
										}}
										onMount={(editor) => {
											editorRef.current = editor;
										}}
										options={{
											readOnly: activeFile.readOnly,
											minimap: { enabled: false },
											fontSize: 13,
											lineNumbers: "on",
											scrollBeyondLastLine: false,
											wordWrap: "on",
											tabSize: 2,
											renderWhitespace: "boundary",
										}}
									/>
								</Spin>
							</div>
						</>
					) : (
						<div
							style={{
								flex: 1,
								display: "flex",
								alignItems: "center",
								justifyContent: "center",
								color: "#999",
								flexDirection: "column",
								gap: 12,
							}}
						>
							<FileTextOutlined style={{ fontSize: 48, opacity: 0.3 }} />
							<span>选择一个文件开始编辑</span>
						</div>
					)}
				</div>
			</div>

			{/* Create File/Dir Modal */}
			<Modal
				title={createType === "directory" ? "新建目录" : "新建文件"}
				open={createOpen}
				onOk={handleCreate}
				onCancel={() => setCreateOpen(false)}
				okText="创建"
				cancelText="取消"
			>
				<div style={{ marginBottom: 8 }}>
					<Text type="secondary">路径 (相对于 dbt 项目根目录):</Text>
				</div>
				<Input
					value={createPath}
					onChange={(e) => setCreatePath(e.target.value)}
					placeholder={createType === "directory" ? "models/dwd/新目录" : "models/dwd/new_model.sql"}
					onPressEnter={handleCreate}
				/>
			</Modal>

			{/* Rename Modal */}
			<Modal
				title="重命名"
				open={renameOpen}
				onOk={handleRename}
				onCancel={() => setRenameOpen(false)}
				okText="确认"
				cancelText="取消"
			>
				<div style={{ marginBottom: 8 }}>
					<Text type="secondary">原路径: {renameOldPath}</Text>
				</div>
				<Input
					value={renameName}
					onChange={(e) => setRenameName(e.target.value)}
					placeholder="新名称"
					onPressEnter={handleRename}
				/>
			</Modal>

			<Modal
				title="触发 dbt 任务"
				open={runConfigOpen}
				onOk={handleDbtRun}
				onCancel={() => setRunConfigOpen(false)}
				okText="触发"
				cancelText="取消"
				confirmLoading={runSubmitting}
			>
				<Form form={runForm} layout="vertical" disabled={runSubmitting}>
					<Form.Item name="operation" label="操作" rules={[{ required: true, message: "请选择操作" }]}>
						<Select
							options={[
								{ label: "run", value: "run" },
								{ label: "test", value: "test" },
								{ label: "compile", value: "compile" },
								{ label: "docs", value: "docs" },
							]}
						/>
					</Form.Item>
					<Form.Item
						name="models"
						label="Selector"
						rules={[{ required: true, message: "请输入模型选择器" }]}
						extra="例如：all、tag:erp、model:dwd_order"
					>
						<Input placeholder="all" />
					</Form.Item>
					<Form.Item name="target" label="Target" rules={[{ required: true, message: "请输入 target" }]}>
						<Input placeholder="dev" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
