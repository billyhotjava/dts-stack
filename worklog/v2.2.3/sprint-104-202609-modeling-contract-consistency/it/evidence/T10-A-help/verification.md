# T10-A 帮助上下文与稳定说明迁移验证

## 范围与提交

- `e55d9123b`：T10-A 代码与本次 sprint 方案补充；`c3e168b31`：校正旧测试仍引用的已删除组件和已迁移规划/标准路由。
- 仅 frontend help-center、ModelImplementationBindingFields 及测试。未改变模型/资产/发布接口，未实现四步向导页面。
- 单次方案复审及该变更的一次聚焦代码 review 均已完成；未发现本切片阻塞。GitNexus impact/detect_changes 为 LOW，解析函数的直接调用包含 HelpCenter 与 HelpCenterPage。

## 执行记录

所有应用测试/构建都在 `/opt/prod/s10/deploy/source/dts-platform-webapp`，开发区只编辑/格式化/提交推送。部署目录通过 git pull --ff-only 获得同一源码，无复制未提交源文件。

| 检查 | 命令/证据 | 结果 |
|---|---|---|
| Node 原生契约 | `node --experimental-strip-types --test src/features/help-center/helpTopics.test.ts src/features/help-center/helpCenter.source-contract.test.ts` | 10/10 PASS，退出0，c3e168b31 |
| 抽屉/时间字段交互 | `pnpm exec vitest run src/features/help-center/HelpCenter.test.tsx src/pages/data-modeling/prototype/ModelImplementationBindingFields.test.ts --reporter=dot` | 5/5 PASS，退出0，e55d9123b；后续提交仅修改 Node 契约引用，无需重复该交互测试 |
| 类型/生产构建 | `pnpm build`，日志 `/tmp/sprint104-t10a-build.log` | PASS，退出0，c3e168b31；Vite 构建 1m 41s |
| 格式/差异 | 7个 owned 源文件由 Biome 格式化；git diff --check | PASS；仓库存在嵌套 root 配置冲突，使用原 webapp 格式配置通过 stdin 格式化，没有迁移/修改仓库配置 |
| 提交 hook | git commit 输出 `Can't find lefthook in PATH` | hook 未执行；未声称通过 hook |
| 页面基线 | 登录中原模型工作台与右上角帮助入口可见 | 仅改前基线；非改后验收 |
| 部署/Chrome95/离线安装 | 本次未替换运行容器/镜像 | 未执行，不记 DONE |

首轮 Node 失败是旧契约引用 `pages/DimensionalModelingWorkspace.tsx` 和原规划路由；对照现有 DataModelingSurface、规划重定向及 `/data-architecture` 后修正测试。未为通过测试恢复已删除页面或更改生产路由。

已验证行为：合法/非法 step 与旧路径兼容、帮助内容唯一且可发现、抽屉内查看主题不离开编辑器、输入保留、关闭焦点回到帮助按钮、重新打开回到页面主题；字段作用修改不改变数据类型，未选择时间字段的数据不被修改。

IT-18 当前组件/源码部分已通过；四步真实页面、Chrome95 1366×768/窄屏与正式离线交付仍待 T10-B/T14 验收。T10 总状态保持 IN_PROGRESS。

生产构建包含类型检查与 legacy browser bundle；仅出现大分包体积提示。构建通过不等于 Chrome95 实机验收或容器已部署。
