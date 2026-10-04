import { useEffect, useState, useCallback } from "react";
import { toast } from "sonner";
import { Button, Card, Empty, Input, Select, Space, Spin, Tag, Typography } from "antd";
import { SearchOutlined } from "@ant-design/icons";
import { listIndicators, listSubscriptions, createSubscription, deleteSubscription } from "@/api/platformApi";
import { aggregationLabel, granularityLabel, indicatorDomainLabel, statusLabel } from "@/utils/customerDisplayLabels";

const { Text, Paragraph } = Typography;

const DOMAIN_OPTIONS = [
	{ label: "全部", value: "" },
	{ label: "财务", value: "FINANCE" },
	{ label: "运营", value: "OPERATION" },
	{ label: "质量", value: "QUALITY" },
	{ label: "合规", value: "COMPLIANCE" },
	{ label: "自定义", value: "CUSTOM" },
];

const STATUS_COLORS: Record<string, string> = {
	DRAFT: "default",
	PUBLISHED: "green",
	ARCHIVED: "red",
};

interface Indicator {
	id: string;
	code: string;
	name: string;
	domain?: string;
	category?: string;
	expressionSql?: string;
	granularity?: string;
	aggregationType?: string;
	measureField?: string;
	status?: string;
	dimensionNames?: string[];
}

interface Subscription {
	id: string;
	indicatorId: string;
	userLogin: string;
	displayOrder: number;
}

export default function IndicatorStorePage() {
	const [indicators, setIndicators] = useState<Indicator[]>([]);
	const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [domain, setDomain] = useState("");

	const subscribedIds = new Set(subscriptions.map((s) => s.indicatorId));

	const fetchData = useCallback(async () => {
		setLoading(true);
		try {
			const [indRes, subRes] = await Promise.all([
				listIndicators({
					page: 0,
					size: 200,
					status: "PUBLISHED",
					keyword: keyword || undefined,
					domain: domain || undefined,
				}),
				listSubscriptions(),
			]);
			const content = (indRes as any)?.data?.content ?? (indRes as any)?.content ?? [];
			setIndicators(Array.isArray(content) ? content : []);
			const subData = (subRes as any)?.data ?? subRes ?? [];
			setSubscriptions(Array.isArray(subData) ? subData : []);
		} catch {
			// global error interceptor handles this
		} finally {
			setLoading(false);
		}
	}, [keyword, domain]);

	useEffect(() => {
		fetchData();
	}, [fetchData]);

	const handleSubscribe = async (indicatorId: string) => {
		try {
			await createSubscription({ indicatorId });
			toast.success("已订阅");
			fetchData();
		} catch {
			// handled by interceptor
		}
	};

	const handleUnsubscribe = async (indicatorId: string) => {
		const sub = subscriptions.find((s) => s.indicatorId === indicatorId);
		if (!sub) return;
		try {
			await deleteSubscription(sub.id);
			toast.success("已取消订阅");
			fetchData();
		} catch {
			// handled by interceptor
		}
	};

	return (
		<div className="p-6">
			<div className="mb-4 flex items-center justify-between">
				<Typography.Title level={4} className="!mb-0">
					指标商店
				</Typography.Title>
			</div>

			<div className="mb-4 flex gap-3 items-center">
				<Input
					placeholder="搜索指标..."
					prefix={<SearchOutlined />}
					value={keyword}
					onChange={(e) => setKeyword(e.target.value)}
					allowClear
					style={{ width: 260 }}
				/>
				<Select value={domain} onChange={setDomain} options={DOMAIN_OPTIONS} style={{ width: 140 }} />
			</div>

			<Spin spinning={loading}>
				{indicators.length === 0 && !loading ? (
					<Empty description="暂无已发布的指标" />
				) : (
					<div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
						{indicators.map((ind) => {
							const subscribed = subscribedIds.has(ind.id);
							return (
								<Card
									key={ind.id}
									size="small"
									title={
										<Space>
											<Text strong>{ind.name}</Text>
											{ind.domain && <Tag color="blue">{indicatorDomainLabel(ind.domain)}</Tag>}
											{ind.status && (
												<Tag color={STATUS_COLORS[ind.status] ?? "default"}>{statusLabel(ind.status)}</Tag>
											)}
										</Space>
									}
									extra={
										<Button
											type="text"
											onClick={() => (subscribed ? handleUnsubscribe(ind.id) : handleSubscribe(ind.id))}
										>
											操作
										</Button>
									}
								>
									{ind.expressionSql && (
										<Paragraph type="secondary" ellipsis={{ rows: 2 }} className="!mb-1 font-mono text-xs">
											{ind.expressionSql}
										</Paragraph>
									)}
									<div className="flex flex-wrap gap-1 mt-1">
										{ind.aggregationType && <Tag>{aggregationLabel(ind.aggregationType)}</Tag>}
										{ind.granularity && <Tag color="purple">{granularityLabel(ind.granularity)}</Tag>}
										{ind.category && <Tag color="cyan">{ind.category}</Tag>}
									</div>
									{ind.dimensionNames && ind.dimensionNames.length > 0 && (
										<div className="mt-1">
											<Text type="secondary" className="text-xs">
												维度:{" "}
											</Text>
											{ind.dimensionNames.map((d) => (
												<Tag key={d} className="text-xs">
													{d}
												</Tag>
											))}
										</div>
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
