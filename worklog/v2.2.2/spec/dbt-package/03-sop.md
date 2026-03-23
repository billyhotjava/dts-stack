# dbt Package SOP

## 适用范围

本 SOP 适用于：

- 现场不能安装 AI
- dbt 模型由总部离线优化后交付
- 现场通过 `services/dts-dbt` 执行 dbt

## 角色分工

- 总部研发：出包、验包、提供版本与校验值
- 现场实施：安装包、运行 dbt、核对结果
- 平台管理员：按需执行 register、维护项目空间

## 首次安装

1. 备份当前 `services/dts-dbt`
2. 执行 package `install`
3. 执行 `status` 确认安装版本
4. 执行 package `run`
5. 核对目标表是否生成
6. 记录安装批次、版本、执行时间

## 升级

1. 确认当前已安装 package 版本
2. 暂停相关调度
3. 安装新 package
4. 先执行 `dbt test`
5. 再执行 `dbt build/run`
6. 核对核心表和关键指标
7. 更新版本记录

## 回滚

1. 找到上一个稳定 package
2. 执行 `reset --package <code>`
3. 安装旧 package
4. 重新执行 `run`
5. 做最小回归验证

## 可选 register

只有在客户明确要求平台逻辑建模展示时，才执行 register：

- 已有项目空间：
  `register --plan-id <uuid>`
- 无项目空间且明确允许创建：
  `register --create-plan --plan-name <name>`

注意：

- 不允许通过“先打开逻辑建模页面”来碰运气触发自动同步
- register 应作为单独的受控操作

## 验证清单

- 目标表是否生成
- 行数是否合理
- 关键指标是否出数
- `dbt test` 是否通过或仅剩已知告警
- 未执行 register 时，`modeling_plan` 不应新增
- 未执行 register 时，`modeling_sql_model` 不应新增

## 常见故障

- `source` 重复定义
- `ods_sources.yml` 被历史映射污染
- `profiles/` 或 `target/` 不可写
- 目标表已存在但 build 失败
- 打开 web 后模型被自动发现
- register 后出现同名项目空间
