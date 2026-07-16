import { Alert, Button, Card, Space, Tag, Typography } from "antd";
import { ArrowRight, CheckCircle2 } from "lucide-react";
import { useState } from "react";
import { useRouter } from "@/routes/hooks";
import { buildBusinessModelingRoute, type BusinessModelingContext } from "../businessModelingContext";
import {
	buildConformedDimensionReference,
	dimensionsForDomain,
	recommendDimensionsForProcess,
	saveConformedDimensionReference,
} from "../../governance/conformedDimensions";

const { Text } = Typography;

export function ConformedDimensionRecommendations({
	domainId,
	processId,
	context,
}: {
	domainId?: string;
	processId?: string;
	context?: Partial<BusinessModelingContext>;
}) {
	const router = useRouter();
	const catalog = domainId ? dimensionsForDomain(domainId) : [];
	const recommended = domainId && processId ? recommendDimensionsForProcess(domainId, processId) : [];
	const [referencedDimensionId, setReferencedDimensionId] = useState<string>();

	const referenceDimension = (dimension: (typeof recommended)[number]) => {
		if (!domainId || !processId) return;
		saveConformedDimensionReference(buildConformedDimensionReference(domainId, processId, dimension));
		setReferencedDimensionId(dimension.dimensionId);
	};
	return (
		<Card
			size="small"
			title="可复用维度推荐"
			extra={<Tag color={recommended.length ? "green" : "orange"}>{recommended.length ? `${recommended.length} 个已登记` : "待登记"}</Tag>}
			data-testid="conformed-dimension-recommendations"
		>
			{recommended.length ? (
				<Space direction="vertical" className="w-full" size="small">
					<Alert
						showIcon
						type="warning"
						message="已命中登记维度，疑似重复建设"
						description="优先引用已有维度；引用关系会写入当前业务过程的建模草稿 metadata。"
					/>
					<div className="flex flex-wrap gap-2">
					{recommended.map((dimension) => (
						<Space key={dimension.dimensionId} size={4}>
							<Tag color="green" icon={<CheckCircle2 size={12} />}>
								{dimension.name} · {dimension.sourceModel}
							</Tag>
							<Button size="small" type={referencedDimensionId === dimension.dimensionId ? "primary" : "link"} onClick={() => referenceDimension(dimension)}>
								{referencedDimensionId === dimension.dimensionId ? "已引用" : "引用"}
							</Button>
						</Space>
					))}
					</div>
				</Space>
			) : (
				<Alert
					showIcon
					type="warning"
					message="当前业务过程还没有登记可复用维度"
					description={`标准目录已有 ${catalog.length} 个公共维度；先在总线矩阵勾选，再回到建模页复用，避免重复创建。`}
					action={
						<Space>
							<Text type="secondary">业务过程：{processId || "未绑定"}</Text>
							<Button
								size="small"
								onClick={() => router.push(buildBusinessModelingRoute("/governance/subjects?focus=business-processes", context || {}))}
							>
								去登记 <ArrowRight size={13} />
							</Button>
						</Space>
					}
				/>
			)}
		</Card>
	);
}
