# F1: Platform统一Admin Gateway

**优先级**: P0
**状态**: DONE

## 目标

建立 `dts-platform` 统一 admin gateway 机制，并将平台当前所有 `dts-admin` 调用与前端直连路径收口到平台接口层。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | Admin Gateway基础层 | P0 | DONE | - |
| T02 | 目录域迁移 | P0 | DONE | T01 |
| T03 | 认证与PKI迁移 | P0 | DONE | T01 |
| T04 | 基础设施工作流菜单审计迁移 | P0 | DONE | T01 |
| T05 | 前端直连清理与回归 | P0 | DONE | T02,T03,T04 |

## 完成标准

- [x] transport、headers、错误包装、envelope 解包统一
- [x] 目录、认证、PKI、基础设施、工作流、菜单、审计调用迁入 gateway
- [x] 前端 service 不再直连 `dts-admin`
- [x] 有完整验证记录和集成检查
