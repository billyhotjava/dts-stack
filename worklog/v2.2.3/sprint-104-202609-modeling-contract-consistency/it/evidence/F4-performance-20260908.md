# F4 性能诊断与验证记录

## 修复前现场（2026-09-08）
- 源码/部署checkout：200812f71094a78020d866a79ee9257be7ba02d4，开发目录无改动；deploy存在既有未跟踪交付/验收文件，保持不动。
- 平台日志18:07–18:09反复 Hikari total=10, active=10, idle=0，30000ms超时，waiting=9–14；DefaultDestinationSyncService同步失败，调度任务也出现连接超时。
- PG读取pg_stat_activity：8条idle in transaction约34秒，最后语句为modeling_warehouse_plan owner/department查询；并非已证明数据库锁等待。
- CPU快照 platform=1.14%，PG=18.02%；不据此泛化全部时段资源使用。
- CUA实际读取已登录 https://bi.yuzhicloud.com/#/data-modeling/dimensions/workbench：17条，首屏10行，物化/目录/分析三列均读取失败。
- G0沿用既有正式Maven容器及deploy Vitest入口；浏览器/实例/真实样本可访问。当前浏览器不是Chrome95证明。

## 验收登记
| IT | 内容 | 状态 |
|---|---|---|
| IT-25 | 聚合事务及身份回归 | PENDING |
| IT-26 | 限流/部分失败/刷新/过期响应 | PENDING |
| IT-27 | 首屏分阶段加载 | PENDING |
| IT-28 | 正式构建/制品/部署/运行时延/Chrome95 | PENDING |

原始诊断日志临时保存在/tmp/dts-modeling-performance-platform.log，不提交含请求身份的全量日志；以上保留最小必要事实。

## 实施与回归
- c2c068125：F4-T01–T04、追溯矩阵、现场账本和首批失败用例。
- 60ff381d8：将新增事务测试纳入现有编译白名单；此前 Maven success 未执行该测试，不计 PASS。
- RED：原事务实现2/2失败（/tmp/s104-f4-red-java-included.log），原前端1失败/4通过（/tmp/s104-f4-red-ui.log），断言成功模型不应被另一模型失败抹掉。
- 73697f73a：聚合NOT_SUPPORTED、状态并发2/逐行提交/AbortSignal取消、列表/编辑资料分阶段加载；GitNexus影响及detect_changes LOW。
- 前端 GREEN：4文件83项通过（/tmp/s104-f4-green-ui.log），含上下文/新建深链/交付Hook回归。jsdom伪元素getComputedStyle警告不是失败。
- 代理修复前最近60条交付请求：200=24，499=36，Duration中位49.930s/P95=50.005s/最大50.009s；是顺序日志样本，非受控压力测试。截取时最新StartUTC=2026-09-08T10:28:37Z。
- Biome存在根/子目录双root配置，未迁移全局配置；使用原webapp格式配置的临时副本运行formatter，仅应用本轮改动区域，避免无关格式改动。

## 发布与运行检查约定
使用release-safety/operability-pack做本次差异检查：无迁移/回填、无REST字段删除或鉴权更改。仅platform及platform-webapp两镜像；部署前保留旧镜像ID，使用原deploy Compose项目及原挂载。回退需要通过正式版本清单/Compose恢复旧镜像，不修改容器；本轮尚未演练回退，不标G3完整PASS。
旧platform=sha256:9ca5a151017b35c93e1e854d747a2bd4408c1c4bc14a18f81c1186205ca2cd95；旧webapp=sha256:7e82f84808c0e8df6dcf6dc6954041f5377f9d81a7fcbd32cbbbf45fb17c4f65。

运维：打开模型列表后逐行状态完成、单条异常不影响其它模型。若5分钟内任一Hikari连接等待超时或delivery-status 499/5xx超过1%，登记P1，先读取platform日志和PG pg_stat_activity，再对照Traefik RequestPath/StartUTC/Duration/OriginStatus。不可用状态不能当业务失败；先刷新单页，不重复发起物化/发布。不新增告警基础设施，阈值为本Feature运行核验规则，尚未自动化部署。

## 用户交付方式修正
用户明确要求不出补丁包、直接替换原镜像。本次采用原deploy Compose定向up -d --no-deps，仅替换platform/webapp；此前自动生成的tar.gz不用于此次更新，后续不再生成包。F4-T04以正式镜像与容器/页面证据验收，不把补丁包作为门槛。
正式构建完成：源码73697f73a；后端30/30、前端83/83；前端pnpm build含tsc和LEGACY_BROWSER_BUILD成功。新platform=sha256:750a6ba63b8bff5ce362210a2f9658065acdab0e909e651cf6ccb93d1faf95d8；新webapp=sha256:0b4c2572ce6edfa088ac9ebb20689844695946e96d367063926405946be919f0。镜像revision标签均为73697f73a605399c793f691e8d76a48a2a499715。

## 直接镜像替换与页面验证（2026-09-08 18:49–18:55）
- 原deploy项目：docker compose --env-file .env --env-file imgversion.conf -f docker-compose-app.yml -p deploy up -d --no-deps dts-platform dts-platform-webapp；退出0，platform healthy、webapp running。运行镜像ID与上文新镜像一致，未创建另一套同用途实例。
- 新登录会话复用浏览器新标签，17条模型首屏状态全部恢复；已物化/资产登记/分析准备状态按原证据展示，未将“暂无当前证据”冒充失败或成功。
- 真实刷新捕捉到“正在准备编辑资料，模型列表可先浏览”，编辑/新建临时禁用，资料就绪后按钮恢复；第二页、返回第一页、进入0908项目任务快照明细详情均成功，详情显示“建模已完成”。未发起物化/保存/发布。
- 36个delivery-status请求全部200，P50=0.139s，P95=0.522s，最大0.522s；见F4-runtime-metrics-73697f73a.json。与修复前60条日志样本比较属于实际浏览器操作前后观察，不冒充同负载受控压测。
- 新容器截至页面复验未出现Hikari Connection is not available。旧标签刷新曾出现空白且控制台无已捕获错误；新标签正常，因此不宣称旧标签问题根因已解决，保留正常新标签供用户使用。
- IT-25事务专项、IT-26列表正常/刷新/分页、IT-27实际分阶段加载均有通过证据。Chrome95真实引擎、窄屏、固定10并发3轮压测及回退演练未在本轮执行，F4-T04与F4保持IN_PROGRESS，不宣称Sprint全部完成；直接镜像替换要求已完成，补丁包N/A。
