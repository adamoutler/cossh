---
name: update-antigravity
description: Updates the Google Antigravity suite on Linux, including the Antigravity CLI ('agy') and the Antigravity Desktop application. Use when the user requests to update, upgrade, check, or reinstall Antigravity or Antigravity CLI.
---

# Update Antigravity

This skill provides procedures and utilities to check, download, and update the Google Antigravity suite on Linux:
1. **Antigravity CLI (`agy`)**: Updated via the official bootstrapper script (`curl -fsSL https://antigravity.google/cli/install.sh | bash`).
2. **Antigravity Desktop**: Dynamically extracts and installs the latest Linux release package (`Antigravity.tar.gz`) resolved directly from [antigravity.google/download](https://antigravity.google/download).
3. **Antigravity IDE** *(Optional)*: Supports updating the VS Code-based Antigravity IDE via `--ide`.

The helper script is located at [update-antigravity.sh](./scripts/update-antigravity.sh) and is also linked to `~/.local/bin/update-antigravity` for immediate execution anywhere on the machine.

---

## Quick Reference

| Task | Command |
| :--- | :--- |
| **Full Update (CLI & Desktop)** | `update-antigravity` |
| **Check for Updates (Dry Run)** | `update-antigravity --check` |
| **Update CLI Only** | `update-antigravity --cli-only` |
| **Update Desktop Only** | `update-antigravity --desktop-only` |
| **Include Antigravity IDE** | `update-antigravity --ide` |
| **Force Reinstall / Re-download** | `update-antigravity --force` |

---

## Detailed Workflows

### 1. Check for Pending Updates
To inspect current installed versions versus latest available upstream releases without modifying any system files:

```bash
update-antigravity --check
```

**Expected Output:**
```text
ℹ Starting Antigravity update check (Architecture: linux-x64)...
ℹ Processing Antigravity CLI ('agy')...
ℹ Antigravity CLI: current=1.2.16 | latest=1.2.16
ℹ Antigravity Desktop: current=2.19.1-6046815158665216 | latest=2.19.1-6046815158665216
✓ Version check complete.
```

### 2. Standard Update (CLI & Desktop)
To perform a standard update:

```bash
update-antigravity
```

This will:
1. Query the CLI release manifest and compare versions. If an update is required, it safely backs up the existing binary, runs `curl -fsSL https://antigravity.google/cli/install.sh | bash`, and verifies the new version.
2. Query `https://antigravity.google/download` to resolve the latest `Antigravity.tar.gz` URL for the host architecture (`linux-x64` or `linux-arm`).
3. Download the tarball, verify archive integrity, extract to a staging area, validate the binary, and atomically install to `~/.local/share/antigravity`.
4. Ensure symlink `~/.local/bin/antigravity -> ~/.local/share/antigravity/antigravity` is active.
5. Install or refresh the desktop launcher at `~/.local/share/applications/antigravity.desktop` with an application icon.
6. Write the version manifest to `~/.local/share/antigravity/.version`.

### 3. Update CLI Only
If only the terminal CLI (`agy`) needs updating:

```bash
update-antigravity --cli-only
```
Or directly via the upstream bootstrap:
```bash
# Note: upstream install.sh exits early if agy is present; backup first if reinstalling:
mv "$HOME/.local/bin/agy" "$HOME/.local/bin/agy.bak" && \
  curl -fsSL https://antigravity.google/cli/install.sh | bash && \
  rm -f "$HOME/.local/bin/agy.bak"
```

### 4. Update Desktop Only
If only the GUI desktop client needs updating:

```bash
update-antigravity --desktop-only
```

### 5. Force Reinstallation
To force re-downloading and overwriting current installations regardless of version match:

```bash
update-antigravity --force
```

---

## Verification & Post-Install Validation

After running the update, verify the installed binaries:

1. **Verify Antigravity CLI:**
   ```bash
   agy --version
   ```

2. **Verify Antigravity Desktop Binary & Version:**
   ```bash
   which antigravity
   cat ~/.local/share/antigravity/.version
   ```

3. **Verify Desktop Launcher:**
   ```bash
   ls -la ~/.local/share/applications/antigravity.desktop
   ```
