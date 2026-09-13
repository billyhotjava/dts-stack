# T05: 聚合实现 · TOP 资产（按密级）

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

实现 TOP 资产聚合，按密级降序（S1 > S2 > S3 > S4），同级按更新时间降序，取前 10 条。仅需要元信息（`id / name / classification / updatedAt / bizDomain`），不需要统计数。

## 技术设计

### 先确认资产表结构

在开始实现前，读 `CatalogDataset` 实体 / `CatalogDatasetRepository` 确认：

- [ ] 是否存在 `classification` 字段（字符串 `"S1"/"S2"/"S3"/"S4"`）。
- [ ] 是否存在 `deptCode`、`bizDomain` 字段。
- [ ] `updatedDate` 或 `lastModifiedDate` 的实际字段名。

如果缺少 `bizDomain` 或 `deptCode`，这两个过滤就降级为"不过滤"，并在本 task 输出中补一条 follow-up task（"为 CatalogDataset 增加 bizDomain/deptCode 字段"）到 sprint-queue 备注里。

### 排序语义

用数据库 `CASE WHEN` 映射密级权重：

```java
@Query("""
  select d from CatalogDatasetEntity d
  where (:scope = 'ALL' and :deptCode is null or d.deptCode = :deptCode)
    and (:bizDomain is null or d.bizDomain = :bizDomain)
  order by case d.classification
             when 'S1' then 4
             when 'S2' then 3
             when 'S3' then 2
             when 'S4' then 1
             else 0
           end desc,
           d.lastModifiedDate desc
""")
List<CatalogDatasetEntity> findTopByClassification(
    @Param("scope") String scope,
    @Param("deptCode") String deptCode,
    @Param("bizDomain") String bizDomain,
    Pageable pageable
);
```

同 T04：ALL 且 `deptCode=null` 的分支要在 Service 层分出两个调用避免 JPQL 条件膨胀。

### Service 装配

```java
private List<TopAsset> computeTopAssets(String scope, String deptCode, String bizDomain) {
    Pageable top10 = PageRequest.of(0, 10);
    List<CatalogDatasetEntity> rows = ("ALL".equals(scope) && !StringUtils.hasText(deptCode))
        ? datasetRepo.findTopByClassificationAll(bizDomain, top10)
        : datasetRepo.findTopByClassification(scope, deptCode, bizDomain, top10);

    return rows.stream()
        .map(d -> new TopAsset(
            d.getId().toString(),
            d.getName(),
            d.getClassification(),
            d.getLastModifiedDate(),
            d.getBizDomain()
        ))
        .toList();
}
```

### MINE 场景

员工视角"我常用的资产"按设计稿口径 = 近 30 天访问过的资产，按个人访问量降序。与按密级口径不同——因此 MINE 分支走**另一路**：

```java
if ("MINE".equals(scope)) {
    return catalogAccessLogRepo.findTopRecentByUser(userLogin, 10)
        .stream()
        .map(row -> new TopAsset(
            row.datasetId().toString(), row.name(), row.classification(),
            row.lastAccessedAt(), row.bizDomain()))
        .toList();
}
```

若项目无 `catalog/access log` 表，MINE 场景先降级为：按用户 created/updated 的资产 TOP 10（先让功能能 demo）。与 F5/T02 的员工文案保持一致。

## 影响范围

- `CatalogDatasetRepository.java`（新增 2 个查询：`findTopByClassification` 、`findTopByClassificationAll`）
- `WorkbenchLeaderOverviewService.java`（新增 `computeTopAssets`）

## 验证

- [ ] 单测：
  - `topAssets_orders_S1_first_then_S2_S3_S4()`
  - `topAssets_within_same_classification_orders_by_updated_desc()`
  - `topAssets_filters_by_deptCode_and_bizDomain()`
  - `topAssets_limits_to_10()`
  - `topAssets_MINE_uses_user_access_log()`
- [ ] IT：fixture 4 密级各 3 条 → 断言前 10 条排序正确。

## 完成标准

- [ ] `classification` 字段按 `S1 > S2 > S3 > S4` 正确排序（非字母序）。
- [ ] MINE 场景单独分支，不与按密级排序混用。
- [ ] 资产字段缺失（如 `bizDomain` 为 null）时不抛 NPE，返回 `null`。
