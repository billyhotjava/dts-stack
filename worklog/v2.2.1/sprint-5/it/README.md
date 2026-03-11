# Sprint-5 集成测试

## 目录

- `seed-ods-project-progress.sql` — ODS 测试数据（40 条，覆盖全部枚举组合）
- `validate-indicators.sql` — 35 个指标验证查询（含预期值断言）
- `screen-template-project-progress.json` — 项目进度分析大屏模板配置

## 执行步骤

### 1. 准备环境
```bash
# 确保 parse_date_safe 函数已创建（参考 worklog/s10/patent/patent-model-design.md）
# 在 dts_platform 库的 public schema 下执行
```

### 2. 导入测试数据
```bash
# 在 dts-pg 中执行
psql -h localhost -U dts_platform -d dts_platform -f worklog/v2.2.1/sprint-5/it/seed-ods-project-progress.sql
```

### 3. 运行 dbt 模型
```bash
# 在 dts-dbt 容器中执行
dbt run --select tag:project-management
```

### 4. 验证指标
```bash
psql -h localhost -U dts_platform -d dts_platform -f worklog/v2.2.1/sprint-5/it/validate-indicators.sql
```

### 5. 导入大屏
通过 analytics API 或大屏设计器 UI 导入 `screen-template-project-progress.json`。
