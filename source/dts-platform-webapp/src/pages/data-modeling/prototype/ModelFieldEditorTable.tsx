import { useState } from "react";
import type { ModelSpecField } from "@/features/modeling/contracts/modelSpecV2Contract";
import { Button, RequestState } from "./PrototypePrimitives";
import type { ModelSpecDraft, ModelWorkbenchContext } from "./services/modelWorkbenchService";

const DATA_TYPES = ["STRING", "BOOLEAN", "INT", "BIGINT", "DECIMAL", "DATE", "TIMESTAMP"];
const IMPORT_UNAVAILABLE_REASON = "当前版本尚无字段级表结构导入契约";

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
	onOpenCode: () => void;
	onOpenAssociation: () => void;
};

export const isBlankModelField = (field: ModelSpecField): boolean => !field.name.trim() && !field.displayName?.trim();

export const dimensionPrimaryKeyPatch = (checked: boolean): Partial<ModelSpecField> =>
	checked ? { role: "KEY", nullable: false } : { role: "ATTRIBUTE" };

export const dimensionNonNullPatch = (checked: boolean): Partial<ModelSpecField> => ({ nullable: !checked });

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
	onOpenCode,
	onOpenAssociation,
}: ModelFieldEditorTableProps) {
	const [insertCount, setInsertCount] = useState("1");
	const [showStandards, setShowStandards] = useState(false);
	const approvedDimensionColumns = ["序号", "字段名称", "类型", "字段显示名", "主键", "非空", "维度属性编码"];
	const headers = dimensionMode
		? [...approvedDimensionColumns, ...(showStandards ? ["字段标准"] : []), "操作"]
		: [
				"序号",
				"字段名称",
				"类型",
				"字段显示名",
				"字段作用",
				...(showStandards ? ["字段标准"] : []),
				"允许为空",
				"操作",
			];
	const addFields = () => {
		const parsed = Number.parseInt(insertCount, 10);
		onAddFields(Number.isFinite(parsed) ? Math.max(1, Math.min(20, parsed)) : 1);
	};

	return (
		<section aria-label="字段编辑器">
			<div className="dmx-table-tools">
				<Button disabled={!canOpenCode || readOnly} onClick={onOpenCode}>
					代码模式
				</Button>
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
				<Button className="right" onClick={() => setShowStandards((current) => !current)}>
					字段显示设置
				</Button>
			</div>
			<div className="dmx-field-table-wrap">
				<table className="dmx-field-table">
					<thead>
						<tr>
							{headers.map((header) => (
								<th key={header}>{header}</th>
							))}
						</tr>
					</thead>
					<tbody>
						{fields.length ? (
							fields.map((field, index) => {
								const binding = bindings.find((item) => item.fieldName === field.name);
								const standardValue = binding ? `${binding.standardElementId}@${binding.standardElementVersion}` : "";
								return (
									<tr key={fieldRowIds[index] || `${field.name}-${index}`}>
										<td>{index + 1}</td>
										<td>
											<input
												disabled={readOnly}
												onChange={(event) => onUpdate(index, { name: event.target.value })}
												value={field.name}
											/>
										</td>
										<td>
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
										</td>
										<td>
											<input
												disabled={readOnly}
												onChange={(event) => onUpdate(index, { displayName: event.target.value })}
												value={field.displayName || ""}
											/>
										</td>
										{dimensionMode ? (
											<>
												<td>
													<input
														aria-label={`字段 ${index + 1} 为主键`}
														checked={field.role === "KEY"}
														disabled={readOnly}
														onChange={(event) => onUpdate(index, dimensionPrimaryKeyPatch(event.target.checked))}
														type="checkbox"
													/>
												</td>
												<td>
													<input
														aria-label={`字段 ${index + 1} 非空`}
														checked={!field.nullable}
														disabled={readOnly}
														onChange={(event) => onUpdate(index, dimensionNonNullPatch(event.target.checked))}
														type="checkbox"
													/>
												</td>
												<td>
													<input
														disabled={readOnly}
														onChange={(event) => onUpdate(index, { dimensionAttributeCode: event.target.value })}
														placeholder="可选"
														value={field.dimensionAttributeCode || ""}
													/>
												</td>
											</>
										) : (
											<>
												<td>
													<select
														disabled={readOnly}
														onChange={(event) =>
															onUpdate(index, { role: event.target.value as ModelSpecField["role"] })
														}
														value={field.role}
													>
														<option value="KEY">键（KEY）</option>
														<option value="ATTRIBUTE">属性</option>
														<option value="TIME">时间</option>
														<option value="MEASURE">度量</option>
													</select>
												</td>
												<td>
													<input
														aria-label={`字段 ${index + 1} 允许为空`}
														checked={field.nullable}
														disabled={readOnly}
														onChange={(event) => onUpdate(index, { nullable: event.target.checked })}
														type="checkbox"
													/>
												</td>
											</>
										)}
										{showStandards ? (
											<td>
												<select
													disabled={readOnly || !field.name.trim()}
													onChange={(event) => onStandardChange(index, event.target.value)}
													value={standardValue}
												>
													<option value="">不绑定</option>
													{standards.map((standard) => (
														<option
															key={`${standard.id}@${standard.version}`}
															value={`${standard.id}@${standard.version}`}
														>
															{standard.name} · {standard.code} · v{standard.version}
														</option>
													))}
												</select>
											</td>
										) : null}
										<td>
											<Button danger disabled={readOnly} onClick={() => onDelete(index)}>
												删除
											</Button>
										</td>
									</tr>
								);
							})
						) : (
							<tr>
								<td colSpan={headers.length}>
									<RequestState description="点击插入字段新增字段。" kind="empty" title="暂无字段" />
								</td>
							</tr>
						)}
					</tbody>
				</table>
			</div>
		</section>
	);
}
