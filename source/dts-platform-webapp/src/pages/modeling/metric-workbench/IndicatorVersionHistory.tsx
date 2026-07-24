import { Button, Empty } from "antd";
import { RotateCcw } from "lucide-react";

export type IndicatorVersion = {
	version?: string;
	status?: string;
	changeSummary?: string;
	releasedAt?: string;
	createdDate?: string;
};

type IndicatorVersionHistoryProps = {
	currentVersion?: string | null;
	versions: IndicatorVersion[];
	canManage: boolean;
	saving: boolean;
	onRollback: (version: string) => void;
};

const versionLabel = (item: IndicatorVersion) =>
	[item.version || "未标记版本", item.status || "UNKNOWN"].filter(Boolean).join(" · ");

export function IndicatorVersionHistory({
	currentVersion,
	versions,
	canManage,
	saving,
	onRollback,
}: IndicatorVersionHistoryProps) {
	return (
		<div className="mt-5 border-t border-gray-100 pt-4">
			<div className="mb-3 text-sm font-semibold text-gray-900">版本历史</div>
			{versions.length ? (
				<div className="grid gap-2 md:grid-cols-2">
					{versions.map((item, index) => (
						<div key={`${item.version}-${index}`} className="rounded-md border border-gray-200 p-3">
							<div className="flex items-center justify-between gap-2">
								<span className="font-medium">{versionLabel(item)}</span>
								{item.version && item.version !== currentVersion ? (
									<Button
										size="small"
										icon={<RotateCcw size={13} />}
										disabled={!canManage || saving}
										onClick={() => item.version && onRollback(item.version)}
									>
										回滚并发布
									</Button>
								) : null}
							</div>
							<div className="mt-1 text-xs text-gray-500">
								{item.changeSummary || item.releasedAt || item.createdDate || "已保存版本快照"}
							</div>
						</div>
					))}
				</div>
			) : (
				<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无历史版本" />
			)}
		</div>
	);
}
