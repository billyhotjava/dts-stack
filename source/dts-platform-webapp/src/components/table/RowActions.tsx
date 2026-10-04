import { Button, Popconfirm, Space, Tooltip } from "antd";
import type { ColumnType } from "antd/es/table";
import type { ReactNode } from "react";
import { Link } from "react-router";

/**
 * 表格行内操作的统一样式。基线取自数据集成（接入概览 / 数据库接入）：
 * 带边框的小号默认按钮 + 纯文字，操作列固定在右侧并给出显式宽度。
 *
 * 不要在页面里手写 <Space><Button …/></Space>——那是各模块样式漂移的来源。
 */
export interface RowAction {
	key: string;
	label: ReactNode;
	onClick?: () => void;
	disabled?: boolean;
	/** 危险动作（删除 / 停用），渲染成 antd 的 danger 按钮 */
	danger?: boolean;
	loading?: boolean;
	/** 条件性动作可直接置 true 隐藏，避免调用方到处写三元表达式 */
	hidden?: boolean;
	/** 悬浮提示，常用于说明按钮为何被禁用 */
	tooltip?: ReactNode;
	/** 填了就套一层 Popconfirm，点击先二次确认 */
	confirm?: ReactNode;
	confirmOkText?: string;
	/** 透传 data-testid，供 e2e 定位 */
	testId?: string;
	/** 透传 aria-label；标签文案相同但对象不同的行内动作需要它来区分 */
	ariaLabel?: string;
	/** 跳转类动作用 href 而不是 onClick，保留 <a> 语义（中键新开页、复制链接） */
	href?: string;
}

export interface RowActionsProps {
	items: RowAction[];
	/** 全部动作都不可用时的占位文案（例如内置记录不可编辑） */
	emptyText?: ReactNode;
}

const renderButton = (item: RowAction) => (
	<Button
		size="small"
		danger={item.danger}
		disabled={item.disabled}
		loading={item.loading}
		data-testid={item.testId}
		aria-label={item.ariaLabel}
		// 字符串 tooltip 同时落到原生 title，便于无障碍读取与非 hover 场景
		title={typeof item.tooltip === "string" ? item.tooltip : undefined}
		onClick={item.confirm ? undefined : item.onClick}
	>
		{item.label}
	</Button>
);

export function RowActions({ items, emptyText = "-" }: RowActionsProps): JSX.Element {
	const visible = items.filter((item) => !item.hidden);
	if (!visible.length) return <span className="text-text-disabled">{emptyText}</span>;

	return (
		<Space size={4}>
			{visible.map((item) => {
				const raw = renderButton(item);
				const button = item.href ? <Link to={item.href}>{raw}</Link> : raw;
				const confirmed = item.confirm ? (
					<Popconfirm
						key={item.key}
						title={item.confirm}
						okText={item.confirmOkText ?? "确定"}
						cancelText="取消"
						onConfirm={item.onClick}
						disabled={item.disabled}
					>
						{button}
					</Popconfirm>
				) : (
					button
				);
				const node = item.tooltip ? <Tooltip title={item.tooltip}>{confirmed}</Tooltip> : confirmed;
				return <span key={item.key}>{node}</span>;
			})}
		</Space>
	);
}

/**
 * 操作列宽度：按动作数量估算，保证按钮在一行内放得下。
 * 单动作沿用数据集成基线的 90；多动作按每个 72 计（两字中文按钮 + 4px 间距）。
 */
export function actionColumnWidth(count: number): number {
	return count <= 1 ? 90 : count * 72 + 24;
}

export interface ActionColumnOptions {
	/** 同一行最多可能出现几个动作，用于估算列宽；也可直接给 width 覆盖 */
	maxActions?: number;
	width?: number;
	title?: string;
	fixed?: ColumnType<never>["fixed"] | false;
}

/**
 * 构造标准操作列：固定标题「操作」、key=actions、右侧冻结、显式宽度。
 * 传入的 render 只需返回该行的动作列表。
 */
export function actionColumn<T>(
	actions: (record: T, index: number) => RowAction[],
	options: ActionColumnOptions = {},
): ColumnType<T> {
	const { maxActions = 1, width, title = "操作", fixed = "right" } = options;
	return {
		title,
		key: "actions",
		dataIndex: "actions",
		fixed: fixed === false ? undefined : fixed,
		width: width ?? actionColumnWidth(maxActions),
		render: (_value: unknown, record: T, index: number) => <RowActions items={actions(record, index)} />,
	};
}

export default RowActions;
