# F5 外部 Chrome 首轮验收与修复记录

源码：36655e29a92a5671e77197b2a8738b3d7ead4aa7。正式 builds/dts-build.sh 后端编译与前端 TypeScript/兼容版打包通过；平台/前端已按 deploy Compose 更新。无手工数据库变更，无新增代码测试。

浏览器：桌面可见 Google Chrome 152.0.7977.82，通过 CDP 9335 外部控制，测试账号 xiezm。另有可见 Chromium 95.0.4638.0，兼容性待本轮修复部署后验证。原 MCP 会话识别为 HeadlessChrome，未将其算作可见 Chrome 验收。

## 首轮发现

| 编号 | 现象与根因 | 整改与状态 |
|---|---|---|
| F5-B01 | 已提交 DBT_MANAGED 包保留 visualImplementation 的完整 inputs，但 modelDraftFromView 只恢复转换配置，未把完整 inputs 传给来源目录，初次打开出现 PIN_REQUIRED | 补齐已提交可视化配置与作者草稿的完整输入恢复；未锁定的设计引用提供显式选择版本动作。源码修复，待新镜像复测 |
| F5-B02 | 打开上游使用普通绝对 href，未经过 HashRouter，路径丢失 # 路由入口 | 使用既有 React Router Link，保留独立标签页。源码修复，待复测 |
| F5-B03 | 候选行结构从 label 变为 div，原 CSS 只匹配直接子 label，模型名、版本和动作挤在一起；刷新/分页按钮未复用产品按钮 | 使用既有 Button 与局部候选行/筛选栏样式，含窄屏换行。源码修复，待桌面及 Chrome 95 复测 |

## 首轮已验证

- IT-34：cost_amount 草稿暂存成功；提交返回 422 MODEL_IMPLEMENTATION_SOURCE_FIELDS_INVALID，目标 actual_cost_amount、fieldPath 和关联 ID 明确。改为 src_0.actual_cost 后 validate/commit 200，物化页面已完成；上游 REUSE，根模型 BUILD。
- IT-35：保存错误字段后重新加载，等待作者上下文恢复后值仍为 cost_amount。初次渲染的已提交配置不能当成恢复完成证据。
- IT-29：无实现草稿“项目任务快照明细_v1.0”禁选且说明尚未提交实现；同方案有有效实现、已物化但仍为 DRAFT 的上游可引用。
- IT-35：先有 0909 明细，再选排序靠前的 0908 明细，请求 inputs 仍为 0909/src_0、0908/src_1；删除原 src_0 后原字段变为明确失效引用，不误绑同名字段。重新加载恢复已保存草稿。
- IT-33：中断上游可用性请求，已知无实现候选继续禁选，字段编辑保留；恢复网络后可刷新。

原始请求响应：F5-browser-36655e29a.json；截图：F5-IT34-field-rejected-36655e29a.png、F5-IT34-materialized-36655e29a.png。不记录登录密码或请求认证头。

## 尚待验收

本轮三项修复的 Chrome 152/95 复测；字段目录失败；20/200 批量边界与耗时；仅实现漂移及明确更新；物理源/生成模式；归档与历史修订边界。正常入口不能构造的并发/归档防御分支继续单列未覆盖，不手工改库造样本，不据此标 F5 DONE。SQL 实际执行次数尚未采集，不能把静态批量调用数称为运行测量。

## 第二轮外部 Chrome 95 发现：F5-B04

源码/镜像 68d896eeae498518ce99e4a5475ce22c08ec9ca7。独立样本 DWD `0b225a86-f2d7-4e5c-b97f-6ba780939aad` 先 r2/无实现，DWS `77406c05-01e2-402f-8c8f-d8a93ebd3620` 的 r2 设计正常引用它。DWD 提交后为 r3/i1；DWS 页面明确选择 r3/i1 后暂存，POST authoring-drafts 仍返回 412 DBT_DRAFT_DEPENDENCY_PIN_STALE，关联 ID `b91ad6d0-9c3c-4834-b1b9-7e46beda67f6`。

根因：createSession 必须先创建后保存，而服务端创建源包时先解析旧 ModelSpec 依赖，未接收用户新选择就拒绝，形成修复入口闭环。整改：首次作者草稿仅初始化模板；已有实现若仅依赖 pin 失效，恢复精确原始证据供编辑。无初始依赖快照的作者草稿必须完整执行提交校验。未改变权限/CAS，未改数据库。源码修复待正式构建复测。

第二轮已确认新版本选择入口和 HashRouter 链接地址生效；完整点击/窄屏验收仍按最终矩阵记录。
