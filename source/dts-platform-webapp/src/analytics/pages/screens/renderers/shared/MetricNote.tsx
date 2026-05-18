/**
 * MetricNote — 大屏指标说明组件
 *
 * 设计原则:
 * - 业务可读: 仅显示口径定义/公式/阈值,不暴露 SQL/表名/字段名
 * - 可选: 仅当组件 config.metricNote 存在时才渲染 ℹ️ 图标
 * - 不打扰: ℹ️ 默认浅色,hover 才展开;支持键盘焦点(a11y)
 * - 阈值感知: 根据 thresholds.rule 自动着色当前值
 *
 * 数据来源: worklog/v2.2.3/s10/v4/pjm/metric-registry.json
 */

import { Popover } from "antd";
import type { CSSProperties, ReactNode } from "react";

export type MetricDirection = "UP" | "DOWN" | "NEUTRAL";

export interface MetricThresholds {
	warning?: number | null;
	critical?: number | null;
	rule?: ">" | "<" | "=" | null;
}

export interface MetricNote {
	displayName?: string;
	domain?: string;
	category?: string;
	unit?: string;
	direction?: MetricDirection;
	definition?: string;
	formula?: string;
	scope?: string;
	sourceTable?: string;
	frequency?: string;
	thresholds?: MetricThresholds | null;
	excelRef?: string;
}

interface MetricNoteBadgeProps {
	note: MetricNote;
	iconColor?: string;
	iconSize?: number;
	style?: CSSProperties;
}

/**
 * 把阈值规则转成可读语言。
 * 例: rule=">" warning=15, critical=25 → "≥ 15 时关注,≥ 25 时预警"
 */
function describeThresholds(t: MetricThresholds | null | undefined, unit?: string): string {
	if (!t) return "";
	const rule = t.rule || "<";
	const u = unit || "";
	const parts: string[] = [];
	if (typeof t.warning === "number") {
		parts.push(`${rule === ">" ? "≥" : "≤"} ${t.warning}${u} 时关注`);
	}
	if (typeof t.critical === "number") {
		parts.push(`${rule === ">" ? "≥" : "≤"} ${t.critical}${u} 时预警`);
	}
	return parts.join(",");
}

function renderRow(label: string, value: ReactNode): ReactNode {
	if (!value) return null;
	return (
		<div style={{ display: "grid", gridTemplateColumns: "72px 1fr", gap: 8, marginTop: 6 }}>
			<div style={{ color: "rgba(15, 23, 42, 0.55)", fontSize: 12, lineHeight: 1.5 }}>{label}</div>
			<div style={{ color: "rgba(15, 23, 42, 0.9)", fontSize: 12, lineHeight: 1.5 }}>{value}</div>
		</div>
	);
}

export function MetricNoteBadge({
	note,
	iconColor = "rgba(255, 255, 255, 0.55)",
	iconSize = 13,
	style,
}: MetricNoteBadgeProps) {
	if (!note) return null;

	const thresholdsText = describeThresholds(note.thresholds, note.unit);
	const directionText =
		note.direction === "UP"
			? "越高越好"
			: note.direction === "DOWN"
				? "越低越好"
				: note.direction === "NEUTRAL"
					? "参考值"
					: "";

	const content = (
		<div style={{ maxWidth: 340, color: "rgba(15, 23, 42, 0.85)", fontSize: 12, lineHeight: 1.5 }}>
			{note.displayName && (
				<div style={{ fontSize: 13, fontWeight: 600, color: "#0f172a", marginBottom: 4 }}>
					{note.displayName}
					{note.unit ? <span style={{ marginLeft: 6, fontWeight: 400, color: "#64748b" }}>· {note.unit}</span> : null}
				</div>
			)}
			{renderRow("定义", note.definition)}
			{renderRow("计算公式", <span style={{ whiteSpace: "pre-wrap" }}>{note.formula}</span>)}
			{renderRow("统计范围", note.scope)}
			{renderRow("数据来源", note.sourceTable)}
			{renderRow("更新频率", note.frequency)}
			{renderRow(
				"评价方向",
				directionText && thresholdsText ? `${directionText} · ${thresholdsText}` : directionText || thresholdsText,
			)}
			{note.excelRef && (
				<div style={{ marginTop: 8, paddingTop: 6, borderTop: "1px dashed rgba(15, 23, 42, 0.12)" }}>
					<span style={{ fontSize: 11, color: "rgba(15, 23, 42, 0.4)" }}>口径源 · {note.excelRef}</span>
				</div>
			)}
		</div>
	);

	return (
		<Popover content={content} placement="topRight" mouseEnterDelay={0.25} overlayStyle={{ maxWidth: 360 }}>
			<span
				role="button"
				aria-label={`查看${note.displayName || ""}指标说明`}
				tabIndex={0}
				style={{
					display: "inline-flex",
					alignItems: "center",
					justifyContent: "center",
					width: iconSize + 4,
					height: iconSize + 4,
					borderRadius: "50%",
					border: `1px solid ${iconColor}`,
					color: iconColor,
					fontSize: iconSize - 2,
					fontWeight: 600,
					fontFamily: "Georgia, serif",
					cursor: "help",
					lineHeight: 1,
					marginLeft: 6,
					verticalAlign: "middle",
					...style,
				}}
			>
				i
			</span>
		</Popover>
	);
}

/**
 * 阈值对比小条 —— 显示当前值 vs 预警线/红线
 *
 * 设计:
 * - 横向 mini-bar,左边是当前值,右边是阈值刻度
 * - 颜色: 当前值优于警戒线 → 绿色; 触及警戒 → 黄色; 触及红线 → 红色
 */
interface ThresholdBarProps {
	value: number | null | undefined;
	thresholds: MetricThresholds | null | undefined;
	direction?: MetricDirection;
	unit?: string;
}

export function ThresholdBar({ value, thresholds, unit = "" }: ThresholdBarProps) {
	if (value == null || !thresholds || thresholds.warning == null) return null;
	const rule = thresholds.rule || "<";
	const warning = Number(thresholds.warning);
	const critical = thresholds.critical != null ? Number(thresholds.critical) : null;

	// 计算状态: ok / warning / critical
	let status: "ok" | "warning" | "critical" = "ok";
	if (rule === "<") {
		// 越高越好,值小于阈值是坏
		if (critical != null && value <= critical) status = "critical";
		else if (value <= warning) status = "warning";
	} else if (rule === ">") {
		// 越低越好,值大于阈值是坏
		if (critical != null && value >= critical) status = "critical";
		else if (value >= warning) status = "warning";
	}

	const colorMap = {
		ok: { fg: "#22c55e", bg: "rgba(34, 197, 94, 0.18)", label: "正常" },
		warning: { fg: "#f59e0b", bg: "rgba(245, 158, 11, 0.18)", label: "关注" },
		critical: { fg: "#ef4444", bg: "rgba(239, 68, 68, 0.20)", label: "预警" },
	};
	const c = colorMap[status];
	const threshLabel = critical != null ? `${warning}${unit} / ${critical}${unit}` : `${warning}${unit}`;

	return (
		<div
			style={{
				display: "inline-flex",
				alignItems: "center",
				gap: 6,
				marginTop: 4,
				padding: "1px 8px",
				borderRadius: 999,
				background: c.bg,
				color: c.fg,
				fontSize: 11,
				lineHeight: 1.5,
				fontWeight: 500,
				maxWidth: "100%",
				overflow: "hidden",
				textOverflow: "ellipsis",
				whiteSpace: "nowrap",
			}}
		>
			<span style={{ width: 6, height: 6, borderRadius: "50%", background: c.fg, flex: "0 0 auto" }} />
			<span>{c.label}</span>
			<span style={{ color: "rgba(148, 163, 184, 0.9)", fontWeight: 400 }}>· 阈值 {threshLabel}</span>
		</div>
	);
}
