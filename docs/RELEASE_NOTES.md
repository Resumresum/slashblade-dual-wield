# 拔刀剑：胁差与双刀 0.4.12-experimental

[下载 / Download](https://github.com/Resumresum/slashblade-dual-wield/releases/tag/v0.4.12-experimental) · [中文玩法](../README.md) · [English guide](../README.en.md)

双刀接力，胁差迎击。一个可改键的「副刀动作」键（默认 **R**），让不同主副手搭配拥有各自的节奏。

## 本版更新

- **双长刀十二连斩：**每 0.2 秒主副交替，2.6 秒双刀齐斩，2.8／3.0／3.2／3.4 秒接四拍 SA。每击在最低伤害下限后按旧八连斩的 2/3 缩放；固定属性与评级下保留开场基础伤害预算，实际扣血仍受护甲与逐击规则影响。主刀逐击耐久保留，开场主刀命中由 4 次增为 6 次。
- **动作更清楚：**8 次大幅＋4 次紧凑挥动，刀身与刀光沿同一物理轨迹；普通斩击延后 2 刻接触。胁差基础三段与三连刺采用右侧、刃口向上的第一人称姿态，左主手设置会镜像；三连刺间隔仍为 3 刻、每刺延后 1 刻接触。
- **五段新实机演示：**爆发、胁差基础三段、三连刺、被动跟斩与真实受击招架。爆发片段只展示十二连斩与齐斩，不包含后续 SA 或兜底。
- **原有体系保留：**0.2 秒／75% 被动跟斩不额外击退；1 秒招架、80% 减伤、反击与可选副刀 SA；泥坯、普通熔炉模具与高级复制；副刀实际参与后的 70/30 成长。

## 安装与验证

游玩文件：`slashblade-dual-wield-26.1.2-0.4.12-experimental.jar`。放入对应 Fabric 实例的 `mods`，升级时只保留一份双刀附属。源码 ZIP、源码 JAR 和测试探针不能代替游玩文件。

需要 **Minecraft 26.1.2、Java 25+、Fabric Loader 0.20.1+、SlashBlade: Refabricated 26.1.2-1.5.1-Resharped-2.0.5**，以及本体前置 Fabric API、Forge Config API Port。可选 Boss 补丁独立发布，另需 Twilight Forest 4.9.768、MixinExtras ≥0.5.5；双刀附属本身不要求暮色。

最终游玩 JAR 通过动作检查（16,884 个姿态、67,536 个模型朝向）、集成服务器战斗探针 9 项与烧制模具／FOX_WHITE 配方冒烟检查。配方、养成与 Boss 的完整历史基线见[测试范围](TESTING.md)。独立服务器、真实多人、第三人称自定义全模型与长期生存仍未充分验证，玩家全身与手臂骨架没有重做。请先在独立世界体验。

## English release summary

The burst now opens with **twelve alternating cuts at 0.2-second intervals**, then the double strike at 2.6 seconds and SA cues at 2.8/3.0/3.2/3.4 seconds. Opening hits are scaled to **2/3 after the damage floor**, preserving the base budget at fixed stats and rank; armor and per-hit rules can change actual damage. Main-blade durability remains per hit, with six opening main hits instead of four.

Eight large and four compact swings share one physical path with their trails. Regular contacts are two ticks after their cues. First-person wakizashi finish/thrust poses are right-side and edge-up, mirrored for a left main hand. Triple thrusts keep three-tick spacing and contact one tick after each cue. Existing follow-up, guard, SA, crafting, and growth rules remain.

Download `slashblade-dual-wield-26.1.2-0.4.12-experimental.jar` from tag `v0.4.12-experimental`. Requirements are listed above and in the English README. The final JAR passed **16,884 pose / 67,536 orientation checks**, **nine integrated-server combat probe checks**, and fired-mold / FOX_WHITE recipe smoke checks. Dedicated servers, real multiplayer, third-person custom full meshes, and long-term survival remain incompletely tested. Player full-body and arm rigs were not rebuilt.

作者 **R1sum_** 负责构思、取舍与实机反馈；本模组主要通过 **OpenAI Codex** 实现，大部分代码、迭代与自动验证由 Codex 完成。免费使用、修改与免费分发须保留署名及许可；禁止售卖、收费下载及付费解锁，正常服务器运营适用许可例外。第三方资源与独立补丁各自保留原许可。完整条款见 [LICENSE](../LICENSE) 与 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。

By **R1sum_**, with most implementation, iteration, and automated verification produced using **OpenAI Codex**. Free use, modification, and redistribution require attribution and the license; selling, paid downloads, and paid feature access are prohibited, with the license's exception for ordinary server operation. Third-party resources and the separate patch keep their own licenses.
