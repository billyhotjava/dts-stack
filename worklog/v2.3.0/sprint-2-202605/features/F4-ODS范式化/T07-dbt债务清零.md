# T07: dbt 12 项遗留债务清零

**优先级**: P1
**状态**: READY
**依赖**: T06

## 目标
清掉 `~/.claude/projects/-opt-prod-s10-s10-stack/memory/feedback_dbt_pjm_v3_pending.md` 列出的 12 项（risk_level 别名归一 / quality_category 别名 / progress_monthly outside_completed / 主键粒度 / 分母口径 / composite derived / open_issue 语义 / typed 静默丢数据 / 精度不一致 / ADS quality kpi 漏字段 / etl_time 幂等 / dim_change_category 漏测试）。

## 技术设计
详细技术方案在 F4 brainstorming 阶段产出，本文件仅占位。

## 影响范围
- 多个 dim/dws/ads 模型
- schema.yml 测试补齐

## 验证
- [ ] 每一项债务有对应 PR / commit 指向；memory 文件清单清空

## 完成标准
- [ ] 12 项债务全部关闭，memory 文件删除或更新为"已清零"
