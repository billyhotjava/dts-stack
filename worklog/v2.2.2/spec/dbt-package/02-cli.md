# dbt Package CLI Contract

## 命令集合

第一版固定 5 个命令：

- `install`
- `run`
- `register`
- `reset`
- `status`

## install

```bash
dts-dbt-package install <zip>
```

用途：

- 安装 dbt package 到 `services/dts-dbt`

默认行为：

- 校验 `package-manifest.json`
- 写入受控目录
- 更新安装注册表

不会做：

- 不执行 dbt
- 不创建项目空间
- 不注册逻辑建模

## run

```bash
dts-dbt-package run --package <code> [--selector <selector>] [--target <target>]
```

用途：

- 运行 package 对应的 dbt 任务

默认行为：

- 可先执行 `dbt seed`
- 执行 `dbt run/build/test`

不会做：

- 不创建项目空间
- 不注册逻辑建模

## register

```bash
dts-dbt-package register --package <code> --plan-id <uuid>
```

或：

```bash
dts-dbt-package register --package <code> --create-plan --plan-name <name>
```

用途：

- 将 dbt 模型显式注册到平台逻辑建模

约束：

- `--plan-id` 与 `--create-plan` 二选一
- 未指定 plan 目标时必须失败

## reset

```bash
dts-dbt-package reset --package <code> [--factory] [--force]
```

用途：

- 卸载某个 package

默认行为：

- 删除 package manifest 中声明的文件
- 清理安装注册信息

不会做：

- 不清空其他 package
- 不删除平台骨架文件

## status

```bash
dts-dbt-package status
```

用途：

- 查看当前安装的 package、版本、manifest 路径和工作区状态

## 返回码

- `0` 成功
- `2` 参数错误
- `3` manifest 校验失败
- `4` workspace 冲突
- `5` dbt 执行失败
- `6` register 失败
- `7` reset 失败

## 禁止的隐式动作

以下行为在第一版中必须禁止：

- `install` 自动执行 `run`
- `run` 自动执行 `register`
- `register` 在未指定 plan 时自动创建项目空间
- `status` 触发任何写操作
- 任何 CLI 默认触发工作区自动扫描并写回逻辑建模
