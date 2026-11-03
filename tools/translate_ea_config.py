"""把 everlastingabilities-server.toml 里的英文注释换成中文。

只改 `#` 开头的注释行，值一律不动 —— 这个文件里 lootTables 有 414 项，
任何值层面的改动都会影响战利品注入。
"""

import pathlib
import re

CFG = pathlib.Path(
    r"D:\Otheapps\Minecraft\.minecraft\versions\26.3-Fabric\config"
)
TARGET = CFG / "everlastingabilities-server.toml"

# 英文原文 -> 中文。原文按「# 后面那段」精确匹配，匹配不上会报出来。
MAP = {
    "How many abilities should be dropped on player death.":
        "玩家死亡时会掉落多少个能力。",
    "These mobs will not be affected by hostile area potion effects such as poison or weakness. (Java regular expressions are allowed)":
        "这些生物不会受到中毒、虚弱这类敌对区域药水效果的影响。（支持 Java 正则表达式）",
    "If ability effects should apply. Disable this if you don't want any abilities to work. Useful for debugging.":
        "能力效果是否生效。不想让任何能力起作用时关掉它，排查问题时也用得上。",
    "The multiplier for ability XP requirement":
        "能力升级所需经验值的倍率。",
    "true: Abilities drop when players die; false: Abilities drop when players die by the hand of other players.":
        "true：玩家死亡就掉落能力；false：只有被其他玩家杀死时才掉落。",
    "Mobs that won't have abilities that can be dropped via totems. You can add things like 'minecraft:pig'.":
        "这些生物不会带可被图腾掉落的能力。可以照这样加：'minecraft:pig'。",
    "1/x chance for mobs to have abilities.":
        "生物带能力的概率是 1/x。",
    "The amount of exhaustion that should by applied to the player per active ability per second.":
        "每个生效中的能力每秒给玩家增加多少疲劳值。",
    "The maximum rarity of totems to spawn when a player first logs in. [0-3], -1 disables totem spawning.":
        "玩家首次登录时生成图腾的最高稀有度，范围 [0-3]；填 -1 则不生成图腾。",
    "Id's of abilities that should be disabled. (ids: https://github.com/CyclopsMC/EverlastingAbilities/wiki/Ability-datapacks#default-abilities)":
        "要禁用的能力 id。（id 列表见 https://github.com/CyclopsMC/EverlastingAbilities/wiki/Ability-datapacks#default-abilities）",
    "The maximum amount of abilities a player can have, -1 is infinite.":
        "玩家最多能拥有多少个能力，-1 表示不限。",
    "The loot tables in which totems should be spawned.":
        "会生成图腾的战利品表。",
    "When combining totems, percentage chance of getting one higher rarity than normal.":
        "合成图腾时，比正常结果提升一档稀有度的百分比机率。",
}

COMMENT = re.compile(r"^(\s*)#\s?(.*)$")

lines = TARGET.read_text(encoding="utf-8").splitlines(keepends=True)
out = []
hit = set()
missed = []

for line in lines:
    m = COMMENT.match(line)
    if not m:
        out.append(line)
        continue
    indent, body = m.group(1), m.group(2).strip()
    if body in MAP:
        out.append(f"{indent}#{MAP[body]}\n")
        hit.add(body)
    else:
        out.append(line)
        missed.append(body)

TARGET.write_text("".join(out), encoding="utf-8")

print(f"已翻译 {len(hit)} 条注释")
if missed:
    print("未翻译的注释：")
    for body in missed:
        print(f"  - {body[:80]}")
