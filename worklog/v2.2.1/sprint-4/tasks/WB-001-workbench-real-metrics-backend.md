# WB-001: WorkbenchService 输出真实趋势与角色化摘要

## 范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/workbench/WorkbenchService.java`
- 相关 repository / dto / test

## 目标

- 将首页数据从“两个计数 + 待办拼装”提升为真实趋势与角色相关摘要

## 交付

- 历史趋势序列
- 角色化摘要卡片数据
- 更完整的待办聚合口径

## 验收

- 后端直接返回趋势序列，前端无需再模拟 7 天数据
- 首页摘要能表达用户角色或部门上下文
- 现有待办能力不回退

## 当前进度

- 状态：TODO
- 备注：一期可先支持常见角色，不要求完整个性化推荐
