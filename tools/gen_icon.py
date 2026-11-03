"""生成 SpaceJoyMod 的模组图标与 Sodium 用的单色小图标。

- icon.png：128x128 彩色图标，品红 → 亮蓝的对角渐变底 + 白色四角星（对应"能力/发光"）。
- textures/gui/config_icon.png：16x16 纯白圆角方块、二元 alpha，Sodium 会用主题色染色。

纯白 + 二元 alpha 是 Sodium 的硬要求：它把图标当遮罩染色，彩色图会糊成一片。
"""

import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = r"D:\Code\MC\spacejoy-compat\src\main\resources\assets\spacejoy_mod"
OUT_ICON = os.path.join(ROOT, "icon.png")
OUT_CONFIG_ICON = os.path.join(ROOT, "textures", "gui", "config_icon.png")

SIZE = 128
SUPERSAMPLE = 4

# 品红 → 亮蓝。两头都拉满饱和度，中间插一个高饱和紫 —— 直接在 RGB 上从粉插到蓝，
# 中段会掉成灰紫（两个通道值互相拉平），加个中转色才保持得住鲜艳。
PINK = np.array([255, 45, 125], dtype=np.float32)
VIOLET = np.array([205, 70, 255], dtype=np.float32)
BLUE = np.array([55, 130, 255], dtype=np.float32)


def make_icon():
    n = SIZE
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)

    # 对角渐变：左上品红 → 中点紫 → 右下亮蓝。t 用 (x+y) 归一化，斜着走更有动感。
    t = np.clip((xx + yy) / (2 * (n - 1)), 0.0, 1.0)[..., None]
    rgb = np.where(t < 0.5, PINK + (VIOLET - PINK) * (t / 0.5), VIOLET + (BLUE - VIOLET) * ((t - 0.5) / 0.5))

    # 左上角一团品红高光，给平面的渐变加一点立体感。用「提亮品红」而不是纯白，
    # 纯白叠上去会把整个左上角冲成粉灰。
    cx, cy, radius = n * 0.16, n * 0.14, n * 0.46
    dist = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / (radius * n)
    highlight = np.clip(1.0 - dist, 0.0, 1.0) ** 2.2
    glow_colour = np.array([255, 130, 190], dtype=np.float32)
    rgb = rgb + (glow_colour - rgb) * highlight[..., None] * 0.40

    # 右下角压暗，拉开明暗层次，避免整块平淡。
    dist2 = np.sqrt((xx - n * 0.86) ** 2 + (yy - n * 0.88) ** 2) / (n * 0.62)
    shade = np.clip(1.0 - dist2, 0.0, 1.0) ** 2.0
    rgb = rgb * (1.0 - shade[..., None] * 0.20)

    rgb = np.clip(rgb, 0, 255).astype(np.uint8)
    gradient = Image.fromarray(np.dstack([rgb, np.full((n, n), 255, np.uint8)]), "RGBA")

    # 圆角遮罩（半径 26，接近原版模组图标的圆角观感）
    mask = Image.new("L", (n, n), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, n - 1, n - 1], radius=26, fill=255)

    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    img.paste(gradient, (0, 0), mask)

    # 星星画在 4 倍图上再缩回来：PIL 的多边形不抗锯齿，直接画边缘全是台阶。
    big_n = n * SUPERSAMPLE
    big = img.resize((big_n, big_n), Image.LANCZOS)
    bd = ImageDraw.Draw(big)

    def star(cx_, cy_, outer, inner, alpha):
        points = []
        for i in range(8):
            angle = i * math.pi / 4
            radius = (outer if i % 2 == 0 else inner) * SUPERSAMPLE
            points.append((cx_ * SUPERSAMPLE + radius * math.sin(angle),
                           cy_ * SUPERSAMPLE - radius * math.cos(angle)))
        bd.polygon(points, fill=(255, 255, 255, alpha))

    star(64, 62, 40, 13, 255)
    star(101, 30, 13, 4.5, 210)
    star(27, 97, 10, 3.5, 180)

    img = big.resize((n, n), Image.LANCZOS)

    # 缩放会把圆角之外的透明区域也带上，重新贴一次遮罩保证边缘干净。
    img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (n, n), 0), mask))

    img.save(OUT_ICON)
    return OUT_ICON


def make_config_icon():
    """Sodium 视频设置页里那一行小图标。

    Sodium 会把这张图当遮罩、用模组的主题色染色，所以只能是纯白 + 透明度，
    形状完全靠 alpha 描出来。

    画一颗撑满画布的四角星，不画三颗小的：Sodium 把这个图标显示成十几个像素，
    小星星缩下去只剩一撮灰点，一颗大的反而一眼能认出来。
    """
    size = 64
    big_n = size * SUPERSAMPLE
    img = Image.new("RGBA", (big_n, big_n), (255, 255, 255, 0))
    bd = ImageDraw.Draw(img)

    cx_, cy_ = size / 2, size / 2
    outer, inner = 30, 9
    points = []
    for i in range(8):
        angle = i * math.pi / 4
        radius = (outer if i % 2 == 0 else inner) * SUPERSAMPLE
        points.append((cx_ * SUPERSAMPLE + radius * math.sin(angle),
                       cy_ * SUPERSAMPLE - radius * math.cos(angle)))
    bd.polygon(points, fill=(255, 255, 255, 255))

    img = img.resize((size, size), Image.LANCZOS)

    # 关键：整张图的 RGB 全压成纯白，只让 alpha 描形状。
    # Sodium 拿这张图当遮罩染色，只要 RGB 有一点杂色（缩放插值、透明像素的黑色底）就会被一起染进去，
    # 表现出来就是入口图标一块黑一块紫。
    img = Image.merge("RGBA", (
        Image.new("L", (size, size), 255),
        Image.new("L", (size, size), 255),
        Image.new("L", (size, size), 255),
        img.getchannel("A"),
    ))
    img.save(OUT_CONFIG_ICON)
    return OUT_CONFIG_ICON


if __name__ == "__main__":
    os.makedirs(os.path.dirname(OUT_CONFIG_ICON), exist_ok=True)
    for path in (make_icon(), make_config_icon()):
        print(f"{path} -> {os.path.getsize(path)} bytes")
