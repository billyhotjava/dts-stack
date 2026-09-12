import { useMemo, useState } from "react";
import { actionColumn, type CompactColumns, CompactTable } from "@/components/table";
import type { ModelSpecField } from "@/features/modeling/contracts/modelSpecV2Contract";
import { Button, RequestState } from "./PrototypePrimitives";
import type { ModelSpecDraft, ModelWorkbenchContext } from "./services/modelWorkbenchService";

const DATA_TYPES = ["STRING", "BOOLEAN", "INT", "BIGINT", "DECIMAL", "DATE", "TIMESTAMP", "TIMESTAMPTZ", "JSONB"];
const SECURITY_LEVELS = [
	{ value: "PUBLIC", label: "公开" },
	{ value: "INTERNAL", label: "内部" },
	{ value: "SECRET", label: "秘密" },
	{ value: "CONFIDENTIAL", label: "机密" },
];
const IMPORT_UNAVAILABLE_REASON = "当前版本暂不支持从表/视图导入字段结构";

export type ModelFieldEditorTableProps = {
	fields: ModelSpecField[];
	bindings: ModelSpecDraft["standardBindings"];
	standards: ModelWorkbenchContext["standards"];
	fieldRowIds: string[];
	dimensionMode: boolean;
	readOnly: boolean;
	onAddFields: (count: number) => void;
	onRemoveBlankFields: () => void;
	onUpdate: (index: number, patch: Partial<ModelSpecField>) => void;
	onDelete: (index: number) => void;
	onStandardChange: (index: number, value: string) => void;
	canAssociate: boolean;
	canOpenCode: boolean;
	showCodeAction?: boolean;
	onOpenCode: () => void;
	onOpenAssociation: () => void;
};

export const isBlankModelField = (field: ModelSpecField): boolean => !field.name.trim() && !field.displayName?.trim();

export const dimensionPrimaryKeyPatch = (checked: boolean): Partial<ModelSpecField> =>
	checked ? { role: "KEY", nullable: false } : { role: "ATTRIBUTE" };

export const dimensionNonNullPatch = (checked: boolean): Partial<ModelSpecField> => ({ nullable: !checked });

type FieldRow = { key: string; field: ModelSpecField; index: number };

function selectableDataTypes(value: string) {
	return DATA_TYPES.includes(value) ? DATA_TYPES : [...DATA_TYPES, value];
}

export function ModelFieldEditorTable({
	fields,
	bindings,
	standards,
	fieldRowIds,
	dimensionMode,
	readOnly,
	onAddFields,
	onRemoveBlankFields,
	onUpdate,
	onDelete,
	onStandardChange,
	canAssociate,
	canOpenCode,
	showCodeAction = true,
	onOpenCode,
	onOpenAssociation,
}: ModelFieldEditorTableProps) {
	const [insertCount, setInsertCount] = useState("1");
	const [showStandards, setShowStandards] = useState(false);
	const addFields = () => {
		const parsed = Number.parseInt(insertCount, 10);
		onAddFields(Number.isFinite(parsed) ? Math.max(1, Math.min(20, parsed)) : 1);
	};

	const rows: FieldRow[] = fields.map((field, index) => ({
		key: fieldRowIds[index] || `${field.name}-${index}`,
		field,
		index,
	}));

	const columns = useMemo<CompactColumns<FieldRow>>(() => {
		const base: CompactColumns<FieldRow> = [
			{ title: "序号", key: "seq", width: 64, render: (_, { index }) => index + 1 },
			{
				title: "字段名称",
				key: "name",
				render: (_, { field, index }) => (
					<input
						disabled={readOnly}
						onChange={(event) => onUpdate(index, { name: event.target.value })}
						value={field.name}
					/>
				),
			},
			{
				title: "类型",
				key: "dataType",
				render: (_, { field, index }) => (
					<select
						disabled={readOnly}
						onChange={(event) => onUpdate(index, { dataType: event.target.value })}
						value={field.dataType}
					>
						{selectableDataTypes(field.dataType).map((dataType) => (
							<option key={dataType} value={dataType}>
								{dataType}
							</option>
						))}
					</select>
				),
			},
			{
				title: "字段显示名",
				key: "displayName",
				render: (_, { field, index }) => (
					<input
						disabled={readOnly}
						onChange={(event) => onUpdate(index, { displayName: event.target.value })}
						value={field.displayName || ""}
					/>
				),
			},
		];
		if (dimensionMode) {
			base.push(
				{
					title: "主键",
					key: "primaryKey",
					align: "center",
					render: (_, { field, index }) => (
						<input
							aria-label={`字段 ${index + 1} 为主键`}
							checked={field.role === "KEY"}
							disabled={readOnly}
							onChange={(event) => onUpdate(index, dimensionPrimaryKeyPatch(event.target.checked))}
							type="checkbox"
						/>
					),
				},
				{
					title: "非空",
					key: "nullable",
					align: "center",
					render: (_, { field, index }) => (
						<input
							aria-label={`字段 ${index + 1} 非空`}
							checked={!field.nullable}
							disabled={readOnly}
							onChange={(event) => onUpdate(index, dimensionNonNullPatch(event.target.checked))}
							type="checkbox"
						/>
					),
				},
				{
					title: "维度属性编码",
					key: "dimensionAttributeCode",
					render: (_, { field, index }) => (
						<input
							disabled={readOnly}
							onChange={(event) => onUpdate(index, { dimensionAttributeCode: event.target.value })}
							placeholder="可选"
							value={field.dimensionAttributeCode || ""}
						/>
					),
				},
			);
		} else {
			base.push({
				title: "字段作用",
				key: "role",
				render: (_, { field, index }) => (
					<select
						disabled={readOnly}
						onChange={(event) => onUpdate(index, { role: event.target.value as ModelSpecField["role"] })}
						value={field.role}
					>
						<option value="KEY">键（KEY）</option>
						<option value="ATTRIBUTE">属性</option>
						<option value="TIME">时间</option>
						<option value="MEASURE">度量</option>
					</select>
				),
			});
		}
		if (showStandards) {
			base.push(
				{
					title: "字段标准",
					key: "standard",
					render: (_, { field, index }) => {
						const binding = bindings.find((item) => item.fieldName === field.name);
						const standardValue =
							binding?.standardElementId && binding.standardElementVersion
								? `${binding.standardElementId}@${binding.standardElementVersion}`
								: "";
						const currentStandard = standards.find((standard) => standard.id === binding?.standardElementId);
						const bindingUnavailable = standardValue && !standards.some(
							(standard) => `${standard.id}@${standard.version}` === standardValue,
						);
						return (
							<select
								aria-label={`字段 ${index + 1} 标准`}
								disabled={readOnly || !field.name.trim()}
								onChange={(event) => onStandardChange(index, event.target.value)}
								value={standardValue}
							>
								<option value="">不关联</option>
								{bindingUnavailable ? (
									<option value={standardValue} disabled>
										{currentStandard?.name || "已关联标准"} · v{binding?.standardElementVersion}
										{currentStandard ? `（当前 v${currentStandard.version}，请核对）` : "（当前列表未找到，请核对）"}
									</option>
								) : null}
								{standards.map((standard) => (
									<option key={`${standard.id}@${standard.version}`} value={`${standard.id}@${standard.version}`}>
										{standard.name} · {standard.code} · v{standard.version}
									</option>
								))}
							</select>
						);
					},
				},
				{
					title: "字段密级",
					key: "securityLevel",
					render: (_, { field, index }) => (
						<select
							aria-label={`字段 ${index + 1} 密级`}
							disabled={readOnly || !field.name.trim()}
							onChange={(event) => onUpdate(index, { securityLevel: event.target.value || null })}
							value={field.securityLevel || ""}
						>
							<option value="">未设置</option>
							{SECURITY_LEVELS.map((level) => (
								<option key={level.value} value={level.value}>
									{level.label}
								</option>
							))}
						</select>
					),
				},
			);
		}
		if (!dimensionMode) {
			base.push({
				title: "允许为空",
				key: "allowNull",
				align: "center",
				render: (_, { field, index }) => (
					<input
						aria-label={`字段 ${index + 1} 允许为空`}
						checked={field.nullable}
						disabled={readOnly}
						onChange={(event) => onUpdate(index, { nullable: event.target.checked })}
						type="checkbox"
					/>
				),
			});
		}
		base.push(
			actionColumn<FieldRow>(
				({ index }) => [
					{ key: "delete", label: "删除", danger: true, disabled: readOnly, onClick: () => onDelete(index) },
				],
				{ fixed: false },
			),
		);
		return base;
	}, [bindings, dimensionMode, onDelete, onStandardChange, onUpdate, readOnly, showStandards, standards]);

	return (
		<section aria-label="字段编辑器">
			<div className="dmx-table-tools">
				{showCodeAction ? (
					<Button disabled={!canOpenCode || readOnly} onClick={onOpenCode}>
						代码模式
					</Button>
				) : null}
				<Button
					disabled={!canAssociate || readOnly}
					onClick={onOpenAssociation}
					title={canAssociate ? undefined : "请先保存模型后关联字段"}
				>
					字段关联
				</Button>
				<Button disabled title={IMPORT_UNAVAILABLE_REASON}>
					从表/视图导入
				</Button>
			</div>
			<div className="dmx-table-tools">
				<label>
					插入行数
					<input
						aria-label="插入行数"
						disabled={readOnly}
						max="20"
						min="1"
						onChange={(event) => setInsertCount(event.target.value)}
						type="number"
						value={insertCount}
					/>
				</label>
				<Button disabled={readOnly} onClick={addFields}>
					插入字段
				</Button>
				<Button disabled={readOnly} onClick={onRemoveBlankFields}>
					移除空白字段
				</Button>
				{showStandards ? (
					<label>
						批量字段密级
						<select
							aria-label="批量设置字段密级"
							disabled={readOnly}
							onChange={(event) => {
								const securityLevel = event.target.value;
								if (!securityLevel) return;
								fields.forEach((field, index) => {
									if (field.name.trim()) onUpdate(index, { securityLevel });
								});
							}}
							value=""
						>
							<option value="">请选择并应用到全部字段</option>
							{SECURITY_LEVELS.map((level) => (
								<option key={level.value} value={level.value}>
									{level.label}
								</option>
							))}
						</select>
					</label>
				) : null}
				<Button className="right" onClick={() => setShowStandards((current) => !current)}>
					字段显示设置
				</Button>
			</div>
			<CompactTable<FieldRow>
				className="dmx-field-editor-table"
				columns={columns}
				dataSource={rows}
				pagination={false}
				rowKey="key"
				locale={{
					emptyText: <RequestState description="点击插入字段新增字段。" kind="empty" title="暂无字段" />,
				}}
			/>
		</section>
	);
}
