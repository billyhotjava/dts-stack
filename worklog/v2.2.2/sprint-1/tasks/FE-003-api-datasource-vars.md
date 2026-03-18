# FE-003: API 数据源全局变量透传

- **优先级**: P1
- **状态**: TODO
- **负责人**: TBD

## 问题

大屏模板全局筛选变量（如项目群、时间范围）无法传递给 API 类型数据源。

## 修复方向

`useCardDataSource` 中对 API URL/body 做变量插值。
