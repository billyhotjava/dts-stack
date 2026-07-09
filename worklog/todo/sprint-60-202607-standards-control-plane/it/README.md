# Sprint-60 集成测试计划

## 目标

证明标准管理已经从“资料维护”进入“建模/指标/发布控制面”：

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

## 前端 source-contract

```bash
node --test \
  source/dts-platform-webapp/src/pages/governance/GlossaryPage.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/governance/DataStandardPackageTemplate.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/foundation/StandardPackageImport.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/modeling/modelSpecification.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/modeling/metricWorkbench.source-contract.test.ts
```

新增或更新测试应覆盖：

- [ ] 数据元页仍展示引用关系、码表选项和标准包模板下载。
- [ ] 模型规格页或建模入口能展示 ODS/DWD/DWS/ADS 分层草稿、DDL/dbt/ETL 预览。
- [ ] SQL 建模页仍有字段标准绑定、自动匹配、标准门禁、schema.yml 生成。
- [ ] SQL 人工微调后能展示字段契约、标准契约、血缘契约、质量契约校验结果。
- [ ] 指标工作台增加术语/口径/标准缺口后有稳定 testid。
- [ ] 低代码向导能跳到标准修复入口。

## 后端 targeted tests

候选命令：

```bash
cd source
./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-platform \
  -Dtest=ModelingSqlModelServiceTest,StandardPackageApplyServiceTest,ModelSpecificationGenerationServiceTest test
```

新增或扩展测试应覆盖：

- [ ] `saveStandardBindings` 写入语义契约。
- [ ] 标准包和来源表可以生成 ODS/DWD/DWS/ADS 模型规格。
- [ ] 模型规格可以生成 DDL、dbt model、schema.yml 和 ETL/SQL 骨架。
- [ ] `checkStandardGate` 对缺绑定、码表缺标准编码、drift 返回 blocker。
- [ ] SQL 微调后对输出字段、类型、血缘和质量规则做反校验。
- [ ] `generateSchemaYml` 输出标准 meta、not_null、relationships。
- [ ] 标准模板引用失效时不能生成假成功候选。
- [ ] 指标 ACTIVE 门禁阻断无业务术语或无数据元依赖的指标。

## 浏览器 smoke

| 场景 | 路径 | 证据 |
|------|------|------|
| 标准包导入/内置包 | `/foundation/standard-package` | 截图 + 预检结果 |
| 数据元引用关系 | `/governance/standards/elements` | 截图 + 引用抽屉 |
| 标准生成模型规格 | `/studio/sql-modeling` 或低代码建模入口 | 截图 + ODS/DWD/DWS/ADS 草稿 |
| DDL/dbt/ETL 骨架预览 | `/studio/sql-modeling` | 截图 + 预览结果 |
| SQL 建模字段标准绑定 | `/studio/sql-modeling` | 截图 + 标准门禁结果 |
| 低代码标准缺口 | `/studio/low-code-development` | 截图 + 修复入口 |
| 指标口径绑定 | `/modeling/metric-workbench` | 截图 + 指标 blocker |

## 阻断条件

- 标准只在标准管理页面可见，开发/指标/发布页面无法消费。
- 标准定义后无法生成模型规格、物理 DDL 或 ETL/SQL 骨架。
- SQL 人工微调可以绕过字段契约、标准契约或血缘契约。
- 数据元缺失不阻断受控 DWD/DWS/ADS 发布。
- 指标可以 ACTIVE 但没有业务术语、口径版本或字段标准依赖。
- 标准模板不参与低代码候选模型或发布审核。
- schema.yml 无法被 dbt parse，或写入路径不受控。

## 证据归档

实施时在本目录增加：

```text
it/evidence/
  sprint60-source-contract.log
  sprint60-maven-standard-gate.log
  sprint60-standard-package.png
  sprint60-model-specification.png
  sprint60-ddl-dbt-etl-preview.png
  sprint60-sql-modeling-standard-gate.png
  sprint60-metric-standard-blocker.png
```
