/**
 * OmpChat active-session reporter (optional OMP extension).
 *
 * Install manually into ~/.omp/agent/extensions/ (or a project's .omp/extensions/)
 * only if you want OMP Chat to know which session is open in the OMP TUI.
 *
 * The extension reads ~/.ompchat/bridge.json (written by omp-bridge) and
 * reports session_start / session_switch events to the bridge. It never reads
 * or logs the token beyond the local POST.
 */
import type { ExtensionAPI } from "@oh-my-pi/pi-coding-agent";
import fs from "node:fs";
import path from "node:path";

function bridgeInfo() {
  try {
    const file = path.join(process.env.HOME || process.env.USERPROFILE || "", ".ompchat", "bridge.json");
    const raw = fs.readFileSync(file, "utf8");
    const parsed = JSON.parse(raw);
    if (parsed && parsed.url && parsed.token) return parsed;
  } catch {}
  return null;
}

async function report(ctx: any) {
  const info = bridgeInfo();
  if (!info) return;
  const sessionFile = ctx?.sessionManager?.getSessionFile?.() || "";
  const cwd = ctx?.cwd || process.cwd();
  const sessionId = ctx?.sessionManager?.getSessionId?.() || "";
  const instanceId = `${process.pid}`;
  try {
    await fetch(`${info.url}/api/omp/active`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${info.token}`,
      },
      body: JSON.stringify({ instanceId, sessionId, sessionFile, cwd }),
    });
  } catch {}
}

export default function (pi: ExtensionAPI) {
  pi.on("session_start", async (_event, ctx) => {
    await report(ctx);
  });
  pi.on("session_switch", async (_event, ctx) => {
    await report(ctx);
  });
}