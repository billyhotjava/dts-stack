# DTS v2.2.1 未完成任务清单（承接 v2.2.0）

## 0. 范围
- 本清单仅包含：
  - v2.2.0 未完成项（`todo/doing`）
  - 本轮 Review 新增修复项（RV）

## 1. P0 收尾（必须）

### V221-P0-001 全量语义联调闭环
- 来源：`P0-API-002 (doing)`
- 目标：确认 Excel/源库两条链路在全量模式下均执行“删表重建”。
- 验收：重复执行同任务后，目标表结构与数据仅由本次输入决定。

### V221-P0-002 模型来源非空约束完善
- 来源：`P0-DB-003 (todo)`
- 目标：彻底杜绝 `source_data_source_id` 为空写入。
- 验收：批量生成场景无空来源入库，失败可读且不中断其它项。

### V221-P0-003 P0 回归包执行
- 来源：`P0-QA-001~007 (todo)`
- 目标：完成 DAG ready、日志可读、ODS 列表刷新、前缀/scheme、一键生成联调、环境矩阵。
- 验收：形成回归报告（legacy/normal/dev × x86/ARM）。

## 2. P1 稳定性与性能

### V221-P1-001 用户管理性能优化闭环
- 来源：`P1-API-005`、`P1-UI-003`、`P1-DB-002`、`P1-QA-003`
- 目标：800~1000 用户查询稳定在目标时延。
- 验收：P95 < 1.5s，搜索/翻页无明显卡顿。

### V221-P1-002 增量审计与失败归因
- 来源：`P1-API-001~004`、`P1-DB-001`、`P1-QA-001/002`
- 目标：可筛选审计 + 标准化错误分类 + 重试策略。
- 验收：Top20 失败归类命中率 > 95%，审计查询性能达标。

### V221-P1-003 报表密级与权限回归
- 来源：`P1-API-006`、`P1-UI-004`、`P1-DB-003`、`P1-QA-004`
- 目标：密级可见性与角色权限一致。
- 验收：无越权显示/访问。

## 3. P2 产品化闭环

### V221-P2-001 项目包导入（一个 ZIP 一个项目）
- 来源：`P2-API-004 (doing)`、`P2-UI-004 (doing)`、`P2-QA-003 (todo)`
- 目标：导入向导 + 冲突检测 + 幂等 + 审计。
- 验收：同包重复导入无脏数据，冲突提示可读。

### V221-P2-002 外部 LLM 产物导入兼容
- 来源：`P2-API-005 (doing)`
- 目标：支持分层模型与指标定义导入，适配离线交付。
- 验收：导入报告含成功/跳过/失败明细，能进入平台治理流。

### V221-P2-003 数据集管理页闭环
- 来源：`P2-UI-002 (doing)`、`P2-QA-001/002/004 (todo)`
- 目标：可视化管理数据集版本、发布与看板依赖。
- 验收：完成“查询 -> 数据集 -> 看板”端到端演示。

## 4. Review 新增修复项（RV）

### V221-RV-001 QueryDataset 部门匹配归一化
- 来源：`RV-001`
- 目标：修复 owner_dept 精确匹配导致的误不可见。
- 涉及文件：
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/QueryDatasetService.java`
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/explore/QueryDatasetAssetRepository.java`
- 验收：部门编码变体下可见性稳定。

### V221-RV-002 维护者角色权限矩阵对齐
- 来源：`RV-002`
- 目标：确认并固化 `ADMIN/OP_ADMIN/INST_DATA_OWNER/DEPT_DATA_OWNER` 的 QueryDataset/BI Link 权限边界。
- 涉及文件：
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/QueryDatasetService.java`
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/visualization/BiReportLinkService.java`
- 验收：角色回归用例全部通过。

### V221-RV-003 P3 验证证据落盘
- 来源：`RV-003`
- 目标：将“已完成的 P3 能力”转化为可审计的测试证据。
- 交付：
  - 24h 稳定性执行记录
  - Addax/Airbyte 语义对照报告
  - 跨项目隔离与血缘影响分析回归报告

## 5. 里程碑建议
- M1（1周）：完成 V221-P0-001/002/003 + V221-RV-001。
- M2（1~2周）：完成 V221-P1-001/002/003 + V221-RV-002。
- M3（2周）：完成 V221-P2-001/002/003 + V221-RV-003。

