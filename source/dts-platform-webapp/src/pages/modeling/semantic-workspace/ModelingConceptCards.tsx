import { Button, Card, Space, Tag, Typography } from "antd";
import { ArrowRight, Boxes, MapPinned, Workflow } from "lucide-react";
import { useEffect, useState } from "react";
import { useRouter } from "@/routes/hooks";
import type { BusinessModelingContext } from "../businessModelingContext";
import { buildBusinessModelingRoute } from "../businessModelingContext";

const { Text } = Typography;
const CONCEPT_CARDS_DISMISSED_KEY = "dts:modeling:concept-cards:dismissed";

const CONCEPTS = [
	{
		key: "subject-domain",
		title: "主题域",
		statement: "管什么业务：组织业务版图和治理边界。",
		route: "/governance/subjects",
		icon: MapPinned,
	},
	{
		key: "business-process",
		title: "业务过程",
		statement: "发生什么事：决定事实建模和总线矩阵。",
		route: "/governance/subjects?focus=business-processes",
		icon: Workflow,
	},
	{
		key: "dimension-catalog",
		title: "维度目录",
		statement: "统一查看维度定义、层级、慢变策略和复用范围。",
		route: "/modeling/dimensions",
		icon: Boxes,
	},
] as const;

export function ModelingConceptCards({ context }: { context?: Partial<BusinessModelingContext> }) {
	const router = useRouter();
	const [dismissed, setDismissed] = useState(false);

	useEffect(() => {
		setDismissed(window.sessionStorage.getItem(CONCEPT_CARDS_DISMISSED_KEY) === "1");
	}, []);

	if (dismissed) return null;

	const dismiss = () => {
		window.sessionStorage.setItem(CONCEPT_CARDS_DISMISSED_KEY, "1");
		setDismissed(true);
	};

	return (
		<Card
			size="small"
			title="建模概念链"
			extra={
				<Space size={8}>
					<Tag color="blue">主题域 → 业务过程 → 业务对象</Tag>
					<Button type="link" size="small" onClick={dismiss}>
						本次隐藏
					</Button>
				</Space>
			}
			data-testid="modeling-concept-cards"
		>
			<div className="grid gap-2 md:grid-cols-3">
				{CONCEPTS.map((concept) => {
					const Icon = concept.icon;
					return (
						<div key={concept.key} className="rounded-md border border-slate-200 bg-slate-50 p-3" data-testid={`modeling-concept-${concept.key}`}>
							<div className="flex items-center gap-2">
								<Icon size={16} className="text-blue-600" />
								<span className="font-medium text-slate-900">{concept.title}</span>
							</div>
							<Text type="secondary" className="mt-1 block text-xs">
								{concept.statement}
							</Text>
							<Space className="mt-2">
								<Button size="small" type="link" onClick={() => router.push(buildBusinessModelingRoute(concept.route, context || {}))}>
									进入台账 <ArrowRight size={13} />
								</Button>
							</Space>
						</div>
					);
				})}
			</div>
		</Card>
	);
}
