# T04: dbt source 刷新回归测试

**优先级**: P1  
**状态**: DONE
**依赖**: T01

## 目标

保证接入任务创建、更新、删除后 dbt source 刷新稳定。

## 范围

- 新建数据库任务后刷新 source。
- 新建文件任务后刷新 source。
- 删除或禁用任务后 source 不残留错误引用。
- 执行 `dbt parse` 验证 YAML 可用。

## 完成标准

- [x] 单测覆盖 `DbtSourceService` 列级输出。
- [x] 集成验证包含 `dbt parse`。
- [x] `it/README.md` 留存 source diff。
