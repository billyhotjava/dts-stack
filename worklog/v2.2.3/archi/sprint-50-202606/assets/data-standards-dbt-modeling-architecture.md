# 数据标准与 dbt 模型联动架构设计

## 设计结论

数据标准不应做成一个孤立后台模块，也不应在数据开发中心新增一个重复页面。正确的产品关系是：

1. 先完善现有前端页面承载面，让标准管理、逻辑建模、dbt 文件浏览具备统一页面结构、按钮状态、空态、错误态和路由交接。
2. 标准管理负责定义“字段和码值应该是什么”。
3. 逻辑建模负责把标准绑定到 dbt 模型字段，并生成可执行契约。
4. dbt 负责通过 `schema.yml`、`seeds`、`manifest.json`、`run_results.json` 形成可验证证据。
5. 发布门禁负责阻断标准缺失、码表未同步、标准版本漂移和下游影响未确认。
6. 资产和语义层只消费发布后的模型结果，不反向成为标准来源。

因此，主入口仍然是现有 `数据开发中心 / 逻辑建模（SQL）`，标准维护仍然在 `数据治理中心 / 标准管理`。

## 前置 Feature: F0 前端页面承载面完善与重构

F0 是 Sprint-50 的前置 feature，必须先做。原因是标准/dbt 联动最终要落到页面、按钮、字段表、抽屉、诊断和发布动作上；如果页面承载面没有先整理好，后续很容易再次出现后台能力存在但前端割裂、按钮无状态、组件无法承接契约的问题。

| 页面 | 前置重构重点 | 必须覆盖的控件状态 |
|------|--------------|--------------------|
| `SqlModelingPage` | 模型列表、字段表、模型详情、编译/测试/构建/发布按钮、诊断抽屉、更多操作菜单 | default、loading、empty、disabled with reason、error/retry、permission denied、success |
| `ModelEditDrawer` | 模型基本信息、SQL 编辑、保存动作、后续字段标准映射承载位 | default、saving、validation error、backend unavailable、success |
| `ElementsPage` | 数据元列表、搜索、新增/编辑、详情、引用关系抽屉、码表选择 | default、loading、empty、disabled by permission、error/retry、success |
| `ReferenceCodesPage` | 码表列表、码表项、映射、导入、更新 dbt Seeds、引用关系抽屉 | default、loading、empty、syncing、sync failed、permission denied、success |
| `DbtFileBrowserPage` | 文件树、编辑器、保存、运行 dbt、预览模型、回到 SQL 建模发布 | default、loading、dirty、save failed、run failed、handoff to SQL modeling |

F0 完成后，F1-F6 才能进入编码。F0 不引入新的业务规则，也不内置客户现场场景，只解决现有页面能否承载真实标准/dbt 工作流。

## 端到端链路

| 阶段 | 页面 | 用户动作 | 系统契约 | 产物 |
|------|------|----------|----------|------|
| 1. 定义标准 | 数据元 | 维护字段名、类型、长度、是否可空、码表、安全等级 | `/modeling/metadata-standards` | 字段级标准 |
| 2. 定义码表 | 公共码表 | 维护码表项，点击“更新 dbt Seeds” | `/governance/reference-codes`、`/governance/reference-codes/seeds` | seeds csv/yml |
| 3. 开发模型 | 逻辑建模（SQL） | 创建或编辑 ODS/DWD/DWS/ADS 模型 | `/modeling/sql-models` | SQL 模型草稿 |
| 4. 绑定标准 | 逻辑建模（SQL） | 在字段列表中选择/自动匹配数据元 | 新增模型字段标准绑定契约 | ModelStandardBinding |
| 5. 生成 dbt 契约 | 逻辑建模（SQL） | 保存模型或运行治理预览 | `/modeling/sql-models/governance/preview`、后续生成 schema.yml | dbt `schema.yml` |
| 6. 校验模型 | 逻辑建模（SQL） | 编译、测试、构建 | `/etl/dbt/compile`、`/etl/dbt/test`、`/etl/dbt/run` | manifest/run_results |
| 7. 发布门禁 | 逻辑建模（SQL） | 点击发布 | `/etl/dbt/quality-gate/check`、`/etl/dbt/release-gate/check`、`/etl/dbt/release/submit` | 发布决策和运行证据 |
| 8. 资产同步 | 资产/语义页面 | 查看数据集、字段、指标影响 | manifest 同步、资产目录同步 | 数据资产、字段、血缘、语义指标 |

## 页面能力矩阵

| 页面/组件 | 当前能力 | 要增强的联动 | 按钮/组件要求 | 风险 |
|-----------|----------|--------------|---------------|------|
| `ElementsPage` 数据元列表 | 已维护字段名、类型、可空、码表、安全等级；已有引用关系抽屉 | 引用关系应能展示被哪些 dbt 模型字段使用 | “查看引用”保留 Drawer；后续增加“查看使用模型”或复用引用抽屉 | 不能把标准使用情况做成静态假数据 |
| `ReferenceCodesPage` 公共码表 | 已有“更新 dbt Seeds”按钮和码表项维护 | 同步后展示 seed 路径、行数、版本和最近同步时间 | “更新 dbt Seeds”失败时给出原因；禁用态说明权限不足 | seeds 已生成但模型未引用会造成误解 |
| `SqlModelingPage` 逻辑建模 | 已有 compile/test/build/release、模型字段、诊断、契约影响 | 增加字段标准映射区，展示映射状态、标准版本、码表状态 | 字段表用 Select/Tag/Tooltip；不引入复杂拖拽或画布 | 这是主工作台，改动必须有 source-contract |
| `ModelEditDrawer` 模型编辑抽屉 | 已承载模型基本信息和 SQL | 保存模型时携带字段标准绑定摘要或触发映射保存 | 保存按钮需区分模型保存失败和标准映射失败 | 后端未升级时需明确降级状态 |
| `OdsGenerateModal` ODS 生成 | 已支持从 ODS 生成模型 | 生成 DWD/DWS/ADS 时使用数据元作为字段命名、类型和质量规则建议 | 生成前展示标准匹配率；允许用户确认 | 现场业务规则不能内置为 demo |
| `DbtModelDiagnosticsDrawer` 诊断抽屉 | 已展示模型诊断、运行状态、上游依赖 | 增加标准诊断：缺失映射、版本漂移、码表 seed 未同步 | 诊断项只展示证据和修复入口 | 不应在诊断抽屉内直接做复杂编辑 |
| `DbtFileBrowserPage` 项目文件浏览 | 可浏览、编辑、运行 dbt 文件 | 作为 schema.yml/seeds 的底层证据面 | 发布继续引导回 SQL 建模页 | 不能让普通用户绕过建模门禁直接发布 |

## 数据契约

### 数据元标准

现有前端字段已经具备 dbt column contract 的基础：

| 数据元字段 | dbt column 作用 |
|------------|-----------------|
| `fieldNameEn` | column name 或标准字段名 |
| `fieldNameCn` | column description 的中文名称 |
| `dataType`、`dataLength`、`dataPrecision`、`dataScale` | data type contract |
| `nullable` | `not_null` test 的来源 |
| `codeSet` | accepted values 或 seed relationship test 的来源 |
| `securityLevel` | column meta 中的数据安全标签 |
| `domain`、`sourceSystem` | 主题域、来源系统元数据 |
| `description` | 字段格式规则或业务解释 |

### 模型字段标准绑定

后续需要补齐一个轻量绑定契约，不要求新增页面：

```ts
type ModelStandardBinding = {
  modelId: string;
  modelName: string;
  columnName: string;
  standardCode: string;
  standardVersion: string;
  bindingSource: "manual" | "auto" | "ods-generate" | "manifest";
  confidence?: number;
  status: "draft" | "active" | "drift" | "missing";
  driftReason?: string;
  lastValidatedAt?: string;
};
```

### dbt schema.yml 输出

标准绑定必须落到 dbt 文件，而不是只停留在数据库或前端状态：

```yaml
models:
  - name: dwd_order_detail
    description: 订单明细标准化模型
    columns:
      - name: order_status
        description: 订单状态
        data_type: varchar
        tests:
          - not_null
          - relationships:
              to: ref('seed_order_status')
              field: code
        meta:
          dts:
            standardCode: STD_ORDER_STATUS
            standardVersion: v1.0
            codeSet: ORDER_STATUS
            securityLevel: INTERNAL
            domain: order
            bindingSource: manual
```

### 公共码表到 seeds

公共码表不直接写死到 SQL。推荐链路：

1. 码表维护在 `/governance/standards/reference`。
2. 点击“更新 dbt Seeds”生成 `seeds/reference/<codeTypeCode>.csv` 和对应 schema。
3. 字段标准的 `codeSet` 引用码表编码。
4. dbt tests 使用 generated literal `accepted_values` 或 seed relationship test 校验码值。
5. 发布门禁检查字段引用的码表 seed 是否已同步、版本是否一致。

## 发布门禁规则

| 场景 | 草稿保存 | dbt test/build | 发布 |
|------|----------|----------------|------|
| 字段无标准映射 | 允许，提示缺失 | warning | P0 模型阻断，非 P0 模型可警告 |
| 字段类型与标准不一致 | 允许，提示差异 | warning 或 fail | 阻断 |
| `nullable=false` 但缺少 not_null test | 允许 | fail | 阻断 |
| 字段绑定码表但 seed 未同步 | 允许 | warning | 阻断 |
| 标准版本已废弃或漂移 | 允许，提示升级 | warning | 阻断或需确认 |
| 下游指标/报表受影响 | 允许 | warning | 需要展示影响并确认 |

## 架构边界

| 层 | 职责 | 不负责 |
|----|------|--------|
| 标准管理 | 标准、数据元、码表、版本 | 不直接发布 dbt 模型 |
| 数据开发中心 | SQL 模型、字段映射、dbt 运行、发布门禁 | 不重新维护一套标准主数据 |
| dbt 项目 | 生成物、测试、血缘、运行证据 | 不作为业务标准审批系统 |
| 资产目录 | 消费发布后的模型、字段、血缘 | 不绕过发布门禁修改模型 |
| 指标与语义 | 消费已发布模型形成指标和数据集 | 不反向定义字段标准 |

## API 规划

优先复用现有 API：

| 能力 | 现有 API |
|------|----------|
| 数据元列表/维护 | `/modeling/metadata-standards` |
| 数据元引用 | `/modeling/metadata-standards/{id}/references` |
| 公共码表 | `/governance/reference-codes` |
| 码表 seeds 同步 | `/governance/reference-codes/seeds` |
| SQL 模型 | `/modeling/sql-models` |
| 模型字段 | `/modeling/sql-models/{id}/columns` |
| 治理预览 | `/modeling/sql-models/governance/preview` |
| dbt 编译/测试/构建 | `/etl/dbt/compile`、`/etl/dbt/test`、`/etl/dbt/run` |
| dbt 门禁 | `/etl/dbt/quality-gate/check`、`/etl/dbt/release-gate/check` |
| dbt 文件证据 | `/etl/dbt/files/*` |

需要补齐的后端契约：

| 能力 | 建议 API | 页面入口 |
|------|----------|----------|
| 模型字段标准映射查询 | `GET /modeling/sql-models/{id}/standard-bindings` | SQL 建模字段表 |
| 保存模型字段标准映射 | `PUT /modeling/sql-models/{id}/standard-bindings` | SQL 建模字段表/抽屉 |
| 自动匹配标准预览 | `POST /modeling/sql-models/{id}/standard-bindings/preview` | “自动匹配标准”按钮 |
| 应用自动匹配结果 | `POST /modeling/sql-models/{id}/standard-bindings/apply` | 匹配预览确认 |
| 生成 dbt schema.yml | `POST /modeling/sql-models/{id}/dbt/schema-yml` | 保存/治理预览/发布前 |
| 标准门禁检查 | `POST /modeling/sql-models/{id}/standard-gate/check` | 发布门禁 |

## 实现顺序建议

1. F0 先完善和重构前端页面承载面：页面结构、按钮状态、组件边界、空态/错误态、路由交接和 Chrome95 基线。
2. F1 做 source-contract 和页面矩阵。验证现有按钮和 API 名称稳定。
3. F2 在 SQL 建模页字段表增加“标准映射”最小闭环：状态 Tag、选择数据元、保存映射、失败态。
4. F3 增强公共码表 seeds 同步反馈：路径、行数、版本、最近同步时间。
5. F4 从标准映射生成 dbt `schema.yml` 和 column meta，并在项目文件浏览中可见。
6. F5 把标准门禁并入 compile/test/build/release 的现有流程。
7. F6 做 Chrome95 smoke、source-contract、构建和代码 review。

## 非目标

- 不新增“标准建模中心”菜单。
- 不做复杂拖拽编排或画布式标准映射。
- 不内置客户业务场景、字段模板或行业 demo。
- 不让项目文件浏览绕过 SQL 建模页的发布门禁。
- 不把指标语义层作为字段标准的主数据来源。

## 验收关注点

| 验收项 | 证据 |
|--------|------|
| 页面不割裂 | 从数据元/码表进入模型字段引用和 dbt 证据 |
| 不是假实现 | 标准绑定写入后端并生成 dbt schema/seeds |
| 发布可控 | release gate 能阻断标准缺失和码表未同步 |
| 客户可理解 | 页面用“数据元、公共码表、模型字段、发布检查”等业务词 |
| Chrome95 可用 | 表格、Drawer、Select、Tag、Tooltip 即可完成流程 |
