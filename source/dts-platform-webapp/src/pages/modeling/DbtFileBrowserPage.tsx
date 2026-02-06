import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Editor from "@monaco-editor/react";
import { toast } from "sonner";
import {
	Button,
	Dropdown,
	Input,
	Modal,
	Space,
	Spin,
	Tooltip,
	Tree,
	Typography,
	Upload,
} from "antd";
import {
	FileOutlined,
	FileTextOutlined,
	FolderOutlined,
	FolderOpenOutlined,
	PlusOutlined,
	DeleteOutlined,
	SaveOutlined,
	ReloadOutlined,
	UploadOutlined,
	EditOutlined,
	ExclamationCircleOutlined,
	InboxOutlined,
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
	uploadDbtZip,
	triggerDbtRun,
} from "@/api/platformApi";

const { Text } = Typography;
const { Dragger } = Upload;

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

interface ImportResult {
	totalFiles: number;
	newFiles: number;
	overwrittenFiles: number;
	directories: string[];
	skippedFiles: string[];
	importedFiles: string[];
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

function detectLanguage(path: string): string {
	if (path.endsWith(".sql")) return "sql";
	if (path.endsWith(".yml") || path.endsWith(".yaml")) return "yaml";
	if (path.endsWith(".md")) return "markdown";
	return "plaintext";
}

// ── Component ─────────────────────────────────────────────────

export default function DbtFileBrowserPage() {
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

	// Upload modal
	const [uploadOpen, setUploadOpen] = useState(false);
	const [uploading, setUploading] = useState(false);

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

	useEffect(() => {
		loadTree();
	}, [loadTree]);

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

	// ── Upload ZIP ────────────────────────────────────────────

	const handleUpload = useCallback(
		async (file: File) => {
			setUploading(true);
			try {
				const formData = new FormData();
				formData.append("file", file);
				const result = (await uploadDbtZip(formData)) as any as ImportResult;
				setUploadOpen(false);
				const msg = `导入完成: ${result.totalFiles} 个文件 (新增 ${result.newFiles}, 覆盖 ${result.overwrittenFiles})`;
				if (result.skippedFiles?.length > 0) {
					Modal.info({
						title: "导入结果",
						width: 560,
						content: (
							<div>
								<p>{msg}</p>
								<p style={{ marginTop: 8 }}>
									<strong>跳过的文件:</strong>
								</p>
								<ul style={{ maxHeight: 200, overflow: "auto", fontSize: 12 }}>
									{result.skippedFiles.map((f, i) => (
										<li key={i}>{f}</li>
									))}
								</ul>
							</div>
						),
					});
				} else {
					toast.success(msg);
				}
				loadTree();
			} catch (err: any) {
				toast.error("上传失败: " + (err?.message || "未知错误"));
			} finally {
				setUploading(false);
			}
		},
		[loadTree],
	);

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

	const handleDbtRun = useCallback(async () => {
		try {
			await triggerDbtRun({ models: "all", target: "dev" });
			toast.success("dbt 运行已触发");
		} catch (err: any) {
			toast.error("触发失败: " + (err?.message || "未知错误"));
		}
	}, []);

	// ── Render ────────────────────────────────────────────────

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
				<Button icon={<UploadOutlined />} onClick={() => setUploadOpen(true)}>
					上传模型包
				</Button>
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
					<Button icon={<RocketOutlined />} onClick={handleDbtRun}>
						运行 dbt
					</Button>
				</Tooltip>
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
										onSelect={(keys, info) => {
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
									暂无文件，请上传模型包
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
							<span style={{ fontSize: 12 }}>或上传 dbt 模型包 (.zip)</span>
						</div>
					)}
				</div>
			</div>

			{/* Upload Modal */}
			<Modal
				title="上传 dbt 模型包"
				open={uploadOpen}
				onCancel={() => !uploading && setUploadOpen(false)}
				footer={null}
				width={480}
			>
				<div style={{ marginBottom: 12 }}>
					<Text type="secondary">
						上传标准 dbt 项目结构的 ZIP 包，包含 models/、macros/、seeds/ 等目录。
						同名文件将被覆盖，系统配置文件不受影响。
					</Text>
				</div>
				<Dragger
					accept=".zip"
					multiple={false}
					showUploadList={false}
					disabled={uploading}
					customRequest={({ file }) => handleUpload(file as File)}
				>
					<p className="ant-upload-drag-icon">
						<InboxOutlined />
					</p>
					<p className="ant-upload-text">{uploading ? "正在导入..." : "点击或拖拽 ZIP 文件到此处"}</p>
					<p className="ant-upload-hint">仅支持 .zip 格式，最大 50 MB</p>
				</Dragger>
			</Modal>

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
		</div>
	);
}
