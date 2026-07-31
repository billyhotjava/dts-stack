import { ListChecks } from "lucide-react";
import type { ReactNode } from "react";
import { useRef, useState } from "react";
import { Dialog as AccessibleDialog, DialogContent, DialogDescription, DialogTitle } from "@/ui/dialog";
import { ActionButton, BackendPendingButton } from "./WorkspacePage";

export const MODEL_FIELD_DISPLAY_COLUMNS = [
	["sequence", "序号"],
	["code", "字段名称"],
	["dataType", "类型"],
	["displayName", "字段显示名"],
	["primaryKey", "主键"],
	["notNull", "非空"],
	["attributeCode", "维度属性编码"],
	["operation", "操作"],
] as const;

export type ModelingDialogKind = "display" | "association" | "release" | null;

type FieldSummary = {
	code: string;
	dataType: string;
};

type DialogProps = {
	title: string;
	subtitle?: string;
	children: ReactNode;
	footer?: ReactNode;
	onClose: () => void;
	wide?: boolean;
};

function Dialog({ title, subtitle, children, footer, onClose, wide = false }: DialogProps) {
	const returnFocusRef = useRef<HTMLElement | null>(
		typeof document !== "undefined" && document.activeElement instanceof HTMLElement ? document.activeElement : null,
	);

	return (
		<AccessibleDialog
			open
			onOpenChange={(open) => {
				if (!open) onClose();
			}}
		>
			<DialogContent
				className={`dm-dialog ${wide ? "dm-dialog--wide" : ""}`}
				onCloseAutoFocus={(event) => {
					event.preventDefault();
					returnFocusRef.current?.focus();
				}}
			>
				<header className="dm-dialog__header">
					<div>
						<DialogTitle>{title}</DialogTitle>
						{subtitle ? <DialogDescription>{subtitle}</DialogDescription> : null}
					</div>
				</header>
				<div className="dm-dialog__body">{children}</div>
				{footer ? <footer className="dm-dialog__footer">{footer}</footer> : null}
			</DialogContent>
		</AccessibleDialog>
	);
}

export function ModelingDialogs({
	dialog,
	onClose,
	visibleColumns,
	onToggleColumn,
	selectionCode,
	rows,
}: {
	dialog: ModelingDialogKind;
	onClose: () => void;
	visibleColumns: Set<string>;
	onToggleColumn: (key: string, checked: boolean) => void;
	selectionCode: string;
	rows: FieldSummary[];
}) {
	const [releaseTab, setReleaseTab] = useState<"publish" | "materialize">("publish");

	if (dialog === "display") {
		return (
			<Dialog
				footer={<ActionButton onClick={onClose}>完成</ActionButton>}
				onClose={onClose}
				subtitle="控制字段表格中展示的列，不改变模型定义。"
				title="字段显示设置"
			>
				<div className="dm-check-grid">
					{MODEL_FIELD_DISPLAY_COLUMNS.map(([key, label]) => (
						<label key={key}>
							<input
								checked={visibleColumns.has(key)}
								onChange={(event) => onToggleColumn(key, event.target.checked)}
								type="checkbox"
							/>
							{label}
						</label>
					))}
				</div>
			</Dialog>
		);
	}

	if (dialog === "association") {
		return (
			<Dialog
				footer={
					<>
						<ActionButton onClick={onClose}>取消</ActionButton>
						<BackendPendingButton>保存关联</BackendPendingButton>
					</>
				}
				onClose={onClose}
				subtitle="检查当前模型与上游字段、维度标准之间的映射。"
				title="设置字段关联"
				wide
			>
				<div className="dm-association-legend">
					<span>
						<i className="is-source" /> 上游来源
					</span>
					<span>
						<i className="is-model" /> 当前模型
					</span>
					<span>
						<i className="is-standard" /> 维度属性
					</span>
				</div>
				<div className="dm-association-canvas">
					<article>
						<small>上游表</small>
						<strong>ods_budget_execution</strong>
						<p>budget_no · STRING</p>
						<p>budget_amount_adjusted · DECIMAL</p>
					</article>
					<div className="dm-association-line">
						<span>字段映射</span>
					</div>
					<article>
						<small>当前模型</small>
						<strong>{selectionCode}</strong>
						<p>
							{rows[0]?.code || "待配置字段"} · {rows[0]?.dataType || "STRING"}
						</p>
						<p>
							{rows[1]?.code || "待配置字段"} · {rows[1]?.dataType || "STRING"}
						</p>
					</article>
				</div>
			</Dialog>
		);
	}

	if (dialog !== "release") return null;

	return (
		<Dialog
			footer={
				<>
					<ActionButton onClick={onClose}>关闭</ActionButton>
					<BackendPendingButton>{releaseTab === "publish" ? "确认发布" : "创建物化任务"}</BackendPendingButton>
				</>
			}
			onClose={onClose}
			subtitle="此处只展示评审后的交付配置，当前不会写入后台。"
			title="发布与物化"
			wide
		>
			<div className="dm-dialog-tabs">
				<button
					className={releaseTab === "publish" ? "is-active" : ""}
					onClick={() => setReleaseTab("publish")}
					type="button"
				>
					发布模型
				</button>
				<button
					className={releaseTab === "materialize" ? "is-active" : ""}
					onClick={() => setReleaseTab("materialize")}
					type="button"
				>
					生成物化任务
				</button>
			</div>
			{releaseTab === "publish" ? (
				<div className="dm-release-layout">
					<div className="dm-release-checklist">
						<h3>发布前检查</h3>
						<p>
							<ListChecks size={15} /> 基本信息和模型粒度已配置
						</p>
						<p>
							<ListChecks size={15} /> 字段定义完整，主键和非空约束可见
						</p>
						<p className="is-pending">
							<ListChecks size={15} /> 后台规则校验待接入
						</p>
					</div>
					<div className="dm-release-form">
						<label>
							发布版本
							<input className="dm-input" readOnly value="v1" />
						</label>
						<label>
							发布说明
							<textarea className="dm-textarea" defaultValue="首次发布模型逻辑定义。" rows={3} />
						</label>
					</div>
				</div>
			) : (
				<div className="dm-release-form dm-release-form--materialize">
					<label>
						目标数据源
						<select className="dm-select" defaultValue="dts_demo">
							<option>dts_demo</option>
						</select>
					</label>
					<label>
						目标 Schema
						<input className="dm-input" defaultValue="dwd" />
					</label>
					<label>
						任务名称
						<input className="dm-input" defaultValue={`materialize_${selectionCode}`} />
					</label>
					<label>
						执行策略
						<select className="dm-select" defaultValue="手工触发">
							<option>手工触发</option>
							<option>周期调度</option>
						</select>
					</label>
					<div className="dm-pending-callout">
						后台重构完成后，这里将创建真实物化任务并关联调度、运行日志与发布记录。
					</div>
				</div>
			)}
		</Dialog>
	);
}
