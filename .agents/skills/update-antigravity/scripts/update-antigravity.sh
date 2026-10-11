#!/usr/bin/env bash
#
# update-antigravity.sh
# Hardened update script for the Google Antigravity Suite on Linux.
#
# Supports:
#   1. Antigravity CLI (`agy`) via official bootstrapper https://antigravity.google/cli/install.sh
#   2. Antigravity Desktop / Hub via latest Linux tarball from https://antigravity.google/download
#   3. Optional: Antigravity IDE (`--ide`)
#

set -euo pipefail

# --- Color Output Helpers ---
if [ -t 1 ]; then
  BOLD="$(printf '\033[1m')"
  GREEN="$(printf '\033[32m')"
  YELLOW="$(printf '\033[33m')"
  BLUE="$(printf '\033[34m')"
  RED="$(printf '\033[31m')"
  RESET="$(printf '\033[0m')"
else
  BOLD=""
  GREEN=""
  YELLOW=""
  BLUE=""
  RED=""
  RESET=""
fi

log_info()  { echo "${BLUE}ℹ${RESET} ${BOLD}$*${RESET}"; }
log_ok()    { echo "${GREEN}✓${RESET} $*"; }
log_warn()  { echo "${YELLOW}⚠${RESET} ${BOLD}$*${RESET}" >&2; }
log_error() { echo "${RED}✗${RESET} ${BOLD}$*${RESET}" >&2; }

# --- Global Staging Cleanup Trap ---
CLEANUP_DIRS=()
cleanup_on_exit() {
  if [ "${#CLEANUP_DIRS[@]}" -gt 0 ]; then
    for d in "${CLEANUP_DIRS[@]}"; do
      if [ -n "$d" ] && [ -d "$d" ]; then
        rm -rf "$d" 2>/dev/null || true
      fi
    done
  fi
}
trap cleanup_on_exit EXIT

# --- Architecture Detection ---
detect_arch() {
  local raw_arch
  raw_arch="$(uname -m)"
  case "$raw_arch" in
    x86_64|amd64)
      ARCH_HUB="linux-x64"
      ARCH_IDE="linux-x64"
      ARCH_CLI="amd64"
      ;;
    aarch64|arm64)
      ARCH_HUB="linux-arm"
      ARCH_IDE="linux-arm"
      ARCH_CLI="arm64"
      ;;
    *)
      log_error "Unsupported architecture: $raw_arch. Only x86_64 and arm64 are supported."
      exit 1
      ;;
  esac
}

# --- Default Paths ---
if [ "$(id -u)" -eq 0 ]; then
  DEFAULT_INSTALL_DIR="/opt/antigravity"
  DEFAULT_IDE_DIR="/opt/antigravity-ide"
  DEFAULT_BIN_DIR="/usr/local/bin"
  DEFAULT_DESKTOP_DIR="/usr/share/applications"
  DEFAULT_ICON_DIR="/usr/share/icons/hicolor/180x180/apps"
else
  DEFAULT_INSTALL_DIR="${HOME}/.local/share/antigravity"
  DEFAULT_IDE_DIR="${HOME}/.local/share/antigravity-ide"
  DEFAULT_BIN_DIR="${HOME}/.local/bin"
  DEFAULT_DESKTOP_DIR="${HOME}/.local/share/applications"
  DEFAULT_ICON_DIR="${HOME}/.local/share/icons/hicolor/180x180/apps"
fi

INSTALL_DIR="$DEFAULT_INSTALL_DIR"
IDE_DIR="$DEFAULT_IDE_DIR"
BIN_DIR="$DEFAULT_BIN_DIR"
DESKTOP_DIR="$DEFAULT_DESKTOP_DIR"
ICON_DIR="$DEFAULT_ICON_DIR"

MODE_ALL=true
MODE_CLI=false
MODE_DESKTOP=false
MODE_IDE=false
CHECK_ONLY=false
FORCE=false

# --- Usage ---
show_usage() {
  cat <<EOF
${BOLD}Usage:${RESET} $(basename "$0") [options]

${BOLD}Description:${RESET}
  Hardened update utility for the Google Antigravity Suite on Linux.
  Automatically updates the CLI ('agy') and Desktop application from official Google channels.

${BOLD}Options:${RESET}
  -h, --help            Show this help message and exit
  -c, --check           Check current vs latest available versions without modifying system
  -f, --force           Force download and reinstallation even if versions match
  --cli-only            Update only the Antigravity CLI ('agy')
  --desktop-only        Update only the Antigravity Desktop app
  --ide                 Also update/install the Antigravity IDE (VS Code-based)
  --dir <path>          Custom installation directory for Antigravity Desktop (default: $DEFAULT_INSTALL_DIR)
  --bin-dir <path>      Custom directory for binary symlinks (default: $DEFAULT_BIN_DIR)

${BOLD}Examples:${RESET}
  $(basename "$0")              # Full update of CLI and Desktop
  $(basename "$0") --check      # Check for pending updates
  $(basename "$0") --cli-only   # Update only CLI
  $(basename "$0") --ide        # Include Antigravity IDE
EOF
}

# --- Argument Parsing ---
while [ "$#" -gt 0 ]; do
  case "$1" in
    -h|--help)
      show_usage
      exit 0
      ;;
    -c|--check)
      CHECK_ONLY=true
      shift
      ;;
    -f|--force)
      FORCE=true
      shift
      ;;
    --cli-only)
      MODE_ALL=false
      MODE_CLI=true
      shift
      ;;
    --desktop-only)
      MODE_ALL=false
      MODE_DESKTOP=true
      shift
      ;;
    --ide)
      MODE_IDE=true
      shift
      ;;
    --dir)
      if [ -z "${2:-}" ]; then
        log_error "Missing value for --dir"
        exit 1
      fi
      INSTALL_DIR="$2"
      shift 2
      ;;
    --bin-dir)
      if [ -z "${2:-}" ]; then
        log_error "Missing value for --bin-dir"
        exit 1
      fi
      BIN_DIR="$2"
      shift 2
      ;;
    *)
      log_error "Unknown option: $1"
      show_usage
      exit 1
      ;;
  esac
done

if [ "$MODE_ALL" = true ]; then
  MODE_CLI=true
  MODE_DESKTOP=true
fi

detect_arch

# --- Fetch Download Page Metadata ---
fetch_download_page() {
  local page
  page=$(curl -fsSL --compressed "https://antigravity.google/download" 2>/dev/null || true)
  if [ -z "$page" ]; then
    log_error "Failed to fetch download page from https://antigravity.google/download"
    return 1
  fi
  echo "$page"
}

get_hub_download_url() {
  local page="$1"
  local url
  # Target pattern: https://storage.googleapis.com/antigravity-public/antigravity-hub/.../linux-x64/Antigravity.tar.gz
  url=$(echo "$page" | grep -oE "https://storage\.googleapis\.com/antigravity-public/antigravity-hub/[^\"' ]+/${ARCH_HUB}/Antigravity\.tar\.gz" | head -n 1 || true)
  if [ -z "$url" ]; then
    # Fallback to checking hub auto-updater endpoint
    local stable_ver
    stable_ver=$(curl -fsSL "https://antigravity-hub-auto-updater-974169037036.us-central1.run.app/" 2>/dev/null | grep -oE "Stable Version: [0-9.]+" | awk '{print $NF}' || true)
    if [ -n "$stable_ver" ]; then
      url=$(echo "$page" | grep -oE "https://storage\.googleapis\.com/antigravity-public/antigravity-hub/${stable_ver}[^\"' ]*/${ARCH_HUB}/Antigravity\.tar\.gz" | head -n 1 || true)
    fi
  fi
  echo "$url"
}

get_ide_download_url() {
  local page="$1"
  echo "$page" | grep -oE "https://edgedl\.me\.gvt1\.com/edgedl/release2/[^\"' ]+/${ARCH_IDE}/Antigravity%20IDE\.tar\.gz" | head -n 1 || true
}

extract_version() {
  local url="$1"
  # Extracts e.g. 2.19.1-6046815158665216 from hub URL or 2.5.5-4923483625488384 from ide URL
  echo "$url" | sed -n 's#.*/\([0-9][0-9.]*-[0-9]*\)/.*#\1#p'
}

# --- CLI Update Routine ---
update_cli() {
  log_info "Processing Antigravity CLI ('agy')..."
  local current_cli="not installed"
  if command -v agy >/dev/null 2>&1; then
    current_cli="$(agy --version 2>/dev/null || echo "installed")"
  elif [ -x "$BIN_DIR/agy" ]; then
    current_cli="$("$BIN_DIR/agy" --version 2>/dev/null || echo "installed")"
  fi

  # Query latest CLI version from official updater manifest
  local cli_manifest_url="https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/linux_${ARCH_CLI}.json"
  local latest_cli
  latest_cli=$(curl -fsSL "$cli_manifest_url" 2>/dev/null | sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' || true)
  [ -z "$latest_cli" ] && latest_cli="unknown"

  log_info "Antigravity CLI: current=$current_cli | latest=$latest_cli"

  if [ "$CHECK_ONLY" = true ]; then
    return 0
  fi

  if [ "$FORCE" != true ] && [ "$current_cli" != "not installed" ] && [ "$current_cli" = "$latest_cli" ]; then
    log_ok "Antigravity CLI is already up to date ($current_cli)."
    return 0
  fi

  # Notice: The upstream install.sh script checks if "$TARGET_DIR/agy" exists and exits immediately
  # with 'Notice: agy is already installed'. To execute a clean update through install.sh,
  # we temporarily move the existing binary to a backup location.
  local agy_bin="$BIN_DIR/agy"
  local agy_backup="${agy_bin}.bak.$$"

  if [ -f "$agy_bin" ]; then
    mv "$agy_bin" "$agy_backup"
  fi

  log_info "Invoking official installer: curl -fsSL https://antigravity.google/cli/install.sh | bash"
  if curl -fsSL https://antigravity.google/cli/install.sh | bash; then
    rm -f "$agy_backup"
    local new_ver
    new_ver="$(agy --version 2>/dev/null || "$BIN_DIR/agy" --version 2>/dev/null || echo "installed")"
    log_ok "Antigravity CLI successfully updated to version $new_ver"
  else
    log_error "Antigravity CLI update failed!"
    if [ -f "$agy_backup" ]; then
      log_warn "Restoring previous CLI binary from backup..."
      mv "$agy_backup" "$agy_bin"
    fi
    return 1
  fi
}

# --- Desktop Application Update Routine ---
update_desktop() {
  local hub_url="$1"
  if [ -z "$hub_url" ]; then
    log_error "Could not resolve latest Antigravity Desktop download URL for $ARCH_HUB from https://antigravity.google/download"
    return 1
  fi

  local latest_hub_ver
  latest_hub_ver=$(extract_version "$hub_url")
  [ -z "$latest_hub_ver" ] && latest_hub_ver="latest"

  local current_hub_ver="not installed"
  if [ -f "$INSTALL_DIR/.version" ]; then
    current_hub_ver=$(grep '^VERSION=' "$INSTALL_DIR/.version" | cut -d= -f2- || echo "unknown")
  fi

  log_info "Antigravity Desktop: current=$current_hub_ver | latest=$latest_hub_ver"

  if [ "$CHECK_ONLY" = true ]; then
    return 0
  fi

  if [ "$FORCE" != true ] && [ "$current_hub_ver" != "not installed" ] && [ "$current_hub_ver" = "$latest_hub_ver" ] && [ -x "$INSTALL_DIR/antigravity" ]; then
    log_ok "Antigravity Desktop is already up to date ($current_hub_ver)."
    return 0
  fi

  log_info "Downloading Antigravity Desktop ($latest_hub_ver) from $hub_url..."
  local tmp_dir
  tmp_dir="$(mktemp -d -t antigravity-hub-XXXXXX)"
  CLEANUP_DIRS+=("$tmp_dir")

  local archive="$tmp_dir/Antigravity.tar.gz"
  if ! curl -fSL "$hub_url" -o "$archive"; then
    log_error "Failed to download $hub_url"
    return 1
  fi

  log_info "Verifying archive integrity..."
  if ! tar -tzf "$archive" >/dev/null 2>&1; then
    log_error "Downloaded archive is corrupted or invalid."
    return 1
  fi

  log_info "Extracting payload to staging area..."
  local staging="$tmp_dir/staging"
  mkdir -p "$staging"
  tar -xzf "$archive" -C "$staging" --strip-components=1

  if [ ! -f "$staging/antigravity" ]; then
    log_error "Extraction validation failed: 'antigravity' binary missing in archive!"
    return 1
  fi
  chmod +x "$staging/antigravity"

  log_info "Installing to $INSTALL_DIR..."
  mkdir -p "$(dirname "$INSTALL_DIR")"
  if [ -d "$INSTALL_DIR" ]; then
    local backup_dir="${INSTALL_DIR}.bak.$$"
    mv "$INSTALL_DIR" "$backup_dir"
    mv "$staging" "$INSTALL_DIR"
    rm -rf "$backup_dir"
  else
    mv "$staging" "$INSTALL_DIR"
  fi

  # Symlink to bin directory
  mkdir -p "$BIN_DIR"
  ln -sf "$INSTALL_DIR/antigravity" "$BIN_DIR/antigravity"
  log_ok "Binary available at: $BIN_DIR/antigravity"

  # Desktop launcher icon
  local icon_path="$INSTALL_DIR/antigravity.png"
  curl -fsSL "https://antigravity.google/apple-touch-icon.png" -o "$icon_path" 2>/dev/null || true
  if [ -d "$ICON_DIR" ] && [ -f "$icon_path" ]; then
    cp -f "$icon_path" "$ICON_DIR/antigravity.png" 2>/dev/null || true
  fi

  # Desktop Entry
  mkdir -p "$DESKTOP_DIR"
  cat <<EOF > "$DESKTOP_DIR/antigravity.desktop"
[Desktop Entry]
Name=Antigravity
Comment=Antigravity Desktop Application
Exec=$BIN_DIR/antigravity %U
Icon=$icon_path
Terminal=false
Type=Application
Categories=Development;IDE;
StartupWMClass=Antigravity
MimeType=x-scheme-handler/antigravity;
EOF
  chmod +x "$DESKTOP_DIR/antigravity.desktop"

  if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$DESKTOP_DIR" 2>/dev/null || true
  fi

  # Write version manifest
  cat <<EOF > "$INSTALL_DIR/.version"
VERSION=$latest_hub_ver
DOWNLOAD_URL=$hub_url
UPDATED_AT=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
EOF

  # Clean up staging directory immediately
  rm -rf "$tmp_dir" 2>/dev/null || true

  log_ok "Antigravity Desktop successfully installed ($latest_hub_ver)!"
}

# --- Antigravity IDE Update Routine (Optional) ---
update_ide() {
  local ide_url="$1"
  if [ -z "$ide_url" ]; then
    log_warn "Could not resolve Antigravity IDE download URL from https://antigravity.google/download"
    return 0
  fi

  local latest_ide_ver
  latest_ide_ver=$(extract_version "$ide_url")
  [ -z "$latest_ide_ver" ] && latest_ide_ver="latest"

  local current_ide_ver="not installed"
  if [ -f "$IDE_DIR/.version" ]; then
    current_ide_ver=$(grep '^VERSION=' "$IDE_DIR/.version" | cut -d= -f2- || echo "unknown")
  fi

  log_info "Antigravity IDE: current=$current_ide_ver | latest=$latest_ide_ver"

  if [ "$CHECK_ONLY" = true ]; then
    return 0
  fi

  if [ "$FORCE" != true ] && [ "$current_ide_ver" != "not installed" ] && [ "$current_ide_ver" = "$latest_ide_ver" ] && [ -x "$IDE_DIR/antigravity" ]; then
    log_ok "Antigravity IDE is already up to date ($current_ide_ver)."
    return 0
  fi

  log_info "Downloading Antigravity IDE ($latest_ide_ver) from $ide_url..."
  local tmp_dir
  tmp_dir="$(mktemp -d -t antigravity-ide-XXXXXX)"
  CLEANUP_DIRS+=("$tmp_dir")

  local archive="$tmp_dir/Antigravity-IDE.tar.gz"
  if ! curl -fSL "$ide_url" -o "$archive"; then
    log_error "Failed to download $ide_url"
    return 1
  fi

  log_info "Extracting IDE payload..."
  local staging="$tmp_dir/staging"
  mkdir -p "$staging"
  tar -xzf "$archive" -C "$staging" --strip-components=1

  mkdir -p "$(dirname "$IDE_DIR")"
  if [ -d "$IDE_DIR" ]; then
    local backup_dir="${IDE_DIR}.bak.$$"
    mv "$IDE_DIR" "$backup_dir"
    mv "$staging" "$IDE_DIR"
    rm -rf "$backup_dir"
  else
    mv "$staging" "$IDE_DIR"
  fi

  mkdir -p "$BIN_DIR"
  ln -sf "$IDE_DIR/antigravity" "$BIN_DIR/antigravity-ide"
  log_ok "Binary available at: $BIN_DIR/antigravity-ide"

  cat <<EOF > "$IDE_DIR/.version"
VERSION=$latest_ide_ver
DOWNLOAD_URL=$ide_url
UPDATED_AT=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
EOF

  rm -rf "$tmp_dir" 2>/dev/null || true
  log_ok "Antigravity IDE successfully installed ($latest_ide_ver)!"
}

# --- Main Execution ---
main() {
  log_info "Starting Antigravity update check (Architecture: $ARCH_HUB)..."

  local page
  page="$(fetch_download_page)"
  local hub_url
  hub_url="$(get_hub_download_url "$page")"
  local ide_url
  ide_url="$(get_ide_download_url "$page")"

  if [ "$MODE_CLI" = true ]; then
    update_cli
  fi

  if [ "$MODE_DESKTOP" = true ]; then
    update_desktop "$hub_url"
  fi

  if [ "$MODE_IDE" = true ]; then
    update_ide "$ide_url"
  fi

  if [ "$CHECK_ONLY" = true ]; then
    log_ok "Version check complete."
  else
    log_ok "All requested Antigravity updates completed successfully!"
  fi
}

main "$@"
