import json
import io

EN = {
    "spacejoy_mod.config.mobGlow": "Outline mobs that carry abilities",
    "spacejoy_mod.config.mobGlow.tip": (
        "Draws the vanilla glowing outline around mobs that actually carry abilities, so you can "
        "pick them out at a glance. Only the outline is added - no particles, no effect icon, and "
        "vanilla glowing (spectral arrow, /glowing) is never touched. "
        "Everything runs on the client: the server does no scanning and no raycasting at all. "
        "The outline is dropped while a solid block is in the way, so mobs behind a hill no longer "
        "shine through; glass, water and leaves still let it through, because only blocks that "
        "actually occlude count. Mobs you cannot see anyway (behind you, off screen, past render "
        "distance) are never even asked about - vanilla already culls those. "
        "Enabling this also turns off Everlasting Abilities own entity particles at runtime, which "
        "is what caused the CyclopsCore + Iris rendering glitch."
    ),
    "spacejoy_mod.config.mobGlowInterval": "Visibility re-check interval",
    "spacejoy_mod.config.mobGlowInterval.value": "%s ticks",
    "spacejoy_mod.config.mobGlowInterval.tip": (
        "How often the client re-checks whether the outline is still visible or now hidden behind "
        "blocks. 8 ticks (0.4s) reacts basically instantly; 20 ticks (1s) is cheaper on a weak "
        "machine and most players will not tell. This affects only your own client's CPU - the "
        "server is not involved at all."
    ),
}

ZH = {
    "spacejoy_mod.config.mobGlow": "给带能力的生物画发光描边",
    "spacejoy_mod.config.mobGlow.tip": (
        "给真正带能力的生物画上原版发光描边，远远就能认出来。只加描边：不撒粒子、"
        "不加右上角状态图标，光灵箭、「/glowing」这类原版发光一律不动。"
        "整套判断都在客户端跑，服务器不扫实体、不打射线，一点开销都没有。"
        "描边会被实心方块挡住——躲在山后面的生物不会再隔山发光；玻璃、水、树叶照常透光，"
        "因为只把「真正遮挡」的方块算数。"
        "你本来就看不清的生物（在背后、在屏幕外、超出渲染距离）原版已经剔掉了，根本不会来问。"
        "开启后还会在运行期关掉永恒能力自带的生物粒子，那套粒子正是 CyclopsCore 和 Iris 渲染错乱的来源。"
    ),
    "spacejoy_mod.config.mobGlowInterval": "遮挡重算间隔",
    "spacejoy_mod.config.mobGlowInterval.value": "%s tick",
    "spacejoy_mod.config.mobGlowInterval.tip": (
        "客户端多久重新判断一次「描边现在还被挡着还是已经看得见」。8 tick（0.4 秒）基本跟手；"
        "20 tick（1 秒）在低配机器上更省，多数人看不出差别。这只影响你自己客户端的 CPU，"
        "跟服务器完全无关。"
    ),
}

for path, data in (
    ("src/main/resources/assets/spacejoy_mod/lang/en_us.json", EN),
    ("src/main/resources/assets/spacejoy_mod/lang/zh_cn.json", ZH),
):
    with io.open(path, "r", encoding="utf-8") as handle:
        current = json.load(handle)
    current.update(data)
    with io.open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(current, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")
    print(path, "->", len(current), "keys")
