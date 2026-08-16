# 2026-08-17 部署后真实 Chrome 验收

## 结论

本轮在真实运行栈使用 `xiezm` 完成登录、模型、资产治理、分析卡片和分析看板操作。所级数据管理员的 BI 列表、编辑器、创建和删除权限已不再返回 403；但模型到资产单一身份、模型到 Analytics 语义投影和站内深链仍未闭环，因此不能把本轮登记为 Sprint-93 全链 E2E 通过。

## 固定环境

- Git：`af19ed44e feat: converge modeling governance and analytics access`，本地与 `origin/v2.2.3` 一致。
- 镜像：
  - `dts-platform:1.0.0`：`sha256:18359a2fef4b...`
  - `dts-ingestion:1.0.0`：`sha256:5b3140bdfbba...`
  - `dts-analytics:1.0.0`：`sha256:473477b9fded...`
  - `dts-platform-webapp:1.0.0`：`sha256:dc5462199abc...`
- 运行态：platform、ingestion、analytics 均 `healthy`，platform-webapp 为 `running`；`https://bi.yuzhicloud.com/` 返回 200。
- 登录：`xiezm`，`/api/session/status` 返回 200、`authenticated=true`，角色包含 `ROLE_INST_DATA_OWNER`。
- 浏览器：Headless Chrome 150.0.0.0；桌面视口 1366x768，窄屏视口 800x700。当前证据不是 Chrome 95 证据。

## 操作与断言

| # | 真实操作 | 结果 | 证据与判断 |
|---|---|---|---|
| 1 | 从真实登录页登录并进入资产目录 | PASS | 会话 200；资产目录、资产详情、治理信息编辑区均可达。 |
| 2 | 刷新资产概览并进入目录 | FAIL | 同一账号同一时刻左侧范围显示 308、概览卡片显示 200、目录显示 286，口径仍不一致。 |
| 3 | 搜索已发布并已物化模型 `biz_ads_progress_kpi_v2` | FAIL | `/api/catalog/assets-v2?...keyword=biz_ads_progress_kpi_v2` 返回 `total=3`；其中两个记录都指向 `public.biz_ads_progress_kpi_v2`，但 datasetId/AssetKey 不同。 |
| 4 | 对比同一 public 关系的物理观察与模型语义投影 | FAIL | 物理观察：`ae72954e-...`、`source:5cc0f57f-...`、PENDING_DOMAIN；语义投影：`c3716dae-...`、`source:a0000000-...`、ADS/研究项目域/xiezm。单一资产身份没有收敛。 |
| 5 | 打开语义资产的质量与 SLA、血缘与影响 | PARTIAL | 页面和血缘图可达；质量运行总数为 0，显示发布前治理阻断；字段血缘开关因无字段血缘数据禁用。 |
| 6 | 从真实菜单进入“分析卡片”并点击“新建问题” | PASS_WITH_GAP | 列表、编辑器及 `/bi/api/card`、`/bi/api/semantic/meta` 均返回 200，不再出现 403。 |
| 7 | 在新建问题页选择语义模型 | BLOCKED | `/bi/api/semantic/meta?exposed_to_modeler=true` 返回 `models=[]`；模型工作台同时存在 85 个模型，样本模型为 PUBLISHED r3、已物化且关系已核验。模型到 Analytics 投影断链。 |
| 8 | 新建 E2E 看板并保存 | PASS_WITH_EXPECTED_BLOCK | `POST /bi/api/dashboard` 返回 200、`can_write=true`；布局保存返回 400 `Dashboard must contain at least one classified card`，是业务前置而非权限错误。 |
| 9 | 清理 E2E 看板 | PASS | 通过 UI 确认移至废纸篓，`DELETE /bi/api/dashboard/1` 返回 204，列表中记录消失。 |
| 10 | 从空语义页点击“去模型工作台” | FAIL | Hash URL 变为 `/data-modeling/dimensions/workbench`，页面仍停留在新建语义卡片；强制整页加载后才显示模型工作台。 |
| 11 | 桌面与窄屏、console/network 收尾 | PARTIAL | 两个视口无白屏，最终页面 console error=0；Chrome 95 尚未执行。保存空看板时出现的单个 400 已由业务错误响应解释。 |

## 关键响应

### 同一 public 物理关系出现两个资产身份

- 物理观察资产：
  - datasetId：`ae72954e-0024-4620-92f1-481c7121da8d`
  - AssetKey：`source:5cc0f57f-071e-4cf2-b630-97e643a7baec/schema:public/table:biz_ads_progress_kpi_v2`
- 模型语义投影资产：
  - datasetId：`c3716dae-6ac4-4f53-aae8-8bae58939996`
  - AssetKey：`source:a0000000-0000-0000-0000-000000000001/schema:public/table:biz_ads_progress_kpi_v2`

另一个 `pjm_pkg_verify_20260814_232627` schema 下的同名表是不同物理关系，不计入上述 public 重复项。

### Analytics 语义投影为空

```json
{"spec_version":"1","models":[]}
```

对应模型工作台样本 `biz_ads_progress_kpi_v2`：PUBLISHED r3、实现 r4、已物化，目标关系 `biadmin.public.biz_ads_progress_kpi_v2`，关系已核验。

### 看板权限与业务阻断

- 创建：`POST /bi/api/dashboard` = 200，返回 `can_write=true`。
- 保存：`POST /bi/api/dashboard/save` = 400，`REQ_INVALID_ARGUMENT`，消息为 `Dashboard must contain at least one classified card`。
- 清理：`DELETE /bi/api/dashboard/1` = 204。

## 截图

- [语义模型为空（1366x768）](20260817-semantic-empty-desktop.png)
- [语义模型为空（800x700）](20260817-semantic-empty-narrow.png)
- [同名资产重复（1366x768）](20260817-asset-duplicate-desktop.png)

## 后续修复顺序

1. 先统一模型物化观察与技术采集所使用的真实 sourceId/AssetKey，合并同一物理关系，禁止使用平台占位 sourceId 创建第二 dataset。
2. 接通 PUBLISHED/ONLINE 模型到 Analytics `semantic/meta` 的持久投影，并把 `exposed_to_modeler`、密级和可见范围作为可观测状态展示在模型工作台。
3. 修复 Hash 路由的站内跳转刷新，使“去模型工作台”立即切换页面。
4. 完成上述修复后，再执行“创建卡片 → 创建看板 → 发布/消费”的真实写链；最后单独执行 Chrome 95 和部门越权负向验收。
