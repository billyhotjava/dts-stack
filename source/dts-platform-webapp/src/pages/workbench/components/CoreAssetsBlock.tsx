import { Card, Empty, List, Skeleton, Tag } from "antd";
import { ClassificationTag } from "@/analytics/pages/screens/components/ClassificationTag";
import type { LeaderOverviewResponse } from "@/api/services/workbenchService";
import { resolveAppHref } from "@/routes/constants";
import { humanizeBizDomain } from "../hooks/bizDomain";
import { relativeTime } from "../hooks/relativeTime";
import type { WorkbenchRole } from "../hooks/useWorkbenchRole";

/**
 * Sprint-15 F5/T02 — Right 1-column core-assets block.
 *
 * Backend returns items pre-sorted by classification desc (S1 > S2 > S3 > S4)
 * then by updatedAt desc; the UI does not re-sort. Up to 10 items expected.
 */

export type CoreAssetItem = LeaderOverviewResponse["topAssets"][number];

export interface CoreAssetsBlockProps {
	role: WorkbenchRole;
	items: CoreAssetItem[];
	loading: boolean;
	domainLabels?: Readonly<Record<string, string>>;
}

function titleFor(role: CoreAssetsBlockProps["role"]): string {
	if (role === "EMP") return "我常用的资产";
	if (role === "DEPT_LEADER") return "本部门资产 · 按密级";
	return "核心资产 · 按密级";
}

function emptyTextFor(role: CoreAssetsBlockProps["role"]): string {
	return role === "EMP" ? "还没有常用资产" : "暂无核心资产";
}

export function CoreAssetsBlock({ role, items, loading, domainLabels }: CoreAssetsBlockProps) {
	const title = titleFor(role);

	const handleRowClick = (item: CoreAssetItem) => {
		window.open(
			resolveAppHref(`/catalog/datasets/${encodeURIComponent(String(item.id))}`),
			"_blank",
			"noopener,noreferrer",
		);
	};

	return (
		<Card title={title} extra={<a href={resolveAppHref("/catalog/assets")}>查看全部 →</a>}>
			{loading ? (
				<Skeleton active paragraph={{ rows: 6 }} />
			) : items.length === 0 ? (
				<Empty description={emptyTextFor(role)} />
			) : (
				<List
					dataSource={items}
					renderItem={(a) => (
						<List.Item style={{ cursor: "pointer" }} onClick={() => handleRowClick(a)}>
							<List.Item.Meta
								title={a.name}
								description={(() => {
									const label = humanizeBizDomain(a.bizDomain, domainLabels);
									return (
										<span>
											{a.updatedAt ? relativeTime(a.updatedAt) : "—"}
											{label ? (
												<>
													{" "}
													·{" "}
													<Tag color="geekblue" style={{ marginLeft: 4 }}>
														{label}
													</Tag>
												</>
											) : null}
										</span>
									);
								})()}
							/>
							<ClassificationTag value={a.classification} size="small" />
						</List.Item>
					)}
				/>
			)}
		</Card>
	);
}

export default CoreAssetsBlock;
