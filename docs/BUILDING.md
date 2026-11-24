# 开发源码与本机构建

普通玩家请按[首页安装说明](../README.md)下载游玩 JAR，无需构建源码。

本项目目前使用 PowerShell 调用 Java 编译器的本机开发流程，没有提供 Gradle / Loom 工程。`build.ps1` 与 `test.ps1` 使用开发时的目录约定；在其他电脑上须调整脚本开头的 Java、游戏运行库和依赖来源路径。公开源码包不含 Minecraft、运行库、前置模组、存档或登录信息。

## 开发依赖

- Java JDK 25，包含 `javac` 和 `jar`。
- Minecraft 26.1.2 原版 JAR、对应启动元数据、游戏依赖库与资源目录。
- Fabric Loader 0.20.1 与对应依赖库。
- Fabric API `0.155.3+26.1.2`。
- Forge Config API Port `v26.1.5-mc26.1.x-Fabric`。
- SlashBlade: Refabricated `26.1.2-1.5.1-Resharped-2.0.5`。

构建脚本默认从 `D:/MC_mod_test/runtime` 读取游戏运行库，从 `D:/MC_mod_test/tools/mojang-java25/bin` 调用 JDK，从 `D:/Minecraft/slashblade-stage1-test/instance/mods` 复制前置模组。重现环境时，应使用合法取得的对应发行包，或将这些路径改为自己的等价目录。

## 构建主模组

在项目根目录用 PowerShell 执行：

```powershell
.\build.ps1
```

源码在 `src/main`，产物写入 `build/libs/slashblade-dual-wield-26.1.2-0.4.1-experimental.jar`。构建脚本不会下载前置，依赖缺失时须先补齐环境。

## 独立测试探针

`src/probe` 用于开发中的自动检查，不是游玩内容，不能作为前置装进长期存档。构建探针可执行：

```powershell
.\build.ps1 -WithProbe
```

测试入口的目录保护与数据来源同样依赖本机约定，迁移后必须一并审查，确保只使用新建的独立测试世界。已有结果见[测试范围](TESTING.md)。

公开源码用于检查与修改本版实现；当前不承诺一条命令在任意电脑上自动配置开发环境。各依赖的源码与许可仍由其原项目提供。
