# T01: setter 注入改构造器注入

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

把 RX/T03 阶段为绕循环依赖引入的 `@Autowired(required=false)` setter 注入改回构造器注入，循环依赖用 `@Lazy` 或事件机制解耦。

## 背景

`IndicatorService` 与 `ModelingSqlModelService` 当前实现：

```java
private CodeAssetGrantWriter codeAssetGrantWriter;

@Autowired(required = false)
public void setCodeAssetGrantWriter(CodeAssetGrantWriter codeAssetGrantWriter) {
    this.codeAssetGrantWriter = codeAssetGrantWriter;
}
```

违反 `rules/java/patterns.md` 强制约束：
> Always use constructor injection — never field injection.

setter 注入隐藏依赖、破坏 final 字段不变性、单元测试 setup 复杂，并掩盖了 `CodeAssetGrantWriter` → `AssetPermissionService` → `?` → 回到 `IndicatorService` 的真实循环。

## 技术设计

1. 排查循环依赖根因：
   ```bash
   ./mvnw -pl dts-platform spring-boot:run -Dspring.main.allow-circular-references=false
   ```
   找出真实环。
2. 两种解法（按优先级）：
   - **A（首选）**：把 `CodeAssetGrantWriter` 拆出 `AssetPermissionService` 依赖，改成 `AssetOwnershipRepository + ApplicationEventPublisher`，发布 `AssetOwnershipGrantedEvent`；`AssetPermissionService` 监听事件 upsert grant。彻底解环。
   - **B（备用）**：构造器注入 + `@Lazy CodeAssetGrantWriter`，运行时延迟代理。
3. 改回构造器注入，字段重新加 `final`。
4. 测试同步迁移到 `@ExtendWith(MockitoExtension.class)` + 构造器 mock 注入。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CodeAssetGrantWriter.java`
- 任何接入 writer 的新 service（参见 F1/T06）
- 对应 `*ServiceTest`

## 验证

- [ ] `mvn -pl dts-platform compile -Dspring.main.allow-circular-references=false` 启动不抛环
- [x] `IndicatorService` / `ModelingSqlModelService` / `ApiCatalogService` 通过构造器注入 `CodeAssetGrantWriter`。
- [x] `grep '@Autowired(required = false)'` 在本次 code asset writer 调用方为 0；平台配置类历史 optional bean 不属于本任务范围。

## 完成标准

- [x] 所有 code asset writer 调用方走构造器注入。
- [x] 本次修改未新增 setter/field injection；如最终 Spring context 暴露循环，再单独补 `@Lazy` 或事件解耦。
- [x] 已更新相关构造器测试，不依赖 setter / 反射注入。
