import { Alert, Button, Drawer, Form, Input, Modal, Select, Tag, Typography } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { confirmDataMart, createDataMart, listDataMarts, retireDataMart, updateDataMart } from "@/api/dataMartApi";
import { searchUsers, type UserDirectoryEntry } from "@/api/services/userDirectoryService";
import { actionColumn, CompactTable } from "@/components/table";
import type { DataMartView } from "@/features/modeling/contracts/dataMartContract";

const { Text } = Typography;

type FormValues = {
	code: string;
	name: string;
	purpose: string;
	ownerId: string;
	businessCategoryIds: string[];
};

type Props = {
	domainId: string;
	domainOptions: Array<{ value: string; label: string }>;
	canManage: boolean;
};

const statusLabel: Record<DataMartView["status"], string> = {
	DRAFT: "草稿",
	CURRENT: "已确认",
	RETIRED: "已退役",
};

const statusColor: Record<DataMartView["status"], string> = {
	DRAFT: "gold",
	CURRENT: "green",
	RETIRED: "default",
};

const idempotencyKey = () =>
	globalThis.crypto?.randomUUID?.() || `data-mart-${Date.now()}-${Math.random().toString(16).slice(2)}`;

const errorMessage = (error: unknown) => {
	if (error && typeof error === "object") {
		const candidate = error as { message?: string; response?: { data?: { message?: string } } };
		return candidate.response?.data?.message || candidate.message || "数据集市操作失败";
	}
	return "数据集市操作失败";
};

const userLabel = (user: UserDirectoryEntry) => {
	const name = user.fullName?.trim() || user.displayName?.trim() || user.username;
	const department = user.deptName?.trim() || user.deptCode?.trim();
	return department ? `${name}（${user.username} · ${department}）` : `${name}（${user.username}）`;
};

export function DataMartWorkspace({ domainId, domainOptions, canManage }: Props) {
	const [form] = Form.useForm<FormValues>();
	const [items, setItems] = useState<DataMartView[]>([]);
	const [loading, setLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState("");
	const [drawerOpen, setDrawerOpen] = useState(false);
	const [editing, setEditing] = useState<DataMartView | null>(null);
	const [query, setQuery] = useState("");
	const [appliedQuery, setAppliedQuery] = useState("");
	const [directoryUsers, setDirectoryUsers] = useState<UserDirectoryEntry[]>([]);
	const [directoryLoading, setDirectoryLoading] = useState(false);
	const [directoryQuery, setDirectoryQuery] = useState("");

	const load = useCallback(async () => {
		setLoading(true);
		setError("");
		try {
			setItems(
				await listDataMarts({
					businessCategoryId: domainId,
					keyword: appliedQuery || undefined,
					offset: 0,
					limit: 100,
				}),
			);
		} catch (loadError) {
			setError(errorMessage(loadError));
		} finally {
			setLoading(false);
		}
	}, [appliedQuery, domainId]);

	useEffect(() => {
		void load();
	}, [load]);

	const loadOwners = useCallback(async (keyword?: string) => {
		setDirectoryLoading(true);
		setDirectoryUsers(await searchUsers(keyword));
		setDirectoryLoading(false);
	}, []);

	useEffect(() => {
		if (!drawerOpen || !directoryQuery.trim()) return;
		const handle = window.setTimeout(() => void loadOwners(directoryQuery.trim()), 300);
		return () => window.clearTimeout(handle);
	}, [directoryQuery, drawerOpen, loadOwners]);

	const openCreate = () => {
		setEditing(null);
		form.resetFields();
		form.setFieldsValue({ businessCategoryIds: [domainId] });
		setDirectoryUsers([]);
		setDirectoryQuery("");
		void loadOwners();
		setDrawerOpen(true);
	};

	const openEdit = (item: DataMartView) => {
		setEditing(item);
		form.setFieldsValue({
			code: item.code,
			name: item.name,
			purpose: item.purpose,
			ownerId: item.ownerId,
			businessCategoryIds: item.businessCategoryIds,
		});
		setDirectoryUsers([]);
		setDirectoryQuery(item.ownerId);
		void loadOwners(item.ownerId);
		setDrawerOpen(true);
	};

	const ownerOptions = useMemo(() => {
		const options = new Map<string, { value: string; label: string }>();
		if (editing) options.set(editing.ownerId, { value: editing.ownerId, label: editing.ownerId });
		for (const user of directoryUsers) options.set(user.id, { value: user.id, label: userLabel(user) });
		return [...options.values()];
	}, [directoryUsers, editing]);

	const save = async () => {
		const values = await form.validateFields();
		setSaving(true);
		try {
			if (editing) {
				await updateDataMart(editing, {
					name: values.name.trim(),
					purpose: values.purpose.trim(),
					ownerId: values.ownerId.trim(),
					businessCategoryIds: values.businessCategoryIds,
				});
			} else {
				await createDataMart({
					code: values.code.trim().toUpperCase(),
					name: values.name.trim(),
					purpose: values.purpose.trim(),
					ownerId: values.ownerId.trim(),
					businessCategoryIds: values.businessCategoryIds,
					idempotencyKey: idempotencyKey(),
				});
			}
			setDrawerOpen(false);
			toast.success(editing ? "数据集市已更新" : "数据集市已登记");
			await load();
		} catch (saveError) {
			setError(errorMessage(saveError));
		} finally {
			setSaving(false);
		}
	};

	const transition = (item: DataMartView, action: "confirm" | "retire") => {
		Modal.confirm({
			title: action === "confirm" ? "确认当前数据集市？" : "退役当前数据集市？",
			content:
				action === "confirm"
					? "确认后可纳入建设规划，并作为维度与模型的建模范围。"
					: "已被规划、维度或模型使用的数据集市不能直接退役。",
			okText: action === "confirm" ? "确认" : "退役",
			okButtonProps: action === "retire" ? { danger: true } : undefined,
			onOk: async () => {
				try {
					if (action === "confirm") await confirmDataMart(item);
					else await retireDataMart(item);
					toast.success(action === "confirm" ? "数据集市已确认" : "数据集市已退役");
					await load();
				} catch (transitionError) {
					setError(errorMessage(transitionError));
				}
			},
		});
	};

	return (
		<div className="space-y-4" data-testid="data-mart-workspace">
			<div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-4">
				<div>
					<div className="font-medium text-slate-900">数据集市</div>
					<Text type="secondary">按消费与建设边界组织多个业务分类；这里只管理规划对象，发布后才登记为数据资产。</Text>
				</div>
				<Button type="primary" disabled={!canManage} onClick={openCreate}>
					新建数据集市
				</Button>
			</div>
			{error ? <Alert type="warning" showIcon message={error} closable onClose={() => setError("")} /> : null}
			<Input.Search
				allowClear
				value={query}
				onChange={(event) => {
					setQuery(event.target.value);
					if (!event.target.value) setAppliedQuery("");
				}}
				onSearch={(value) => setAppliedQuery(value.trim())}
				placeholder="搜索数据集市名称、编码、用途或责任人"
			/>
			<CompactTable<DataMartView>
				rowKey="id"
				loading={loading}
				dataSource={items}
				pagination={{ pageSize: 10 }}
				locale={{ emptyText: "当前业务分类尚未纳入数据集市" }}
				columns={[
					{
						title: "数据集市",
						key: "name",
						render: (_, item) => (
							<div>
								<div className="font-medium">{item.name}</div>
								<Text type="secondary">{item.code}</Text>
							</div>
						),
					},
					{ title: "用途", dataIndex: "purpose", ellipsis: true },
					{ title: "责任人", dataIndex: "ownerId", width: 140 },
					{
						title: "业务分类",
						key: "domains",
						width: 120,
						render: (_, item) => `${item.businessCategoryIds.length} 个`,
					},
					{
						title: "状态",
						dataIndex: "status",
						width: 100,
						render: (status: DataMartView["status"]) => <Tag color={statusColor[status]}>{statusLabel[status]}</Tag>,
					},
					{
						title: "使用数",
						dataIndex: "usageCount",
						width: 90,
					},
					actionColumn<DataMartView>(
						(item) => [
							{
								key: "edit",
								label: "编辑",
								disabled: !canManage || item.status === "RETIRED",
								onClick: () => openEdit(item),
							},
							{
								key: "confirm",
								label: "确认",
								hidden: item.status !== "DRAFT",
								disabled: !canManage,
								onClick: () => transition(item, "confirm"),
							},
							{
								key: "retire",
								label: "退役",
								danger: true,
								hidden: item.status !== "CURRENT",
								disabled: !canManage,
								onClick: () => transition(item, "retire"),
							},
						],
						{ width: 210, fixed: false },
					),
				]}
			/>
			<Drawer
				open={drawerOpen}
				title={editing ? "编辑数据集市" : "新建数据集市"}
				width={560}
				destroyOnClose
				onClose={() => setDrawerOpen(false)}
				footer={
					<div className="flex justify-end gap-2">
						<Button disabled={saving} onClick={() => setDrawerOpen(false)}>
							取消
						</Button>
						<Button type="primary" loading={saving} onClick={() => void save()}>
							保存
						</Button>
					</div>
				}
			>
				<Form form={form} layout="vertical" disabled={saving}>
					<Form.Item
						name="code"
						label="数据集市编码"
						rules={[
							{ required: true, whitespace: true, message: "请输入数据集市编码" },
							{ pattern: /^[A-Za-z][A-Za-z0-9_]{1,63}$/, message: "使用字母、数字和下划线，且以字母开头" },
						]}
					>
						<Input disabled={Boolean(editing)} placeholder="例如：FINANCE_MART" />
					</Form.Item>
					<Form.Item
						name="name"
						label="数据集市名称"
						rules={[{ required: true, whitespace: true, message: "请输入名称" }]}
					>
						<Input placeholder="例如：财务分析集市" maxLength={256} />
					</Form.Item>
					<Form.Item
						name="purpose"
						label="建设用途"
						rules={[{ required: true, whitespace: true, message: "请说明数据集市服务的分析或应用场景" }]}
					>
						<Input.TextArea rows={4} maxLength={2000} showCount />
					</Form.Item>
					<Form.Item
						name="ownerId"
						label="责任人"
						extra="负责人必须来自人员目录，不能手工填写账号。"
						rules={[{ required: true, message: "请选择责任人" }]}
					>
						<Select
							showSearch
							filterOption={false}
							onSearch={setDirectoryQuery}
							options={ownerOptions}
							loading={directoryLoading}
							placeholder="输入姓名或账号搜索人员目录"
							notFoundContent={directoryLoading ? "正在查询人员目录" : "未找到可选负责人"}
						/>
					</Form.Item>
					<Form.Item
						name="businessCategoryIds"
						label="包含的业务分类"
						extra={
							editing && editing.usageCount > 0
								? "该数据集市已被规划、维度或模型使用；解除引用后才能调整业务分类。"
								: undefined
						}
						rules={[{ required: true, type: "array", min: 1, message: "至少选择一个业务分类" }]}
					>
						<Select
							mode="multiple"
							showSearch
							optionFilterProp="label"
							options={domainOptions}
							disabled={Boolean(editing && editing.usageCount > 0)}
						/>
					</Form.Item>
				</Form>
			</Drawer>
		</div>
	);
}
