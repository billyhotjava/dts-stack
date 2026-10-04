# BE-001: dbt 模型上线链路修复

- **优先级**: P0
- **状态**: TODO
- **负责人**: TBD

## 问题描述

UI 导入 `project-management-ui-import.zip` → 点"上线" → 数仓表未生成。
手动执行 `dbt seed + dbt run --full-refresh` 可以成功。

## 排查方向

1. **DAG 命令差异**
   - UI 上线: `dbt build --select +tag:project-management --vars {...}`
   - 手动: `dbt run --full-refresh`（无 selector、无 vars）
   - 可能 `--vars` 中的 source 配置覆盖了 `pm_ods_sources.yml`

2. **Source 冲突**
   - 平台会自动生成 `__topic_bindings/topic_sources.yml` 和 `ods_sources.yml`
   - zip 包自带 `pm_ods_sources.yml`
   - 三个 source yml 是否存在同名 source 冲突

3. **残留文件**
   - 旧 seed CSV 是否还在 `seeds/` 目录
   - 是否有旧模型文件引用已删除的 seed

## 排查命令（远程执行）

```bash
# 1. 看最新 dbt build 日志
tail -100 services/dts-dbt/logs/dbt.log

# 2. 看所有 source yml
find services/dts-dbt/models -name "*.yml" -exec echo "=== {} ===" \; -exec cat {} \;

# 3. 看残留 seed
ls services/dts-dbt/seeds/

# 4. 看 DAG 传的实际命令
cat services/dts-airflow/dags/dwh/dwh_project_management_dbt_manual.py | head -60
```

## 交付标准

- [ ] 全新环境：导入 zip → 点上线 → 21 张表全部生成
- [ ] 项目看板显示正确的重大项目数和子项目数
