# P1-04 配置分组细化与环境参数自动纳管

`status`: `done`  
`priority`: `P1`

## 目标

解决运维配置页面“分类下配置数量过少、分组粒度不清”的问题：

- 将 `.env` 中纳管范围内的参数自动同步到 `system_config`（仅插入缺失项，不覆盖现有值）。
- 在页面内按“分类 + 子分组”展示，提升可读性和运维定位效率。

## 范围

- 后端新增启动期同步服务：`OpsConfigEnvSyncService`。
- 后端新增分组规则服务：`OpsConfigGroupingService` + YAML 规则文件。
- 扩展 API 视图字段：`groupKey/groupLabel/groupOrder`。
- 前端 `ops-config` 页面改为分组渲染，展示每组数量、重启项数量、敏感项数量。

## 子任务

1. 定义环境变量纳管白名单（Airflow/OpenMetadata/MDM/PKI/OIDC 等）。
2. 实现“缺失即插入”的同步机制，自动推断数据类型、敏感标记、scope、owner。
3. 定义可配置分组规则（keys/prefixes/regex），并按 `groupOrder` 排序。
4. 页面展示子分组并补充元信息（需重启、敏感、校验规则）。

## 验收标准

- `ops-config` 页面不再只有少量固定项，能看到纳管范围内的环境参数。
- 同一分类下按业务语义分组展示（如 Airflow、OpenMetadata、PKI、访问控制等）。
- 参数更新行为与既有护栏一致（启动期只读、敏感占位保护、校验规则生效）。

## 风险与回滚

- 风险：白名单配置过宽可能引入噪声参数。
- 风险：分组规则不准确会导致“未分组”过多。
- 回滚：可直接回滚 `OpsConfigEnvSyncService` 与分组规则配置文件；不影响既有 `system_config` 基础功能。

## 实现进展（2026-02-22）

- 已新增：
  - `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/ops/OpsConfigEnvSyncService.java`
  - `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/ops/OpsConfigGroupingService.java`
  - `source/dts-admin/src/main/resources/config/ops-config-groups.yml`
- 已扩展：
  - `OpsConfigView`（新增分组字段）
  - `OpsConfigService`（按分组顺序返回）
  - `source/dts-admin-webapp/src/admin/views/ops-config.tsx`（分类内子分组渲染）
