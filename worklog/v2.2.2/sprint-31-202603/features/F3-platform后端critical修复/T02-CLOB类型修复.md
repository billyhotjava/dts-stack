# T02: AssetPermissionAudit CLOB 类型修复

**严重度**: Critical
**文件**: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/permission/AssetPermissionAudit.java`

## 问题

```java
@Column(name = "detail", columnDefinition = "clob")
```

PostgreSQL 无 `clob` 类型。Liquibase 迁移正确使用 `${clobType}` → `text`，但 JPA 实体硬编码 `clob` 会导致 Hibernate schema 验证/生成失败。

## 修复方案

```java
@Column(name = "detail", columnDefinition = "text")
private String detail;
```

或移除 `columnDefinition`，让 JPA 自动映射：
```java
@Lob
@Column(name = "detail")
private String detail;
```

推荐直接用 `columnDefinition = "text"`，与 Liquibase 迁移一致。

## 验证

```bash
mvn compile -pl source/dts-platform -q
# Hibernate 无 schema validation 警告
```
