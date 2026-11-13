#!/usr/bin/env python3
"""Generate a QR code that connects the OmpChat 2.0 Android app to the OMP bridge.

The bridge (`bridge/omp-bridge.mjs`) is the thing the phone talks to; it in turn
drives the real `omp --mode rpc` process on this PC. This script only renders the
pairing payload that the app understands.

Usage examples:
    python omp_qr.py                       # auto-detect LAN IP, port 7777
    python omp_qr.py --token <token>       # explicit token (else read from bridge/config.json)
    python omp_qr.py --bridge 100.96.46.66:7777
    python omp_qr.py --port 7777 --out qr.png --no-ascii

Payload format (also valid as an Android deep link):
    omp2://<host>:<port>?token=<token>

The app also accepts `omp://` and `ompchat://` schemes and plain http(s) URLs.
"""

from __future__ import annotations

import argparse
import json
import socket
import sys
from pathlib import Path
from urllib.parse import quote

DEFAULT_PORT = 7777
SCHEME = "omp2"
BRIDGE_CONFIG = Path(__file__).resolve().parent.parent / "bridge" / "config.json"


def detect_lan_ip() -> str:
    """Best-effort local LAN IP (does not send any packets)."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        sock.close()


def read_bridge_config() -> dict:
    try:
        return json.loads(BRIDGE_CONFIG.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def build_payload(host: str, port: int, token: str | None) -> str:
    query = f"?token={quote(token, safe='')}" if token else ""
    return f"{SCHEME}://{host}:{port}{query}"


def print_qr_text(qr) -> None:
    """Print the QR matrix with plain ASCII so Windows consoles (cp1252) work."""
    matrix = qr.get_matrix()
    for row in matrix:
        line = "".join("##" if cell else "  " for cell in row)
        try:
            print(line)
        except UnicodeEncodeError:
            print(line.encode("ascii", "replace").decode("ascii"))


def compact_qr_lines(qr) -> list[str]:
    """Half-block QR: one character per module, two module rows per line.

    The result is about half as tall as the ASCII rendering and stays scannable
    in a normal terminal (each module is still roughly square).
    """
    matrix = qr.get_matrix()
    lines: list[str] = []
    for y in range(0, len(matrix), 2):
        top = matrix[y]
        bottom = matrix[y + 1] if y + 1 < len(matrix) else [False] * len(top)
        chars = []
        for upper, lower in zip(top, bottom):
            if upper and lower:
                chars.append("\u2588")  # full block
            elif upper:
                chars.append("\u2580")  # upper half block
            elif lower:
                chars.append("\u2584")  # lower half block
            else:
                chars.append(" ")
        lines.append("".join(chars))
    return lines



def main() -> int:
    config = read_bridge_config()

    parser = argparse.ArgumentParser(
        description="Create a QR code for pairing OmpChat 2.0 with the local OMP bridge."
    )
    parser.add_argument("--host", default=None, help="LAN/Tailscale IP the phone connects to (default: auto-detected)")
    parser.add_argument("--port", type=int, default=int(config.get("port", DEFAULT_PORT)), help=f"Bridge port (default: {DEFAULT_PORT})")
    parser.add_argument("--bridge", default=None, help="Shortcut for host:port, e.g. 100.96.46.66:7777")
    parser.add_argument("--token", default=None, help="Bridge bearer token (default: bridge/config.json)")
    parser.add_argument("--out", default="omp_chat_qr.png", help="Output PNG file (default: omp_chat_qr.png)")
    parser.add_argument("--no-ascii", action="store_true", help="Do not print the QR code in the terminal")
    parser.add_argument(
        "--terminal",
        action="store_true",
        help="Print a compact half-block QR only (no token/payload text), for OMP's /ompqr command",
    )
    args = parser.parse_args()

    if args.terminal:
        # OMP pipes stdout, so Python would otherwise use the Windows ANSI code
        # page and fail on the block characters.
        try:
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass


    host = args.host
    port = args.port
    if args.bridge:
        bridge_host, _, bridge_port = args.bridge.partition(":")
        host = bridge_host or host
        if bridge_port.isdigit():
            port = int(bridge_port)
    host = host or detect_lan_ip()

    token = args.token or config.get("token")
    if not token:
        print("No token found. Pass --token, or start the bridge once so it writes bridge/config.json.")
        return 2

    payload = build_payload(host, port, token)

    if not args.terminal:
        print("QR payload:")
        print(f"  {payload}")
        print()
        print("The app will call:")
        print(f"  http://{host}:{port}/api")
        print()

    if host in ("127.0.0.1", "localhost"):
        stream = sys.stderr if args.terminal else sys.stdout
        print("WARNING: 127.0.0.1 can only be reached from the PC itself.", file=stream)
        print("         Run with --host <LAN IP or Tailscale IP> so the phone can connect.", file=stream)
        if not args.terminal:
            print()

    try:
        import qrcode  # type: ignore
    except ImportError:
        print("Missing dependency. Install it with:", file=sys.stderr if args.terminal else sys.stdout)
        print("  python -m pip install qrcode pillow", file=sys.stderr if args.terminal else sys.stdout)
        return 1

    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_L, box_size=10, border=2)
    qr.add_data(payload)
    qr.make(fit=True)

    if args.terminal:
        print("OMPQR-BEGIN")
        for line in compact_qr_lines(qr):
            print(line)
        print("OMPQR-END")
    elif not args.no_ascii:
        print_qr_text(qr)
        print()

    image = qr.make_image(fill_color="black", back_color="white")
    out_path = Path(args.out).resolve()
    image.save(out_path)

    if args.terminal:
        # Never print the payload/token in terminal mode: only non-secret metadata.
        print(f"PATH={out_path}")
        print(f"HOST={host}")
        print(f"PORT={port}")
        return 0

    print(f"Saved QR image: {out_path}")

    print()
    print("Tips:")
    print("  - Phone and PC must be on the same network (Wi-Fi or Tailscale).")
    print("  - The bridge must be running:  node bridge/omp-bridge.mjs")
    print("  - Allow port 7777 through Windows Firewall if the phone cannot connect.")
    print("  - The token is embedded in the QR image; treat the image as a secret.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
