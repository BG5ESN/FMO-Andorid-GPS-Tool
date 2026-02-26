#!/usr/bin/env python3
"""
生成 Android Launcher 图标
"""

from PIL import Image, ImageOps, ImageDraw
import os

# 原始图片路径
SOURCE_IMAGE = "fmoicon-1.jpg"
RES_DIR = "app/src/main/res"

# Launcher 图标尺寸（不同密度）
LAUNCHER_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

# Round Launcher 图标尺寸
ROUND_SIZES = LAUNCHER_SIZES

# Adaptive icon foreground 尺寸
ADAPTIVE_FOREGROUND_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432
}

def create_launcher_icons(img):
    """创建普通 launcher 图标（WebP 格式）"""
    print("生成 launcher 图标...")
    for density, size in LAUNCHER_SIZES.items():
        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        output_path = f"{RES_DIR}/mipmap-{density}/ic_launcher.webp"
        resized.save(output_path, "WEBP", quality=90)
        print(f"  生成: {output_path}")

def create_round_launcher_icons(img):
    """创建圆形 launcher 图标（WebP 格式）"""
    print("\n生成圆形 launcher 图标...")
    for density, size in ROUND_SIZES.items():
        # 创建圆形蒙版
        resized = img.resize((size, size), Image.Resampling.LANCZOS)

        # 创建透明背景的圆形图片
        mask = Image.new("L", (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse([(0, 0), (size, size)], fill=255)

        # 应用蒙版
        output = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        output.paste(resized, (0, 0))
        output.putalpha(mask)

        output_path = f"{RES_DIR}/mipmap-{density}/ic_launcher_round.webp"
        output.save(output_path, "WEBP", quality=90)
        print(f"  生成: {output_path}")

def create_adaptive_icons(img):
    """创建 adaptive icon（透明 foreground）"""
    print("\n生成 adaptive icon foreground...")

    # 创建自适应图标的前景（透明背景）
    for density, size in ADAPTIVE_FOREGROUND_SIZES.items():
        # 计算内容区域大小（66dp of 108dp）
        content_size = int(size * 66 / 108)

        # 缩放图片到内容区域大小
        resized = img.resize((content_size, content_size), Image.Resampling.LANCZOS)

        # 创建透明背景的新图片
        output = Image.new("RGBA", (size, size), (0, 0, 0, 0))

        # 居中放置图片
        offset = (size - content_size) // 2
        output.paste(resized, (offset, offset))

        # 保存
        output_path = f"{RES_DIR}/mipmap-{density}/ic_launcher_foreground.webp"
        output.save(output_path, "WEBP", quality=90)
        print(f"  生成: {output_path}")

def create_adaptive_background():
    """创建 adaptive icon 背景（白色）"""
    print("\n生成 adaptive icon background...")

    for density, size in ADAPTIVE_FOREGROUND_SIZES.items():
        # 创建白色背景
        output = Image.new("RGB", (size, size), (255, 255, 255))

        output_path = f"{RES_DIR}/mipmap-{density}/ic_launcher_background.xml"
        # 使用 vector drawable 或 color
        # 这里使用简单的白色背景
        with open(output_path, "w") as f:
            f.write(f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M0,0h108v108h-108z" />
</vector>''')
        print(f"  生成: {output_path}")

def update_adaptive_xml():
    """更新 adaptive icon XML 文件"""
    print("\n更新 adaptive icon XML...")

    ic_launcher_xml = f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>'''

    ic_launcher_round_xml = f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>'''

    with open(f"{RES_DIR}/mipmap-anydpi-v26/ic_launcher.xml", "w") as f:
        f.write(ic_launcher_xml)
    print(f"  更新: {RES_DIR}/mipmap-anydpi-v26/ic_launcher.xml")

    with open(f"{RES_DIR}/mipmap-anydpi-v26/ic_launcher_round.xml", "w") as f:
        f.write(ic_launcher_round_xml)
    print(f"  更新: {RES_DIR}/mipmap-anydpi-v26/ic_launcher_round.xml")

def main():
    print("=" * 50)
    print("Android 图标生成工具")
    print("=" * 50)

    # 加载原始图片
    if not os.path.exists(SOURCE_IMAGE):
        print(f"错误: 找不到源图片 {SOURCE_IMAGE}")
        return

    print(f"\n加载源图片: {SOURCE_IMAGE}")
    img = Image.open(SOURCE_IMAGE)
    print(f"原始尺寸: {img.size}")

    # 转换为 RGBA 模式以支持透明度
    if img.mode != "RGBA":
        img = img.convert("RGBA")

    # 生成图标
    create_launcher_icons(img)
    create_round_launcher_icons(img)
    create_adaptive_icons(img)
    create_adaptive_background()
    update_adaptive_xml()

    print("\n" + "=" * 50)
    print("图标生成完成！")
    print("=" * 50)

if __name__ == "__main__":
    main()
