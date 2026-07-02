# Sprint-57: 基础数据闭环 —— 标准包导入管道与内置国标包

**时间**: 2026-07
**状态**: IN_PROGRESS
**目标**: 打通"下载标准包模板 → 客户填写 → 上传 DTS → 校验/应用/回滚"的完整闭环，并以同一管道交付内置国标包，使基础数据模块（数据元/业务术语/公共码表/标准包）达到可交付状态。

## 背景

菜单重构后，"数据基础"模块（主题域管理/标准管理/标准模板）已独立成黄金线的地基层。现状盘点：

| 环节 | 现状 | 断点 |
|------|------|------|
| 模板下载 | ✅ `GET /api/modeling/metadata-standards/template` 已产出 `data-standard-package-template.zip`（5 CSV + README），ElementsPage 有下载按钮 | 无 |
| 码表导入 | ✅ 已有 preview/apply/rollback/runs 完整范式（`GovernanceReferenceCodeResource` 249-340 行） | 只能按单个码表目录逐个导 |
| 数据元导入 | ⚠️ `POST /api/modeling/metadata-standards/import` 单 CSV 直接入库 | 无 preview、无回滚、校验逻辑独立一套 |
| 术语导入 | ❌ 不存在（`ModelingAuxResource /api/modeling/glossary/terms` 仅单条 CRUD） | 完全缺失 |
| **包级上传** | ❌ 不存在 | **闭环断点：客户填好的 zip 没有入口整包吃进去** |

**用户决策（2026-07-02）**：
1. "标准模板" = 数据标准包（一组数据元+码表+术语的打包），不是 TemplatesPage 的建模模板。
2. 收编旧的数据元直导路径到新管道，避免两套校验逻辑漂移。
3. 内置国标包本期一起做，作为管道的第一个消费者。

## 核心设计

**把码表已有的 preview/apply/rollback 范式提升到"包"级别，一条管道两个来源（客户上传 zip / 官方内置包）。**

```
POST /api/modeling/standard-packages/import/preview   (multipart zip)
  解压 → 解析 5 CSV → 跨文件引用校验 → 校验报告（新增/更新/冲突/错误行）

POST /api/modeling/standard-packages/import/apply     (基于 preview runId 确认)
  单事务按依赖序入库：术语 → 码表目录 → 码表值 → 数据元 → 映射

POST /api/modeling/standard-packages/runs/{runId}/rollback   (整包回滚)
GET  /api/modeling/standard-packages/runs                    (导入历史)
GET  /api/modeling/standard-packages/builtin                 (内置包列表)
POST /api/modeling/standard-packages/builtin/{code}/install  (安装=走同一 preview/apply)
```

### 包内 CSV 契约（沿用既有模板，不破坏 source-contract）

| 文件 | 表头（已由 DataStandardPackageTemplate.source-contract.test.ts 锁定） | 目标实体 |
|------|------|------|
| 01-business-terms.csv | term_code,term_name,aliases,definition,domain,owner_dept,owner,tags,version,status,version_notes | ModelingGlossaryTerm |
| 02-data-elements.csv | field_name_cn,field_name_en,data_type,data_length,data_precision,data_scale,nullable,domain,description,source_system,code_set,default_value,is_pk,security_level | MetadataStandard |
| 03-reference-code-directories.csv | code_type_id,code_type_code,code_type_name,std_level,biz_catalog,data_type,status,owner_dept,version | StdCodeDirectory |
| 04-reference-code-items.csv | code_type_code,code_value,code_name,description,sort_num,parent_code,is_default | StdCodeValue |
| 05-reference-code-mappings.csv | code_type_code,source_system,source_code,standard_code | 码表映射 |

### 跨文件校验规则（preview 阶段）

1. 02 数据元的 `code_set` 非空时，必须能在 03（本包）或库中已有码表目录解析。
2. 04/05 的 `code_type_code` 必须在 03（本包）或库中存在。
3. 04 的 `parent_code` 必须指向同目录内已有/本包内的 code_value。
4. `term_code`/`field_name_en`/`code_type_code` 与库中重复 → 判定为"更新"并给出 diff 摘要，不算错误。
5. 类型/长度/security_level 校验复用 `MetadataStandardImportService` 现有规则（ALLOWED_TYPES、TYPE_WITH_SIZE、SecurityLevelCatalog）。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 标准包导入管道 | 4 | READY |
| F2 | 内置国标包 | 2 | READY |
| F3 | 基础数据页面完善 | 3 | READY |

**范围外**：数据资产工业级打磨（原任务 2）单列 Sprint-58 规划，不混入本 sprint。

## 全局约束

- Chrome 95 兼容：禁 oklch()/:has()/@container/subgrid。
- 分页约定：CompactTable，默认 10 条/页，切换条数刷新并回第 1 页。
- 不新增 `/v2` 路由命名空间；`/modeling/semantic-center` iframe 保持不动。
- 不触碰 `services/dts-airflow/dags/addax-env-runner.jar`。
- 既有 source-contract 测试（模板 zip 结构、下载按钮）不得破坏。
- Java：`Optional.orElseThrow()` 而非 `get()`（modernizer 强制）。
- 先补/改 source-contract 测试，再做实现（沿用 Sprint-51 以来的工作方式）。

## 完成标准

- [ ] 客户可下载模板 zip、填写后整包上传，看到逐行校验报告，确认后一次入库
- [ ] 导入历史可查、单次导入可整包回滚
- [ ] 旧数据元直导端点收编到新管道校验逻辑，行为兼容
- [ ] 至少 3 个内置国标包（性别/学历/行政区划节选 + 常用数据元）可在页面一键安装
- [ ] 基础数据四页面交互范式统一（列表+详情+引用追溯+导入导出入口）
- [ ] it/ 留存集成验证证据（curl 报文 + 页面截图）
