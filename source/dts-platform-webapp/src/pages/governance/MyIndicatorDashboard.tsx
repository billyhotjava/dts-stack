import { useEffect, useState, useCallback } from "react";
import { toast } from "sonner";
import {
	Button,
	Card,
	Empty,
	Space,
	Spin,
	Tag,
	Typography,
} from "antd";
import {
	ArrowUpOutlined,
	ArrowDownOutlined,
	DeleteOutlined,
} from "@ant-design/icons";
import {
	getIndicator,
	listSubscriptions,
	updateSubscription,
	deleteSubscription,
} from "@/api/platformApi";

const { Text, Paragraph } = Typography;

const STATUS_COLORS: Record<string, string> = {
	DRAFT: "default",
	PUBLISHED: "green",
	ARCHIVED: "red",
};

interface Subscription {
	id: string;
	indicatorId: string;
	userLogin: string;
	displayOrder: number;
}

interface IndicatorInfo {
	id: string;
	code: string;
	name: string;
	domain?: string;
	category?: string;
	expressionSql?: string;
	aggregationType?: string;
	granularity?: string;
	status?: string;
	dimensionNames?: string[];
}

interface SubWithIndicator {
	sub: Subscription;
	indicator: IndicatorInfo | null;
}

export default function MyIndicatorDashboard() {
	const [items, setItems] = useState<SubWithIndicator[]>([]);
	const [loading, setLoading] = useState(false);

	const fetchData = useCallback(async () => {
		setLoading(true);
		try {
			const subRes = await listSubscriptions();
			const subs: Subscription[] = Array.isArray((subRes as any)?.data) ? (subRes as any).data : Array.isArray(subRes) ? subRes as any : [];
			const enriched: SubWithIndicator[] = await Promise.all(
				subs.map(async (sub) => {
					try {
						const indRes = await getIndicator(sub.indicatorId);
						const indicator = (indRes as any)?.data ?? indRes ?? null;
						return { sub, indicator };
					} catch {
						return { sub, indicator: null };
					}
				}),
			);
			setItems(enriched);
		} catch {
			// global interceptor
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		fetchData();
	}, [fetchData]);

	const handleMove = async (index: number, direction: "up" | "down") => {
		const swapIdx = direction === "up" ? index - 1 : index + 1;
		if (swapIdx < 0 || swapIdx >= items.length) return;

		const a = items[index];
		const b = items[swapIdx];
		try {
			await Promise.all([
				updateSubscription(a.sub.id, { displayOrder: b.sub.displayOrder }),
				updateSubscription(b.sub.id, { displayOrder: a.sub.displayOrder }),
			]);
			toast.success("排序已更新");
			fetchData();
		} catch {
			// handled by interceptor
		}
	};

	const handleUnsubscribe = async (subId: string) => {
		try {
			await deleteSubscription(subId);
			toast.success("已取消订阅");
			fetchData();
		} catch {
			// handled by interceptor
		}
	};

	return (
		<div className="p-6">
			<div className="mb-4 flex items-center justify-between">
				<Typography.Title level={4} className="!mb-0">我的指标看板</Typography.Title>
				<Text type="secondary">{items.length} 个已订阅指标</Text>
			</div>

			<Spin spinning={loading}>
				{items.length === 0 && !loading ? (
					<Empty description="暂无订阅指标，请前往指标商店订阅" />
				) : (
					<div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
						{items.map((item, index) => {
							const ind = item.indicator;
							return (
								<Card
									key={item.sub.id}
									size="small"
									title={
										<Space>
											<Text strong>{ind?.name ?? "指标已删除"}</Text>
											{ind?.domain && <Tag color="blue">{ind.domain}</Tag>}
											{ind?.status && <Tag color={STATUS_COLORS[ind.status] ?? "default"}>{ind.status}</Tag>}
										</Space>
									}
									extra={
										<Space size={0}>
											<Button
												type="text"
												size="small"
												icon={<ArrowUpOutlined />}
												disabled={index === 0}
												onClick={() => handleMove(index, "up")}
											/>
											<Button
												type="text"
												size="small"
												icon={<ArrowDownOutlined />}
												disabled={index === items.length - 1}
												onClick={() => handleMove(index, "down")}
											/>
											<Button
												type="text"
												size="small"
												danger
												icon={<DeleteOutlined />}
												onClick={() => handleUnsubscribe(item.sub.id)}
											/>
										</Space>
									}
								>
									{ind ? (
										<>
											{ind.expressionSql && (
												<Paragraph
													type="secondary"
													ellipsis={{ rows: 2 }}
													className="!mb-1 font-mono text-xs"
												>
													{ind.expressionSql}
												</Paragraph>
											)}
											<div className="flex flex-wrap gap-1 mt-1">
												{ind.aggregationType && <Tag>{ind.aggregationType}</Tag>}
												{ind.granularity && <Tag color="purple">{ind.granularity}</Tag>}
												{ind.category && <Tag color="cyan">{ind.category}</Tag>}
											</div>
											{ind.dimensionNames && ind.dimensionNames.length > 0 && (
												<div className="mt-1">
													<Text type="secondary" className="text-xs">维度: </Text>
													{ind.dimensionNames.map((d) => (
														<Tag key={d} className="text-xs">{d}</Tag>
													))}
												</div>
											)}
										</>
									) : (
										<Text type="danger">指标信息不可用</Text>
									)}
								</Card>
							);
						})}
					</div>
				)}
			</Spin>
		</div>
	);
}
