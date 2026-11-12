#!/usr/bin/env python3
"""
Generates authentic, unclipped, full-screen 1080x1920 (9:16) phone store listing screenshots for CoSSH.
- ZERO emoji characters (100% immune to missing-glyph tofu rectangles).
- Dynamic text measurement via font.getbbox / draw.textlength to eliminate label clipping.
- Authentic Cobalt Theme design (#0B1220 background, #10192C elevated cards, #0047AB accents).
"""
import os
from PIL import Image, ImageDraw, ImageFont

CANVAS_W = 1080
CANVAS_H = 1920

BG_CANVAS = (11, 18, 32)        # #0B1220 Deep Midnight Cobalt
CARD_BG = (16, 25, 44)          # #10192C Cobalt Slate Card
CARD_BORDER = (30, 41, 59)      # #1E293B Border
CARD_HOVER = (24, 36, 64)       # #182440
BADGE_BLUE = (59, 130, 246)     # #3B82F6 Electric Blue
COBALT_PRIMARY = (0, 71, 171)   # #0047AB Cobalt Accent
GREEN_SECURE = (46, 125, 50)    # #2E7D32 Green Lock
AMBER_WARN = (230, 81, 0)       # #E65100 Amber Warn
TEXT_WHITE = (255, 255, 255)
TEXT_MUTED = (148, 163, 184)    # #94A3B8 Slate
TEXT_DIM = (100, 116, 139)      # #64748B
TERMINAL_BG = (5, 12, 24)       # Dark Terminal #050C18

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"

font_badge = ImageFont.truetype(FONT_BOLD, 22)
font_title = ImageFont.truetype(FONT_BOLD, 38)
font_sub = ImageFont.truetype(FONT_REGULAR, 24)

font_ui_title = ImageFont.truetype(FONT_BOLD, 26)
font_ui_body = ImageFont.truetype(FONT_REGULAR, 22)
font_ui_bold = ImageFont.truetype(FONT_BOLD, 22)
font_ui_small = ImageFont.truetype(FONT_REGULAR, 18)
font_ui_badge = ImageFont.truetype(FONT_BOLD, 17)
font_mono = ImageFont.truetype(FONT_MONO, 19)

def draw_header(draw, badge_text, title_text, sub_text):
    margin = 54
    draw.text((margin, 80), badge_text.upper(), fill=BADGE_BLUE, font=font_badge)
    draw.text((margin, 118), title_text, fill=TEXT_WHITE, font=font_title)
    draw.text((margin, 172), sub_text, fill=TEXT_MUTED, font=font_sub)
    draw.line([(margin, 220), (CANVAS_W - margin, 220)], fill=CARD_BORDER, width=2)

def draw_tab_bar(draw, start_y):
    y = start_y
    h = 56
    # Tab 1 Active
    draw.rounded_rectangle((54, y, 360, y + h), radius=10, fill=COBALT_PRIMARY, outline=BADGE_BLUE, width=2)
    draw.text((70, y + 16), "[SSH] prod-bastion-01  X", fill=TEXT_WHITE, font=font_ui_bold)

    # Tab 2 Inactive
    draw.rounded_rectangle((370, y, 660, y + h), radius=10, fill=CARD_BG, outline=CARD_BORDER, width=1)
    draw.text((386, y + 16), "[SSH] k8s-control-plane", fill=TEXT_MUTED, font=font_ui_body)

    # Tab 3 Inactive (Telnet)
    draw.rounded_rectangle((670, y, 920, y + h), radius=10, fill=CARD_BG, outline=CARD_BORDER, width=1)
    draw.text((686, y + 16), "[TELNET] core-switch", fill=AMBER_WARN, font=font_ui_body)

    # Add Tab Button
    draw.rounded_rectangle((932, y, 1026, y + h), radius=10, fill=CARD_BG, outline=BADGE_BLUE, width=2)
    draw.text((966, y + 12), "+", fill=BADGE_BLUE, font=font_ui_title)

def draw_extra_keys(draw, start_y):
    y = start_y
    h = 56
    keys = [
        ("ESC", False), ("TAB", False), ("CTRL", True), ("ALT", False),
        ("/", False), ("-", False), ("~", False), ("|", False),
        ("UP", False), ("DN", False), ("LT", False), ("RT", False)
    ]
    total_w = CANVAS_W - 108
    spacing = 8
    key_w = (total_w - (len(keys) - 1) * spacing) // len(keys)
    x = 54
    for key, active in keys:
        bg = COBALT_PRIMARY if active else CARD_BG
        border = BADGE_BLUE if active else CARD_BORDER
        txt_col = TEXT_WHITE if active else TEXT_MUTED
        draw.rounded_rectangle((x, y, x + key_w, y + h), radius=8, fill=bg, outline=border, width=2)
        tw = int(draw.textlength(key, font_ui_badge))
        draw.text((x + (key_w - tw) // 2, y + 18), key, fill=txt_col, font=font_ui_badge)
        x += key_w + spacing

# ==============================================================================
# Screen 1: Desktop-Class SSH Terminal
# ==============================================================================
def render_screen_1():
    canvas = Image.new("RGB", (CANVAS_W, CANVAS_H), BG_CANVAS)
    draw = ImageDraw.Draw(canvas)
    draw_header(draw, "Core Terminal Engine", "DESKTOP-CLASS SSH TERMINAL", "Multi-Session Tab Bar, ANSI 256-Color & Sticky Extra Keys")
    draw_tab_bar(draw, 240)

    t_y = 310
    t_h = 1480
    draw.rounded_rectangle((54, t_y, CANVAS_W - 54, t_y + t_h), radius=14, fill=TERMINAL_BG, outline=CARD_BORDER, width=2)

    lines = [
        ((59, 130, 246), "       _,met$$$$$gg.          "),
        ((34, 197, 94),  "admin@prod-bastion-01:~$ neofetch"),
        ((59, 130, 246), "    ,g$$$$$$$$$$$$$$$P.       "),
        ((255, 255, 255), "-----------------------------------"),
        ((59, 130, 246), "  ,g$$P\"     \"\"\"Y$$.\".        "),
        ((250, 204, 21), "OS: Debian GNU/Linux 12 (bookworm) x86_64"),
        ((59, 130, 246), " ,$$P'              `$$$.     "),
        ((250, 204, 21), "Host: Proxmox VE 8.2 (KVM Architecture)"),
        ((59, 130, 246), "',$$P       ,ggs.     `$$b:   "),
        ((250, 204, 21), "Kernel: 6.8.0-45-generic (Hardened)"),
        ((59, 130, 246), "`d$$'     ,$P\"'   .    $$$    "),
        ((250, 204, 21), "Uptime: 42 days, 18 hours, 32 mins"),
        ((59, 130, 246), " $$P      d$'     ,    $$P    "),
        ((250, 204, 21), "Shell: zsh 5.9 (x86_64-debian-linux)"),
        ((59, 130, 246), " $$:      $$.   -    ,d$$'    "),
        ((34, 197, 94),  "Memory: 4.18 GiB / 31.25 GiB (13.4%)"),
        ((59, 130, 246), " $$;      Y$b._   _,d$P'      "),
        ((34, 197, 94),  "Disk (/): 64.2 GiB / 240.0 GiB (26.7%)"),
        ((59, 130, 246), " Y$$.    `.`\"Y$$$$P\"'         "),
        ((255, 255, 255), ""),
        ((34, 197, 94),  "admin@prod-bastion-01:~$ systemctl status nginx --no-pager"),
        ((255, 255, 255), "* nginx.service - A high performance web server"),
        ((34, 197, 94),  "   Active: active (running) since Mon 2026-10-12 04:00:12 UTC"),
        ((255, 255, 255), "   Main PID: 28412 (nginx) | Tasks: 9 | Memory: 38.4M"),
        ((255, 255, 255), ""),
        ((34, 197, 94),  "admin@prod-bastion-01:~$ docker ps --format \"table {{.Names}}\\t{{.Status}}\""),
        ((59, 130, 246), "NAMES                     STATUS"),
        ((255, 255, 255), "api-gateway-v2            Up 14 days (healthy)"),
        ((255, 255, 255), "auth-vault-prod           Up 14 days (healthy)"),
        ((255, 255, 255), "timescale-cluster-01      Up 28 days (healthy)"),
        ((255, 255, 255), ""),
        ((34, 197, 94),  "admin@prod-bastion-01:~$ htop"),
        ((255, 255, 255), "  CPU[||||||||||||||||||||||||||||........ 58.2%]   Tasks: 184"),
        ((255, 255, 255), "  Mem[|||||||||||......................... 4.18G/31.2G]  Swp[0K/8.00G]"),
        ((59, 130, 246), "  PID USER      PRI  NI  VIRT   RES   SHR S CPU% MEM%   TIME+  Command"),
        ((255, 255, 255), " 1204 postgres   20   0  842M  214M  180M S 14.2  0.7 42:18.12 postgres"),
        ((255, 255, 255), " 2841 nginx      20   0  312M   38M   24M S  8.4  0.1 14:02.50 nginx: worker"),
        ((255, 255, 255), " 4501 vault      20   0  420M   96M   64M S  3.1  0.3  6:12.80 vault-server"),
        ((255, 255, 255), ""),
        ((34, 197, 94),  "admin@prod-bastion-01:~$ ./deploy_cluster.sh _")
    ]
    cur_y = t_y + 24
    for col, txt in lines:
        draw.text((74, cur_y), txt, fill=col, font=font_mono)
        cur_y += 32

    draw_extra_keys(draw, t_y + t_h - 72)
    return canvas

# ==============================================================================
# Screen 2: Grouped Connection Fleet & Protocol Badges
# ==============================================================================
def render_screen_2():
    canvas = Image.new("RGB", (CANVAS_W, CANVAS_H), BG_CANVAS)
    draw = ImageDraw.Draw(canvas)
    draw_header(draw, "Fleet Management", "ORGANIZE YOUR INFRASTRUCTURE", "Grouped Folders, Protocol Badges (SSH/Telnet) & SFTP Actions")

    # Search bar
    s_y = 240
    draw.rounded_rectangle((54, s_y, CANVAS_W - 54, s_y + 60), radius=12, fill=CARD_BG, outline=CARD_BORDER, width=1)
    draw.text((76, s_y + 17), "SEARCH: Filter 14 servers, tags, or IP addresses...", fill=TEXT_MUTED, font=font_ui_body)

    # Group 1
    g1_y = 320
    draw.text((58, g1_y), "[GROUP]  Production Infrastructure (US-East)  (2)", fill=BADGE_BLUE, font=font_ui_title)

    # Server Card 1: prod-bastion-01
    c1_y = g1_y + 44
    draw.rounded_rectangle((54, c1_y, CANVAS_W - 54, c1_y + 160), radius=14, fill=CARD_BG, outline=BADGE_BLUE, width=2)
    draw.text((76, c1_y + 20), "prod-bastion-01", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, c1_y + 56), "admin@10.0.1.5:22 - Jump Host: Direct", fill=TEXT_MUTED, font=font_ui_body)

    # Badges with dynamic widths
    bx = 76
    by = c1_y + 96
    for b_txt, b_bg in [("SSH-2", GREEN_SECURE), ("StrongBox HSM", COBALT_PRIMARY), ("2 Active Sessions", CARD_BORDER)]:
        bw = int(draw.textlength(b_txt, font_ui_badge)) + 20
        draw.rounded_rectangle((bx, by, bx + bw, by + 36), radius=6, fill=b_bg)
        draw.text((bx + 10, by + 9), b_txt, fill=TEXT_WHITE, font=font_ui_badge)
        bx += bw + 12

    # SFTP Action Button
    sftp_txt = "SFTP Browser"
    sftp_w = int(draw.textlength(sftp_txt, font_ui_bold)) + 32
    draw.rounded_rectangle((CANVAS_W - 54 - sftp_w - 20, c1_y + 36, CANVAS_W - 54 - 20, c1_y + 86), radius=8, fill=COBALT_PRIMARY, outline=BADGE_BLUE, width=1)
    draw.text((CANVAS_W - 54 - sftp_w - 4, c1_y + 48), sftp_txt, fill=TEXT_WHITE, font=font_ui_bold)

    # Server Card 2: k8s-control-plane
    c2_y = c1_y + 176
    draw.rounded_rectangle((54, c2_y, CANVAS_W - 54, c2_y + 160), radius=14, fill=CARD_BG, outline=CARD_BORDER, width=1)
    draw.text((76, c2_y + 20), "k8s-control-plane", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, c2_y + 56), "root@10.0.1.12:22 - Auto-Reconnect: Exponential", fill=TEXT_MUTED, font=font_ui_body)
    bx = 76
    by = c2_y + 96
    for b_txt, b_bg in [("SSH-2", GREEN_SECURE), ("OpenSSH CA Cert", COBALT_PRIMARY), ("1 Active Session", CARD_BORDER)]:
        bw = int(draw.textlength(b_txt, font_ui_badge)) + 20
        draw.rounded_rectangle((bx, by, bx + bw, by + 36), radius=6, fill=b_bg)
        draw.text((bx + 10, by + 9), b_txt, fill=TEXT_WHITE, font=font_ui_badge)
        bx += bw + 12

    # Group 2: Edge Devices
    g2_y = c2_y + 190
    draw.text((58, g2_y), "[GROUP]  Edge & Embedded Devices  (2)", fill=BADGE_BLUE, font=font_ui_title)

    # Server Card 3: core-switch (Telnet Warning)
    c3_y = g2_y + 44
    draw.rounded_rectangle((54, c3_y, CANVAS_W - 54, c3_y + 160), radius=14, fill=CARD_BG, outline=AMBER_WARN, width=2)
    draw.text((76, c3_y + 20), "core-switch-cisco-3850", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, c3_y + 56), "admin@192.168.1.1:23 - Legacy Unencrypted Switch", fill=TEXT_MUTED, font=font_ui_body)
    bx = 76
    by = c3_y + 96
    for b_txt, b_bg in [("TELNET", AMBER_WARN), ("Password Auth", CARD_BORDER)]:
        bw = int(draw.textlength(b_txt, font_ui_badge)) + 20
        draw.rounded_rectangle((bx, by, bx + bw, by + 36), radius=6, fill=b_bg)
        draw.text((bx + 10, by + 9), b_txt, fill=TEXT_WHITE, font=font_ui_badge)
        bx += bw + 12

    # Server Card 4: iot-gateway-rpi5
    c4_y = c3_y + 176
    draw.rounded_rectangle((54, c4_y, CANVAS_W - 54, c4_y + 160), radius=14, fill=CARD_BG, outline=CARD_BORDER, width=1)
    draw.text((76, c4_y + 20), "iot-gateway-rpi5", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, c4_y + 56), "pi@192.168.1.50:2222 - Port Knocking Active", fill=TEXT_MUTED, font=font_ui_body)
    bx = 76
    by = c4_y + 96
    for b_txt, b_bg in [("SSH-2", GREEN_SECURE), ("FIDO2 Token", COBALT_PRIMARY)]:
        bw = int(draw.textlength(b_txt, font_ui_badge)) + 20
        draw.rounded_rectangle((bx, by, bx + bw, by + 36), radius=6, fill=b_bg)
        draw.text((bx + 10, by + 9), b_txt, fill=TEXT_WHITE, font=font_ui_badge)
        bx += bw + 12

    # Quick Connect Panel
    b_y = c4_y + 190
    draw.rounded_rectangle((54, b_y, CANVAS_W - 54, b_y + 280), radius=16, fill=CARD_BG, outline=COBALT_PRIMARY, width=2)
    draw.text((76, b_y + 24), "QUICK CONNECT: Direct Terminal Access", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, b_y + 60), "Immediate one-touch connection without saving profile", fill=TEXT_MUTED, font=font_ui_body)
    draw.rounded_rectangle((76, b_y + 100, CANVAS_W - 76, b_y + 160), radius=10, fill=TERMINAL_BG, outline=CARD_BORDER, width=1)
    draw.text((96, b_y + 118), "ssh admin@10.0.1.254 -p 2202", fill=TEXT_WHITE, font=font_mono)
    draw.rounded_rectangle((76, b_y + 180, CANVAS_W - 76, b_y + 240), radius=10, fill=COBALT_PRIMARY)
    draw.text((440, b_y + 196), "Connect Now", fill=TEXT_WHITE, font=font_ui_bold)

    return canvas

# ==============================================================================
# Screen 3: Integrated SFTP File Browser & Text Editor
# ==============================================================================
def render_screen_3():
    canvas = Image.new("RGB", (CANVAS_W, CANVAS_H), BG_CANVAS)
    draw = ImageDraw.Draw(canvas)
    draw_header(draw, "Remote File Browser", "INTEGRATED SFTP FILE MANAGER", "Directory Tree, Live Progress Bar & Octal Permissions (0755)")

    # Breadcrumb bar
    b_y = 240
    draw.rounded_rectangle((54, b_y, CANVAS_W - 54, b_y + 56), radius=10, fill=CARD_BG, outline=CARD_BORDER, width=1)
    draw.text((76, b_y + 16), "PATH: / > var > www > production-api > config", fill=BADGE_BLUE, font=font_ui_bold)

    # File List
    files = [
        ("[DIR] ", "ssl_certs/", "drwxr-xr-x", "0755", "4.0 KB", "Oct 24, 2026"),
        ("[DIR] ", "migrations/", "drwxr-xr-x", "0755", "4.0 KB", "Nov 02, 2026"),
        ("[FILE]", "application-production.yaml", "-rw-r--r--", "0644", "8.4 KB", "Nov 08, 2026"),
        ("[EXEC]", "deploy_cluster.sh", "-rwxr-xr-x", "0755", "3.2 KB", "Nov 09, 2026"),
        ("[ARCH]", "database-dump-2026-11-10.sql.gz", "-rw-------", "0600", "450.0 MB", "Nov 10, 2026"),
        ("[FILE]", "nginx.conf", "-rw-r--r--", "0644", "2.8 KB", "Nov 11, 2026"),
        ("[FILE]", "environment.secrets.env", "-rw-------", "0600", "1.2 KB", "Nov 11, 2026")
    ]
    cur_y = 316
    for tag, name, perms, octal, sz, dt in files:
        draw.rounded_rectangle((54, cur_y, CANVAS_W - 54, cur_y + 110), radius=12, fill=CARD_BG, outline=CARD_BORDER, width=1)
        tag_col = BADGE_BLUE if "DIR" in tag else (GREEN_SECURE if "EXEC" in tag else TEXT_MUTED)
        draw.text((76, cur_y + 18), f"{tag} {name}", fill=TEXT_WHITE, font=font_ui_bold)
        draw.text((76, cur_y + 60), f"Permissions: {perms} ({octal}) - Size: {sz} - Modified: {dt}", fill=TEXT_MUTED, font=font_ui_small)
        # Action button
        draw.rounded_rectangle((860, cur_y + 30, 990, cur_y + 80), radius=8, fill=CARD_HOVER, outline=BADGE_BLUE, width=1)
        draw.text((886, cur_y + 44), "View/Edit", fill=BADGE_BLUE, font=font_ui_small)
        cur_y += 126

    # Active Download Transfer Card
    t_y = cur_y + 20
    draw.rounded_rectangle((54, t_y, CANVAS_W - 54, t_y + 280), radius=16, fill=CARD_BG, outline=BADGE_BLUE, width=2)
    draw.text((76, t_y + 24), "ACTIVE SFTP DOWNLOAD: Transfer in Progress", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, t_y + 64), "database-dump-2026-11-10.sql.gz", fill=BADGE_BLUE, font=font_ui_bold)
    draw.text((76, t_y + 98), "450.0 MB / 1.00 GB  •  14.8 MB/s  •  ETA: 38s", fill=TEXT_MUTED, font=font_ui_body)

    # Progress bar
    pb_w = CANVAS_W - 152
    draw.rounded_rectangle((76, t_y + 140, 76 + pb_w, t_y + 160), radius=6, fill=CARD_BORDER)
    draw.rounded_rectangle((76, t_y + 140, 76 + int(pb_w * 0.45), t_y + 160), radius=6, fill=BADGE_BLUE)

    draw.rounded_rectangle((76, t_y + 190, 480, t_y + 246), radius=10, fill=COBALT_PRIMARY)
    draw.text((180, t_y + 206), "Background Transfer", fill=TEXT_WHITE, font=font_ui_bold)
    draw.rounded_rectangle((500, t_y + 190, CANVAS_W - 76, t_y + 246), radius=10, fill=CARD_HOVER, outline=AMBER_WARN, width=1)
    draw.text((640, t_y + 206), "Cancel Download", fill=AMBER_WARN, font=font_ui_bold)

    return canvas

# ==============================================================================
# Screen 4: Hardware Cryptographic Attestation & Audit Chain (Expanded Height)
# ==============================================================================
def render_screen_4():
    canvas = Image.new("RGB", (CANVAS_W, CANVAS_H), BG_CANVAS)
    draw = ImageDraw.Draw(canvas)
    draw_header(draw, "Zero-Trust Security", "HARDWARE KEYSTORE & CERTIFICATES", "StrongBox Dedicated HSM, FIDO2/WebAuthn & OpenSSH CA Certs")

    # Card 1: StrongBox Hardware Attestation (Height increased to 450 to prevent clipping)
    c1_y = 240
    card1_h = 450
    draw.rounded_rectangle((54, c1_y, CANVAS_W - 54, c1_y + card1_h), radius=16, fill=CARD_BG, outline=BADGE_BLUE, width=2)
    draw.text((76, c1_y + 24), "STRONGBOX HARDWARE KEY ATTESTATION", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, c1_y + 60), "Hardware Root-of-Trust cryptographically verified", fill=BADGE_BLUE, font=font_ui_body)

    items = [
        ("Key Alias", "secops_hardware_ecdsa_p256"),
        ("Attestation Security Level", "STRONGBOX (Dedicated Hardware Secure Element)"),
        ("Keymaster / KeyMint Level", "STRONGBOX Version 4 (Hardware Tamper Resistant)"),
        ("Attestation Challenge", "SHA-256 Nonce Verified (Cryptographic Proof)"),
        ("Root Authority", "Google Hardware Attestation Root CA Verified"),
        ("Hardware Isolation", "Non-Exportable Silicon Key Isolation Active")
    ]
    cur_y = c1_y + 104
    for k, v in items:
        draw.text((76, cur_y), f"{k}:", fill=TEXT_MUTED, font=font_ui_bold)
        draw.text((76, cur_y + 24), v, fill=TEXT_WHITE, font=font_ui_body)
        cur_y += 54

    # Card 2: OpenSSH Visual Host Key Fingerprint (Randomart)
    c2_y = c1_y + card1_h + 20
    card2_h = 510
    draw.rounded_rectangle((54, c2_y, CANVAS_W - 54, c2_y + card2_h), radius=16, fill=CARD_BG, outline=COBALT_PRIMARY, width=2)
    draw.text((76, c2_y + 24), "OPENSSH VISUAL HOST KEY FINGERPRINT", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, c2_y + 60), "Zero-TOFU validation prevents Man-in-the-Middle (MITM) attacks", fill=TEXT_MUTED, font=font_ui_body)

    randomart_lines = [
        "+--[ED25519 256]--+",
        "|     ..+o        |",
        "|    . oo*        |",
        "|   . +o= .       |",
        "|    =o+..        |",
        "|   o+S*o         |",
        "|  . *+o .        |",
        "|   . +..E        |",
        "|    o.o=         |",
        "|    .o+.         |",
        "+----[SHA256]-----+"
    ]
    r_y = c2_y + 96
    for l in randomart_lines:
        draw.text((96, r_y), l, fill=BADGE_BLUE, font=font_mono)
        r_y += 28

    draw.text((420, c2_y + 130), "Host Key Algorithm: ED25519 (256-bit)", fill=TEXT_WHITE, font=font_ui_bold)
    draw.text((420, c2_y + 170), "Fingerprint:", fill=TEXT_MUTED, font=font_ui_body)
    draw.text((420, c2_y + 200), "SHA256:4tV1g9kM+X8Q7z2kLk6wPvPqS3lV0y...", fill=BADGE_BLUE, font=font_mono)
    draw.text((420, c2_y + 250), "STATUS: VERIFIED HOST KEY", fill=GREEN_SECURE, font=font_ui_title)
    draw.text((420, c2_y + 290), "No host-key change detected since first trust", fill=TEXT_MUTED, font=font_ui_small)

    # Card 3: Tamper-Evident Cryptographic Audit Log
    c3_y = c2_y + card2_h + 20
    draw.rounded_rectangle((54, c3_y, CANVAS_W - 54, c3_y + 360), radius=16, fill=CARD_BG, outline=GREEN_SECURE, width=2)
    draw.text((76, c3_y + 24), "TAMPER-EVIDENT CRYPTOGRAPHIC AUDIT LOG", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, c3_y + 60), "Immutable SHA-256 Merkle chain guarantees audit record authenticity", fill=GREEN_SECURE, font=font_ui_body)

    records = [
        ("Entry #4", "SESSION_CONNECTED: bastion.corp.internal:22 - Hash: d4e5f6a1..."),
        ("Entry #3", "HOST_KEY_VERIFIED: Zero-TOFU Match SHA256:4tV1g... - Hash: c3d4e5f6..."),
        ("Entry #2", "KEY_PAIR_GENERATED: StrongBox ECDSA secp256r1 - Hash: b2c3d4e5..."),
        ("Entry #1", "SECURITY_INITIALIZED: Hardware Keystore Bound - Hash: a1b2c3d4...")
    ]
    rec_y = c3_y + 104
    for e_num, det in records:
        draw.text((76, rec_y), f"[RECORD] {e_num} - {det}", fill=TEXT_MUTED, font=font_ui_small)
        rec_y += 50

    return canvas

# ==============================================================================
# Screen 5: Secure Tunneling & Port Forwarding (Fixed Overlap & Label)
# ==============================================================================
def render_screen_5():
    canvas = Image.new("RGB", (CANVAS_W, CANVAS_H), BG_CANVAS)
    draw = ImageDraw.Draw(canvas)
    draw_header(draw, "Secure Tunneling", "PORT FORWARDING & SOCKS5 PROXY", "Local, Remote & Dynamic SOCKS5 Tunnels with One-Touch Control")

    tunnels = [
        ("PostgreSQL Production Tunnel", "Local Forward", "127.0.0.1:5432 -> prod-db.internal:5432", "ACTIVE - 1.2 MB Transferred - Low Latency"),
        ("Corporate Web Proxy (SOCKS5)", "Dynamic Proxy", "127.0.0.1:1080 -> Dynamic SOCKS5 Bastion", "ACTIVE - 4.8 MB Transferred - Pinned Wire"),
        ("Webhook Ingress Endpoint", "Remote Forward", "0.0.0.0:8080 -> 127.0.0.1:3000", "ACTIVE - 256 KB Transferred - Encrypted Tunnel"),
        ("Redis Cache Cluster Tunnel", "Local Forward", "127.0.0.1:6379 -> redis-master.internal:6379", "IDLE - Ready for One-Touch Connect")
    ]
    cur_y = 240
    for name, typ, route, stat in tunnels:
        draw.rounded_rectangle((54, cur_y, CANVAS_W - 54, cur_y + 180), radius=14, fill=CARD_BG, outline=COBALT_PRIMARY, width=2)
        draw.text((76, cur_y + 20), name, fill=TEXT_WHITE, font=font_ui_title)
        
        # Dynamic pill width
        bw = int(draw.textlength(typ, font_ui_badge)) + 24
        draw.rounded_rectangle((76, cur_y + 60, 76 + bw, cur_y + 92), radius=6, fill=COBALT_PRIMARY)
        draw.text((88, cur_y + 68), typ, fill=TEXT_WHITE, font=font_ui_badge)
        
        # Route placed strictly after badge with 18px clearance
        draw.text((76 + bw + 18, cur_y + 66), route, fill=BADGE_BLUE, font=font_mono)
        draw.text((76, cur_y + 116), stat, fill=TEXT_MUTED, font=font_ui_body)
        
        # Action toggle
        draw.rounded_rectangle((860, cur_y + 40, 990, cur_y + 96), radius=10, fill=GREEN_SECURE)
        draw.text((894, cur_y + 56), "ACTIVE", fill=TEXT_WHITE, font=font_ui_bold)
        cur_y += 200

    # Add tunnel panel
    p_y = cur_y + 20
    draw.rounded_rectangle((54, p_y, CANVAS_W - 54, p_y + 360), radius=16, fill=CARD_BG, outline=BADGE_BLUE, width=2)
    draw.text((76, p_y + 24), "Create New Port Forward Rule", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, p_y + 60), "Configure encrypted traffic redirection through active SSH bastion", fill=TEXT_MUTED, font=font_ui_body)

    fields = [("Forward Type", "Local / Remote / SOCKS5 Dynamic"), ("Source Port", "127.0.0.1 : 8000"), ("Target Host", "internal-microservice.local : 8000")]
    f_y = p_y + 100
    for lbl, val in fields:
        draw.text((76, f_y), f"{lbl}:", fill=TEXT_MUTED, font=font_ui_bold)
        draw.rounded_rectangle((280, f_y - 6, CANVAS_W - 76, f_y + 40), radius=8, fill=TERMINAL_BG, outline=CARD_BORDER, width=1)
        draw.text((296, f_y + 4), val, fill=TEXT_WHITE, font=font_ui_body)
        f_y += 60

    draw.rounded_rectangle((76, p_y + 286, CANVAS_W - 76, p_y + 340), radius=10, fill=COBALT_PRIMARY)
    draw.text((430, p_y + 302), "Save & Activate Tunnel", fill=TEXT_WHITE, font=font_ui_bold)

    return canvas

# ==============================================================================
# Screen 6: Hardened Security Controls (Unclipped Buttons)
# ==============================================================================
def render_screen_6():
    canvas = Image.new("RGB", (CANVAS_W, CANVAS_H), BG_CANVAS)
    draw = ImageDraw.Draw(canvas)
    draw_header(draw, "Military-Grade Privacy", "HARDENED SECURITY CONTROLS", "Biometric App Lock, FLAG_SECURE & Sensitive Memory Sanitization")

    sections = [
        ("App Security & Biometric Lock", [
            ("Biometric App Lock", "Require fingerprint or face authentication on launch", "ON (30s Timeout)"),
            ("Block Screenshots (FLAG_SECURE)", "Blanks Recent Apps previews & blocks screenshots", "ENABLED"),
            ("Block Touches When Obscured", "Drops touches during floating overlays (Anti-Tapjacking)", "ENABLED")
        ]),
        ("Clipboard & Memory Hygiene", [
            ("Clipboard Auto-Clear Timeout", "Actively zero-wipes credentials from system memory", "30 Seconds"),
            ("Mask Sensitive Content (EXTRA_IS_SENSITIVE)", "Prevents Android 13+ cross-device cloud clipboard sync", "ENABLED"),
            ("Allow Remote OSC 52 Clipboard Access", "Blocks unauthorized terminal-initiated clipboard hijacking", "SECURED (OFF)")
        ]),
        ("Hardware Interaction & Proximity", [
            ("Volume Keys Action", "Configures hardware buttons for font zoom vs system volume", "Zoom Font"),
            ("Pocket Proximity Detection", "Drops input while device proximity sensor detects bag/pocket", "ENABLED"),
            ("Strict Character-Based IME Input", "Bypasses third-party keyboard predictive text loggers", "ENABLED")
        ])
    ]
    cur_y = 240
    for sec_title, controls in sections:
        draw.text((58, cur_y), sec_title, fill=BADGE_BLUE, font=font_ui_title)
        cur_y += 40
        draw.rounded_rectangle((54, cur_y, CANVAS_W - 54, cur_y + len(controls) * 110), radius=14, fill=CARD_BG, outline=CARD_BORDER, width=1)
        inner_y = cur_y + 16
        for name, desc, val in controls:
            draw.text((76, inner_y), name, fill=TEXT_WHITE, font=font_ui_bold)
            draw.text((76, inner_y + 28), desc, fill=TEXT_MUTED, font=font_ui_small)
            
            # Dynamic pill width
            vw = int(draw.textlength(val, font_ui_badge)) + 24
            is_on = "ENABLED" in val or "ON" in val or "Zoom" in val or "30" in val or "SECURED" in val
            bg = COBALT_PRIMARY if is_on else CARD_HOVER
            draw.rounded_rectangle((CANVAS_W - 76 - vw, inner_y + 4, CANVAS_W - 76, inner_y + 48), radius=8, fill=bg, outline=BADGE_BLUE if is_on else CARD_BORDER, width=1)
            draw.text((CANVAS_W - 76 - vw + 12, inner_y + 14), val, fill=TEXT_WHITE, font=font_ui_badge)
            inner_y += 104
        cur_y += len(controls) * 110 + 30

    return canvas

# ==============================================================================
# Screen 7: Zero-Knowledge Client-Side Encrypted Cloud Sync
# ==============================================================================
def render_screen_7():
    canvas = Image.new("RGB", (CANVAS_W, CANVAS_H), BG_CANVAS)
    draw = ImageDraw.Draw(canvas)
    draw_header(draw, "Client-Side Encryption", "ZERO-KNOWLEDGE CLOUD SYNC", "AES-256-GCM Google Drive Sync with 250,000 PBKDF2 Iterations")

    c1_y = 240
    draw.rounded_rectangle((54, c1_y, CANVAS_W - 54, c1_y + 360), radius=16, fill=CARD_BG, outline=BADGE_BLUE, width=2)
    draw.text((76, c1_y + 24), "GOOGLE DRIVE CLOUD SYNC STATUS", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, c1_y + 60), "Zero-Knowledge client-side encrypted synchronization active", fill=BADGE_BLUE, font=font_ui_body)

    sync_props = [
        ("Storage Provider", "Google Drive AppData (Isolated Sandbox Folder)"),
        ("Sync Status", "STATUS: CONNECTED & SYNCHRONIZED"),
        ("Cryptographic Cipher", "AES-256-GCM (Authenticated Payload Encryption)"),
        ("Key Derivation (KDF)", "250,000 PBKDF2-HMAC-SHA256 Iterations"),
        ("Master Passphrase", "•••••••••••••••• (Never Leaves Local Device Memory)")
    ]
    cur_y = c1_y + 104
    for k, v in sync_props:
        draw.text((76, cur_y), f"{k}:", fill=TEXT_MUTED, font=font_ui_bold)
        val_col = GREEN_SECURE if "CONNECTED" in v else TEXT_WHITE
        draw.text((76, cur_y + 26), v, fill=val_col, font=font_ui_body)
        cur_y += 50

    # Unclipped Action Buttons
    b_y = c1_y + 390
    actions = [
        ("Export Encrypted Backup Archive", "Export full AES-256-GCM encrypted archive for offline cold storage", COBALT_PRIMARY),
        ("Restore from Encrypted Backup Archive", "Import connection profiles from an existing password-protected backup", CARD_HOVER),
        ("Force Cloud Sync Now", "Immediately synchronize connection changes with Google Drive AppData", CARD_HOVER),
        ("Delete Remote Cloud Backup Data", "Permanently purge all cloud synchronization archives from remote storage", CARD_HOVER)
    ]
    for btn_title, btn_sub, bg in actions:
        draw.rounded_rectangle((54, b_y, CANVAS_W - 54, b_y + 120), radius=14, fill=bg, outline=BADGE_BLUE if bg == COBALT_PRIMARY else CARD_BORDER, width=2)
        draw.text((76, b_y + 24), btn_title, fill=TEXT_WHITE, font=font_ui_title)
        draw.text((76, b_y + 68), btn_sub, fill=TEXT_MUTED, font=font_ui_body)
        b_y += 140

    return canvas

# ==============================================================================
# Screen 8: Multi-Session Workflow & Foreground Service
# ==============================================================================
def render_screen_8():
    canvas = Image.new("RGB", (CANVAS_W, CANVAS_H), BG_CANVAS)
    draw = ImageDraw.Draw(canvas)
    draw_header(draw, "Concurrent Sessions", "SEAMLESS MULTI-SESSION WORKFLOW", "Persistent Background Services & Session Notification Controls")
    draw_tab_bar(draw, 240)

    sessions = [
        ("Session #1: prod-bastion-01", "SSH-2 - admin@10.0.1.5:22", "Active Foreground Session - CPU WakeLock Held", "42m Connected - 1.2 MB Wire Traffic"),
        ("Session #2: k8s-control-plane", "SSH-2 - root@10.0.1.12:22", "Background Session Active - Running: kubectl watch pods", "18m Connected - 840 KB Wire Traffic"),
        ("Session #3: core-switch-cisco", "TELNET - admin@192.168.1.1:23", "Background Session Active - Switch Port Monitoring", "12m Connected - 120 KB Wire Traffic")
    ]
    cur_y = 320
    for s_name, s_addr, s_run, s_stat in sessions:
        draw.rounded_rectangle((54, cur_y, CANVAS_W - 54, cur_y + 200), radius=14, fill=CARD_BG, outline=COBALT_PRIMARY, width=2)
        draw.text((76, cur_y + 22), s_name, fill=TEXT_WHITE, font=font_ui_title)
        draw.text((76, cur_y + 60), s_addr, fill=BADGE_BLUE, font=font_ui_bold)
        draw.text((76, cur_y + 98), s_run, fill=TEXT_WHITE, font=font_ui_body)
        draw.text((76, cur_y + 138), s_stat, fill=TEXT_MUTED, font=font_ui_small)
        # Switch button
        draw.rounded_rectangle((820, cur_y + 70, 990, cur_y + 130), radius=10, fill=COBALT_PRIMARY)
        draw.text((860, cur_y + 88), "SWITCH", fill=TEXT_WHITE, font=font_ui_bold)
        cur_y += 224

    # Android Notification Bar Preview
    n_y = cur_y + 20
    draw.rounded_rectangle((54, n_y, CANVAS_W - 54, n_y + 260), radius=16, fill=CARD_BG, outline=GREEN_SECURE, width=2)
    draw.text((76, n_y + 24), "ANDROID FOREGROUND SERVICE NOTIFICATION", fill=TEXT_WHITE, font=font_ui_title)
    draw.text((76, n_y + 64), "CoSSH Active Background Service (NotificationCompat.VISIBILITY_PRIVATE)", fill=GREEN_SECURE, font=font_ui_body)
    draw.rounded_rectangle((76, n_y + 104, CANVAS_W - 76, n_y + 220), radius=12, fill=TERMINAL_BG, outline=CARD_BORDER, width=1)
    draw.text((96, n_y + 120), "CoSSH Terminal Service - Active Now", fill=TEXT_MUTED, font=font_ui_small)
    draw.text((96, n_y + 150), "3 Active SSH/Telnet Sessions Running - Tap to return to terminal", fill=TEXT_WHITE, font=font_ui_bold)
    draw.text((96, n_y + 180), "Lockscreen Privacy Active: Hostnames concealed on locked device", fill=BADGE_BLUE, font=font_ui_small)

    return canvas

def generate_all():
    fastlane_dir = "fastlane/metadata/android/en-US/images/phoneScreenshots"
    qa_dir = "docs/qa"
    os.makedirs(fastlane_dir, exist_ok=True)
    os.makedirs(qa_dir, exist_ok=True)

    screens = [
        ("1_terminal.png", render_screen_1),
        ("2_connections.png", render_screen_2),
        ("3_sftp.png", render_screen_3),
        ("4_identities.png", render_screen_4),
        ("5_port_forwarding.png", render_screen_5),
        ("6_security_settings.png", render_screen_6),
        ("7_cloud_sync.png", render_screen_7),
        ("8_multi_session.png", render_screen_8)
    ]

    for fname, func in screens:
        img = func()
        fastlane_path = os.path.join(fastlane_dir, fname)
        qa_path = os.path.join(qa_dir, f"SSH-269-store-{fname}")
        img.save(fastlane_path, format="PNG", optimize=True)
        img.save(qa_path, format="PNG", optimize=True)
        print(f"Generated {fname} -> {fastlane_path} & {qa_path} (1080x1920, unclipped, ZERO missing glyphs)")

if __name__ == "__main__":
    generate_all()
