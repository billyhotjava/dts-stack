import { SafetyCertificateOutlined, WarningOutlined } from "@ant-design/icons";
import { Alert, Button, Space } from "antd";
import { EmptyState } from "@/components/empty-state";
import type { ReconciliationResult } from "./assetPageShared";
import { MetricTile } from "./assetPageShared";

type AssetReconciliationPanelProps = {
	onNavigate: (path: string) => void;
	reconciliation: ReconciliationResult | null;
};

export function AssetReconciliationPanel({ onNavigate, reconciliation }: AssetReconciliationPanelProps) {
	if (!reconciliation) {
		return <EmptyState title="暂无核对结果" description="当前账号无权限或尚未执行核对。" />;
	}

	const failedAssertions = Array.isArray(reconciliation.assertions)
		? reconciliation.assertions.filter((item) => item.passed === false)
		: [];

	return (
		<Space direction="vertical" size={12} className="w-full">
			<div className="grid gap-3 md:grid-cols-4">
				<MetricTile
					icon={<SafetyCertificateOutlined />}
					label="断言总数"
					value={Number(reconciliation.assertionCount || 0)}
				/>
				<MetricTile
					icon={<WarningOutlined />}
					label="失败项"
					value={Number(reconciliation.failedCount || 0)}
					tone="text-red-600"
				/>
				<MetricTile
					icon={<WarningOutlined />}
					label="错误级"
					value={Number(reconciliation.errorCount || 0)}
					tone="text-red-600"
				/>
				<MetricTile
					icon={<WarningOutlined />}
					label="告警级"
					value={Number(reconciliation.warningCount || 0)}
					tone="text-amber-600"
				/>
			</div>
			{failedAssertions.length ? (
				<div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
					{failedAssertions.slice(0, 6).map((item) => (
						<div key={item.code || item.name}>
							[{item.code || "-"}] {item.name || "未命名检查"}：{item.detail || "-"}；建议：
							{item.suggestion || "-"}
						</div>
					))}
				</div>
			) : (
				<Alert type="success" showIcon message="一致性断言通过，未发现阻断项。" />
			)}
			<div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
				<div className="mb-2 text-sm font-medium text-slate-700">核心页面回归清单</div>
				<Space direction="vertical" size={6} className="w-full">
					{Array.isArray(reconciliation.regressionChecklist) && reconciliation.regressionChecklist.length > 0 ? (
						reconciliation.regressionChecklist.map((item) => (
							<div
								key={item.code || item.name}
								className="flex items-center justify-between gap-3 text-xs text-slate-700"
							>
								<div className="min-w-0">
									<span className="font-medium">
										[{item.code || "-"}] {item.name || "-"}
									</span>
									<div className="truncate text-slate-500">{item.description || "-"}</div>
								</div>
								<Button
									size="small"
									onClick={() => {
										if (item.route) onNavigate(item.route);
									}}
								>
									打开页面
								</Button>
							</div>
						))
					) : (
						<div className="text-xs text-slate-500">暂无回归清单</div>
					)}
				</Space>
			</div>
		</Space>
	);
}
