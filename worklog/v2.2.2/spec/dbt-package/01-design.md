# dbt Package Design

## 背景与问题

现场版本存在三个核心约束：

- 现场不能安装 AI，优化工作需要在总部离线完成。
- 现场不能依赖 Java 发版，dbt 交付需要尽量以文件和配置为主。
- 现场手工执行 `dbt seed/run` 时，不应该默认污染平台 `项目空间 / 逻辑建模` 元数据。

当前产品还存在几类历史问题：

- `逻辑建模` 页面加载时会自动扫描 dbt 工作区并写回 `modeling_sql_model`。
- `项目空间` 允许同名创建，前端树按原始列表直接展示，重复后很难区分。
- `dbt run/build/test` 触发后会提前把模型状态更新成 `PUBLISHED/TESTED`，与真实运行结果脱节。
- `source` 定义仍然存在 `ods_sources.yml` 与项目管理专用 source 双轨并存的问题。

## 目标

- 提供一套总部离线产出的 dbt package 交付模型。
- 现场默认只做“安装包 + 跑包”，不默认创建项目空间，不默认注册逻辑建模。
- 将 dbt 文件交付、dbt 执行、平台注册三类动作拆开。
- 让回滚和卸载按 package 精确执行。

## 非目标

- 第一版不引入现场在线 AI。
- 第一版不做一键“装包并注册模型”。
- 第一版不把 dbt package 与 card/sql/template 包统一到一个总包协议里。

## 核心原则

1. 文件交付与平台注册解耦。
2. 默认保守，不做隐式创建。
3. `source` 定义必须单一权威。
4. 注册动作必须显式。
5. 回滚必须按 package 精确执行。

## 总体架构

总部离线环境负责：

- 优化和校验专题 dbt 模型
- 产出 `dbt package zip`
- 产出版本号、校验值和安装说明

客户现场负责：

- `install` 将 package 解压到 `services/dts-dbt`
- `run` 执行 `dbt seed/run/build/test`
- 可选 `register` 将模型显式注册到平台
- 需要时 `reset` 或安装旧包回滚

平台负责：

- 提供稳定的 dbt workspace
- 提供显式元数据注册入口
- 提供 package-aware reset 与状态查询

## Package 边界

package 包含：

- `models/`
- `macros/`
- `seeds/`
- `tests/`
- 可选 `analyses/`
- `package-manifest.json`
- `README-install.md`

package 不包含：

- `profiles/`
- `target/`
- `logs/`
- `dbt_packages/`
- Airflow DAG
- 数据库凭据
- 平台 Java 配置

## 四段式生命周期

### 1. install

动作：

- 解压 package
- 校验 manifest
- 覆盖受控目录
- 更新安装注册表

不做：

- 不跑 dbt
- 不创建项目空间
- 不导入逻辑建模

### 2. run

动作：

- 执行 `dbt seed`
- 执行 `dbt run/build/test`
- 生成目标表

不做：

- 不改 `modeling_plan`
- 不改 `modeling_sql_model`

### 3. register

动作：

- 显式将模型注册进平台逻辑建模

约束：

- 必须指定 `--plan-id`
  或
- 显式使用 `--create-plan --plan-name`

### 4. reset

动作：

- 按 package manifest 删除该包写入的文件
- 清理 package 注册信息
- 可选清理 `target/`、`logs/`

不做：

- 不删除其他专题文件
- 不删除平台骨架文件

## 元数据策略

dbt package 第一版必须把“数仓交付”和“平台元数据展示”拆开：

- `install/run` 属于数仓交付动作。
- `register` 属于平台元数据动作。

因此：

- `逻辑建模` 页面加载时不得再隐式触发工作区扫描。
- `dbt seed/run` 本身不得隐式创建 `项目空间`。
- register 必须显式触发，并带上明确的 plan 目标。

## 风险与约束

- 历史环境可能已有脏工作区或遗留 `pm_ods_sources.yml`。
- 历史环境可能已有重复的 ODS 映射记录，导致 `ods_sources.yml` 生成重复 table。
- 历史环境可能已有同名 `modeling_plan`，前端树会直接重复展示。
- 目前 dbt 发布状态流转仍然偏乐观，需要先修复。

## 验收标准

- 仅执行 `install/run` 时，不新增 `modeling_plan`。
- 仅执行 `install/run` 时，不新增 `modeling_sql_model`。
- 仅在显式 `register` 后，模型才进入逻辑建模。
- `reset --package <code>` 只删除该包文件，不影响其他专题。
- `source` 定义只保留一套权威来源。
