# Sprint-14: Excel 导入内核统一与解析重构

**时间**: 2026-04
**状态**: READY
**类型**: Implementation（兼容式重构，平台侧先收口，保持现有对外接口与 Addax CSV 落地契约）
**目标**: 把 Excel 从“上传后转 CSV 的工具链”升级为“正式一等数据源解析内核”，统一 `dts-platform` 与 `dts-ingestion` 的 Excel 解析规则，解决负数、本地化日期、公式、合并单元格等长期兼容问题。

## 背景

### 现状问题

1. **双链路分裂**：正式导入走 `source/dts-platform/.../ExcelImportService.java`，预检又走 `source/dts-ingestion/.../ExcelParseService.java`，两套解析规则天然漂移。
2. **过早 CSV 化**：平台侧在 parse 阶段直接写 `data.csv`，导致原始类型、显示格式、公式、格式码等 Excel 语义过早丢失。
3. **值分层不足**：当前大多数地方只有一个字符串值，无法区分 raw/display/normalized，定位问题靠猜。
4. **客户 Excel 占比高**：现场反馈约 60% 数据通过 Excel 导入，这说明 Excel 解析质量不是边缘能力，而是主链路质量门槛。
5. **现网已暴露格式 bug**：中文或非常规负数格式（如 `{900}`、`（88）`、全角负号）进入 ODS 后污染数据，再经 dbt cast 变成 `NULL`。

### 本 Sprint 解决什么

本 Sprint 不做“直接跳过 CSV、让 Addax 读原始 xlsx”的大改，而是先完成下面四件事：

1. 统一一个 **POI-based Excel Core**，作为平台侧唯一解析内核。
2. 在内部引入 `rawValue / displayValue / normalizedValue` 三层语义。
3. 平台侧继续输出现有 `data.csv` / `error.csv`，但改为“先解析、后产物化”。
4. `dts-ingestion` 预检复用同一套规则，不再维护另一份 Excel 语义。

## 约束与非目标

### 硬约束

1. **对外 REST 契约保持兼容**：`/api/infra/excel-import/prepare|parse|errors` 本 Sprint 不改 URL 与主响应结构。
2. **Addax 落地契约保持兼容**：下游仍消费 `csvPath / csvContainerPath`，本 Sprint 不改为直接读取 xlsx。
3. **平台侧先收口**：Excel 核心放在 `dts-platform`，`dts-ingestion` 只复用，不再并行演进另一套主逻辑。
4. **优先修语义兼容，不优先追极限性能**：先保证“解析正确、可解释、可回归”，后续再按需要引入流式优化。

### 非目标

- 直接替换 Addax 文件 reader，使其消费结构化 Excel Artifact
- 支持任意复杂 Excel 视觉对象（图表、批注、嵌入对象）
- 完整实现 `.xls` 老格式的高保真兼容矩阵
- 自动回填历史 ODS 脏数据（单独 followup）
- 客户端页面大幅改造；前端只允许最小兼容调整

## 架构概览

```
上传 Excel/CSV
    │
    ▼
ExcelImportResource
    │
    ▼
ExcelParseFacade                              ← 新增统一入口
    │
    ├─ ExcelWorkbookGateway                   ← 打开文件 / 选 sheet / 枚举 sheet
    ├─ ExcelSheetScanner                      ← 逐行逐格扫描（POI）
    ├─ ExcelCellNormalizer                    ← 负数 / 日期 / 文本 / 全角 / 公式值规范化
    ├─ ExcelSchemaInferer                     ← 列名、类型、置信度
    └─ ExcelArtifactWriter                    ← data.csv / error.csv / manifest.json
    │
    ├─ 前端预览、错误回显、列推断
    └─ ingestion / Addax 继续消费 csvPath

dts-ingestion.ExcelParseService
    │
    └─ 复用 ExcelCellNormalizer / 共享策略，不再单独定义另一套格式语义
```

## Feature 列表

| ID | Feature | Task 数 | 状态 | 依赖 |
|---|---|---:|---|---|
| F1 | 统一 Excel 解析内核 | 3 | READY | — |
| F2 | 平台侧 ExcelImport 流水线重构 | 3 | READY | F1 |
| F3 | ingestion 预检与正式导入一致性收敛 | 3 | READY | F1, F2 |
| F4 | 兼容样本库与回归体系 | 2 | READY | F1, F2, F3 |

**共 11 tasks。**

## 阶段节奏

```
阶段 1（2 天）─ F1：统一 Artifact / Policy / Normalizer 抽象
阶段 2（3 天）─ F2：平台侧切到新解析流水线，保持 REST 与 CSV 契约不变
阶段 3（2 天）─ F3：ingestion 预检复用核心规则，打通一致性
阶段 4（2 天）─ F4：补 fixtures、回归矩阵、样本验证与历史修复方案
阶段 5（1 天）─ 收口文档、验收、灰度与回滚说明
```

## 完成标准

- [ ] `dts-platform` 存在统一 Excel 核心包，平台侧 parse 主链路不再依赖 EasyExcel 读主数据
- [ ] 平台侧对 `{900}`、`(900)`、`（88）`、`－900`、`−1200` 等负数格式输出标准化值
- [ ] 平台侧与 ingestion 预检对同一份样本文件给出一致的规范化结果
- [ ] `data.csv` / `error.csv` 继续产出，现有前端与 Addax 契约不破
- [ ] fixtures 样本至少覆盖：负数、日期、公式、合并单元格、空白行、千分位
- [ ] 至少有一份历史脏数据修复建议文档，供后续执行

## 范围外（留后续 Sprint）

- 让 Addax 直接消费 Excel Artifact 或原始 xlsx
- `.xls` 全量兼容性专项
- 公式重算缓存策略的运行时配置化
- 列级格式码透传到建模 / ODS 元数据
- 历史 ODS 污染数据的一键回灌工具

## 与已有 Sprint 的关系

| 相关 Sprint | 关系 |
|---|---|
| Sprint-4（数据质量管控体系重构） | Sprint-4 做了 Excel 入湖预检与修复工作台；本 Sprint 解决其底层 Excel 解析一致性与可解释性问题。 |
| Sprint-5（dbt 自动生成引擎） | 现有 CSV 落地契约与 ODS→dbt 链路保留，本 Sprint 不碰 dbt 生成方向，只保证上游 Excel 质量。 |
| Sprint-13（语义层） | 两者独立；但若后续要把 Excel 也纳入语义层或数据目录，需先完成本 Sprint 的统一解析底座。 |

## 参考代码入口

- 平台侧入口：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/ExcelImportService.java`
- 平台侧 REST：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/infra/ExcelImportResource.java`
- ingestion 预检：`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/ExcelParseService.java`
- 前端消费接口：`source/dts-platform-webapp/src/api/services/dataSourcesService.ts`
