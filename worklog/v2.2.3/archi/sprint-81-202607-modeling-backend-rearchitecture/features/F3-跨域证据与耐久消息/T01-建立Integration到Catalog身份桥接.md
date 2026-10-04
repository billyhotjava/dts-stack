# T01：建立 Integration 到 Catalog 身份桥接

**优先级**：P0
**状态**：PLANNED
**依赖**：F1/T02 Catalog identity port

## 目标

让接入结果先解析/注册统一 Catalog 身份，再被质量和建模消费，禁止 integration 直接创建模型状态。

## 技术设计（Contract-first）

- **输入契约**：`AssetRegistrationCommand {sourceSystem,externalIdentity,assetType,namespace,name,checksum,correlationId}`；tenant/actor 服务端注入。
- **输出契约**：`CatalogAssetRef {assetType,assetKey,tenantId,externalIdentity,checksum}`。
- **数据流**：integration completion → catalog port → identity resolution/register → domain event → quality/modeling query。
- **错误路径**：ambiguous/unsupported/cross-tenant/duplicate-with-different-checksum typed failure；不得回退原始 string key。
- **复用点**：CatalogAssetKey/Type、identity resolution audit、existing catalog repository。
- **禁止**：integration import modeling repository；为 ingestion 建专用模型资产表。

## 影响范围

integration completion adapter、catalog application port、domain event publisher。

## 验证

- [ ] 注册/解析/重放/冲突/跨租户测试。
- [ ] 同 external identity 返回同 asset key。
- [ ] 失败不创建 quality binding/ModelSpec。

## Definition of Done

- [ ] 所有新接入资产先具有 canonical asset identity。
- [ ] integration→modeling 直接依赖为 0。
- [ ] identity 成功/失败产生分类审计和 correlationId。
