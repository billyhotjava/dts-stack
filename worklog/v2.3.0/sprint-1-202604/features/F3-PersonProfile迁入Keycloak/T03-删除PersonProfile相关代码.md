# T03: 删除 PersonProfile 相关代码

**优先级**: P1
**状态**: READY
**依赖**: T02, F2

## 目标
在数据迁移完成且缓存层稳定运行后，删除 PersonProfile 相关的实体、Repository、Service、导入逻辑中的 PersonProfile 写入。

## 技术设计

### 需要删除/修改的文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `PersonProfile.java` | 删除 | JPA 实体 |
| `PersonProfileRepository.java` | 删除 | Repository |
| `PersonnelImportService.java` | 修改 | 移除 PersonProfile upsert，保留 import_record |
| `AdminUserService.refreshSnapshotsFromProfiles()` | 删除 | 从 PersonProfile 刷新 AdminKeycloakUser |
| `PlatformDirectoryResource.fallbackFromSnapshots()` | 已在 F2/T03 删除 | - |

### 保留的表

- `person_import_batch` — 导入批次记录
- `person_import_record` — 导入明细记录（含 national_id 明文，溯源用）

### Liquibase

不立即 DROP TABLE，而是添加注释标记废弃：
```xml
<changeSet id="YYYYMMDD_01_deprecate_person_profile">
    <sql>COMMENT ON TABLE person_profile IS 'DEPRECATED since v2.3.0 - data migrated to Keycloak attributes'</sql>
</changeSet>
```

DROP 在本 Sprint F5/T01 内执行（v2.3.0），不延后。

## 验证
- [ ] 编译通过，无 PersonProfile 引用残留
- [ ] 人员导入功能正常（直写 Keycloak）
- [ ] 导入记录正常保存
- [ ] person_profile 表无新数据写入

## 完成标准
- [ ] PersonProfile 实体和 Repository 删除
- [ ] 所有引用点清理
- [ ] person_profile 表标记废弃
