# OmpChat

**Mobile client for the Oh My Pi (OMP) AI agent on your PC.**

OmpChat does not run models itself and does not talk directly to Ollama. It talks to a small bridge on the PC that drives `omp --mode rpc` in your project folders and streams responses to the phone.

> **Note:** This project is a work in progress. It is already stable for daily use, but some features (like voice) are still being finalized. See the [Version History](#version-history) for details.

[![Version](https://img.shields.io/badge/version-2.9.56-blue)](https://github.com/megaxenon83-server/OmpChat/releases)
[![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-green)](https://github.com/megaxenon83-server/OmpChat/releases)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

---

## Features

### Chat
- Full conversation with OMP — tools, approvals, streaming responses
- Send text, images, and files as attachments
- Regenerate, edit, and resend messages
- Auto-generated conversation titles
- Project-based conversation filtering
- Favorites and archived sessions
- Markdown rendering with syntax highlighting

### Terminal
- xterm.js-based terminal via WebViewAssetLoader (no CDN, no HTTP server)
- Local assets only — works fully offline
- PTY integration with the PC shell
- Multiple terminal tabs per PC
- Keyboard handling (backspace, CTRL, ALT, etc.)

### Multi-PC
- Register and switch between multiple PCs
- Separate profiles and tokens per PC (stored in Android Keystore)
- LAN + Tailscale addresses per PC
- Automatic failover between LAN and Tailscale
- Wake-on-LAN support (local broadcast or Docker over SSH)
- PC status tracking (online/offline/starting)

### Connection
- QR code pairing (`/ompqr` in OMP)
- Automatic LAN → Tailscale failover
- Token stored securely in Android Keystore (AES/GCM)
- Connection verification (ping + health + auth)
- Automatic reconnection on app foreground
- Multiple endpoints learned from `/api/ping` hosts

### Voice (Work in Progress)
- Speech-to-text (STT) code present, not yet fully implemented
- Text-to-speech (TTS) code present, not yet fully implemented
- Voice conversation mode code present, pending final integration
- Voice settings UI is available but features are disabled until implementation is complete

### UI / UX
- Dark and light themes with dynamic color
- Adjustable font scale
- Norwegian and English language support
- Token usage indicator (inline, discreet)
- Activity cards for tool calls and agent actions
- Collapsible tool call cards
- Responsive layout (phone + tablet)

### PC Resource Monitor (New)
- Discreet CPU/RAM/GPU usage indicator in chat header
- Tap to see detailed breakdown with color coding
- Supports NVIDIA, AMD, and Intel GPUs
- Polls every 30 seconds (opt-in via settings)
- Color coding: green (< 60%), orange (60-85%), red (> 85%)
- File manager (browse, upload, download)
- Git integration (status, diff, commit)
- Task tracking and todo management
- Settings export/import
- Crash reporting

## Architecture

```
Phone (Compose)  →  HTTP + SSE  →  bridge/omp-bridge.mjs  →  NDJSON  →  omp --mode rpc
       ↕                                    ↕                              ↕
  Keystore (token)                    config.json                   project folder
```

- `bridge/omp-bridge.mjs` starts one OMP process per project folder (max 4, inactive ones are terminated)
- Exposes REST + SSE with bearer token
- The app owns no conversations — history, titles, and tool calls come from OMP
- Approvals from OMP are sent to the app as sheets with Approve / Approve writing for this session / Reject

## Getting Started

### 1. Install Bridge on the PC

**Windows:**
```powershell
# Download and run NimayOMP-Installer-3.2.0.exe
# Installs Bridge, OMP commands, firewall rule, and auto-start
```

**Linux:**
```bash
# Install Flatpak package
flatpak install net.nimay.ompbridge.flatpak
```

> **Note:** The Linux Flatpak has not been tested yet. It is provided as-is and may require adjustments.

### 2. Install the app on your phone

Download `OmpChat-2.9.46.apk` from [Releases](https://github.com/megaxenon83-server/OmpChat/releases) and install.

### 3. Connect

1. Kjør `/ompqr` i OMP for å generere QR-kode
2. Åpne OmpChat-appen → skann QR-koden
3. Appen kobler seg automatisk til Bridge

> **Autostart-tips:** Hvis Bridge ikke starter automatisk ved Windows-oppstart, kjør `/ompserver` i OMP først, og deretter `/ompqr` for å generere ny QR-kode.

## Building

### Android app

```bash
# Debug
./gradlew assembleDebug

# Release
./gradlew assembleRelease

# Tests
./gradlew testDebugUnitTest
```

**Requirements:** Android SDK 34, Kotlin 2.0, JDK 17

### Windows installer

```powershell
# Build with Inno Setup
iscc NimayOMP-Installer.iss
```

**Requirements:** Inno Setup 6.x, PowerShell 5.1+

## Project Structure

```
OmpChat/
├── app/                    # Android app (Kotlin/Compose)
│   ├── src/main/java/      # Source code
│   ├── src/main/assets/    # Terminal (xterm.js, local)
│   └── src/test/           # Unit tests
├── bridge/                 # Node.js bridge (runtime)
├── installer/              # Windows installer (Inno Setup)
├── omp-commands/           # OMP commands (/ompserver, /ompqr)
├── scripts/                # Linux service scripts
├── tools/                  # Helper tools
├── wol-service/            # Wake-on-LAN (Docker)
├── .github/                # GitHub Actions
└── docs/                   # Documentation
```

## Security

- Token stored in Android Keystore (AES/GCM) per PC profile
- Bridge requires bearer token for all endpoints
- `/api/ping` is public (for availability check)
- `/api/health` requires valid token
- No passwords or tokens in plaintext in app or config

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Cannot connect" | Check that Bridge is running (`/ompserver` in OMP) |
| "Token rejected" | Re-pair via QR code (`/ompqr` in OMP) |
| Port 7777 blocked | Check firewall (TCP 7777) |
| App crashes | Check logcat for stack trace |
| Bridge doesn't auto-start at boot | Run `/ompserver` in OMP first, then `/ompqr` |
| Multiple PCs not connecting | Delete old PC profiles in app, re-pair each PC |

## Known Limitations

- **Voice** — Code is present but not yet fully implemented. UI is available but features are disabled.
- **Linux Flatpak** — Not tested. Provided as-is, may require adjustments.
- **Terminal** — Works in emulator; physical device keyboard behavior may vary.
- **QR scanning** — Requires camera permission; may not work on all devices.
- **Tailscale** — Not testable in emulator; requires physical device on same Tailnet.
- **Multi-PC switching** — Tested in emulator only; physical device testing pending.

## Version History

See [CHANGELOG.md](CHANGELOG.md) for full version history.

## License

MIT License — see [LICENSE](LICENSE) for details.

## Contributing

1. Fork the repository
2. Create a branch (`git checkout -b feature/my-feature`)
3. Commit changes (`git commit -m "Add my feature"`)
4. Push (`git push origin feature/my-feature`)
5. Open a Pull Request

## Latest release
- Android: OmpChat 2.9.60 (versionCode 85)
- Windows installer: NimayOMP-Setup 3.2.2 (Bridge 2.9.12)
- Release: https://github.com/megaxenon83-server/OmpChat/releases/tag/v2.9.60

## Installation
Download the latest installer from GitHub Releases:
https://github.com/megaxenon83-server/OmpChat/releases

Windows: Nimay-OMP-Bridge-Setup.exe
Android: OmpChat-*.apk
