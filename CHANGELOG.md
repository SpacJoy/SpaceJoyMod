<!-- @format -->

# CHANGELOG

***

# V0.15.0 (2026-11-07)

**配方书支持鼠标滚轮翻页，版本号收敛为单一真源**\
Full Changelog (相对于 0.14.1): [https://github.com/SpacJoy/SpaceJoyMod/compare/b679c89...v0.15.0](https://github.com/SpacJoy/SpaceJoyMod/compare/b679c89...v0.15.0)

## ✨ 配方书滚轮翻页

- **鼠标放到配方书上滚动就能翻页**：不用再去点书页底部那两个小箭头。配方书的分页是纯客户端状态（`currentPage` 加 20 个 `RecipeButton`），翻页只需改页码再重建那 20 个按钮，所以不重写布局，把原版私有的 `updateButtonsForPage()` 用 `@Invoker` 出来复用，滚轮和点箭头走同一段代码。
  - `mixin/recipebook/MixinRecipeBookPageScroll.java`：管翻页本身。浮层（`OverlayRecipeComponent`）开着时不拦，滚动该给浮层用；已经翻不动时返回 `false`，不吞掉这次滚动
  - `mixin/recipebook/MixinRecipeBookComponentScroll.java`：自己算书本矩形判定悬停 —— 26.3 的 `RecipeBookComponent#isMouseOver` 硬编码 `return false`，指望不上；矩形与 `extractRenderState` 里那次 `blit(..., 147, 166)` 同源
  - `mixin/recipebook/MixinAbstractContainerScreenRecipeBookScroll.java`：挂在 `mouseScrolled` 的 `HEAD`，翻不动就放行 —— 原版这个方法还负责把滚动转给 `ItemSlotMouseAction`，吞掉会连那个一起坏
  - `mixin/recipebook/RecipeBookScreenAccessor.java`：`AbstractRecipeBookScreen` 的 `recipeBookComponent` 是 private，用 `@Accessor` 取出来
- **滚轮向上翻上一页**：`scrollY > 0` 对应上一页，与原版滚轮约定一致。
- **配置项 `recipeBookScroll` 默认开启**，开关在配置界面的「界面」页。

## 🛠️ 构建

- **版本号改为单一真源**：jar 文件名和 jar 内 `fabric.mod.json` 的 `version` 都来自 `gradle.properties`，但 `processResources` 里的 `expand` 变量不被 Gradle 当成任务输入，改完版本号再构建时该任务仍判 `UP-TO-DATE`，于是文件名是新号、jar 里还是旧号。
  - `build.gradle`：`processResources` 补 `inputs.property 'version', version`，堵住根因
  - 新增 `verifyModVersion` 任务（挂在 `check` 上）：核对 `gradle.properties`、jar 文件名、jar 内 `fabric.mod.json` 三处一致，不一致直接让构建失败
  - 新增 `bumpVersion -Pbump=major|minor|patch`：递增并写回 `gradle.properties`，按单字节编码读写以保住首行那段非 UTF-8 注释

## 🧹 工程

- **两个共用接口移出 mixin 包**：`SpaceJoyRecipeBookScrollable` / `SpaceJoyRecipeBookPageTurner` 放到 `feature/recipebook/`。mixin 包被 `spacejoy_mod.mixins.json` 声明为混入包，里面的类不能被普通代码直接引用，否则启动时抛 `IllegalClassLoadError: ... is in a defined mixin package ... and cannot be referenced directly`。

***

# V0.14.1 (2026-11-07)

**修掉 Iris 换包的优先级错向与多余管线销毁**\
Full Changelog (相对于 0.14.0): [https://github.com/SpacJoy/SpaceJoyMod/compare/4b4767f...b679c89](https://github.com/SpacJoy/SpaceJoyMod/compare/4b4767f...b679c89)

## 🐛 修复

- **换包早切优先级从 1500 改为 900**：`priority` 数字越大越晚执行，想先动手必须写更小的数。0.14.0 用 1500 时实测日志里 Iris 的 `Reloading pipeline on dimension change` 每次穿门照常出现，我们的 `dimension switch hook reached` 总晚一步（00:21:24 vs 00:21:25）。同时把早切路径的 `reason` 改成 `dimension change, before pipeline`，日志能直接分辨是早切生效还是 tick 路径补救。
- **吞掉 Iris 多余的管线销毁**：Iris 判断维度变化用的是引用比较（`getCurrentDimension() != lastDimension`），而 `getCurrentDimension()` 每次都 `new NamespacedId`，条件恒为真，对齐 `lastDimension` 拦不住它。改为在早切成功后打一段 250ms 时限许可，`MixinPipelineManagerRebuildGuard` 据此取消 `PipelineManager#destroyPipeline`，使随后的 `preparePipeline` 命中缓存，管线重建与 `allChanged` 全量区块重建一起省掉。

***

# V0.14.0 (2026-11-06)

**抢在 Iris 之前换包，省掉一次白建的渲染管线**\
Full Changelog (相对于 0.13.0): [https://github.com/SpacJoy/SpaceJoyMod/compare/6881d8b...4b4767f](https://github.com/SpacJoy/SpaceJoyMod/compare/6881d8b...4b4767f)

## ✨ Iris 维度换包提速

- **跨维度换包不再编译两遍管线的第一遍**：Iris 自己在 `Minecraft#updateLevelInEngines` 的 `HEAD` 上挂了注入，发现维度变了就 destroy 旧管线、按**当时已载入的包**为新维度建一条。跨维度时载入的还是旧包，本模组紧接着 reload 换成新包，于是这条管线建完就被扔 —— 一次换包把同一套程序编译两遍，其中一遍纯属浪费。
  - `MixinMinecraftIrisDimSwitch`：在同一注入点之前先把包换好。`Iris.reload()` 结束时会为新维度准备好管线，成功后把 `Iris.lastDimension` 对齐，Iris 的注入随后就无事可做。`require = 0`，第一次进方法打一行 debug 日志确认注入点挂上了
  - `DimensionSwitchController#applyEarlyOnLevelChange`：只在「本来下一 tick 就会应用」时介入（`applyDuringLevelLoading` 开、无延迟），且只有真的重载了才返回 `true`
  - `DimensionWatcher#hasLevel/adopt`：早切之后让下一 tick 的 poll 不再把同一次变化报一遍，否则 `reloadOnlyWhenChanged` 关着时会重载两遍
- **阴影距离走免重载快路径**：`IrisVideoSettings.shadowDistance` 逐帧实时读，改它不需要 reload，所以只有阴影距离不同的规则之间不再整包重载。
- **换包耗时可见**：`IrisBridge.apply` 前后计时，成功日志带 `[N ms]`，以前只能靠感觉判断换包花了多久。

***

# V0.13.0 (2026-11-05)

**服务端配置页新增静音开关**\
Full Changelog (相对于 0.12.6): [https://github.com/SpacJoy/SpaceJoyMod/compare/e5a40c4...6881d8b](https://github.com/SpacJoy/SpaceJoyMod/compare/e5a40c4...6881d8b)

## ✨ 服务端页

- **静音断线包编码报错**：断线包编码失败时那串报错刷屏，服务端配置页给了独立开关，需要排查时再打开。

***

# V0.12.6 (2026-11-05)

**服务端配置、路径点传送，以及副手火把改纯客户端**\
Full Changelog (相对于 0.11.0): [https://github.com/SpacJoy/SpaceJoyMod/compare/631f2cf...v0.12.6](https://github.com/SpacJoy/SpaceJoyMod/compare/631f2cf...v0.12.6)

## ✨ 服务端配置与权限

- **新增 `SpaceJoyServerConfig`**：S2C 快照包与 C2S 修改包，配置界面多出「服务端」分页，多人服上也能改服务端侧设置。
- **OP 判定走 `PlayerList.isOp`**：26.3 没有 `hasPermissionLevel`，改用这一条，服务端侧能力按 OP 放行。
- **`/op` `/deop` 即时推送**：`PlayerListOpSyncMixin` 挂上后就推一次权限状态刷新界面，不轮询。

## ✨ 路径点传送

- **`/sjwp <x> <y> <z> [yaw]`**：非 OP 只能传送到已登记的路径点，OP 可以传送到任意坐标。
  - `Xaero WaypointSet` mixin 把增删改全量同步登记上来，新加的点马上就能用
- **与 `/back` 联动**：`SwthbCompat` 软依赖 simple-warp-tpa-home-back，传送前先存返回点；模组缺失时回退原版 `teleport`，不会因为缺它而失效。

## 🐛 副手火把

- **改纯客户端、默认关闭**：0.9.0 是两端各挂一次 `UseBlockCallback`，现在只在客户端拦，省掉服务端那份。保留数支持 `-1` 表示整摞保留。

***

# V0.11.0 (2026-11-04)

**修掉 TaCZ 生物数据表在并发读写下的崩溃**\
Full Changelog (相对于 0.10.0): [https://github.com/SpacJoy/SpaceJoyMod/compare/v0.10.0...631f2cf](https://github.com/SpacJoy/SpaceJoyMod/compare/v0.10.0...631f2cf)

## 🐛 修复

- **`DataHolder.dataMap` 的 `ConcurrentModificationException`**：TaCZ 这张表是裸 `HashMap`，服务端 tick 线程与实体存档线程会同时读写 —— tick 侧走 `get` / `computeIfAbsent`，存档侧在 `deserializeNBT` 里 `clear` 后逐条 `put`。撞上就抛在 `HashMap.java:1230` 的 `modCount` 检查上崩游戏，装了把实体存档挪出主线程的模组（如 c2me）更容易触发。
  - `TaczDataHolderMixin` / `TaczDataHolderProviderMixin`：在构造与各入口把 `dataMap` 换成 `ConcurrentHashMap`，换表加锁防止两个线程各建一张互相覆盖；provider 侧额外兜底，因为 `onPlayerClone` 会把表整个换回普通 `HashMap`
  - 新增 `spacejoy_mod.tacz.mixins.json`（不带 `environment`，服务端也要生效）
- **开关 `taczConcurrencyFix` 默认开启**，未装 TaCZ 时界面置灰。

***

# V0.10.0 (2026-11-04)

**自由相机，以及一批从 tweakeroo 移植的 tweak**\
Full Changelog (相对于 0.9.0): [https://github.com/SpacJoy/SpaceJoyMod/compare/4f3cb51...v0.10.0](https://github.com/SpacJoy/SpaceJoyMod/compare/4f3cb51...v0.10.0)

## ✨ 自由相机

- **镜头脱离身体自由飞**（移植自 tweakeroo 的 `tweakFreeCamera`）：身体留在原地，镜头可单独移动。可分别开关是否显示第一人称手、快捷栏、状态栏；默认身体不响应任何输入。死亡、换世界、重生都会自动关掉。

## ✨ 新 tweak

- **始终疾跑**：按住前进自动疾跑，疾跑中按一下疾跑键可把这一次前进改回走路。
- **浮空放置**：准星指着空气时右键，在视线前方定点放方块，只对创造模式生效。
- **鞘翅自动换装**：起跳换鞘翅、落地换回胸甲。
- **旁观者传送**：旁观时可直接传送到玩家列表里的人。
- **背包界面缩放**：背包按指定倍率单独缩放，不受全局 GUI 缩放影响。

## 🛠️ 配置界面

- **按用途分组**：设置项按自由相机、移动、建造、装备、界面等归类，不再平铺一长列。
- **默认值收敛到 `DEFAULT_*` 常量**：字段初始化和界面的重置按钮读同一份，不会再出现「默认关闭的项反而在开启时变灰」。

## 🐛 修复

- 副手火把在保留数边界上的判断。
- Iris 轮廓线在部分维度下的显示。

***

# V0.9.0 (2026-11-03)

**首个公开版本：副手火把保护 + 项目建立**\
首个公开版本，无前置版本可对比。

## ✨ 副手火把保护

- **副手火把剩到保留数就不再从副手放置**：主手拿工具挖矿、副手放火把照明时，右键一下就把副手那摞火把放到只剩一支，之后摸黑找不着路。现在副手火把数量降到保留数（默认 1 支）就拒绝这次放置，手里始终留得住最后几支。
  - 走 Fabric 的 `UseBlockCallback`：客户端 `MultiPlayerGameMode#interactBlock` 和服务端 `ServerPlayerGameMode#interactBlock` 各挂一次，返回 `FAIL` 直接取消原方法体，改客户端也绕不过去，两端不需要额外同步
  - 只认 `TorchBlock` 及其墙挂形态（火把 / 灵魂火把 / 铜火把）；红石火把是 `RedstoneTorchBlock`，继承的是 `BaseTorchBlock`，天然排除在外
  - 创造模式直接放行（那时物品本来就不减少），被拦时客户端动作栏提示一句，服务端那份不提示，否则同一次点击会响两遍
- **配置项 `offhandTorchGuard` / `offhandTorchKeepCount`**：保留数 0 等于关掉限制，超过 16 按 16 处理。

## 🛠️ 项目建立

- 初始提交，Minecraft 26.3 / Fabric 工程骨架。
- 补充 MIT 许可证（Copyright 2026 SpaceJoy）与 README 徽章。
