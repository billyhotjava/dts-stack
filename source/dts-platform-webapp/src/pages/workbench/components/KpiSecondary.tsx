import { ArrowDownOutlined, ArrowUpOutlined } from "@ant-design/icons";

/**
 * Sprint-15 F4/T02 — Semantic KPI secondary line renderers.
 *
 * The three components render the "副标题" under a KPI card's main value
 * with consistent AntD semantic colors; unknown / null inputs render nothing
 * so we avoid ugly "环比 -" or "占比 NaN%" placeholders.
 */

const POSITIVE_COLOR = "#52c41a";
const NEGATIVE_COLOR = "#ff4d4f";
const NEUTRAL_COLOR = "#8c8c8c";
const SECONDARY_FONT_SIZE = 12;

export interface MoMSecondaryProps {
	mom: number | null | undefined;
	prefix?: string;
}

export function MoMSecondary({ mom, prefix = "环比" }: MoMSecondaryProps) {
	if (mom === null || mom === undefined) return null;
	const pct = Math.abs(mom * 100).toFixed(1);
	if (mom === 0) {
		return <span style={{ color: NEUTRAL_COLOR, fontSize: SECONDARY_FONT_SIZE }}>{prefix} 持平</span>;
	}
	const positive = mom > 0;
	return (
		<span style={{ color: positive ? POSITIVE_COLOR : NEGATIVE_COLOR, fontSize: SECONDARY_FONT_SIZE }}>
			{positive ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {prefix} {pct}%
		</span>
	);
}

export interface RatioSecondaryProps {
	ratio: number | null | undefined;
	prefix?: string;
}

export function RatioSecondary({ ratio, prefix = "占比" }: RatioSecondaryProps) {
	if (ratio === null || ratio === undefined) return null;
	return (
		<span style={{ color: NEUTRAL_COLOR, fontSize: SECONDARY_FONT_SIZE }}>
			{prefix} {(ratio * 100).toFixed(1)}%
		</span>
	);
}

export interface StaticSecondaryProps {
	text: string | null | undefined;
}

export function StaticSecondary({ text }: StaticSecondaryProps) {
	if (!text) return null;
	return <span style={{ color: NEUTRAL_COLOR, fontSize: SECONDARY_FONT_SIZE }}>{text}</span>;
}
