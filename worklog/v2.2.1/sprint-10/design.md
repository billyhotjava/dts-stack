# dbt 工作区自举与产出表管理修复设计

日期：2026-03-16
Sprint：v2.2.1 / sprint-10

## 背景

当前逻辑建模的 dbt 工作区能力存在三个结构性问题：

1. `services/dts-dbt` 在客户环境初始可能是空目录，但平台只校验“可写”，不会自动生成一个最小可运行 dbt 项目
2. 批量导入模型只做 ZIP 解包和记录写入，不保证工作区真实落盘成功，容易出现“数据库已导入、工作区无文件”的假成功
3. “清空产出表 / 重建产出表” 走的是通用 rollback 代理链路，语义属于接入回滚，不属于 dbt 模型产出管理

这些问题叠加后，逻辑建模在客户环境里会表现为：
- 批量导入失败或导入后工作区不可运行
- 空工作区 compile/build/docs 无法稳定运行
- 清空/重建产出表操作失败，且报错语义与 dbt 模型无关

## 目标

1. 将空目录 `services/dts-dbt` 自动补齐为最小可运行 dbt 工作区
2. 让单模型导入和批量导入在空工作区下稳定成功，并保证“导入成功 = DB 记录 + 工作区落盘都成功”
3. 把“清空产出表 / 重建产出表”改成 dbt 原生 relation 管理，不再依赖 rollback 代理
4. 对前端暴露可理解的工作区状态、导入失败原因和产出表维护结果

---

## Part 1：最小 dbt 工作区自举

### 设计原则

- **幂等自愈**：缺什么补什么，不覆盖用户已有文件
- **多入口兜底**：不是只在保存配置时初始化，而是在所有依赖 dbt 项目的入口前都能自愈
- **最小可运行**：目标不是生成完整模板，而是让 dbt compile/build/docs 能识别项目并运行

### 自动生成骨架

初始化后工作区至少包含：

```text
services/dts-dbt/
├── dbt_project.yml
├── README.md
├── .gitignore
├── models/
│   ├── ods/
│   ├── dwd/
│   ├── dws/
│   ├── ads/
│   └── ods_sources.yml
├── macros/
│   └── get_custom_schema.sql
├── seeds/
├── tests/
├── analyses/
├── snapshots/
├── target/
└── logs/
```

### 自举时机

以下入口在真正执行前都应调用 bootstrap：

- `DbtConfigService.ensureConfigFile()`
- `DbtConfigService.loadConfig()`
- `DbtConfigService.saveConfig()`
- `ModelingSqlModelService.create()/update()/importFromFiles()/batchImportFromArchive()`
- `EtlResource` 触发 `compile/test/build/docs`
- 模型产出表的 truncate/rebuild 入口

### 关键规则

- `dbt_project.yml` 仅在不存在时生成
- `models/ods_sources.yml` 如不存在则创建最小占位：
  - `version: 2`
  - `sources: []`
- 目录一律按缺失补齐
- 平台托管文件支持“内容为空时修复”，但不覆盖非空用户文件

---

## Part 2：批量导入修复

### 已识别问题

当前导入链路为：

```text
batchImportFromArchive
  -> importFromFiles
    -> create
      -> writeModelFile
      -> writeCsvSidecar
```

但存在两个缺陷：

1. 导入前没有确保 dbt 工作区骨架存在
2. `writeModelFile()/writeCsvSidecar()` 写失败时只打日志，不让导入失败

### 改造目标

- 导入成功必须同时满足：
  - 模型记录写入成功
  - SQL 文件写入成功
  - 若有 CSV，则 sidecar 写入成功
- 任何工作区落盘失败都应体现在返回结果中

### 预检机制

在正式导入前先预检 ZIP 内容：

- 是否存在 `models.tsv`
- 每行 `name/layer/sql_path` 是否齐全
- 对应 SQL 文件是否存在
- `layer` 是否属于 `ODS/DWD/DWS/ADS`
- `materialized` 是否属于允许值
- 是否有重复模型名

### 结果分类

批量导入结果增加失败语义细分：

- `imported`
- `skipped`
- `validation_failed`
- `write_failed`

### 事务语义

- 每个模型条目仍保持独立处理，避免一条失败阻断全部导入
- 但单条模型内部必须做到“记录 + 落盘”一致

---

## Part 3：模型产出表 relation 管理

### 现状问题

逻辑建模页里的“清空产出表 / 重建产出表”当前通过 rollback modal 走：

```text
UI -> /api/rollback/analyze|execute -> ingestion rollback
```

这条链语义错误。它面向接入回滚，不面向 dbt 模型产出 relation。

### 新语义

#### Level 1：清空产出表

- 根据当前模型解析目标 relation：
  - `database`
  - `schema`
  - `identifier`
  - `materialized`
- 若 relation 不存在：返回“当前无产出表”
- 若 relation 为 `view`：拒绝 truncate，并返回明确提示
- 若 relation 为 `table`：执行 `truncate table`

#### Level 2：重建产出表

- 解析当前模型 relation
- 若 relation 存在：
  - table -> `drop table`
  - view -> `drop view`
- 然后触发当前 selector 的 `dbt build`
- 等构建结束后同步最新：
  - `manifest`
  - `run_results`
  - 模型状态

### Relation 解析

relation 解析应统一由后端完成，基于：

- `schemaName`
- `alias`
- `name`
- `materialized`
- 工作区 `profiles.yml` 的 target database/schema

### 前端交互

前端不再打开 rollback modal，而是使用新的 dbt 产出表维护弹窗，展示：

- 当前模型名
- 目标 relation
- materialized 类型
- 风险提示
- 下游引用数量摘要（如当前已有可用数据）

---

## Part 4：测试策略

### 后端测试

- 空目录 bootstrap 自动生成最小骨架
- 已有工作区不被覆盖
- 批量导入：
  - 正常 ZIP 成功
  - 缺 SQL 文件失败
  - layer/materialized 非法失败
  - 写文件失败返回 `write_failed`
- relation 管理：
  - table 可 truncate
  - view 禁止 truncate
  - rebuild 触发 drop + dbt build

### 前端测试

- 空工作区状态展示“已自动初始化最小 dbt 项目”
- 批量导入结果页显示失败细分类型
- 清空/重建产出表不再调用 rollback API

---

## 结论

本次修复不应只停留在“让某个按钮不报错”，而是要把逻辑建模的 dbt 工作区正式补成一个最小可运行项目，并让模型导入与产出表管理都回到 dbt 原生语义。这样后续客户环境的初始化、导入、构建、维护才会稳定。
