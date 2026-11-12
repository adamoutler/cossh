#!/usr/bin/env python3
"""
Regenerates clean, centered, zero-artifact store icons and raster launcher mipmaps for CoSSH.
"""
import io
import os
import subprocess
from PIL import Image, ImageDraw

SVG_TEMPLATE = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">
    <!-- Outer Shield (Silver) -->
    <path fill="#E2E6EA" d="M 54,8 C 28,15 13,25 10,40 C 10,70 33,90 54,100 C 75,90 98,70 98,40 C 95,25 80,15 54,8 Z" />
    <!-- Inner Shield Left (Light Cobalt) -->
    <path fill="#256EBB" d="M 54,14 C 31,20 16,29 14,42 C 14,68 35,85 54,94 Z" />
    <!-- Inner Shield Right (Dark Cobalt) -->
    <path fill="#0047AB" d="M 54,14 C 77,20 92,29 94,42 C 94,68 73,85 54,94 Z" />
    <!-- Circuit Lines Left (Cyan, 40% Opacity) -->
    <path stroke="#4DA1FF" stroke-opacity="0.4" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round" fill="none"
          d="M 25,55 L 35,55 L 45,65 L 45,75 M 20,45 L 30,45 L 40,35 L 40,25 M 35,75 L 35,82 M 45,28 L 50,28" />
    <!-- Circuit Lines Right (Cyan, 40% Opacity) -->
    <path stroke="#4DA1FF" stroke-opacity="0.4" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round" fill="none"
          d="M 83,55 L 73,55 L 63,65 L 63,75 M 88,45 L 78,45 L 68,35 L 68,25 M 73,75 L 73,82 M 63,28 L 58,28" />
    <!-- Left Chevron (White) -->
    <path fill="#FFFFFF" d="M 20,21 L 54,38 L 54,70 L 20,87 L 20,71 L 54,54 L 20,37 Z" />
    <!-- Right Chevron (Light Gray) -->
    <path fill="#D0D6DC" d="M 54,38 L 86,54 L 54,70 Z" />
</svg>"""

def render_standard_icon(size: int) -> Image.Image:
    svg_code = SVG_TEMPLATE.format(size=size)
    p = subprocess.Popen(
        ["convert", "-background", "none", "svg:-", "png:-"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    stdout, stderr = p.communicate(input=svg_code.encode("utf-8"))
    if p.returncode != 0:
        raise RuntimeError(f"convert failed: {stderr.decode('utf-8')}")
    return Image.open(io.BytesIO(stdout)).convert("RGBA")

def render_round_icon(size: int) -> Image.Image:
    fg = render_standard_icon(size)
    # Scale 4x for supersampled circle
    scale = 4
    high_res = Image.new("RGBA", (size * scale, size * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(high_res)
    draw.ellipse((0, 0, size * scale - 1, size * scale - 1), fill=(0, 0, 0, 255))
    circle_bg = high_res.resize((size, size), Image.Resampling.LANCZOS)
    
    combined = Image.alpha_composite(circle_bg, fg)
    
    mask_high = Image.new("L", (size * scale, size * scale), 0)
    mask_draw = ImageDraw.Draw(mask_high)
    mask_draw.ellipse((0, 0, size * scale - 1, size * scale - 1), fill=255)
    circle_mask = mask_high.resize((size, size), Image.Resampling.LANCZOS)
    
    final_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    final_icon.paste(combined, (0, 0), circle_mask)
    return final_icon

def generate_all():
    # 1. 512x512 Master Store Icon
    icon_512 = render_standard_icon(512)
    os.makedirs("docs/img", exist_ok=True)
    icon_512.save("docs/img/icon.png", format="PNG", optimize=True)
    print("Saved docs/img/icon.png (512x512, RGBA, zero smudge)")

    # 2. Raster Launcher Mipmaps
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        dir_path = os.path.join("app/src/main/res", folder)
        os.makedirs(dir_path, exist_ok=True)
        
        std = render_standard_icon(size)
        std.save(os.path.join(dir_path, "ic_launcher.png"), format="PNG", optimize=True)
        
        rnd = render_round_icon(size)
        rnd.save(os.path.join(dir_path, "ic_launcher_round.png"), format="PNG", optimize=True)
        print(f"Generated {folder}: {size}x{size} std & round")

if __name__ == "__main__":
    generate_all()
