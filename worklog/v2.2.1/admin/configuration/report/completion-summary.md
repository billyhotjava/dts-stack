# Admin Configuration 完成摘要（v2.2.1）

## 代码改动

- `source/dts-admin`
  - `SystemConfig` 扩展: `config_scope`、`restart_required`、`validation_rule`、`owner`
  - `OpsConfigService` 增加:
    - 启动期参数编辑护栏
    - 数据类型/规则校验
    - 敏感值占位保护
    - 新元数据透出
  - `OpsConfigEnvSyncService`:
    - 应用启动后将纳管范围内 `.env` 参数缺失项写入 `system_config`（不覆盖现有值）
    - 自动推断 `category/dataType/sensitive/scope/owner`
  - `OpsConfigGroupingService`:
    - 基于 `config/ops-config-groups.yml` 提供分类内子分组能力（keys/prefixes/regex）
    - 输出 `groupKey/groupLabel/groupOrder` 给前端
  - Liquibase 新增:
    - `20260222-03_ops_config_runtime_metadata.xml`
    - `master.xml` include
- `source/dts-admin-webapp`
  - `ops-config` 页面:
    - 展示 scope/restart/validation 元数据
    - 启动期参数拦截
    - 重启确认提示
    - 分类内子分组渲染（组计数、需重启计数、敏感计数）
  - `infra-settings` 页面:
    - 保存后重启提示
    - 测试结果结构化展示（message/status/body）

## 交付物（worklog）

- 参数台账脚本: `scripts/build-env-catalog.sh`
- 漂移检测脚本: `scripts/check-config-drift.sh`
- 台账与运行文档:
  - `report/p0-01-env-catalog-latest.md`
  - `report/p0-02-secret-policy-latest.md`
  - `report/p2-02-drift-detection-runbook.md`
  - `report/p2-03-release-gate-checklist.md`

## 当前状态

- 任务清单 P0/P1/P2 已按“最小可执行版本”落地。
- 建议下一步在目标环境执行一次端到端回归并固化截图证据。
