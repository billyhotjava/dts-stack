import { Code2, Link2, ListChecks, RefreshCw, Settings2, TableProperties, Trash2 } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { MODEL_FIELD_DISPLAY_COLUMNS, type ModelingDialogKind, ModelingDialogs } from "./ModelingDialogs";
import { ActionButton, BackendPendingButton, StatusTag } from "./WorkspacePage";

export type ModelObjectType = "dimension" | "source" | "dimension-table" | "fact" | "aggregate" | "application";

export type ModelSelection = {
	type: ModelObjectType;
	code: string;
	name: string;
	layer: string;
	domain: string;
	isNew?: boolean;
};

type BasicField = {
	label: string;
	value: string;
	kind?: "input" | "select" | "textarea";
	required?: boolean;
};

type FieldRow = {
	id: string;
	code: string;
	dataType: string;
	displayName: string;
	primaryKey: boolean;
	notNull: boolean;
	attributeCode: string;
};

const BASIC_FIELDS: Record<ModelObjectType, BasicField[]> = {
	dimension: [
		{ label: "数仓分层", value: "公共层 / 维度层", kind: "select", required: true },
		{ label: "业务分类", value: "财务管理", kind: "select" },
		{ label: "数据域", value: "财务域", kind: "select", required: true },
		{ label: "英文缩写", value: "budget_account", required: true },
		{ label: "中文名称", value: "预算科目", required: true },
		{ label: "描述", value: "财务预算分析使用的统一预算科目。", kind: "textarea" },
	],
	source: [
		{ label: "数仓分层", value: "贴源层", kind: "select", required: true },
		{ label: "业务分类", value: "财务管理", kind: "select" },
		{ label: "存储策略", value: "默认存储策略", kind: "select" },
		{ label: "表名规则", value: "ODS 表命名规范", kind: "select" },
		{ label: "表名", value: "ods_budget_execution", required: true },
		{ label: "表中文名", value: "预算执行导入表", required: true },
		{ label: "生命周期", value: "365" },
		{ label: "负责人", value: "示例负责人", kind: "select", required: true },
		{ label: "描述", value: "离线 Excel 导入的预算执行原始数据。", kind: "textarea" },
	],
	"dimension-table": [
		{ label: "数仓分层", value: "公共层 / 维度层", kind: "select", required: true },
		{ label: "业务分类", value: "财务管理", kind: "select" },
		{ label: "数据域", value: "财务域", kind: "select", required: true },
		{ label: "存储策略", value: "默认存储策略", kind: "select" },
		{ label: "维度", value: "预算科目", kind: "select", required: true },
		{ label: "表名规则", value: "DIM 表命名规范", kind: "select" },
		{ label: "表名", value: "dim_budget_account", required: true },
		{ label: "表中文名", value: "预算科目维度表", required: true },
		{ label: "生命周期", value: "长期" },
		{ label: "负责人", value: "示例负责人", kind: "select", required: true },
		{ label: "描述", value: "统一维护预算科目编码、名称、类别及状态。", kind: "textarea" },
	],
	fact: [
		{ label: "数仓分层", value: "公共层 / 明细层", kind: "select", required: true },
		{ label: "业务分类", value: "财务管理", kind: "select" },
		{ label: "业务过程", value: "预算执行", kind: "select", required: true },
		{ label: "存储策略", value: "默认存储策略", kind: "select" },
		{ label: "表名规则", value: "DWD 表命名规范", kind: "select" },
		{ label: "表名", value: "fct_budget_execution", required: true },
		{ label: "表中文名", value: "预算执行明细表", required: true },
		{ label: "生命周期", value: "1095" },
		{ label: "负责人", value: "示例负责人", kind: "select", required: true },
		{
			label: "描述",
			value: "一个预算执行事项一行，记录预算、预付、成本与应付金额。",
			kind: "textarea",
		},
	],
	aggregate: [
		{ label: "数仓分层", value: "公共层 / 汇总层", kind: "select", required: true },
		{ label: "业务分类", value: "财务管理", kind: "select" },
		{ label: "数据域", value: "财务域", kind: "select", required: true },
		{ label: "统计粒度", value: "项目 + 预算科目 + 月", kind: "select", required: true },
		{ label: "统计周期", value: "月", kind: "select", required: true },
		{ label: "修饰词", value: "累计", kind: "select" },
		{ label: "表名规则", value: "DWS 表命名规范", kind: "select" },
		{ label: "表名", value: "agg_budget_monthly", required: true },
		{ label: "表中文名", value: "月度预算执行汇总表", required: true },
		{ label: "生命周期", value: "1095" },
		{ label: "负责人", value: "示例负责人", kind: "select", required: true },
		{ label: "描述", value: "按项目、预算科目和月份汇总预算执行金额。", kind: "textarea" },
	],
	application: [
		{ label: "数仓分层", value: "应用层", kind: "select", required: true },
		{ label: "业务分类", value: "财务管理", kind: "select" },
		{ label: "主题域", value: "预算驾驶舱", kind: "select", required: true },
		{ label: "统计粒度", value: "项目 + 月", kind: "select", required: true },
		{ label: "统计周期", value: "月", kind: "select", required: true },
		{ label: "修饰词", value: "累计", kind: "select" },
		{ label: "表名规则", value: "ADS 表命名规范", kind: "select" },
		{ label: "表名", value: "app_budget_dashboard", required: true },
		{ label: "表中文名", value: "预算驾驶舱应用表", required: true },
		{ label: "生命周期", value: "1095" },
		{ label: "负责人", value: "示例负责人", kind: "select", required: true },
		{ label: "描述", value: "面向预算驾驶舱的发布数据集。", kind: "textarea" },
	],
};

const FIELD_SEEDS: Record<ModelObjectType, Array<Omit<FieldRow, "id">>> = {
	dimension: [
		{
			code: "account_code",
			dataType: "STRING",
			displayName: "科目编码",
			primaryKey: true,
			notNull: true,
			attributeCode: "ACCOUNT_CODE",
		},
		{
			code: "account_name",
			dataType: "STRING",
			displayName: "科目名称",
			primaryKey: false,
			notNull: true,
			attributeCode: "ACCOUNT_NAME",
		},
	],
	source: [
		{
			code: "project_no",
			dataType: "STRING",
			displayName: "项目号",
			primaryKey: false,
			notNull: false,
			attributeCode: "",
		},
		{
			code: "budget_no",
			dataType: "STRING",
			displayName: "预算编号",
			primaryKey: false,
			notNull: false,
			attributeCode: "",
		},
		{
			code: "budget_amount_adjusted",
			dataType: "DECIMAL(18,2)",
			displayName: "调整后预算金额",
			primaryKey: false,
			notNull: false,
			attributeCode: "",
		},
		{
			code: "_dts_import_time",
			dataType: "TIMESTAMP",
			displayName: "导入时间",
			primaryKey: false,
			notNull: false,
			attributeCode: "",
		},
	],
	"dimension-table": [
		{
			code: "account_code",
			dataType: "STRING",
			displayName: "科目编码",
			primaryKey: true,
			notNull: true,
			attributeCode: "ACCOUNT_CODE",
		},
		{
			code: "account_name",
			dataType: "STRING",
			displayName: "科目名称",
			primaryKey: false,
			notNull: true,
			attributeCode: "ACCOUNT_NAME",
		},
		{
			code: "account_category",
			dataType: "STRING",
			displayName: "科目类别",
			primaryKey: false,
			notNull: false,
			attributeCode: "ACCOUNT_CATEGORY",
		},
		{
			code: "enabled_flag",
			dataType: "BOOLEAN",
			displayName: "是否启用",
			primaryKey: false,
			notNull: true,
			attributeCode: "ENABLED_FLAG",
		},
	],
	fact: [
		{
			code: "budget_execution_id",
			dataType: "BIGINT",
			displayName: "预算执行主键",
			primaryKey: true,
			notNull: true,
			attributeCode: "",
		},
		{
			code: "date_key",
			dataType: "INT",
			displayName: "财务日期键",
			primaryKey: false,
			notNull: true,
			attributeCode: "DATE_KEY",
		},
		{
			code: "account_code",
			dataType: "STRING",
			displayName: "预算科目编码",
			primaryKey: false,
			notNull: true,
			attributeCode: "ACCOUNT_CODE",
		},
		{
			code: "project_no",
			dataType: "STRING",
			displayName: "项目号",
			primaryKey: false,
			notNull: true,
			attributeCode: "PROJECT_NO",
		},
		{
			code: "budget_amount",
			dataType: "DECIMAL(18,2)",
			displayName: "预算金额",
			primaryKey: false,
			notNull: false,
			attributeCode: "",
		},
	],
	aggregate: [
		{
			code: "month_key",
			dataType: "INT",
			displayName: "月份键",
			primaryKey: true,
			notNull: true,
			attributeCode: "",
		},
		{
			code: "project_no",
			dataType: "STRING",
			displayName: "项目号",
			primaryKey: true,
			notNull: true,
			attributeCode: "PROJECT_NO",
		},
		{
			code: "account_code",
			dataType: "STRING",
			displayName: "预算科目编码",
			primaryKey: true,
			notNull: true,
			attributeCode: "ACCOUNT_CODE",
		},
		{
			code: "execution_rate",
			dataType: "DECIMAL(9,4)",
			displayName: "预算执行率",
			primaryKey: false,
			notNull: false,
			attributeCode: "",
		},
	],
	application: [
		{
			code: "month_key",
			dataType: "INT",
			displayName: "月份键",
			primaryKey: true,
			notNull: true,
			attributeCode: "",
		},
		{
			code: "project_no",
			dataType: "STRING",
			displayName: "项目号",
			primaryKey: true,
			notNull: true,
			attributeCode: "PROJECT_NO",
		},
		{
			code: "budget_amount",
			dataType: "DECIMAL(18,2)",
			displayName: "预算金额",
			primaryKey: false,
			notNull: false,
			attributeCode: "",
		},
		{
			code: "executed_amount",
			dataType: "DECIMAL(18,2)",
			displayName: "已执行金额",
			primaryKey: false,
			notNull: false,
			attributeCode: "",
		},
	],
};

function buildBasicValues(selection: ModelSelection) {
	const values = Object.fromEntries(BASIC_FIELDS[selection.type].map((field) => [field.label, field.value]));
	if (selection.type === "dimension") {
		values["英文缩写"] = selection.code.replace(/^dim_/, "");
		values["中文名称"] = selection.name.replace(/维度表$/, "");
	} else {
		values["表名"] = selection.code;
		values["表中文名"] = selection.name;
	}
	values["数仓分层"] = selection.layer;
	values["数据域"] = selection.domain;
	return values;
}

function seedRows(selection: ModelSelection): FieldRow[] {
	const seeds =
		selection.code === "dim_fin_date"
			? [
					{
						code: "date_key",
						dataType: "INT",
						displayName: "日期键",
						primaryKey: true,
						notNull: true,
						attributeCode: "DATE_KEY",
					},
					{
						code: "full_date",
						dataType: "DATE",
						displayName: "完整日期",
						primaryKey: false,
						notNull: true,
						attributeCode: "FULL_DATE",
					},
					{
						code: "month_no",
						dataType: "INT",
						displayName: "月份",
						primaryKey: false,
						notNull: true,
						attributeCode: "MONTH_NO",
					},
				]
			: FIELD_SEEDS[selection.type];
	return seeds.map((field, index) => ({ ...field, id: `${selection.code}-${index}` }));
}

export function ModelingEditor({ selection }: { selection: ModelSelection }) {
	const [basicValues, setBasicValues] = useState<Record<string, string>>(() => buildBasicValues(selection));
	const [rows, setRows] = useState<FieldRow[]>(() => seedRows(selection));
	const [mode, setMode] = useState<"quick" | "code">("quick");
	const [insertCount, setInsertCount] = useState(1);
	const [dialog, setDialog] = useState<ModelingDialogKind>(null);
	const [visibleColumns, setVisibleColumns] = useState<Set<string>>(
		() => new Set(MODEL_FIELD_DISPLAY_COLUMNS.map(([key]) => key)),
	);
	const rowCounter = useRef(100);

	useEffect(() => {
		setBasicValues(buildBasicValues(selection));
		setRows(seedRows(selection));
		setMode("quick");
	}, [selection]);

	const codePreview = useMemo(
		() =>
			[
				`MODEL ${selection.code} {`,
				...rows.map(
					(row) =>
						`  ${row.code || "new_field"} ${row.dataType}${row.primaryKey ? " KEY" : ""}${row.notNull ? " NOT NULL" : ""};`,
				),
				"}",
			].join("\n"),
		[rows, selection.code],
	);

	const updateRow = <K extends keyof FieldRow>(id: string, key: K, value: FieldRow[K]) => {
		setRows((current) => current.map((row) => (row.id === id ? { ...row, [key]: value } : row)));
	};

	const addRows = () => {
		const additions = Array.from({ length: Math.max(1, insertCount) }, () => ({
			id: `new-field-${rowCounter.current++}`,
			code: "",
			dataType: "STRING",
			displayName: "",
			primaryKey: false,
			notNull: false,
			attributeCode: "",
		}));
		setRows((current) => [...current, ...additions]);
	};

	const resetEditor = () => {
		setBasicValues(buildBasicValues(selection));
		setRows(seedRows(selection));
	};

	return (
		<section className="dm-model-editor">
			<div className="dm-editor-tabs">
				<div className="dm-editor-tab is-active">
					<TableProperties aria-hidden="true" size={15} />
					<strong>{selection.name}</strong>
					<span>{selection.isNew ? "新建" : "草稿 v1"}</span>
				</div>
			</div>
			<div className="dm-editor-toolbar">
				<BackendPendingButton>保存</BackendPendingButton>
				<BackendPendingButton>提交</BackendPendingButton>
				<ActionButton onClick={resetEditor}>
					<RefreshCw aria-hidden="true" size={14} />
					刷新
				</ActionButton>
				<ActionButton onClick={() => setDialog("association")}>
					<Link2 aria-hidden="true" size={14} />
					关联关系
				</ActionButton>
				<ActionButton onClick={() => setDialog("release")}>发布与物化</ActionButton>
				<ActionButton disabled title="后台重构阶段接入运行日志">
					日志
				</ActionButton>
				<ActionButton disabled title="后台重构阶段接入质量规则">
					质量规则
				</ActionButton>
				<span className="dm-editor-toolbar__status">
					<StatusTag tone="warning">未发布</StatusTag>
				</span>
			</div>
			<div className="dm-model-editor__scroll">
				<section className="dm-editor-section">
					<h2>基本信息</h2>
					<div className="dm-model-form">
						{BASIC_FIELDS[selection.type].map((field) => {
							const value = basicValues[field.label] ?? "";
							const wide = field.kind === "textarea";
							return (
								<div className={`dm-model-form__field ${wide ? "dm-model-form__field--wide" : ""}`} key={field.label}>
									<span>
										{field.required ? <b>*</b> : null}
										{field.label}
									</span>
									{field.kind === "select" ? (
										<select
											aria-label={field.label}
											className="dm-select"
											onChange={(event) =>
												setBasicValues((current) => ({
													...current,
													[field.label]: event.target.value,
												}))
											}
											value={value}
										>
											<option>{value}</option>
											<option>请选择</option>
										</select>
									) : field.kind === "textarea" ? (
										<textarea
											aria-label={field.label}
											className="dm-textarea"
											onChange={(event) =>
												setBasicValues((current) => ({
													...current,
													[field.label]: event.target.value,
												}))
											}
											rows={2}
											value={value}
										/>
									) : (
										<input
											aria-label={field.label}
											className="dm-input"
											onChange={(event) =>
												setBasicValues((current) => ({
													...current,
													[field.label]: event.target.value,
												}))
											}
											value={value}
										/>
									)}
								</div>
							);
						})}
					</div>
				</section>

				<section className="dm-editor-section">
					<div className="dm-editor-section__title">
						<h2>字段管理</h2>
						<fieldset className="dm-segmented">
							<legend>字段编辑模式</legend>
							<button className={mode === "quick" ? "is-active" : ""} onClick={() => setMode("quick")} type="button">
								<ListChecks aria-hidden="true" size={13} />
								快捷模式
							</button>
							<button className={mode === "code" ? "is-active" : ""} onClick={() => setMode("code")} type="button">
								<Code2 aria-hidden="true" size={13} />
								代码模式
							</button>
						</fieldset>
					</div>
					{mode === "code" ? (
						<div className="dm-code-editor">
							<div>
								<span>FML 模型定义预览</span>
								<StatusTag tone="info">只读预览</StatusTag>
							</div>
							<textarea aria-label="FML 模型定义" readOnly spellCheck={false} value={codePreview} />
						</div>
					) : (
						<>
							<div className="dm-field-import">
								<span>从表/视图导入</span>
								<small>后台接入后可选择数据源并识别字段</small>
								<ActionButton disabled>选择上游表</ActionButton>
								<ActionButton kind="quiet" onClick={() => setDialog("association")}>
									字段关联
								</ActionButton>
							</div>
							<div className="dm-field-actions">
								<ActionButton
									onClick={() =>
										setRows((current) => current.filter((row) => row.code || row.displayName || row.attributeCode))
									}
								>
									移除空白行
								</ActionButton>
								<label>
									插入
									<input
										aria-label="插入行数"
										max={20}
										min={1}
										onChange={(event) => setInsertCount(Number(event.target.value) || 1)}
										type="number"
										value={insertCount}
									/>
									行
								</label>
								<ActionButton onClick={addRows}>添加</ActionButton>
								<span className="dm-field-actions__spacer" />
								<ActionButton onClick={() => setDialog("display")}>
									<Settings2 aria-hidden="true" size={14} />
									字段显示设置
								</ActionButton>
							</div>
							<div className="dm-field-table-wrap">
								<table className="dm-field-table">
									<thead>
										<tr>
											{visibleColumns.has("sequence") ? <th>序号</th> : null}
											{visibleColumns.has("code") ? <th>字段名称</th> : null}
											{visibleColumns.has("dataType") ? <th>类型</th> : null}
											{visibleColumns.has("displayName") ? <th>字段显示名</th> : null}
											{visibleColumns.has("primaryKey") ? <th>主键</th> : null}
											{visibleColumns.has("notNull") ? <th>非空</th> : null}
											{visibleColumns.has("attributeCode") ? <th>维度属性编码</th> : null}
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
															onChange={(event) => updateRow(row.id, "code", event.target.value)}
															placeholder="field_name"
															value={row.code}
														/>
													</td>
												) : null}
												{visibleColumns.has("dataType") ? (
													<td>
														<select
															aria-label={`第 ${index + 1} 行数据类型`}
															onChange={(event) => updateRow(row.id, "dataType", event.target.value)}
															value={row.dataType}
														>
															{["STRING", "INT", "BIGINT", "DECIMAL(18,2)", "BOOLEAN", "DATE", "TIMESTAMP"].map(
																(type) => (
																	<option key={type}>{type}</option>
																),
															)}
														</select>
													</td>
												) : null}
												{visibleColumns.has("displayName") ? (
													<td>
														<input
															aria-label={`第 ${index + 1} 行字段显示名`}
															onChange={(event) => updateRow(row.id, "displayName", event.target.value)}
															placeholder="字段中文名"
															value={row.displayName}
														/>
													</td>
												) : null}
												{visibleColumns.has("primaryKey") ? (
													<td className="dm-field-table__check">
														<input
															aria-label={`第 ${index + 1} 行主键`}
															checked={row.primaryKey}
															onChange={(event) => updateRow(row.id, "primaryKey", event.target.checked)}
															type="checkbox"
														/>
													</td>
												) : null}
												{visibleColumns.has("notNull") ? (
													<td className="dm-field-table__check">
														<input
															aria-label={`第 ${index + 1} 行非空`}
															checked={row.notNull}
															onChange={(event) => updateRow(row.id, "notNull", event.target.checked)}
															type="checkbox"
														/>
													</td>
												) : null}
												{visibleColumns.has("attributeCode") ? (
													<td>
														<input
															aria-label={`第 ${index + 1} 行维度属性编码`}
															onChange={(event) => updateRow(row.id, "attributeCode", event.target.value)}
															placeholder="可选"
															value={row.attributeCode}
														/>
													</td>
												) : null}
												{visibleColumns.has("operation") ? (
													<td>
														<button
															aria-label={`删除第 ${index + 1} 行`}
															className="dm-text-action dm-text-action--danger"
															onClick={() => setRows((current) => current.filter((item) => item.id !== row.id))}
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
							<div className="dm-partition-row">
								<strong>分区字段</strong>
								<code>ds</code>
								<span>STRING</span>
								<span>业务日期，格式 yyyymmdd</span>
								<StatusTag tone="info">非空</StatusTag>
							</div>
						</>
					)}
				</section>
			</div>

			<ModelingDialogs
				dialog={dialog}
				onClose={() => setDialog(null)}
				onToggleColumn={(key, checked) =>
					setVisibleColumns((current) => {
						const next = new Set(current);
						if (checked) next.add(key);
						else next.delete(key);
						return next;
					})
				}
				rows={rows}
				selectionCode={selection.code}
				visibleColumns={visibleColumns}
			/>
		</section>
	);
}
