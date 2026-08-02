import { Trash2 } from "lucide-react";
import type { ModelFieldStandardOption } from "@/api/modelingStandardsApi";
import type { ModelSpecFieldRole } from "@/features/modeling/contracts/modelSpecV2Contract";

export type ModelingFieldRow = {
	id: string;
	code: string;
	dataType: string;
	displayName: string;
	role: ModelSpecFieldRole;
	notNull: boolean;
	attributeCode: string;
	standardElementId: string;
	standardElementVersion: number | null;
	originalCode: string;
	sourceFieldRef: string | null;
	securityLevel: string | null;
	redundant: boolean;
	redundancySourceRef: string | null;
};

type Props = {
	rows: ModelingFieldRow[];
	standardOptions: ModelFieldStandardOption[];
	visibleColumns: Set<string>;
	onDelete: (id: string) => void;
	onPatch: (id: string, patch: Partial<ModelingFieldRow>) => void;
};

export function ModelingFieldTable({ rows, standardOptions, visibleColumns, onDelete, onPatch }: Props) {
	const patchStandard = (id: string, standardElementId: string) => {
		const option = standardOptions.find((item) => item.id === standardElementId);
		onPatch(id, {
			standardElementId: option?.id || "",
			standardElementVersion: option?.version || null,
		});
	};

	return (
		<div className="dm-field-table-wrap">
			<table className="dm-field-table">
				<thead>
					<tr>
						{visibleColumns.has("sequence") ? <th>序号</th> : null}
						{visibleColumns.has("code") ? <th>字段名称</th> : null}
						{visibleColumns.has("dataType") ? <th>类型</th> : null}
						{visibleColumns.has("displayName") ? <th>字段显示名</th> : null}
						{visibleColumns.has("primaryKey") ? <th>字段作用</th> : null}
						{visibleColumns.has("notNull") ? <th>非空</th> : null}
						{visibleColumns.has("attributeCode") ? <th>维度属性编码</th> : null}
						<th>数据标准</th>
						{visibleColumns.has("operation") ? <th>操作</th> : null}
					</tr>
				</thead>
				<tbody>
					{rows.map((row, index) => (
						<tr key={row.id}>
							{visibleColumns.has("sequence") ? <td>{index + 1}</td> : null}
							{visibleColumns.has("code") ? (
								<td>
									<input
										aria-label={`第 ${index + 1} 行字段名称`}
										onChange={(event) => onPatch(row.id, { code: event.target.value })}
										placeholder="field_name"
										value={row.code}
									/>
								</td>
							) : null}
							{visibleColumns.has("dataType") ? (
								<td>
									<select
										aria-label={`第 ${index + 1} 行数据类型`}
										onChange={(event) => onPatch(row.id, { dataType: event.target.value })}
										value={row.dataType}
									>
										{["STRING", "INT", "BIGINT", "DECIMAL(18,2)", "BOOLEAN", "DATE", "TIMESTAMP"].map((type) => (
											<option key={type}>{type}</option>
										))}
									</select>
								</td>
							) : null}
							{visibleColumns.has("displayName") ? (
								<td>
									<input
										aria-label={`第 ${index + 1} 行字段显示名`}
										onChange={(event) => onPatch(row.id, { displayName: event.target.value })}
										placeholder="字段中文名"
										value={row.displayName}
									/>
								</td>
							) : null}
							{visibleColumns.has("primaryKey") ? (
								<td>
									<select
										aria-label={`第 ${index + 1} 行字段作用`}
										onChange={(event) => onPatch(row.id, { role: event.target.value as ModelSpecFieldRole })}
										value={row.role}
									>
										{["KEY", "ATTRIBUTE", "TIME", "MEASURE"].map((role) => (
											<option key={role}>{role}</option>
										))}
									</select>
								</td>
							) : null}
							{visibleColumns.has("notNull") ? (
								<td className="dm-field-table__check">
									<input
										aria-label={`第 ${index + 1} 行非空`}
										checked={row.notNull}
										onChange={(event) => onPatch(row.id, { notNull: event.target.checked })}
										type="checkbox"
									/>
								</td>
							) : null}
							{visibleColumns.has("attributeCode") ? (
								<td>
									<input
										aria-label={`第 ${index + 1} 行维度属性编码`}
										onChange={(event) => onPatch(row.id, { attributeCode: event.target.value.toUpperCase() })}
										placeholder="可选"
										value={row.attributeCode}
									/>
								</td>
							) : null}
							<td>
								<select
									aria-label={`第 ${index + 1} 行数据标准`}
									onChange={(event) => patchStandard(row.id, event.target.value)}
									value={row.standardElementId}
								>
									<option value="">不绑定</option>
									{standardOptions.map((option) => (
										<option key={`${option.id}:${option.version}`} value={option.id}>
											{option.code} · {option.name} · v{option.version}
										</option>
									))}
								</select>
							</td>
							{visibleColumns.has("operation") ? (
								<td>
									<button
										aria-label={`删除第 ${index + 1} 行`}
										className="dm-text-action dm-text-action--danger"
										onClick={() => onDelete(row.id)}
										type="button"
									>
										<Trash2 aria-hidden="true" size={13} />
										删除
									</button>
								</td>
							) : null}
						</tr>
					))}
				</tbody>
			</table>
		</div>
	);
}
