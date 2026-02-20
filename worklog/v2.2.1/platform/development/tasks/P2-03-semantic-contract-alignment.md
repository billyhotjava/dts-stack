# P2-03 语义契约对齐（模型-指标-看板）

- 优先级：P2
- 状态：done

## 范围

- 建立开发中心到可视化中心的语义契约，减少重复 SQL。

## 子任务

- 为模型增加可选“指标定义/维度定义”元信息。
- 看板中心绑定查询数据集时可显示指标契约版本。
- 引入“契约变更影响面”提示（影响报表数、字段数）。

## 验收标准

- 同一指标可在建模与看板中一致复用。
- 契约变更可追踪并可审计。

## 风险与回滚

- 风险：历史报表无契约元数据。
- 回滚：兼容“无契约模式”，逐步迁移。

## 已完成进展（2026-02-17）

- 建模模型已支持语义契约元信息（JSON）：
  - 表结构新增：`semantic_contract`、`contract_version`、`contract_updated_at`
  - 新增 Liquibase：`20260217_01_modeling_semantic_contract.xml`
  - `create/update` 自动规范化 JSON 并计算版本号（`sc-xxxxxxxxxxxxxxxx`）
  - 模型接口返回：
    - `semanticContract`
    - `contractVersion`
    - `contractUpdatedAt`
    - `metricCount`
    - `dimensionCount`
- 新增契约影响面接口：
  - `GET /api/modeling/sql-models/{id}/contract-impact`
  - 返回字段：
    - 指标/维度数量
    - 字段数量
    - 受影响查询数据集数量与清单
    - 受影响报表数量与清单
- 查询数据集（SQL 沉淀）已补充契约信息：
  - `GET /api/sql/query-datasets` 增加
    - `semanticContractVersion`
    - `semanticModelCount`
    - `semanticModelNames`
  - 基于 SQL 中 `ref('model')` 自动关联模型契约
- 前端联动完成：
  - 逻辑建模页支持编辑“语义契约(JSON)”
  - 右侧面板展示契约版本、指标/维度、影响报表与影响字段
  - 看板中心绑定数据集下拉项展示契约版本（如：`xxx · 契约 sc-...`）
  - 查询数据集管理页增加“契约版本”列

## 影响文件

- `source/dts-platform/src/main/resources/config/liquibase/changelog/20260217_01_modeling_semantic_contract.xml`
- `source/dts-platform/src/main/resources/config/liquibase/master.xml`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingSqlModelResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/QueryDatasetService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/dto/QueryDatasetResponse.java`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- `source/dts-platform-webapp/src/api/sql-workbench.ts`
- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/pages/services/BiLinksPage.tsx`
- `source/dts-platform-webapp/src/components/sql/QueryDatasetManager.tsx`

## 回归结果（2026-02-17）

- `mvn -f source/dts-platform/pom.xml -DskipTests compile`：通过
- `pnpm -C source/dts-platform-webapp build`：通过
