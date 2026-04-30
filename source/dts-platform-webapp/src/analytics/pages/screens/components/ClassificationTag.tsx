import { Tag, Tooltip } from 'antd';

/**
 * 大屏密级展示 Tag（Sprint-24 F2/T01）
 *
 * 与 ClassificationSelect 是两个组件，**不复用** —— select 用于"编辑"，
 * tag 用于"展示"。列表卡片、面板只读态、审计明细都用本组件，保证视觉一致。
 *
 * 颜色映射跟随 antd preset，避免引入新色板：
 * - PUBLIC        → 灰色「公开」
 * - INTERNAL      → 蓝色「内部」
 * - SECRET        → 黄色「秘密」
 * - CONFIDENTIAL  → 红色「机密」
 * - null/缺失     → 橙色「未设密级」+ tooltip 警示，便于运维一眼扫
 */
export interface ClassificationTagProps {
	value?: string | null;
	/** 紧凑模式：tag 字号略小，配合卡片右上角 */
	size?: 'small' | 'default';
	/** 自定义样式叠加（margin 等） */
	style?: React.CSSProperties;
}

const COLOR: Record<string, string> = {
	PUBLIC: 'default',
	INTERNAL: 'blue',
	SECRET: 'gold',
	CONFIDENTIAL: 'red',
};

const LABEL: Record<string, string> = {
	PUBLIC: '公开',
	INTERNAL: '内部',
	SECRET: '秘密',
	CONFIDENTIAL: '机密',
};

const UNCLASSIFIED_TOOLTIP =
	'该大屏未设密级，对所有登录用户可见。请联系 owner 在编辑器属性面板补登。';

export function ClassificationTag({ value, size = 'default', style }: ClassificationTagProps) {
	const upper = typeof value === 'string' ? value.trim().toUpperCase() : '';
	const sizeStyle: React.CSSProperties =
		size === 'small' ? { fontSize: 11, padding: '0 6px', lineHeight: '18px' } : {};
	const merged: React.CSSProperties = { margin: 0, ...sizeStyle, ...style };

	if (!upper) {
		return (
			<Tooltip title={UNCLASSIFIED_TOOLTIP}>
				<Tag color="orange" style={merged}>
					未设密级
				</Tag>
			</Tooltip>
		);
	}

	return (
		<Tag color={COLOR[upper] || 'default'} style={merged}>
			{LABEL[upper] || upper}
		</Tag>
	);
}
