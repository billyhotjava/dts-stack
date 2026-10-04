# IAM-001: ClassificationService 接 REST，补真实同步状态/失败重试闭环

## 范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/iam/ClassificationService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/`
- 相关 dto / repository / test

## 目标

- 把用户搜索、刷新、同步状态、执行同步、失败重试真正对外暴露
- 将当前“直接写 SUCCESS 日志”的流程升级成最小可用同步运行时

## 交付

- 新的 sync REST 资源
- 失败记录与重试入口
- 后端测试覆盖 user search / sync status / run sync / retry failure

## 验收

- 前端不再需要依赖假数据就能调用同步接口
- 同步失败可以落库并可查询
- `retryFailure` 不再只是再次写一条成功日志

## 当前进度

- 状态：TODO
- 备注：一期可以先接内部数据源，不强制外部 IAM 全接入
