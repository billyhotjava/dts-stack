import {
	Alert,
	Button,
	Card,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Spin,
	Switch,
	Tag,
	Tooltip,
	Tree,
	TreeSelect,
} from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import {
	type CatalogTagCategoryDto,
	type CatalogTagCategoryRequest,
	type CatalogTagDto,
	type CatalogTagRequest,
	createCatalogTag,
	createTagCategory,
	deleteCatalogTag,
	deleteTagCategory,
	installBuiltinCatalogTags,
	listCatalogTags,
	listTagCategories,
	updateCatalogTag,
	updateTagCategory,
} from "@/api/catalogTagsApi";
import { type CompactColumns, CompactTable } from "@/components/table";

const DEFAULT_PAGE_SIZE = 10;
const COLOR_PALETTE = ["#1677ff", "#13c2c2", "#52c41a", "#faad14", "#fa541c", "#722ed1", "#eb2f96", "#8c8c8c"] as const;
const COLOR_PALETTE_SET = new Set<string>(COLOR_PALETTE);
const BUILTIN_CATEGORY_DELETE_REASON = "预置分类由系统维护，不可删除；如需停止使用，请将其停用";
const BUILTIN_TAG_DELETE_REASON = "预置标签由系统维护，不可删除；如需停止使用，请将其停用";

function displayColor(color?: string | null): string {
	const normalized = String(color || "")
		.trim()
		.toLowerCase();
	return COLOR_PALETTE_SET.has(normalized) ? normalized : COLOR_PALETTE[0];
}

type CategoryFormValue = {
	code: string;
	name: string;
	parentId?: string;
	sortOrder?: number;
	enabled?: boolean;
	description?: string;
};

type TagFormValue = {
	code: string;
	name: string;
	color?: string;
	enabled?: boolean;
	description?: string;
};

type EditorKind = "category" | "tag";

function getErrorMessage(error: unknown, fallback: string): string {
	if (error instanceof Error && error.message) return error.message;
	if (error && typeof error === "object") {
		const response = (error as { response?: { data?: unknown } }).response;
		const data = response?.data;
		if (typeof data === "string" && data.trim()) return data;
		if (data && typeof data === "object") {
			const detail = (data as { detail?: unknown; message?: unknown }).detail;
			const message = (data as { message?: unknown }).message;
			if (typeof detail === "string" && detail.trim()) return detail;
			if (typeof message === "string" && message.trim()) return message;
		}
	}
	return fallback;
}

function flattenCategories(categories: CatalogTagCategoryDto[]): CatalogTagCategoryDto[] {
	const result: CatalogTagCategoryDto[] = [];
	for (const category of categories) {
		result.push(category);
		result.push(...flattenCategories(category.children || []));
	}
	return result;
}

function toTreeData(categories: CatalogTagCategoryDto[]): Array<{
	key: string;
	title: string;
	children?: ReturnType<typeof toTreeData>;
}> {
	return categories.map((category) => ({
		key: category.id,
		title: `${category.name}（${category.tagCount}）${category.builtin ? " · 预置" : ""}`,
		children: category.children?.length ? toTreeData(category.children) : undefined,
	}));
}

type CategoryParentTreeNode = {
	value: string;
	title: string;
	children?: CategoryParentTreeNode[];
};

function collectCategoryBranchIds(category: CatalogTagCategoryDto, result = new Set<string>()): Set<string> {
	result.add(category.id);
	for (const child of category.children || []) collectCategoryBranchIds(child, result);
	return result;
}

function toParentTreeData(
	categories: CatalogTagCategoryDto[],
	excludedIds: ReadonlySet<string>,
): CategoryParentTreeNode[] {
	return categories
		.filter((category) => !excludedIds.has(category.id))
		.map((category) => ({
			value: category.id,
			title: `${category.name}${category.builtin ? " · 预置" : ""}`,
			children: category.children?.length ? toParentTreeData(category.children, excludedIds) : undefined,
		}));
}

type TagManagementTabProps = {
	canManage: boolean;
	onViewAssets?: (tag: CatalogTagDto) => void;
	onAssociateAssets?: (tag: CatalogTagDto) => void;
};

export function TagManagementTab({ canManage, onViewAssets, onAssociateAssets }: TagManagementTabProps) {
	const [categories, setCategories] = useState<CatalogTagCategoryDto[]>([]);
	const [selectedCategoryId, setSelectedCategoryId] = useState("");
	const [tags, setTags] = useState<CatalogTagDto[]>([]);
	const [page, setPage] = useState(1);
	const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
	const [total, setTotal] = useState(0);
	const [keyword, setKeyword] = useState("");
	const [loadingCategories, setLoadingCategories] = useState(true);
	const [loadingTags, setLoadingTags] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editorKind, setEditorKind] = useState<EditorKind | null>(null);
	const [editingCategory, setEditingCategory] = useState<CatalogTagCategoryDto | null>(null);
	const [editingTag, setEditingTag] = useState<CatalogTagDto | null>(null);
	const [categoryForm] = Form.useForm<CategoryFormValue>();
	const [tagForm] = Form.useForm<TagFormValue>();
	const tagRequestSequence = useRef(0);

	const flatCategories = useMemo(() => flattenCategories(categories), [categories]);
	const selectedCategory = flatCategories.find((category) => category.id === selectedCategoryId);
	const parentCategoryTreeData = useMemo(() => {
		const excludedIds = editingCategory ? collectCategoryBranchIds(editingCategory) : new Set<string>();
		return toParentTreeData(categories, excludedIds);
	}, [categories, editingCategory]);

	const loadCategories = useCallback(async (preferredId?: string) => {
		setLoadingCategories(true);
		try {
			const rows = (await listTagCategories()) || [];
			setCategories(rows);
			const flattened = flattenCategories(rows);
			setSelectedCategoryId((current) => {
				const nextPreferred = preferredId || current;
				return flattened.some((row) => row.id === nextPreferred) ? nextPreferred : flattened[0]?.id || "";
			});
		} catch (error: unknown) {
			const message = getErrorMessage(error, "标签分类目录加载失败");
			toast.error(message);
			setCategories([]);
			setSelectedCategoryId("");
		} finally {
			setLoadingCategories(false);
		}
	}, []);

	useEffect(() => {
		void loadCategories();
	}, [loadCategories]);

	const loadTags = useCallback(async () => {
		const sequence = ++tagRequestSequence.current;
		if (!selectedCategoryId) {
			setTags([]);
			setTotal(0);
			setLoadingTags(false);
			return;
		}
		setLoadingTags(true);
		try {
			const result = await listCatalogTags({
				categoryId: selectedCategoryId,
				keyword: keyword.trim() || undefined,
				page: page - 1,
				size: pageSize,
			});
			if (sequence !== tagRequestSequence.current) return;
			setTags(result?.content || []);
			setTotal(result?.total || 0);
		} catch (error: unknown) {
			if (sequence !== tagRequestSequence.current) return;
			const message = getErrorMessage(error, "业务数据标签加载失败");
			toast.error(message);
			setTags([]);
			setTotal(0);
		} finally {
			if (sequence === tagRequestSequence.current) setLoadingTags(false);
		}
	}, [keyword, page, pageSize, selectedCategoryId]);

	useEffect(() => {
		void loadTags();
		return () => {
			tagRequestSequence.current++;
		};
	}, [loadTags]);

	const openCategoryEditor = (category?: CatalogTagCategoryDto) => {
		setEditingCategory(category || null);
		categoryForm.resetFields();
		categoryForm.setFieldsValue(
			category
				? {
						code: category.code,
						name: category.name,
						parentId: category.parentId || undefined,
						sortOrder: category.sortOrder,
						enabled: category.enabled,
						description: category.description || "",
					}
				: { sortOrder: 0, enabled: true },
		);
		setEditorKind("category");
	};

	const openTagEditor = (tag?: CatalogTagDto) => {
		setEditingTag(tag || null);
		tagForm.resetFields();
		tagForm.setFieldsValue(
			tag
				? {
						code: tag.code,
						name: tag.name,
						color: tag.color || COLOR_PALETTE[0],
						enabled: tag.enabled,
						description: tag.description || "",
					}
				: { color: COLOR_PALETTE[0], enabled: true },
		);
		setEditorKind("tag");
	};

	const saveCategory = async () => {
		if (!canManage) return;
		try {
			const value = await categoryForm.validateFields();
			const builtin = editingCategory?.builtin === true;
			const parentId = builtin ? editingCategory?.parentId || null : value.parentId || null;
			if (parentId && editingCategory && collectCategoryBranchIds(editingCategory).has(parentId)) {
				toast.error("上级分类不能选择当前分类或其子分类");
				return;
			}
			const request: CatalogTagCategoryRequest = {
				code: builtin ? editingCategory.code : value.code?.trim(),
				name: builtin ? editingCategory.name : value.name?.trim(),
				parentId,
				sortOrder: builtin ? editingCategory.sortOrder : Number(value.sortOrder || 0),
				enabled: value.enabled !== false,
				description: builtin ? editingCategory.description || null : value.description?.trim() || null,
			};
			setSaving(true);
			const saved = editingCategory
				? await updateTagCategory(editingCategory.id, request)
				: await createTagCategory(request);
			setEditorKind(null);
			await loadCategories(saved.id);
			toast.success(editingCategory ? "标签分类已更新" : "标签分类已创建");
		} catch (error: unknown) {
			const message = getErrorMessage(error, "标签分类保存失败");
			toast.error(message);
		} finally {
			setSaving(false);
		}
	};

	const saveTag = async () => {
		if (!canManage || !selectedCategoryId) return;
		try {
			const value = await tagForm.validateFields();
			const request: CatalogTagRequest = {
				categoryId: editingTag?.builtin ? editingTag.categoryId : selectedCategoryId,
				code: editingTag?.builtin ? editingTag.code : value.code?.trim(),
				name: editingTag?.builtin ? editingTag.name : value.name?.trim(),
				color: value.color || COLOR_PALETTE[0],
				enabled: value.enabled !== false,
				description: editingTag?.builtin ? editingTag.description || null : value.description?.trim() || null,
			};
			setSaving(true);
			if (editingTag) {
				await updateCatalogTag(editingTag.id, request);
			} else {
				await createCatalogTag(request);
			}
			setEditorKind(null);
			await Promise.all([loadTags(), loadCategories(selectedCategoryId)]);
			toast.success(editingTag ? "业务数据标签已更新" : "业务数据标签已创建");
		} catch (error: unknown) {
			const message = getErrorMessage(error, "业务数据标签保存失败");
			toast.error(message);
		} finally {
			setSaving(false);
		}
	};

	const confirmDeleteTag = (tag: CatalogTagDto) => {
		if (!canManage || tag.builtin) return;
		const assigned = tag.usageCount > 0;
		Modal.confirm({
			title: assigned ? "确认删除已使用的标签" : "确认删除标签",
			content: assigned
				? `“${tag.name}”已用于 ${tag.usageCount} 个资产。删除后将一并移除这些资产的标签关系，是否继续？`
				: `确认删除“${tag.name}”吗？`,
			okText: assigned ? "删除标签及关系" : "删除",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				try {
					await deleteCatalogTag(tag.id, assigned);
					await Promise.all([loadTags(), loadCategories(selectedCategoryId)]);
					toast.success("业务数据标签已删除");
				} catch (error: unknown) {
					const message = getErrorMessage(error, "业务数据标签删除失败");
					toast.error(message);
					throw error;
				}
			},
		});
	};

	const confirmDeleteCategory = () => {
		if (!canManage || !selectedCategory || selectedCategory.builtin) return;
		Modal.confirm({
			title: "确认删除标签分类",
			content: `确认删除“${selectedCategory.name}”吗？仅未包含标签和子分类的分类可以删除。`,
			okText: "删除",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				try {
					await deleteTagCategory(selectedCategory.id);
					await loadCategories();
					toast.success("标签分类已删除");
				} catch (error: unknown) {
					const message = getErrorMessage(error, "标签分类删除失败");
					toast.error(message);
					throw error;
				}
			},
		});
	};

	const installBuiltins = async () => {
		if (!canManage) return;
		setSaving(true);
		try {
			const report = await installBuiltinCatalogTags();
			if (report.conflicts?.length) {
				toast.error(`预置标签未安装：发现 ${report.conflicts.length} 个编码冲突`);
				return;
			}
			await loadCategories(selectedCategoryId);
			await loadTags();
			toast.success(report.applied ? `预置标签已安装，共新增 ${report.tagsCreated} 个标签` : "预置标签已是最新版本");
		} catch (error: unknown) {
			toast.error(getErrorMessage(error, "预置标签安装失败"));
		} finally {
			setSaving(false);
		}
	};

	const columns: CompactColumns<CatalogTagDto> = [
		{
			title: "标签名称",
			dataIndex: "name",
			key: "name",
			render: (_, tag) => (
				<Space size={6}>
					<span
						aria-hidden="true"
						style={{
							display: "inline-block",
							width: 8,
							height: 8,
							borderRadius: "50%",
							background: displayColor(tag.color),
						}}
					/>
					<span>{tag.name}</span>
					{tag.builtin ? <Tag>预置</Tag> : null}
					{!tag.enabled ? <Tag>已停用</Tag> : null}
				</Space>
			),
		},
		{ title: "标签编码", dataIndex: "code", key: "code" },
		{
			title: "使用情况",
			dataIndex: "usageCount",
			key: "usageCount",
			render: (count) => `${Number(count || 0)} 个资产`,
		},
		{
			title: "业务说明",
			dataIndex: "description",
			key: "description",
			render: (description) => description || "—",
		},
		{
			title: "操作",
			key: "actions",
			dataIndex: "actions",
			render: (_: unknown, tag: CatalogTagDto) => (
				<Space size={4}>
					{onViewAssets ? (
						<Button type="link" size="small" onClick={() => onViewAssets(tag)}>
							查看资产
						</Button>
					) : null}
					{canManage ? (
						<>
							{onAssociateAssets ? (
								<Button type="link" size="small" onClick={() => onAssociateAssets(tag)}>
									关联资产
								</Button>
							) : null}
							<Button type="link" size="small" aria-label={`编辑标签 ${tag.name}`} onClick={() => openTagEditor(tag)}>
								编辑
							</Button>
							<Tooltip title={tag.builtin ? BUILTIN_TAG_DELETE_REASON : undefined}>
								<span>
									<Button
										type="link"
										size="small"
										danger
										aria-label={`删除标签 ${tag.name}`}
										disabled={tag.builtin}
										title={tag.builtin ? BUILTIN_TAG_DELETE_REASON : undefined}
										onClick={() => confirmDeleteTag(tag)}
									>
										删除
									</Button>
								</span>
							</Tooltip>
						</>
					) : null}
				</Space>
			),
		},
	];

	return (
		<div className="space-y-4">
			{!canManage ? (
				<Alert
					type="info"
					showIcon
					message="标签目录为只读状态"
					description="您可以查看标签目录、标签状态和使用情况；维护分类、标签及预置包需要标签治理权限。"
				/>
			) : null}
			<div className="grid grid-cols-1 gap-4 lg:grid-cols-[280px_minmax(0,1fr)]">
				<Card
					title="标签分类"
					extra={
						canManage ? (
							<Button type="link" size="small" aria-label="新建标签分类" onClick={() => openCategoryEditor()}>
								新建
							</Button>
						) : null
					}
				>
					<Spin spinning={loadingCategories}>
						<Tree
							blockNode
							selectedKeys={selectedCategoryId ? [selectedCategoryId] : []}
							treeData={toTreeData(categories)}
							onSelect={(keys) => {
								const id = String(keys[0] || "");
								if (!id) return;
								setSelectedCategoryId(id);
								setPage(1);
							}}
						/>
					</Spin>
					{canManage && selectedCategory ? (
						<div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
							<Button size="small" onClick={() => openCategoryEditor(selectedCategory)}>
								编辑分类
							</Button>
							<Tooltip title={selectedCategory.builtin ? BUILTIN_CATEGORY_DELETE_REASON : undefined}>
								<span>
									<Button
										size="small"
										danger
										disabled={selectedCategory.builtin}
										title={selectedCategory.builtin ? BUILTIN_CATEGORY_DELETE_REASON : undefined}
										onClick={confirmDeleteCategory}
									>
										删除分类
									</Button>
								</span>
							</Tooltip>
						</div>
					) : null}
				</Card>

				<Card
					title={selectedCategory?.name || "业务数据标签"}
					extra={
						canManage ? (
							<Space>
								<Button onClick={() => void installBuiltins()} loading={saving}>
									安装预置标签
								</Button>
								<Button
									type="primary"
									aria-label="新建标签"
									disabled={!selectedCategoryId}
									onClick={() => openTagEditor()}
								>
									新建标签
								</Button>
							</Space>
						) : null
					}
				>
					<div className="mb-4 flex flex-wrap items-center gap-2">
						<Input.Search
							allowClear
							value={keyword}
							placeholder="按标签名称或编码查询"
							style={{ width: 280 }}
							onChange={(event) => {
								setKeyword(event.target.value);
								setPage(1);
							}}
							onSearch={(value) => {
								setKeyword(value);
								setPage(1);
							}}
						/>
					</div>
					<CompactTable<CatalogTagDto>
						rowKey="id"
						columns={columns}
						dataSource={tags}
						loading={loadingTags}
						pagination={{
							current: page,
							pageSize,
							total,
							showSizeChanger: true,
							pageSizeOptions: [10, 20, 50, 100],
							showTotal: (count) => `共 ${count} 个标签`,
							onChange: (nextPage, nextSize) => {
								setPageSize(nextSize);
								setPage(nextSize !== pageSize ? 1 : nextPage);
							},
						}}
					/>
				</Card>
			</div>

			<Modal
				open={editorKind === "category"}
				title={editingCategory ? "编辑标签分类" : "新建标签分类"}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				onCancel={() => setEditorKind(null)}
				onOk={() => void saveCategory()}
				destroyOnClose
			>
				<Form form={categoryForm} layout="vertical" preserve={false}>
					<Form.Item name="name" label="分类名称" rules={[{ required: true, message: "请输入分类名称" }]}>
						<Input placeholder="例如：业务域" disabled={Boolean(editingCategory?.builtin)} />
					</Form.Item>
					<Form.Item name="code" label="分类编码" rules={[{ required: true, message: "请输入分类编码" }]}>
						<Input placeholder="例如：BUSINESS_DOMAIN" disabled={Boolean(editingCategory?.builtin)} />
					</Form.Item>
					<Form.Item name="parentId" label="上级分类" extra="不选择表示一级分类；编辑时不能选择当前分类或其子分类。">
						<TreeSelect
							allowClear
							showSearch
							treeDefaultExpandAll
							treeNodeFilterProp="title"
							placeholder="请选择上级分类（可选）"
							treeData={parentCategoryTreeData}
							disabled={Boolean(editingCategory?.builtin)}
						/>
					</Form.Item>
					<Form.Item name="description" label="分类说明">
						<Input.TextArea
							rows={3}
							placeholder="说明该分类适用于哪些业务数据"
							disabled={Boolean(editingCategory?.builtin)}
						/>
					</Form.Item>
					<Form.Item name="sortOrder" label="显示顺序">
						<Input type="number" min={0} disabled={Boolean(editingCategory?.builtin)} />
					</Form.Item>
					<Form.Item name="enabled" label="启用状态" valuePropName="checked">
						<Switch />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={editorKind === "tag"}
				title={editingTag ? "编辑业务数据标签" : "新建业务数据标签"}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				onCancel={() => setEditorKind(null)}
				onOk={() => void saveTag()}
				destroyOnClose
			>
				<Form form={tagForm} layout="vertical" preserve={false}>
					<Form.Item name="name" label="标签名称" rules={[{ required: true, message: "请输入标签名称" }]}>
						<Input placeholder="例如：财务数据" disabled={Boolean(editingTag?.builtin)} />
					</Form.Item>
					<Form.Item name="code" label="标签编码" rules={[{ required: true, message: "请输入标签编码" }]}>
						<Input placeholder="例如：BUSINESS_FINANCE" disabled={Boolean(editingTag?.builtin)} />
					</Form.Item>
					<Form.Item name="color" label="显示颜色">
						<Select
							options={COLOR_PALETTE.map((color) => ({
								value: color,
								label: color,
							}))}
						/>
					</Form.Item>
					<Form.Item name="description" label="业务说明">
						<Input.TextArea
							rows={3}
							placeholder="说明该标签代表的业务含义和适用范围"
							disabled={Boolean(editingTag?.builtin)}
						/>
					</Form.Item>
					<Form.Item name="enabled" label="启用状态" valuePropName="checked">
						<Switch />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
