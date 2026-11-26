# 更新说明 / Changelog

## 0.4.12-experimental · 十二连斩与第一人称动作更新

发布标签：`v0.4.12-experimental`。游玩文件：`slashblade-dual-wield-26.1.2-0.4.12-experimental.jar`。

- 双长刀开场由八击改为十二击，每 4 刻 / 0.2 秒主副交替；齐斩仍在第 52 刻，四拍 SA 仍在第 56、60、64、68 刻。
- 开场每击在最低伤害下限处理后按旧每击的 2/3 缩放，保留固定属性与评级下的基础伤害预算。实际护甲、评级变化与逐击结算会影响总扣血；主刀逐击耐久成本保留，开场主刀命中由 4 次增为 6 次。
- 十二连斩采用 8 次大幅与 4 次紧凑挥动，加入陡斜、反向斜面与纵深倾角；刀身和刀光走同一条物理路径。
- 普通斩击在动作提示后 2 刻接触；短刀三连刺每刺延后 1 刻接触，仍保持 3 刻间隔。
- 第一人称短刀基础三段与三连刺采用右侧、刃口向上的收尾／刺击姿态；原版左主手设置会镜像。没有重做玩家全身与手臂骨架。
- 五段当前实机 GIF 替换旧动作展示；保留准确的装备、道具与实际配方截图。
- 被动跟斩、招架、反击 SA、制作、复制与 70/30 成长规则保留。多人联机、第三人称自定义全模型与长期生存仍未充分验证。

### English

- The opening burst now has twelve alternating cuts at 4-tick / 0.2-second intervals. The double strike remains at tick 52 and SA cues at ticks 56/60/64/68.
- Each opening hit is 2/3 of the old hit after the minimum-damage floor, preserving the base budget at fixed stats and rank. Armor, changing rank, and per-hit rules can alter actual damage. Main-blade durability remains per hit; opening main-blade hits increase from four to six.
- Eight large and four compact swings use varied steep/reverse planes and depth cant, with blade and trail following the same physical path.
- Regular cuts make contact two ticks after their cues. Triple-thrust contacts are one tick after each cue, with three-tick spacing retained.
- First-person short-blade finish/thrust poses are right-side and edge-up, mirrored by the native left-main-hand setting. The player full-body/arm rig is retained.
- Five current gameplay GIFs replace old action displays; equipment, item, and recipe images remain. Follow-up, guard, SA follow-up, crafting, copying, and growth rules are retained.

验证范围及历史基线见[测试说明](docs/TESTING.md)。


## 0.4.1-experimental · 初版玩法基线

这一版将双长刀输出、胁差招架和主手短刀快攻整理为一套只增加一个按键的双刀体系。

### 玩法内容

- 双长刀被动跟斩：主刀普通刀招命中后 0.2 秒追加 75% 基础伤害的副刀斩，带独立表现且不增加击退。
- 双长刀按 R 连续爆发：八次交替快斩、双刀齐斩和主副交替四拍 SA，加入装备锁定、免控和实际命中目标的短暂移动限制。
- 副手胁差一键招架：1 秒窗口、首个有效受击减伤 80%、环形反击和 0.8 秒无敌；成功后可再按 R 追加副刀普通 SA。
- 主手胁差快斩链与 V 突进后的连续三连刺；主短副长使用 R 释放前方扇形横斩。
- 同侧错角副刀挂载，以及主手短刀的第一人称抬刀蓄力、抽刀斩出和收势停顿。

### 制作与养成

- 泥坯、熔炉烧制模具及工作台成型流程；样板长刀返还，模具消耗一次。
- 高级模具复制已支持的附魔、击杀数、精炼及持久设置；耀魂归零，两刀独立成长。
- 副刀实际参与目标战斗后，符合条件的击杀数、耀魂按主刀 70% / 副刀 30% 分配，余数持久保存。
- 创造模式「拔刀剑双刀」标签及中英文物品名称。

### 本次兼容更新

- 新增快斩、三连刺、横斩、爆发、反击和跟斩接入统一目标处理。
- 与独立战斗兼容补丁共同安装时，按实际九头蛇部位判断范围、方向和视线，保留原生护甲、无敌和弱点处理；多部位命中不重复放大伤害。
- 延迟跟斩复查原先命中的部位；修复目标选择时继续保留中立保护规则。
- 不安装暮色森林和兼容补丁时，双刀附属仍能在本体环境使用。

### 发布状态

这是 **公开测试版 / Pre-release**，不是已完成所有环境验证的稳定版。独立服务器、真实多人联机、长期生存及第三方独有能力尚待继续验证。现有证据见[测试范围](docs/TESTING.md)。
