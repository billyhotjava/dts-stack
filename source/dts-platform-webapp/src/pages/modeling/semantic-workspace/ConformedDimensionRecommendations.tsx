import { Alert, Button, Card, Space, Tag, Typography } from "antd";
import { ArrowRight, CheckCircle2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { listBusMatrixApi, listConformedDimensionsApi } from "@/api/sprint64GovernanceApi";
import { useRouter } from "@/routes/hooks";
import {
	type BusMatrix,
	buildConformedDimensionReference,
	type ConformedDimension,
	recommendDimensionsForProcess,
	saveConformedDimensionReference,
} from "../../governance/conformedDimensions";
import { type BusinessModelingContext, buildBusinessModelingRoute } from "../businessModelingContext";

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
	const [catalog, setCatalog] = useState<ConformedDimension[]>([]);
	const [matrix, setMatrix] = useState<BusMatrix | null>(null);
	const [loading, setLoading] = useState(false);
	const [referencedDimensionId, setReferencedDimensionId] = useState<string>();

	useEffect(() => {
		if (!domainId) {
			setCatalog([]);
			setMatrix(null);
			return;
		}
		let cancelled = false;
		setLoading(true);
		setCatalog([]);
		setMatrix(null);
		void Promise.all([listConformedDimensionsApi(domainId), listBusMatrixApi(domainId)])
			.then(([dimensions, links]) => {
				if (cancelled) return;
				setCatalog(
					(Array.isArray(dimensions) ? dimensions : []).map((dimension) => ({
						version: 1,
						dimensionId: dimension.dimensionId,
						name: dimension.name,
						sourceModel: dimension.sourceModel,
						domainIds: dimension.domainIds,
						sourceType: dimension.sourceType,
						sourceId: dimension.sourceId,
						sourceVersion: dimension.sourceVersion,
						confirmed: dimension.confirmed,
					})),
				);
				const registeredLinks = (Array.isArray(links) ? links : []).reduce<Record<string, string[]>>((acc, link) => {
					if (link.enabled) acc[link.processId] = [...(acc[link.processId] || []), link.dimensionId];
					return acc;
				}, {});
				setMatrix({ version: 1, domainId, links: registeredLinks, updatedAt: new Date().toISOString() });
			})
			.catch(() => {
				if (!cancelled) {
					setCatalog([]);
					setMatrix(null);
				}
			})
			.finally(() => {
				if (!cancelled) setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [domainId]);

	const recommended = useMemo(
		() => (processId && matrix ? recommendDimensionsForProcess(processId, catalog, matrix) : []),
		[catalog, matrix, processId],
	);

	const referenceDimension = (dimension: (typeof recommended)[number]) => {
		if (!domainId || !processId) return;
		saveConformedDimensionReference(buildConformedDimensionReference(domainId, processId, dimension));
		setReferencedDimensionId(dimension.dimensionId);
	};
	return (
		<Card
			size="small"
			title="可复用维度推荐"
			extra={
				<Tag color={recommended.length ? "green" : "orange"}>
					{loading ? "加载中" : recommended.length ? `${recommended.length} 个已登记` : "待登记"}
				</Tag>
			}
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
								<Button
									size="small"
									type={referencedDimensionId === dimension.dimensionId ? "primary" : "link"}
									onClick={() => referenceDimension(dimension)}
								>
									{referencedDimensionId === dimension.dimensionId ? "已引用" : "引用"}
								</Button>
							</Space>
						))}
					</div>
				</Space>
			) : (
				<Alert
					showIcon
					type={catalog.length ? "warning" : "info"}
					message="当前业务过程还没有登记可复用维度"
					description={
						loading
							? "正在读取当前主题域的一致性维度和总线矩阵。"
							: catalog.length
								? `当前主题域已有 ${catalog.length} 个一致性维度；先在总线矩阵勾选，再回到建模页复用。`
								: "当前主题域还没有一致性维度，请先完成登记，或显式安装并确认适用的行业模板。"
					}
					action={
						<Space>
							<Text type="secondary">业务过程：{processId || "未绑定"}</Text>
							<Button
								size="small"
								onClick={() =>
									router.push(
										buildBusinessModelingRoute("/governance/subjects?focus=business-processes", context || {}),
									)
								}
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
