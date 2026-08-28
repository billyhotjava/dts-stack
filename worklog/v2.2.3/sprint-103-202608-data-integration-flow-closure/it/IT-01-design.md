# IT-01：任务设计、保存与并发

检查日期：2026-08-28。判定：`PASS_SOURCE_DEPLOYED`。

## 已验证

- `IngestionTaskDesignServiceTest` 11 项通过：规范化、稳定 checksum、mapping 上限/结构、source/sync config、sync mode 与目标引用校验。
- `IngestionTaskResourceTest` 4 个 Sprint-103 方法通过：task-owned design、ETag/If-Match、任务级取消与持久化幂等执行身份。
- 平台验证与保存复用同一 write/reference 权限边界；对应代理测试通过。
- 前端提供任务选择、四步类型化表单、服务端数据集搜索、脏数据重置和 409 并发保护；组件测试 3 项、source contract 4 项通过。
- 新代码已进入接入、平台与 Web 三个运行镜像。

## 保留门禁

本轮没有修改 7 个现有 active 任务。真实双会话 409 和三角色矩阵留待可回收金丝雀/隔离账号验收。
