# SpaceJoyMod

一个整合包用的兼容修复 + 便利功能模组。每项功能独立开关，缺哪个前置就自动禁用对应功能，界面上置灰并说明原因。

| 功能 | 需要 | 说明 |
| --- | --- | --- |
| polymer 配方编码修复 | polymer-core | 修好 TACZ 配方同步丢失 → JEI 合成页空掉 |
| 静音 polymer 的空物品表报错 | polymer-core + JEI | 服务器没发 polymer 物品时 JEI 会收到空表并抛 `ingredients must not be empty`，改成直接跳过 |
| 补齐 Iris 的描边管线映射 | Iris | 把 `OUTLINE_CULL` / `OUTLINE_NO_CULL` 补进 Iris 的管线表，并静音 `entity_outline` 后处理链的假报错（原版发光效果本身要用） |
| Jade 显示可掉落能力 | Jade + Everlasting Abilities | 准星指着生物时多一行「可掉落能力」 |
| 标记带能力的生物 | Everlasting Abilities | 在带能力掉落的生物头顶显示它能掉什么，走原版命名牌渲染，遮挡与光影天然正确 |
| 按维度切换光影 | Iris | 进维度时按规则换光影包，规则用内置的编辑器改 |

全部开关在 ModMenu 的配置界面里，分「通用 / 修复 / 功能 / 维度光影」四页。装了 Sodium 的话，视频设置里还会多出一行 **SpaceJoyMod → 光影规则**，点进去就是规则编辑器。

图标由 `tools/gen_icon.py` 生成（Pillow）：品红 → 紫 → 亮蓝的对角渐变，加三颗白色四角星；另出一张 16×16 的单色方块给 Sodium 当遮罩染色用。想换配色就改脚本顶部的 `PINK` / `VIOLET` / `BLUE` 三个常量再跑一遍。

## 快捷键

| 按键 | 作用 |
| --- | --- |
| 小键盘 8 | 打开主配置界面（同 ModMenu 里点齿轮） |
| 未绑定 | 规则编辑器（想用键开就自己绑） |

主配置界面默认放在小键盘 8：主键盘区一个键都不占，也不会和 `1`–`9` 的快捷栏、`E`/`Q`/`F` 这些常用键撞。规则编辑器默认不绑键 —— 它是低频操作，还有 ModMenu 的入口按钮、Sodium 视频设置里的那一行、`/spacejoydim gui` 三条路能进，占个键不值当。

在「选项 → 控制 → 按键绑定 → SpaceJoyMod」里可以随时改成别的。

## 规则编辑器

三个地方都能进规则编辑器：ModMenu 配置界面「维度光影」页顶部的 **编辑规则…** 按钮、命令 `/spacejoydim gui`、以及自己绑的快捷键（默认没绑）。装 Sodium 的话，视频设置里还会多一行 **SpaceJoyMod → 光影规则**。进去是自绘的规则编辑界面：左边是规则列表（行首勾选框直接开关，按优先级降序排），右边是当前规则的详情（优先级、匹配的维度 / 维度类型、动作、光影包、阴影距离），底部是新增 / 复制 / 删除 / 恢复默认 / 重新读取 / 打开配置目录 / 保存并应用 / 完成。

为什么不塞进 Cloth Config：Cloth 的条目类型表达不了「左侧列表 + 右侧详情 + 行内勾选框」这种结构，硬套会变成一堆下拉框，反而比原界面难用。所以这里只保留一个入口按钮，点进去就是完整编辑器。

改完点「保存并应用」才会写 `config/spacejoy-mod/irisdim/config.json` 并立刻生效；点「完成」时如果有未保存改动会先问你。

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

## 配置文件

- `config/spacejoy-mod.json` —— 七个开关（总开关、日志、两项修复、两项功能、维度光影；`irisOutlineFix` 与 `polymerRecipeFix` 在「修复」页，其中 `irisOutlineFix` **默认关闭**）
- `config/spacejoy-mod/irisdim/config.json` —— 维度光影的规则列表（界面上也能改）
- `config/spacejoy-mod/irisdim/discovered.json` —— 进过的维度，带可粘贴的规则模板

## 构建

```bash
./gradlew build                 # 产物在 build/libs/
./gradlew installToInstance     # 装进实例，顺带清掉旧身份的 jar
./gradlew syncLibs              # 从实例 mods 同步 Jade/EA/CyclopsCore/ClothConfig/ModMenu 到 libs/
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
├── client/SpaceJoyKeys         小键盘 8 键位（规则编辑器默认不绑）
├── client/ClientMobLabel        能力标签文本：EA 能力缓存 + 按 id 缓存结果
├── compat/EACompat             EA 的全部接触面，EA 缺失时这个类不会被加载
├── compat/IrisCompat           Iris 的唯一接触面：补 outline 管线、判定要静音的 fatal
├── compat/MobGlow              服务端只负责清掉老版本遗留的无限时长 GLOWING
├── config/SpaceJoyConfig       Gson 读写 config/spacejoy-mod.json
├── config/SpaceJoyConfigScreen Cloth Config 四页界面，缺依赖置灰
├── config/ScreenLinkEntry      跳转子界面的按钮条目（Cloth 没这类型，自己写的）
├── jade/                       Jade entrypoint 与 tooltip provider
├── modmenu/ModMenuIntegration  ModMenu entrypoint
├── irisdim/                    从 IrisDimensionShaders 合并进来的维度光影
│   ├── IrisDimClient           客户端接线：tick 驱动 + /spacejoydim 命令
│   ├── compat/sodium/          Sodium 视频设置页里的入口
│   └── client/gui/             规则编辑器（自绘，原 IrisDimensionShaders 的界面）
└── mixin/                      polymer 编解码补丁 + EntityRendererMixin（头顶标签）
                                + IrisPipelinesMixin / IrisLoggingMixin / PolymerJeiCompatibilityMixin
                                + SpaceJoyMixinPlugin（按前置决定上不上）
```

`spacejoy_mod.mixins.json` 放通用补丁，`spacejoy_mod.client.mixins.json` 只放必须客户端加载的，`spacejoy_mod.compat.mixins.json` 专放「目标模组可能不在」的补丁 —— 它的 `plugin` 是 `SpaceJoyMixinPlugin`，前置缺失时把这些 mixin 整个跳过，而不是靠 `require = 0` 硬吞注入失败。

软依赖铁律：第三方模组的类型只能出现在它自己的 `*Compat` 桥接类里，其余类最多引用 `CompatFlags` 的布尔值。这样前置缺失时不会 `NoClassDefFoundError`。
