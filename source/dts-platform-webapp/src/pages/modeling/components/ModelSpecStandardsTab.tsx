import { Alert, Button, Empty, Form, Modal, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { listMeasurementUnits, listMetadataStandards, listReferenceCodes } from "@/api/platformApi";
import { modelSpecDetailPath } from "../modelSpecDetailNavigation";
import type { CanonicalModelSpecView, ModelSpecStandardBinding } from "../modelSpecV2Contract";

const { Text } = Typography;

type Props = {
	model: CanonicalModelSpecView;
	canEdit: boolean;
	saving: boolean;
	onSaveStandardBindings: (bindings: ModelSpecStandardBinding[]) => Promise<boolean>;
};

type BindingRow = {
	key: string;
	fieldName: string;
	fieldSecurityLevel?: string | null;
	binding?: ModelSpecStandardBinding;
};

type OwnerOption = {
	value: string;
	label: string;
	version: number | null;
	disabled?: boolean;
};

type MetadataStandardOwner = {
	id?: string;
	fieldNameCn?: string;
	fieldNameEn?: string;
	version?: number | string | null;
};

type ReferenceCodeOwner = {
	codeTypeId?: string;
	codeTypeCode?: string;
	codeTypeName?: string;
	version?: number | string | null;
	status?: number | string | null;
};

type MeasurementUnitOwner = {
	id?: string;
	code?: string;
	name?: string;
	symbol?: string;
	version?: number | null;
	status?: string | null;
};

const versionedRef = (id?: string | null, version?: number | null) =>
	id && version != null ? `${id} · v${version}` : "—";

const pageItems = <T,>(value: unknown): T[] => {
	if (Array.isArray(value)) return value as T[];
	if (!value || typeof value !== "object") return [];
	const candidate = value as { content?: unknown; items?: unknown; data?: unknown };
	if (Array.isArray(candidate.content)) return candidate.content as T[];
	if (Array.isArray(candidate.items)) return candidate.items as T[];
	if (candidate.data !== value) return pageItems<T>(candidate.data);
	return [];
};

const numericVersion = (value: number | string | null | undefined) => {
	if (typeof value === "number" && Number.isInteger(value) && value > 0) return value;
	const match = String(value ?? "")
		.trim()
		.match(/^v?(\d+)$/i);
	const parsed = match ? Number(match[1]) : 0;
	return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
};

const hasVersionedStandard = (binding?: ModelSpecStandardBinding) =>
	Boolean(
		binding &&
			((binding.standardElementId && binding.standardElementVersion) ||
				(binding.referenceCode && binding.referenceCodeVersion) ||
				(binding.measurementUnitId && binding.measurementUnitVersion)),
	);

export function ModelSpecStandardsTab({ model, canEdit, saving, onSaveStandardBindings }: Props) {
	const navigate = useNavigate();
	const [dataElements, setDataElements] = useState<OwnerOption[]>([]);
	const [referenceCodes, setReferenceCodes] = useState<OwnerOption[]>([]);
	const [measurementUnits, setMeasurementUnits] = useState<OwnerOption[]>([]);
	const [ownersLoading, setOwnersLoading] = useState(false);
	const [ownersError, setOwnersError] = useState("");
	const [editing, setEditing] = useState<BindingRow | null>(null);
	const [draft, setDraft] = useState<ModelSpecStandardBinding | null>(null);
	const [saveError, setSaveError] = useState("");
	const standardBindings = model.standardBindings || [];

	const loadOwners = useCallback(async () => {
		setOwnersLoading(true);
		setOwnersError("");
		const [elementsResult, codesResult, unitsResult] = await Promise.allSettled([
			listMetadataStandards({ page: 0, size: 500 }),
			listReferenceCodes({ page: 0, size: 500 }),
			listMeasurementUnits(),
		]);
		if (elementsResult.status === "fulfilled") {
			setDataElements(
				pageItems<MetadataStandardOwner>(elementsResult.value)
					.filter((item) => item.id)
					.map((item) => {
						const version = numericVersion(item.version);
						return {
							value: item.id || "",
							label: `${item.fieldNameCn || item.fieldNameEn || item.id}${version ? ` · v${version}` : " · 暂无版本契约"}`,
							version,
							disabled: version == null,
						};
					}),
			);
		} else setDataElements([]);
		if (codesResult.status === "fulfilled") {
			setReferenceCodes(
				pageItems<ReferenceCodeOwner>(codesResult.value)
					.filter((item) => item.codeTypeId)
					.map((item) => {
						const version = numericVersion(item.version);
						return {
							value: item.codeTypeId || "",
							label: `${item.codeTypeName || item.codeTypeCode || item.codeTypeId}${version ? ` · v${version}` : " · 暂无版本契约"}`,
							version,
							disabled: version == null || item.status === 0 || item.status === "INACTIVE",
						};
					}),
			);
		} else setReferenceCodes([]);
		if (unitsResult.status === "fulfilled") {
			setMeasurementUnits(
				pageItems<MeasurementUnitOwner>(unitsResult.value)
					.filter((item) => item.id)
					.map((item) => ({
						value: item.id || "",
						label: `${item.name || item.code || item.id}${item.symbol ? ` (${item.symbol})` : ""} · v${item.version ?? "?"}`,
						version: numericVersion(item.version),
						disabled: item.status !== "ACTIVE" || numericVersion(item.version) == null,
					})),
			);
		} else setMeasurementUnits([]);
		if ([elementsResult, codesResult, unitsResult].some((result) => result.status === "rejected")) {
			setOwnersError("部分专业标准暂时不可用；已保存引用保持不变，可稍后重试");
		}
		setOwnersLoading(false);
	}, []);

	useEffect(() => {
		void loadOwners();
	}, [loadOwners]);

	const bindingByField = useMemo(
		() => new Map(standardBindings.map((binding) => [binding.fieldName, binding])),
		[standardBindings],
	);
	const rows: BindingRow[] = model.fields.map((field) => ({
		key: field.name,
		fieldName: field.name,
		fieldSecurityLevel: field.securityLevel,
		binding: bindingByField.get(field.name),
	}));

	const openEditor = (row: BindingRow) => {
		setEditing(row);
		setSaveError("");
		setDraft(
			row.binding || {
				fieldName: row.fieldName,
				securityLevel: row.fieldSecurityLevel || undefined,
			},
		);
	};

	const chooseOwner = (kind: "element" | "code" | "unit", value: string | undefined, options: OwnerOption[]) => {
		if (!draft) return;
		const selected = options.find((option) => option.value === value);
		if (kind === "element") {
			setDraft({ ...draft, standardElementId: value, standardElementVersion: selected?.version || undefined });
		} else if (kind === "code") {
			setDraft({ ...draft, referenceCode: value, referenceCodeVersion: selected?.version || undefined });
		} else {
			setDraft({ ...draft, measurementUnitId: value, measurementUnitVersion: selected?.version || undefined });
		}
	};

	const saveBinding = async () => {
		if (!editing || !draft || !hasVersionedStandard(draft)) {
			setSaveError("请至少选择一个带有效版本的数据元、公共码表或度量单位");
			return;
		}
		setSaveError("");
		const next = standardBindings.filter((binding) => binding.fieldName !== editing.fieldName);
		const saved = await onSaveStandardBindings([...next, { ...draft, fieldName: editing.fieldName }]);
		if (saved) {
			setEditing(null);
			setDraft(null);
		}
	};

	const columns: ColumnsType<BindingRow> = [
		{ title: "字段", dataIndex: "fieldName", width: 180 },
		{
			title: "数据元",
			render: (_, row) => versionedRef(row.binding?.standardElementId, row.binding?.standardElementVersion),
		},
		{
			title: "公共码表",
			render: (_, row) => versionedRef(row.binding?.referenceCode, row.binding?.referenceCodeVersion),
		},
		{
			title: "度量单位",
			render: (_, row) => versionedRef(row.binding?.measurementUnitId, row.binding?.measurementUnitVersion),
		},
		{ title: "安全等级", render: (_, row) => row.binding?.securityLevel || row.fieldSecurityLevel || "—" },
		{
			title: "状态",
			width: 100,
			render: (_, row) => (hasVersionedStandard(row.binding) ? <Tag color="green">已关联</Tag> : <Tag>未关联</Tag>),
		},
		{
			title: "操作",
			width: 130,
			fixed: "right",
			render: (_, row) => (
				<Button size="small" disabled={!canEdit} onClick={() => openEditor(row)}>
					配置字段标准
				</Button>
			),
		},
	];

	const openStandardOwner = () => {
		const returnTo = modelSpecDetailPath(model.id, "standards", model.planId);
		const params = new URLSearchParams({
			modelSpecId: model.id,
			revision: String(model.revision),
			returnTo,
		});
		if (model.planId) params.set("planId", model.planId);
		navigate(`/governance/standards/elements?${params.toString()}`);
	};

	return (
		<div data-testid="model-spec-standards-tab">
			<div className="mb-3 flex flex-wrap items-center justify-between gap-3">
				<Space direction="vertical" size={0}>
					<Text strong>字段标准</Text>
					<Text type="secondary" className="text-xs">
						标准正文由专业模块持有；模型只保存字段、稳定 ID 和版本
					</Text>
					<Text type="secondary" className="text-xs">
						当前显示已保存版本 r{model.revision}
					</Text>
				</Space>
				<Space wrap>
					<Button onClick={() => void loadOwners()} loading={ownersLoading}>
						刷新标准
					</Button>
					<Button onClick={openStandardOwner}>前往数据元</Button>
				</Space>
			</div>
			{ownersError ? <Alert className="mb-3" type="warning" showIcon message={ownersError} /> : null}
			{rows.length > 0 ? (
				<Table<BindingRow>
					size="small"
					rowKey="key"
					columns={columns}
					dataSource={rows}
					pagination={false}
					scroll={{ x: 1040 }}
				/>
			) : (
				<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请先在“字段设计”中添加字段" />
			)}

			<Modal
				open={Boolean(editing)}
				title={editing ? `配置字段标准：${editing.fieldName}` : "配置字段标准"}
				okText="保存绑定"
				cancelText="取消"
				confirmLoading={saving}
				onOk={() => void saveBinding()}
				onCancel={() => {
					setEditing(null);
					setDraft(null);
					setSaveError("");
				}}
				destroyOnClose
			>
				{saveError ? <Alert className="mb-3" type="error" showIcon message={saveError} /> : null}
				<Form layout="vertical">
					<Form.Item label="数据元">
						<Select
							allowClear
							showSearch
							optionFilterProp="label"
							loading={ownersLoading}
							options={dataElements}
							value={draft?.standardElementId}
							onChange={(value) => chooseOwner("element", value, dataElements)}
							placeholder="选择具备版本契约的数据元"
						/>
					</Form.Item>
					<Form.Item label="公共码表">
						<Select
							allowClear
							showSearch
							optionFilterProp="label"
							loading={ownersLoading}
							options={referenceCodes}
							value={draft?.referenceCode}
							onChange={(value) => chooseOwner("code", value, referenceCodes)}
							placeholder="选择具备版本契约的公共码表"
						/>
					</Form.Item>
					<Form.Item label="度量单位">
						<Select
							allowClear
							showSearch
							optionFilterProp="label"
							loading={ownersLoading}
							options={measurementUnits}
							value={draft?.measurementUnitId}
							onChange={(value) => chooseOwner("unit", value, measurementUnits)}
							placeholder="选择 ACTIVE 度量单位"
						/>
					</Form.Item>
					<Form.Item label="安全等级">
						<Select
							allowClear
							value={draft?.securityLevel || undefined}
							onChange={(value) => draft && setDraft({ ...draft, securityLevel: value })}
							options={["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"].map((value) => ({ value, label: value }))}
						/>
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
