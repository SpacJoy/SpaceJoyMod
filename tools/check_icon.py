from PIL import Image

p = r"D:\Code\MC\spacejoy-compat\src\main\resources\assets\spacejoy_mod\textures\gui\config_icon.png"
im = Image.open(p).convert("RGBA")
alpha = im.getchannel("A")
hist = alpha.histogram()
total = im.size[0] * im.size[1]
print("size:", im.size)
print("alpha min/max:", alpha.getextrema())
print("fully opaque px:", hist[255], "/", total, "=", round(hist[255] / total * 100, 1), "%")
print("fully transparent px:", hist[0], "of", total)
print("rgb min/max:", im.convert("RGB").getextrema())
