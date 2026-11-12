#!/usr/bin/env python3
"""
Generates standard 1080x1920 (9:16) phone store listing screenshots for Google Play and F-Droid.
Combines high-resolution marketing hero banners with pixel-perfect committed UI captures.
"""
import os
from PIL import Image, ImageDraw, ImageFont

CANVAS_WIDTH = 1080
CANVAS_HEIGHT = 1920
BG_COLOR = (11, 18, 32)         # #0B1220 Deep Midnight Cobalt
CARD_BG = (16, 25, 44)          # #10192C Elevated Slate
BORDER_COLOR = (30, 41, 59)     # #1E293B Slate Border
BADGE_COLOR = (59, 130, 246)    # #3B82F6 Electric Blue
TITLE_COLOR = (255, 255, 255)   # #FFFFFF
SUBTITLE_COLOR = (148, 163, 184)# #94A3B8 Muted Slate

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

SCENARIOS = [
    {
        "filename": "1_terminal.png",
        "badge": "CORE TERMINAL ENGINE",
        "title": "DESKTOP-CLASS SSH TERMINAL",
        "subtitle": "Multi-Session Tab Bar, ANSI 256-Color & Sticky Extra Keys",
        "source": "docs/qa/SSH-197-CobaltBlue.png",
    },
    {
        "filename": "2_connections.png",
        "badge": "FLEET MANAGEMENT",
        "title": "ORGANIZE YOUR INFRASTRUCTURE",
        "subtitle": "Grouped Folders, Protocol Badges (SSH/Telnet) & SFTP Actions",
        "source": "docs/qa/SSH-122-connection-list.png",
    },
    {
        "filename": "3_sftp.png",
        "badge": "REMOTE FILE BROWSER",
        "title": "INTEGRATED SFTP FILE MANAGER",
        "subtitle": "Directory Tree, Live Progress Bar & Octal Permissions (0755)",
        "source": "docs/qa/SSH-223-sftp-browser.png",
    },
    {
        "filename": "4_identities.png",
        "badge": "ZERO-TRUST KEYS",
        "title": "HARDWARE KEYSTORE & CERTIFICATES",
        "subtitle": "StrongBox Dedicated HSM, FIDO2/WebAuthn & OpenSSH CA Certs",
        "source": "docs/qa/SSH-227-identity-badges.png",
    },
    {
        "filename": "5_port_forwarding.png",
        "badge": "SECURE TUNNELING",
        "title": "PORT FORWARDING & SOCKS5 PROXY",
        "subtitle": "Local, Remote & Dynamic SOCKS5 Tunnels with One-Touch Control",
        "source": "docs/qa/SSH-175-port-forward-dynamic-dialog.png",
    },
    {
        "filename": "6_security_settings.png",
        "badge": "MILITARY-GRADE PRIVACY",
        "title": "HARDENED SECURITY CONTROLS",
        "subtitle": "Biometric App Lock, FLAG_SECURE & Sensitive Memory Sanitization",
        "source": "docs/qa/SSH-264-settings-security-controls.png",
    },
    {
        "filename": "7_cloud_sync.png",
        "badge": "CLIENT-SIDE ENCRYPTION",
        "title": "ZERO-KNOWLEDGE CLOUD SYNC",
        "subtitle": "AES-256-GCM Google Drive Sync with 250,000 PBKDF2 Iterations",
        "source": "docs/qa/SSH-206-cloud-sync-controls.png",
    },
    {
        "filename": "8_multi_session.png",
        "badge": "CONCURRENT SESSIONS",
        "title": "SEAMLESS SESSION SWITCHING",
        "subtitle": "Persistent Background Services & Session Notification Controls",
        "source": "docs/qa/SSH-221-terminal-tabs.png",
    },
]

def generate_screenshots():
    out_dir_fastlane = "fastlane/metadata/android/en-US/images/phoneScreenshots"
    out_dir_qa = "docs/qa"
    os.makedirs(out_dir_fastlane, exist_ok=True)
    os.makedirs(out_dir_qa, exist_ok=True)

    font_badge = ImageFont.truetype(FONT_BOLD, 24)
    font_title = ImageFont.truetype(FONT_BOLD, 42)
    font_sub = ImageFont.truetype(FONT_REGULAR, 26)

    for item in SCENARIOS:
        canvas = Image.new("RGB", (CANVAS_WIDTH, CANVAS_HEIGHT), BG_COLOR)
        draw = ImageDraw.Draw(canvas)

        # Header typography
        margin_x = 64
        top_y = 90
        draw.text((margin_x, top_y), item["badge"].upper(), fill=BADGE_COLOR, font=font_badge)
        draw.text((margin_x, top_y + 42), item["title"], fill=TITLE_COLOR, font=font_title)
        draw.text((margin_x, top_y + 104), item["subtitle"], fill=SUBTITLE_COLOR, font=font_sub)

        # Subtle separator line
        draw.line([(margin_x, top_y + 160), (CANVAS_WIDTH - margin_x, top_y + 160)], fill=BORDER_COLOR, width=2)

        # Frame for inner screenshot
        src_path = item["source"]
        src = Image.open(src_path).convert("RGBA")

        avail_w = CANVAS_WIDTH - (margin_x * 2)
        avail_h = CANVAS_HEIGHT - (top_y + 190) - 60

        scale = min(avail_w / src.width, avail_h / src.height)
        scaled_w = int(src.width * scale)
        scaled_h = int(src.height * scale)
        resized = src.resize((scaled_w, scaled_h), Image.Resampling.LANCZOS)

        pos_x = (CANVAS_WIDTH - scaled_w) // 2
        pos_y = top_y + 190 + (avail_h - scaled_h) // 2

        # Rounded container border behind the screenshot
        padding = 8
        frame_box = (
            pos_x - padding,
            pos_y - padding,
            pos_x + scaled_w + padding,
            pos_y + scaled_h + padding,
        )
        draw.rounded_rectangle(frame_box, radius=16, fill=CARD_BG, outline=BORDER_COLOR, width=3)

        # Paste screenshot
        canvas.paste(resized, (pos_x, pos_y), resized)

        # Save to fastlane
        dest_fastlane = os.path.join(out_dir_fastlane, item["filename"])
        canvas.save(dest_fastlane, format="PNG", optimize=True)

        # Also save copy to docs/qa/ for QA Gate verification
        qa_filename = f"SSH-269-store-{item['filename']}"
        dest_qa = os.path.join(out_dir_qa, qa_filename)
        canvas.save(dest_qa, format="PNG", optimize=True)

        print(f"Generated {item['filename']} -> {dest_fastlane} & {dest_qa} (1080x1920)")

if __name__ == "__main__":
    generate_screenshots()
