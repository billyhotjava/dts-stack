# Sprint-3 集成测试

## 测试场景

### IT-001: Level 1 — ODS 表 TRUNCATE

**前置**：已有采集任务且已执行成功，ODS 表有数据
**步骤**：
1. 调用 `POST /api/rollback/analyze` 传入 `level=1, scope=task, taskId=X`
2. 验证响应包含 affectedTables 列表
3. 调用 `POST /api/rollback/execute` 执行清空
4. 查询 ODS 表验证 `SELECT COUNT(*) = 0`
5. 验证审计日志已写入
6. 重跑采集任务，验证数据恢复

### IT-002: Level 1 — dbt 产出表 TRUNCATE

**前置**：ODS 数据已就绪，dbt run 已产出 DWD 表
**步骤**：
1. 调用 `POST /api/rollback/analyze` 传入 `level=1, tables=[schema.dwd_xxx]`
2. 调用 `POST /api/rollback/execute` 执行清空
3. 验证 DWD 表为空
4. 执行 `dbt run` 重建数据

### IT-003: Level 2 — ODS 表 DROP + 重建

**前置**：采集任务已建表，发现字段错误
**步骤**：
1. 调用 `POST /api/rollback/analyze` 传入 `level=2, scope=task, taskId=X`
2. 验证响应包含将被 DROP 的表列表
3. 调用 `POST /api/rollback/execute`
4. 验证 ODS 表已被 DROP
5. 修改任务列映射
6. 重跑采集，验证新表结构正确

### IT-004: Level 2 — dbt --full-refresh

**前置**：DWD/DWS 模型已有产出表
**步骤**：
1. 调用回退 API，level=2，附带 `rebuildDbt=true`
2. 验证触发 `dbt run --full-refresh`
3. 验证产出表结构已更新

### IT-005: Level 3 — 任务级全链路回退

**前置**：完整链路已跑通（采集 → ODS → 模型 → dbt run → 产出表 → 资产数据集）
**步骤**：
1. 调用 `POST /api/rollback/analyze` 传入 `level=3, scope=task, taskId=X`
2. 验证影响分析包含所有产物
3. 调用 `POST /api/rollback/execute`
4. 验证：
   - ODS 物理表已 DROP
   - ODS 映射记录已删除
   - SQL 模型定义已删除
   - dbt 文件已删除
   - DWD/DWS/ADS 产出表已 DROP
   - 资产数据集已删除
   - 采集任务 status=deleted
   - 执行记录已清除
   - 审计日志已写入

### IT-006: Level 3 — 数据源级全链路回退

**前置**：同一数据源下有 3 个采集任务，各自有 ODS + 模型 + 产出
**步骤**：
1. 调用 `POST /api/rollback/analyze` 传入 `level=3, scope=datasource, dataSourceId=X`
2. 验证影响分析列出所有 3 个任务的产物
3. 调用 `POST /api/rollback/execute`
4. 验证所有 3 个任务的全部产物均已清除

### IT-007: Excel 导入场景回退

**前置**：通过 Excel 上传创建采集任务并执行成功
**步骤**：
1. Level 1: TRUNCATE ODS 表 → 验证数据清空 → 重新上传重跑 → 数据恢复
2. Level 2: DROP ODS 表 → 修改列映射 → 重新上传文件 → 重跑 → 验证新结构
3. Level 3: 全链路回退 → 验证上传文件也被清理

### IT-008: 审计日志完整性

**步骤**：
1. 执行 Level 1/2/3 各一次回退操作
2. 查询审计日志 API
3. 验证每次操作均有对应记录，包含 operator/timestamp/level/scope/impact/result
