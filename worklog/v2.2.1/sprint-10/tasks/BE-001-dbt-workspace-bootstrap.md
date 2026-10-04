# BE-001

## 标题

统一实现最小 dbt 工作区 bootstrap，让空目录 `services/dts-dbt` 自动变成可运行项目。

## 范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/`
- `services/dts-dbt/`

## 目标

- 自动生成 `dbt_project.yml`、models/macros/target/logs 等最小骨架
- 保持幂等，不覆盖已有用户文件

## 交付

- bootstrap 实现
- 工作区骨架模板
- 后端单元测试

## 验收

- 空目录下首次加载配置可自动补齐骨架
- 二次执行不会覆盖已有文件

## 当前进度

- 状态：TODO

## 风险

- 若 `dbt_project.yml` 生成规则过于强势，可能覆盖客户已有定制
