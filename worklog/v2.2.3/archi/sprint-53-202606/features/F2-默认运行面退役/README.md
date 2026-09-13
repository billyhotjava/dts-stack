# F2: 默认运行面退役

**优先级**: P0  
**状态**: DONE

## 目标

让默认部署、默认构建和默认初始化不再启动或构建 `dts-metrics`，同时保留显式 legacy 回滚路径。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | compose 默认服务退役 | P0 | DONE | F0/T03 |
| T02 | init 与 image/build 默认项退役 | P0 | DONE | T01 |
| T03 | 平台 metrics capability 配置降级 | P1 | DONE | F0/T02 |
| T04 | legacy 回滚与物理删除边界文档 | P0 | DONE | T01 T02 T03 |

## 完成标准

- [x] 默认 app compose 不启动 dts-metrics。
- [x] 默认 build all 不构建 dts-metrics。
- [x] 默认 init 不生成 metrics triplet。
- [x] 需要旧服务时有明确 legacy/profile 或手工构建方式。
