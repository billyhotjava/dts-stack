# T01：收敛数据架构导航、模型 Table 与兼容路由

**优先级**：P1

**状态**：CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E

**证据**：`../../assets/implementation-evidence-20260810.md`

**依赖**：F1～F4 DONE、ADR-86-09

## Contract-first

- Navigation：新增已批准的一级“数据架构”入口；复用现有页面和 command boundary，不复制功能。
- Model UI：搜索/筛选 + 服务端分页 Table；显式多选、批量预检、逐项结果和二次物化入口。
- Hierarchy UI：业务分类、数据域等层级选择继续使用树，不把所有对象机械改成 Table。
- Compatibility：旧菜单、旧深链和带 query/hash 的地址按映射无损跳转，首轮不删除。
- State：empty/loading/error/success 四态与无权限、部分失败、后台运行态均可访问和恢复。

## DoR

- [ ] F1～F4 DONE；目标 route/menu seed、权限和 API 契约已冻结。
- [ ] Chrome 95、真实登录、菜单点击和旧深链样本可用。

## DoD

- [ ] 一级入口、二级视图和页面标题使用客户可理解语言，无 sprint/dbt/内部状态泄漏。
- [ ] 模型列表分页 10，选择跨页策略、批量上限和失败明细与后端契约一致。
- [ ] 所有旧路由样本保留参数并到达唯一目标页，无重定向环。
- [ ] 部门只读角色看不到可写动作；直接调用仍由服务端拒绝并审计。
- [ ] Chrome 95 source-contract、构建和一次集中真实菜单 E2E 通过。
