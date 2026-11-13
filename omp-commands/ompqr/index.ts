/**
 * /ompqr — render a pairing QR for OmpChat 2.0.
 *
 * Uses the existing tools/omp_qr.py (port/token from bridge/config.json) and
 * prefers the Tailscale IPv4 address. Token text is never printed: the script
 * runs in terminal mode, and all captured output is redacted before display.
 */
import fs from "node:fs";
import path from "node:path";
import {
  findPython,
  listAddresses,
  openPath,
  pingBridge,
  projectRoot,
  qrOutputPath,
  qrScriptPath,
  readBridgeConfig,
  run,
  show,
  type RunResult,
} from "../ompchat-shared/ompchat.ts";

interface Ctx {
  hasUI?: boolean;
  ui: {
    notify(message: string, type?: "info" | "warning" | "error"): void;
    setWidget(key: string, content: string[] | undefined, options?: { placement?: "aboveEditor" | "belowEditor" }): void;
  };
}

function redact(text: string): string {
  return text.replace(/([?&](?:token|api_key|key)=)[^&\s"'<>]+/gi, "$1***");
}

function parseQr(output: string): string[] {
  const lines = output.split(/\r?\n/);
  const begin = lines.indexOf("OMPQR-BEGIN");
  const end = lines.indexOf("OMPQR-END");
  if (begin === -1 || end === -1 || end <= begin + 1) return [];
  return lines.slice(begin + 1, end).filter((line) => line.length > 0);
}

function between(output: string, prefix: string): string | null {
  const line = output.split(/\r?\n/).find((entry) => entry.startsWith(prefix));
  return line ? line.slice(prefix.length).trim() : null;
}

export default function ompQrCommand() {
  return {
    name: "ompqr",
    description: "Lag QR-kode for OmpChat-pairing og APK-nedlasting (Tailscale først, ellers lokal IP)",
    async execute(args: string[], ctx: Ctx): Promise<undefined> {
      const action = (args[0] ?? "").toLowerCase();
      const root = projectRoot();

      if (action === "clear" || action === "hide") {
        try {
          ctx.ui.setWidget("ompqr", undefined);
        } catch {
          // headless
        }
        ctx.ui.notify("QR-koden er skjult", "info");
        return;
      }

      // Ny: Åpne bildefilen i bildeviser
      if (action === "open") {
        const pngPath = qrOutputPath(root);
        const bmpPath = pngPath.replace(/\.png$/i, ".bmp");
        const found = [pngPath, bmpPath].find((candidate) => fs.existsSync(candidate));
        if (found) {
          openPath(found);
          ctx.ui.notify(`Åpnet QR-bilde: ${found}`, "info");
        } else {
          ctx.ui.notify("Fant ingen QR-bilde. Kjør /ompqr først.", "error");
        }
        return;
      }

      const config = readBridgeConfig(root);
      if (!(await pingBridge(config.port))) {
        show(
          ctx,
          "ompqr",
          [
            "OmpChat-paring — broen kjører ikke",
            `Ingen svar på port ${config.port}.`,
            "Kjør /ompserver først, og deretter /ompqr.",
          ],
          "Broen kjører ikke — kjør /ompserver først",
          "error",
        );
        return;
      }

      const addresses = listAddresses();
      const tailscale = addresses.tailscale[0]?.address;
      const lan = addresses.local[0]?.address;
      const host = tailscale ?? lan;
      if (!host) {
        ctx.ui.notify("Fant ingen Tailscale- eller lokal IPv4-adresse", "error");
        return;
      }

      const script = qrScriptPath(root);
      const python = findPython();
      const pngPath = qrOutputPath(root);
      try {
        fs.mkdirSync(path.dirname(pngPath), { recursive: true });
      } catch {
        // The renderers below report any real write error.
      }
      const startedAt = Date.now();

      // 1) Preferred: the Python renderer, which also prints a terminal QR.
      let result: RunResult | null = null;
      if (fs.existsSync(script) && python) {
        result = await run(
          python.command,
          [script, "--bridge", `${host}:${config.port}`, "--terminal", "--out", pngPath],
          60000,
        );
      }

      // 2) Fallback: the Node renderer shipped with the installer. It prints no
      //    terminal QR, but always produces a scannable image, so /ompqr works
      //    even when Python or qrcode/pillow is missing.
      let imagePath = pngPath;
      let usedImageFallback = false;
      if (!result || result.code !== 0 || parseQr(result.stdout).length === 0) {
        const qrgen = path.join(root, "tools", "qrgen.mjs");
        if (fs.existsSync(qrgen)) {
          const bmpPath = pngPath.replace(/\.png$/i, ".bmp");
          const link = `omp2://${host}:${config.port}?token=${config.token}`;
          const fallback = await run("node", [qrgen, link, bmpPath, "12"], 60000);
          if (fallback.code === 0 && fs.existsSync(bmpPath)) {
            imagePath = bmpPath;
            usedImageFallback = true;
            result = {
              code: 0,
              stdout: `OMPQR-BEGIN\nOMPQR-END\nPATH=${bmpPath}\nHOST=${host}\nPORT=${config.port}`,
              stderr: fallback.stderr,
            };
          }
        }
      }

      if (!result) {
        ctx.ui.notify("Fant verken tools/omp_qr.py eller tools/qrgen.mjs i installasjonen.", "error");
        return;
      }

      const output = redact(`${result.stdout}\n${result.stderr}`);
      const qr = parseQr(result.stdout);
      const reportedHost = between(output, "HOST=") ?? host;
      const reportedPort = between(output, "PORT=") ?? String(config.port);
      const freshImage = fs.existsSync(imagePath) && fs.statSync(imagePath).mtimeMs >= startedAt - 1000;

      if (result.code !== 0 || (qr.length === 0 && !usedImageFallback)) {
        if (freshImage) openPath(imagePath);
        const tail = output
          .split(/\r?\n/)
          .filter((line) => line.trim() && !line.startsWith("OMPQR-") && !line.startsWith("HOST=") && !line.startsWith("PORT=") && !line.startsWith("PATH="))
          .slice(-4);
        show(
          ctx,
          "ompqr",
          [
            "OmpChat-paring — QR feilet",
            ...(tail.length ? tail : ["Ukjent feil fra QR-verktøyet"]),
          ],
          "Kunne ikke lage QR-koden",
          "error",
        );
        return;
      }

      if (usedImageFallback) {
        openPath(imagePath);
        show(
          ctx,
          "ompqr",
          [
            `OmpChat-paring — ${reportedHost}:${reportedPort}`,
            tailscale ? "Skann med OmpChat (Tailscale)" : "Skann med OmpChat (samme Wi-Fi)",
            "",
            "QR-bildet er åpnet i bildeviser:",
            imagePath,
            "",
            "Kommandoer:",
            "  /ompqr open     – Åpne QR-bildet igjen",
            "  /ompqr clear    – Skjul denne boksen",
          ],
          `QR klar for ${reportedHost} — skann med OmpChat`,
        );
        return;
      }

      const lines = [
        `OmpChat-paring — ${reportedHost}:${reportedPort}`,
        tailscale ? "Skann med OmpChat 2.1+ (Tailscale)" : "Skann med OmpChat 2.1+ (må være på samme Wi-Fi)",
        "",
        ...qr,
        "",
        `Bilde: ${imagePath}`,
        "Kommandoer:",
        "  /ompqr open     – Åpne QR-bildet i bildeviser",
        "  /ompqr download – Last ned APK fra PC (lokal filserver)",
        "  /ompqr clear    – Skjul QR-widget",
      ];

      // Alltid åpne bildefilen i Windows' bildeviser slik at brukeren kan skanne den
      // også når terminalwidgeten på telefonen avkutter QR-koden.
      openPath(imagePath);

      if (ctx.hasUI === false) {
        // No terminal widget available: just notify.
        ctx.ui.notify(`QR lagret: ${imagePath}`, "info");
        return;
      }

      show(ctx, "ompqr", lines, `QR klar for ${reportedHost} — skann med OmpChat`);
      return;
    },
  };
}
