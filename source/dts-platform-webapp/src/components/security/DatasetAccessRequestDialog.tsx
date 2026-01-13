import { useCallback, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Label } from "@/ui/label";
import { Input } from "@/ui/input";
import { Textarea } from "@/ui/textarea";
import { Checkbox } from "@/ui/checkbox";
import { createDatasetAccessRequest } from "@/api/platformApi";

export type DatasetAccessDialogAction = "query" | "preview";

export type DatasetAccessDialogDataset = {
	id: string;
	name?: string;
	classification?: string;
	warehouseLayer?: string;
	ownerDept?: string;
};

type Props = {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	dataset: DatasetAccessDialogDataset;
	defaultActions: DatasetAccessDialogAction[];
	onSubmitted?: () => void | Promise<void>;
};

const normalizeAction = (action: DatasetAccessDialogAction): DatasetAccessDialogAction => action;

export function DatasetAccessRequestDialog({ open, onOpenChange, dataset, defaultActions, onSubmitted }: Props) {
	const defaultSet = useMemo(() => new Set(defaultActions.map(normalizeAction)), [defaultActions]);
	const [canQuery, setCanQuery] = useState(defaultSet.has("query"));
	const [canPreview, setCanPreview] = useState(defaultSet.has("preview"));
	const [validDays, setValidDays] = useState<string>("7");
	const [reason, setReason] = useState<string>("");
	const [submitting, setSubmitting] = useState(false);

	const title = dataset?.name ? `申请数据访问：${dataset.name}` : "申请数据访问";
	const metaLine = useMemo(() => {
		const parts: string[] = [];
		if (dataset?.warehouseLayer) parts.push(`分层 ${dataset.warehouseLayer}`);
		if (dataset?.classification) parts.push(`密级 ${dataset.classification}`);
		if (dataset?.ownerDept) parts.push(`归属 ${dataset.ownerDept}`);
		return parts.join(" · ");
	}, [dataset]);

	const resetForm = useCallback(() => {
		setCanQuery(defaultSet.has("query"));
		setCanPreview(defaultSet.has("preview"));
		setValidDays("7");
		setReason("");
	}, [defaultSet]);

	const handleSubmit = useCallback(async () => {
		if (!dataset?.id) {
			toast.error("缺少数据集信息");
			return;
		}
		if (!canQuery && !canPreview) {
			toast.error("请选择需要申请的权限（查询/预览）");
			return;
		}
		const days = Math.max(1, Math.min(180, Number(validDays || "0")));
		if (!Number.isFinite(days) || days <= 0) {
			toast.error("有效期天数不合法");
			return;
		}
		const trimmedReason = reason.trim();
		if (!trimmedReason) {
			toast.error("请填写申请理由");
			return;
		}

		setSubmitting(true);
		try {
			const now = new Date();
			const validFrom = now.toISOString();
			const validTo = new Date(now.getTime() + days * 24 * 60 * 60 * 1000).toISOString();
			await createDatasetAccessRequest({
				datasetId: dataset.id,
				canQuery,
				canPreview,
				validFrom,
				validTo,
				reason: trimmedReason,
			});
			toast.success("已提交审批申请");
			onOpenChange(false);
			resetForm();
			if (onSubmitted) {
				await onSubmitted();
			}
		} catch (error) {
			console.error(error);
			toast.error("提交申请失败");
		} finally {
			setSubmitting(false);
		}
	}, [dataset, canQuery, canPreview, validDays, reason, onOpenChange, resetForm, onSubmitted]);

	return (
		<Dialog
			open={open}
			onOpenChange={(next) => {
				onOpenChange(next);
				if (!next) {
					resetForm();
				}
			}}
		>
			<DialogContent className="max-w-lg">
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
				</DialogHeader>
				<div className="space-y-4">
					{metaLine ? <div className="text-xs text-muted-foreground">{metaLine}</div> : null}

					<div className="space-y-2">
						<Label>申请权限</Label>
						<div className="flex flex-wrap items-center gap-4 text-sm">
							<label className="flex items-center gap-2">
								<Checkbox
									checked={canQuery}
									onCheckedChange={(v) => setCanQuery(Boolean(v))}
								/>
								<span>查询</span>
							</label>
							<label className="flex items-center gap-2">
								<Checkbox
									checked={canPreview}
									onCheckedChange={(v) => setCanPreview(Boolean(v))}
								/>
								<span>预览</span>
							</label>
						</div>
						<div className="text-xs text-muted-foreground">说明：数据内容访问必须审批；元数据浏览不受影响。</div>
					</div>

					<div className="grid gap-2">
						<Label>有效期（天）</Label>
						<Input
							type="number"
							min={1}
							max={180}
							value={validDays}
							onChange={(e) => setValidDays(e.target.value)}
							placeholder="例如 7"
						/>
					</div>

					<div className="grid gap-2">
						<Label>申请理由</Label>
						<Textarea
							value={reason}
							onChange={(e) => setReason(e.target.value)}
							placeholder="请输入用途/项目/课题等信息"
						/>
					</div>
				</div>
				<DialogFooter>
					<Button variant="ghost" onClick={() => onOpenChange(false)} disabled={submitting}>
						取消
					</Button>
					<Button onClick={handleSubmit} disabled={submitting}>
						{submitting ? "提交中..." : "提交申请"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
