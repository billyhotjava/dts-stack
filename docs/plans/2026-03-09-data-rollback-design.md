# 三级数据回退机制设计

## 背景

数据源接入后，ODS 表字段可能写错、采集数据可能有误、dbt 计算结果可能不正确。
当前系统缺乏统一的回退/清理手段，只能手动操作数据库。

## 数据流全景

```
Excel/CSV 上传 ─┐
                ├→ [采集任务 IngestionTask] → Addax Job → ODS 物理表 (PostgreSQL)
数据库连接 ─────┘                                              │
                                                               ├→ ODS 映射 (infra_ods_table_mapping)
                                                               ├→ 资产数据集 (catalog_dataset)
                                                               │
                                              [generateFromOds] → SQL 模型定义 + dbt 文件
                                                                        │
                                                                   [dbt run] → DWD/DWS/ADS 物理表
```

Excel 导入特殊性：`sourceType=excel/csv`，走 FileUploadService → Addax excelreader/txtfilereader，
文件源无法走 ensureTargetTables，靠 Addax preSql 注入 CREATE TABLE。

## 三级回退定义

### Level 1 — 清空数据（TRUNCATE）

- **场景**：数据脏了，表结构没问题
- **粒度**：按表操作，可多选
- **操作**：`TRUNCATE TABLE schema.table`
- **覆盖**：ODS 表 / DWD/DWS/ADS 产出表均可选
- **Excel 场景**：清空后需重新上传文件重跑

### Level 2 — 重建表结构（DROP + CREATE）

- **场景**：ODS 字段名写错、类型不对
- **粒度**：按采集任务操作
- **操作**：DROP 目标表 → 将任务 syncMode 临时切 full_refresh → 重跑采集
- **Excel 场景**：DROP 表 → 用户修正列映射 → 重新上传文件重跑
- **dbt 产出表**：提供 `dbt run --full-refresh` 选项一并重建下游

### Level 3 — 全链路回退

**任务级回退**：
1. 软删除采集任务（status → deleted）
2. DROP ODS 物理表
3. 删除 ODS 映射记录 (infra_ods_table_mapping)
4. 删除关联 SQL 模型定义 + dbt 文件
5. DROP DWD/DWS/ADS 产出表
6. 删除关联资产数据集 (catalog_dataset)
7. 清理上传文件（Excel/CSV 场景）
8. 清理执行记录、增量 checkpoint、Addax Job、DAG

**数据源级回退**：找到该数据源下所有采集任务，逐个执行任务级回退。

## 架构设计

### 后端 — dts-ingestion

```
DataRollbackService              -- 核心编排
  ├─ analyzeImpact(request)      -- dryRun 影响分析
  ├─ executeRollback(request)    -- 实际执行
  ├─ ConfirmationPolicy          -- 可插拔确认策略接口
  └─ RollbackAuditService        -- 审计记录

TableOperationService            -- 物理表 DDL 操作
  ├─ truncateTable(schema, table)
  ├─ dropTable(schema, table)
  └─ listTablesForTask(taskId)
```

### 后端 — dts-platform

```
RollbackProxyResource            -- 代理 API（转发到 ingestion）
  ├─ POST /api/rollback/analyze  -- 影响分析
  ├─ POST /api/rollback/execute  -- 执行回退
  └─ 级联清理 SQL 模型 + dbt 文件 + 资产数据集
```

### 前端入口

- **采集任务详情页**：任务级操作按钮（Level 1/2/3）
- **数据源管理页面**：数据源级 Level 3 入口
- **建模页面**：dbt 产出表 Level 1（清空）+ Level 2（full-refresh 重建）

### 影响分析（dryRun）

每次回退前先返回影响清单：
```json
{
  "level": 1,
  "scope": "task",
  "taskId": 42,
  "affectedTables": ["public.ods_order"],
  "affectedModels": ["dwd_order_detail", "dws_order_summary"],
  "affectedDatasets": 2,
  "affectedDbtFiles": ["models/dwd/dwd_order_detail.sql"],
  "uploadFiles": ["uploads/abc_order.xlsx"],
  "executionRecords": 15
}
```

### 可插拔确认策略

```java
public interface ConfirmationPolicy {
    boolean requiresConfirmation(RollbackLevel level);
    String confirmationType(); // MODAL | INPUT_NAME | ADMIN_APPROVE
}
```

当前阶段实现 `ModalConfirmationPolicy`（前端 Modal.confirm）。
未来权限/角色体系完善后，替换为 `InputNameConfirmationPolicy` 或 `AdminApprovePolicy`。
ConfirmationPolicy 由 Spring Bean 注入，切换实现无需改业务代码。

### 审计日志

所有回退操作写入 `rollback_audit_log` 表：
- operator, timestamp, level, scope, request_json, impact_json, result

## 安全约束

- Level 3 数据源级回退执行前强制 dryRun
- 所有 DROP/TRUNCATE 操作记录审计日志
- 确认策略接口预留 ADMIN_APPROVE 扩展点
- 物理表操作走 JDBC 直连目标库，不走应用数据源
