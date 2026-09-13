# F5: 配置外部化与旧路径下线

**优先级**: P1
**状态**: DONE

## 目标

API 入湖运行时参数全部配置化（`ApiProperties`），删除内嵌 Python 旧路径与 env 密钥语义，存量 DAG 平滑迁移。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | ApiProperties配置类 | P0 | DONE | - |
| T02 | 内嵌Python与env密钥路径下线 | P0 | DONE | F2全部, F3-T01 |
| T03 | 文档与部署清单 | P1 | DONE | T02 |

## 完成标准

- [x] API 路径代码内无业务硬编码（对照审计清单逐项核销；证据：`../../it/evidence/api-properties-hardcoding-20260612.txt`）
- [x] `buildApiDagSource` 旧实现删除，存量 API DAG 重新生成为瘦触发版（IT-08：`../../it/evidence/api-dag-migration-20260612.txt`）
- [x] 部署/配置/迁移文档齐备
