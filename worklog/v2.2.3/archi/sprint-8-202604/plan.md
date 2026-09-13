# Sprint-8: 指标中心重构 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构指标管理为以主题域为导航轴的完整指标中心——左树导航、原始/二次分层、模板导入导出对称、域详情回填指标统计。

**Architecture:** 新建 IndicatorCenterPage 外壳页（左域树 + 右嵌入 IndicatorsPage），通过 URL 参数 `?domain=xxx&derived=` 传递过滤条件。后端新增 domain 软校验、derived 过滤、域指标统计、模板批量导入 4 个 API 改动。前端改动集中在 3 个页面 + 1 个新页面。

**Tech Stack:** Spring Boot 3.4.5 / Java 21 / JPA / PostgreSQL / React / antd / Vite

**Spec:** `worklog/v2.2.3/sprint-8-202604/README.md`

---

## File Structure

```
# 新增
source/dts-platform-webapp/src/pages/governance/IndicatorCenterPage.tsx
source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceIndicatorTemplateImportResource.java
source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDomainIndicatorStatsResource.java

# 修改（后端）
source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/catalog/CatalogDomainRepository.java
source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java
source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceIndicatorResource.java
source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorTemplateService.java

# 修改（前端）
source/dts-platform-webapp/src/api/platformApi.ts
source/dts-platform-webapp/src/pages/governance/IndicatorsPage.tsx
source/dts-platform-webapp/src/pages/governance/IndicatorTemplatePage.tsx
source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx
source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx
```

---

### Task 1: CatalogDomainRepository — 添加 existsByCode 方法

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/catalog/CatalogDomainRepository.java`

- [ ] **Step 1: 添加 existsByCode 派生方法**

在 `CatalogDomainRepository` 接口中添加：

```java
boolean existsByCodeIgnoreCase(String code);
```

现有方法有 `findFirstByCodeIgnoreCase`，新增 exists 变体供软校验使用。

- [ ] **Step 2: 验证编译**

Run: `cd source/dts-platform && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/catalog/CatalogDomainRepository.java
git commit -m "feat(S8/F1-T01): add existsByCodeIgnoreCase to CatalogDomainRepository"
```

---

### Task 2: IndicatorService — domain code 软校验

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java`

- [ ] **Step 1: 注入 CatalogDomainRepository**

在 IndicatorService 的构造函数中添加 `CatalogDomainRepository catalogDomainRepository` 参数并赋值到字段。

现有构造函数参数（9 个）：
```java
public IndicatorService(
    GovIndicatorDefinitionRepository repository,
    GovIndicatorVersionRepository versionRepository,
    GovIndicatorReferenceRepository referenceRepository,
    CatalogDatasetRepository datasetRepository,
    AccessChecker accessChecker,
    OrganizationVisibilityService organizationVisibilityService,
    QueryGateway queryGateway,
    SecuritySqlRewriter securitySqlRewriter,
    ObjectMapper objectMapper
)
```

添加第 10 个参数 `CatalogDomainRepository catalogDomainRepository`。添加导入：
```java
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
```

添加字段：
```java
private final CatalogDomainRepository catalogDomainRepository;
```

- [ ] **Step 2: 添加 validateDomainCode 私有方法**

在 IndicatorService 类中添加：

```java
private void validateDomainCode(String domain) {
    if (StringUtils.hasText(domain) && !catalogDomainRepository.existsByCodeIgnoreCase(domain)) {
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "域编码不存在: " + domain
        );
    }
}
```

- [ ] **Step 3: 在 create() 和 update() 中调用校验**

在 `create()` 方法中，`IndicatorMapper.apply(entity, request)` 之后、`applyDefaults` 之前添加：
```java
validateDomainCode(request.getDomain());
```

在 `update()` 方法中，`IndicatorMapper.apply(entity, request)` 之后、`applyDefaults` 之前添加同样调用。

注意：`IndicatorUpsertRequest` 需确认有 `getDomain()` 方法。查看现有 `IndicatorUpsertRequest` — 它通过 `IndicatorMapper.apply()` 映射，request 中的 domain 字段会设置到 entity。因此校验时直接从 request 获取 domain。

如果 `IndicatorUpsertRequest` 没有 `getDomain()` 方法，则改为在 `IndicatorMapper.apply()` 之后从 entity 读取：
```java
validateDomainCode(entity.getDomain());
```

- [ ] **Step 4: 验证编译**

Run: `cd source/dts-platform && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java
git commit -m "feat(S8/F1-T01): add domain code soft validation in IndicatorService create/update"
```

---

### Task 3: GovernanceIndicatorResource — 添加 derived 过滤参数

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceIndicatorResource.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java`

- [ ] **Step 1: IndicatorService.list() 添加 derived 参数**

修改主 `list()` 方法签名（当前在 ~line 93）：

从：
```java
public Page<IndicatorDto> list(String keyword, String status, String domain, String category, Pageable pageable, String activeDept) {
```

改为：
```java
public Page<IndicatorDto> list(String keyword, String status, String domain, String category, Boolean derived, Pageable pageable, String activeDept) {
```

在过滤循环中（`domainMatches` 检查之后），添加：
```java
if (!derivedMatches(indicator, derived)) continue;
```

添加过滤方法：
```java
private boolean derivedMatches(GovIndicatorDefinition indicator, Boolean derived) {
    if (derived == null) return true;
    if (derived) {
        return Boolean.TRUE.equals(indicator.getIsDerived());
    }
    return !Boolean.TRUE.equals(indicator.getIsDerived());
}
```

同时修改短参数 `list()` 重载（~line 88）的调用：
```java
public Page<IndicatorDto> list(String keyword, String status, Pageable pageable, String activeDept) {
    return list(keyword, status, null, null, null, pageable, activeDept);
}
```

- [ ] **Step 2: GovernanceIndicatorResource.listIndicators() 添加 derived 参数**

修改端点方法签名，添加参数：
```java
@RequestParam(required = false) Boolean derived,
```

在调用 `indicators.list()` 时传入 derived：
```java
Page<IndicatorDto> result = indicators.list(keyword, status, domain, category, derived, pageable, activeDept);
```

在 auditPayload 中添加：
```java
if (derived != null) auditPayload.put("derived", derived);
```

- [ ] **Step 3: 验证编译**

Run: `cd source/dts-platform && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java \
      source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceIndicatorResource.java
git commit -m "feat(S8/F1-T04): add derived filter parameter to listIndicators API"
```

---

### Task 4: CatalogDomainIndicatorStatsResource — 域指标统计端点

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDomainIndicatorStatsResource.java`

- [ ] **Step 1: 创建端点文件**

```java
package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/domains")
public class CatalogDomainIndicatorStatsResource {

    private final CatalogDomainRepository domainRepository;
    private final GovIndicatorDefinitionRepository indicatorRepository;

    public CatalogDomainIndicatorStatsResource(
        CatalogDomainRepository domainRepository,
        GovIndicatorDefinitionRepository indicatorRepository
    ) {
        this.domainRepository = domainRepository;
        this.indicatorRepository = indicatorRepository;
    }

    @GetMapping("/{id}/indicator-stats")
    public ApiResponse<Map<String, Object>> getIndicatorStats(@PathVariable UUID id) {
        String domainCode = domainRepository.findById(id)
            .map(CatalogDomain::getCode)
            .orElse(null);

        Map<String, Object> stats = new LinkedHashMap<>();
        if (domainCode == null) {
            stats.put("total", 0);
            stats.put("published", 0);
            stats.put("draft", 0);
            return ApiResponses.ok(stats);
        }

        List<GovIndicatorDefinition> indicators = indicatorRepository.findByDomainIgnoreCase(domainCode);
        long published = indicators.stream()
            .filter(i -> "PUBLISHED".equalsIgnoreCase(i.getStatus()))
            .count();
        long draft = indicators.stream()
            .filter(i -> "DRAFT".equalsIgnoreCase(i.getStatus()))
            .count();

        stats.put("total", indicators.size());
        stats.put("published", published);
        stats.put("draft", draft);
        return ApiResponses.ok(stats);
    }
}
```

注意：需要在 `GovIndicatorDefinitionRepository` 中添加 `findByDomainIgnoreCase(String domain)` 方法。

- [ ] **Step 2: GovIndicatorDefinitionRepository 添加 findByDomainIgnoreCase**

在 `GovIndicatorDefinitionRepository` 中添加：
```java
List<GovIndicatorDefinition> findByDomainIgnoreCase(String domain);
```

- [ ] **Step 3: 确认 CatalogDomain 实体有 getCode() 方法**

检查 `CatalogDomain.java`，确认有 `code` 字段和 `getCode()` 方法。如果方法不存在，需要查找正确的字段名。

- [ ] **Step 4: 验证编译**

Run: `cd source/dts-platform && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDomainIndicatorStatsResource.java \
      source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/governance/GovIndicatorDefinitionRepository.java
git commit -m "feat(S8/F1-T02): add GET /api/catalog/domains/{id}/indicator-stats endpoint"
```

---

### Task 5: GovernanceIndicatorTemplateImportResource — 模板批量导入端点

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceIndicatorTemplateImportResource.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorTemplateService.java`

- [ ] **Step 1: IndicatorTemplateService 添加 batchImport 方法**

在 `IndicatorTemplateService.java` 中添加方法：

```java
@Transactional
public Map<String, Integer> batchImport(List<GovIndicatorTemplate> templates) {
    int created = 0;
    int updated = 0;
    int skipped = 0;

    for (GovIndicatorTemplate incoming : templates) {
        if (incoming.getCode() == null || incoming.getCode().isBlank()) {
            skipped++;
            continue;
        }
        java.util.Optional<GovIndicatorTemplate> existing = templateRepo.findFirstByCodeIgnoreCase(incoming.getCode());
        if (existing.isPresent()) {
            GovIndicatorTemplate exist = existing.orElseThrow();
            if (Boolean.TRUE.equals(exist.getBuiltin())) {
                skipped++;
                continue;
            }
            exist.setName(incoming.getName());
            exist.setDescription(incoming.getDescription());
            exist.setDomain(incoming.getDomain());
            exist.setIndicatorBlueprints(incoming.getIndicatorBlueprints());
            exist.setRequiredSourceFields(incoming.getRequiredSourceFields());
            exist.setSeedTables(incoming.getSeedTables());
            exist.setRecommendedSnapshot(incoming.getRecommendedSnapshot());
            exist.setDisplayOrder(incoming.getDisplayOrder());
            templateRepo.save(exist);
            updated++;
        } else {
            incoming.setId(null);
            incoming.setBuiltin(Boolean.FALSE);
            incoming.setEnabled(Boolean.TRUE);
            templateRepo.save(incoming);
            created++;
        }
    }

    return Map.of("created", created, "updated", updated, "skipped", skipped);
}
```

注意：需确认 `GovIndicatorTemplateRepository` 有 `findFirstByCodeIgnoreCase(String code)` 方法。若没有则先添加。

- [ ] **Step 2: 创建 Import Resource 端点**

```java
package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate;
import com.yuzhi.dts.platform.service.governance.IndicatorTemplateService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/governance/indicator-templates")
public class GovernanceIndicatorTemplateImportResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";

    private final IndicatorTemplateService templateService;
    private final ObjectMapper objectMapper;

    public GovernanceIndicatorTemplateImportResource(
        IndicatorTemplateService templateService,
        ObjectMapper objectMapper
    ) {
        this.templateService = templateService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/import")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Integer>> importTemplates(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > 10 * 1024 * 1024L) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "文件不能超过 10MB");
        }

        List<GovIndicatorTemplate> templates;
        try {
            templates = objectMapper.readValue(
                file.getInputStream(),
                new TypeReference<List<GovIndicatorTemplate>>() {}
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 格式错误: " + e.getMessage());
        }

        Map<String, Integer> result = templateService.batchImport(templates);
        return ApiResponses.ok(result);
    }
}
```

- [ ] **Step 3: 确认 GovIndicatorTemplateRepository 有 findFirstByCodeIgnoreCase**

检查 `GovIndicatorTemplateRepository.java`，若缺少则添加：
```java
Optional<GovIndicatorTemplate> findFirstByCodeIgnoreCase(String code);
```

- [ ] **Step 4: 验证编译**

Run: `cd source/dts-platform && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceIndicatorTemplateImportResource.java \
      source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorTemplateService.java \
      source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/governance/GovIndicatorTemplateRepository.java
git commit -m "feat(S8/F1-T03): add POST /api/governance/indicator-templates/import batch upsert endpoint"
```

---

### Task 6: platformApi.ts — 新增 API 函数

**Files:**
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`

- [ ] **Step 1: 添加 3 个新 API 函数**

在文件末尾（现有 indicator/template 函数附近）添加：

```typescript
// --- Sprint-8: 指标中心 ---

export const getDomainIndicatorStats = (domainId: string) =>
	apiClient.get(`/api/catalog/domains/${domainId}/indicator-stats`).then(unwrap);

export const importIndicatorTemplates = (file: File) => {
	const formData = new FormData();
	formData.append("file", file);
	return apiClient
		.post("/api/governance/indicator-templates/import", formData, {
			headers: { "Content-Type": "multipart/form-data" },
		})
		.then(unwrap);
};
```

注意：确认 `unwrap` 是否是 platformApi.ts 中已有的响应解包函数。如果现有模式是 `.then((r) => r.data?.data ?? r.data)` 或类似写法，使用相同模式。

同时确认 `listIndicators` 已有的参数传递方式支持 `derived` 参数。现有签名 `listIndicators(params: any = {})` 已经透传所有参数，无需修改。

- [ ] **Step 2: 验证前端构建**

Run: `cd source/dts-platform-webapp && npx vite build 2>&1 | tail -10`
Expected: build success（可能有 chunk size warning，忽略）

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/api/platformApi.ts
git commit -m "feat(S8/F1): add getDomainIndicatorStats and importIndicatorTemplates API functions"
```

---

### Task 7: IndicatorCenterPage — 外壳页（左域树 + 右内容）

**Files:**
- Create: `source/dts-platform-webapp/src/pages/governance/IndicatorCenterPage.tsx`
- Modify: `source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx`

- [ ] **Step 1: 创建 IndicatorCenterPage.tsx**

```tsx
import { Suspense, lazy, useEffect, useState } from "react";
import { Layout, Spin, Tree } from "antd";
import type { DataNode } from "antd/es/tree";
import { useSearchParams } from "react-router";
import { getDomainTree } from "@/api/platformApi";

const IndicatorsPage = lazy(() => import("./IndicatorsPage"));

const { Sider, Content } = Layout;

type DomainNode = {
	id?: string;
	name?: string;
	code?: string;
	children?: DomainNode[];
};

function buildTreeData(nodes: DomainNode[]): DataNode[] {
	return nodes.map((n, i) => ({
		key: n.code || `node-${i}`,
		title: n.name || n.code || "未命名",
		children: n.children?.length ? buildTreeData(n.children) : undefined,
	}));
}

export default function IndicatorCenterPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const [treeData, setTreeData] = useState<DataNode[]>([]);
	const [treeLoading, setTreeLoading] = useState(true);
	const [treeError, setTreeError] = useState(false);

	const activeDomain = searchParams.get("domain") || "";

	useEffect(() => {
		loadTree();
	}, []);

	const loadTree = () => {
		setTreeLoading(true);
		setTreeError(false);
		getDomainTree()
			.then((res: any) => {
				const nodes: DomainNode[] = Array.isArray(res) ? res : res?.data ?? [];
				const allNode: DataNode = { key: "", title: "全部" };
				setTreeData([allNode, ...buildTreeData(nodes)]);
			})
			.catch(() => setTreeError(true))
			.finally(() => setTreeLoading(false));
	};

	const handleSelect = (selectedKeys: React.Key[]) => {
		const code = String(selectedKeys[0] ?? "");
		const next = new URLSearchParams(searchParams);
		if (code) {
			next.set("domain", code);
		} else {
			next.delete("domain");
		}
		setSearchParams(next, { replace: true });
	};

	return (
		<Layout style={{ minHeight: "100%" }}>
			<Sider
				width={240}
				style={{
					background: "#fff",
					borderRight: "1px solid #f0f0f0",
					overflowY: "auto",
					height: "calc(100vh - 64px)",
				}}
			>
				<div className="px-3 py-3 text-sm font-semibold text-slate-700">主题域导航</div>
				{treeLoading ? (
					<div className="flex justify-center py-8"><Spin size="small" /></div>
				) : treeError ? (
					<div className="px-3 py-4 text-xs text-slate-400">
						加载失败，
						<a onClick={loadTree} className="text-blue-500 cursor-pointer">点击重试</a>
					</div>
				) : (
					<Tree
						treeData={treeData}
						selectedKeys={[activeDomain]}
						onSelect={handleSelect}
						defaultExpandAll
						blockNode
						style={{ padding: "0 4px" }}
					/>
				)}
			</Sider>
			<Content style={{ padding: 0 }}>
				<Suspense fallback={<div className="flex h-64 items-center justify-center"><Spin /></div>}>
					<IndicatorsPage />
				</Suspense>
			</Content>
		</Layout>
	);
}
```

- [ ] **Step 2: 注册路由**

在 `dynamic-resolver.tsx` 的 `PATH_COMPONENT_OVERRIDES` 中添加：

```typescript
"/governance/indicator-center": "/pages/governance/IndicatorCenterPage",
```

- [ ] **Step 3: 验证前端构建**

Run: `cd source/dts-platform-webapp && npx vite build 2>&1 | tail -10`
Expected: build success

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/pages/governance/IndicatorCenterPage.tsx \
      source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx
git commit -m "feat(S8/F2-T01,T03): add IndicatorCenterPage with domain tree navigation + route"
```

---

### Task 8: IndicatorsPage — 添加 isDerived 分组 Tab

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/governance/IndicatorsPage.tsx`

- [ ] **Step 1: 读取现有文件，找到指标列表获取逻辑**

阅读 IndicatorsPage.tsx 中调用 `listIndicators()` 的位置和当前过滤参数的传递方式。关注：
- `indicatorFilters` state 的定义和使用
- `fetchIndicators()` 或类似函数的调用
- `useSearchParams` 的使用方式

- [ ] **Step 2: 添加 derived Tab 和 URL 参数读取**

在 IndicatorsPage 中：

1. 从 `searchParams` 读取 `derived` 参数：
```tsx
const derivedParam = searchParams.get("derived");
const derivedFilter: boolean | undefined = derivedParam === "true" ? true : derivedParam === "false" ? false : undefined;
```

2. 在指标列表获取函数中传递 `derived` 参数：
```tsx
// 在 listIndicators 调用时添加
const params = {
    ...existingParams,
    derived: derivedFilter,
};
```

3. 在指标列表上方（Table 组件前）添加 Tab：
```tsx
const DERIVED_TABS = [
    { key: "", label: "全部" },
    { key: "false", label: "原始指标" },
    { key: "true", label: "二次指标" },
];

// 在 JSX 中，Table 上方：
<div className="mb-3 flex gap-2">
    {DERIVED_TABS.map((tab) => (
        <Button
            key={tab.key}
            type={((derivedParam ?? "") === tab.key) ? "primary" : "default"}
            size="small"
            onClick={() => {
                const next = new URLSearchParams(searchParams);
                if (tab.key) {
                    next.set("derived", tab.key);
                } else {
                    next.delete("derived");
                }
                next.set("page", "0");
                setSearchParams(next, { replace: true });
            }}
        >
            {tab.label}
        </Button>
    ))}
</div>
```

注意：需要确认 IndicatorsPage 中 `searchParams` 的实际使用方式。现有代码用 `useSearchParams` 但可能用了不同的 key（如 `i_st` 表示 status）。需要阅读实际代码来确定合适的 key name。如果现有用的是简短 key，则用 `i_dr` 之类。

- [ ] **Step 3: 确认 domain 参数也从 searchParams 传递到 API**

IndicatorsPage 被嵌入 IndicatorCenterPage 后，需要确认 `domain` 参数从 URL 传递到 `listIndicators()` API 调用。阅读现有 fetchIndicators 逻辑，如果没有读取 `domain` searchParam，需要添加。

- [ ] **Step 4: 验证前端构建**

Run: `cd source/dts-platform-webapp && npx vite build 2>&1 | tail -10`
Expected: build success

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/pages/governance/IndicatorsPage.tsx
git commit -m "feat(S8/F2-T02): add isDerived group tabs and domain URL param support in IndicatorsPage"
```

---

### Task 9: SubjectAreasPage — 域详情接指标统计

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx`

- [ ] **Step 1: 导入新 API 和 router**

在文件顶部导入中添加：
```tsx
import { getDomainIndicatorStats } from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";
```

- [ ] **Step 2: 添加指标统计 state 和 useEffect**

在组件内部，现有 `assetStats` 相关代码附近添加：

```tsx
const router = useRouter();
const [indicatorStats, setIndicatorStats] = useState<{ total: number; published: number; draft: number } | null>(null);
const [indicatorStatsLoading, setIndicatorStatsLoading] = useState(false);
```

在加载 domain asset stats 的 useEffect 中（或新建一个），当 `activeDomain?.id` 变化时加载：

```tsx
useEffect(() => {
    if (!activeDomain?.id) {
        setIndicatorStats(null);
        return;
    }
    setIndicatorStatsLoading(true);
    getDomainIndicatorStats(activeDomain.id)
        .then((res: any) => setIndicatorStats(res ?? null))
        .catch(() => setIndicatorStats(null))
        .finally(() => setIndicatorStatsLoading(false));
}, [activeDomain?.id]);
```

- [ ] **Step 3: 在域详情面板渲染指标统计**

在现有"域级治理指标"区域（~line 393-417），替换或补充硬编码内容为：

```tsx
<div className="mb-3 text-sm font-semibold text-slate-900">域级指标统计</div>
{indicatorStatsLoading ? (
    <div className="text-xs text-slate-400">加载中...</div>
) : indicatorStats && indicatorStats.total > 0 ? (
    <div className="space-y-2">
        <div className="grid grid-cols-3 gap-3 text-center">
            <div className="rounded border border-slate-100 bg-slate-50 p-2">
                <div className="text-lg font-bold text-slate-800">{indicatorStats.total}</div>
                <div className="text-xs text-slate-500">总数</div>
            </div>
            <div className="rounded border border-green-100 bg-green-50 p-2">
                <div className="text-lg font-bold text-green-600">{indicatorStats.published}</div>
                <div className="text-xs text-slate-500">已发布</div>
            </div>
            <div className="rounded border border-orange-100 bg-orange-50 p-2">
                <div className="text-lg font-bold text-orange-500">{indicatorStats.draft}</div>
                <div className="text-xs text-slate-500">草稿</div>
            </div>
        </div>
        <a
            onClick={() => router.push(`/governance/indicator-center?domain=${activeDomain?.code ?? ""}`)}
            className="cursor-pointer text-xs text-blue-500 hover:underline"
        >
            查看该域全部指标 →
        </a>
    </div>
) : (
    <div className="text-xs text-slate-400">暂无指标</div>
)}
```

- [ ] **Step 4: 验证前端构建**

Run: `cd source/dts-platform-webapp && npx vite build 2>&1 | tail -10`
Expected: build success

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx
git commit -m "feat(S8/F3-T01): add indicator stats with clickable link in SubjectAreasPage domain detail"
```

---

### Task 10: IndicatorTemplatePage — 动态域 Tab + 导入按钮

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/governance/IndicatorTemplatePage.tsx`

- [ ] **Step 1: 替换硬编码 DOMAIN_TABS**

移除文件顶部的 `DOMAIN_TABS` 常量和 `DOMAIN_COLORS` 常量。

添加导入：
```tsx
import { Upload } from "antd";
import { UploadOutlined } from "@ant-design/icons";
import { getDomainTree, importIndicatorTemplates } from "@/api/platformApi";
```

添加 state：
```tsx
const [domainTabs, setDomainTabs] = useState<Array<{ key: string; label: string }>>([{ key: "", label: "全部" }]);
```

添加 useEffect 加载域 Tab：
```tsx
useEffect(() => {
    getDomainTree()
        .then((res: any) => {
            const nodes: any[] = Array.isArray(res) ? res : res?.data ?? [];
            const flatten = (items: any[]): Array<{ key: string; label: string }> =>
                items.flatMap((n) => [
                    { key: n.code || "", label: n.name || n.code || "未知" },
                    ...(n.children?.length ? flatten(n.children) : []),
                ]).filter((d) => d.key);
            setDomainTabs([{ key: "", label: "全部" }, ...flatten(nodes)]);
        })
        .catch(() => { /* keep default "全部" tab */ });
}, []);
```

在 Tabs 的 items 渲染中，将 `DOMAIN_TABS` 替换为 `domainTabs`：
```tsx
<Tabs
    activeKey={domain}
    onChange={(key) => setDomain(key)}
    items={domainTabs.map((t) => ({ key: t.key, label: t.label }))}
/>
```

移除 `DOMAIN_COLORS`（用于 Tag color）的引用。模板列表 domain 列改为直接显示文本，不根据硬编码 map 着色。

- [ ] **Step 2: 添加导入按钮和处理逻辑**

在操作栏（"新建"按钮旁边）添加：

```tsx
const handleImport = async (file: File) => {
    try {
        const result: any = await importIndicatorTemplates(file);
        toast.success(
            `导入完成：新增 ${result.created ?? 0}，更新 ${result.updated ?? 0}，跳过 ${result.skipped ?? 0}`
        );
        fetchList();
    } catch {
        // global interceptor handles
    }
    return false; // prevent antd default upload
};

const handleDownloadExample = () => {
    const example = [
        {
            code: "TPL_EXAMPLE",
            name: "示例模板",
            domain: "",
            description: "这是一个示例模板",
            indicatorBlueprints: "[]",
            requiredSourceFields: "[]",
            seedTables: "[]",
            recommendedSnapshot: false,
        },
    ];
    const blob = new Blob([JSON.stringify(example, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "indicator-template-example.json";
    a.click();
    URL.revokeObjectURL(url);
};
```

在 JSX 操作栏中添加（在"新建"按钮之后）：

```tsx
<Upload
    accept=".json"
    showUploadList={false}
    beforeUpload={(file) => { handleImport(file); return false; }}
>
    <Button icon={<UploadOutlined />}>导入</Button>
</Upload>
<Button type="link" size="small" onClick={handleDownloadExample}>
    下载格式示例
</Button>
```

- [ ] **Step 3: 验证前端构建**

Run: `cd source/dts-platform-webapp && npx vite build 2>&1 | tail -10`
Expected: build success

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/pages/governance/IndicatorTemplatePage.tsx
git commit -m "feat(S8/F4-T01,T02): replace hardcoded domain tabs with dynamic data + add template import"
```

---

## Self-Review Checklist

**Spec coverage:**
- F1-T01 (domain 软校验) → Task 1 + Task 2 ✅
- F1-T04 (derived 过滤) → Task 3 ✅
- F1-T02 (域指标统计) → Task 4 ✅
- F1-T03 (模板批量导入) → Task 5 ✅
- F2-T01 (IndicatorCenterPage) → Task 7 ✅
- F2-T02 (isDerived Tab) → Task 8 ✅
- F2-T03 (路由注册) → Task 7 ✅
- F3-T01 (SubjectAreasPage 统计) → Task 9 ✅
- F4-T01 (动态域 Tab) → Task 10 ✅
- F4-T02 (导入按钮) → Task 10 ✅
- F5 (数据资产关联) → Sprint-7 已完成，无 Task ✅
- platformApi.ts → Task 6 ✅

**Placeholder scan:** 无 TBD/TODO。所有步骤包含完整代码。

**Type consistency:**
- `existsByCodeIgnoreCase` → Task 1 定义，Task 2 使用 ✅
- `findByDomainIgnoreCase` → Task 4 定义并使用 ✅
- `getDomainIndicatorStats` → Task 6 定义，Task 9 使用 ✅
- `importIndicatorTemplates` → Task 6 定义，Task 10 使用 ✅
- `derivedMatches` → Task 3 定义并使用 ✅
- `batchImport` → Task 5 定义并使用 ✅
