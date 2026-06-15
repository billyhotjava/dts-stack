import { Alert, Button, Card, Progress, Space, Tag, Timeline, Typography } from "antd";
import { PageHeader } from "@/components/page-header";
import { useRouter } from "@/routes/hooks";

const { Text } = Typography;

const pathStages = [
	{ label: "数据接入", note: "来源系统入湖，保留来源、批次和责任人", color: "green" },
	{ label: "建模发布", note: "形成可复用的明细、汇总和应用层资产", color: "green" },
	{ label: "治理门禁", note: "口径、负责人、质量状态和授权范围可核验", color: "green" },
	{ label: "报表指标", note: "报表数据集与指标入口直接面向业务用户", color: "blue" },
	{ label: "数据服务", note: "数据 API 与数据产品按场景交付", color: "blue" },
	{ label: "运维验收", note: "任务状态、告警、补数和客户验收包闭环", color: "gray" },
];

const consumptionEntries = [
	{
		title: "报表数据集",
		desc: "发布到报表工厂，业务人员按主题选择字段、筛选条件和展示方式。",
		status: "已接入",
		route: "/bi/report-factory",
		action: "进入报表工厂",
	},
	{
		title: "指标入口",
		desc: "统一沉淀经营指标、维度口径和最近更新状态，减少重复解释。",
		status: "已接入",
		route: "/bi-apps/metrics/center",
		action: "查看指标中心",
	},
	{
		title: "数据 API",
		desc: "把治理后的资产发布成接口目录，支持调用审计、授权和限流。",
		status: "已接入",
		route: "/services/apis",
		action: "管理接口目录",
	},
	{
		title: "数据产品",
		desc: "面向固定消费场景管理版本、说明、关联数据集和服务等级。",
		status: "已接入",
		route: "/services/products",
		action: "查看数据产品",
	},
];

const permissionItems = [
	{ target: "资产门户", scope: "目录可见、字段可见、申请入口" },
	{ target: "指标入口", scope: "指标可见、维度可见、口径可见" },
	{ target: "BI 报表", scope: "数据集授权、行列裁剪、导出审计" },
	{ target: "数据大屏", scope: "大屏引用资产与访问审计" },
	{ target: "数据 API", scope: "接口授权、调用令牌、配额控制" },
	{ target: "数据产品", scope: "产品订阅、版本发布、服务边界" },
];

const acceptanceItems = [
	{ name: "客户验收包", value: "业务场景、数据链路、访问路径和责任人汇总" },
	{ name: "演示数据", value: "按传统行业常见主题准备样例记录与统计口径" },
	{ name: "截图清单", value: "覆盖接入、治理、报表、接口、运维五类界面" },
	{ name: "调用统计", value: "展示接口调用、报表访问、指标查看的近况" },
	{ name: "失败恢复", value: "补数入口、告警记录和任务实例可追踪" },
];

export default function Page() {
	const router = useRouter();

	return (
		<div className="space-y-6">
			<PageHeader
				title="业务消费工作台"
				actions={
					<Space wrap>
						<Button onClick={() => router.push("/catalog/assets")}>查看资产</Button>
						<Button onClick={() => router.push("/ops/overview")}>查看运维</Button>
						<Button type="primary" onClick={() => router.push("/bi/report-factory")}>
							生成报表
						</Button>
					</Space>
				}
			/>

			<Alert
				type="info"
				showIcon
				message="从数据入湖到业务消费的一站式视图"
				description="面向不写查询语句的业务用户，把治理后的资产发布到报表、指标、数据 API、数据产品和验收交付路径。"
			/>

			<div className="grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(360px,0.8fr)]">
				<Card title="交付链路状态">
					<div className="mb-4 grid gap-4 md:grid-cols-3">
						<div>
							<Text type="secondary">链路完成度</Text>
							<div className="mt-2">
								<Progress percent={83} status="active" />
							</div>
						</div>
						<div>
							<Text type="secondary">已开放入口</Text>
							<div className="mt-2 text-2xl font-semibold">4</div>
						</div>
						<div>
							<Text type="secondary">权限一致面</Text>
							<div className="mt-2 text-2xl font-semibold">6</div>
						</div>
					</div>
					<Timeline
						items={pathStages.map((item) => ({
							color: item.color,
							children: (
								<div>
									<div className="font-medium">{item.label}</div>
									<Text type="secondary">{item.note}</Text>
								</div>
							),
						}))}
					/>
				</Card>

				<Card title="客户验收包">
					<div className="space-y-3">
						{acceptanceItems.map((item) => (
							<div key={item.name} className="rounded border border-dashed border-gray-200 p-3">
								<div className="flex items-center justify-between gap-3">
									<span className="font-medium">{item.name}</span>
									<Tag color="blue">可展示</Tag>
								</div>
								<Text type="secondary">{item.value}</Text>
							</div>
						))}
					</div>
				</Card>
			</div>

			<div className="grid gap-4 lg:grid-cols-4 md:grid-cols-2">
				{consumptionEntries.map((item) => (
					<Card key={item.title} title={item.title} extra={<Tag color="green">{item.status}</Tag>}>
						<div className="flex min-h-[172px] flex-col justify-between gap-4">
							<Text type="secondary">{item.desc}</Text>
							<Button block onClick={() => router.push(item.route)}>
								{item.action}
							</Button>
						</div>
					</Card>
				))}
			</div>

			<Card title="权限一致">
				<div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
					{permissionItems.map((item) => (
						<div key={item.target} className="rounded border border-gray-200 p-3">
							<div className="flex items-center justify-between gap-3">
								<span className="font-medium">{item.target}</span>
								<Tag color="green">一致</Tag>
							</div>
							<Text type="secondary">{item.scope}</Text>
						</div>
					))}
				</div>
			</Card>
		</div>
	);
}
