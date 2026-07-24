import { Alert, Button, Drawer, Form, Input, Select, Space } from "antd";
import { useEffect, useRef, useState } from "react";
import { createDimensionDefinition, updateDimensionDefinition } from "@/api/dimensionDefinitionApi";
import {
	createDimensionDefinitionIdempotencyKey,
	dimensionDefinitionErrorMessage,
	isDimensionDefinitionVersionConflict,
	shouldApplyDimensionDefinitionReload,
} from "../dimensionCatalogViewState";
import type {
	CreateDimensionDefinitionCommand,
	DimensionDefinitionReuseScope,
	DimensionDefinitionView,
	UpdateDimensionDefinitionCommand,
} from "../dimensionDefinitionContract";

type DimensionDefinitionFormValues = {
	domainId: string;
	name: string;
	definition: string;
	ownerId: string;
	reuseScope: DimensionDefinitionReuseScope;
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
});

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
	const currentDefinitionRef = useRef<DimensionDefinitionView | null>(null);
	const activeDefinitionIdRef = useRef<string | undefined>(undefined);
	const idempotencyKeyRef = useRef("");
	const reloadRequestRef = useRef(0);
	activeDefinitionIdRef.current = open ? definition?.id : undefined;

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
				},
		);
		return () => {
			reloadRequestRef.current += 1;
		};
	}, [definition, form, initialDomainId, initialOwnerId, open]);

	const submit = async () => {
		setSubmitError("");
		setVersionConflict(false);
		try {
			const values = await form.validateFields();
			setSaving(true);
			const current = currentDefinitionRef.current;
			if (current) {
				const command: UpdateDimensionDefinitionCommand = {
					name: values.name.trim(),
					definition: values.definition.trim(),
					ownerId: values.ownerId.trim(),
					reuseScope: values.reuseScope,
					hierarchies: current.hierarchies,
				};
				await updateDimensionDefinition(current, command);
			} else {
				const command: CreateDimensionDefinitionCommand = {
					domainId: values.domainId,
					name: values.name.trim(),
					definition: values.definition.trim(),
					ownerId: values.ownerId.trim(),
					reuseScope: values.reuseScope,
					hierarchies: [],
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
					)
					|| currentDefinitionRef.current?.id !== requestedDefinitionId
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
					)
					|| currentDefinitionRef.current?.id !== requestedDefinitionId
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
						<Button type="primary" loading={saving} disabled={!canEdit || domainsLoading || reloading} onClick={() => void submit()}>
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
						/>
					</Form.Item>
					<Form.Item name="name" label="维度名称" rules={[{ required: true, whitespace: true, message: "请输入维度名称" }]}>
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
					<Form.Item name="ownerId" label="责任人" rules={[{ required: true, whitespace: true, message: "请输入责任人" }]}>
						<Input placeholder="填写负责维护此维度的人员标识" maxLength={128} />
					</Form.Item>
					<Form.Item name="reuseScope" label="复用范围" rules={[{ required: true, message: "请选择复用范围" }]}>
						<Select options={reuseScopeOptions} />
					</Form.Item>
				</Form>
			</Space>
		</Drawer>
	);
}
