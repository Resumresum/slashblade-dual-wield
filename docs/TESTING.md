# 测试范围与兼容边界

[返回首页](../README.md)

本页区分当前动作检查与历史玩法基线；历史通过不等于本版重新覆盖了全部配方、养成、Boss 和第三方能力。

## 当前动作与调度检查

- 当前动作数学检查覆盖 **16,884 个姿态**与 **67,536 个模型朝向**，检查刀身、刀光轨迹和朝向关系。
- 完整时序检查覆盖十二次交替提示、双刀齐斩、四拍 SA、延后接触与短刀三连刺间隔；它验证调度规则，不代替所有环境的实战。
- 当前五段正常速度、无声实机片段见[实机展示](GALLERY.md)。常规目标关闭 AI、400 点生命；招架片段使用一次真实敌方攻击。
- 最终 `0.4.12-experimental` 游玩 JAR 已通过集成服务器 `-Combat` 探针 **9 项检查**；配方冒烟检查中的烧制模具与 `FOX_WHITE` 成型均通过。这不代表独立服务器或真实多人联机已验证。
- 最终游玩 JAR SHA-256：`5FF5E5ECE041C8D0B114BE755D3D17948784D5097B7023A25B2F6CB326BA1ACA`。

配方道具图于 0.4.1 独立截图世界拍摄：四张实际物品渲染图，五张工作台／普通熔炉原生界面图，拍摄前检查实际菜单产物。当前制作逻辑保留；这些图片仅说明配方和物品，不证明本版全面战斗回归。[截图检查摘要](validation/041-recipe-photos.txt)。

### English scope

Current motion checks cover **16,884 poses** and **67,536 model orientations**, plus full sequence scheduling, delayed contact, and triple-thrust spacing. Five fresh silent, normal-speed clips demonstrate the current moves; regular targets have AI disabled and 400 HP, with one real enemy attack for the guard. The final **0.4.12-experimental** gameplay JAR passed **all nine integrated-server combat probe checks**, plus fired-mold and `FOX_WHITE` recipe smoke checks. This is not dedicated-server or real multiplayer verification. Earlier crafting, growth, Boss, and blade-catalog results below are historical baselines for retained logic, not a fresh exhaustive 0.4.12 regression. Real multiplayer and third-person custom full meshes remain incompletely tested.

## 历史玩法基线

| 环境 / 项目 | 已有结果与范围 | 原始摘要 |
| --- | --- | --- |
| 0.4.1 双刀附属＋战斗兼容补丁，单人独立实例 | 配方、复制、成长、招架、反击 SA、跟斩、爆发、三连刺、横斩等回归记录通过；78 把短刀实际招架 | [041-complete.txt](validation/041-complete.txt) |
| 0.4.1 新增攻击 Boss 与敌对单位受控检查 | 六类新增攻击、九头蛇实际部位、重叠去重、原有无敌 / 护甲、敌对狼 / 铁傀儡 / 蜜蜂及中立保护 | [041-boss.txt](validation/041-boss.txt) |
| 0.4.1 仅本体依赖 | 27 把制作胁差的目录、资源与配方；真实短刀攻击、横斩可伤害僵尸；未加载可选补丁 | [041-base.txt](validation/041-base.txt) |
| 0.4.0 外观视图检查 | 双长刀、长刀＋胁差的正 / 背 / 侧 / 第一人称；额外 45 把刀侧视；短刀蓄力、SA 出刀和收势截图 | [040-views.txt](validation/040-views.txt) |

完整环境中的 78 把短刀由 **73 把制作胁差＋5 把原生小太刀**组成。它证明这些样本能加载模型贴图、进入招架并处理受击反击，不证明第三方所有特殊能力均已完成验证。

## 可选战斗兼容补丁

双刀附属可以不安装此补丁。需要暮色九头蛇部位命中和敌对中立单位索敌修复时，再安装独立 JAR：

| 依赖 | 补丁要求 |
| --- | --- |
| Minecraft | 26.1.2 Fabric |
| SlashBlade: Refabricated | 26.1.2-1.5.1-Resharped-2.0.5 |
| Twilight Forest | 4.9.768 |
| MixinExtras | ≥0.5.5 |

补丁仍按其原有 **LGPL-2.1-only** 许可发布，和双刀附属的自定义许可分开处理。发布补丁二进制时须同时提供其对应源码、原许可与说明；主项目 README 不替代补丁自己的交付文档。

**九头蛇的身体不是无甲弱点。**原生规则对身体、颈部、尾部及闭嘴头的单次伤害除以 8 后四舍五入，小伤害可能变成 0；打开嘴的头部使用不同的受伤规则。补丁让攻击送到真实部位，不绕过这些规则。

## 仍需继续验证

- 独立服务器、真实多人联机、不同玩家互相观察的动作同步与延迟体验，以及第三人称自定义全模型。
- 长期生存、自然 Boss 战全流程、大型整合包的组合冲突。
- 每个第三方附属的独立物品类、额外组件、自定义 SA 与特殊持久数据。
- 不同 JEI / EMI 版本对本附属动态成型配方的完整展示。
- 尚未测过的渲染、动作、相机与战斗系统模组组合。

当前源码使用客户端与服务端分工，但这本身不是多人验证结果。对服务器的运营许可也不代表已经宣称支持服务器。

## 反馈模板

提交问题时，可提供以下内容：

```text
双刀版本：
Minecraft / 加载器版本：
拔刀剑本体版本：
主手刀名、来源模组：
副手刀名、来源模组：
是否装战斗兼容补丁 / 暮色版本：
复现步骤：
预期结果 / 实际结果：
相关日志与截图：
```

崩溃请保留崩溃报告及对应运行日志；分享前去除私人信息。只听到声音、看不到技能特效或面板伤害不同，请同时描述视角、目标部位和刀具状态，便于区分视觉问题、护甲规则与实际命中问题。
