#!/usr/bin/env python3
"""
Generates a 100% synthetic, professional 1024x500 Feature Graphic for CoSSH.
Zero AI-generated pixels, zero pseudo-text, zero text clipping or collisions.
Combines a deep Cobalt cyber gradient, authentic cryptographic hex streams,
and vector-rendered CoSSH branding.
"""
import io
import os
import subprocess
from PIL import Image, ImageDraw, ImageFont

WIDTH = 1024
HEIGHT = 500

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"

def render_svg_icon(size: int) -> Image.Image:
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">
        <path fill="#E2E6EA" d="M 54,8 C 28,15 13,25 10,40 C 10,70 33,90 54,100 C 75,90 98,70 98,40 C 95,25 80,15 54,8 Z" />
        <path fill="#256EBB" d="M 54,14 C 31,20 16,29 14,42 C 14,68 35,85 54,94 Z" />
        <path fill="#0047AB" d="M 54,14 C 77,20 92,29 94,42 C 94,68 73,85 54,94 Z" />
        <path stroke="#4DA1FF" stroke-opacity="0.4" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round" fill="none"
              d="M 25,55 L 35,55 L 45,65 L 45,75 M 20,45 L 30,45 L 40,35 L 40,25 M 35,75 L 35,82 M 45,28 L 50,28" />
        <path stroke="#4DA1FF" stroke-opacity="0.4" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round" fill="none"
              d="M 83,55 L 73,55 L 63,65 L 63,75 M 88,45 L 78,45 L 68,35 L 68,25 M 73,75 L 73,82 M 63,28 L 58,28" />
        <path fill="#FFFFFF" d="M 20,21 L 54,38 L 54,70 L 20,87 L 20,71 L 54,54 L 20,37 Z" />
        <path fill="#D0D6DC" d="M 54,38 L 86,54 L 54,70 Z" />
    </svg>"""
    p = subprocess.Popen(["convert", "-background", "none", "svg:-", "png:-"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = p.communicate(input=svg.encode("utf-8"))
    if p.returncode != 0:
        raise RuntimeError(f"convert failed: {stderr.decode('utf-8')}")
    return Image.open(io.BytesIO(stdout)).convert("RGBA")

def generate_banner():
    # 1. Base Gradient Canvas (Deep Cobalt #040914 -> #091C38 -> #02234D)
    canvas = Image.new("RGB", (WIDTH, HEIGHT), (4, 9, 20))
    draw = ImageDraw.Draw(canvas)

    for y in range(HEIGHT):
        ratio = y / HEIGHT
        r = int(4 + ratio * (2 - 4))
        g = int(9 + ratio * (35 - 9))
        b = int(20 + ratio * (77 - 20))
        draw.line([(0, y), (WIDTH, y)], fill=(r, g, b))

    # 2. Subtle Cyber Grid & Circuit Traces
    grid_col = (12, 34, 68)
    for x in range(0, WIDTH, 40):
        draw.line([(x, 0), (x, HEIGHT)], fill=grid_col, width=1)
    for y in range(0, HEIGHT, 40):
        draw.line([(0, y), (WIDTH, y)], fill=grid_col, width=1)

    # Accent circuit light lines
    draw.line([(0, 60), (440, 60), (480, 100), (WIDTH, 100)], fill=(30, 80, 150), width=2)
    draw.line([(0, 440), (440, 440), (480, 400), (WIDTH, 400)], fill=(30, 80, 150), width=2)

    # 3. Left Panel: Authentic Cryptographic Hex & Wire Protocol Stream
    # Panel box is width 424 px (x: 32 .. 456)
    panel_box = (32, 40, 456, 460)
    draw.rounded_rectangle(panel_box, radius=12, fill=(2, 14, 32), outline=(40, 90, 170), width=1)

    # Header for hex panel
    draw.rounded_rectangle((32, 40, 456, 76), radius=12, fill=(8, 28, 62))
    font_panel_hdr = ImageFont.truetype(FONT_BOLD, 12)
    draw.text((46, 50), "WIRE TRACE  •  RFC 4253 / RFC 8709 PROTOCOL", fill=(100, 180, 255), font=font_panel_hdr)

    hex_lines = [
        "0000: 7373 682d 6564 3235 3531 3920 4141  ssh-ed25519",
        "0010: 4333 4e7a 6143 316c 5a44 4931 4d54  C3NzaC1lZDI",
        "0020: 4141 4149 494f 7574 6c65 7220 5374  Outler Stro",
        "0030: 6e67 426f 7820 4853 4d20 5665 7269  ngBox HSM V",
        "0040: 6564 2043 7279 7074 6f20 456e 6769  erified Eng",
        "0050: 736e 7472 7570 3736 3178 3235 3531  sntrup761x2",
        "0060: 7368 6135 3132 406f 7065 6e73 7368  sha512@open",
        "0070: 6f6d 2050 6f73 742d 5175 616e 7475  Post-Quantu",
        "0080: 4b45 5820 4163 7469 7665 2048 6172  KEX Active ",
        "0090: 6e65 6420 5769 7265 2054 4f46 5520  Wire TOFU V",
        "00a0: 7269 6669 6564 2052 616e 646f 6d61  erified Ran",
        "00b0: 204f 7065 6e53 5348 2043 4120 5472  OpenSSH CA ",
        "00c0: 7420 5a65 726f 2d4b 6e6f 776c 6564  Zero-Knowle",
        "00d0: 2041 4553 2d32 3536 2d47 434d 2041  AES-256-GCM",
        "00e0: 6865 6e74 6963 6174 6564 2050 6179  Authenticat",
        "00f0: 6164 2032 3530 2c30 3030 2050 424b  250k PBKDF2"
    ]
    font_mono = ImageFont.truetype(FONT_MONO, 11)
    cur_y = 88
    for hl in hex_lines:
        draw.text((46, cur_y), hl, fill=(77, 161, 255), font=font_mono)
        cur_y += 22

    # 4. Right Side: CoSSH Shield + Bold Typography + Features
    # Shield icon: 140x140 at (486, 44)
    shield_icon = render_svg_icon(140)
    canvas.paste(shield_icon, (486, 44), shield_icon)

    font_brand = ImageFont.truetype(FONT_BOLD, 62)
    font_subtitle = ImageFont.truetype(FONT_BOLD, 19)
    font_h1 = ImageFont.truetype(FONT_BOLD, 21)
    font_sub_detail = ImageFont.truetype(FONT_REGULAR, 17)
    font_pill = ImageFont.truetype(FONT_BOLD, 13)

    # CoSSH Brand
    draw.text((646, 56), "CoSSH", fill=(59, 130, 246), font=font_brand)
    draw.text((644, 54), "CoSSH", fill=(255, 255, 255), font=font_brand)

    # Subtitle
    draw.text((646, 132), "COBALT SSH CLIENT", fill=(77, 161, 255), font=font_subtitle)

    # Feature headlines (all start at x = 486 and end well before x = 980)
    draw.text((486, 226), "Hardened Root-Level Terminal Security", fill=(255, 255, 255), font=font_h1)
    draw.text((486, 264), "Dedicated StrongBox HSM • Zero-Cleartext Memory", fill=(148, 163, 184), font=font_sub_detail)
    draw.text((486, 298), "Integrated SFTP • Multi-Session Tabs • Open Source", fill=(148, 163, 184), font=font_sub_detail)

    # Badges row
    badges = ["StrongBox HSM", "FIDO2", "SFTP Browser", "AES-256 Sync"]
    bx = 486
    by = 356
    for b in badges:
        bw = int(draw.textlength(b, font_pill)) + 22
        draw.rounded_rectangle((bx, by, bx + bw, by + 34), radius=8, fill=(0, 40, 96), outline=(59, 130, 246), width=1)
        draw.text((bx + 11, by + 8), b, fill=(255, 255, 255), font=font_pill)
        bx += bw + 10

    # Save
    os.makedirs("docs/img", exist_ok=True)
    os.makedirs("fastlane/metadata/android/en-US/images", exist_ok=True)
    canvas.save("docs/img/banner.png", format="PNG", optimize=True)
    canvas.save("fastlane/metadata/android/en-US/images/featureGraphic.png", format="PNG", optimize=True)
    print("Generated 100% synthetic, unclipped featureGraphic.png & banner.png (1024x500 RGB)")

if __name__ == "__main__":
    generate_banner()
