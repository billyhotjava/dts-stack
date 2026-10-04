# T03: AuditIngestResource 未分类治理

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

platform 送来的未知动作不再被默默吞掉或错误归类，而是形成可治理 miss 记录。

## 技术设计

- miss 记录包含 sourceSystem、actionCode、moduleKeyRaw、requestUri、httpMethod、actor、payload 摘要。
- miss 不阻断原业务操作审计入库。
- service/system actor 仍按现有非人工规则跳过。

## 影响范围

- `AuditIngestResource.java`
- `AuditActionCatalogService.java`

## 验证

- [x] unknown actionCode 返回 202 且写入未分类审计/miss。
- [x] service actor 仍跳过，不写成人工审计。

## 完成标准

- [x] 审计治理员能追踪待补目录项。
