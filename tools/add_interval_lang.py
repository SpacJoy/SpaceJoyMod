import json
import io

K = "spacejoy_mod.config."

EN = {
    K + "mobGlowInterval": "Sight check interval",
    K + "mobGlowInterval.value": "%s ticks",
    K + "mobGlowInterval.tip": (
        "How often the server re-checks whether a player can actually see a glowing mob. "
        "8 ticks (0.4s) feels instant; 20 ticks (1s) is noticeably cheaper on a weak server and "
        "most players will not tell the difference. Only mobs that already have abilities are "
        "checked, and short range / point blank cases skip the raycast entirely, so this is a "
        "cheap loop either way -- raise it if the server is struggling."
    ),
}

ZH = {
    K + "mobGlowInterval": "视野检查间隔",
    K + "mobGlowInterval.value": "%s tick",
    K + "mobGlowInterval.tip": (
        "服务器多久重新判断一次「玩家现在能不能看见发光的生物」。8 tick（0.4 秒）几乎感觉不到延迟；"
        "低配服务器调到 20 tick（1 秒）明显更省，多数玩家看不出区别。"
        "这项检查只针对本来就有能力的生物，近距离和超远的情况还会直接跳过射线，"
        "所以本身已经很轻 —— 服务器吃紧再往上调。"
    ),
}

for lang, table in (("en_us", EN), ("zh_cn", ZH)):
    path = rf"D:\Code\MC\spacejoy-compat\src\main\resources\assets\spacejoy_mod\lang\{lang}.json"
    with io.open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    data.update(table)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(lang, len(data), "keys")
