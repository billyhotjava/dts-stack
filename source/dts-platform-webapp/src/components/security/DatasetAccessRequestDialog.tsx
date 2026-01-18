import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Label } from "@/ui/label";
import { Input } from "@/ui/input";
import { Textarea } from "@/ui/textarea";
import { Checkbox } from "@/ui/checkbox";
import { Popover, PopoverContent, PopoverTrigger } from "@/ui/popover";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/ui/command";
import { createDatasetAccessRequest, getDatasetAccessWorkflowPreview } from "@/api/platformApi";
import userDirectoryService, { type UserDirectoryEntry } from "@/api/services/userDirectoryService";
import { useUserInfo } from "@/store/userStore";
import { cn } from "@/utils";
import { Check, ChevronsUpDown } from "lucide-react";

export type DatasetAccessDialogAction = "query" | "preview";

export type DatasetAccessDialogDataset = {
	id: string;
	name?: string;
	classification?: string;
	warehouseLayer?: string;
	ownerDept?: string;
};

type WorkflowPreview = {
	source?: string;
	templateId?: string;
	templateName?: string;
	ownerScope?: string;
	classificationMin?: string;
	classificationMax?: string;
	steps?: Array<{
		stepOrder?: number;
		approverRole?: string;
		deptBinding?: boolean;
		deptCode?: string | null;
	}>;
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
	const userInfo = useUserInfo() as any;
	const normalizedRoleSet = useMemo(() => {
		const raw = (userInfo as any)?.roles;
		const list = Array.isArray(raw) ? raw : [];
		return new Set(list.map((r: any) => String(r ?? "").toUpperCase()).filter(Boolean));
	}, [userInfo]);
	const canProxyApply = useMemo(() => {
		const allowed = [
			"ROLE_OP_ADMIN",
			"OPADMIN",
			"ROLE_ADMIN",
			"ADMIN",
			"ROLE_INST_DATA_OWNER",
			"INST_DATA_OWNER",
			"ROLE_DEPT_DATA_OWNER",
			"DEPT_DATA_OWNER",
		];
		return allowed.some((r) => normalizedRoleSet.has(r));
	}, [normalizedRoleSet]);

	const defaultSet = useMemo(() => new Set(defaultActions.map(normalizeAction)), [defaultActions]);
	const [canQuery, setCanQuery] = useState(defaultSet.has("query"));
	const [canPreview, setCanPreview] = useState(defaultSet.has("preview"));
	const [validDays, setValidDays] = useState<string>("7");
	const [reason, setReason] = useState<string>("");
	const [submitting, setSubmitting] = useState(false);
	const [previewLoading, setPreviewLoading] = useState(false);
	const [workflowPreview, setWorkflowPreview] = useState<WorkflowPreview | null>(null);
	const [userPickerOpen, setUserPickerOpen] = useState(false);
	const [userSearch, setUserSearch] = useState("");
	const [userLoading, setUserLoading] = useState(false);
	const [userOptions, setUserOptions] = useState<UserDirectoryEntry[]>([]);
	const [targetUser, setTargetUser] = useState<UserDirectoryEntry | null>(null);

	const title = dataset?.name ? `申请数据访问：${dataset.name}` : "申请数据访问";
	const metaLine = useMemo(() => {
		const parts: string[] = [];
		if (dataset?.warehouseLayer) parts.push(`分层 ${dataset.warehouseLayer}`);
		if (dataset?.classification) parts.push(`密级 ${dataset.classification}`);
		if (dataset?.ownerDept) parts.push(`归属 ${dataset.ownerDept}`);
		return parts.join(" · ");
	}, [dataset]);

	const workflowSourceLabel = useMemo(() => {
		if (!workflowPreview) return "";
		if (workflowPreview.source === "ADMIN_CONFIG") {
			return `审批模板：${workflowPreview.templateName || "管理端配置"}`;
		}
		return "审批模板：默认安全策略";
	}, [workflowPreview]);

	const resolveRoleLabel = (role?: string) => {
		const normalized = String(role || "").toUpperCase();
		if (normalized === "ROLE_INST_LEADER" || normalized === "INST_LEADER") return "所级审批";
		if (normalized === "ROLE_DEPT_LEADER" || normalized === "DEPT_LEADER") return "部门审批";
		return role || "-";
	};

	const resetForm = useCallback(() => {
		setCanQuery(defaultSet.has("query"));
		setCanPreview(defaultSet.has("preview"));
		setValidDays("7");
		setReason("");
		setTargetUser(null);
		setUserOptions([]);
		setUserSearch("");
		setUserPickerOpen(false);
		setWorkflowPreview(null);
	}, [defaultSet]);

	const loadUsers = useCallback(async (keyword: string) => {
		const query = keyword.trim();
		setUserLoading(true);
		try {
			const list = await userDirectoryService.searchUsers(query);
			setUserOptions(list);
		} catch (error) {
			console.error(error);
			setUserOptions([]);
		} finally {
			setUserLoading(false);
		}
	}, []);

	useEffect(() => {
		if (!open || !canProxyApply) return;
		setUserSearch("");
		void loadUsers("");
	}, [open, canProxyApply, loadUsers]);

	useEffect(() => {
		if (!open || !dataset?.id) return;
		let cancelled = false;
		const loadPreview = async () => {
			setPreviewLoading(true);
			try {
				const resp: any = await getDatasetAccessWorkflowPreview(dataset.id);
				if (!cancelled) {
					setWorkflowPreview(resp || null);
				}
			} catch (error) {
				console.error(error);
				if (!cancelled) {
					setWorkflowPreview(null);
				}
			} finally {
				if (!cancelled) setPreviewLoading(false);
			}
		};
		void loadPreview();
		return () => {
			cancelled = true;
		};
	}, [open, dataset?.id]);

	useEffect(() => {
		if (!open || !canProxyApply) return;
		const handle = window.setTimeout(() => {
			void loadUsers(userSearch);
		}, 300);
		return () => window.clearTimeout(handle);
	}, [open, canProxyApply, userSearch, loadUsers]);

	const handleUserSelect = useCallback((option: UserDirectoryEntry) => {
		setTargetUser(option);
		setUserSearch("");
		setUserPickerOpen(false);
	}, []);

	const handleSubmit = useCallback(async () => {
		if (!dataset?.id) {
			toast.error("缺少数据集信息");
			return;
		}
		if (!canProxyApply) {
			toast.error("普通员工不支持自助申请，请联系数据管理员代申请");
			return;
		}
		if (!targetUser?.username) {
			toast.error("请选择申请对象");
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
			const preferredName = targetUser.fullName?.trim() || targetUser.displayName?.trim() || targetUser.username;
			await createDatasetAccessRequest({
				datasetId: dataset.id,
				targetUserId: targetUser.id,
				targetUsername: targetUser.username,
				targetName: preferredName,
				targetDept: targetUser.deptCode,
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
	}, [dataset, canProxyApply, targetUser, canQuery, canPreview, validDays, reason, onOpenChange, resetForm, onSubmitted]);

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
					{workflowSourceLabel ? <div className="text-xs text-muted-foreground">{workflowSourceLabel}</div> : null}
					{previewLoading ? (
						<div className="text-xs text-muted-foreground">审批路径加载中…</div>
					) : workflowPreview?.steps?.length ? (
						<div className="rounded-md border border-dashed p-3 text-xs text-muted-foreground">
							<div className="mb-2 text-[13px] font-medium text-foreground">审批路径预览</div>
							<div className="space-y-1">
								{workflowPreview.steps
									.slice()
									.sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0))
									.map((step, idx) => (
										<div key={`${step.approverRole || "step"}-${idx}`} className="flex flex-wrap gap-2">
											<span>第{step.stepOrder ?? idx + 1}步</span>
											<span>{resolveRoleLabel(step.approverRole)}</span>
											{step.deptBinding ? <span>绑定部门：{step.deptCode || "未指定"}</span> : <span>不绑定部门</span>}
										</div>
									))}
							</div>
						</div>
					) : (
						<div className="text-xs text-muted-foreground">审批路径未配置，将采用默认审批链路。</div>
					)}

					{canProxyApply ? (
						<div className="space-y-2">
							<Label>申请对象 *</Label>
							<Popover open={userPickerOpen} onOpenChange={setUserPickerOpen}>
								<PopoverTrigger asChild>
									<Button
										variant="outline"
										role="combobox"
										aria-expanded={userPickerOpen}
										className={cn("justify-between", targetUser?.username ? "" : "text-muted-foreground")}
									>
										{targetUser?.username
											? `${targetUser.fullName || targetUser.displayName || targetUser.username} (${targetUser.username})`
											: "选择用户"}
										<ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
									</Button>
								</PopoverTrigger>
								<PopoverContent className="w-[360px] p-0">
									<Command>
										<CommandInput placeholder="搜索用户名..." value={userSearch} onValueChange={setUserSearch} />
										<CommandList>
											{userLoading ? (
												<div className="px-3 py-4 text-sm text-muted-foreground">加载中…</div>
											) : (
												<>
													<CommandEmpty>未找到匹配用户</CommandEmpty>
													<CommandGroup heading="用户">
														{userOptions.map((option) => (
															<CommandItem
																key={option.id}
																value={option.username}
																onSelect={() => handleUserSelect(option)}
															>
																<div className="flex flex-col overflow-hidden">
																	<span className="truncate font-medium">
																		{option.fullName || option.displayName || option.username}
																	</span>
																	<span className="truncate text-xs text-muted-foreground">
																		{option.username}
																		{option.deptCode ? ` · ${option.deptCode}` : ""}
																	</span>
																</div>
																<Check
																	className={cn(
																		"ml-2 h-4 w-4",
																		targetUser?.username === option.username ? "opacity-100" : "opacity-0",
																	)}
																/>
															</CommandItem>
														))}
													</CommandGroup>
												</>
											)}
										</CommandList>
									</Command>
								</PopoverContent>
							</Popover>
							<div className="text-xs text-muted-foreground">
								普通员工不支持自助申请；由部门/所级数据管理员代申请并走领导审批。
							</div>
						</div>
					) : (
						<div className="rounded-md border px-3 py-2 text-sm text-muted-foreground">
							普通员工不支持自助申请，请联系部门/所级数据管理员代申请。
						</div>
					)}

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
					<Button onClick={handleSubmit} disabled={submitting || !canProxyApply || !targetUser?.username}>
						{submitting ? "提交中..." : "提交申请"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
