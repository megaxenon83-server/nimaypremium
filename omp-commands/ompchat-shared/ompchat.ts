/**
 * Shared helpers for the OmpChat global OMP slash commands (/ompserver, /ompqr).
 *
 * These modules are loaded by OMP's own custom-command loader from
 * `~/.omp/agent/commands/<name>/index.ts`, so they may only use Node builtins
 * (no imports from @oh-my-pi packages, no npm dependencies).
 */
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawn, spawnSync } from "node:child_process";

// Try to find the OmpChat installation in common locations.
// The installer writes the actual path to ~/.omp/agent/ompchat-commands.json,
// so this is only a fallback for edge cases (e.g. manual setup).
function findDefaultProjectRoot(): string {
  const candidates = [
    "C:\\Program Files\\Nimay OMP",
    "C:\\Program Files (x86)\\Nimay OMP",
    path.join(process.env.LOCALAPPDATA || "", "Nimay OMP"),
    path.join(process.env.PROGRAMFILES || "C:\\Program Files", "Nimay OMP"),
  ];
  for (const c of candidates) {
    if (fs.existsSync(path.join(c, BRIDGE_DIR_NAME, BRIDGE_SCRIPT))) return c;
  }
  return "C:\\Program Files\\Nimay OMP";
}
export const BRIDGE_DIR_NAME = "bridge";
export const BRIDGE_SCRIPT = "omp-bridge.mjs";
export const DEFAULT_PROJECT_ROOT = findDefaultProjectRoot();

export interface BridgeConfig {
  port: number;
  host: string;
  token: string;
  approvalMode: string;
  allowBash: boolean;
}

export interface Address {
  address: string;
  iface: string;
}

export interface BridgeStatus {
  up: boolean;
  port: number;
  pid: number | null;
  approvalMode: string;
  allowBash: boolean;
  local: Address[];
  tailscale: Address[];
  logPath: string;
}

export interface CommandIO {
  hasUI?: boolean;
  ui: {
    notify(message: string, type?: "info" | "warning" | "error"): void;
    setWidget(key: string, content: string[] | undefined, options?: { placement?: "aboveEditor" | "belowEditor" }): void;
    setStatus?(key: string, text: string | undefined): void;
  };
}

const configStateFile = () => path.join(os.homedir(), ".omp", "agent", "ompchat-commands.json");

/** Project root: OMPCHAT_HOME, then the installer-written pointer, then the default. */
export function projectRoot(): string {
  const fromEnv = process.env.OMPCHAT_HOME?.trim();
  if (fromEnv && isOmpChatProject(fromEnv)) return path.resolve(fromEnv);
  try {
    const raw = fs.readFileSync(configStateFile(), "utf8").replace(/^\uFEFF/, "");
    const parsed = JSON.parse(raw) as { projectRoot?: unknown };
    if (typeof parsed.projectRoot === "string" && isOmpChatProject(parsed.projectRoot)) {
      return path.resolve(parsed.projectRoot);
    }
  } catch {
    // fall through
  }
  return DEFAULT_PROJECT_ROOT;
}

export function isOmpChatProject(dir: string): boolean {
  try {
    return fs.existsSync(path.join(dir, BRIDGE_DIR_NAME, BRIDGE_SCRIPT));
  } catch {
    return false;
  }
}

export function bridgeDir(root = projectRoot()): string {
  return path.join(root, BRIDGE_DIR_NAME);
}

export function readBridgeConfig(root = projectRoot()): BridgeConfig {
  const file = path.join(bridgeDir(root), "config.json");
  let parsed: Record<string, unknown> = {};
  try {
    // Strip the UTF-8 BOM that Notepad/PowerShell may add.
    parsed = JSON.parse(fs.readFileSync(file, "utf8").replace(/^\uFEFF/, ""));
  } catch {
    parsed = {};
  }
  return {
    port: Number(process.env.OMPCHAT_BRIDGE_PORT || parsed.port || 7777),
    host: String(process.env.OMPCHAT_BRIDGE_HOST || parsed.host || "0.0.0.0"),
    token: String(process.env.OMPCHAT_BRIDGE_TOKEN || parsed.token || ""),
    approvalMode: String(parsed.approvalMode || "always-ask"),
    allowBash: parsed.allowBash !== false,
  };
}

/** True only when a real OmpChat bridge answers on the loopback port. */
export async function pingBridge(port: number): Promise<boolean> {
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 1500);
    const response = await fetch(`http://127.0.0.1:${port}/api/ping`, { signal: controller.signal });
    clearTimeout(timer);
    if (!response.ok) return false;
    const data = (await response.json()) as { name?: string };
    return data?.name === "omp-bridge";
  } catch {
    return false;
  }
}

export function listAddresses(): { local: Address[]; tailscale: Address[] } {
  const local: Address[] = [];
  const tailscale: Address[] = [];
  for (const [iface, entries] of Object.entries(os.networkInterfaces())) {
    for (const entry of entries ?? []) {
      const isV4 = entry.family === "IPv4" || (entry.family as unknown) === 4;
      if (!isV4 || entry.internal) continue;
      const address = entry.address;
      if (isTailscaleAddress(address, iface)) {
        tailscale.push({ address, iface });
      } else if (isPrivateLanAddress(address)) {
        local.push({ address, iface });
      }
    }
  }
  const rank = (item: Address): number => {
    const name = item.iface.toLowerCase();
    if (name.includes("wi-fi") || name.includes("wifi") || name.includes("ethernet")) return 0;
    if (name.includes("vethernet") || name.includes("virtual")) return 2;
    return 1;
  };
  local.sort((a, b) => rank(a) - rank(b));
  return { local, tailscale };
}

function isTailscaleAddress(address: string, iface: string): boolean {
  if (iface.toLowerCase().includes("tailscale")) return true;
  const parts = address.split(".").map(Number);
  // Tailscale CGNAT range 100.64.0.0/10
  return parts.length === 4 && parts[0] === 100 && parts[1] >= 64 && parts[1] <= 127;
}

function isPrivateLanAddress(address: string): boolean {
  const parts = address.split(".").map(Number);
  if (parts.length !== 4) return false;
  if (parts[0] === 10) return true;
  if (parts[0] === 192 && parts[1] === 168) return true;
  if (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) return true;
  return false;
}

export function findPidOnPort(port: number): number | null {
  if (process.platform !== "win32") return null;
  try {
    const result = spawnSync("netstat", ["-ano", "-p", "tcp"], {
      encoding: "utf8",
      windowsHide: true,
      timeout: 5000,
    });
    for (const line of String(result.stdout ?? "").split(/\r?\n/)) {
      if (!/LISTENING/i.test(line)) continue;
      const cols = line.trim().split(/\s+/);
      if (cols.length < 5) continue;
      const localAddress = cols[1] ?? "";
      if (localAddress.endsWith(`:${port}`)) {
        const pid = Number(cols[4]);
        return Number.isFinite(pid) ? pid : null;
      }
    }
  } catch {
    // best effort
  }
  return null;
}

export function tailFile(file: string, lines: number): string[] {
  try {
    const text = fs.readFileSync(file, "utf8").replace(/\u0000/g, "");
    return text.split(/\r?\n/).filter(Boolean).slice(-lines);
  } catch {
    return [];
  }
}

export function logPaths(): { out: string; err: string } {
  const dir = path.join(os.tmpdir(), "opencode");
  return { out: path.join(dir, "bridge.out.log"), err: path.join(dir, "bridge.err.log") };
}

export function bridgeStatus(port: number, up: boolean): BridgeStatus {
  const config = readBridgeConfig();
  const addresses = listAddresses();
  return {
    up,
    port,
    pid: up ? findPidOnPort(port) : null,
    approvalMode: config.approvalMode,
    allowBash: config.allowBash,
    local: addresses.local,
    tailscale: addresses.tailscale,
    logPath: logPaths().out,
  };
}

export function statusLines(status: BridgeStatus, extra: string[] = []): string[] {
  const lines: string[] = [];
  lines.push(`OmpChat-bro — ${status.up ? "kjører" : "stopper"} (port ${status.port})`);
  if (status.pid) lines.push(`PID ${status.pid} · godkjenning ${status.approvalMode} · terminal ${status.allowBash ? "på" : "av"}`);
  if (status.local.length) {
    lines.push(`Lokal:     http://${status.local[0].address}:${status.port}`);
  } else {
    lines.push("Lokal:     ingen privat IPv4 funnet");
  }
  if (status.tailscale.length) {
    lines.push(`Tailscale: http://${status.tailscale[0].address}:${status.port}`);
  }
  lines.push(`Logg:      ${status.logPath}`);
  for (const line of extra) lines.push(line);
  if (!status.up) lines.push("Start med /ompserver");
  return lines;
}

export function show(ctx: CommandIO, key: string, lines: string[], message?: string, type: "info" | "warning" | "error" = "info"): void {
  if (ctx.hasUI === false) return;
  try {
    ctx.ui.setWidget(key, lines, { placement: "aboveEditor" });
  } catch {
    // headless / unsupported host
  }
  if (message) {
    try {
      ctx.ui.notify(message, type);
    } catch {
      // ignore
    }
  }
}

export function nodeVersion(): string | null {
  try {
    const result = spawnSync("node", ["--version"], { encoding: "utf8", windowsHide: true, timeout: 5000 });
    if (result.status !== 0) return null;
    return String(result.stdout || "").trim() || null;
  } catch {
    return null;
  }
}

export function ompBinary(): string {
  const configured = process.env.OMP_BIN?.trim();
  if (configured) return configured;
  // Fall back to saved omp-bin.txt next to the bridge (written by installer).
  try {
    const binFile = path.join(bridgeDir(), "omp-bin.txt");
    if (fs.existsSync(binFile)) {
      const saved = fs.readFileSync(binFile, "utf8").trim();
      if (saved) return saved;
    }
  } catch { /* ignore */ }
  // Check the Bun shim first (may be a shim that can't find node_modules)
  const bunShim = path.join(os.homedir(), ".bun", "bin", "omp.exe");
  // Check if the actual OMP cli.js exists (Bun package)
  const cliJs = path.join(os.homedir(), ".bun", "install", "global", "node_modules", "@oh-my-pi", "pi-coding-agent", "dist", "cli.js");
  const bunExe = process.platform === "win32"
    ? "C:\\Users\\" + os.userInfo().username + "\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Oven-sh.Bun_Microsoft.Winget.Source_8wekyb3d8bbwe\\bun-windows-x64\\bun.exe"
    : "/usr/local/bin/bun";
  // If the shim exists and the cli.js doesn't, use the shim
  if (fs.existsSync(bunShim) && !fs.existsSync(cliJs)) return bunShim;
  // If cli.js exists and bun is available, use bun|cli.js format
  if (fs.existsSync(cliJs) && fs.existsSync(bunExe)) return bunExe + "|" + cliJs;
  // Fall back to common locations
  const candidates = [
    path.join(os.homedir(), ".bun", "bin", "omp.exe"),
    path.join(os.homedir(), "AppData", "Roaming", "npm", "omp.cmd"),
    path.join(os.homedir(), "AppData", "Roaming", "npm", "omp"),
    "/usr/local/bin/omp",
    "/usr/bin/omp",
  ];
  for (const c of candidates) {
    if (fs.existsSync(c)) return c;
  }
  return bunShim;
}

export function checkStartPrerequisites(root = projectRoot()): string[] {
  const problems: string[] = [];
  const dir = bridgeDir(root);
  if (!fs.existsSync(path.join(dir, BRIDGE_SCRIPT))) {
    problems.push(`Mangler ${path.join(dir, BRIDGE_SCRIPT)}`);
  }
  if (!fs.existsSync(path.join(dir, "config.json"))) {
    problems.push(`Mangler ${path.join(dir, "config.json")} (bridgen lager en ny med nytt token)`);
  }
  if (!nodeVersion()) {
    problems.push("Finner ikke Node.js i PATH. Installer Node.js 20+ og åpne terminalen på nytt.");
  }
  const omp = ompBinary();
  const ompPath = omp.includes("|") ? omp.split("|")[0] : omp;
  const ompEntry = omp.includes("|") ? omp.split("|")[1] : null;
  if (ompEntry) {
    if (!fs.existsSync(ompEntry)) {
      problems.push(`Finner ikke OMP på ${ompEntry}. Sett OMP_BIN hvis den ligger et annet sted.`);
    }
  } else if (!fs.existsSync(ompPath)) {
    problems.push(`Finner ikke OMP på ${ompPath}. Sett OMP_BIN hvis den ligger et annet sted.`);
  }
  return problems;
}

/** Start the bridge detached so it survives OMP/terminal exit. */
export function spawnBridge(root = projectRoot()): { pid: number | null; error: string | null } {
  const dir = bridgeDir(root);
  const logs = logPaths();
  fs.mkdirSync(path.dirname(logs.out), { recursive: true });
  // Start each run with clean logs: older bridge versions wrote pairing URLs
  // (and therefore the bearer token) to this file, and diagnostics read it.
  for (const file of [logs.out, logs.err]) {
    try {
      fs.writeFileSync(file, "");
    } catch {
      // another process may hold it; appending is still fine
    }
  }
  let outFd: number | null = null;
  let errFd: number | null = null;
  try {
    outFd = fs.openSync(logs.out, "a");
    errFd = fs.openSync(logs.err, "a");
    const omp = ompBinary();
    const child = spawn("node", [BRIDGE_SCRIPT], {
      cwd: dir,
      detached: true,
      windowsHide: true,
      stdio: ["ignore", outFd, errFd],
      env: { ...process.env, OMP_BIN: omp },
    });
    child.unref();
    return { pid: child.pid ?? null, error: null };
  } catch (error) {
    return { pid: null, error: error instanceof Error ? error.message : String(error) };
  } finally {
    if (outFd !== null) try { fs.closeSync(outFd); } catch { /* ignore */ }
    if (errFd !== null) try { fs.closeSync(errFd); } catch { /* ignore */ }
  }
}

export async function waitForBridge(port: number, timeoutMs = 12000): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await pingBridge(port)) return true;
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  return false;
}

export interface RunResult {
  code: number | null;
  stdout: string;
  stderr: string;
}

/** Async argv runner; keeps the OMP terminal responsive. */
export function run(command: string, args: string[], timeoutMs = 60000): Promise<RunResult> {
  return new Promise((resolve) => {
    let stdout = "";
    let stderr = "";
    let settled = false;
    let child;
    const finish = (code: number | null): void => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolve({ code, stdout, stderr });
    };
    const timer = setTimeout(() => {
      try {
        child?.kill();
      } catch {
        // ignore
      }
      finish(null);
    }, timeoutMs);
    try {
      child = spawn(command, args, { windowsHide: true, env: { ...process.env } });
    } catch (error) {
      stderr = error instanceof Error ? error.message : String(error);
      finish(null);
      return;
    }
    child.stdout?.setEncoding("utf8");
    child.stderr?.setEncoding("utf8");
    child.stdout?.on("data", (chunk) => { stdout += chunk; });
    child.stderr?.on("data", (chunk) => { stderr += chunk; });
    child.on("error", (error) => {
      stderr += error.message;
      finish(null);
    });
    child.on("close", (code) => finish(code));
  });
}

export function openPath(target: string): boolean {
  if (process.env.OMPCHAT_QR_NO_OPEN === "1") return true;
  try {
    const command = process.platform === "win32" ? "cmd" : process.platform === "darwin" ? "open" : "xdg-open";
    const args = process.platform === "win32" ? ["/c", "start", "", target] : [target];
    const child = spawn(command, args, { detached: true, stdio: "ignore", windowsHide: true });
    child.unref();
    return true;
  } catch {
    return false;
  }
}

export function qrScriptPath(root = projectRoot()): string {
  return path.join(root, "tools", "omp_qr.py");
}

/**
 * Where the generated QR image is written.
 *
 * A source checkout is writable, so the image stays next to it (historical
 * behaviour). An installation under `C:\Program Files` is read-only for normal
 * users (only bridge/voice/tools are writable), so the image goes to
 * `%LOCALAPPDATA%\OmpChat` instead of failing with EACCES.
 */
export function qrOutputPath(root = projectRoot()): string {
  // A write probe is the only reliable check on Windows: fs.accessSync(W_OK)
  // reports a Program Files directory as writable even when the ACL denies it.
  const probe = path.join(root, `.ompchat-write-${process.pid}-${Date.now()}.tmp`);
  try {
    fs.writeFileSync(probe, "");
    fs.unlinkSync(probe);
    return path.join(root, "omp_chat_qr.png");
  } catch {
    const base = process.env.LOCALAPPDATA?.trim() || path.join(os.homedir(), ".ompchat");
    return path.join(base, "OmpChat", "omp_chat_qr.png");
  }
}

export interface PythonInfo {
  command: string;
  version: string;
}

/** Find a usable Python interpreter (`python`, then `py -3`). */
export function findPython(): PythonInfo | null {
  const candidates: Array<{ command: string; args: string[] }> = [
    { command: "python", args: ["--version"] },
    { command: "py", args: ["-3", "--version"] },
  ];
  for (const candidate of candidates) {
    try {
      const result = spawnSync(candidate.command, candidate.args, { encoding: "utf8", windowsHide: true, timeout: 8000 });
      if (result.status === 0) {
        const version = `${result.stdout || ""}${result.stderr || ""}`.trim();
        return { command: candidate.command, version };
      }
    } catch {
      // try next
    }
  }
  return null;
}
