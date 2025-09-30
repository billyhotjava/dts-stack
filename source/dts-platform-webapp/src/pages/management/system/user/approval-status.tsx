import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import type { ApprovalRequest } from "#/keycloak";
import { KeycloakApprovalService } from "@/api/services/approvalService";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";

interface ApprovalStatusProps {
	userId: string;
}

export function ApprovalStatus({ userId }: ApprovalStatusProps) {
	const [approvals, setApprovals] = useState<ApprovalRequest[]>([]);
	const [loading, setLoading] = useState(false);

	const loadApprovals = useCallback(async () => {
		setLoading(true);
		try {
			// 这里应该根据用户ID过滤审批请求
			const data = await KeycloakApprovalService.getApprovalRequests();
			// 过滤与当前用户相关的审批请求
			const userApprovals = data.filter(
				(req) => req.reason.includes(userId) || (req as any).items?.some((item: any) => item.targetId === userId),
			);
			setApprovals(userApprovals);
		} catch (error: any) {
			console.error("Error loading approvals:", error);
			toast.error(`加载审批状态失败: ${error.message || "未知错误"}`);
		} finally {
			setLoading(false);
		}
	}, [userId]);

	useEffect(() => {
		loadApprovals();
	}, [loadApprovals]);

	if (loading) {
		return <Badge variant="secondary">加载中...</Badge>;
	}

	if (approvals.length === 0) {
		return <Badge variant="success">无待处理审批</Badge>;
	}

	const pendingApprovals = approvals.filter((approval) => approval.status === "PENDING");
	const failedApprovals = approvals.filter((approval) => approval.status === "FAILED");

	if (failedApprovals.length > 0) {
		return (
			<div className="flex items-center gap-2">
				<Badge variant="destructive">有失败的审批</Badge>
				<Button variant="ghost" size="sm" onClick={loadApprovals}>
					刷新
				</Button>
			</div>
		);
	}

	if (pendingApprovals.length > 0) {
		return (
			<div className="flex items-center gap-2">
				<Badge variant="warning">有待审批请求</Badge>
				<Button variant="ghost" size="sm" onClick={loadApprovals}>
					刷新
				</Button>
			</div>
		);
	}

	return (
		<div className="flex items-center gap-2">
			<Badge variant="secondary">有审批记录</Badge>
			<Button variant="ghost" size="sm" onClick={loadApprovals}>
				刷新
			</Button>
		</div>
	);
}
