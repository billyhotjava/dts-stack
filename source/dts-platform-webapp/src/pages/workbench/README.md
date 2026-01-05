# 数据可视化工作台-首页设计方案（业务报表 + 数据治理）

本文件用于指导 `WorkBench` 首页的信息架构、模块拆分与交互跳转，并提供一份 Ant Design + React 的可运行 Demo（含假数据），便于快速落地。

## 1. 目标与设计原则

### 1.1 目标
首页作为“数据可视化工作台”的总览入口，需要同时满足：
- 业务态势：业务指标是否达成、趋势是否异常
- 数据资产：数据集/表/指标/主题域等资产规模与覆盖
- 数据治理：质量、时效、完整性、可用性与风险告警
- 行动导向：当前最重要的告警/待办是什么、点击后能进入处理闭环

### 1.2 设计原则
- 第一屏回答“现在怎么样（态势）+ 为什么（线索）+ 该做什么（行动）”
- 指标数量可控：KPI 卡片 6~8 个为宜
- 图表少而精：主趋势图 1 个 + 结构分布图 1 个（其余用表格承载行动信息）
- 表格不是“展示全量”，而是“问题与热点的 TopN”
- 所有卡片/图表/表格都必须可跳转到二级页（分析/治理/工单/血缘/详情）

---

## 2. 首页信息架构图

### 2.1 首页四区结构（推荐：治理 + 业务）

```mermaid
flowchart TB
  A[顶部 KPI 总览区\\n(业务KPI + 数据资产 + 治理状态)] --> B[中部主视觉区\\n趋势分析(左) + 结构分布(右)]
  B --> C[表格行动区\\n质量问题TopN / 热门数据集&报表]
  B --> D[告警与待办区\\n告警摘要 / 待处理任务 / 近期变更]
```

### 2.2 布局建议（栅格）

- Row1：KPI 卡片 6~8 个（24 栅格分配：每卡 6 或 8）
- Row2：左 16（主趋势折线图），右 8（分布图 + 小卡片）
- Row3：左 16（质量问题 Top 表格），右 8（待办 + 告警列表）
- Row4：全宽（热门数据集/报表、最近发布、血缘变更等）

---

## 3. 模块说明（指标、交互与跳转建议）

### 3.1 顶部 KPI 总览区（态势）

目的：第一眼判断“业务是否健康、数据是否稳定、治理是否有风险”。

**A1 业务 KPI（示例）**
- 今日订单数 / 产值 / 交付数（可按行业替换）
- 环比/同比（↑↓ + %）
- 跳转建议：业务驾驶舱 / 主题域分析

**A2 数据资产 KPI（示例）**
- 数据集数、表数、指标数、主题域数
- 跳转建议：数据资产目录 / 指标中心

**A3 治理状态 KPI（示例）**
- 数据质量评分（DQ Score）
- 今日告警数 / 延迟任务数 / 失败任务数
- 跳转建议：质量总览 / 调度监控 / 告警中心

交互建议：卡片整体可点击（或右上角 “详情”）；支持切换“今日/本周/本月”。

### 3.2 中部主视觉区（趋势 + 结构）

**B1 趋势折线图（左大图）**
- 推荐组合：业务量 + 治理指标对照（便于发现因果线索）
- X：日期（近 7/14/30 天）
- Y1：业务量（订单数/交易额/请求量）
- Y2：质量评分或异常数（DQ Score / 异常记录数）
- 交互：
  - 鼠标悬浮显示 Tooltip
  - 图例可开关线条
  - 点击某天：下钻到“当天异常明细 / 当天业务波动原因页”

**B2 结构分布图（右侧）**
可选 1~2 个轮播展示：
- 来源分布：ERP/PLM/QMS/Excel/IoT
- 分层分布：ODS/DWD/DWS/ADS/MDM
- 密级分布：PUBLIC/INTERNAL/IMPORTANT/CORE
- 业务域分布：采购/生产/质量/财务/人资等

交互：点击某类进入该类资产清单页（带过滤条件）。

### 3.3 表格行动区（可执行信息）

**C1 数据质量问题 TopN（强烈建议）**
- 字段建议：数据集/表、规则、问题类型、影响行数、严重级别、责任人、状态、最近发生时间
- 交互：点击行进入“问题详情”（血缘影响范围、处理工单、修复建议）

**C2 热门数据集/报表 TopN（推荐）**
- 字段建议：名称、类型（数据集/报表/指标）、访问量、最近更新、状态
- 交互：点击进入“数据集详情 / 报表详情 / 指标解释页”

### 3.4 告警与待办区（闭环入口）

**D1 今日告警摘要**
- 质量告警、同步失败、SLA 超时、行数突变、口径变更
- 交互：进入告警中心（默认过滤今日）

**D2 待办事项**
- 待审核标准、待发布数据集、待处理质量规则、待确认口径变更
- 交互：进入任务中心（按角色过滤）

---

## 4. 三套首页方案（同一套组件，不同配置与优先级）

### 4.1 领导视角（决策态势）
- KPI：业务 KPI 权重最高（订单/产值/交付/成本/达成率）
- 趋势：业务趋势 + 关键风险（异常数/失败任务）对照
- 表格：高影响质量问题 Top5 + 关键报表 Top5
- 待办：仅保留“需领导审批/关注”

### 4.2 数据负责人视角（治理与价值）
- KPI：DQ Score、告警数、资产覆盖率、标准落地率
- 趋势：质量评分趋势 + 告警趋势 + 任务失败趋势
- 结构：来源分布、分层分布、密级分布
- 表格：质量问题 Top10（含责任人/工单/影响范围）、待办 Top10

### 4.3 工程师视角（排障与交付）
- KPI：失败任务数、延迟任务数、平均延迟、重试次数、资源水位（可选）
- 趋势：调度成功率/延迟趋势 + 异常数据量趋势
- 结构：按“系统来源/任务类型/失败原因”聚合
- 表格：失败任务列表、延迟任务列表、最新告警（重跑/忽略/派单）

---

## 5. Ant Design + React 首页 Demo（假数据）

下面是一份单文件 Demo（可直接复制到新项目的 `src/App.tsx` 运行），结构对应本方案的 4 区布局。

### 5.1 单文件 `App.tsx`

```tsx
import { useMemo, useState } from "react";
import { Card, Col, List, Row, Segmented, Space, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { LineChartOutlined } from "@ant-design/icons";
import dayjs from "dayjs";

type View = "leader" | "owner" | "engineer";

type Issue = { key: string; dataset: string; rule: string; severity: "P0" | "P1" | "P2"; rows: number; owner: string };
type Hot = { key: string; name: string; kind: string; views: number; updatedAt: string };

export default function App() {
  const [view, setView] = useState<View>("leader");
  const now = dayjs();

  const issues = useMemo<Issue[]>(() => ([
    { key: "1", dataset: "dwd_sales_order", rule: "订单金额非空", severity: "P0", rows: 1284, owner: "张三" },
    { key: "2", dataset: "dws_customer_profile", rule: "主键唯一性", severity: "P1", rows: 217, owner: "李四" },
  ]), []);

  const hot = useMemo<Hot[]>(() => ([
    { key: "1", name: "销售总览（领导驾驶舱）", kind: "报表", views: 1823, updatedAt: now.subtract(1, "hour").format("YYYY-MM-DD HH:mm") },
    { key: "2", name: "客户画像数据集", kind: "数据集", views: 934, updatedAt: now.subtract(6, "hour").format("YYYY-MM-DD HH:mm") },
  ]), [now]);

  const issueCols: ColumnsType<Issue> = [
    { title: "数据集/表", dataIndex: "dataset" },
    { title: "规则", dataIndex: "rule" },
    { title: "级别", dataIndex: "severity", width: 90, render: (v) => <Tag color={v === "P0" ? "red" : v === "P1" ? "orange" : "gold"}>{v}</Tag> },
    { title: "影响行数", dataIndex: "rows", width: 120, align: "right" },
    { title: "责任人", dataIndex: "owner", width: 120 },
  ];

  const hotCols: ColumnsType<Hot> = [
    { title: "名称", dataIndex: "name" },
    { title: "类型", dataIndex: "kind", width: 90 },
    { title: "访问量", dataIndex: "views", width: 120, align: "right" },
    { title: "最近更新", dataIndex: "updatedAt", width: 180 },
  ];

  return (
    <div style={{ padding: 16 }}>
      <Card
        title={<Space size={8}><LineChartOutlined />工作台 Demo</Space>}
        extra={
          <Segmented
            value={view}
            onChange={(v) => setView(v as View)}
            options={[
              { label: "领导", value: "leader" },
              { label: "负责人", value: "owner" },
              { label: "工程师", value: "engineer" },
            ]}
          />
        }
      >
        <Row gutter={[12, 12]}>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small"><Statistic title="数据质量评分" value={92} suffix="分" /></Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small"><Statistic title="今日告警" value={6} suffix="条" /></Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small"><Statistic title="数据资产" value={1280} suffix="个" /></Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small"><Statistic title="示例 KPI" value={view === "engineer" ? 3 : 92.4} suffix={view === "engineer" ? "个" : "%"} /></Card>
          </Col>
        </Row>

        <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
          <Col xs={24} lg={16}>
            <Card title="趋势图（此处接入图表组件）">主趋势图区域</Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card title="结构分布（此处接入分布图）">分布图区域</Card>
          </Col>
        </Row>

        <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
          <Col xs={24} lg={16}>
            <Card title="质量问题 TopN">
              <Table size="small" rowKey="key" columns={issueCols} dataSource={issues} pagination={false} />
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card title="告警与待办">
              <List
                size="small"
                dataSource={[
                  { key: "a", title: "SLA 超时：dws_customer_profile" },
                  { key: "b", title: "待审核：新增质量规则 2 条" },
                ]}
                renderItem={(it) => <List.Item key={it.key}>{it.title}</List.Item>}
              />
            </Card>
          </Col>
        </Row>

        <Card title="热门资产 / 报表 TopN" style={{ marginTop: 12 }}>
          <Table size="small" rowKey="key" columns={hotCols} dataSource={hot} pagination={false} />
        </Card>

        <Typography.Text type="secondary" style={{ display: "block", marginTop: 12 }}>
          说明：以上为首页信息架构示例（假数据），用于验证布局与交互，再逐步替换为真实 API 数据源。
        </Typography.Text>
      </Card>
    </div>
  );
}
```

### 5.2 最小项目脚手架

```bash
pnpm create vite workbench-demo --template react-ts
cd workbench-demo
pnpm add antd @ant-design/icons dayjs
pnpm dev
```

如需要折线/饼图，可增加 `apexcharts react-apexcharts` 或使用团队既有图表组件。

---

## 6. 在本仓库中的落地点

- 实际页面实现：`src/pages/workbench/index.tsx`
- 入口菜单：由 dts-admin 门户菜单下发（root `workbench`）

