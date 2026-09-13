# F5: 平台读路径与本地回退

**优先级**: P1
**状态**: DONE
**依赖**: F1, F4

## 目标

修复平台侧 OpenMetadata 查询命中率和回退表达，让用户知道当前看到的数据来自 OpenMetadata 还是本地 catalog。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | FQN Candidate 生成与日志 | P0 | DONE |
| T02 | Metadata Source 与 Fallback Reason | P1 | DONE |
| T03 | 血缘/质量查询缺失态 | P1 | DONE |
| T04 | 前端 Catalog 状态展示 | P1 | DONE |

## 完成标准

- [x] FQN candidate 生成有单测覆盖坏 pattern。
- [x] API 响应能表达 OpenMetadata 命中、未命中、本地回退和错误原因。
- [x] 血缘和质量页不再把 OpenMetadata 查询失败表现为空白成功。
- [x] 前端状态文案清晰，不泄露 token、密码或内部堆栈。
