# WE-013

## 标题

落地 Sprint-2 README、IT 说明与任务卡。

## 范围

- `worklog/v2.2.1/sprint-2/README.md`
- `worklog/v2.2.1/sprint-2/it/README.md`
- `worklog/v2.2.1/sprint-2/tasks/`

## 目标

- 用 `sprint-1` 风格承载本轮 Web E2E 回迁计划与执行状态
- 把迁移工作拆成可跟踪、可验收的任务卡

## 交付

- Sprint README
- IT 说明
- 细粒度任务卡

## 验收

- `sed -n '1,220p' worklog/v2.2.1/sprint-2/README.md`
- `find worklog/v2.2.1/sprint-2/tasks -maxdepth 1 -type f | sort`

## 当前进度

- 进行中：
  - Sprint README
  - IT 说明
  - 任务卡

## 风险

- 如果 README 状态未与实际验证同步，worklog 会很快失真
