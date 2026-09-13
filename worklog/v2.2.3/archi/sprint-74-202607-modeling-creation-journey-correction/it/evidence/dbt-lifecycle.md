# dbt 与生命周期证据

**日期**：2026-07-27  
**结果**：PASS

## 1. 真实 dbt 项目

项目位于 `services/dts-dbt/sprint74_acceptance/`：

- `dbt_project.yml`
- `models/ads_s74_finance_dashboard.sql`
- `models/schema.yml`

容器内执行：

```bash
dbt compile \
  --project-dir /opt/dbt/sprint74_acceptance \
  --profiles-dir /root/.dbt \
  --select ads_s74_finance_dashboard \
  --no-use-colors
```

结果：

- dbt 1.11.3；
- postgres adapter 1.10.0；
- compile PASS；
- canonical unique id：`model.sprint74_acceptance.ads_s74_finance_dashboard`。

## 2. 生命周期

- ModelSpec：`42fe5aa7-7d89-46fd-88b9-4baae9b314a4` r4；
- ModelImplementation：`2b8180df-07b8-4a01-b32c-0238068e686e` r1；
- compile event：`9661cf57-95aa-42d0-95d4-3ef3eb38afdf`；
- status：`COMPILE / PASSED`；
- artifacts：SQL + SCHEMA 共 2 个，均为 COMPILED；
- event 中保存当前 model 与 implementation 精确 revision/checksum。

## 3. 发布边界

该样本仍被真实标准、质量与密级证据阻断，未伪造 PUBLISHED 或 physicalAssetRef。发布结果页因此：

- 展示真实 COMPILE/PASSED 时间线；
- 不展示虚构 table/view/DDL；
- 不提供高级 dbt 编辑入口；
- 提示完成测试、治理和发布后才显示真实物理资产。

这是 Sprint-74 对“数据实现是生成方式、发布结果是已产生事实”的核心验收。
