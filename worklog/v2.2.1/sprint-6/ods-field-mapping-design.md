# Excel 入湖关联 ODS 表自动映射字段设计

日期：2026-03-12
Sprint：v2.2.1 / sprint-6

## 背景

通过"数据入湖配置"上传 Excel 时，`SqlFieldNameResolver` 对中文表头自动生成的字段名是拼音或 `field_N` 占位符（如"分系统/分任务"→`field_2`），与 ODS 表定义的标准字段名（如 `subsystem`）不一致。29 个字段需要手动逐个修改，非常低效。

## 核心约束

- **ODS 表字段是基准**：每个 ODS 字段都必须有对应的 Excel 列
- **缺失映射必须醒目提示**：ODS 字段缺少数据源会导致下游 DWD → DWS → ADS 全链路数据不正确
- **数据源入湖流程不变**：仅改动 Excel 入湖路径

## 设计

### 1. 整体流程

```
上传 Excel → 解析列 → [选择关联 ODS 表] → 自动按位置匹配字段名
                              ↓
                    查询数据湖 information_schema
                    (复用 SqlMetadataService)
                              ↓
                    ODS 字段名覆盖 Excel 自动生成的字段名
                              ↓
                    ⚠️ 未匹配的 ODS 字段红色警告
```

### 2. 后端（零改动）

复用已有接口：

| 接口 | 用途 |
|------|------|
| `GET /api/sql/tables/{datasourceId}` | 列出数据湖中的表（前端过滤 ods_ 前缀） |
| `GET /api/sql/columns/{datasourceId}?schema=&table=` | 获取 ODS 表列定义 |

入湖向导第一步已确认数据湖 datasourceId，字段编辑步骤可直接使用。

`SqlMetadataService.listColumns()` 通过 JDBC `DatabaseMetaData` API 返回列名、类型、序号。

### 3. 前端改动

改动位置：`TransformCreatePage.tsx` 字段编辑步骤

**新增 UI 元素：**

字段编辑表格上方新增"关联 ODS 表"区域：
- ODS 表下拉选择（调用 tables 接口，过滤 ods_ 前缀）
- "自动匹配"按钮

**匹配逻辑（纯前端）：**

1. 用户选择 ODS 表 → 调用 `/api/sql/columns/{datasourceId}` 获取列列表
2. 按 `ordinal_position` 排序
3. 按位置对齐：Excel 第 i 列的字段名替换为 ODS 第 i 列的 column_name
4. 匹配状态标记：
   - ✅ 已匹配 — 正常显示
   - ❌ ODS 有但 Excel 无 — 红色警告行，提示"ODS 字段缺少数据源"
   - 灰色 — Excel 多余列，保留自动生成名
5. 用户仍可手动修改任何字段名

**异常处理：**

- ODS 列数 > Excel 列数 → 底部追加红色警告行
- Excel 列数 > ODS 列数 → 多余列灰色标注"未关联"
- 未选择 ODS 表 → 保持现有行为不变

### 4. 改动范围

| 层 | 改动 | 文件 |
|---|---|---|
| 后端 | 零改动 | — |
| 前端 | 字段编辑区新增 ODS 表关联 | `TransformCreatePage.tsx` |
| 前端 | 新增 API 调用 | `dataSourcesService.ts` 或现有 sql API |

仅限 Excel 入湖路径，数据源入湖流程完全不变。

### 5. 字典扩展（第二优先级）

完成 ODS 关联后，补充 `SqlFieldNameResolver.EXACT_TRANSLATIONS`：

```java
// 项目进度 Excel 29 字段
"项目编号"           → "project_no"
"分系统/分任务"       → "subsystem"
"节点任务及目标"      → "node_task"
"节点计划时间"        → "plan_date"
"节点计划周数"        → "plan_week"
"节点类型"           → "node_type"
"负责人"             → "owner"
"责任科室"           → "dept"
"分管室领导"          → "dept_leader"
"完成情况"           → "completion_status"
"协同部门"           → "collab_dept"
"责任监管部门"        → "supervisor_dept"
"延期预计完成时间"    → "delay_expected_date"
"未完成原因及当前进展" → "incomplete_reason"
"风险等级"           → "risk_level"
"主要风险内容及措施"   → "risk_content"
"延期影响分析"        → "delay_impact"
"实际完成时间"        → "actual_date"
"实际完成周数"        → "actual_week"
"所领导"             → "institute_leader"
"来源"               → "source"
"延期项目原计划时间"   → "original_plan_date"
"计划延误时间（已变更）" → "delay_days_changed"
"计划延误时间（未变更）" → "delay_days_unchanged"
"是否提交延期申请"     → "delay_applied"
"项目主管"            → "project_manager"
"最后更新时间"        → "last_update_time"
"填写人"             → "filled_by"
"亮点工作"           → "highlight"
```

使上传后即使不关联 ODS 表，字段名也能自动正确。
