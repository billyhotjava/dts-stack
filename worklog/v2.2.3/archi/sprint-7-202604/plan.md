# Sprint-7: 数据目录与元数据体系完善 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补全数据目录缺口（域资产统计 API、字段列表 API、dbt 血缘导入、DatasetPicker 组件），并重构 4 个 catalog 前端页面的 UI/UX，解除 Sprint-8 指标中心重构的阻塞。

**Architecture:**
- 后端：在现有 `CatalogDomainResource` / `CatalogDatasetResource` 中追加端点，新建 `CatalogDbtLineageService` 和 `CatalogDataProduct` 实体；无需新 domain_id 迁移（FK 已存在）
- 前端：在现有 6 个 catalog 页面基础上升级（不推倒重建）；新建 `DatasetPicker` 可复用组件；新增 `/catalog/datasets/:id` 路由作为独立 Tab 详情页
- 图可视化：引入 `@xyflow/react` 渲染血缘 DAG

**Tech Stack:** Spring Boot 3.4.5 / Java 21 / JPA + PostgreSQL / React 19 / antd v5 / Tailwind v4 / @xyflow/react

**关键已知路径：**
- 后端 catalog Controller: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/`
- 后端 catalog Repository: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/catalog/`
- 后端 catalog Domain: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/catalog/`
- Liquibase changelog: `source/dts-platform/src/main/resources/config/liquibase/changelog/`
- Liquibase master: `source/dts-platform/src/main/resources/config/liquibase/master.xml`
- 前端 catalog 页面: `source/dts-platform-webapp/src/pages/catalog/`
- 前端路由: `source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx`
- 前端 API: `source/dts-platform-webapp/src/api/platformApi.ts`

---

## 实施进度（2026-04-05 代码审查后更新）

> T1~T4、T6~T8、T10~T13 已在代码中实现，checkbox 未逐一勾选（实现先于本计划落地）。
> 剩余未完成：**T5**（IndicatorWizard DatasetPicker）、**T9**（DatasetDetailPage 关联指标）。
> 计划外新增缺口见文末 **Remaining Work** 章节（T14~T18）。

---

## P0 阶段：解除 Sprint-8 阻塞

### Task 1: 后端 — 域资产统计端点

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDomainResource.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/catalog/CatalogDatasetRepository.java`

**背景：** `CatalogDataset.domain` 是 `@ManyToOne FK` 到 `CatalogDomain`，数据已存在，只需统计查询。`CatalogDomainResource` 注入了 `CatalogDomainRepository`，还需注入 `CatalogDatasetRepository`。

- [ ] **Step 1: 读取 CatalogDomainResource 当前构造器和注入**

  验证当前只注入了 `CatalogDomainRepository` 和 `AuditService`，确认类结构。

- [ ] **Step 2: 在 CatalogDatasetRepository 添加按域统计方法**

  在 `CatalogDatasetRepository` 中添加：

  ```java
  long countByDomain(CatalogDomain domain);
  ```

- [ ] **Step 3: 在 CatalogDomainResource 注入 CatalogDatasetRepository**

  更新构造器：

  ```java
  private final CatalogDomainRepository domainRepo;
  private final CatalogDatasetRepository datasetRepo;
  private final AuditService audit;

  public CatalogDomainResource(CatalogDomainRepository domainRepo,
                                CatalogDatasetRepository datasetRepo,
                                AuditService audit) {
      this.domainRepo = domainRepo;
      this.datasetRepo = datasetRepo;
      this.audit = audit;
  }
  ```

- [ ] **Step 4: 添加 asset-stats 端点**

  在 `CatalogDomainResource` 中添加（注意 Java CLAUDE.md 规则：用 `orElseThrow()` 不用 `get()`）：

  ```java
  @GetMapping("/domains/{id}/asset-stats")
  @Transactional(readOnly = true)
  public ApiResponse<Map<String, Object>> getDomainAssetStats(@PathVariable UUID id) {
      CatalogDomain domain = domainRepo.findById(id).orElseThrow();
      long datasetCount = datasetRepo.countByDomain(domain);
      // indicatorCount 和 qualityRuleCount 当前未统计，返回 -1 表示"未实现"
      Map<String, Object> stats = Map.of(
          "datasetCount", datasetCount,
          "indicatorCount", -1L,
          "qualityRuleCount", -1L
      );
      audit.audit("READ", "catalog.domain.asset-stats", id.toString());
      return ApiResponses.ok(stats);
  }
  ```

- [ ] **Step 5: 验证编译**

  ```bash
  cd source/dts-platform && mvn compile -q 2>&1 | tail -20
  ```

  Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

  ```bash
  git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDomainResource.java \
          source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/catalog/CatalogDatasetRepository.java
  git commit -m "feat(catalog/F1): add domain asset-stats endpoint"
  ```

---

### Task 2: 前端 — SubjectAreasPage 接入资产统计

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx`
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`

**背景：** `SubjectAreasPage.tsx` 第 346 行硬编码 `资产数：-`，域详情面板的「域级治理指标」区块也是静态 `-`。选中域后需调用 Task 1 新增的 `GET /api/catalog/domains/{id}/asset-stats`。

- [ ] **Step 1: 在 platformApi.ts 添加 API 函数**

  在 `platformApi.ts` 中适当位置添加：

  ```typescript
  export const getDomainAssetStats = (domainId: string) =>
    api.get(`/api/catalog/domains/${domainId}/asset-stats`).then((r) => r.data?.data as {
      datasetCount: number;
      indicatorCount: number;
      qualityRuleCount: number;
    });
  ```

- [ ] **Step 2: 在 SubjectAreasPage 添加状态和加载逻辑**

  在组件顶部 state 区域添加：

  ```typescript
  const [assetStats, setAssetStats] = useState<{ datasetCount: number; indicatorCount: number; qualityRuleCount: number } | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);
  ```

  添加加载函数：

  ```typescript
  const loadAssetStats = useCallback(async (domainId: string) => {
    setStatsLoading(true);
    try {
      const stats = await getDomainAssetStats(domainId);
      setAssetStats(stats);
    } catch {
      setAssetStats(null);
    } finally {
      setStatsLoading(false);
    }
  }, []);
  ```

- [ ] **Step 3: 在 activeDomain 变化时触发加载**

  在现有 `useEffect` 下方添加：

  ```typescript
  useEffect(() => {
    if (activeDomain?.id) {
      void loadAssetStats(activeDomain.id);
    } else {
      setAssetStats(null);
    }
  }, [activeDomain?.id, loadAssetStats]);
  ```

- [ ] **Step 4: 替换硬编码的 `-`**

  找到第 346 行附近的 `资产数：-`，替换为：

  ```tsx
  负责人：{activeDomain.owner || "未指定"} ｜ 子域数：{activeChildren.length} ｜
  资产数：{statsLoading ? "..." : (assetStats?.datasetCount ?? "-")}
  ```

  找到「域级治理指标」区块中的三行静态 `-`，替换为：

  ```tsx
  <div className="flex items-center justify-between py-1">
    <Text>数据集数</Text>
    <Text strong>{statsLoading ? "..." : (assetStats?.datasetCount ?? "-")}</Text>
  </div>
  <div className="flex items-center justify-between py-1">
    <Text>指标数</Text>
    <Text strong>{assetStats?.indicatorCount === -1 ? "-" : (assetStats?.indicatorCount ?? "-")}</Text>
  </div>
  <div className="flex items-center justify-between py-1">
    <Text>质量规则数</Text>
    <Text strong>{assetStats?.qualityRuleCount === -1 ? "-" : (assetStats?.qualityRuleCount ?? "-")}</Text>
  </div>
  ```

- [ ] **Step 5: 确认 import**

  确保文件顶部导入了 `getDomainAssetStats` 和 `useCallback`。

- [ ] **Step 6: 手动测试**

  启动前端开发服务，进入「主题域管理」页面，点击任一主题域，确认「资产数」从 `-` 变为实际数字。

- [ ] **Step 7: Commit**

  ```bash
  git add source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx \
          source/dts-platform-webapp/src/api/platformApi.ts
  git commit -m "feat(catalog/F1): connect SubjectAreasPage to domain asset-stats API"
  ```

---

### Task 3: 后端 — 数据集字段列表端点

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDatasetResource.java`

**背景：**
- `CatalogTableSchema` → `CatalogTableSchemaRepository.findByDataset(dataset)` 已有
- `CatalogColumnSchema` → `CatalogColumnSchemaRepository.findByTable(table)` 已有
- `CatalogDatasetResource` 已注入 `CatalogDatasetRepository`，需额外注入两个 Schema Repository

- [ ] **Step 1: 读取 CatalogDatasetResource 当前构造器**

  确认已注入哪些 Repository，找到合适位置添加新端点。

- [ ] **Step 2: 注入 Schema Repository（如未注入）**

  在构造器中添加 `CatalogTableSchemaRepository tableSchemaRepo` 和 `CatalogColumnSchemaRepository columnSchemaRepo`。

- [ ] **Step 3: 添加 fields 端点**

  ```java
  @GetMapping("/datasets/{id}/fields")
  @Transactional(readOnly = true)
  public ApiResponse<List<Map<String, Object>>> getDatasetFields(@PathVariable UUID id) {
      CatalogDataset dataset = datasetRepo.findById(id).orElseThrow();
      List<CatalogTableSchema> tables = tableSchemaRepo.findByDataset(dataset);
      List<Map<String, Object>> fields = tables.stream()
          .flatMap(table -> columnSchemaRepo.findByTable(table).stream()
              .map(col -> {
                  Map<String, Object> m = new java.util.LinkedHashMap<>();
                  m.put("name", col.getName());
                  m.put("dataType", col.getDataType());
                  m.put("comment", col.getComment());
                  m.put("nullable", col.getNullable());
                  m.put("tableName", table.getName());
                  return m;
              }))
          .toList();
      return ApiResponses.ok(fields);
  }
  ```

- [ ] **Step 4: 验证编译**

  ```bash
  cd source/dts-platform && mvn compile -q 2>&1 | tail -20
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDatasetResource.java
  git commit -m "feat(catalog/F2): add dataset fields endpoint from CatalogColumnSchema"
  ```

---

### Task 4: 前端 — DatasetPicker 可复用组件

**Files:**
- Create: `source/dts-platform-webapp/src/components/catalog/DatasetPicker.tsx`
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`

**背景：** 无现成 DatasetPicker。`DatasetAccessRequestDialog` 中有类似数据集搜索逻辑，可参考但不共用（职责不同）。

- [ ] **Step 1: 在 platformApi.ts 添加字段查询函数**

  ```typescript
  export type DatasetField = {
    name: string;
    dataType: string;
    comment?: string;
    nullable?: boolean;
    tableName?: string;
  };

  export const getDatasetFields = (datasetId: string): Promise<DatasetField[]> =>
    api.get(`/api/catalog/datasets/${datasetId}/fields`).then((r) => r.data?.data ?? []);
  ```

- [ ] **Step 2: 创建 DatasetPicker 组件**

  ```typescript
  // source/dts-platform-webapp/src/components/catalog/DatasetPicker.tsx
  import { useEffect, useState, useCallback } from "react";
  import { Select, Space, Tag } from "antd";
  import { listDatasets, listDomains, getDatasetFields, type DatasetField } from "@/api/platformApi";

  type DatasetOption = {
    id: string;
    name: string;
    warehouseLayer?: string;
    domainId?: string;
    domain?: string;
  };

  type Props = {
    value?: string;                              // datasetId
    onChange?: (datasetId: string | undefined) => void;
    onFieldsLoaded?: (fields: DatasetField[]) => void;
    placeholder?: string;
    style?: React.CSSProperties;
  };

  const LAYER_COLOR: Record<string, string> = {
    ODS: "default", DWD: "blue", DWS: "cyan", ADS: "green",
  };

  export function DatasetPicker({ value, onChange, onFieldsLoaded, placeholder, style }: Props) {
    const [options, setOptions] = useState<DatasetOption[]>([]);
    const [loading, setLoading] = useState(false);
    const [fieldsLoading, setFieldsLoading] = useState(false);
    const [keyword, setKeyword] = useState("");
    const [domainId, setDomainId] = useState<string | undefined>();
    const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);

    useEffect(() => {
      void (async () => {
        try {
          const resp: any = await listDomains({ page: 0, size: 200 });
          const content = Array.isArray(resp?.content) ? resp.content : [];
          setDomains(content.map((d: any) => ({ id: String(d.id), name: String(d.name || "") })));
        } catch { /* global interceptor handles */ }
      })();
    }, []);

    const loadDatasets = useCallback(async (kw: string, dId?: string) => {
      setLoading(true);
      try {
        const resp: any = await listDatasets({
          page: 0,
          size: 50,
          keyword: kw || undefined,
          domainId: dId || undefined,
          enabledOnly: true,
        });
        const content = Array.isArray(resp?.content) ? resp.content : [];
        setOptions(content.map((d: any) => ({
          id: String(d.id || ""),
          name: String(d.name || ""),
          warehouseLayer: d.warehouseLayer,
          domainId: d.domainId,
          domain: d.domain,
        })));
      } catch { /* global interceptor handles */ } finally {
        setLoading(false);
      }
    }, []);

    useEffect(() => {
      void loadDatasets(keyword, domainId);
    }, [keyword, domainId, loadDatasets]);

    const handleChange = async (datasetId: string | undefined) => {
      onChange?.(datasetId);
      if (datasetId && onFieldsLoaded) {
        setFieldsLoading(true);
        try {
          const fields = await getDatasetFields(datasetId);
          onFieldsLoaded(fields);
        } catch { /* global interceptor handles */ } finally {
          setFieldsLoading(false);
        }
      }
    };

    return (
      <Space direction="vertical" style={{ width: "100%", ...style }}>
        <Space wrap>
          <Select
            placeholder="筛选主题域"
            allowClear
            style={{ width: 160 }}
            options={domains.map((d) => ({ label: d.name, value: d.id }))}
            onChange={setDomainId}
          />
          <Select
            showSearch
            style={{ minWidth: 300 }}
            placeholder={placeholder ?? "选择数据集"}
            filterOption={false}
            loading={loading || fieldsLoading}
            value={value}
            onChange={handleChange}
            onSearch={(v) => setKeyword(v)}
            allowClear
            options={options.map((d) => ({
              label: (
                <Space size={4}>
                  <span>{d.name}</span>
                  {d.warehouseLayer && <Tag color={LAYER_COLOR[d.warehouseLayer] ?? "processing"}>{d.warehouseLayer}</Tag>}
                </Space>
              ),
              value: d.id,
            }))}
          />
        </Space>
      </Space>
    );
  }
  ```

- [ ] **Step 3: 确认 `listDatasets` 在 platformApi 支持传入 domainId 参数**

  ```typescript
  // 在 platformApi.ts 中找到 listDatasets 函数定义
  // 确认其 params 包含 domainId?: string 或 domain?: string
  // 如果不支持，添加 domainId 参数传递
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add source/dts-platform-webapp/src/components/catalog/DatasetPicker.tsx \
          source/dts-platform-webapp/src/api/platformApi.ts
  git commit -m "feat(catalog/F2): add DatasetPicker reusable component"
  ```

---

### Task 5: 前端 — 指标创建接入 DatasetPicker

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/governance/IndicatorsPage.tsx`（或 `IndicatorWizard.tsx`，读完再定）

**背景：** `GovIndicatorDefinition.datasetId` 是 `String`（自由文本），`sourceTable` 也是自由文本。需要在创建/编辑指标的表单中，将 sourceTable 改为 DatasetPicker，选中后 datasetId 存数据集的 UUID（字符串形式），dimensionFields 候选来自 onFieldsLoaded 回调。

- [ ] **Step 1: 读取 IndicatorsPage.tsx / IndicatorWizard.tsx 中的指标创建表单**

  找到 sourceTable 和 datasetId 的 Form.Item 位置，确认表单结构。

- [ ] **Step 2: 替换 sourceTable 输入为 DatasetPicker**

  找到类似 `<Form.Item name="sourceTable">` 的位置，替换为：

  ```tsx
  import { DatasetPicker } from "@/components/catalog/DatasetPicker";
  import type { DatasetField } from "@/api/platformApi";

  // 在组件 state 中添加
  const [datasetFields, setDatasetFields] = useState<DatasetField[]>([]);

  // Form.Item 替换
  <Form.Item label="来源数据集" name="datasetId">
    <DatasetPicker
      onFieldsLoaded={(fields) => setDatasetFields(fields)}
      placeholder="选择来源数据集"
    />
  </Form.Item>
  ```

- [ ] **Step 3: dimensionFields 使用字段候选**

  找到 dimensionFields 的输入控件，改为 Select（多选，候选来自 datasetFields）：

  ```tsx
  <Form.Item label="维度字段" name="dimensionFields">
    <Select
      mode="multiple"
      placeholder="选择维度字段（可手动输入）"
      options={datasetFields.map((f) => ({ label: `${f.name} (${f.dataType})`, value: f.name }))}
      allowClear
    />
  </Form.Item>
  ```

- [ ] **Step 4: 确认保存逻辑兼容**

  确认 `datasetId` 字段在 POST/PUT 请求 payload 中会被发送（String UUID 格式），`sourceTable` 字段如仍需保留则从选中数据集的 name/hiveTable 中获取。

- [ ] **Step 5: Commit**

  ```bash
  git add source/dts-platform-webapp/src/pages/governance/IndicatorsPage.tsx
  # 如果在 IndicatorWizard.tsx 中，则 add 对应文件
  git commit -m "feat(catalog/F2): replace sourceTable free-text with DatasetPicker in indicator form"
  ```

---

## P1 阶段：血缘与可观测性

### Task 6: 后端 — dbt 血缘导入服务

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogDbtLineageService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDatasetResource.java`

**背景：**
- `CatalogDatasetLineage` 实体字段：`upstreamDatasetId`, `downstreamDatasetId`, `relationType`, `notes`, `upstreamAssetType`, `downstreamAssetType`, `direction`
- dbt manifest.json 结构：`nodes` Map（key = `model.project.model_name`），每个 node 有 `depends_on.nodes` 数组
- `CatalogDatasetLineageRepository` 存在，需确认是否有 `deleteByUpstreamAndDownstream` 等方法

- [ ] **Step 1: 读取 CatalogDatasetLineage 实体和 Repository 完整内容**

  确认字段类型（UUID 还是 String）和 Repository 已有方法。

- [ ] **Step 2: 创建 CatalogDbtLineageService**

  ```java
  package com.yuzhi.dts.platform.service.catalog;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
  import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
  import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
  import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;
  import org.springframework.web.multipart.MultipartFile;

  import java.io.IOException;
  import java.util.*;

  @Service
  public class CatalogDbtLineageService {

      private final CatalogDatasetRepository datasetRepo;
      private final CatalogDatasetLineageRepository lineageRepo;
      private final ObjectMapper objectMapper;

      public CatalogDbtLineageService(CatalogDatasetRepository datasetRepo,
                                       CatalogDatasetLineageRepository lineageRepo,
                                       ObjectMapper objectMapper) {
          this.datasetRepo = datasetRepo;
          this.lineageRepo = lineageRepo;
          this.objectMapper = objectMapper;
      }

      /**
       * 解析 dbt manifest.json，将模型依赖写入 catalog_dataset_lineage。
       * 通过 hiveTable 名称匹配现有数据集（大小写不敏感）。
       */
      @Transactional
      public Map<String, Object> importManifest(MultipartFile file) throws IOException {
          @SuppressWarnings("unchecked")
          Map<String, Object> manifest = objectMapper.readValue(file.getInputStream(), Map.class);
          @SuppressWarnings("unchecked")
          Map<String, Object> nodes = (Map<String, Object>) manifest.getOrDefault("nodes", Collections.emptyMap());

          // 预加载所有数据集，以 hiveTable 小写为索引
          Map<String, UUID> tableIndex = new HashMap<>();
          for (CatalogDataset ds : datasetRepo.findAll()) {
              if (ds.getHiveTable() != null) {
                  tableIndex.put(ds.getHiveTable().toLowerCase(), ds.getId());
              }
          }

          int created = 0;
          int skipped = 0;

          for (Map.Entry<String, Object> entry : nodes.entrySet()) {
              String nodeKey = entry.getKey(); // e.g. "model.my_project.dim_user"
              if (!nodeKey.startsWith("model.")) continue;

              @SuppressWarnings("unchecked")
              Map<String, Object> node = (Map<String, Object>) entry.getValue();
              String modelName = String.valueOf(node.getOrDefault("name", ""));

              @SuppressWarnings("unchecked")
              Map<String, Object> dependsOn = (Map<String, Object>) node.getOrDefault("depends_on", Collections.emptyMap());
              @SuppressWarnings("unchecked")
              List<String> parentKeys = (List<String>) dependsOn.getOrDefault("nodes", Collections.emptyList());

              UUID downstreamId = tableIndex.get(modelName.toLowerCase());
              if (downstreamId == null) { skipped++; continue; }

              for (String parentKey : parentKeys) {
                  String parentName = parentKey.contains(".") ? parentKey.substring(parentKey.lastIndexOf('.') + 1) : parentKey;
                  UUID upstreamId = tableIndex.get(parentName.toLowerCase());
                  if (upstreamId == null) continue;

                  // 避免重复插入
                  if (!lineageRepo.existsByUpstreamDatasetIdAndDownstreamDatasetId(upstreamId, downstreamId)) {
                      CatalogDatasetLineage lineage = new CatalogDatasetLineage();
                      lineage.setUpstreamDatasetId(upstreamId);
                      lineage.setDownstreamDatasetId(downstreamId);
                      lineage.setRelationType("DBT_MODEL");
                      lineage.setDirection("DOWNSTREAM");
                      lineage.setNotes("Imported from dbt manifest");
                      lineageRepo.save(lineage);
                      created++;
                  }
              }
          }

          return Map.of("created", created, "skipped", skipped, "total", nodes.size());
      }
  }
  ```

- [ ] **Step 3: 在 CatalogDatasetLineageRepository 添加 existsByUpstreamAndDownstream**

  ```java
  boolean existsByUpstreamDatasetIdAndDownstreamDatasetId(UUID upstreamDatasetId, UUID downstreamDatasetId);
  ```

- [ ] **Step 4: 在 CatalogDatasetResource 添加导入端点**

  在 `CatalogDatasetResource` 中注入 `CatalogDbtLineageService`，添加：

  ```java
  @PostMapping("/lineage/import-dbt-manifest")
  @Transactional
  @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
  public ApiResponse<Map<String, Object>> importDbtManifest(
      @RequestParam("file") org.springframework.web.multipart.MultipartFile file
  ) throws java.io.IOException {
      Map<String, Object> result = dbtLineageService.importManifest(file);
      audit.audit("CREATE", "catalog.lineage.dbt-import", "file=" + file.getOriginalFilename());
      return ApiResponses.ok(result);
  }
  ```

- [ ] **Step 5: 验证编译**

  ```bash
  cd source/dts-platform && mvn compile -q 2>&1 | tail -20
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogDbtLineageService.java \
          source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/catalog/CatalogDatasetLineageRepository.java \
          source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDatasetResource.java
  git commit -m "feat(catalog/F3): add dbt manifest lineage import service and endpoint"
  ```

---

### Task 7: 前端 — LineagePage 安装 @xyflow/react + 图组件

**Files:**
- Modify: `source/dts-platform-webapp/package.json`
- Modify: `source/dts-platform-webapp/src/pages/catalog/LineagePage.tsx`
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`

**背景：** `LineagePage` 目前调用 `getCatalogLineageImpact` 返回 `nodes` 和 `edges`，以表格展示。需要新增图 Tab，并添加 dbt 导入按钮。项目已离线部署，需要安装 `@xyflow/react` 到 node_modules。

- [ ] **Step 1: 安装 @xyflow/react**

  ```bash
  cd source/dts-platform-webapp && npm install @xyflow/react
  ```

  Expected: 安装成功，package.json 中出现 `@xyflow/react`。

- [ ] **Step 2: 在 platformApi.ts 添加 dbt 导入 API**

  ```typescript
  export const importDbtManifest = (file: File): Promise<{ created: number; skipped: number; total: number }> => {
    const form = new FormData();
    form.append("file", file);
    return api.post("/api/catalog/lineage/import-dbt-manifest", form, {
      headers: { "Content-Type": "multipart/form-data" },
    }).then((r) => r.data?.data);
  };
  ```

- [ ] **Step 3: 在 LineagePage 添加 dbt 导入按钮**

  在 LineagePage 顶部操作区添加：

  ```tsx
  import { importDbtManifest } from "@/api/platformApi";
  import { Upload } from "antd";
  import type { UploadProps } from "antd";

  const dbtUploadProps: UploadProps = {
    accept: ".json",
    showUploadList: false,
    beforeUpload: async (file) => {
      try {
        const result = await importDbtManifest(file);
        toast.success(`dbt 血缘导入成功：新建 ${result.created} 条，跳过 ${result.skipped} 条`);
        // 刷新当前数据集的血缘视图
        if (selectedId) {
          void loadImpact(selectedId, direction, depth, projectName, layerFilters, changedWithinHours);
        }
      } catch {
        toast.error("dbt manifest 导入失败");
      }
      return false; // 阻止 antd 默认上传行为
    },
  };

  // 在现有筛选控件旁边添加
  <Upload {...dbtUploadProps}>
    <Button icon={<UploadOutlined />}>导入 dbt 血缘</Button>
  </Upload>
  ```

- [ ] **Step 4: 添加图 Tab，在现有表格视图上方切换**

  在 LineagePage return 语句中，用 `antd Tabs` 包裹现有内容，新增「图视图」Tab：

  ```tsx
  import { ReactFlow, Background, Controls, type Node, type Edge } from "@xyflow/react";
  import "@xyflow/react/dist/style.css";

  // 将 impact.nodes / impact.edges 转换为 ReactFlow 格式
  const rfNodes: Node[] = useMemo(() => {
    if (!impact?.nodes) return [];
    const layerX: Record<string, number> = { ODS: 0, DWD: 250, DWS: 500, ADS: 750 };
    const layerCount: Record<string, number> = {};
    return impact.nodes.map((n) => {
      const layer = n.layer?.toUpperCase() ?? "UNKNOWN";
      const x = layerX[layer] ?? 900;
      layerCount[layer] = (layerCount[layer] ?? 0) + 1;
      const y = (layerCount[layer] - 1) * 80;
      return {
        id: n.id ?? Math.random().toString(36),
        position: { x, y },
        data: { label: n.name ?? n.table ?? "未知" },
        style: {
          background: { ODS: "#f5f5f5", DWD: "#e6f4ff", DWS: "#e6fffb", ADS: "#f6ffed" }[layer] ?? "#fff",
          border: "1px solid #d9d9d9",
          borderRadius: 6,
          fontSize: 11,
          padding: "4px 8px",
        },
      };
    });
  }, [impact?.nodes]);

  const rfEdges: Edge[] = useMemo(() =>
    (impact?.edges ?? []).map((e, i) => ({
      id: e.id ?? `e-${i}`,
      source: e.upstreamDatasetId ?? "",
      target: e.downstreamDatasetId ?? "",
      animated: false,
      style: { stroke: "#bfbfbf" },
    })),
    [impact?.edges]
  );

  // Tabs 结构
  <Tabs defaultActiveKey="table" items={[
    {
      key: "table",
      label: "影响分析表格",
      children: <>{/* 现有表格内容 */}</>
    },
    {
      key: "graph",
      label: "血缘图",
      children: (
        <div style={{ height: 500, border: "1px solid #e8e8e8", borderRadius: 8 }}>
          <ReactFlow nodes={rfNodes} edges={rfEdges} fitView>
            <Background />
            <Controls />
          </ReactFlow>
        </div>
      )
    }
  ]} />
  ```

- [ ] **Step 5: 验证前端编译**

  ```bash
  cd source/dts-platform-webapp && npm run build 2>&1 | tail -20
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add source/dts-platform-webapp/package.json \
          source/dts-platform-webapp/package-lock.json \
          source/dts-platform-webapp/src/pages/catalog/LineagePage.tsx \
          source/dts-platform-webapp/src/api/platformApi.ts
  git commit -m "feat(catalog/F3,F4): add dbt import button and @xyflow/react lineage graph to LineagePage"
  ```

---

### Task 8: 后端 — 指标依赖数据集查询端点

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDatasetResource.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/governance/GovIndicatorDefinitionRepository.java`（或等效路径）

**背景：** `GovIndicatorDefinition.datasetId` 是 `String`（自由文本），Task 5 之后新增的指标会存 UUID 字符串。此端点返回依赖指定数据集的指标列表。

- [ ] **Step 1: 找到 GovIndicatorDefinitionRepository 路径**

  ```bash
  find source/dts-platform/src/main/java -name "GovIndicatorDefinition*" | grep -i repo
  ```

- [ ] **Step 2: 在 Repository 添加 datasetId 查询**

  ```java
  List<GovIndicatorDefinition> findByDatasetId(String datasetId);
  ```

- [ ] **Step 3: 在 CatalogDatasetResource 添加 indicator-deps 端点**

  ```java
  @GetMapping("/datasets/{id}/indicator-deps")
  @Transactional(readOnly = true)
  public ApiResponse<List<Map<String, Object>>> getIndicatorDeps(@PathVariable UUID id) {
      CatalogDataset dataset = datasetRepo.findById(id).orElseThrow();
      List<GovIndicatorDefinition> indicators = indicatorRepo.findByDatasetId(id.toString());
      List<Map<String, Object>> result = indicators.stream().map(ind -> {
          Map<String, Object> m = new java.util.LinkedHashMap<>();
          m.put("id", ind.getId());
          m.put("name", ind.getName());
          m.put("code", ind.getCode());
          m.put("isDerived", ind.getIsDerived());
          m.put("status", ind.getStatus());
          return m;
      }).toList();
      return ApiResponses.ok(result);
  }
  ```

- [ ] **Step 4: 注入 GovIndicatorDefinitionRepository 到 CatalogDatasetResource**

  在构造器中添加注入（需要 import GovIndicatorDefinition 相关类）。

- [ ] **Step 5: 编译验证**

  ```bash
  cd source/dts-platform && mvn compile -q 2>&1 | tail -20
  ```

- [ ] **Step 6: Commit**

  ```bash
  git commit -m "feat(catalog/F5): add indicator-deps endpoint on dataset"
  ```

---

### Task 9: 前端 — AssetDetailPage 抽屉添加「关联指标」区块

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/catalog/AssetDetailPage.tsx`
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`

- [ ] **Step 1: 在 platformApi.ts 添加 indicator-deps 查询**

  ```typescript
  export type IndicatorDep = { id: string; name: string; code: string; isDerived: boolean; status: string };

  export const getDatasetIndicatorDeps = (datasetId: string): Promise<IndicatorDep[]> =>
    api.get(`/api/catalog/datasets/${datasetId}/indicator-deps`).then((r) => r.data?.data ?? []);
  ```

- [ ] **Step 2: 在 AssetDetailPage 打开详情时加载关联指标**

  找到 `detailOpen = true` 时触发的加载逻辑，添加：

  ```typescript
  const [indicatorDeps, setIndicatorDeps] = useState<IndicatorDep[]>([]);

  // 在 detailRow 变化时加载
  useEffect(() => {
    if (detailRow?.id) {
      void getDatasetIndicatorDeps(detailRow.id).then(setIndicatorDeps).catch(() => setIndicatorDeps([]));
    }
  }, [detailRow?.id]);
  ```

- [ ] **Step 3: 在详情抽屉 / Card 中新增「关联指标」区块**

  在现有详情 JSX 中，治理健康区块之后添加：

  ```tsx
  {indicatorDeps.length > 0 && (
    <div className="rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-4 mt-4">
      <div className="mb-3 text-sm font-semibold text-slate-900">关联指标（{indicatorDeps.length}）</div>
      <Space wrap>
        {indicatorDeps.map((ind) => (
          <Tag key={ind.id} color={ind.isDerived ? "purple" : "blue"}>
            {ind.name}（{ind.code}）
          </Tag>
        ))}
      </Space>
    </div>
  )}
  ```

- [ ] **Step 4: Commit**

  ```bash
  git commit -m "feat(catalog/F5): show indicator deps in asset detail panel"
  ```

---

### Task 10: 后端 — CatalogDataProduct 实体 + CRUD

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/catalog/CatalogDataProduct.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/catalog/CatalogDataProductRepository.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDataProductResource.java`
- Create: `source/dts-platform/src/main/resources/config/liquibase/changelog/20260405_10_catalog_data_product.xml`
- Modify: `source/dts-platform/src/main/resources/config/liquibase/master.xml`

- [ ] **Step 1: 读取一个现有 Catalog 实体作为模板**

  读取 `CatalogDataset.java` 确认 `AbstractAuditingEntity<UUID>` 的继承方式和注解风格。

- [ ] **Step 2: 创建 CatalogDataProduct 实体**

  ```java
  package com.yuzhi.dts.platform.domain.catalog;

  import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
  import jakarta.persistence.*;
  import java.io.Serializable;
  import java.util.UUID;

  @Entity
  @Table(name = "catalog_data_product")
  public class CatalogDataProduct extends AbstractAuditingEntity<UUID> implements Serializable {

      @Id
      @GeneratedValue
      @Column(name = "id", columnDefinition = "uuid")
      private UUID id;

      @Column(name = "name", length = 128, nullable = false)
      private String name;

      @Column(name = "code", length = 64, unique = true)
      private String code;

      @Column(name = "owner_dept", length = 64)
      private String ownerDept;

      @Column(name = "description", length = 2048)
      private String description;

      // JSONB arrays stored as text: ["uuid1","uuid2"]
      @Column(name = "dataset_ids", columnDefinition = "text")
      private String datasetIds;

      // indicator codes: ["pjm_prog_total_nodes", ...]
      @Column(name = "indicator_codes", columnDefinition = "text")
      private String indicatorCodes;

      // DRAFT / PUBLISHED / OFFLINE
      @Column(name = "status", length = 32)
      private String status = "DRAFT";

      @Override
      public UUID getId() { return id; }
      public void setId(UUID id) { this.id = id; }
      public String getName() { return name; }
      public void setName(String name) { this.name = name; }
      public String getCode() { return code; }
      public void setCode(String code) { this.code = code; }
      public String getOwnerDept() { return ownerDept; }
      public void setOwnerDept(String ownerDept) { this.ownerDept = ownerDept; }
      public String getDescription() { return description; }
      public void setDescription(String description) { this.description = description; }
      public String getDatasetIds() { return datasetIds; }
      public void setDatasetIds(String datasetIds) { this.datasetIds = datasetIds; }
      public String getIndicatorCodes() { return indicatorCodes; }
      public void setIndicatorCodes(String indicatorCodes) { this.indicatorCodes = indicatorCodes; }
      public String getStatus() { return status; }
      public void setStatus(String status) { this.status = status; }
  }
  ```

- [ ] **Step 3: 创建 Repository**

  ```java
  package com.yuzhi.dts.platform.repository.catalog;

  import com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct;
  import org.springframework.data.jpa.repository.JpaRepository;
  import java.util.UUID;

  public interface CatalogDataProductRepository extends JpaRepository<CatalogDataProduct, UUID> {
      boolean existsByCode(String code);
  }
  ```

- [ ] **Step 4: 创建 Liquibase changelog**

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                                         http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="20260405_10_1" author="system">
      <createTable tableName="catalog_data_product">
        <column name="id" type="uuid"><constraints primaryKey="true" nullable="false"/></column>
        <column name="name" type="varchar(128)"><constraints nullable="false"/></column>
        <column name="code" type="varchar(64)"><constraints unique="true"/></column>
        <column name="owner_dept" type="varchar(64)"/>
        <column name="description" type="varchar(2048)"/>
        <column name="dataset_ids" type="text"/>
        <column name="indicator_codes" type="text"/>
        <column name="status" type="varchar(32)" defaultValue="DRAFT"/>
        <column name="created_by" type="varchar(50)"/>
        <column name="created_date" type="timestamp"/>
        <column name="last_modified_by" type="varchar(50)"/>
        <column name="last_modified_date" type="timestamp"/>
      </createTable>
    </changeSet>

    <!-- Reserved: row_security_policy / sla_refresh_cron on catalog_dataset -->
    <changeSet id="20260405_10_2" author="system">
      <addColumn tableName="catalog_dataset">
        <column name="row_security_policy" type="varchar(256)"/>
        <column name="sla_refresh_cron" type="varchar(128)"/>
      </addColumn>
    </changeSet>

    <!-- Reserved: column-level lineage on catalog_dataset_lineage -->
    <changeSet id="20260405_10_3" author="system">
      <addColumn tableName="catalog_dataset_lineage">
        <column name="source_column" type="varchar(128)"/>
        <column name="target_column" type="varchar(128)"/>
      </addColumn>
    </changeSet>

  </databaseChangeLog>
  ```

- [ ] **Step 5: 在 master.xml 引入 changelog**

  在 master.xml 末尾 `</databaseChangeLog>` 之前添加：

  ```xml
  <include file="config/liquibase/changelog/20260405_10_catalog_data_product.xml" relativeToChangelogFile="false"/>
  ```

- [ ] **Step 6: 创建 CatalogDataProductResource**

  ```java
  package com.yuzhi.dts.platform.web.rest.catalog;

  import com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct;
  import com.yuzhi.dts.platform.repository.catalog.CatalogDataProductRepository;
  import com.yuzhi.dts.platform.service.audit.AuditService;
  import com.yuzhi.dts.platform.web.rest.ApiResponse;
  import com.yuzhi.dts.platform.web.rest.ApiResponses;
  import org.springframework.data.domain.*;
  import org.springframework.security.access.prepost.PreAuthorize;
  import org.springframework.transaction.annotation.Transactional;
  import org.springframework.web.bind.annotation.*;

  import java.util.*;

  import static com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper.CATALOG_MAINTAINER_EXPRESSION;

  @RestController
  @RequestMapping("/api/catalog")
  public class CatalogDataProductResource {

      private final CatalogDataProductRepository repo;
      private final AuditService audit;

      public CatalogDataProductResource(CatalogDataProductRepository repo, AuditService audit) {
          this.repo = repo;
          this.audit = audit;
      }

      @GetMapping("/data-products")
      @Transactional(readOnly = true)
      public ApiResponse<Map<String, Object>> list(
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "20") int size
      ) {
          Page<CatalogDataProduct> p = repo.findAll(PageRequest.of(page, size, Sort.by("createdDate").descending()));
          return ApiResponses.ok(Map.of("content", p.getContent(), "total", p.getTotalElements()));
      }

      @GetMapping("/data-products/{id}")
      @Transactional(readOnly = true)
      public ApiResponse<CatalogDataProduct> get(@PathVariable UUID id) {
          return ApiResponses.ok(repo.findById(id).orElseThrow());
      }

      @PostMapping("/data-products")
      @Transactional
      @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
      public ApiResponse<CatalogDataProduct> create(@RequestBody CatalogDataProduct body) {
          CatalogDataProduct saved = repo.save(body);
          audit.audit("CREATE", "catalog.data-product", saved.getId().toString());
          return ApiResponses.ok(saved);
      }

      @PutMapping("/data-products/{id}")
      @Transactional
      @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
      public ApiResponse<CatalogDataProduct> update(@PathVariable UUID id, @RequestBody CatalogDataProduct body) {
          CatalogDataProduct existing = repo.findById(id).orElseThrow();
          existing.setName(body.getName());
          existing.setCode(body.getCode());
          existing.setOwnerDept(body.getOwnerDept());
          existing.setDescription(body.getDescription());
          existing.setDatasetIds(body.getDatasetIds());
          existing.setIndicatorCodes(body.getIndicatorCodes());
          existing.setStatus(body.getStatus());
          CatalogDataProduct saved = repo.save(existing);
          audit.audit("UPDATE", "catalog.data-product", id.toString());
          return ApiResponses.ok(saved);
      }

      @DeleteMapping("/data-products/{id}")
      @Transactional
      @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
      public ApiResponse<Boolean> delete(@PathVariable UUID id) {
          repo.deleteById(id);
          audit.audit("DELETE", "catalog.data-product", id.toString());
          return ApiResponses.ok(Boolean.TRUE);
      }
  }
  ```

- [ ] **Step 7: 编译验证**

  ```bash
  cd source/dts-platform && mvn compile -q 2>&1 | tail -20
  ```

- [ ] **Step 8: Commit**

  ```bash
  git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/catalog/CatalogDataProduct.java \
          source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/catalog/CatalogDataProductRepository.java \
          source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDataProductResource.java \
          source/dts-platform/src/main/resources/config/liquibase/changelog/20260405_10_catalog_data_product.xml \
          source/dts-platform/src/main/resources/config/liquibase/master.xml
  git commit -m "feat(catalog/F6): add CatalogDataProduct entity, repository, CRUD API, and reserved column migrations"
  ```

---

## P_UX 阶段：前端页面重构

### Task 11: 前端 — DatasetsPage 门户化改造

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/catalog/DatasetsPage.tsx`

**背景：** 当前是横向筛选栏（顶部）+ 列表 + reconciliation 卡片。改造为「左侧域树（Sider）+ 右侧卡片网格」，reconciliation 区块折叠。

- [ ] **Step 1: 读取 DatasetsPage.tsx 完整内容（200 行之后部分）**

  了解 reconciliation JSX 位置和列表渲染结构。

- [ ] **Step 2: 引入 antd Layout（Sider + Content）**

  在 imports 中确认 `Layout` 已引入（参考 SubjectAreasPage.tsx）。

- [ ] **Step 3: 添加域树状态和加载逻辑**

  ```typescript
  type DomainNode = { id?: string; name?: string; code?: string; children?: DomainNode[] };
  const [domainTree, setDomainTree] = useState<DomainNode[]>([]);
  const [treeLoading, setTreeLoading] = useState(false);

  // 引入 getDomainTree（已在 platformApi.ts 中存在）
  useEffect(() => {
    void (async () => {
      setTreeLoading(true);
      try {
        const tree = (await getDomainTree()) as DomainNode[];
        setDomainTree(Array.isArray(tree) ? tree : []);
      } catch { /* global interceptor */ } finally {
        setTreeLoading(false);
      }
    })();
  }, []);
  ```

- [ ] **Step 4: 将资产列表改为卡片网格**

  将现有 `<Table>` 或列表替换为：

  ```tsx
  <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
    {records.map((row) => (
      <div
        key={row.id}
        className="cursor-pointer rounded-[20px] border border-slate-200 bg-white px-4 py-3 hover:border-blue-300 hover:shadow-sm transition-all"
        onClick={() => router.push(`/catalog/asset-detail?id=${row.id}`)}
      >
        <div className="flex items-start justify-between gap-2">
          <div className="font-semibold text-slate-900 text-sm truncate">{row.name}</div>
          <Tag color={row.warehouseLayer === "ODS" ? "default" : row.warehouseLayer === "DWD" ? "blue" : row.warehouseLayer === "DWS" ? "cyan" : "green"}>
            {row.warehouseLayer ?? "未分层"}
          </Tag>
        </div>
        <div className="mt-1 text-xs text-slate-500">{row.domain ?? "未归域"}</div>
        <div className="mt-2 flex flex-wrap gap-1">
          {row.classification && <Tag color="orange" style={{ fontSize: 11 }}>{CLASSIFICATION_LABEL[row.classification] ?? row.classification}</Tag>}
          <Tag style={{ fontSize: 11 }}>{row.type ?? "未知"}</Tag>
        </div>
      </div>
    ))}
  </div>
  ```

- [ ] **Step 5: 域树点击时过滤列表**

  ```tsx
  <Layout>
    <Sider width={240} theme="light" className="border-r border-slate-200 p-3">
      <Tree
        showLine
        defaultExpandAll
        treeData={[
          { key: "ALL", title: "全部资产", children: buildTreeNodes(domainTree) },
        ]}
        onSelect={(keys) => {
          const selected = String(keys?.[0] ?? "ALL");
          setDomain(selected === "ALL" ? undefined : selected);
        }}
      />
    </Sider>
    <Content className="p-4 overflow-auto">{/* 筛选栏 + 卡片网格 */}</Content>
  </Layout>
  ```

  （`buildTreeNodes` 是将 DomainNode[] 转为 antd DataNode[] 的工具函数，参考 SubjectAreasPage.tsx 中已有的 `toTreeNodes`）

- [ ] **Step 6: reconciliation 区块改为折叠面板**

  将现有 reconciliation Card 包裹在 `<Collapse>` 中，`defaultActiveKey={[]}` 默认折叠。

- [ ] **Step 7: Commit**

  ```bash
  git commit -m "feat(catalog/F7): redesign DatasetsPage with left domain tree and asset card grid"
  ```

---

### Task 12: 前端 — AssetDetailPage 改为 Tab 独立详情页

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/catalog/AssetDetailPage.tsx`
- Modify: `source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx`

**背景：** 当前 `/catalog/asset-detail` 是列表+抽屉混合页，没有 `:id` 路由参数。需要支持 `/catalog/datasets/:id` 独立 URL 的 Tab 详情页（概览/字段/血缘/治理健康/权限）。

**策略：** 保留原 `AssetDetailPage` 逻辑（列表+抽屉）不动，新建 `DatasetDetailPage.tsx` 作为独立 Tab 详情页，注册新路由 `/catalog/datasets/:id`。

- [ ] **Step 1: 读取 AssetDetailPage.tsx 200~400 行**

  了解抽屉内的 Detail 渲染逻辑，作为新页面的内容参考。

- [ ] **Step 2: 创建 DatasetDetailPage.tsx**

  ```tsx
  // source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.tsx
  import { useEffect, useState } from "react";
  import { useParams } from "react-router";
  import { Tabs, Spin, Button } from "antd";
  import { getDataset, getDatasetFields, getDatasetGovernanceHealth, getCatalogLineageImpact } from "@/api/platformApi";
  import { useRouter } from "@/routes/hooks";

  // 概览 Tab 内容（复用 AssetDetailPage 中的 profileForm 逻辑）
  // 字段 Tab 内容（调用 getDatasetFields，展示字段表格）
  // 血缘 Tab 内容（嵌入 @xyflow/react 小型血缘图）
  // 治理健康 Tab（复用 governanceHealth 逻辑）
  // 权限申请 Tab（占位，显示"申请权限"按钮）

  export default function DatasetDetailPage() {
    const { id } = useParams<{ id: string }>();
    const router = useRouter();
    const [dataset, setDataset] = useState<Record<string, any> | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
      if (!id) return;
      setLoading(true);
      void getDataset(id)
        .then((d: any) => setDataset(d))
        .catch(() => { /* global interceptor */ })
        .finally(() => setLoading(false));
    }, [id]);

    if (loading) return <div className="flex items-center justify-center h-64"><Spin /></div>;
    if (!dataset) return <div className="p-8 text-slate-500">数据集不存在或无权限。</div>;

    return (
      <div className="p-4 space-y-4">
        <div className="flex items-center gap-3">
          <Button type="text" onClick={() => router.back()}>← 返回</Button>
          <h2 className="text-lg font-bold">{dataset.name}</h2>
        </div>
        <Tabs
          defaultActiveKey="overview"
          items={[
            { key: "overview", label: "概览", children: <DatasetOverviewTab dataset={dataset} onUpdate={() => { if (id) void getDataset(id).then(setDataset); }} /> },
            { key: "fields", label: "字段详情", children: <DatasetFieldsTab datasetId={id!} /> },
            { key: "lineage", label: "血缘图", children: <DatasetLineageTab datasetId={id!} /> },
            { key: "governance", label: "治理健康", children: <DatasetGovernanceTab datasetId={id!} /> },
            { key: "access", label: "权限申请", children: <div className="py-4 text-slate-500 text-sm">权限申请功能将在后续版本开放，请联系数据管理员。</div> },
          ]}
        />
      </div>
    );
  }

  // 各 Tab 子组件按需实现，从 AssetDetailPage 抽取逻辑
  function DatasetOverviewTab({ dataset, onUpdate }: { dataset: Record<string, any>; onUpdate: () => void }) {
    return (
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div><span className="text-slate-500">仓库分层：</span>{dataset.warehouseLayer ?? "-"}</div>
          <div><span className="text-slate-500">密级：</span>{dataset.classification ?? "-"}</div>
          <div><span className="text-slate-500">负责人：</span>{dataset.owner ?? "-"}</div>
          <div><span className="text-slate-500">所属部门：</span>{dataset.ownerDept ?? "-"}</div>
          <div><span className="text-slate-500">类型：</span>{dataset.type ?? "-"}</div>
          <div><span className="text-slate-500">生命周期状态：</span>{dataset.lifecycleStatus ?? "-"}</div>
        </div>
        {dataset.description && <div className="text-sm text-slate-600">{dataset.description}</div>}
      </div>
    );
  }

  function DatasetFieldsTab({ datasetId }: { datasetId: string }) {
    const [fields, setFields] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);
    useEffect(() => {
      void getDatasetFields(datasetId).then(setFields).catch(() => setFields([])).finally(() => setLoading(false));
    }, [datasetId]);
    if (loading) return <Spin />;
    if (!fields.length) return <div className="py-4 text-slate-500 text-sm">暂无字段信息（未同步或数据集无表结构）。</div>;
    return (
      <div className="overflow-auto">
        <table className="w-full text-sm border-collapse">
          <thead><tr className="bg-slate-50"><th className="border border-slate-200 px-3 py-2 text-left">字段名</th><th className="border border-slate-200 px-3 py-2">类型</th><th className="border border-slate-200 px-3 py-2">描述</th></tr></thead>
          <tbody>{fields.map((f, i) => (<tr key={i} className={i % 2 === 0 ? "" : "bg-slate-50"}><td className="border border-slate-200 px-3 py-1 font-mono">{f.name}</td><td className="border border-slate-200 px-3 py-1 text-center">{f.dataType}</td><td className="border border-slate-200 px-3 py-1">{f.comment ?? "-"}</td></tr>))}</tbody>
        </table>
      </div>
    );
  }

  function DatasetLineageTab({ datasetId }: { datasetId: string }) {
    // 嵌入 mini 血缘图，复用 ReactFlow 逻辑（参考 Task 7）
    return <div className="text-slate-500 text-sm py-4">血缘图正在加载... <a href={`/catalog/lineage?id=${datasetId}`} className="text-blue-600">查看完整血缘 →</a></div>;
  }

  function DatasetGovernanceTab({ datasetId }: { datasetId: string }) {
    return <div className="text-slate-500 text-sm py-4">治理健康数据（从 AssetDetailPage 中抽取 governanceHealth 渲染逻辑）</div>;
  }
  ```

- [ ] **Step 3: 注册新路由**

  在 `dynamic-resolver.tsx` 中添加：

  ```typescript
  "/catalog/datasets/:id": "/pages/catalog/DatasetDetailPage",
  ```

  在 `backend.tsx` 路由中，确认 `catalog` 的通配路由能匹配 `/catalog/datasets/:id`（当前是 `{ path: "*", element: <DynamicMenuResolver> }` 应该能匹配）。

- [ ] **Step 4: DataSearchPage 的点击跳转改为新路由**

  在 `DataSearchPage.tsx` 中，搜索结果数据集行点击时改为：

  ```typescript
  router.push(`/catalog/datasets/${row.id}`);
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.tsx \
          source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx \
          source/dts-platform-webapp/src/pages/catalog/DataSearchPage.tsx
  git commit -m "feat(catalog/F8,F10): add DatasetDetailPage with Tab layout and update search click routing"
  ```

---

### Task 13: 前端 — DataSearchPage 搜索结果 Tab 化 + 卡片化

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/catalog/DataSearchPage.tsx`

**背景：** 已有 DATASET/TABLE/COLUMN 分类搜索，`searchCatalog` API 不变，只改结果展示方式。

- [ ] **Step 1: 读取 DataSearchPage.tsx 当前结果渲染部分**

  了解 `results` 类型结构（`assetKind`：DATASET/TABLE/COLUMN）。

- [ ] **Step 2: 按 assetKind 分组结果**

  ```typescript
  const grouped = useMemo(() => ({
    DATASET: results.filter((r) => r.assetKind === "DATASET"),
    TABLE: results.filter((r) => r.assetKind === "TABLE"),
    COLUMN: results.filter((r) => r.assetKind === "COLUMN"),
  }), [results]);
  ```

- [ ] **Step 3: 将表格改为 Tabs + 卡片**

  ```tsx
  <Tabs
    items={[
      {
        key: "DATASET",
        label: `数据集 (${grouped.DATASET.length})`,
        children: (
          <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
            {grouped.DATASET.map((row) => (
              <div
                key={row.id}
                className="cursor-pointer rounded-[18px] border border-slate-200 bg-white px-4 py-3 hover:border-blue-300 hover:shadow-sm"
                onClick={() => router.push(`/catalog/datasets/${row.id}`)}
              >
                <div className="font-semibold text-sm">{row.name}</div>
                <div className="text-xs text-slate-500 mt-1">{row.domain ?? "未归域"}</div>
              </div>
            ))}
            {!grouped.DATASET.length && searched && <div className="text-slate-400 text-sm py-4">无匹配数据集</div>}
          </div>
        ),
      },
      {
        key: "TABLE",
        label: `表 (${grouped.TABLE.length})`,
        children: (
          <div className="space-y-2">
            {grouped.TABLE.map((row) => (
              <div key={row.id} className="border border-slate-200 rounded-[14px] px-3 py-2 text-sm">
                <span className="font-medium">{row.name}</span>
                <span className="text-slate-400 ml-2">in {row.datasetName ?? "-"}</span>
              </div>
            ))}
          </div>
        ),
      },
      {
        key: "COLUMN",
        label: `字段 (${grouped.COLUMN.length})`,
        children: (
          <div className="space-y-2">
            {grouped.COLUMN.map((row) => (
              <div key={row.id} className="border border-slate-200 rounded-[14px] px-3 py-2 text-sm">
                <span className="font-mono">{row.name}</span>
                <span className="text-slate-400 ml-2">in {row.datasetName ?? "-"}</span>
              </div>
            ))}
          </div>
        ),
      },
    ]}
  />
  ```

- [ ] **Step 4: Commit**

  ```bash
  git commit -m "feat(catalog/F10): tabbed and card-based search results in DataSearchPage"
  ```

---

---

## Remaining Work（代码审查后补充）

### Task 14: 前端 — IndicatorWizard 接入 DatasetPicker（P0）

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/governance/components/IndicatorWizard.tsx`

**背景：** IndicatorWizard Step 1「绑定源表」仍调用 `listOdsTables()` + 普通 Select（line 321-333）。DatasetPicker 已存在于 `src/components/catalog/DatasetPicker.tsx`，通过 `onFieldsLoaded` 回调返回 `DatasetField[]`。

- [ ] **Step 1: 读取 IndicatorWizard.tsx 中的 renderStep1 + state 区域（lines 50-175）**

- [ ] **Step 2: 替换 listOdsTables 为 DatasetPicker**

  删除：
  - `import { listOdsTables, listOdsColumns, ... }` 中的 `listOdsTables, listOdsColumns`
  - `const [tables, setTables] = useState<any[]>([]);` 状态
  - `const [columns, setColumns] = useState<any[]>([]);` 状态
  - `fetchTables()` 函数
  - `fetchColumns(tableName)` 函数
  - `useEffect(() => { void fetchTables(); }, [])` 调用

  添加：
  ```typescript
  import { DatasetPicker } from "@/components/catalog/DatasetPicker";
  import type { DatasetField } from "@/api/platformApi";

  // state 区域
  const [pickerFields, setPickerFields] = useState<DatasetField[]>([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState<string | undefined>();
  ```

- [ ] **Step 3: 修改 handleTableChange 为 handleDatasetChange**

  ```typescript
  const handleDatasetChange = (datasetId: string | undefined, fields: DatasetField[]) => {
    setSelectedDatasetId(datasetId);
    setFieldMapping({});
    setPickerFields(fields);
    // 取第一个字段的 tableName 作为 sourceTable，格式 "db.tableName"
    const tableName = fields[0]?.tableName ?? "";
    setSelectedTable(tableName);
  };
  ```

- [ ] **Step 4: 在 renderStep1 中替换 Select 为 DatasetPicker**

  ```tsx
  const renderStep1 = () => (
    <div>
      <Typography.Text strong style={{ display: "block", marginBottom: 12 }}>
        绑定数据集
      </Typography.Text>
      <DatasetPicker
        value={selectedDatasetId}
        onChange={(id) => {/* handled by onFieldsLoaded */}}
        onFieldsLoaded={(fields) => handleDatasetChange(selectedDatasetId, fields)}
        placeholder="选择数据集（支持域筛选和关键字搜索）"
        style={{ width: "100%", marginBottom: 16 }}
      />
  ```

  注意：`DatasetPicker` 的 `onChange` 先触发，`onFieldsLoaded` 异步后触发。需要同步两个回调：

  ```tsx
  <DatasetPicker
    value={selectedDatasetId}
    onChange={(id) => {
      setSelectedDatasetId(id);
      if (!id) {
        setSelectedTable("");
        setPickerFields([]);
        setFieldMapping({});
      }
    }}
    onFieldsLoaded={(fields) => {
      setPickerFields(fields);
      setFieldMapping({});
      const tableName = fields[0]?.tableName ?? "";
      setSelectedTable(tableName);
    }}
    placeholder="选择数据集（支持域筛选和关键字搜索）"
    style={{ width: "100%", marginBottom: 16 }}
  />
  ```

- [ ] **Step 5: 将 columns 来源改为 pickerFields**

  字段映射表中将 `columns` 替换为 `pickerFields`。找到 `mappingColumns` 和 `mappingData` 的计算，将原来依赖 `columns` 的地方改为 `pickerFields.map(f => f.name)`：

  ```typescript
  const mappingData = requiredFields.map((rf) => ({
    field: rf,
    columnOptions: pickerFields.map((f) => ({ label: f.name, value: f.name })),
  }));
  ```

- [ ] **Step 6: 确认 canNext 逻辑仍有效**

  Step 1 的 canNext 条件是 `!!selectedTable`。`selectedTable` 在 `onFieldsLoaded` 回调中被赋值（从 `fields[0]?.tableName`）。如果数据集没有字段记录则 `selectedTable` 为空字符串，需要改为：

  ```typescript
  if (step === 1) return !!selectedDatasetId;
  ```

- [ ] **Step 7: Commit**

  ```bash
  git add source/dts-platform-webapp/src/pages/governance/components/IndicatorWizard.tsx
  git commit -m "feat(catalog/F2): replace listOdsTables with DatasetPicker in IndicatorWizard"
  ```

---

### Task 15: 前端 — DatasetDetailPage 治理 Tab 添加关联指标（P1）

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.tsx`

**背景：** `DatasetGovernanceTab` 只显示健康分和 quality 统计，没有关联指标列表。`getDatasetIndicatorDeps(datasetId)` 已在 `platformApi.ts` 第 22 行。

- [ ] **Step 1: 读取 DatasetGovernanceTab 函数（lines 146-179）**

- [ ] **Step 2: 扩展 DatasetGovernanceTab 加载关联指标**

  ```typescript
  function DatasetGovernanceTab({ datasetId }: { datasetId: string }) {
    const [health, setHealth] = useState<Record<string, any> | null>(null);
    const [loading, setLoading] = useState(true);
    const [indicators, setIndicators] = useState<any[]>([]);

    useEffect(() => {
      void Promise.all([
        getDatasetGovernanceHealth(datasetId)
          .then((h: any) => setHealth(h ?? null))
          .catch(() => setHealth(null)),
        getDatasetIndicatorDeps(datasetId)
          .then((r: any) => {
            const list = Array.isArray(r?.data?.data) ? r.data.data
              : Array.isArray(r?.data) ? r.data
              : [];
            setIndicators(list);
          })
          .catch(() => setIndicators([])),
      ]).finally(() => setLoading(false));
    }, [datasetId]);

    if (loading) return <div className="py-4"><Spin /></div>;

    // ... existing health score JSX ...

    return (
      <div className="space-y-4 py-2 text-sm">
        {/* 现有健康分区块保持不变 */}
        {score != null && ( ... )}
        {health?.quality?.totalRuns != null && ( ... )}

        {/* 新增关联指标区块 */}
        {indicators.length > 0 && (
          <div>
            <div className="mb-2 font-medium text-slate-700">关联指标（{indicators.length}）</div>
            <Table
              size="small"
              rowKey={(_, i) => String(i)}
              dataSource={indicators}
              pagination={false}
              columns={[
                { title: "指标名称", dataIndex: "name", render: (v) => v ?? "-" },
                { title: "类型", dataIndex: "type", width: 100, render: (v) => v ? <Tag>{v}</Tag> : "-" },
                { title: "状态", dataIndex: "status", width: 90, render: (v) => v ? <Tag color={v === "PUBLISHED" ? "green" : "default"}>{v}</Tag> : "-" },
              ]}
            />
          </div>
        )}
      </div>
    );
  }
  ```

  并在文件顶部 import 补上 `getDatasetIndicatorDeps` 和 `Table`（antd）。

- [ ] **Step 3: Commit**

  ```bash
  git add source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.tsx
  git commit -m "feat(catalog/F4): add indicator-deps section to DatasetDetailPage governance tab"
  ```

---

### Task 16: 前端 — DatasetDetailPage 血缘 Tab 内嵌迷你图（P_UX）

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.tsx`

**背景：** 血缘 Tab 目前只有一行跳转链接。LineagePage 已用 `@xyflow/react` 渲染图，可复用相同的 `rfNodes`/`rfEdges` 计算逻辑。高度固定 300px，深度限制为 2。

- [ ] **Step 1: 在 DatasetDetailPage 顶部补充导入**

  ```typescript
  import { ReactFlow, Background, Controls, type Node, type Edge } from "@xyflow/react";
  import "@xyflow/react/dist/style.css";
  import { getCatalogLineageImpact } from "@/api/platformApi";
  ```

- [ ] **Step 2: 新建 DatasetLineageMiniTab 子组件**

  在文件末尾（DatasetGovernanceTab 之后）添加：

  ```typescript
  const LAYER_BG: Record<string, string> = {
    ODS: "#f5f5f5", DWD: "#e6f4ff", DWS: "#e6fffb", ADS: "#f6ffed", DIM: "#f9f0ff",
  };

  function DatasetLineageMiniTab({ datasetId }: { datasetId: string }) {
    const [rfNodes, setRfNodes] = useState<Node[]>([]);
    const [rfEdges, setRfEdges] = useState<Edge[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
      void getCatalogLineageImpact(datasetId, { direction: "BOTH", depth: 2 })
        .then((resp: any) => {
          const nodes: any[] = Array.isArray(resp?.nodes) ? resp.nodes : [];
          const edges: any[] = Array.isArray(resp?.edges) ? resp.edges : [];
          const layerX: Record<string, number> = { ODS: 0, DWD: 250, DWS: 500, ADS: 750, DIM: 1000 };
          const layerCount: Record<string, number> = {};
          setRfNodes(nodes.map((n, idx) => {
            const layer = n.layer?.toUpperCase() ?? "UNKNOWN";
            const x = layerX[layer] ?? 1100;
            layerCount[layer] = (layerCount[layer] ?? 0) + 1;
            return {
              id: n.id ?? `node-${idx}`,
              position: { x, y: (layerCount[layer] - 1) * 80 },
              data: { label: n.name ?? n.table ?? "未知" },
              style: { background: LAYER_BG[layer] ?? "#fff", border: "1px solid #d9d9d9", borderRadius: 6, fontSize: 11, padding: "4px 8px" },
            };
          }));
          setRfEdges(edges
            .filter((e) => e.upstreamDatasetId && e.downstreamDatasetId)
            .map((e, i) => ({
              id: e.id ?? `e-${i}`,
              source: e.upstreamDatasetId,
              target: e.downstreamDatasetId,
              style: { stroke: "#bfbfbf" },
            }))
          );
        })
        .catch(() => {})
        .finally(() => setLoading(false));
    }, [datasetId]);

    if (loading) return <div className="py-4"><Spin /></div>;
    if (!rfNodes.length) return <div className="py-4 text-sm text-slate-500">暂无血缘数据。</div>;

    return (
      <div className="space-y-2 py-2">
        <div style={{ height: 300, border: "1px solid #e8e8e8", borderRadius: 8, overflow: "hidden" }}>
          <ReactFlow nodes={rfNodes} edges={rfEdges} fitView>
            <Background />
            <Controls />
          </ReactFlow>
        </div>
        <a href={`/catalog/lineage?selectedId=${datasetId}`} className="text-xs text-blue-600 underline">
          查看完整血缘 →
        </a>
      </div>
    );
  }
  ```

- [ ] **Step 3: 替换血缘 Tab 内容**

  找到 DatasetDetailPage 中：
  ```tsx
  {
    key: "lineage",
    label: "血缘图",
    children: (
      <div className="py-4 text-sm text-slate-500">
        <a href={`/catalog/lineage?selectedId=${id}`} className="text-blue-600 underline">
          查看完整血缘 →
        </a>
      </div>
    ),
  },
  ```

  替换为：
  ```tsx
  {
    key: "lineage",
    label: "血缘图",
    children: <DatasetLineageMiniTab datasetId={id!} />,
  },
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.tsx
  git commit -m "feat(catalog/F8): embed mini ReactFlow lineage graph in DatasetDetailPage lineage tab"
  ```

---

### Task 17: 前端 — RuleCreateWizard datasetIds 改为 DatasetPicker（P0）

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/governance/components/RuleCreateWizard.tsx`

**背景：** Step 2「绑定数据集」字段 `datasetIds` 使用普通 `<Select mode="multiple">`（line 413-429）。改为 `DatasetPicker` 单选，submit 时封装为数组。

- [ ] **Step 1: 读取 RuleCreateWizard renderStep2 函数（lines 408-429）及 submit 逻辑**

- [ ] **Step 2: 替换 Select 为 DatasetPicker**

  ```tsx
  import { DatasetPicker } from "@/components/catalog/DatasetPicker";

  // renderStep2 中：
  <Form.Item
    label="绑定数据集"
    name="datasetId"
    extra="选择此规则绑定的数据集"
  >
    <DatasetPicker placeholder="选择数据集" />
  </Form.Item>
  ```

  注意：字段名从 `datasetIds`（数组）改为 `datasetId`（字符串）。

- [ ] **Step 3: 更新 submit 逻辑**

  找到 `datasetIds: values.datasetIds || []`，改为：
  ```typescript
  datasetIds: values.datasetId ? [values.datasetId] : [],
  datasetId: values.datasetId || undefined,
  ```

- [ ] **Step 4: 移除 listDatasets 的 datasetOptions 加载逻辑**

  删除相关 state：`const [datasetOptions, setDatasetOptions] = useState`，以及对应的 `useEffect` 和 `listDatasets` 调用（DatasetPicker 内部自己加载）。

- [ ] **Step 5: Commit**

  ```bash
  git add source/dts-platform-webapp/src/pages/governance/components/RuleCreateWizard.tsx
  git commit -m "feat(catalog/F2): replace datasetIds Select with DatasetPicker in RuleCreateWizard"
  ```

---

### Task 18: 后端 — 保留字段 Liquibase 迁移（Reserved Fields）

**Files:**
- Create: `source/dts-platform/src/main/resources/config/liquibase/changelog/20260405_11_catalog_reserved_fields.xml`
- Modify: `source/dts-platform/src/main/resources/config/liquibase/master.xml`

**背景：** Sprint-7 README 要求预留 4 个字段（仅建列，不实现功能）。当前最新 changelog 为 `20260405_10_catalog_data_product.xml`。

- [ ] **Step 1: 创建 changelog 文件**

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
      http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.9.xsd">

    <changeSet id="20260405_11_1" author="system">
      <addColumn tableName="catalog_dataset">
        <column name="row_security_policy" type="varchar(500)">
          <constraints nullable="true"/>
        </column>
        <column name="sla_refresh_cron" type="varchar(100)">
          <constraints nullable="true"/>
        </column>
      </addColumn>
    </changeSet>

    <changeSet id="20260405_11_2" author="system">
      <addColumn tableName="catalog_dataset_lineage">
        <column name="source_column" type="varchar(255)">
          <constraints nullable="true"/>
        </column>
        <column name="target_column" type="varchar(255)">
          <constraints nullable="true"/>
        </column>
      </addColumn>
    </changeSet>

  </databaseChangeLog>
  ```

- [ ] **Step 2: 在 master.xml 末尾添加引用**

  在最后一个 `<include>` 之后添加：
  ```xml
  <include file="config/liquibase/changelog/20260405_11_catalog_reserved_fields.xml" relativeToChangelogFile="false"/>
  ```

- [ ] **Step 3: 编译验证**

  ```bash
  cd source/dts-platform && mvn compile -q 2>&1 | tail -5
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add source/dts-platform/src/main/resources/config/liquibase/changelog/20260405_11_catalog_reserved_fields.xml \
          source/dts-platform/src/main/resources/config/liquibase/master.xml
  git commit -m "feat(catalog): add reserved fields migration for catalog_dataset and catalog_dataset_lineage"
  ```

---

## 验收检查清单

- [x] `GET /api/catalog/domains/{id}/asset-stats` 返回真实 datasetCount
- [x] SubjectAreasPage 选中域后显示真实资产数
- [x] `GET /api/catalog/datasets/{id}/fields` 返回 CatalogColumnSchema 中的字段列表
- [x] DatasetPicker 组件可正常使用（域筛选 + 数据集搜索 + 字段回调）
- [ ] IndicatorWizard 中 sourceTable 改为 DatasetPicker（Task 14）
- [ ] RuleCreateWizard datasetIds 改为 DatasetPicker（Task 17）
- [x] `POST /api/catalog/lineage/import-dbt-manifest` 接受 manifest.json 文件，返回 created/skipped 统计
- [x] LineagePage 新增「血缘图」Tab，使用 @xyflow/react 渲染节点
- [x] `GET /api/catalog/datasets/{id}/indicator-deps` 返回依赖指标列表
- [ ] DatasetDetailPage 治理 Tab 显示「关联指标」区块（Task 15）
- [x] `catalog_data_product` 表已创建，CRUD API 可用
- [x] DatasetsPage 改为左树+卡片网格布局
- [x] `/catalog/datasets/:id` 路由可访问，Tab 详情页展示概览/字段/血缘/治理健康/权限
- [ ] DatasetDetailPage 血缘 Tab 内嵌迷你 ReactFlow 图（Task 16）
- [x] DataSearchPage 搜索结果按类型分 Tab，点击数据集跳转 Tab 详情页
- [ ] 保留字段 Liquibase 迁移（Task 18）
