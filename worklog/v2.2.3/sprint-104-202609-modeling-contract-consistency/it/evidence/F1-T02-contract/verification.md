# F1-T02 首批源码验证

- 源码提交：b85753abe5e3bec6fde8032f4db61b2af992a518，已推送 origin/v2.2.3。
- `/opt/prod/s10/deploy` 初始分支 v2.2.3、工作区干净，已通过 git pull --ff-only 更新到相同 SHA。
- 开发目录只执行 Node v24.14.1 原生无编译契约检查：原 37/24/13，首批修复及新增反例后 40/40；见 green.tap。
- 在部署目录执行工作台回归：`pnpm exec vitest run src/pages/data-modeling/prototype/services/modelWorkbenchService.test.ts --reporter=dot`，退出码 254，ERR_PNPM_RECURSIVE_EXEC_FIRST_FAIL / Command "vitest" not found。目录无 node_modules，测试未实际开始，不记为产品用例失败或通过。
- 正式前端构建入口位于 `builds/dts-build.sh` / `builds/dts-platform-webapp/Dockerfile`，宿主源码测试需先在部署目录按锁文件准备依赖；不能引用开发目录 node_modules 或复制未提交源码。正式构建本轮未执行。
- 提交提示 Can't find lefthook in PATH，钩子未执行；已完成 git diff --check、JSON 解析、文档链接检查、一次聚焦源码检查，以及 GitNexus detect_changes（LOW）。索引存在漏识别，未据此宣称零调用方。
- 未执行 Java 编译测试、镜像构建/交付包、容器重建、浏览器或真实物化；这些阶段仍待验证。
- 下一步：F1-T02 其余阶段/草稿入口契约及部署目录测试基线；然后按修订依赖开展 F1-T05/F1-T06/F1-T03/F1-T04，F1-T08 最终验收。
