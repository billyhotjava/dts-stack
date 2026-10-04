# DTS 开发、构建与部署边界

- 开发目录固定为 `/opt/prod/s10/v2.2.3`：仅修改代码、静态检查、review、commit 和 push。禁止在此目录编译、打包或构建镜像；会触发编译的测试也必须移至构建测试目录。
- 构建测试目录固定为 `/data/dts-stack`，替代原 `/opt/prod/s10/deploy` 构建测试路径。代码修改后，先在开发目录 commit & push，再在构建测试目录确认分支和工作区状态，执行 `git pull --ff-only`，核对提交 SHA 后使用仓库正式构建、测试和打包入口。不得复制开发目录的未提交文件或编译产物到构建测试目录。
- 禁止通过 `docker cp`、容器内改代码/JAR/静态资源/依赖、`docker commit`、临时 hotfix 镜像或开发目录 bind mount 给测试容器打补丁。修复必须进入 Git，再走构建测试目录的正式构建流程和部署目录的正式发布流程。
- 正式容器由部署目录的 Compose/交付配置管理；不得从开发目录启动另一套同用途容器。迁移现有容器前核对项目名、持久化数据和挂载，禁止为了清理镜像顺带重建或迁移运行环境。
- 在线和离线测试环境必须使用同一提交对应的正式交付包和镜像；记录提交 SHA、镜像 ID/digest、版本清单及包校验和。包内包含声明的 dbt 模型路径、DAG、脚本和依赖，不依赖本机补丁、构建缓存或部署时联网补装来补齐运行功能。重打标签不等于完成一致性验证。
- 删除镜像前检查所有运行及停止容器引用、正式部署配置、构建基础镜像和离线交付依赖。仅清理已确认淘汰的镜像；禁止 `docker system prune -a`、强制删除被使用的镜像或顺带删除数据卷。
- 验证结果分别报告：源码/测试、正式构建/交付包、容器部署、真实页面验收。未执行的阶段不得宣称完成。上述路径约束优先于通用技能中的原地构建建议。

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **s10-stack** (164138 symbols, 355073 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/s10-stack/context` | Codebase overview, check index freshness |
| `gitnexus://repo/s10-stack/clusters` | All functional areas |
| `gitnexus://repo/s10-stack/processes` | All execution flows |
| `gitnexus://repo/s10-stack/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->