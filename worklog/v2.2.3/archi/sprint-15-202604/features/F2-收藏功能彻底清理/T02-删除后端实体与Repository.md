# T02: 删除后端 PortalUserFavorite 实体 + Repository

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

物理删除 `PortalUserFavorite` JPA 实体与对应 Repository，确保无代码引用。

## 技术设计

### 要删除的文件

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/portal/PortalUserFavorite.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/portal/PortalUserFavoriteRepository.java`

### 删除前验证无引用

```bash
grep -rn "PortalUserFavorite" /opt/prod/s10/v2.2.3/source/dts-platform/src/
```

预期：仅 `WorkbenchService.java` 和 `WorkbenchResource.java`（T03 会处理）、两个被删文件自身。任何其他引用都必须在本 task 内先清理。

特别检查点：
- [ ] 是否存在依赖 `PortalUserFavorite` 的序列化类（DTO / 映射器）。
- [ ] 是否存在 Spring Data projection interface。
- [ ] `portal/` 包下是否还有类似 entity，若无则整个 `portal/` 包是否可删（本 task 先不动，避免影响 F2 外的其他 portal 遗留）。

### 回滚策略

该 task 只动 Java 源码，不动 DB，revert commit 即可恢复。

## 影响范围

- 删除：`domain/portal/PortalUserFavorite.java`
- 删除：`repository/portal/PortalUserFavoriteRepository.java`

## 验证

- [ ] `grep -rn "PortalUserFavorite" source/dts-platform/src/` 除 `WorkbenchService.java` / `WorkbenchResource.java` 外无结果。
- [ ] `./mvnw -pl source/dts-platform compile` 在本 task 完成时 **会失败**（因为 WorkbenchService/Resource 仍引用），属于预期；T03 会修复。
- [ ] 本 task 的 commit 信息明确标注："breaks compile, fixed by next commit (T03)"，或把 T02+T03 合并为一个 commit（推荐）。

## 完成标准

- [ ] 两个文件完全删除，git status 显示 `deleted:`。
- [ ] 如果选择 T02 + T03 合并提交，compile 在合并后恢复通过。
