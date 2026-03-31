import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Editor from "@monaco-editor/react";
import { toast } from "sonner";
import { registerDbtLanguage, DBT_SQL_LANGUAGE_ID } from "./dbt-monaco-lang";
import {
	Button,
	Card,
	Dropdown,
	Form,
	Input,
	Modal,
	Select,
	Spin,
	Tooltip,
	Tag,
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

interface DbtSyncArtifactStatus {
	synced?: boolean;
	lastSyncAt?: string;
}

interface DbtSyncStatus {
	latestRun?: DbtRunSummary | null;
	manifest?: DbtSyncArtifactStatus | null;
	runResults?: DbtSyncArtifactStatus | null;
}

// ── Helpers ───────────────────────────────────────────────────

/** Map file extension → Monaco language id (overrides backend's basic detection). */
function resolveEditorLanguage(path: string, backendLang?: string): string {
	const lower = path.toLowerCase();
	if (lower.endsWith(".sql")) return DBT_SQL_LANGUAGE_ID;
	if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
	if (lower.endsWith(".md")) return "markdown";
	if (lower.endsWith(".csv") || lower.endsWith(".tsv")) return "plaintext";
	if (lower.endsWith(".json")) return "json";
	if (lower.endsWith(".py")) return "python";
	if (lower.endsWith(".sh") || lower.endsWith(".bash")) return "shell";
	if (lower.endsWith(".toml")) return "ini";
	if (lower.endsWith(".txt") || lower.endsWith(".cfg") || lower.endsWith(".conf")) return "plaintext";
	return backendLang || "plaintext";
}

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

function filterTreeByKeyword(node: FileNode, keyword: string): FileNode | null {
	const lower = keyword.toLowerCase();
	if (node.type === "file") {
		return node.name.toLowerCase().includes(lower) ? node : null;
	}
	const filteredChildren = (node.children ?? [])
		.map((child) => filterTreeByKeyword(child, keyword))
		.filter((child): child is FileNode => child !== null);
	if (filteredChildren.length > 0 || node.name.toLowerCase().includes(lower)) {
		return { ...node, children: filteredChildren };
	}
	return null;
}

function collectAllFilePaths(node: FileNode): string[] {
	if (node.type === "file") return [node.path];
	return (node.children ?? []).flatMap(collectAllFilePaths);
}

// ── Component ─────────────────────────────────────────────────

export default function DbtFileBrowserPage() {
	const router = useRouter();
	// Tree state
	const [treeData, setTreeData] = useState<FileNode | null>(null);
	const [treeLoading, setTreeLoading] = useState(false);
	const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
	const [selectedKey, setSelectedKey] = useState<string>("");
	const [searchKeyword, setSearchKeyword] = useState("");
	const [checkedKeys, setCheckedKeys] = useState<string[]>([]);

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

	const [batchDeleting, setBatchDeleting] = useState(false);

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
		} catch {
			// global interceptor handles the error toast
		} finally {
			setTreeLoading(false);
		}
	}, []);

	const handleBatchDelete = useCallback(async () => {
		if (checkedKeys.length === 0) return;
		Modal.confirm({
			title: `确认删除 ${checkedKeys.length} 个文件？`,
			icon: <ExclamationCircleOutlined />,
			content: "删除后无法恢复，请确认选中的文件不再需要。",
			okText: "确认删除",
			okType: "danger",
			cancelText: "取消",
			onOk: async () => {
				setBatchDeleting(true);
				let successCount = 0;
				let failCount = 0;
				for (const path of checkedKeys) {
					try {
						await deleteDbtFile(path);
						successCount++;
					} catch {
						failCount++;
					}
				}
				setBatchDeleting(false);
				setCheckedKeys([]);
				if (failCount === 0) {
					toast.success(`已删除 ${successCount} 个文件`);
				} else {
					toast.warning(`删除完成：成功 ${successCount}，失败 ${failCount}`);
				}
				if (activeFile && checkedKeys.includes(activeFile.path)) {
					setActiveFile(null);
					setEditorValue("");
					setDirty(false);
					setSelectedKey("");
				}
				await loadTree();
			},
		});
	}, [checkedKeys, activeFile, loadTree]);

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
			} catch {
				// global interceptor handles the error toast
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
		} catch {
			// global interceptor handles the error toast
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
		} catch {
			// global interceptor handles the error toast
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
					} catch {
						// global interceptor handles the error toast
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
		} catch {
			// global interceptor handles the error toast
		}
	}, [renameName, renameOldPath, activeFile?.path, loadFile, loadTree]);

	// ── Tree Data ─────────────────────────────────────────────

	const filteredTree = useMemo(() => {
		if (!treeData) return null;
		if (!searchKeyword.trim()) return treeData;
		return filterTreeByKeyword(treeData, searchKeyword.trim());
	}, [treeData, searchKeyword]);

	const antTreeData = useMemo(
		() => (filteredTree?.children ?? []).map(fileNodeToTreeData),
		[filteredTree],
	);

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
		<div className="space-y-6">
			<Card
				title="DBT 文件工作区"
				extra={
					<div className="flex flex-wrap items-center gap-2">
						<Button className="rounded-2xl" onClick={() => router.push("/modeling/sql")}>
							回到逻辑建模
						</Button>
						<Button className="rounded-2xl" type="link" onClick={() => router.push("/foundation/data-sources")}>
							去 ODS 接入
						</Button>
					</div>
				}
			/>

			<Card
				title="文件浏览与编辑"
				extra={
					<div className="flex flex-wrap items-center gap-2">
						<Dropdown
							menu={{
								items: [
									{
										key: "file",
										label: "新建文件",
										icon: <FileOutlined />,
										onClick: () => {
											setCreateType("file");
											setCreatePath(selectedKey && !selectedKey.includes(".") ? `${selectedKey}/` : "models/");
											setCreateOpen(true);
										},
									},
									{
										key: "dir",
										label: "新建目录",
										icon: <FolderOutlined />,
										onClick: () => {
											setCreateType("directory");
											setCreatePath(selectedKey && !selectedKey.includes(".") ? `${selectedKey}/` : "models/");
											setCreateOpen(true);
										},
									},
								],
							}}
						>
							<Button className="rounded-2xl" icon={<PlusOutlined />}>
								新建
							</Button>
						</Dropdown>
						<Button className="rounded-2xl" icon={<ReloadOutlined />} onClick={loadTree}>
							刷新
						</Button>
						{activeFile && dirty ? (
							<Button className="rounded-2xl" type="primary" icon={<SaveOutlined />} loading={saving} onClick={saveFile}>
								保存
							</Button>
						) : null}
						<Tooltip title="触发 dbt run">
							<Button className="rounded-2xl" icon={<RocketOutlined />} onClick={openDbtRunConfig}>
								运行 dbt
							</Button>
						</Tooltip>
						<Button className="rounded-2xl" size="small" onClick={() => handleQuickBuild("compile")} loading={buildSubmitting === "compile"}>
							编译
						</Button>
						<Button className="rounded-2xl" size="small" onClick={() => handleQuickBuild("test")} loading={buildSubmitting === "test"}>
							测试
						</Button>
						<Button className="rounded-2xl" size="small" onClick={() => handleQuickBuild("docs")} loading={buildSubmitting === "docs"}>
							文档
						</Button>
					</div>
				}
				styles={{ body: { padding: 0 } }}
			>
				<div className="border-b border-border/70 bg-muted/25 px-5 py-4 text-xs text-muted-foreground">
					<div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
						<div className="leading-6">
							<span className="font-semibold text-foreground">工作区说明：</span>
							在逻辑建模页完成 ODS 一键生成后，这里负责模型微调与运行验证。
							<span className="ml-2 inline-flex items-center gap-2">
								<Tag color={latestStatusColor}>{latestStatus}</Tag>
								{latestRun?.command ? <span>{latestRun.command}</span> : null}
								{latestRun?.generatedAt ? <span>@ {new Date(latestRun.generatedAt).toLocaleString()}</span> : null}
							</span>
						</div>
						<div className="flex flex-wrap items-center gap-2">
							{syncStatus?.manifest ? <Tag color={syncStatus.manifest.synced ? "success" : "error"}>manifest</Tag> : null}
							{syncStatus?.runResults ? <Tag color={syncStatus.runResults.synced ? "success" : "error"}>run_results</Tag> : null}
						</div>
					</div>
				</div>

				<div className="flex min-h-[720px] flex-col xl:flex-row">
					<div className="w-64 min-w-[256px] max-w-[280px] border-r border-border bg-card p-4 overflow-x-auto overflow-y-auto [&_.ant-tree-title]:block [&_.ant-tree-title]:whitespace-nowrap [&_.ant-tree-switcher]:flex-shrink-0">
						<div className="mb-3 text-xs font-bold uppercase text-muted-foreground">项目目录</div>
						<Input
							size="small"
							placeholder="搜索文件..."
							value={searchKeyword}
							onChange={(e) => setSearchKeyword(e.target.value)}
							allowClear
							className="mb-2"
						/>
						<div className="mb-3 rounded-md border border-border bg-background px-2 py-2">
							<div className="mb-2 flex items-center justify-between gap-2">
								<div className="min-w-0">
									<Text className="text-xs text-muted-foreground">已选 {checkedKeys.length} 项</Text>
								</div>
								{checkedKeys.length > 0 ? (
									<Button type="link" size="small" className="px-0" onClick={() => setCheckedKeys([])}>
										清空选择
									</Button>
								) : null}
							</div>
							<div className="flex flex-wrap gap-2">
								<Button
									size="small"
									onClick={() => {
										const allPaths = filteredTree ? collectAllFilePaths(filteredTree) : [];
										setCheckedKeys(allPaths);
									}}
								>
									全选当前结果
								</Button>
								<Button
									size="small"
									danger
									icon={<DeleteOutlined />}
									disabled={checkedKeys.length === 0 || batchDeleting}
									onClick={handleBatchDelete}
								>
									{batchDeleting ? "删除中..." : "删除所选"}
								</Button>
							</div>
						</div>
						<Spin spinning={treeLoading} size="small">
							{antTreeData.length > 0 ? (
								<Dropdown menu={{ items: contextMenuItems }} trigger={["contextMenu"]}>
									<div>
										<Tree
											checkable
											showIcon
											blockNode
											treeData={antTreeData}
											expandedKeys={expandedKeys}
											selectedKeys={selectedKey ? [selectedKey] : []}
											checkedKeys={checkedKeys}
											onExpand={(keys) => setExpandedKeys(keys)}
											onSelect={(_keys, info) => {
												const node = (info.node as any)?.data as FileNode | undefined;
												if (node?.type === "file") {
													loadFile(node.path);
												}
											}}
											onCheck={(keys) => {
												const checked = (Array.isArray(keys) ? keys : keys.checked).map(String);
												setCheckedKeys(checked.filter((k) => !k.endsWith("/")));
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
									<div className="px-4 py-10 text-center text-sm text-muted-foreground">
										{searchKeyword.trim() ? "无匹配结果" : "暂无文件，请先新建目录或文件。"}
									</div>
								)
							)}
						</Spin>
					</div>

					<div className="flex min-w-0 flex-1 flex-col bg-card">
						{activeFile ? (
							<>
								<div className="flex flex-wrap items-center gap-2 border-b border-border/70 bg-muted/15 px-4 py-3 text-xs">
									<Text className="font-mono text-[13px] text-foreground">{activeFile.path}</Text>
									<div className="flex-1" />
									{activeFile.readOnly ? <Tag>只读</Tag> : null}
									{dirty ? <Tag color="warning">已修改</Tag> : <Tag color="success">已同步</Tag>}
									<Tag>{resolveEditorLanguage(activeFile.path, activeFile.language).toUpperCase()}</Tag>
								</div>
								<div className="relative flex-1 overflow-hidden">
									{fileLoading ? (
										<div className="absolute inset-0 z-10 flex items-center justify-center bg-white/55">
											<Spin size="small" />
										</div>
									) : null}
									<Editor
										height="100%"
										language={resolveEditorLanguage(activeFile.path, activeFile.language)}
										theme="dbt-light"
										value={editorValue}
										beforeMount={(monaco) => registerDbtLanguage(monaco)}
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
											renderWhitespace: "trailing",
											renderControlCharacters: true,
											unicodeHighlight: {
												ambiguousCharacters: true,
												invisibleCharacters: true,
												nonBasicASCII: true,
											},
											bracketPairColorization: { enabled: true },
											guides: {
												bracketPairs: true,
												indentation: true,
											},
											matchBrackets: "always",
											folding: true,
											foldingHighlight: true,
										}}
									/>
								</div>
							</>
						) : (
							<div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center text-sm text-muted-foreground">
								<FileTextOutlined style={{ fontSize: 48, opacity: 0.3 }} />
								<div>选择一个文件开始编辑。</div>
							</div>
						)}
					</div>
				</div>
			</Card>

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
						label="选择器"
						rules={[{ required: true, message: "请输入模型选择器" }]}
						extra="例如：all、tag:erp、model:dwd_order"
					>
						<Input placeholder="all" />
					</Form.Item>
					<Form.Item name="target" label="目标" rules={[{ required: true, message: "请输入 target" }]}>
						<Input placeholder="dev" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
