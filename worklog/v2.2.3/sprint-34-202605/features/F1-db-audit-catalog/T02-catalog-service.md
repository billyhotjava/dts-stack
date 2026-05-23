# T02: Catalog domain/repository/service

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

实现 dts-admin DB 审计目录查询服务，为 V2 审计写入提供确定性分类。

## 技术设计

- 新增 JPA entity/repository：模块目录、动作目录、分类 miss。
- 新增 `AuditActionCatalogService`：按 `sourceSystem + actionCode` 查找动作，记录 miss。
- 返回对象包含模块、动作、操作类型、资源类型、allowEmptyTargets、版本和来源。

## 影响范围

- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/domain/audit/`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/repository/audit/`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/audit/`

## 验证

- [x] focused unit test 覆盖命中和未命中动作。
- [x] 未命中只写 miss，不抛出影响业务写入的异常。

## 完成标准

- [x] `AuditV2Service` 可消费该服务返回的分类结果。
- [x] 未知 actionCode 有治理证据。
