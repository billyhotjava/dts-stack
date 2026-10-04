# P3-03 开发中心运营指标体系

- 优先级：P3
- 状态：done

## 范围

- 建立“开发效率 + 运行质量 + 发布稳定性”指标闭环。

## 子任务

- 定义并采集关键指标：
  - 开发效率：模型交付周期、脚本交付周期。
  - 运行质量：失败率、重试率、MTTR。
  - 发布稳定性：发布成功率、回滚率。
- 打通审计日志与运行日志的指标汇总。
- 在平台提供运营看板。

## 验收标准

- 可按日/周查看核心指标趋势。
- 指标支持按项目空间、部门过滤。

## 风险与回滚

- 风险：日志口径不一致导致统计偏差。
- 回滚：先以统一口径落地最小指标集，再逐步扩展。

## 当前进展（2026-02-17）

- 已完成后端聚合接口：
  - `GET /api/ops/metrics/dev-center`
  - 支持过滤：`days`、`entryKey`、`ownerDept`、`artifactId`、`artifactName`
  - 输出：`summary`、`trend`、`topFailures`、`availableOwnerDepts`
- 已完成前端概览接入（任务运行概览页）：
  - 失败率、重试率、MTTR、发布成功率指标卡
  - 近 7/14/30 天趋势图
  - 失败作业 Top10 表
  - 过滤项：入口类型、部门、项目空间（枚举）
- 已补“项目空间枚举 + 结构化回滚口径”：
  - 指标接口返回 `availablePlans`（项目空间 ID/名称/状态/runCount）
  - 回滚识别优先使用结构化字段（`conf.rollback`、`conf.operation=rollback|revert|restore`），关键词仅作为兜底
  - 汇总新增：`rollbackStructuredRuns`、`rollbackKeywordRuns`

## 后续优化

- `planId` 在现有运行日志中的覆盖率仍依赖上游写入，建议后续在触发链路统一补齐。
- 可补充按项目空间的导出报表与告警阈值配置。

## 本轮回归

- `mvn -f source/dts-platform/pom.xml -DskipTests compile`
- `pnpm -C source/dts-platform-webapp build`
