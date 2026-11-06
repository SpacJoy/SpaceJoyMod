# SpaceJoyMod

<a href="https://github.com/SpacJoy/SpaceJoyMod/releases"><img src="https://img.shields.io/github/v/release/SpacJoy/SpaceJoyMod?logo=github&label=Latest%20Release" alt="Latest Release"></a>
<a href="https://github.com/SpacJoy/SpaceJoyMod/blob/main/LICENSE"><img src="https://img.shields.io/github/license/SpacJoy/SpaceJoyMod?label=License" alt="License"></a>
<a href="https://github.com/SpacJoy/SpaceJoyMod/stargazers"><img src="https://img.shields.io/github/stars/SpacJoy/SpaceJoyMod?label=Stars" alt="Stars"></a>
<img src="https://img.shields.io/badge/Minecraft-26.3-62B47A" alt="Minecraft 26.3">
<img src="https://img.shields.io/badge/Fabric-0.19.5-DBD0B4" alt="Fabric">
<a href="https://github.com/deepseek-ai/deepseek-harness"><img src="https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white" alt="powered by dsh"></a>

一个整合包用的兼容修复 + 便利功能模组。每项功能独立开关，缺哪个前置就自动禁用对应功能，界面上置灰并说明原因。

| 功能 | 需要 | 说明 |
| --- | --- | --- |
| polymer 配方编码修复 | polymer-core | 修好 TACZ 配方同步丢失 → JEI 合成页空掉 |
| 静音 polymer 的空物品表报错 | polymer-core + JEI | 服务器没发 polymer 物品时 JEI 会收到空表并抛 `ingredients must not be empty`，改成直接跳过 |
| TaCZ 并发崩溃修复 | TaCZ | TaCZ 存生物数据的表是裸 `HashMap`，服务端 tick 和实体存档读写会同时碰它，装了 c2me 这类多线程区块模组就会撞出 `ConcurrentModificationException` 崩游戏。换成线程安全的表，行为不变 |
| 补齐 Iris 的描边管线映射 | Iris | 把 `OUTLINE_CULL` / `OUTLINE_NO_CULL` 补进 Iris 的管线表，并静音 `entity_outline` 后处理链的假报错（原版发光效果本身要用） |
| Jade 显示可掉落能力 | Jade + Everlasting Abilities | 准星指着生物时多一行「可掉落能力」 |
| 标记带能力的生物 | Everlasting Abilities | 在带能力掉落的生物头顶显示它能掉什么，走原版命名牌渲染，遮挡与光影天然正确 |
| 副手火把保留 | 无 | 副手那摞火把剩到保留数（默认 1 支）就不再从副手放置，永远留得住最后几支照明；红石火把不受影响 |
| 按维度切换光影 | Iris | 进维度时按规则换光影包，规则用内置的编辑器改 |

全部开关在 ModMenu 的配置界面里，分「通用 / 修复 / 便利 / 快捷键 / 功能 / 联动 / 维度光影」七页。装了 Sodium 的话，视频设置里还会多出一行 **SpaceJoyMod → 光影规则**，点进去就是规则编辑器。

### 便利功能（移植自 tweakeroo）

| 功能 | 默认 | 说明 |
| --- | --- | --- |
| 自由相机 | 关 | 灵魂出窍：镜头脱离身体飞出去，身体留在原地。身体默认不吃任何输入（可单独放开）；第一人称手 / 快捷栏 / 状态栏各有开关；死亡、进新世界会自动关掉 |
| 始终疾跑 | 关 | 按住前进自动疾跑。此时按一下疾跑键换成走路，再按一下切回疾跑；松开前进后重置 |
| 浮空放置方块 | 关 | 创造模式下对着空气右键，方块直接落在准星前 N 格（默认 3 格），省得先垫个方块 |
| 自动切换鞘翅 | 关 | 下落时自动换上背包里最好的鞘翅，落地后换回原来的胸甲 |
| 旁观者传送 | 关 | 旁观菜单里的「传送到玩家」列出所有在线玩家，不再只列正在旁观的 |
| 自定义背包缩放 | 关 | 打开容器界面时临时换一个 GUI 缩放，关掉界面恢复原样 |

自由相机的镜头转向系数、移动速度都按 tweakeroo 的手感对齐（转向 0.15、基准速度 `0.07 × 10`、斜走 1.2 倍、起步 0.15 缓动、松手 0.4 减速）。和参考实现不同的是没搬相机预设、区域选择器互斥、火箭自动切换这些附属功能，本体也恒为冻结，不做「相机开着还能走」。

图标由 `tools/gen_icon.py` 生成（Pillow）：品红 → 紫 → 亮蓝的对角渐变，加三颗白色四角星；另出一张 16×16 的单色方块给 Sodium 当遮罩染色用。想换配色就改脚本顶部的 `PINK` / `VIOLET` / `BLUE` 三个常量再跑一遍。

## 快捷键

| 按键 | 作用 |
| --- | --- |
| 小键盘 8 | 打开主配置界面（同 ModMenu 里点齿轮） |
| 未绑定 | 规则编辑器（想用键开就自己绑） |
| 未绑定 | 切换自由相机（想用键开就自己绑） |

主配置界面默认放在小键盘 8：主键盘区一个键都不占，也不会和 `1`–`9` 的快捷栏、`E`/`Q`/`F` 这些常用键撞。规则编辑器和自由相机默认不绑键 —— 规则编辑器是低频操作，还有 ModMenu 的入口按钮、Sodium 视频设置里的那一行、`/spacejoydim gui` 三条路能进；自由相机也能从配置界面里开关。占个键不值当。

在「选项 → 控制 → 按键绑定 → SpaceJoyMod」里可以随时改成别的。

## 规则编辑器

三个地方都能进规则编辑器：ModMenu 配置界面「维度光影」页顶部的 **编辑规则…** 按钮、命令 `/spacejoydim gui`、以及自己绑的快捷键（默认没绑）。装 Sodium 的话，视频设置里还会多一行 **SpaceJoyMod → 光影规则**。进去是自绘的规则编辑界面：左边是规则列表（行首勾选框直接开关，按优先级降序排），右边是当前规则的详情（优先级、匹配的维度 / 维度类型、动作、光影包、阴影距离），底部是新增 / 复制 / 删除 / 恢复默认 / 重新读取 / 打开配置目录 / 保存并应用 / 完成。

为什么不塞进 Cloth Config：Cloth 的条目类型表达不了「左侧列表 + 右侧详情 + 行内勾选框」这种结构，硬套会变成一堆下拉框，反而比原界面难用。所以这里只保留一个入口按钮，点进去就是完整编辑器。

改完点「保存并应用」才会写 `config/spacejoy-mod/irisdim/config.json` 并立刻生效；点「完成」时如果有未保存改动会先问你。

## 换包为什么慢，以及省掉的那一次

跨维度换光影是整包重载，代价分四段：Iris 重新解析 zip 并重建渲染管线（管线里的后处理 / final / shadow / setup 程序在构造时就预编译）、换包后 `allChanged()` 全量重建区块（走 Sodium 的异步队列）、地形和实体程序按需首次编译、以及那个包本身的大小（Eclipse 这类 40 MB 级的包光是解析就要一会儿）。**这四段都消不掉** —— 换包本身就要付这个钱，所以真要说「零卡顿」是不可能的。

但有一份是白付的。Iris 自己在 `Minecraft#updateLevelInEngines` 的 HEAD 上挂了个注入：发现维度变了，就按**当时已载入的包**给新维度建一条管线。跨维度时载入的还是旧包，而本模组紧接着就把包换了，于是这条管线建完立刻被扔掉 —— 一次换包把同一套程序编译两遍，其中一遍纯属浪费。

`MixinMinecraftIrisDimSwitch` 把注入优先级抬到 Iris 之上（900 对默认的 1000 —— Mixin 里数字**越大越晚**执行，这里踩过一次坑），在它的注入之前先把包换好：`Iris.reload()` 结束时 Iris 已经为新维度准备好了管线，本模组再把这次变化标记为已处理，tick 路径不会重复排队。只有**真的重载了**才这么干 —— 无规则命中、已在目标状态、或只差阴影距离时不介入，Iris 和 tick 路径的行为跟改动之前完全一样。

不过光换得早还不够。Iris 判断维度变没变用的是**引用比较**：`if (Iris.getCurrentDimension() != Iris.lastDimension)`，而 `Iris#getCurrentDimension()` 每次调用都 `new NamespacedId(...)`，新对象永远不等于旧对象 —— 这个条件**恒为真**，给 `lastDimension` 赋任何值都拦不住它。实测日志里 `Reloading pipeline on dimension change: the_nether => the_nether`（两边同名）就是证据。

所以还得把后面那次多余的销毁吞掉：`MixinPipelineManagerRebuildGuard` 在 `PipelineManager#destroyPipeline` 的 HEAD 上取消调用，条件是本模组刚在 250 ms 内建好过管线（`PipelineRebuildGuard` 的时限许可）。管线留在缓存里，Iris 随后的 `preparePipeline` 命中缓存直接返回 —— 不重建，也不会走到那个分支里的 `levelExtractor.allChanged()`，**管线重建和全量区块重建一起省掉**。

日志上怎么确认它生效：每次换包应该看到 `skipped Iris's redundant pipeline destroy: the new pipeline is already in place`（需要开调试日志），并且紧跟着**没有** `Destroying pipeline ...` / `Creating pipeline for dimension ...`。本模组那行 `Applied rule ... [N ms]` 里的毫秒数就是这次换包的实际耗时。注意 `Reloading pipeline on dimension change: ...` 这行**仍会出现**，它在销毁之前打印，删不掉，别拿它当判据。

另外阴影距离现在单独走一条不重载的路径：`IrisVideoSettings.shadowDistance` 是逐帧实时读的，改它不需要重载，所以规则之间只有阴影距离不同时不再触发整包重载。

## 头顶标签：为什么不用发光描边

早期版本走的是原版发光描边（拦 `Minecraft#shouldEntityAppearGlowing`，再自己打射线判遮挡）。三个问题都绕不过去：

1. **原版描边不参与深度测试** —— 隔着山、隔着墙也能看见。
2. **描边只画轮廓，不看别的实体** —— 被另一只生物挡住的那部分照样亮。想补上「被实体遮挡也要剔除」等于重写整套描边渲染，代价和收益完全不匹配。
3. **开了光影还得单独补管线映射** —— 就是 `irisOutlineFix` 那件事，本质上是在替 Iris 擦屁股。

现在是**头顶标签**，走原版命名牌那条渲染通道：

mixin 拦 `EntityRenderer#extractNameTags(Entity, EntityRenderState, float, double, double)` 的 TAIL —— 这是原版唯一决定「这只生物头顶显示什么文本」的地方。它在方法里填 `EntityRenderState.nameTag`（名字）、`scoreText`（名字上方那一行）、`nameTagAttachment`（文本挂点），填完交给 `submitNameDisplay` 去画。深度、遮挡、光影映射全部由原版处理，我们只管塞文本。

两个槽位的用法（`submitNameDisplay` 先画 `scoreText`，再把坐标抬高 `9 × 1.15 × 0.025` 格画 `nameTag`，所以 **`nameTag` 在上、`scoreText` 在下**，这也是原版计分板 belowName 显示在名字下面的原因）：

- 生物**没有自定义名** → 标签直接占 `nameTag`，跟普通命名牌一模一样；
- 生物**已经被命名** → 名字让到下面那行 `scoreText`，标签占上面那行 `nameTag`。标签始终在最上面、名字紧贴其下，两者不重叠，这就是「被命名时上移让位」；
- 下面那行已经被计分板分数占着时，不抢，这次就不显示标签。

挂点自己算：原版只有在 `shouldShowName` 为真时才填 `nameTagAttachment`，而大多数带能力的生物本来不该显示名字，那个字段是 null。所以这里直接问 `EntityAttachments` 要 `NAME_TAG` 挂点。

标签文本读的是 Everlasting Abilities 同步到客户端的本地 ability store，纯客户端、服务端零开销，按实体 id 缓存，20 tick 刷新一次。超过 64 格不画（和原版命名牌一致）。

**服务端仍然只做一件事**：实体加载时清掉 0.6.0 及更早版本遗留的无限时长 `GLOWING` 效果（O(1) 的效果表查找）。那批效果会绕过一切判定，隔着山一直亮。

另外：EA 的能力是**懒初始化**的 —— 只有 `getId() % mobAbilityChance == 0`（默认 10）的生物才有机会带能力。所以世界里本来就只有约十分之一的生物带标签，这是 EA 的设计，不是判定漏了。

## polymer 配方为什么要单独修

polymer 0.18.6 的 `IngredientExtension.BaseStreamCodec.encode`（`IngredientExtension.java:102`）：

```java
var context = PacketContext.get().get(PacketContext.CONNECTION);  // 先解引用
if (context == null || ...) { original.encode(buf, value); return; }  // 后判空
```

`PacketContext.get()` 在「没有网络包上下文」时合法返回 `null`。TACZ 的 `CommonAssetsManager.sanitizeSyncedRecipes` 会在服务端 reload 阶段对每条配方试编码，正好命中这个 null，抛 NPE，TACZ 捕获后把配方从 `fabric:recipe_sync` 里剔除，JEI 合成页整片空掉。

修法是在 `encode` 入口判空，无上下文时走原版 codec —— 正是 polymer 作者本意的兜底分支，真实发包时仍走 polymer 扩展路径。`OptionalStreamCodec` 同理。

## polymer 的另一处空表：JEI 插件

`JeiCompatibility.update`（`polymer-core` 的 `client/compat/JeiCompatibility.java`）先删掉 polymer 自己控制过的物品，再调 `CompatUtils.iterateItems` 把当前该显示的物品补回 JEI：

```java
var stacks = new ArrayList<ItemStack>();
CompatUtils.iterateItems(stacks::add);
manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks);   // 空表就抛异常
```

`CompatUtils.iterateItems` 只收集 polymer 往创造模式物品栏注入的条目（`polymer$getStacksGroup` / `polymer$getStacksSearch`），**不含原版物品**。单人游戏或服务器没发 polymer 自定义物品时它返回空表，JEI 的 `IngredientManager.addIngredientsAtRuntime` 在入口 `ErrorUtil.checkNotEmpty` 抛 `IllegalArgumentException: ingredients must not be empty.`，被 polymer 自己的 `catch (Throwable)` 吞掉打一行 STDERR —— 不崩游戏，但这次运行时注册整批作废。

修法是 `PolymerJeiCompatibilityMixin` 用 `@WrapOperation` 拦这个调用点：集合为空就不调，非空照常转发。语义上「没有要加的条目」本来就不该调 `addIngredientsAtRuntime`。

## Iris 为什么要补 outline 管线

MC 26.3 的实体发光描边走 `RenderPipelines.OUTLINE_CULL` / `OUTLINE_NO_CULL`（`RenderTypes.lambda$static$0` 按是否剔除二选一），`entity_outline` 后处理链还会在运行时造出 `minecraft:entity_outline/0..3` 四条 pass 管线。Iris 1.11.7 的 `IrisPipelines` 静态块里**一条 outline 映射都没有**（`git log --all -S "OUTLINE_CULL"` 全历史零命中），于是 `MixinShaderManager_Overrides` 查表拿到 null，走到：

```java
if (renderPipeline.getLocation().getNamespace().equals("minecraft")) {
    Iris.logger.fatal("Missing program " + renderPipeline.getLocation() + " in override list. This is likely an Iris bug!!!", new Throwable());
}
```

`missingShaders` 是去重集合，所以每条管线每次启动只报一次。日志里那 6 条（`entity_outline/0`–`/3`、`pipeline/outline_no_cull`、`pipeline/outline_cull`）就是全部。

修法分两半：

- **真的有映射**：`IrisPipelinesMixin` 在 `getPipeline` 的 HEAD 调 `IrisCompat.ensureOutlinePipelines()`，幂等地把两条 outline 管线 `assignPipeline` 到 `ShaderKey.TEXTURED_COLOR` —— 这个 key 的顶点格式 `POSITION_TEX_COLOR`、alpha 测试 `ONE_TENTH_ALPHA` 与 `OUTLINE_SNIPPET` 完全对得上（`ShaderKey.LINES` 用的是 `POSITION_COLOR_NORMAL_LINE_WIDTH`，格式不符，会把描边画歪）。Iris 自己以后补了同名映射的话，`assignPipeline` 抛 `IllegalStateException`，这里接住并放弃，不覆盖上游。
- **补不了的部分**：`entity_outline/0..3` 是后处理 pass，跑的是 `core/screenquad` 全屏着色器，塞进三维管线表只会把画面搞花。所以 `IrisLoggingMixin` 拦 `fatal(String, Throwable)`，只对 `Missing program minecraft:entity_outline/` 开头的消息取消输出。其余 `minecraft:` 命名空间的缺失照常 fatal，不掩盖以后真正的 Iris 回归。

注入点选 `getPipeline` 的 HEAD 而不是 `IrisPipelines` 静态块 TAIL：静态块执行期间补表会牵扯 `IrisCompat` 自身的类初始化顺序，而 `getPipeline` 只可能在静态块跑完之后才被调用。

这一项**默认关闭**。带能力生物现在靠头顶标签标记，不依赖描边；开着它只对原版发光效果（光灵箭、`/glowing`、队伍颜色）在光影下是否画对有意义。关着时那 6 条 `Missing program` 会回来，属于预期。

## TaCZ 数据表为什么要换成并发容器

崩溃栈是这样（26.3 R1-hotfix3 的实机崩溃报告）：

```
java.util.ConcurrentModificationException
  at java.base/java.util.HashMap.computeIfAbsent(HashMap.java:1230)
  at com.tacz.guns.entity.sync.core.DataHolder.get(DataHolder.java:30)
  at com.tacz.guns.entity.sync.core.SyncedEntityData.get(SyncedEntityData.java:124)
  at com.tacz.guns.entity.sync.core.SyncedDataKey.getValue(SyncedDataKey.java:28)
  at net.minecraft.world.entity.LivingEntity.getSynReloadState(LivingEntity.java:29383)
  at com.tacz.guns.entity.shooter.LivingEntityAim.tickSprint(LivingEntityAim.java:104)
```

`DataHolder` 里那行是 `public Map<..., ...> dataMap = new HashMap<>();` —— 一张裸 `HashMap`，全类没有任何同步。而它会同时被两边摸到：

- **服务端 tick 线程**：`LivingEntityAim.tickSprint` 每 tick 取一次 `reload_state`，走 `DataHolder.get` → `computeIfAbsent`。每只生物都走，没拿枪的骷髅也一样。
- **实体 NBT 线程**：TaCZ 自己的 `EntityMixin` 把 `DataHolderCapabilityProvider.writeToNbt` / `readFromNbt` 注进了 `Entity.saveWithoutId` / `Entity.load`，而 `deserializeNBT` 会先 `dataMap.clear()` 再逐条 `put`。装了 c2me 之后实体存档读写会离开主线程，于是撞上第一边。

`HashMap.computeIfAbsent` 在 mapping function 执行完会核对一次 `modCount`（JDK 25 的 `HashMap.java:1230`），期间被改过就抛 CME。所以这不是重入 bug，是实打实的跨线程并发写。

修法是把 `dataMap` 换成 `ConcurrentHashMap`：`get` / `set` 的 `computeIfAbsent` 本来就是原子语义，换掉之后并发语义只会更强；`clear()` 加逐条 `put` 的重建期间别的线程会看到一张还在重建的表（可能瞬态读到默认值），但不会再抛异常，而重建发生在实体加载期，那时实体还没开始 tick。

两个混入分工：`TaczDataHolderMixin` 在构造和 `get` / `set` / `clean` / `gather*` 入口兜底换表；`TaczDataHolderProviderMixin` 在 `writeToNbt` / `readFromNbt` 入口再兜一次 —— 因为玩家克隆（`SyncedEntityDataEvent.onPlayerClone`）会把表整个换回普通 `HashMap`，换完之后如果第一个摸它的是存档线程，就轮不到 tick 线程去换。换表本身加锁，否则两个线程各建一张，后赋值的会盖掉另一半数据。

混入配置单独放在 `spacejoy_mod.tacz.mixins.json`，**不带 `environment: client`** —— 崩溃发生在 Integrated Server 线程，只修客户端等于没修。目标类属于第三方模组，所以 `required: false`，由 `SpaceJoyMixinPlugin` 按 `isModLoaded("tacz")` 决定是否应用。

## 配置文件

- `config/spacejoy-mod.json` —— 全部开关（总开关、日志、三项修复、六项便利功能、三项功能联动、维度光影；`irisOutlineFix`、`polymerRecipeFix`、`taczConcurrencyFix` 在「修复」页，其中 `irisOutlineFix` **默认关闭**）
- `config/spacejoy-mod/irisdim/config.json` —— 维度光影的规则列表（界面上也能改）
- `config/spacejoy-mod/irisdim/discovered.json` —— 进过的维度，带可粘贴的规则模板

## 构建

```bash
./gradlew build                 # 产物在 build/libs/
./gradlew installToInstance     # 装进实例，顺带清掉旧身份的 jar
./gradlew syncLibs              # 从实例 mods 同步 Jade/EA/CyclopsCore/ClothConfig/ModMenu/TaCZ 到 libs/
./gradlew verifyPolymerTargets  # 校验 mixin 目标类在 polymer-bundled 的内嵌 jar 里确实存在
./gradlew listJarClasses -Pjar=<jar> -Pprefix=<包前缀>    # 查 jar 里有哪些类
./gradlew findClass -Pjar=<jar> -Ppattern=<片段>          # 连 META-INF/jars 里的内嵌 jar 一起搜
./gradlew javapClass -Pjar=<jar> -PtargetClass=<全类名>   # 打印方法签名
```

`-PinstanceMods="<mods 目录>"` 覆盖默认实例路径。

## 结构

```
net.spacejoy.mod
├── SpaceJoyMod                 主入口
├── CompatFlags                 软依赖探测（只碰 FabricLoader，绝不引用第三方类）
├── client/SpaceJoyKeys         快捷键：小键盘 8 配置界面、规则编辑器与自由相机默认不绑
├── client/ClientMobLabel        能力标签文本：EA 能力缓存 + 按 id 缓存结果
├── compat/EACompat             EA 的全部接触面，EA 缺失时这个类不会被加载
├── compat/IrisCompat           Iris 的唯一接触面：补 outline 管线、判定要静音的 fatal
├── compat/MobGlow              服务端只负责清掉老版本遗留的无限时长 GLOWING
├── config/SpaceJoyConfig       Gson 读写 config/spacejoy-mod.json
├── config/SpaceJoyConfigScreen Cloth Config 七页界面，缺依赖置灰
├── config/ScreenLinkEntry      跳转子界面的按钮条目（Cloth 没这类型，自己写的）
├── feature/EquipmentSwapper    鞘翅与胸甲互换（含背包里挑最好的鞘翅）
├── feature/OffhandTorchGuard   副手火把保留最后几支
├── feature/freecam/            自由相机
│   ├── FreeCameraEntity        继承 LocalPlayer 的假实体，只当镜头
│   ├── SpaceJoyFreeCamera      状态管理：建/拆相机、tick 驱动、死亡与换世界自动关
│   └── EmptyClientInput        给假实体挂的空输入，保证它自己不动
├── jade/                       Jade entrypoint 与 tooltip provider
├── modmenu/ModMenuIntegration  ModMenu entrypoint
├── irisdim/                    从 IrisDimensionShaders 合并进来的维度光影
│   ├── IrisDimClient           客户端接线：tick 驱动 + /spacejoydim 命令
│   ├── compat/sodium/          Sodium 视频设置页里的入口
│   └── client/gui/             规则编辑器（自绘，原 IrisDimensionShaders 的界面）
└── mixin/                      polymer 编解码补丁 + EntityRendererMixin（头顶标签）
                                + 便利功能补丁（疾跑 / 鞘翅 / 浮空放置 / 旁观传送 / 背包缩放）
                                + freecam/ 自由相机的一整套拦截
                                + tacz/ TaCZ 数据表换并发容器（唯一一组非 client 混入）
                                + IrisPipelinesMixin / IrisLoggingMixin / PolymerJeiCompatibilityMixin
                                + MixinMinecraftIrisDimSwitch（抢在 Iris 前换包）
                                + MixinPipelineManagerRebuildGuard（吞掉 Iris 多余的管线销毁）
                                + SpaceJoyMixinPlugin（按前置决定上不上）
```

`spacejoy_mod.mixins.json` 放通用补丁，`spacejoy_mod.client.mixins.json` 只放必须客户端加载的，`spacejoy_mod.compat.mixins.json` 专放「目标模组可能不在」的补丁 —— 它的 `plugin` 是 `SpaceJoyMixinPlugin`，前置缺失时把这些 mixin 整个跳过，而不是靠 `require = 0` 硬吞注入失败。

软依赖铁律：第三方模组的类型只能出现在它自己的 `*Compat` 桥接类里，其余类最多引用 `CompatFlags` 的布尔值。这样前置缺失时不会 `NoClassDefFoundError`。混入是唯一例外 —— 它们直接 import 目标模组的类，但由 `SpaceJoyMixinPlugin` 按前置是否加载决定上不上，没装前置时这些类根本不会被加载。

## 许可证

本项目采用 [MIT License](LICENSE)，可自由使用、修改、分发，包括商业用途，保留版权声明即可。
