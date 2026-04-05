# Sprint-8: 指标中心重构

**时间**: 2026-04
**状态**: READY（下一个开始）
**目标**: 重构指标管理为以主题域为导航轴的完整指标中心——左树接主题域、原始/二次指标分层、导入导出对称格式、主题域详情回填指标统计。

## 设计决策记录

| 决策点 | 结论 |
|--------|------|
| 整体布局 | 左树+右内容（经典资源管理器风格） |
| 域树数据源 | 来自主题域管理 `GET /api/catalog/domains/tree`，不硬编码 |
| domain 字段耦合方式 | 软校验：存 subject area code 字符串，服务层校验存在性，无 DB FK |
| 域选择传递机制 | URL 参数透传（`?domain=xxx`），IndicatorsPage 通过 searchParams 读取 |
| 重构策略 | 新建 IndicatorCenterPage 作为外壳页，IndicatorsPage 嵌入右侧，核心逻辑不动 |
| 指标展示 | 默认表格视图，加"原始/二次"分组 Tab |
| 导入/导出 | 对称 JSON 格式，单文件批量上传（JSON 数组），按 code upsert |
| 旧 domain 数据 | 不迁移，无法匹配域树的旧值通过 URL 直查仍可访问 |
| 域指标统计 | 简版（总数/已发布/草稿）+ 可点击跳转到指标中心 |
| 数据资产关联 | Sprint-7 已通过 DatasetPicker 完成，不重复实现 |

## 架构概览

### 文件结构

```
# 新增
source/dts-platform-webapp/src/pages/governance/
  IndicatorCenterPage.tsx          ← 外壳页：左域树 + 右侧嵌入 IndicatorsPage

source/dts-platform/src/.../web/rest/
  GovernanceIndicatorTemplateImportResource.java  ← 模板批量导入端点

source/dts-platform/src/.../web/rest/catalog/
  CatalogDomainIndicatorStatsResource.java  ← 域指标统计端点

# 修改
IndicatorsPage.tsx               ← 仅加 isDerived 分组 Tab（原始/二次）
IndicatorTemplatePage.tsx        ← 域 Tab 改为动态 + 加导入按钮
SubjectAreasPage.tsx             ← 域详情面板加指标统计 + 跳转链接
IndicatorService.java            ← create/update 加 domain code 软校验
dynamic-resolver.tsx             ← 新增 /governance/indicator-center 路由
platformApi.ts                   ← 新增模板导入 + 域指标统计 API
```

### 路由变化

- 保留 `/governance/indicators`（原 IndicatorsPage，不破坏现有链接）
- 新增 `/governance/indicator-center`（IndicatorCenterPage，主入口）
- 菜单入口指向新路由

## Feature 设计

### F1: 后端 API 改造

#### F1-T01: IndicatorService — domain code 软校验

在 `create()` 和 `update()` 中，若请求包含非空 `domain` 字段，校验该 code 存在于 `catalog_domain` 表：

```java
// IndicatorService 注入 CatalogDomainRepository
if (StringUtils.hasText(request.getDomain())) {
    boolean exists = catalogDomainRepository.existsByCode(request.getDomain());
    if (!exists) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "域编码不存在: " + request.getDomain());
    }
}
```

`CatalogDomainRepository` 新增 `existsByCode(String code)` 派生方法。

#### F1-T04: listIndicators 支持 derived 过滤参数

`GovernanceIndicatorResource.listIndicators()` 新增 `@RequestParam(required = false) Boolean derived` 参数，传入 `IndicatorService.list()`。`IndicatorService.list()` 中增加 `derivedMatches(indicator, derived)` 过滤方法：
- `derived == null` → 不过滤（全部）
- `derived == true` → 仅 `isDerived=true`
- `derived == false` → 仅 `isDerived=false` 或 `isDerived=null`

#### F1-T02: 域指标统计端点

新建 `CatalogDomainIndicatorStatsResource`：

```
GET /api/catalog/domains/{id}/indicator-stats
→ { total: 12, published: 8, draft: 4 }
```

实现：根据 domain id 查 `catalog_domain.code`，再 `COUNT` `gov_indicator_definition` 按 status 分组返回聚合结果。

#### F1-T03: 模板批量导入端点

新建 `GovernanceIndicatorTemplateImportResource`：

```
POST /api/governance/indicator-templates/import
Content-Type: multipart/form-data (JSON file)
→ { created: 3, updated: 1, skipped: 2 }
```

逻辑：
- 解析 JSON 数组，每项字段与导出格式完全对称
- 按 `code` upsert：存在且 `builtin=false` → 更新；存在且 `builtin=true` → 跳过（skipped）；不存在 → 创建
- 文件大小限制 10MB

### F2: 指标中心页面重构

#### F2-T01: IndicatorCenterPage 外壳页

布局：

```
┌─────────────────────────────────────────────────┐
│  Layout                                         │
│  ┌──────────┐  ┌──────────────────────────────┐ │
│  │  Sider   │  │  Content                     │ │
│  │  240px   │  │                              │ │
│  │  全部     │  │  <IndicatorsPage />           │ │
│  │  ▸ 财务域 │  │  （URL ?domain=xxx 过滤）      │ │
│  │  ▸ 运营域 │  │                              │ │
│  │  ▸ 合规域 │  │                              │ │
│  └──────────┘  └──────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

实现细节：
- 调用 `getDomainTree()` 获取主题域树，构建 antd `Tree` 节点，key = domain.code
- 顶部"全部"节点（key = `""`），点击清空 domain 过滤
- 点击树节点 → `setSearchParams({ domain: node.code })`
- Sider 宽 240px，`overflowY: auto`，`height: calc(100vh - 64px)`
- IndicatorsPage 以动态 import 懒加载嵌入

#### F2-T02: IndicatorsPage 加 isDerived 分组 Tab

在指标列表顶部新增 Tab：
- "全部" / "原始指标（isDerived=false）" / "二次指标（isDerived=true）"
- 通过 URL 参数 `?derived=` 控制，与 domain 参数并行不互斥
- IndicatorsPage 在调用 `listIndicators()` 时传递 derived 参数

#### F2-T03: 路由注册

`dynamic-resolver.tsx` 新增：
```
"/governance/indicator-center": "/pages/governance/IndicatorCenterPage"
```

### F3: 主题域管理页面增强

#### F3-T01: SubjectAreasPage 域详情接指标统计

域详情面板新增统计区块（在现有资产统计下方）：

```
域级指标统计
┌────────┬────────┬────────┐
│ 总数 12 │ 已发布 8 │ 草稿 4  │
└────────┴────────┴────────┘
         查看该域全部指标 →
```

- 选中域节点后调用 `GET /api/catalog/domains/{id}/indicator-stats`
- "查看该域全部指标 →" 点击 → `router.push(/governance/indicator-center?domain=${domainCode})`
- 若该域无指标，显示"暂无指标"灰色文字

### F4: 指标模板导入导出对称化

#### F4-T01: IndicatorTemplatePage 域 Tab 动态化

将硬编码 `DOMAIN_TABS` 替换为动态数据：
- 加载时调用 `getDomainTree()` → 拉平为一级列表
- Tab 项 = `[{ key: "", label: "全部" }, ...domains.map(d => ({ key: d.code, label: d.name }))]`

#### F4-T02: IndicatorTemplatePage 加导入按钮

- 在"新建"按钮旁新增"导入"按钮，antd `Upload` 组件
- `beforeUpload` 调用 `importIndicatorTemplates(file)` → toast 显示 created/updated/skipped
- 新增"下载格式示例"链接，前端生成包含示例结构的空模板 JSON 触发下载

### F5: 数据资产关联

**已在 Sprint-7 完成。** IndicatorsPage 已集成 `DatasetPicker` 组件，指标创建/编辑时可从数据目录选择数据集和字段。不重复实现。

## 数据流

```
用户点击左侧域树 "财务域"
  → URL: /governance/indicator-center?domain=CAIWU
  → IndicatorsPage 读 searchParams.domain
  → listIndicators({ domain: "CAIWU" })
  → 后端 IndicatorService.list() 按 domain 内存过滤（已有逻辑）
  → 返回该域指标列表

用户创建/编辑指标，填写 domain 字段
  → IndicatorService.create/update()
  → 软校验: catalogDomainRepository.existsByCode(domain)
  → 不存在 → 400 "域编码不存在"
  → 存在 → 正常保存

SubjectAreasPage 点击 "查看该域全部指标 →"
  → router.push(/governance/indicator-center?domain=CAIWU)
  → IndicatorCenterPage 域树自动选中节点
  → 指标列表自动过滤
```

## 边界条件

| 场景 | 处理 |
|------|------|
| 域树加载失败 | Sider 显示"加载失败，点击重试"，右侧正常展示全部指标 |
| domain 参数对应的域已删除 | 后端正常返回该 domain 下的指标（domain 是字符串非 FK） |
| 旧数据 domain 值不在域树中 | 域树不显示该节点，`?domain=FINANCE` 直查仍可访问历史数据 |
| 模板导入文件格式错误 | 后端返回 400 + 错误消息，前端 toast 显示 |
| 模板导入中 builtin=true 被跳过 | 不报错，计入 skipped，结果 toast 告知 |
| 指标统计接口域不存在 | 返回 `{ total: 0, published: 0, draft: 0 }`，不报 404 |

## 不做的事

- 不新增 Liquibase 迁移（domain 字段已存在）
- 不做旧 domain 数据迁移（方案 A — 直接替换）
- 不重复实现 DatasetPicker（Sprint-7 已完成）
- 不重构 IndicatorService.list() 的内存过滤为 SQL（不在本 Sprint 范围）
