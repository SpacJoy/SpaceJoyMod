"""把配置界面的 tooltip 全部压短。

原来 mobGlow.tip 一条就 800 字符，Cloth Config 会把它铺满整个屏幕，
视觉上像一堵墙，反而没人读。原则：一句说清「这是做什么的」，
展开解释留给 README。
"""

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
LANG = ROOT / "src" / "main" / "resources" / "assets" / "spacejoy_mod" / "lang"

EN = {
    "spacejoy_mod.config.debugLog.tip":
        "Print this mod's own log lines. Turn on when troubleshooting.",
    "spacejoy_mod.config.enabled.tip":
        "Master switch. Off means the mod does nothing at all.",
    "spacejoy_mod.config.iris.announceDurationTicks.tip":
        "How long the action bar note stays up, in ticks. Vanilla uses 60.",
    "spacejoy_mod.config.iris.announceInChat.tip":
        "Show a note on the action bar when the shaderpack changes.",
    "spacejoy_mod.config.iris.applyDelayTicks.tip":
        "Extra ticks to wait after a dimension change. Only for debugging.",
    "spacejoy_mod.config.iris.applyDuringLevelLoading.tip":
        "Start as soon as the level exists, hiding the reload behind the loading screen. Recommended.",
    "spacejoy_mod.config.iris.applyOnDimensionChange.tip":
        "Apply rules when you move between dimensions in the same world.",
    "spacejoy_mod.config.iris.applyOnWorldEnter.tip":
        "Apply the matching rule as soon as a world loads.",
    "spacejoy_mod.config.iris.missingPackPolicy.tip":
        "What to do when a rule names a pack that is not installed.",
    "spacejoy_mod.config.iris.reloadOnlyWhenChanged.tip":
        "Skip the reload when Iris already has the target state.",
    "spacejoy_mod.config.iris.restoreOnWorldExit.tip":
        "Put back the previous shaderpack on world exit. Off by default, reloading on the main menu is slow.",
    "spacejoy_mod.config.iris.trackDiscoveredDimensions.tip":
        "Record every dimension you enter in discovered.json.",
    "spacejoy_mod.config.irisDimensionShaders.tip":
        "Master switch. Off leaves the rules intact but stops touching Iris.",
    "spacejoy_mod.config.jadeAbilityDisplay.tip":
        "List what a mob can drop in the Jade tooltip.",
    "spacejoy_mod.config.mobGlow.tip":
        "Outline mobs that carry abilities. Client side only. Hidden behind solid blocks, still visible through glass and water.",
    "spacejoy_mod.config.mobGlowInterval.tip":
        "How often the client re-checks whether the outline is still visible. 8 ticks is basically instant.",
    "spacejoy_mod.config.polymerRecipeFix.tip":
        "Fix the null pointer that made TACZ lose recipes and left JEI crafting pages empty.",
}

ZH = {
    "spacejoy_mod.config.debugLog.tip":
        "输出模组自己的日志。排查问题时再打开。",
    "spacejoy_mod.config.enabled.tip":
        "总开关。关掉后模组完全不介入。",
    "spacejoy_mod.config.iris.announceDurationTicks.tip":
        "动作栏提示停留的 tick 数，原版是 60。",
    "spacejoy_mod.config.iris.announceInChat.tip":
        "切换光影时在动作栏显示一行提示。",
    "spacejoy_mod.config.iris.applyDelayTicks.tip":
        "切换维度后额外等待的 tick 数，只用于排查。",
    "spacejoy_mod.config.iris.applyDuringLevelLoading.tip":
        "新世界一存在就切换，把重载藏进加载画面。推荐开启。",
    "spacejoy_mod.config.iris.applyOnDimensionChange.tip":
        "在同一世界内跨维度时应用规则。",
    "spacejoy_mod.config.iris.applyOnWorldEnter.tip":
        "世界加载时按当前维度应用匹配到的规则。",
    "spacejoy_mod.config.iris.missingPackPolicy.tip":
        "规则写的包没装时怎么办。",
    "spacejoy_mod.config.iris.reloadOnlyWhenChanged.tip":
        "Iris 已经是目标状态就不动，省掉一次卡顿。",
    "spacejoy_mod.config.iris.restoreOnWorldExit.tip":
        "退出世界时还原之前的光影包。默认关闭，主菜单重载很慢。",
    "spacejoy_mod.config.iris.trackDiscoveredDimensions.tip":
        "把进过的维度记进 discovered.json。",
    "spacejoy_mod.config.irisDimensionShaders.tip":
        "总开关。关掉后规则还在，但不再操作 Iris。",
    "spacejoy_mod.config.jadeAbilityDisplay.tip":
        "指着生物时，在 Jade 提示里列出它能掉的能力。",
    "spacejoy_mod.config.mobGlow.tip":
        "给带能力的生物画发光描边。纯客户端；实心方块挡住就不画，玻璃水照样透光。",
    "spacejoy_mod.config.mobGlowInterval.tip":
        "客户端多久重新判断一次遮挡。8 tick 基本跟手。",
    "spacejoy_mod.config.polymerRecipeFix.tip":
        "修掉让 TACZ 丢配方、JEI 合成页空掉的那个空指针。",
}


def apply(name, table):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for key, value in table.items():
        if key not in data:
            raise SystemExit(f"{name}: 缺少键 {key}")
        data[key] = value
        print(f"{name}  {key}  {len(value)}")
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


apply("en_us.json", EN)
apply("zh_cn.json", ZH)
print("done")
