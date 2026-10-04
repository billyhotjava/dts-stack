# F1: 升级模式识别与包契约

**优先级**: P0
**状态**: DONE

## 目标
明确升级器如何识别 normal/legacy 运行模式，以及升级包在两种模式下必须满足的交付契约。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 识别 legacy/normal 运行模式 | P0 | DONE | - |
| T02 | 定义离线升级包结构与元数据 | P0 | DONE | T01 |
| T03 | 梳理 compose 主文件与启动脚本契约 | P1 | DONE | T01 |

## 完成标准
- [x] 明确 legacy 模式以 `docker-compose.legacy.yml` 作为运行态主文件
- [x] 明确 normal 模式使用 `docker-compose.yml` / `docker-compose-app.yml`
- [x] 升级包结构、manifest、checksums、images 目录有统一约定
