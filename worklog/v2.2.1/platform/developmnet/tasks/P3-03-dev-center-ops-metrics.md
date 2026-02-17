# P3-03 开发中心运营指标体系

- 优先级：P3
- 状态：in-progress

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
  - 过滤项：入口类型、部门、项目空间/作业关键词

## 待完成

- 将“项目空间”从当前关键词过滤升级为真实项目空间枚举（名称/ID 对照）。
- 增加发布回滚识别的结构化口径（当前基于日志关键词兜底）。

## 本轮回归

- `mvn -f source/dts-platform/pom.xml -DskipTests compile`
- `pnpm -C source/dts-platform-webapp build`
