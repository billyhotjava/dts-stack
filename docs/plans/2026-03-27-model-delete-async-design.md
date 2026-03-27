# SQL Model Delete Async Design

## 背景

当前逻辑建模页面删除模型采用同步链路：

- 前端点击删除后等待 `DELETE /modeling/sql-models/{id}`
- 后端在同一事务内删除数据库记录并同步删除 dbt 伴生文件
- 前端删除成功后立即刷新模型列表
- 列表刷新后页面会自动选中下一个模型，并继续触发字段、契约影响、专题绑定、运行状态等详情请求

这在局域网环境下是不合理的。局域网 RTT 应为毫秒级，用户看到 5 秒以上的等待，根因通常不是网络，而是服务端做了不该同步做的工作，或者前端在一个操作后立刻打出不必要的联动请求。

## 目标

1. 删除模型接口在正常场景下快速返回，不被文件系统清理拖慢
2. 文件清理保持可靠，但放到事务提交后异步执行
3. 删除后页面不再自动选中下一个模型，避免触发整串详情请求
4. 保持现有数据一致性语义：数据库删除成功后才允许异步清理文件

## 方案

### 后端

沿用仓库里已有的 `afterCommit + taskExecutor` 模式。

- `ModelingSqlModelService.delete()` 仍保留数据库删除逻辑
- 仅当模型路径没有被其它模型复用时，注册一个 `afterCommit` 回调
- 回调在事务提交后通过 `taskExecutor` 异步执行 `fileService.deleteFileIfChanged(modelPath, null)`
- 若当前线程没有事务同步上下文，则直接通过 `taskExecutor` 异步执行
- 异步清理失败只记录日志，不影响已提交的数据库删除结果

这样删除请求的关键路径只剩权限校验、读取模型、删除记录，文件 IO 不再阻塞接口返回。

### 前端

删除成功后切换成“列表刷新、详情待用户手动选择”的模式。

- 删除当前模型后，先在本地清空 `activeModelKey`
- 设置一次性保护标记，阻止列表刷新后自动选中第一个模型
- 仅刷新模型列表，不主动恢复选中
- 用户手动点选模型时，再恢复自动选择能力

这样删除动作只会触发必要的列表刷新，不会继续把字段、契约影响、专题绑定、运行记录等详情接口全部打出去。

## 取舍

### 为什么不只加 timeout

只加 timeout 只能掩盖问题，不能改善交互模型。一个本应快速返回的删除动作，如果因为文件清理或删除后联动而拖到 5-15 秒，本质是设计错误，不是 timeout 配置错误。

### 为什么这次不引入完整异步作业模型

当前问题集中在“提交后文件清理”和“删除后详情联动”两个点。直接引入新的删除作业表、状态查询和轮询会扩大改动面。先把同步阻塞点拆出去，能以最小改动恢复合理交互。

后续若发现文件清理本身仍需要可观测性，再升级为正式 job 模型更合适。

## 风险与控制

- 风险：异步文件删除失败后，数据库记录已删、文件残留
  - 控制：日志告警；文件删除本身是幂等的，后续可以加巡检/补偿
- 风险：前端删除后无自动选中，用户需要再次点击
  - 控制：这是有意的交互收敛，目的是避免删除后自动风暴请求
- 风险：测试环境里异步调度行为不易断言
  - 控制：服务层单测直接验证 `afterCommit` 时机与 `taskExecutor` 调度，而不是验证线程真实执行

## 测试策略

### 后端

- 给 `ModelingSqlModelServiceTest` 增加删除用例
- 断言事务同步激活时：
  - `delete()` 返回前不会直接调用文件删除
  - `afterCommit` 后才会把删除任务投递到 `taskExecutor`
  - 执行投递的 `Runnable` 后才触发 `fileService.deleteFileIfChanged`

### 前端

- 抽一个轻量 helper，描述“删除当前模型后的选中状态”
- 用 node:test 验证：
  - 删除当前模型后应清空选中并阻止自动补选
  - 手动重新选择模型后，允许恢复自动选择

## 涉及文件

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelServiceTest.java`
- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/` 下新增删除交互 helper 与测试
