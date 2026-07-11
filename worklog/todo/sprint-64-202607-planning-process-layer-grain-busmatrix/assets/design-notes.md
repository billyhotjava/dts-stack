# Sprint-64 设计要点

## 1. 对标结论（DataWorks 智能建模四模块）

| DataWorks | 我们现状 | 本 sprint 动作 |
|---|---|---|
| 业务分类：数据域→业务过程 | 主题域树 ✔，业务过程 ✘ | F1 补业务过程 |
| 分层设计 | 硬编码 5 层卡片 | F2 注册表化 + 依赖红线校验 |
| 概念模型（维度先于表） | 直接进建模 | F4 一致性维度登记 + 总线矩阵 |
| 命名词典/度量单位 | v4 规范未产品化 | 本 sprint 不做，列 sprint-65 候选 |
| 指标时间周期/修饰词 | 公式为文本 | 归指标工作台演进，不在规划域 |

## 2. 核心契约

```ts
type BusinessProcess = {
  version: 1;
  processId: string;
  domainId: string;
  name: string;            // 如 节点计划闭环 / 质量问题归零 / 风险提出与释放
  description?: string;
  createdAt: string;
};

type WarehouseLayerDefinition = {
  key: "ODS_RAW" | "ODS_STANDARDIZED" | "DWD" | "DWS" | "ADS";
  title: string;
  responsibility: string;      // 层职责（来自 model-governance）
  allowedUpstream: string[];   // 允许依赖的上游层
  namingPrefixes: string[];    // 命名前缀（ods_ / biz_dwd_ / dim_ ...）
};

type GrainDeclaration = {
  statement: string;           // 一行代表什么（自然语言，必填）
  grainKeys: string[];         // 粒度键字段（≥1）
};

type ConformedDimension = {
  dimensionId: string;
  name: string;                // 完成情况 / 节点类型 / ...
  sourceModel?: string;        // 已实现载体（如 dim_completion_status_v2）
  domainIds: string[];         // 出现在哪些主题域
};

// 总线矩阵 = processId × dimensionId 勾选集合（session 存储，矩阵视图渲染）
```

- 业务过程/维度登记/矩阵沿用 Sprint-63 的版本化 session 草稿 + 注入式 storage 模式；status 一律计算态。
- 分层注册表是**静态受控常量**（不是 session）：它是规范不是用户数据；可配置化列后端缺口。

## 3. 门禁扩展（在 Sprint-63 dimensionCandidateGate 之上）

新增检查项：`processId` 存在、`grain.statement` 非空且 `grainKeys ≥ 1`、目标层与来源层满足 `allowedUpstream`（如 ADS 直读 ODS → blocked）。ready/missing/blocked 三态与修复路由沿用既有模式。

## 4. 与旅程机制集成

- `processId` 进 `JOURNEY_CONTEXT_PARAM_KEYS`（连锁：LABELS、快照、清参、校验 API 名、既有测试回归——同 Sprint-63 3.3 清单）。
- grain 与矩阵不进 URL（体积大、非索引），随 planning/模型草稿 session 存储走。

## 5. 后端 API 缺口表

| 缺口 | 未来接口（建议） | 当前替代 |
|---|---|---|
| 业务过程 CRUD | `GET/POST /api/governance/subject-domains/{domainId}/processes` | session 草稿 |
| 分层注册配置化 | `GET /api/governance/warehouse-layers` | 静态 ts 常量 |
| 一致性维度登记 | `GET/POST /api/modeling/conformed-dimensions` | session + canonical 种子常量 |
| 总线矩阵 | `GET/PUT /api/governance/bus-matrix/{domainId}` | session 勾选集合 |
| 模型 grain 持久化 | 模型实体增加 grain 字段 | 模型草稿 metadata |

## 6. 种子数据

- 业务过程示例（文案内置，创建时可一键采用）：节点计划闭环、质量问题归零、风险提出与释放。
- 一致性维度种子（8）：完成情况、节点类型、风险等级、质量归零状态、质量原因分类、技术状态更改类别、签署状态、风险分类——sourceModel 指向对应 dim_*_v2。
