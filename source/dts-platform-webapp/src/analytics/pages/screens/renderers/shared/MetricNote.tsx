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

/**
 * 系列说明 — 用于图表场景,每条系列(图例)对应一个权威指标。
 * 渲染时按 code/name + formula/definition 紧凑列出。
 */
export interface MetricSeriesNote {
	/** 系列名称(图例上的文字) */
	name: string;
	/** 指标 code(对齐 registry) */
	code?: string;
	/** 指标显示名(冗余,便于离线快速展示) */
	displayName?: string;
	/** 计算口径(简短) */
	formula?: string;
	/** 单位 */
	unit?: string;
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
	/** 图表场景: 各系列的指标口径(按图例顺序) */
	seriesNotes?: MetricSeriesNote[];
}

/**
 * FieldNote — 字段字典说明(用于明细表的枚举类列)。
 *
 * 与 MetricNote 区别:
 *   - MetricNote 描述"计算指标"(有公式/阈值)
 *   - FieldNote   描述"字段含义 + 枚举取值表"(列代码与业务标签的对照)
 *
 * 数据源: 后端 dim_*_v2.sql 字典表(在 metric-registry.json 的 fieldDictionary 节点维护)。
 */
export interface FieldEnumEntry {
	code: string;
	label: string;
	description?: string;
}

export interface FieldNote {
	/** 字段中文名 */
	displayName: string;
	/** 字段含义说明 */
	definition?: string;
	/** 枚举取值表 */
	enumValues?: FieldEnumEntry[];
	/** 数据来源(业务可读) */
	sourceTable?: string;
	/** 业务备注 */
	note?: string;
	/** 是 fieldNote 不是 metricNote 的标识 */
	kind: "field";
}

export type ColumnNote = MetricNote | FieldNote;

function isFieldNote(n: unknown): n is FieldNote {
	return !!n && typeof n === "object" && (n as { kind?: string }).kind === "field";
}

interface MetricNoteBadgeProps {
	note: MetricNote | FieldNote;
	iconColor?: string;
	iconSize?: number;
	style?: CSSProperties;
}

/**
 * 字体大小常量 —— 通过 clamp() 在不同分辨率下自适应,确保 2K 屏可读。
 * 正常显示器(1080p/1440p)走 min 值,>= 18px;2K 屏走 max 值,更大。
 */
const FZ = {
	title: "clamp(20px, 1.2vw, 24px)",         // Popover 顶部标题
	titleMeta: "clamp(15px, 0.85vw, 18px)",   // 标题后的单位/小注
	body: "clamp(18px, 1.0vw, 20px)",          // 正文内容
	label: "clamp(16px, 0.9vw, 18px)",         // 左侧标签列
	enumLabel: "clamp(16px, 0.9vw, 18px)",     // 枚举值名称
	enumDesc: "clamp(15px, 0.85vw, 17px)",     // 枚举值含义
	note: "clamp(15px, 0.85vw, 17px)",         // 底部业务备注
} as const;

/** Popover 主体白色背景,确保暗色大屏下也清晰可读 */
const POPOVER_BG = "#ffffff";
const POPOVER_BOX_SHADOW = "0 6px 24px rgba(0, 0, 0, 0.18), 0 2px 8px rgba(0, 0, 0, 0.12)";

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
		<div style={{ display: "grid", gridTemplateColumns: "84px 1fr", gap: 10, marginTop: 8 }}>
			<div style={{ color: "rgba(15, 23, 42, 0.55)", fontSize: FZ.label, lineHeight: 1.6 }}>{label}</div>
			<div style={{ color: "rgba(15, 23, 42, 0.9)", fontSize: FZ.body, lineHeight: 1.6 }}>{value}</div>
		</div>
	);
}

function FieldNoteContent({ note }: { note: FieldNote }) {
	return (
		<div style={{ maxWidth: 520, color: "rgba(15, 23, 42, 0.9)", fontSize: FZ.body, lineHeight: 1.6 }}>
			<div style={{ fontSize: FZ.title, fontWeight: 600, color: "#0f172a", marginBottom: 6 }}>
				{note.displayName}
				<span style={{ marginLeft: 8, fontWeight: 400, color: "#64748b", fontSize: FZ.titleMeta }}>· 字段说明</span>
			</div>
			{note.definition ? renderRow("定义", note.definition) : null}
			{note.enumValues && note.enumValues.length > 0 ? (
				<div style={{ marginTop: 10 }}>
					<div style={{ fontSize: FZ.label, fontWeight: 600, color: "rgba(15, 23, 42, 0.55)", marginBottom: 6 }}>
						取值含义({note.enumValues.length} 种)
					</div>
					{note.enumValues.map((e) => (
						<div key={e.code} style={{ display: "grid", gridTemplateColumns: "140px 1fr", gap: 10, marginTop: 4 }}>
							<div style={{ color: "#334155", fontSize: FZ.enumLabel, lineHeight: 1.6, fontWeight: 500 }}>{e.label}</div>
							<div style={{ color: "rgba(15, 23, 42, 0.85)", fontSize: FZ.enumDesc, lineHeight: 1.6 }}>
								{e.description || ""}
							</div>
						</div>
					))}
				</div>
			) : null}
			{note.sourceTable ? renderRow("数据来源", note.sourceTable) : null}
			{note.note ? (
				<div style={{ marginTop: 10, paddingTop: 8, borderTop: "1px dashed rgba(15, 23, 42, 0.12)", fontSize: FZ.note, color: "rgba(15, 23, 42, 0.6)", lineHeight: 1.6 }}>
					{note.note}
				</div>
			) : null}
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

	if (isFieldNote(note)) {
		return (
			<Popover
				content={<FieldNoteContent note={note} />}
				placement="topRight"
				mouseEnterDelay={0.25}
				color={POPOVER_BG}
				overlayStyle={{ maxWidth: 540 }}
				overlayInnerStyle={{ background: POPOVER_BG, color: "#0f172a", boxShadow: POPOVER_BOX_SHADOW, padding: 14, borderRadius: 8 }}
			>
				<span
					role="button"
					aria-label={`查看${note.displayName}字段说明`}
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
		<div style={{ maxWidth: 520, color: "rgba(15, 23, 42, 0.9)", fontSize: FZ.body, lineHeight: 1.6 }}>
			{note.displayName && (
				<div style={{ fontSize: FZ.title, fontWeight: 600, color: "#0f172a", marginBottom: 6 }}>
					{note.displayName}
					{note.unit ? <span style={{ marginLeft: 8, fontWeight: 400, color: "#64748b", fontSize: FZ.titleMeta }}>· {note.unit}</span> : null}
				</div>
			)}
			{renderRow("定义", note.definition)}
			{renderRow("计算公式", <span style={{ whiteSpace: "pre-wrap" }}>{note.formula}</span>)}
			{renderRow("统计范围", note.scope)}
			{renderRow("数据来源", note.sourceTable)}
			{renderRow(
				"评价方向",
				directionText && thresholdsText ? `${directionText} · ${thresholdsText}` : directionText || thresholdsText,
			)}
			{note.seriesNotes && note.seriesNotes.length > 0 ? (
				<div style={{ marginTop: 10, paddingTop: 8, borderTop: "1px dashed rgba(15, 23, 42, 0.12)" }}>
					<div style={{ fontSize: FZ.label, fontWeight: 600, color: "rgba(15, 23, 42, 0.55)", marginBottom: 6 }}>
						各系列口径
					</div>
					{note.seriesNotes.map((s, i) => (
						<div key={`${s.code || s.name}-${i}`} style={{ display: "grid", gridTemplateColumns: "100px 1fr", gap: 10, marginTop: 5 }}>
							<div style={{ color: "#334155", fontSize: FZ.enumLabel, lineHeight: 1.6, overflow: "hidden", textOverflow: "ellipsis", fontWeight: 500 }}>
								{s.name}
							</div>
							<div style={{ color: "rgba(15, 23, 42, 0.85)", fontSize: FZ.enumDesc, lineHeight: 1.6 }}>
								<span style={{ whiteSpace: "pre-wrap" }}>{s.formula || s.displayName || s.code || "—"}</span>
								{s.unit ? <span style={{ marginLeft: 4, color: "#64748b" }}>· {s.unit}</span> : null}
							</div>
						</div>
					))}
				</div>
			) : null}
		</div>
	);

	return (
		<Popover
			content={content}
			placement="topRight"
			mouseEnterDelay={0.25}
			color={POPOVER_BG}
			overlayStyle={{ maxWidth: 540 }}
			overlayInnerStyle={{ background: POPOVER_BG, color: "#0f172a", boxShadow: POPOVER_BOX_SHADOW, padding: 14, borderRadius: 8 }}
		>
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
