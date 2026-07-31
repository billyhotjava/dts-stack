import { AlertTriangle, Calculator, Sigma, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { BackendPendingButton, StatusTag } from "./WorkspacePage";

export type MetricType = "复合指标" | "派生指标" | "原子指标" | "修饰词" | "时间周期";

export type MetricSelection = {
	domain: string;
	code: string;
	name: string;
	isNew?: boolean;
};

type MetricField = {
	label: string;
	value: string;
	kind?: "input" | "select" | "textarea" | "radio";
	required?: boolean;
	note?: string;
};

type MetricSection = {
	title: string;
	icon: "basic" | "calculation";
	fields: MetricField[];
};

const commonFields = (selection: MetricSelection, type: MetricType): MetricField[] => [
	{
		label: "英文缩写",
		value: selection.code,
		required: true,
		note: `${type}英文缩写是唯一性标识，一经保存无法修改。`,
	},
	{ label: "英文名称", value: selection.code ? `${selection.code} metric` : "" },
	{ label: "中文名称", value: selection.name, required: true },
];

function metricSections(type: MetricType, selection: MetricSelection): MetricSection[] {
	const common = commonFields(selection, type);
	if (type === "原子指标") {
		return [
			{
				title: "原子指标基本信息",
				icon: "basic",
				fields: [
					...common,
					{
						label: "业务口径",
						value: selection.name ? `${selection.name}的统一业务统计口径。` : "",
						kind: "textarea",
						required: true,
						note: "输入 @ 可以引用其他原子指标。",
					},
					{ label: "业务分类", value: "财务管理", kind: "select" },
					{ label: "业务过程", value: "预算执行", kind: "select", required: true },
					{ label: "负责人", value: "示例负责人", kind: "select", required: true },
					{ label: "描述", value: "", kind: "textarea" },
				],
			},
			{
				title: "计算逻辑",
				icon: "calculation",
				fields: [
					{ label: "计算函数", value: "SUM", kind: "select" },
					{ label: "小数位数", value: "0" },
					{ label: "数据单位", value: "元", kind: "select" },
					{ label: "是否去重", value: "否", kind: "radio" },
				],
			},
		];
	}
	if (type === "派生指标") {
		return [
			{
				title: "派生指标基本信息",
				icon: "basic",
				fields: [
					...common,
					{ label: "原子指标", value: "executed_amount", kind: "select", required: true },
					{ label: "业务分类", value: "财务管理", kind: "select" },
					{ label: "负责人", value: "示例负责人", kind: "select", required: true },
					{ label: "描述", value: "", kind: "textarea" },
				],
			},
			{
				title: "派生规则",
				icon: "calculation",
				fields: [
					{ label: "时间周期", value: "月", kind: "select", required: true },
					{ label: "修饰词", value: "累计", kind: "select" },
					{ label: "统计粒度", value: "项目 + 预算科目", kind: "select", required: true },
					{
						label: "计算表达式",
						value: "executed_amount / budget_amount",
						kind: "textarea",
						required: true,
					},
				],
			},
		];
	}
	if (type === "复合指标") {
		return [
			{
				title: "复合指标基本信息",
				icon: "basic",
				fields: [
					...common,
					{ label: "业务分类", value: "财务管理", kind: "select" },
					{ label: "负责人", value: "示例负责人", kind: "select", required: true },
					{ label: "描述", value: "", kind: "textarea" },
				],
			},
			{
				title: "计算逻辑",
				icon: "calculation",
				fields: [
					{
						label: "依赖指标",
						value: "monthly_execution_rate, project_budget_variance",
						kind: "select",
						required: true,
					},
					{
						label: "计算表达式",
						value: "0.6 * monthly_execution_rate + 0.4 * variance_score",
						kind: "textarea",
						required: true,
					},
					{ label: "小数位数", value: "2" },
					{ label: "数据单位", value: "分", kind: "select" },
				],
			},
		];
	}
	if (type === "修饰词") {
		return [
			{
				title: "修饰词基本信息",
				icon: "basic",
				fields: [
					...common,
					{ label: "修饰词类型", value: "统计方式", kind: "select", required: true },
					{ label: "负责人", value: "示例负责人", kind: "select", required: true },
					{ label: "描述", value: "", kind: "textarea" },
				],
			},
			{
				title: "应用范围",
				icon: "calculation",
				fields: [
					{ label: "数据域", value: "全部数据域", kind: "select" },
					{ label: "适用指标类型", value: "原子指标、派生指标", kind: "select" },
					{ label: "排序", value: "10" },
				],
			},
		];
	}
	return [
		{
			title: "时间周期基本信息",
			icon: "basic",
			fields: [
				...common,
				{ label: "周期类型", value: "自然周期", kind: "select", required: true },
				{ label: "负责人", value: "示例负责人", kind: "select", required: true },
				{ label: "描述", value: "", kind: "textarea" },
			],
		},
		{
			title: "周期规则",
			icon: "calculation",
			fields: [
				{ label: "时间粒度", value: "月", kind: "select", required: true },
				{ label: "开始偏移", value: "0" },
				{ label: "结束偏移", value: "0" },
				{ label: "日期格式", value: "YYYY-MM", kind: "select" },
			],
		},
	];
}

export function MetricEditor({ type, selection }: { type: MetricType; selection: MetricSelection }) {
	const sections = useMemo(() => metricSections(type, selection), [selection, type]);
	const [values, setValues] = useState<Record<string, string>>(() =>
		Object.fromEntries(sections.flatMap((section) => section.fields.map((field) => [field.label, field.value]))),
	);

	useEffect(() => {
		setValues(
			Object.fromEntries(sections.flatMap((section) => section.fields.map((field) => [field.label, field.value]))),
		);
	}, [sections]);

	const updateValue = (label: string, value: string) => {
		setValues((current) => ({ ...current, [label]: value }));
	};

	return (
		<section className="dm-metric-editor">
			<div className="dm-editor-tabs">
				<div className="dm-editor-tab is-active">
					<Sigma aria-hidden="true" size={15} />
					<strong>{selection.isNew ? `新建${type}` : selection.name}</strong>
					<span>{type}</span>
					<X aria-hidden="true" size={13} />
				</div>
			</div>
			<div className="dm-editor-toolbar">
				<BackendPendingButton>保存</BackendPendingButton>
				<BackendPendingButton>提交</BackendPendingButton>
				<span className="dm-editor-toolbar__status">
					<StatusTag tone={selection.isNew ? "info" : "warning"}>{selection.isNew ? "新建" : "草稿"}</StatusTag>
				</span>
			</div>
			<div className="dm-metric-editor__scroll">
				{sections.map((section) => (
					<section className="dm-metric-form-section" key={section.title}>
						<h2>
							{section.icon === "calculation" ? (
								<Calculator aria-hidden="true" size={16} />
							) : (
								<Sigma aria-hidden="true" size={16} />
							)}
							{section.title}
						</h2>
						<div className="dm-metric-form">
							{section.fields.map((field) => {
								const value = values[field.label] ?? "";
								const isTextarea = field.kind === "textarea";
								return (
									<div
										className={`dm-metric-form__row ${isTextarea ? "dm-metric-form__row--textarea" : ""}`}
										key={field.label}
									>
										<span>
											{field.required ? <b>*</b> : null}
											{field.label}：
										</span>
										<div>
											{field.kind === "select" ? (
												<select
													aria-label={field.label}
													className="dm-select"
													onChange={(event) => updateValue(field.label, event.target.value)}
													value={value}
												>
													<option>{value || `请选择${field.label}`}</option>
													<option>请选择</option>
												</select>
											) : field.kind === "textarea" ? (
												<textarea
													aria-label={field.label}
													className="dm-textarea"
													onChange={(event) => updateValue(field.label, event.target.value)}
													placeholder={`请输入${field.label}`}
													rows={3}
													value={value}
												/>
											) : field.kind === "radio" ? (
												<div className="dm-radio-group">
													{["是", "否"].map((option) => (
														<label key={option}>
															<input
																checked={value === option}
																name={`${type}-${field.label}`}
																onChange={() => updateValue(field.label, option)}
																type="radio"
															/>
															{option}
														</label>
													))}
												</div>
											) : (
												<input
													aria-label={field.label}
													className="dm-input"
													onChange={(event) => updateValue(field.label, event.target.value)}
													placeholder={`请输入${field.label}`}
													value={value}
												/>
											)}
											{field.note ? (
												<p className={field.label === "英文缩写" ? "dm-metric-warning" : "dm-metric-note"}>
													{field.label === "英文缩写" ? <AlertTriangle aria-hidden="true" size={13} /> : null}
													{field.note}
												</p>
											) : null}
										</div>
									</div>
								);
							})}
						</div>
					</section>
				))}
			</div>
		</section>
	);
}
