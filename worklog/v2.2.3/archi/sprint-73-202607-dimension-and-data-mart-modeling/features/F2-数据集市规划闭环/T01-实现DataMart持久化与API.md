# T01: 实现 DataMart 持久化与 API

**优先级**: P0  
**状态**: DRAFT  
**依赖**: F0、F1/T01

## 目标

交付 tenant-scoped、versioned、可幂等创建和 CAS 更新的 DataMart owner。

## 技术设计 (Contract-first)

- **输入契约**: `CreateDataMartCommand/UpdateDataMartCommand`。
- **输出契约**: `DataMartView`、ETag 和 `assets/contract-design.md` 错误码。
- **数据流**: Resource → `DataMartApplicationService` → Repository → head/revision/domain relation。
- **错误路径**: 不可见 domain=404；重复 code=409；幂等 key 污染=409；CAS=412；引用中退役=409。
- **复用点**: DimensionDefinition 的 head/revision/idempotency/CAS 模式；`CatalogDomainResolutionPort`。
- **实现方案**:
  1. RED REST/service/repository/Liquibase tests；
  2. 创建三个 DataMart 表；
  3. 实现列表、创建、读取、更新、确认、退役；
  4. 注册 master.xml 和审计动作。

## 影响范围

`dts-platform` modeling service/resource/repository、Liquibase、focused tests。

## 验证

- [ ] clean DB 迁移
- [ ] 1..100 domain 边界
- [ ] 并发幂等/CAS
- [ ] size>100 返回 400

## Definition of Done

- [ ] 架构契约和 migration IT 绿
- [ ] 数据无 N+1
- [ ] 证据入 `it/IT-01-data-mart.md`
