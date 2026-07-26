import { Alert, Button, Checkbox, Drawer, Form, Input, Select, Space } from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { listDataMarts } from "@/api/dataMartApi";
import { createDimensionDefinition, updateDimensionDefinition } from "@/api/dimensionDefinitionApi";
import { searchUsers, type UserDirectoryEntry } from "@/api/services/userDirectoryService";
import {
	createDimensionDefinitionIdempotencyKey,
	dimensionDefinitionErrorMessage,
	isDimensionDefinitionVersionConflict,
	shouldApplyDimensionDefinitionReload,
} from "../dimensionCatalogViewState";
import type {
	CreateDimensionDefinitionCommand,
	DimensionDefinitionAttribute,
	DimensionDefinitionHierarchy,
	DimensionDefinitionReuseScope,
	DimensionDefinitionScopeType,
	DimensionDefinitionView,
	UpdateDimensionDefinitionCommand,
} from "../dimensionDefinitionContract";

type DimensionDefinitionFormValues = {
	domainId: string;
	name: string;
	definition: string;
	ownerId: string;
	reuseScope: DimensionDefinitionReuseScope;
	scopeType: DimensionDefinitionScopeType;
	dataMartId?: string;
	attributes: DimensionDefinitionAttribute[];
	hierarchies: DimensionDefinitionHierarchy[];
};

type Props = {
	open: boolean;
	definition: DimensionDefinitionView | null;
	canEdit: boolean;
	initialDomainId?: string;
	initialOwnerId?: string;
	domainOptions: { value: string; label: string }[];
	domainsLoading: boolean;
	domainLoadError: string;
	onClose: () => void;
	onSaved: () => Promise<void> | void;
	onReloadLatest: (id: string) => Promise<DimensionDefinitionView>;
};

const reuseScopeOptions: { value: DimensionDefinitionReuseScope; label: string }[] = [
	{ value: "PLAN", label: "当前建设范围内复用" },
	{ value: "DOMAIN", label: "同一业务分类内复用" },
	{ value: "TENANT", label: "全租户复用" },
];

const valuesFromDefinition = (definition: DimensionDefinitionView): DimensionDefinitionFormValues => ({
	domainId: definition.domainId,
	name: definition.name,
	definition: definition.definition,
	ownerId: definition.ownerId,
	reuseScope: definition.reuseScope,
	scopeType: definition.scopeType || "DOMAIN",
	dataMartId: definition.dataMartId || undefined,
	attributes: definition.attributes || [],
	hierarchies: definition.hierarchies || [],
});

const userLabel = (user: UserDirectoryEntry) => {
	const name = user.fullName?.trim() || user.displayName?.trim() || user.username;
	const department = user.deptName?.trim() || user.deptCode?.trim();
	return department ? `${name}（${user.username} · ${department}）` : `${name}（${user.username}）`;
};

export function DimensionDefinitionCreateDrawer({
	open,
	definition,
	canEdit,
	initialDomainId,
	initialOwnerId,
	domainOptions,
	domainsLoading,
	domainLoadError,
	onClose,
	onSaved,
	onReloadLatest,
}: Props) {
	const [form] = Form.useForm<DimensionDefinitionFormValues>();
	const [saving, setSaving] = useState(false);
	const [reloading, setReloading] = useState(false);
	const [submitError, setSubmitError] = useState("");
	const [versionConflict, setVersionConflict] = useState(false);
	const [dataMartOptions, setDataMartOptions] = useState<Array<{ value: string; label: string }>>([]);
	const [dataMartsLoading, setDataMartsLoading] = useState(false);
	const [directoryUsers, setDirectoryUsers] = useState<UserDirectoryEntry[]>([]);
	const [directoryLoading, setDirectoryLoading] = useState(false);
	const [directoryQuery, setDirectoryQuery] = useState("");
	const currentDefinitionRef = useRef<DimensionDefinitionView | null>(null);
	const activeDefinitionIdRef = useRef<string | undefined>(undefined);
	const idempotencyKeyRef = useRef("");
	const reloadRequestRef = useRef(0);
	activeDefinitionIdRef.current = open ? definition?.id : undefined;
	const scopeType = Form.useWatch("scopeType", form);

	const loadOwners = useCallback(async (keyword?: string) => {
		setDirectoryLoading(true);
		setDirectoryUsers(await searchUsers(keyword));
		setDirectoryLoading(false);
	}, []);

	const loadDataMarts = useCallback(async (domainId?: string) => {
		if (!domainId) {
			setDataMartOptions([]);
			return;
		}
		setDataMartsLoading(true);
		try {
			const items = await listDataMarts({ domainId, status: "CURRENT", offset: 0, limit: 100 });
			setDataMartOptions(items.map((item) => ({ value: item.id, label: `${item.name}（${item.code}）` })));
		} catch {
			setDataMartOptions([]);
		} finally {
			setDataMartsLoading(false);
		}
	}, []);

	useEffect(() => {
		reloadRequestRef.current += 1;
		setReloading(false);
		if (!open) {
			currentDefinitionRef.current = null;
			return;
		}
		currentDefinitionRef.current = definition;
		idempotencyKeyRef.current = createDimensionDefinitionIdempotencyKey();
		setSubmitError("");
		setVersionConflict(false);
		form.resetFields();
		form.setFieldsValue(
			definition
				? valuesFromDefinition(definition)
				: {
						domainId: initialDomainId || undefined,
						ownerId: initialOwnerId || undefined,
						reuseScope: "DOMAIN",
						scopeType: "DOMAIN",
						attributes: [],
						hierarchies: [],
					},
		);
		const ownerId = definition?.ownerId || initialOwnerId;
		setDirectoryUsers([]);
		setDirectoryQuery(ownerId || "");
		void loadOwners(ownerId);
		void loadDataMarts(definition?.domainId || initialDomainId);
		return () => {
			reloadRequestRef.current += 1;
		};
	}, [definition, form, initialDomainId, initialOwnerId, loadDataMarts, loadOwners, open]);

	useEffect(() => {
		if (!open || !directoryQuery.trim()) return;
		const handle = window.setTimeout(() => void loadOwners(directoryQuery.trim()), 300);
		return () => window.clearTimeout(handle);
	}, [directoryQuery, loadOwners, open]);

	const ownerOptions = useMemo(() => {
		const options = new Map<string, { value: string; label: string }>();
		const currentOwnerId = definition?.ownerId || initialOwnerId;
		if (currentOwnerId) options.set(currentOwnerId, { value: currentOwnerId, label: currentOwnerId });
		for (const user of directoryUsers) options.set(user.id, { value: user.id, label: userLabel(user) });
		return [...options.values()];
	}, [definition?.ownerId, directoryUsers, initialOwnerId]);

	const submit = async () => {
		setSubmitError("");
		setVersionConflict(false);
		try {
			const values = await form.validateFields();
			setSaving(true);
			const current = currentDefinitionRef.current;
			const hierarchies = (values.hierarchies || []).map((hierarchy) => ({
				code: hierarchy.code.trim().toUpperCase(),
				name: hierarchy.name.trim(),
				levels: (hierarchy.levels || []).map((level, index) => ({
					code: level.code.trim().toUpperCase(),
					name: level.name.trim(),
					order: index + 1,
				})),
			}));
			if (current) {
				const attributes = (values.attributes || []).map((attribute, index) => ({
					...attribute,
					code: attribute.code.trim().toUpperCase(),
					name: attribute.name.trim(),
					definition: attribute.definition.trim(),
					standardRef: attribute.standardRef?.trim() || null,
					standardVersion: attribute.standardVersion?.trim() || null,
					order: index + 1,
				}));
				const command: UpdateDimensionDefinitionCommand = {
					name: values.name.trim(),
					definition: values.definition.trim(),
					ownerId: values.ownerId.trim(),
					reuseScope: values.reuseScope,
					hierarchies,
					scopeType: values.scopeType,
					dataMartId: values.scopeType === "DATA_MART" ? values.dataMartId : null,
					attributes,
				};
				await updateDimensionDefinition(current, command);
			} else {
				const attributes = (values.attributes || []).map((attribute, index) => ({
					...attribute,
					code: attribute.code.trim().toUpperCase(),
					name: attribute.name.trim(),
					definition: attribute.definition.trim(),
					standardRef: attribute.standardRef?.trim() || null,
					standardVersion: attribute.standardVersion?.trim() || null,
					order: index + 1,
				}));
				const command: CreateDimensionDefinitionCommand = {
					domainId: values.domainId,
					name: values.name.trim(),
					definition: values.definition.trim(),
					ownerId: values.ownerId.trim(),
					reuseScope: values.reuseScope,
					hierarchies,
					scopeType: values.scopeType,
					dataMartId: values.scopeType === "DATA_MART" ? values.dataMartId : null,
					attributes,
					idempotencyKey: idempotencyKeyRef.current,
				};
				await createDimensionDefinition(command);
			}
			await onSaved();
		} catch (error) {
			if (error && typeof error === "object" && "errorFields" in error) return;
			setSubmitError(dimensionDefinitionErrorMessage(error));
			setVersionConflict(isDimensionDefinitionVersionConflict(error));
		} finally {
			setSaving(false);
		}
	};

	const reloadLatest = async () => {
		const current = currentDefinitionRef.current;
		if (!current || reloading) return;
		const requestId = ++reloadRequestRef.current;
		const requestedDefinitionId = current.id;
		setReloading(true);
		setSubmitError("");
		try {
			const latest = await onReloadLatest(requestedDefinitionId);
			if (
				!shouldApplyDimensionDefinitionReload(
					requestId,
					reloadRequestRef.current,
					requestedDefinitionId,
					activeDefinitionIdRef.current,
				) ||
				currentDefinitionRef.current?.id !== requestedDefinitionId
			) {
				return;
			}
			if (latest.id !== requestedDefinitionId) {
				setSubmitError("服务端返回了其他维度，当前输入未被覆盖，请重新打开维度后再试");
				return;
			}
			currentDefinitionRef.current = latest;
			form.setFieldsValue(valuesFromDefinition(latest));
			setVersionConflict(false);
		} catch (error) {
			if (
				!shouldApplyDimensionDefinitionReload(
					requestId,
					reloadRequestRef.current,
					requestedDefinitionId,
					activeDefinitionIdRef.current,
				) ||
				currentDefinitionRef.current?.id !== requestedDefinitionId
			) {
				return;
			}
			setSubmitError(dimensionDefinitionErrorMessage(error));
		} finally {
			if (requestId === reloadRequestRef.current) setReloading(false);
		}
	};

	const editing = Boolean(definition);
	return (
		<Drawer
			title={editing ? "编辑维度" : "登记维度"}
			aria-label={editing ? "编辑维度" : "登记维度"}
			open={open}
			width={540}
			keyboard={!saving}
			maskClosable={!saving}
			destroyOnClose
			onClose={onClose}
			footer={
				<Space className="flex justify-end">
					<Button onClick={onClose} disabled={saving}>
						取消
					</Button>
					<Button
						type="primary"
						loading={saving}
						disabled={!canEdit || domainsLoading || reloading}
						onClick={() => void submit()}
					>
						保存
					</Button>
				</Space>
			}
		>
			<Space direction="vertical" size={12} className="w-full">
				{!canEdit ? <Alert type="info" showIcon message="当前账号为只读浏览，不能维护维度" /> : null}
				{domainLoadError ? <Alert type="warning" showIcon message={domainLoadError} /> : null}
				{submitError ? (
					<Alert
						type="error"
						showIcon
						message={submitError}
						action={
							versionConflict ? (
								<Button size="small" loading={reloading} onClick={() => void reloadLatest()}>
									加载最新版本
								</Button>
							) : undefined
						}
					/>
				) : null}
				<Form layout="vertical" form={form} disabled={saving || reloading || !canEdit} requiredMark="optional">
					{editing ? (
						<Form.Item label="系统编码">
							<Input value={definition?.systemCode} readOnly aria-label="系统编码，只读" />
						</Form.Item>
					) : null}
					<Form.Item name="domainId" label="业务分类" rules={[{ required: true, message: "请选择业务分类" }]}>
						<Select
							showSearch
							optionFilterProp="label"
							placeholder="选择维度适用的业务分类"
							options={domainOptions}
							loading={domainsLoading}
							disabled={editing || domainsLoading}
							onChange={(value) => void loadDataMarts(value)}
						/>
					</Form.Item>
					<Form.Item name="scopeType" label="适用范围类型" rules={[{ required: true, message: "请选择适用范围" }]}>
						<Select
							options={[
								{ value: "DOMAIN", label: "业务分类" },
								{ value: "DATA_MART", label: "数据集市" },
							]}
						/>
					</Form.Item>
					{scopeType === "DATA_MART" ? (
						<Form.Item
							name="dataMartId"
							label="所属数据集市"
							rules={[{ required: true, message: "请选择已确认的数据集市" }]}
							extra="仅显示包含当前业务分类且已确认的数据集市。"
						>
							<Select
								showSearch
								optionFilterProp="label"
								loading={dataMartsLoading}
								options={dataMartOptions}
								placeholder="选择数据集市"
							/>
						</Form.Item>
					) : null}
					<Form.Item
						name="name"
						label="维度名称"
						rules={[{ required: true, whitespace: true, message: "请输入维度名称" }]}
					>
						<Input autoFocus placeholder="例如：组织机构" maxLength={128} />
					</Form.Item>
					<Form.Item
						name="definition"
						label="业务定义"
						rules={[{ required: true, whitespace: true, message: "请说明这个维度用于描述什么" }]}
					>
						<Input.TextArea
							rows={4}
							maxLength={2000}
							showCount
							placeholder="说明该维度的业务含义、适用范围和使用口径"
						/>
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
					<Form.Item name="reuseScope" label="复用范围" rules={[{ required: true, message: "请选择复用范围" }]}>
						<Select options={reuseScopeOptions} />
					</Form.Item>
					<Form.Item
						label="业务属性"
						extra="属性属于业务维度定义；模型字段在创建维度表时映射到这些属性。主键最多一个。"
					>
						<Form.List
							name="attributes"
							rules={[
								{
									validator: async (_, attributes: DimensionDefinitionAttribute[] | undefined) => {
										const values = attributes || [];
										if (values.length > 200) throw new Error("业务属性最多登记 200 个");
										if (values.filter((attribute) => attribute?.primaryKey).length > 1) {
											throw new Error("业务主键最多只能选择一个");
										}
										const codes = values.map((attribute) => attribute?.code?.trim().toUpperCase()).filter(Boolean);
										if (new Set(codes).size !== codes.length) throw new Error("属性编码不能重复");
										if (
											values.some(
												(attribute) =>
													Boolean(attribute?.standardRef?.trim()) !== Boolean(attribute?.standardVersion?.trim()),
											)
										) {
											throw new Error("标准引用和标准版本需同时填写");
										}
									},
								},
							]}
						>
							{(fields, { add, remove }, { errors }) => (
								<Space direction="vertical" className="w-full" size={10}>
									{fields.map((field) => (
										<div key={field.key} className="rounded-lg border border-slate-200 p-3">
											<div className="grid grid-cols-2 gap-2">
												<Form.Item
													{...field}
													name={[field.name, "code"]}
													label="属性编码"
													rules={[
														{ required: true, whitespace: true, message: "请输入属性编码" },
														{ pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: "使用字母、数字和下划线" },
													]}
												>
													<Input placeholder="例如：ORG_CODE" />
												</Form.Item>
												<Form.Item
													{...field}
													name={[field.name, "name"]}
													label="属性名称"
													rules={[{ required: true, whitespace: true, message: "请输入属性名称" }]}
												>
													<Input placeholder="例如：组织编码" />
												</Form.Item>
											</div>
											<Form.Item
												{...field}
												name={[field.name, "definition"]}
												label="属性定义"
												rules={[{ required: true, whitespace: true, message: "请输入属性定义" }]}
											>
												<Input placeholder="说明属性的业务含义" />
											</Form.Item>
											<div className="grid grid-cols-2 gap-2">
												<Form.Item {...field} name={[field.name, "standardRef"]} label="标准引用（可选）">
													<Input placeholder="标准项编码" />
												</Form.Item>
												<Form.Item {...field} name={[field.name, "standardVersion"]} label="标准版本（可选）">
													<Input placeholder="例如：v1" />
												</Form.Item>
											</div>
											<div className="flex items-center justify-between">
												<Form.Item
													{...field}
													name={[field.name, "primaryKey"]}
													valuePropName="checked"
													className="mb-0"
												>
													<Checkbox>业务主键</Checkbox>
												</Form.Item>
												<Button danger type="link" onClick={() => remove(field.name)}>
													移除
												</Button>
											</div>
										</div>
									))}
									<Button
										type="dashed"
										disabled={fields.length >= 200}
										onClick={() =>
											add({
												code: "",
												name: "",
												definition: "",
												primaryKey: false,
												order: 0,
											})
										}
									>
										添加业务属性
									</Button>
									<Form.ErrorList errors={errors} />
								</Space>
							)}
						</Form.List>
					</Form.Item>
					<Form.Item
						label="分析层级（可选）"
						extra="层级属于业务维度语义，例如“集团 → 公司 → 部门”；这里不填写数据库字段名。"
					>
						<Form.List name="hierarchies">
							{(hierarchyFields, { add: addHierarchy, remove: removeHierarchy }) => (
								<Space direction="vertical" className="w-full" size={10}>
									{hierarchyFields.map((hierarchyField, hierarchyIndex) => (
										<div key={hierarchyField.key} className="rounded-lg border border-slate-200 p-3">
											<div className="grid grid-cols-2 gap-2">
												<Form.Item
													name={[hierarchyField.name, "code"]}
													label={`层级 ${hierarchyIndex + 1} 编码`}
													rules={[
														{ required: true, whitespace: true, message: "请输入层级编码" },
														{ pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: "使用字母、数字和下划线" },
													]}
												>
													<Input placeholder="例如：ORG_PATH" />
												</Form.Item>
												<Form.Item
													name={[hierarchyField.name, "name"]}
													label="层级名称"
													rules={[{ required: true, whitespace: true, message: "请输入层级名称" }]}
												>
													<Input placeholder="例如：组织层级" />
												</Form.Item>
											</div>
											<Form.List name={[hierarchyField.name, "levels"]}>
												{(levelFields, { add: addLevel, remove: removeLevel }) => (
													<Space direction="vertical" className="w-full" size={8}>
														{levelFields.map((levelField, levelIndex) => (
															<div key={levelField.key} className="grid grid-cols-[1fr_1fr_auto] gap-2">
																<Form.Item
																	name={[levelField.name, "code"]}
																	label={`第 ${levelIndex + 1} 级编码`}
																	rules={[
																		{ required: true, whitespace: true, message: "请输入级别编码" },
																		{
																			pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/,
																			message: "使用字母、数字和下划线",
																		},
																	]}
																>
																	<Input placeholder="例如：DEPARTMENT" />
																</Form.Item>
																<Form.Item
																	name={[levelField.name, "name"]}
																	label="级别名称"
																	rules={[{ required: true, whitespace: true, message: "请输入级别名称" }]}
																>
																	<Input placeholder="例如：部门" />
																</Form.Item>
																<Button
																	danger
																	type="text"
																	className="mt-8"
																	onClick={() => removeLevel(levelField.name)}
																>
																	移除
																</Button>
															</div>
														))}
														<Button type="dashed" onClick={() => addLevel({ code: "", name: "", order: 0 })}>
															添加级别
														</Button>
													</Space>
												)}
											</Form.List>
											<div className="mt-2 flex justify-end">
												<Button danger type="link" onClick={() => removeHierarchy(hierarchyField.name)}>
													移除层级
												</Button>
											</div>
										</div>
									))}
									<Button type="dashed" onClick={() => addHierarchy({ code: "", name: "", levels: [] })}>
										添加分析层级
									</Button>
								</Space>
							)}
						</Form.List>
					</Form.Item>
				</Form>
			</Space>
		</Drawer>
	);
}
