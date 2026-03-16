# BE-005

## 标题

新增模型产出 relation 管理后端，按 dbt 模型语义维护 table/view 产出。

## 范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/`

## 目标

- 统一解析当前模型 relation
- 提供 truncate/drop/rebuild 所需后端能力

## 交付

- `SqlModelOutputService`
- `SqlModelOutputResource`
- 后端测试

## 验收

- table 可 truncate
- view 不允许 truncate
- relation 不存在时返回可理解结果

## 当前进度

- 状态：TODO

## 风险

- relation 解析若与 dbt 最终命名不一致，会误操作错误对象
