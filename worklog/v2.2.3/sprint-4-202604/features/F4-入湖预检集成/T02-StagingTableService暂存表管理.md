# T02: StagingTableService — 暂存表管理

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
管理 Excel 预检数据的 PG 暂存表，提供创建/写入/读取/编辑/清理全生命周期。

## 技术设计

### 暂存表结构
```sql
CREATE TABLE tmp_ingestion_{taskId} (
    _row_num    SERIAL,          -- 对应 Excel 原始行号
    _errors     JSONB,           -- [{"col":"phone","rule":"非空","msg":"..."}]
    _status     VARCHAR(10),     -- CLEAN / ERROR / FIXED
    col_1       TEXT,            -- 全部 TEXT 存储
    col_2       TEXT,
    ...
);
```

**为什么全部 TEXT**：Excel 数据本身混乱，类型校验交给质量规则，避免写入阶段类型转换报错。

### 生命周期
```
创建 → 上传解析完成时
清理 →
  ├─ 正常：提交入湖成功后立即 DROP
  ├─ 超时：24h 无操作，定时任务清理
  └─ 取消：用户主动放弃时 DROP
```

### API
```java
StagingTableService {
    String create(String taskId, List<ColumnInfo> columns);
    void bulkInsert(String taskId, List<Map<String, String>> rows);
    Page<Map<String, Object>> query(String taskId, boolean errorsOnly, Pageable page);
    void updateCell(String taskId, int rowNum, String column, String value);
    void updateErrors(String taskId, int rowNum, List<CellError> errors);
    void drop(String taskId);
}
```

### 清理定时任务
每小时扫描 `information_schema.tables`，匹配 `tmp_ingestion_%`，检查修改时间 >24h 的表执行 DROP。

## 影响范围
- 新增 `StagingTableService.java` 在 dts-ingestion 模块
- PG：动态 DDL（CREATE TABLE / DROP TABLE）
- 需注意表名注入防护：taskId 必须为 UUID 格式

## 验证
- [ ] 创建暂存表，列名正确
- [ ] 批量写入 1000 行 <2s
- [ ] 分页查询 + 仅错误行过滤
- [ ] 单格编辑后 _status 更新
- [ ] 24h 超时自动清理

## 完成标准
- [ ] 暂存表 CRUD 全生命周期可用
- [ ] taskId 白名单校验防注入
