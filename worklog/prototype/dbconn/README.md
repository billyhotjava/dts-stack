# DTS 数据接入交互原型

本地只读原型，用来验证「数据接入」链路的重构方案。不连接任何后端，数据全部为示意。

## 启动

在仓库根目录执行：

```bash
python3 -m http.server 4174 --directory worklog/prototype
```

浏览器打开：

```text
http://127.0.0.1:4174/dbconn/
```

## 为什么做这个原型

现网把"把一个库接进湖"这件事拆在 **3 个顶级模块、6 个菜单**里：

| 菜单 | 路径 | 性质 |
|---|---|---|
| 连接器目录 | `/foundation/connectors` | 管理员一年动一次 |
| 数据源管理 | `/foundation/data-sources` | 每次都要 |
| 驱动管理 | `/foundation/jdbc-drivers` | 管理员一年动一次 |
| 数据源结构采集 | `/catalog/metadata` | 跳到「数据治理」 |
| 数据入湖配置 | `/explore/etl/transform` | 跳到「数据开发」 |
| 接入变更记录 | `/foundation/access-changes` | 审计 |

同时前端有 **11 个裸 JSON 文本框**要用户手写（`readerConfig` 还是必填），后端 `IngestionTask`
有 **8 个无校验的 jsonb 列**。本原型论证的是：这不是 UI 问题，是配置领域模型缺位。

## 已覆盖的界面

| 界面 | 说明 | 截图 |
|---|---|---|
| 数据接入（列表） | 连接与任务合一，卡片直接暴露漂移/失败/待审批 | [prototype-list.png](./prototype-list.png) |
| 向导 ① 连接 | 连接器选择器（含驱动状态）+ schema 驱动表单 + 测试连接 | [prototype-wizard-api.png](./prototype-wizard-api.png) |
| 向导 ② 选表 | 自动发现、增量列识别、ODS 表名与字段映射推导 | [prototype-wizard-tables.png](./prototype-wizard-tables.png) |
| 向导 ③ 策略 | 同步方式、调度、继承/覆盖、生成的 Addax 作业只读预览 | [prototype-wizard-policy.png](./prototype-wizard-policy.png) |
| 接入详情 / 运维 | 概览、运行历史、结构漂移、落地预检、变更记录、配置 | [prototype-detail.png](./prototype-detail.png) |
| 参数归属 | 现网 86 个字段逐个归位，可按归属筛选 | [prototype-params.png](./prototype-params.png) |
| 连接器与驱动 | 下沉为系统管理，"配置字段数"来自各连接器自己的 schema | — |
| 设计说明 | 六个决策 + 现状对照 + 本原型未处理的问题 | [prototype-notes.png](./prototype-notes.png) |

## 参数归属结论

现网数据源表单 22 个字段 + 入湖任务表单 64 个字段 = **86 个**，归位如下：

| 归属 | 数量 | 含义 |
|---|---|---|
| 连接器 schema | 18 | 建连接时填一次，任务侧继承 |
| 向导②选表 | 21 | 自动推导为主，可逐表覆盖 |
| 向导③策略 | 20 | 调度、增量、容错 |
| 平台配置 | 14 | 管理员维护，最终用户看不到 |
| 配额 | 2 | 设在项目或连接上，任务只申请 |
| **应删除** | **7** | 裸 JSON 兜底或概念重复 |

三组最值得先处理：

1. **`writerJdbcUrls` / `writerUsername` / `writerPassword`** —— 用户建任务时要手填**数据湖**的地址、
   账号、密码，每个任务各存一份。轮换一次要改 N 个任务，且普通数据工程师必须知道写入口令。
2. **`readerConfig` + `readerExtraConfig` + `writerConfig` + `writerExtraConfig`** ——
   四个 JSON 兜底框。"config 之外还要有 extraConfig"本身就说明第一个 config 不够用又不敢改。
3. **`taskConcurrency` / `sourceConcurrency` / `projectConcurrency`** —— 三个并发数都让用户填。
   后两个是护源库、护集群的闸门，应当是配额而非输入项。

## 建议的浏览路径

1. **列表页** —— 看"一条接入"作为单一心智对象长什么样。
2. **新建接入 → 选「HTTP / REST API」** —— 这是全篇重点。现网这里是 8 个
   `Input.TextArea` 让用户手写 JSON；这里是 32 个结构化字段，鉴权方式与分页方式
   联动显示对应参数。切换「鉴权方式」和「分页方式」可以看到条件字段。
3. **测试连接 → 下一步** —— 表结构发现、增量列识别、ODS 推导。
4. **下一步 → 策略页** —— 看「密级 / 归属部门」的**继承自连接**标记，点「覆盖」
   会提示进入变更审批。展开底部可看平台生成的 Addax 作业（密钥是引用，不落明文）。
5. **接入详情** —— 漂移、预检、重跑聚到同一个对象下的四个标签页。
6. **设计说明** —— 五个决策各自对应现网哪一处代码问题。

## 文件结构

```text
index.html          外壳
prototype-data.js   连接器 schema 与示意数据
schema-form.js      schema 驱动的表单渲染器（核心机制）
wizard.js           三步向导
views.js            列表 / 详情 / 连接器管理 / 设计说明
app.js              路由
styles.css          样式（沿用 dataworks-kimball 的设计变量）
```

`schema-form.js` 是本原型的论点所在：它不知道 MySQL 和 HTTP API 的任何区别，
六个连接器的表单全部由 `prototype-data.js` 里的 schema 渲染出来。

## 本原型刻意没有处理的问题

1. **服务边界**：`dts-platform` 与 `dts-ingestion` 互相调用（40 个透传方法 + 反向取数据源），
   是循环依赖。属于后端拓扑，需单独立项。
2. **文件体积**：`AddaxJobService` 3284 行、`IngestionTaskResource` 2903 行、
   `IngestionTaskService` 2865 行，均远超项目 800 行上限。落地时会大量触碰，建议先拆再改。
3. **存量迁移**：现有任务的 8 个 jsonb 列如何映射到 schema 字段，需要逐连接器的迁移表与回退方案。
4. **权限模型**：未体现按部门/密级的接入可见性，实际需接 ABAC。

## 已知偏差

- 交互为演示性质：连接测试固定返回成功；表发现返回固定 8 张表，与所选连接器无关。
- 「按规则匹配」模式只展示交互形态，不会真正过滤列表。
- 详情页的运行/漂移/变更数据为固定示意，与列表卡片上的计数不完全一致。
