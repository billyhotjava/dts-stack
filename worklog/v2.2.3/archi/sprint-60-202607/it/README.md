# Sprint-60 集成验收

## 验收环境

- 前端：`source/dts-platform-webapp`
- API/后端：`source/dts-platform`
- 元数据数据库：PostgreSQL 测试库
- 运行编排：Addax 测试任务 + Airflow 测试 DAG
- dbt：PJM fixture 或最小 dbt 项目
- 浏览器：Chrome 95 兼容模式 + Playwright

## 必测场景

1. 业务过程 → 业务对象 → 粒度 → 数据标准 → DWD ModelSpec。
2. ModelSpec 编译出 SQL、schema.yml、tests 和 docs。
3. 设计器生成模型提交审核，并投递 Airflow。
4. 高级开发导入 dbt manifest，页面展示模型、字段、血缘和运行状态。
5. SQL 修改造成字段或粒度漂移时，发布被阻断并显示修复动作。
6. Addax 批次、dbt run、dbt test、Airflow DAG、PostgreSQL 目标表可串联查询。
7. 旧 `/api/semantic/*` 页面和旧 dbt 模型仍可读取或导入。

## 证据目录

- `it/evidence/frontend/`
- `it/evidence/api/`
- `it/evidence/backend/`
- `it/evidence/dbt/`
- `it/evidence/airflow/`
- `it/evidence/playwright/`

完成 Sprint 前必须在本目录记录命令、结果、截图或 JSON 响应，不能只写“已验证”。

## 标准控制面专项验收

本专项验证标准管理是否从资料维护进入建模、指标和发布控制面：

```text
业务术语 + 数据元 + 公共码表 + 标准模板
  -> 资产字段落标
  -> ODS/DWD/DWS/ADS 模型规格
  -> DDL / dbt model / ETL SQL 骨架
  -> SQL/dbt 模型标准绑定
  -> schema.yml / dbt tests
  -> 指标口径绑定
  -> 发布门禁
```

### 前端 source-contract

```bash
node --test \
  source/dts-platform-webapp/src/pages/governance/GlossaryPage.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/governance/DataStandardPackageTemplate.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/foundation/StandardPackageImport.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/modeling/modelSpecification.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/modeling/metricWorkbench.source-contract.test.ts
```

### 后端 targeted tests

```bash
cd source
./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-platform \
  -Dtest=ModelingSqlModelServiceTest,StandardPackageApplyServiceTest,ModelSpecificationGenerationServiceTest test
```

重点覆盖标准绑定、标准包到模型规格/DDL/dbt/ETL 骨架、标准门禁、schema.yml 元数据、模板失效和指标 ACTIVE 阻断。

### 标准控制面浏览器 smoke

| 场景 | 路径 |
|------|------|
| 标准包导入/内置包 | `/foundation/standard-package` |
| 数据元引用关系 | `/governance/standards/elements` |
| 标准生成模型规格 | `/studio/sql-modeling` 或低代码建模入口 |
| DDL/dbt/ETL 骨架预览 | `/studio/sql-modeling` |
| SQL 建模字段标准绑定 | `/studio/sql-modeling` |
| 低代码标准缺口 | `/studio/low-code-development` |
| 指标口径绑定 | `/modeling/metric-workbench` |

标准专项证据与既有建模运行证据统一归档到本目录的 `evidence/` 下。
