# 第三方材料、署名与开发说明

## 项目贡献

**R1sum_** 负责本附属的玩法方向、功能取舍、实机体验反馈及发布维护。GitHub 账号为 **Resumresum**。

**本模组主要通过 OpenAI Codex 实现作者的玩法想法。**大部分代码实现、迭代修改、文档编写、自动测试与问题排查由 Codex 完成；R1sum_ 提出玩法构思，决定功能取舍，并进行实机体验与反馈。该说明如实披露开发方式，不把 AI 工具列为上游刀具资源作者。

## SlashBlade: Refabricated

本项目依赖 `26.1.2-1.5.1-Resharped-2.0.5`。该已测试 JAR 的元数据将其作者列为 **Sh1roCu**，许可列为 **MIT**；随附 MIT 许可的版权声明为 **Copyright (c) 2024 M Mysterious Mountain Forging-shop Group**。

本项目保留该发行包内的完整许可文本：[SlashBlade-Refabricated-MIT.txt](docs/licenses/SlashBlade-Refabricated-MIT.txt)。本项目的自定义许可不改变本体原有许可，也不替代其他上游作者的署名要求。

双刀附属调用本体刀具状态、连招与 SA 接口，复用本体提供的动作与渲染资源。本附属的游玩 JAR 不内嵌本体的刀具模型或贴图；玩家需自行安装本体和相应前置。

## 截图中的刀具、模型与贴图

截图来自本体及独立测试实例中已经加载的附属刀具，包含 TwilightTsuki 等来源的资源。它们的模型、贴图、刀名和独有能力归相应作者，展示截图不代表这些资源由本项目原创，也不授予抽取、改作或重新分发第三方资源文件的权利。

公开材料仅随附实机截图，不重新打包这些第三方资源文件。分发他人的刀具模组或修改其资源时，应另外遵守对应项目的许可和署名要求。

## 可选 SlashBlade Combat Compatibility 补丁

`slashblade-combat-compat-26.1.2-0.1.0.jar` 是单独交付、按 **LGPL-2.1-only** 发布的兼容补丁。本双刀附属的自定义许可及禁止收费条款不施加于该补丁本身。

如同时发布该补丁，应保留补丁原有 LICENSE、说明和对应源码，供接收者依其原许可使用。不要把补丁标注成与双刀附属相同的自定义许可。

## 其他依赖与平台

Minecraft、Fabric、Fabric API、Forge Config API Port、MixinExtras、暮色森林及其他刀具模组均为独立项目。本项目不是它们的官方发行物，不表示获得其作者背书。依赖由玩家从各自发布渠道取得，其许可不因本项目而改变。

## 许可名称说明

本项目采用自定义源码可用许可，不以 MIT、LGPL 或其他标准开源许可为本项目主许可。有关开源定义与保留第三方许可的一般背景，可查阅 [OSI Open Source Definition](https://opensource.org/osd) 与 [GitHub 的 MIT 许可说明](https://choosealicense.com/licenses/mit/)；实际使用权限以本项目及各第三方随附的正式许可文本为准。
