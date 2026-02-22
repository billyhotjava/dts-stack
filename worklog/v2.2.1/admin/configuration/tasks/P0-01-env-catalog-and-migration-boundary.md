# P0-01 参数目录与迁移边界

`status`: `done`  
`priority`: `P0`

## 目标

形成 `.env` 参数台账，明确哪些参数进入 `dts-admin` 可视化，哪些继续留在运维层。

## 范围

- `.env` 参数分组与分类标准
- 迁移白名单（可视化管理）
- 保留白名单（仅 `.env` / compose）

## 子任务

1. 生成参数清单（key、当前值来源、敏感级别、生效方式）。
2. 标注 `runtime-editable` / `restart-required` / `bootstrap-only`。
3. 给出第一批迁移名单（Airflow/OpenMetadata/Addax/安全开关/MDM）。
4. 评审并冻结 v2.2.1 迁移范围。

## 验收标准

- 100% `.env` key 被分类，且有唯一归属。
- 迁移名单与保留名单可直接用于开发实施。

## 风险与回滚

- 风险：分类不清导致线上行为漂移。
- 回滚：以 `.env` 为权威源，暂停 DB 覆盖。

## 实现进展（2026-02-22）

- 新增脚本：`worklog/v2.2.1/admin/configuration/scripts/build-env-catalog.sh`
- 新增台账：`worklog/v2.2.1/admin/configuration/raw/env-catalog.csv`
- 新增报告：`worklog/v2.2.1/admin/configuration/report/p0-01-env-catalog-latest.md`
