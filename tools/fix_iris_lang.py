import json
import io

K = "spacejoy_mod.config."

EN_TIP = ("Master switch for dimension shaders. When on, the mod matches the dimension you are in "
          "against the rules and switches Iris accordingly. Off leaves the rule list intact but stops "
          "touching Iris entirely -- use it to rule this mod out when debugging shader packs.")

ZH_TIP = ("维度光影的总开关。开着时进入 / 切换维度会按规则切换 Iris 的光影包；"
          "关掉之后规则列表还在，但模组不再去碰 Iris —— 排查光影包问题时先关它排除嫌疑。")

EN_NAME = "Dimension shaders"
ZH_NAME = "按维度切换光影"

# 这两个已经合并进上面的总开关，键留着不删也没用，直接去掉。
DROP = [K + "iris.rulesEnabled", K + "iris.rulesEnabled.tip",
        K + "iris.debugLogging", K + "iris.debugLogging.tip"]

for lang, name, tip in (("en_us", EN_NAME, EN_TIP), ("zh_cn", ZH_NAME, ZH_TIP)):
    path = rf"D:\Code\MC\spacejoy-compat\src\main\resources\assets\spacejoy_mod\lang\{lang}.json"
    with io.open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    data[K + "irisDimensionShaders"] = name
    data[K + "irisDimensionShaders.tip"] = tip
    for key in DROP:
        data.pop(key, None)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(lang, len(data), "keys")
