import { Title, Text } from "@/ui/typography";

type GaugeBarProps = { value: number; tone?: "blue" | "emerald" | "amber" };
const barTone: Record<NonNullable<GaugeBarProps["tone"]>, string> = {
  blue: "bg-blue-500",
  emerald: "bg-emerald-500",
  amber: "bg-amber-500",
};

function GaugeBar({ value, tone = "blue" }: GaugeBarProps) {
  const width = Math.min(Math.max(value, 0), 100);
  return (
    <div className="w-full bg-slate-100 h-2 rounded-full overflow-hidden">
      <div className={`h-2 ${barTone[tone]}`} style={{ width: `${width}%` }} />
    </div>
  );
}

const kpis = [
  { title: "物料保障率", value: "92%", note: "关键物料库存充足", tone: "emerald" },
  { title: "预算执行率", value: "68%", note: "本月执行在控", tone: "blue" },
  { title: "在管项目", value: "17 个", note: "5 个提前完成", tone: "amber" },
];

const materialList = [
  { name: "高频物料", status: "库存安全", risk: "低", progress: 82 },
  { name: "核心器件", status: "到货周期 2 天", risk: "中", progress: 64 },
  { name: "备件库存", status: "低于安全库存：3 项", risk: "高", progress: 38 },
];

const budgetList = [
  { name: "年度预算", exec: 68, note: "整体执行" },
  { name: "本月预算", exec: 72, note: "当月执行" },
  { name: "偏差警戒", exec: 12, note: "超 10% 的条目" },
];

const projectHighlights = [
  { name: "数据集成平台", stage: "里程碑 M3", owner: "项目组 A", budget: 62, progress: 78 },
  { name: "指标治理体系", stage: "设计验收", owner: "项目组 B", budget: 55, progress: 64 },
  { name: "仓储一体化", stage: "上线准备", owner: "项目组 C", budget: 71, progress: 82 },
];

export default function Workbench() {
  return (
    <div className="space-y-6">
      <section className="bg-white border rounded-lg p-5 shadow-sm">
        <Title as="h2" className="text-3xl font-bold mb-2" align="center">
          经营驾驶舱-虚拟数据演示
        </Title>
        <Title as="h5" className="text-1xl font-bold mb-2">
          物料 / 预算 / 项目 一屏透视
        </Title>
        <Text variant="body3" className="text-slate-600 mb-4">
          参考示例布局，突出三大核心要素；页面样式简洁，兼容 Windows 7 + Chrome 95。
        </Text>

        <div className="grid gap-3 md:grid-cols-3">
          {kpis.map((item) => (
            <div key={item.title} className="bg-slate-50 border rounded-md p-3">
              <div className="flex items-center justify-between">
                <span className="text-xs text-slate-500">{item.title}</span>
                <span className="text-[11px] px-2 py-0.5 rounded-full bg-slate-200 text-slate-700">重点</span>
              </div>
              <div
                className={`text-2xl font-semibold mt-1 ${
                  item.tone === "emerald" ? "text-emerald-700" : item.tone === "amber" ? "text-amber-700" : "text-blue-700"
                }`}
              >
                {item.value}
              </div>
              <div className="text-xs text-slate-600 mt-1">{item.note}</div>
            </div>
          ))}
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-2">
        <div className="bg-white border rounded-lg p-4 shadow-sm">
          <div className="flex items-center justify-between mb-2">
            <Text variant="body2" className="font-semibold">
              物料看板
            </Text>
            <span className="text-[11px] px-2 py-0.5 rounded-full bg-blue-50 text-blue-700">补货 / 消耗</span>
          </div>
          <Text variant="body3" className="text-slate-600 mb-3">
            今日出入库 86 单，低于安全库存 3 项，热门物料交付 2 天。
          </Text>
          <div className="space-y-3">
            {materialList.map((item) => (
              <div key={item.name} className="border rounded-md p-3">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-sm font-medium text-slate-800">{item.name}</div>
                    <div className="text-xs text-slate-600">{item.status}</div>
                  </div>
                  <span
                    className={`text-[11px] px-2 py-0.5 rounded-full ${
                      item.risk === "高"
                        ? "bg-amber-100 text-amber-700"
                        : item.risk === "中"
                          ? "bg-blue-100 text-blue-700"
                          : "bg-emerald-100 text-emerald-700"
                    }`}
                  >
                    风险 {item.risk}
                  </span>
                </div>
                <div className="mt-2 flex items-center gap-2">
                  <GaugeBar value={item.progress} tone={item.risk === "高" ? "amber" : item.risk === "中" ? "blue" : "emerald"} />
                  <span className="text-xs text-slate-700 w-10 text-right">{item.progress}%</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="bg-white border rounded-lg p-4 shadow-sm">
          <div className="flex items-center justify-between mb-2">
            <Text variant="body2" className="font-semibold">
              预算执行
            </Text>
            <span className="text-[11px] px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700">在控</span>
          </div>
          <Text variant="body3" className="text-slate-600 mb-3">
            自动校验超预算条目，支持审批流；预算差异与项目支出联动提示。
          </Text>
          <div className="space-y-3">
            {budgetList.map((item) => (
              <div key={item.name} className="border rounded-md p-3">
                <div className="flex items-center justify-between">
                  <div className="text-sm font-medium text-slate-800">{item.name}</div>
                  <span className="text-xs text-slate-600">{item.note}</span>
                </div>
                <div className="mt-2 flex items-center gap-2">
                  <GaugeBar value={item.exec} tone="blue" />
                  <span className="text-xs text-slate-700 w-10 text-right">{item.exec}%</span>
                </div>
              </div>
            ))}
          </div>
          <div className="mt-4 border-t pt-3">
            <Text variant="body3" className="text-slate-700">
              差异提醒：4 条预算偏差超 10%，已锁定超预算提交。
            </Text>
          </div>
        </div>
      </section>

      <section className="bg-white border rounded-lg p-4 shadow-sm">
        <div className="flex items-center justify-between mb-3">
          <div>
            <Text variant="body2" className="font-semibold">
              项目进度与风险
            </Text>
            <Text variant="body3" className="text-slate-600">
              里程碑、预算执行、物料风险一目了然。
            </Text>
          </div>
          <span className="text-xs px-2 py-1 rounded-full bg-slate-100 text-slate-700">实时概览</span>
        </div>
        <div className="overflow-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="text-left text-slate-500 border-b">
                <th className="py-2 pr-4">项目</th>
                <th className="py-2 pr-4">阶段</th>
                <th className="py-2 pr-4">责任组</th>
                <th className="py-2 pr-4">预算执行</th>
                <th className="py-2 pr-4">整体进度</th>
              </tr>
            </thead>
            <tbody>
              {projectHighlights.map((row) => (
                <tr key={row.name} className="border-b last:border-0">
                  <td className="py-3 pr-4 text-slate-800">{row.name}</td>
                  <td className="py-3 pr-4 text-slate-700">{row.stage}</td>
                  <td className="py-3 pr-4 text-slate-700">{row.owner}</td>
                  <td className="py-3 pr-4">
                    <div className="flex items-center gap-2">
                      <div className="w-32">
                        <GaugeBar value={row.budget} tone="blue" />
                      </div>
                      <span className="text-slate-700">{row.budget}%</span>
                    </div>
                  </td>
                  <td className="py-3 pr-4">
                    <div className="flex items-center gap-2">
                      <div className="w-32">
                        <GaugeBar value={row.progress} tone="emerald" />
                      </div>
                      <span className="text-slate-700">{row.progress}%</span>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
