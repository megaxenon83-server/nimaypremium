#!/usr/bin/env node
/**
 * OmpChat Bridge
 * --------------
 * Exposes the real Oh My Pi (OMP) RPC surface to the OmpChat Android app.
 *
 * OMP speaks newline-delimited JSON over stdin/stdout (`omp --mode rpc`).
 * This bridge spawns one OMP process per project folder, keeps it warm,
 * normalises its event stream and re-publishes it over HTTP + Server-Sent
 * Events, protected by a bearer token.
 *
 * No third-party dependencies: Node.js >= 20 only.
 *
 *   node omp-bridge.mjs
 *   node omp-bridge.mjs --port 7777 --host 0.0.0.0
 */
import http from "node:http";
import fs from "node:fs";
import fsp from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";
import { spawn, exec, execSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const VERSION = "2.9.26";
const HERE = path.dirname(fileURLToPath(import.meta.url));

// Resource monitoring state
let prevCpuTimes = null;

async function getResources() {
  // CPU
  const cpus = os.cpus();
  const cpuTimes = cpus.map((c) => c.times);
  const totalIdle = cpuTimes.reduce((sum, t) => sum + t.idle, 0);
  const totalTick = cpuTimes.reduce((sum, t) => sum + t.user + t.nice + t.sys + t.idle + t.irq, 0);

  let cpuPercent = 0;
  if (prevCpuTimes) {
    const idleDelta = totalIdle - prevCpuTimes.idle;
    const totalDelta = totalTick - prevCpuTimes.total;
    cpuPercent = totalDelta > 0 ? Math.round((1 - idleDelta / totalDelta) * 100) : 0;
  }
  prevCpuTimes = { idle: totalIdle, total: totalTick };

  // RAM
  const totalMem = os.totalmem();
  const freeMem = os.freemem();
  const usedMem = totalMem - freeMem;
  const ramPercent = Math.round((usedMem / totalMem) * 100);

  // GPU (via PowerShell performance counters — works for all vendors on Windows 10+)
  let gpuPercent = 0;
  let gpuName = "";
  let gpuMemoryTotal = 0;
  let gpuMemoryUsed = 0;

  try {
    const gpuUtil = execSync(
      'powershell -NoProfile -Command "(Get-Counter \'\\GPU Engine(*)\\Utilization Percentage\' -ErrorAction SilentlyContinue).CounterSamples | Measure-Object -Property CookedValue -Sum | Select-Object -ExpandProperty Sum"',
      { timeout: 3000, windowsHide: true }
    ).toString().trim();
    if (gpuUtil && !isNaN(parseFloat(gpuUtil))) {
      gpuPercent = Math.round(parseFloat(gpuUtil));
    }
  } catch (e) {
    // GPU counter not available
  }

  try {
    const gpuInfo = execSync(
      'powershell -NoProfile -Command "Get-CimInstance Win32_VideoController | Select-Object -First 1 Name, AdapterRAM | ConvertTo-Json"',
      { timeout: 3000, windowsHide: true }
    ).toString().trim();
    const parsed = JSON.parse(gpuInfo);
    gpuName = parsed?.Name || "";
    gpuMemoryTotal = parsed?.AdapterRAM || 0;
  } catch (e) {
    // GPU info not available
  }

  // Try nvidia-smi for more accurate GPU memory (NVIDIA only)
  try {
    const nvidiaOut = execSync(
      'nvidia-smi --query-gpu=memory.used,memory.total --format=csv,noheader,nounits',
      { timeout: 3000, windowsHide: true }
    ).toString().trim();
    const parts = nvidiaOut.split(",").map((s) => s.trim());
    if (parts.length >= 2) {
      gpuMemoryUsed = parseInt(parts[0]) * 1024 * 1024; // MB to bytes
      gpuMemoryTotal = parseInt(parts[1]) * 1024 * 1024;
    }
  } catch (e) {
    // Not NVIDIA or nvidia-smi not available
  }

  return {
    cpu: { percent: Math.max(0, Math.min(100, cpuPercent)), cores: cpus.length },
    ram: { percent: Math.max(0, Math.min(100, ramPercent)), usedBytes: usedMem, totalBytes: totalMem },
    gpu: { percent: Math.max(0, Math.min(100, gpuPercent)), name: gpuName, memoryUsedBytes: gpuMemoryUsed, memoryTotalBytes: gpuMemoryTotal },
    timestamp: Date.now(),
  };
}
const OMP_BIN = (() => {
  // 1. Respect OMP_BIN environment variable
  if (process.env.OMP_BIN) return process.env.OMP_BIN;
  // 1b. Fall back to saved omp-bin.txt next to the bridge
  try {
    const saved = fs.readFileSync(path.join(HERE, "omp-bin.txt"), "utf8").trim();
    if (saved) return saved;
  } catch { /* ignore */ }
  // 2. Bun-based OMP (run with bun) - preferred over the broken bun shim
  const bunCliJs = path.join(os.homedir(), ".bun", "install", "global", "node_modules", "@oh-my-pi", "pi-coding-agent", "dist", "cli.js");
  try {
    if (fs.existsSync(bunCliJs)) {
      const bunExe = process.platform === "win32"
        ? path.join(os.homedir(), "AppData", "Local", "Microsoft", "WinGet", "Packages", "Oven-sh.Bun_Microsoft.Winget.Source_8wekyb3d8bbwe", "bun-windows-x64", "bun.exe")
        : "/usr/local/bin/bun";
      if (fs.existsSync(bunExe)) return bunExe + "|" + bunCliJs; // special format: exe|entry
    }
  } catch { /* ignore */ }
  // 3. Search PATH for omp / omp.exe / bun
  const pathEnv = process.env.PATH || "";
  const pathDirs = pathEnv.split(path.delimiter).filter(Boolean);
  const ompNames = process.platform === "win32" ? ["omp.exe", "omp.cmd", "omp.bat", "omp"] : ["omp"];
  for (const dir of pathDirs) {
    for (const name of ompNames) {
      const candidate = path.join(dir, name);
      try {
        if (fs.existsSync(candidate)) return candidate;
      } catch { /* ignore */ }
    }
  }
  // 4. Common npm global install locations
  const npmGlobalCandidates = [
    path.join(os.homedir(), "AppData", "Roaming", "npm", "omp.exe"),
    path.join(os.homedir(), "AppData", "Roaming", "npm", "omp.cmd"),
    path.join(os.homedir(), "AppData", "Roaming", "npm", "omp"),
    path.join(os.homedir(), ".npm-global", "bin", "omp.exe"),
    path.join(os.homedir(), ".npm-global", "bin", "omp"),
    "/usr/local/bin/omp",
    "/usr/bin/omp",
  ];
  for (const candidate of npmGlobalCandidates) {
    try {
      if (fs.existsSync(candidate)) return candidate;
    } catch { /* ignore */ }
  }
  // 5. Bun shim location (may be a shim that can't find node_modules)
  const bunShim = path.join(os.homedir(), ".bun", "bin", "omp.exe");
  try {
    if (fs.existsSync(bunShim)) return bunShim;
  } catch { /* ignore */ }
  // 6. Last resort: let the OS resolve "omp" via system PATH instead of a
  // hardcoded, version-specific file that may not exist after an update.
  return "omp";
})();
// If OMP_BIN is "exe|entry", split it
const _ompBinParts = OMP_BIN.includes("|") ? OMP_BIN.split("|") : [OMP_BIN, null];
const OMP_BIN_EXEC = _ompBinParts[0];
const OMP_BIN_ENTRY = _ompBinParts[1];
const HOME = os.homedir();
let SESSIONS_ROOT = path.join(HOME, ".omp", "agent", "sessions");
let TRASH_ROOT = path.join(SESSIONS_ROOT, "_ompchat-trash");
let PROJECTS_FILE = path.join(HOME, ".omp", "agent", "omp-projects.json");

// ---------------------------------------------------------------------------
// Path helpers
//
// A stored path is only a working directory when it is absolute and exists.
// "C:" is a drive-relative stub, not a folder, and must never be shown or used
// as a project. Casing is preserved where OMP compares paths verbatim.
// ---------------------------------------------------------------------------

const IS_WINDOWS = process.platform === "win32";

function isAbsoluteDirPath(value) {
  if (typeof value !== "string") return false;
  const trimmed = value.trim();
  if (!trimmed) return false;
  if (/^[A-Za-z]:$/.test(trimmed)) return false;
  if (IS_WINDOWS) {
    return /^[A-Za-z]:[\\/]/.test(trimmed) || /^\\\\[^\\]+\\/.test(trimmed);
  }
  return trimmed.startsWith("/");
}

/** The path exactly as given (casing preserved) when it is an existing folder. */
function existingDir(value) {
  if (!isAbsoluteDirPath(value)) return "";
  const resolved = path.resolve(value.trim());
  try {
    if (!fs.statSync(resolved).isDirectory()) return "";
  } catch {
    return "";
  }
  return resolved;
}

/** Canonical spelling of an existing folder (real casing on Windows). */
function canonicalDir(value) {
  const dir = existingDir(value);
  if (!dir) return "";
  try {
    return fs.realpathSync.native(dir);
  } catch {
    return dir;
  }
}

/** Windows paths compare case-insensitively; other platforms stay exact. */
function samePath(a, b) {
  if (!a || !b) return false;
  const left = path.resolve(String(a).trim());
  const right = path.resolve(String(b).trim());
  return IS_WINDOWS ? left.toLowerCase() === right.toLowerCase() : left === right;
}

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------

function readConfig() {
  const file = path.join(HERE, "config.json");
  let cfg = {};
  try {
    // Notepad and PowerShell's `Set-Content -Encoding UTF8` prepend a BOM on
    // Windows, which JSON.parse rejects. Strip it instead of silently falling
    // back to defaults (that used to rotate the pairing token).
    const raw = fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
    cfg = JSON.parse(raw);
  } catch {
    cfg = {};
  }
  const merged = {
    port: Number(process.env.OMPCHAT_BRIDGE_PORT || cfg.port || 7777),
    host: process.env.OMPCHAT_BRIDGE_HOST || cfg.host || "0.0.0.0",
    token: process.env.OMPCHAT_BRIDGE_TOKEN || cfg.token || "",
    approvalMode: cfg.approvalMode || "always-ask",
    allowBash: cfg.allowBash !== false,
    projectRoots: Array.isArray(cfg.projectRoots) && cfg.projectRoots.length
      ? cfg.projectRoots
      : [
          path.join(HOME, "Documents"),
          path.join(HOME, "source"),
          path.join(HOME, "projects"),
        ],
    maxChildren: Number(cfg.maxChildren || 4),
    idleMinutes: Number(cfg.idleMinutes || 90),
    projectsFile: cfg.projectsFile || "",
    sessionDir: cfg.sessionDir || "",
    voice: {
      enabled: cfg.voice?.enabled !== false,
      profile: cfg.voice?.profile || "raske",
      engine: cfg.voice?.engine || "norsk-piper",
      voices: cfg.voice?.voices || {
        "norsk-piper": {
          name: "Norsk Piper",
          type: "piper",
          lang: "nb",
          installed: false,
          size: "64 MB",
          cpu: "lav",
          ram: "512 MB",
          vram: "0 MB",
          description: "Rask norsk tale på CPU",
        },
        "chatterbox-v3": {
          name: "Chatterbox Multilingual V3",
          type: "chatterbox",
          lang: "nb",
          installed: false,
          size: "1.5 GB",
          cpu: "høy",
          ram: "4 GB",
          vram: "2 GB",
          description: "Naturlig norsk tale",
        },
        "prat-9b-nf4": {
          name: "Prat-9B-NF4 (VibeVoice)",
          type: "vibevoice",
          lang: "nb",
          installed: false,
          size: "6.2 GB",
          cpu: "svært høy",
          ram: "8 GB",
          vram: "7 GB",
          description: "Norsk bokmål, krever sterkt GPU",
        },
        "original-vibevoice": {
          name: "Original Microsoft VibeVoice",
          type: "vibevoice",
          lang: "en",
          installed: false,
          size: "2.5 GB",
          cpu: "høy",
          ram: "6 GB",
          vram: "4 GB",
          description: "Primært engelsk/kinesisk",
        },
      },
      stt: {
        engine: cfg.voice?.stt?.engine || "nb-whisper-base",
        engines: {
          "nb-whisper-tiny": {
            name: "NB-Whisper Tiny",
            lang: "nb",
            installed: false,
            size: "39 MB",
          },
          "nb-whisper-base": {
            name: "NB-Whisper Base",
            lang: "nb",
            installed: false,
            size: "142 MB",
          },
          "nb-whisper-small": {
            name: "NB-Whisper Small",
            lang: "nb",
            installed: false,
            size: "466 MB",
          },
        },
      },
      settings: {
        autoplay: cfg.voice?.settings?.autoplay !== false,
        bargeIn: cfg.voice?.settings?.bargeIn !== false,
        micMute: cfg.voice?.settings?.micMute !== false,
        speaker: cfg.voice?.settings?.speaker || "høyttaler",
        speed: cfg.voice?.settings?.speed || 1.0,
      },
    },
  };
  if (!merged.token) {
    merged.token = crypto.randomBytes(24).toString("hex");
  }
  // Persist so the printed pairing QR survives restarts.
  try {
    fs.writeFileSync(file, JSON.stringify({ ...merged, token: merged.token }, null, 2), { mode: 0o600 });
  } catch (error) {
    // log() is not usable yet (logLines is declared further down), and a
    // read-only install directory must not abort the bridge: the token stays
    // in memory and pairing still works for this run.
    console.warn(`could not persist bridge config: ${error.message}`);
  }
  return merged;
}

const CONFIG = readConfig();
// Apply config overrides for session/project paths
if (CONFIG.sessionDir) SESSIONS_ROOT = path.resolve(CONFIG.sessionDir);
if (CONFIG.projectsFile) PROJECTS_FILE = path.resolve(CONFIG.projectsFile);
TRASH_ROOT = path.join(SESSIONS_ROOT, "_ompchat-trash");

const GOTIFY_FILE = path.join(HERE, "gotify.json");
let gotifyConfig = { enabled: false, server: "", token: "", events: {} };
try {
  const raw = fs.readFileSync(GOTIFY_FILE, "utf8");
  const parsed = JSON.parse(raw);
  gotifyConfig = { enabled: !!parsed.enabled, server: String(parsed.server || ""), token: String(parsed.token || ""), events: parsed.events || {} };
} catch {}
const gotifyDedup = new Set();

function saveGotifyConfig() {
  try { fs.writeFileSync(GOTIFY_FILE, JSON.stringify(gotifyConfig, null, 2), { mode: 0o600 }); } catch (e) { log("warn", `gotify config write failed: ${e.message}`); }
}

async function sendGotify(title, message, priority, dedupKey) {
  if (!gotifyConfig.enabled || !gotifyConfig.server || !gotifyConfig.token) return false;
  if (dedupKey) {
    if (gotifyDedup.has(dedupKey)) return false;
    gotifyDedup.add(dedupKey);
    if (gotifyDedup.size > 1000) {
      const it = gotifyDedup.values();
      for (let i = 0; i < 500; i++) it.next();
    }
  }
  try {
    const url = gotifyConfig.server.replace(/\/$/, "") + "/message";
    const body = JSON.stringify({ title: String(title || "OMP"), message: String(message || ""), priority: priority || 5 });
    const res = await fetch(url, { method: "POST", headers: { "X-Gotify-Key": gotifyConfig.token, "Content-Type": "application/json" }, body });
    return res.ok;
  } catch (e) {
    log("warn", `gotify send failed: ${e.message}`);
    return false;
  }
}

function gotifyEnabledFor(event) {
  if (!gotifyConfig.enabled) return false;
  const ev = gotifyConfig.events || {};
  if (Object.prototype.hasOwnProperty.call(ev, event)) return !!ev[event];
  return true;
}

function writeConfig() {
  try {
    const file = path.join(HERE, "config.json");
    const raw = JSON.stringify(CONFIG, null, 2);
    fs.writeFileSync(file, raw, { encoding: "utf8" });
    log("debug", "config.json oppdatert");
  } catch (error) {
    log("error", `Kunne ikke skrive config.json: ${error.message}`);
  }
}

// ---------------------------------------------------------------------------
// Logging
// ---------------------------------------------------------------------------

const LOG_LIMIT = 500;
const logLines = [];

/** Every event from every runtime, fanned out to the SSE subscribers. */
const globalSubscribers = new Set();

function globalBroadcast(event) {
  for (const subscriber of globalSubscribers) {
    try {
      subscriber(event);
    } catch {
      // dropped subscriber
    }
  }
}

function log(level, message) {
  const line = `${new Date().toISOString()} ${level.toUpperCase()} ${message}`;
  logLines.push(line);
  if (logLines.length > LOG_LIMIT) logLines.shift();
  if (process.env.OMPCHAT_BRIDGE_QUIET === "1" && level === "debug") return;
  // Skriver ikke til stdout for å unngå at bro-status forurenser OMP-terminalen.
  // Loggen er fortsatt tilgjengelig via /api/diagnostics.
}

function logFileTail(limit = 200) {
  return logLines.slice(-limit);
}

// ---------------------------------------------------------------------------
// Session metadata (bridge-owned title/archive state)
// ---------------------------------------------------------------------------

const META_FILE = path.join(HERE, "sessions-meta.json");
let meta = { sessions: {} };
try {
  meta = JSON.parse(fs.readFileSync(META_FILE, "utf8"));
  if (!meta.sessions) meta.sessions = {};
} catch {
  meta = { sessions: {} };
}

let metaWriteTimer = null;
function saveMeta() {
  if (metaWriteTimer) return;
  metaWriteTimer = setTimeout(async () => {
    metaWriteTimer = null;
    try {
      await fsp.writeFile(META_FILE, JSON.stringify(meta, null, 2));
    } catch (error) {
      log("warn", `sessions-meta write failed: ${error.message}`);
    }
  }, 250);
}

function metaFor(file) {
  const key = path.resolve(file);
  if (!meta.sessions[key]) meta.sessions[key] = {};
  return meta.sessions[key];
}

// ---------------------------------------------------------------------------
// Session files
// ---------------------------------------------------------------------------

const sessionCache = new Map();

function readJsonl(file) {
  const raw = fs.readFileSync(file, "utf8");
  const entries = [];
  for (const line of raw.split("\n")) {
    const text = line.trim();
    if (!text) continue;
    try {
      entries.push(JSON.parse(text));
    } catch {
      // A partially written trailing line while OMP appends; ignore.
    }
  }
  return entries;
}

function textOfContent(content) {
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) return "";
  const parts = [];
  for (const block of content) {
    if (!block || typeof block !== "object") continue;
    if (block.type === "text" && typeof block.text === "string") parts.push(block.text);
  }
  return parts.join("\n");
}

function firstUserText(entries) {
  for (const entry of entries) {
    if (entry.type !== "message") continue;
    const message = entry.message;
    if (!message || message.role !== "user" || message.synthetic) continue;
    const text = textOfContent(message.content).replace(/\s+/g, " ").trim();
    if (text) return text;
  }
  return "";
}

function describeSession(file, entries, stat) {
  const resolved = path.resolve(file);
  let title = "";
  let cwd = "";
  let sessionId = path.basename(file, ".jsonl").split("_").pop();
  let model = "";
  let messageCount = 0;
  let toolCount = 0;
  let lastUser = "";

  for (const entry of entries) {
    switch (entry.type) {
      case "title":
        if (typeof entry.title === "string" && entry.title.trim()) title = entry.title.trim();
        break;
      case "session":
        if (entry.cwd) cwd = entry.cwd;
        if (entry.id) sessionId = entry.id;
        break;
      case "model_change":
        if (typeof entry.model === "string") model = entry.model;
        break;
      case "message": {
        const message = entry.message;
        if (!message) break;
        if (message.role === "user" && !message.synthetic) {
          messageCount++;
          const text = textOfContent(message.content);
          if (text) lastUser = text;
        } else if (message.role === "assistant") {
          messageCount++;
          if (Array.isArray(message.content)) {
            toolCount += message.content.filter((block) => block && block.type === "toolCall").length;
          }
          if (message.model) model = `${message.provider ? message.provider + "/" : ""}${message.model}`;
        }
        break;
      }
      default:
        break;
    }
  }

  const custom = metaFor(resolved);
  if (custom.title) title = custom.title;
  if (!title) {
    title = lastUser.replace(/\s+/g, " ").trim().slice(0, 70) || "Ny samtale";
  }

    const customMeta = metaFor(resolved);
    const projectId = customMeta.projectId || "";

  return {
    id: sessionId,
    file: resolved,
    cwd,
    title,
    model,
    messageCount,
    toolCount,
    updatedAt: stat ? stat.mtimeMs : Date.now(),
    size: stat ? stat.size : 0,
    archived: !!customMeta.archived,
    projectId,
  };
}

function listSessionFiles(root = SESSIONS_ROOT, depth = 0) {
  const out = [];
  let entries;
  try {
    entries = fs.readdirSync(root, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const entry of entries) {
    if (entry.name.startsWith("_")) continue;
    const full = path.join(root, entry.name);
    if (entry.isDirectory()) {
      if (depth < 2) out.push(...listSessionFiles(full, depth + 1));
    } else if (entry.isFile() && entry.name.endsWith(".jsonl")) {
      out.push(full);
    }
  }
  return out;
}

function loadSession(file) {
  const resolved = path.resolve(file);
  const stat = fs.statSync(resolved, { throwIfNoEntry: false });
  if (!stat) return null;
  const cached = sessionCache.get(resolved);
  if (cached && cached.mtimeMs === stat.mtimeMs && cached.size === stat.size) return cached.session;
  let entries;
  try {
    entries = readJsonl(resolved);
  } catch (error) {
    log("warn", `cannot read session ${resolved}: ${error.message}`);
    return null;
  }
  const session = describeSession(resolved, entries, stat);
  sessionCache.set(resolved, { mtimeMs: stat.mtimeMs, size: stat.size, session, entries });
  return session;
}

/**
 * Rename a stored session without switching the live OMP runtime to it. The
 * title lives in the first jsonl line (`{"type":"title"}`); OMP's
 * `set_session_name` can only rename the session that is currently loaded, so
 * the bridge rewrites that line when the runtime is busy elsewhere.
 */
async function writeSessionTitle(file, name) {
  const resolved = path.resolve(file);
  const raw = fs.readFileSync(resolved, "utf8");
  const lines = raw.split("\n");
  let found = false;
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i].trim();
    if (!line) continue;
    let entry;
    try {
      entry = JSON.parse(line);
    } catch {
      continue;
    }
    if (entry && entry.type === "title") {
      entry.title = name;
      entry.source = "user";
      entry.updatedAt = new Date().toISOString();
      lines[i] = JSON.stringify(entry);
      found = true;
    }
    break;
  }
  if (!found) {
    lines.unshift(
      JSON.stringify({
        type: "title",
        v: 1,
        title: name,
        source: "user",
        updatedAt: new Date().toISOString(),
      })
    );
  }
  const temp = `${resolved}.rename.tmp`;
  fs.writeFileSync(temp, lines.join("\n"));
  // Retry rename with backoff (Windows file locking)
  let renamed = false;
  for (const delay of [0, 100, 200, 400]) {
    if (delay > 0) await new Promise(r => setTimeout(r, delay));
    try {
      fs.renameSync(temp, resolved);
      renamed = true;
      break;
    } catch (e) {
      if (delay === 400) {
        log("warn", `writeSessionTitle rename failed after retries: ${e.message}`);
      }
    }
  }
  if (!renamed) {
    try { fs.unlinkSync(temp); } catch {}
    throw new Error("Kunne ikke omdøpe sesjonsfilen (Windows-fillås)");
  }
  sessionCache.delete(resolved);
}

function entriesFor(file) {
  const resolved = path.resolve(file);
  const stat = fs.statSync(resolved, { throwIfNoEntry: false });
  if (!stat) return [];
  const cached = sessionCache.get(resolved);
  if (cached && cached.mtimeMs === stat.mtimeMs && cached.size === stat.size) return cached.entries;
  loadSession(resolved);
  return sessionCache.get(resolved)?.entries || [];
}

function listSessions({ cwd, includeArchived = false } = {}) {
  const files = listSessionFiles();
  const sessions = [];
  for (const file of files) {
    let session;
    try {
      session = loadSession(file);
    } catch {
      continue;
    }
    if (!session) continue;
    if (!includeArchived && session.archived) continue;
    if (cwd && !samePath(session.cwd, cwd)) continue;
    sessions.push(session);
  }
  sessions.sort((a, b) => b.updatedAt - a.updatedAt);
  return sessions;
}

/** Map a stored OMP transcript (jsonl entries) into app-friendly messages. */
function toAppMessages(entries) {
  const messages = [];
  let index = 0;
  for (const entry of entries) {
    if (entry.type !== "message") continue;
    const message = entry.message;
    if (!message || !message.role) continue;
    index++;

    if (message.role === "user") {
      if (message.synthetic) continue;
      const text = textOfContent(message.content);
      const images = Array.isArray(message.content)
        ? message.content.filter((block) => block && block.type === "image").length
        : 0;
      messages.push({
        id: entry.id || `u${index}`,
        parentId: entry.parentId || "",
        role: "user",
        text,
        images,
        timestamp: message.timestamp || Date.parse(entry.timestamp) || Date.now(),
        status: "done",
      });
      continue;
    }

    if (message.role === "assistant") {
      const content = Array.isArray(message.content) ? message.content : [];
      const text = content.filter((b) => b && b.type === "text").map((b) => b.text).join("");
      const thinking = content.filter((b) => b && b.type === "thinking").map((b) => b.thinking).join("\n\n");
      const toolCalls = content
        .filter((b) => b && b.type === "toolCall")
        .map((b) => ({
          id: b.id,
          name: b.name,
          args: b.arguments,
          state: "done",
        }));
      if (!text && !thinking && toolCalls.length === 0 && !message.errorMessage) continue;
      messages.push({
        id: entry.id || `a${index}`,
        parentId: entry.parentId || "",
        role: "assistant",
        text: text || message.errorMessage || "",
        thinking,
        toolCalls,
        model: message.model ? `${message.provider || ""}${message.provider ? "/" : ""}${message.model}` : "",
        timestamp: message.timestamp || Date.parse(entry.timestamp) || Date.now(),
        status: message.errorMessage ? "error" : "done",
        error: message.errorMessage || "",
        usage: message.usage || null,
      });
      continue;
    }

    if (message.role === "toolResult") {
      messages.push({
        id: entry.id || `t${index}`,
        role: "tool",
        toolCallId: message.toolCallId,
        toolName: message.toolName,
        text: textOfContent(message.content),
        isError: !!message.isError,
        timestamp: message.timestamp || Date.parse(entry.timestamp) || Date.now(),
        status: "done",
      });
      continue;
    }

    if (message.role === "bashExecution" || message.role === "pythonExecution") {
      messages.push({
        id: entry.id || `b${index}`,
        role: "terminal",
        text: [message.command, message.output].filter(Boolean).join("\n"),
        timestamp: message.timestamp || Date.parse(entry.timestamp) || Date.now(),
        status: "done",
      });
      continue;
    }

    if (message.role === "developer") continue;
    const text = textOfContent(message.content);
    if (text) {
      messages.push({
        id: entry.id || `s${index}`,
        role: "system",
        text,
        timestamp: message.timestamp || Date.parse(entry.timestamp) || Date.now(),
        status: "done",
      });
    }
  }
  return messages;
}

// ---------------------------------------------------------------------------
// OMP RPC child
// ---------------------------------------------------------------------------

let rpcSequence = 0;

class OmpRuntime {
  constructor(cwd) {
    this.cwd = path.resolve(cwd);
    this.child = null;
    this.buffer = "";
    this.pending = new Map();
    // Interactive requests (tool approvals, questions) that OMP is waiting on.
    // Kept so a reconnecting app can answer instead of leaving OMP blocked.
    this.pendingUi = new Map();
    this.subscribers = new Set();
    this.ready = false;
    this.sessionFile = "";
    this.sessionId = "";
    this.sessionTitle = "";
    this.lastUsed = Date.now();
    this.models = [];
    this.state = null;
    this.restarting = false;
    this.stderrTail = [];
    this.readyPromise = null;
    this.readyResolve = null;
  }

  async start() {
    if (this.child && this.ready) return;
    if (this.readyPromise) return this.readyPromise;
    this.readyPromise = new Promise((resolve, reject) => {
      this.readyResolve = resolve;
      this.readyReject = reject;
    });
    const args = OMP_BIN_ENTRY
      ? [OMP_BIN_ENTRY, "--mode", "rpc", "--cwd", this.cwd, "--session-dir", SESSIONS_ROOT]
      : ["--mode", "rpc", "--cwd", this.cwd, "--session-dir", SESSIONS_ROOT];
    // OMP-moduser: always-ask | always-ask-cached | write | yolo ("write" =
    // spør ved sletting/store endringer; "yolo" = godkjenn alt automatisk).
    if (CONFIG.approvalMode && CONFIG.approvalMode !== "default") {
      args.push("--approval-mode", CONFIG.approvalMode);
    }
    log("info", `spawning omp rpc for ${this.cwd} (approval=${CONFIG.approvalMode}) using ${OMP_BIN_EXEC}`);
    const child = spawn(OMP_BIN_EXEC, args, {
      cwd: this.cwd,
      env: { ...process.env, NO_COLOR: "1", FORCE_COLOR: "0" },
      windowsHide: true,
      stdio: ["pipe", "pipe", "pipe"],
    });
    this.child = child;

    child.stdout.setEncoding("utf8");
    child.stdout.on("data", (chunk) => this.onStdout(chunk));
    child.stderr.setEncoding("utf8");
    child.stderr.on("data", (chunk) => {
      const text = chunk.trim();
      if (!text) return;
      this.stderrTail.push(text);
      if (this.stderrTail.length > 40) this.stderrTail.shift();
      log("debug", `[${path.basename(this.cwd)}] stderr: ${text.slice(0, 400)}`);
    });
    child.on("exit", (code, signal) => {
      log("warn", `omp rpc exited for ${this.cwd} (code=${code} signal=${signal})`);
      this.ready = false;
      this.child = null;
      this.readyPromise = null;
      for (const [, entry] of this.pending) {
        entry.reject(new Error("OMP-prosessen avsluttet"));
      }
      this.pending.clear();
      this.pendingUi.clear();
      this.broadcast({ type: "backend", status: "offline", cwd: this.cwd, error: this.stderrTail.slice(-3).join("\n") });
      if (gotifyEnabledFor("omp_error")) sendGotify("OMP frakoblet", `OMP-prosessen stoppet på ${this.cwd}`, 7, `offline:${this.cwd}`).catch(() => {});
    });
    child.on("error", (error) => {
      log("error", `omp spawn failed: ${error.message}`);
      this.ready = false;
      this.child = null;
      this.readyPromise = null;
      this.broadcast({ type: "backend", status: "offline", cwd: this.cwd, error: error.message });
      if (gotifyEnabledFor("omp_error")) sendGotify("OMP frakoblet", `OMP-prosessen stoppet: ${error.message}`, 7, `offline:${this.cwd}:${error.message}`).catch(() => {});
      this.readyReject?.(error);
    });

    try {
      await Promise.race([
        this.readyPromise,
        new Promise((_, reject) => setTimeout(() => reject(new Error("OMP svarte ikke innen 60 sekunder")), 60000)),
      ]);
    } catch (error) {
      this.readyPromise = null;
      throw error;
    }
    return this;
  }

  onStdout(chunk) {
    this.buffer += chunk;
    let idx;
    while ((idx = this.buffer.indexOf("\n")) >= 0) {
      const line = this.buffer.slice(0, idx).trim();
      this.buffer = this.buffer.slice(idx + 1);
      if (!line) continue;
      let frame;
      try {
        frame = JSON.parse(line);
      } catch {
        continue;
      }
      try {
        this.onFrame(frame);
      } catch (error) {
        log("warn", `frame handler failed: ${error.message}`);
      }
    }
  }

  onFrame(frame) {
    this.lastUsed = Date.now();
    switch (frame.type) {
      case "ready": {
        this.ready = true;
        this.broadcast({ type: "ready", cwd: this.cwd, protocolVersion: frame.protocolVersion });
        this.readyResolve?.(this);
        // Route the interactive `ask` tool to the host (our app).
        this.rpc("set_ask_dialog", { enabled: true }).catch((error) =>
          log("debug", `set_ask_dialog failed: ${error.message}`),
        );
        this.rpc("get_available_models")
          .then((models) => {
            this.models = models?.models || [];
            this.broadcast({ type: "models", cwd: this.cwd, models: publicModels(this.models) });
          })
          .catch(() => {});
        this.refreshState().catch(() => {});
        return;
      }
      case "response": {
        const waiter = frame.id ? this.pending.get(frame.id) : undefined;
        if (waiter) {
          this.pending.delete(frame.id);
          clearTimeout(waiter.timer);
          if (frame.success) waiter.resolve(frame.data);
          else waiter.reject(new Error(frame.error || `RPC ${frame.command} feilet`));
        } else if (!frame.success) {
          log("warn", `uncorrelated rpc error: ${frame.command}: ${frame.error}`);
        }
        return;
      }
      case "extension_ui_request": {
        this.onUiRequest(frame);
        return;
      }
      case "available_commands_update":
        return;
      case "session_info_update": {
        if (frame.sessionId) this.sessionId = frame.sessionId;
        if (typeof frame.title === "string") this.sessionTitle = frame.title;
        // OMP announces the title it holds in memory, which is stale when the
        // user renamed the stored session while the runtime was idle. The
        // bridge owns user-set names, so keep them and push them back into the
        // runtime instead of letting the default title win.
        const stored = this.sessionFile ? metaFor(this.sessionFile).title : "";
        if (stored && stored !== this.sessionTitle) {
          this.sessionTitle = stored;
          this.rpc("set_session_name", { name: stored }).catch((error) =>
            log("debug", `title sync failed: ${error.message}`)
          );
        }
        this.broadcast({ type: "session_info", cwd: this.cwd, id: this.sessionId, title: this.sessionTitle });
        return;
      }
      case "config_update": {
        this.broadcast({ type: "config", cwd: this.cwd, model: frame.model ? publicModel(frame.model) : undefined });
        return;
      }
      case "rpc_frame_error": {
        this.broadcast({ type: "rpc_error", cwd: this.cwd, error: frame.error, originalType: frame.originalType });
        return;
      }
      default:
        break;
    }

    if (frame.type === "message_update") {
      const event = frame.assistantMessageEvent;
      if (!event) return;
      const map = {
        text_delta: "message_delta",
        thinking_delta: "message_delta",
        toolcall_start: "tool_call_start",
        toolcall_delta: "tool_call_delta",
        toolcall_end: "tool_call_end",
      };
      const type = map[event.type];
      if (!type) return;
      this.broadcast({
        type,
        cwd: this.cwd,
        messageId: frame.messageId,
        kind: event.type === "thinking_delta" ? "thinking" : "text",
        delta: event.delta,
        contentIndex: event.contentIndex,
        toolCall: event.toolCall,
        partial: event.partial ? summariseAssistant(event.partial) : undefined,
      });
      return;
    }

    if (frame.type === "message_start" || frame.type === "message_end") {
      this.broadcast({
        type: frame.type,
        cwd: this.cwd,
        messageId: frame.messageId,
        message: frame.type === "message_end" ? summariseMessage(frame.message, frame.messageId) : undefined,
        role: frame.message?.role,
      });
      if (frame.type === "message_start" && frame.message?.role === "assistant") {
        this.broadcast({ type: "streaming", cwd: this.cwd, active: true });
      }
      return;
    }

    if (frame.type === "tool_execution_start" || frame.type === "tool_execution_update" || frame.type === "tool_stream_update" || frame.type === "tool_execution_end") {
      const type = {
        tool_execution_start: "tool_start",
        tool_execution_update: "tool_update",
        tool_stream_update: "tool_stream",
        tool_execution_end: "tool_end",
      }[frame.type];
      this.broadcast({
        type,
        cwd: this.cwd,
        toolCallId: frame.toolCallId,
        toolName: frame.toolName,
        args: frame.args,
        intent: frame.intent,
        partial: frame.partialResult ?? frame.update,
        result: frame.result,
        isError: frame.isError,
      });
      return;
    }

    if (frame.type === "agent_start" || frame.type === "agent_end" || frame.type === "turn_start" || frame.type === "turn_end") {
      this.broadcast({ type: frame.type, cwd: this.cwd });
      if (frame.type === "agent_end") {
        this.pendingUi.clear();
        this.broadcast({ type: "streaming", cwd: this.cwd, active: false });
        this.refreshState().catch(() => {});
      }
      return;
    }

    if (frame.type === "prompt_result") {
      this.broadcast({
        type: "prompt_result",
        cwd: this.cwd,
        status: frame.status,
        error: frame.error,
        sessionSettled: frame.sessionSettled,
      });
      this.pendingUi.clear();
      this.broadcast({ type: "streaming", cwd: this.cwd, active: false });
      this.refreshState().catch(() => {});
      return;
    }

    if (frame.type === "session_settled") {
      this.pendingUi.clear();
      this.broadcast({ type: "session_settled", cwd: this.cwd });
      if (gotifyEnabledFor("task_done")) sendGotify("Oppgave fullført", `OMP fullførte en oppgave i ${this.cwd}`, 4, `settled:${this.cwd}:${Date.now()}`).catch(() => {});
      return;
    }

    if (frame.type === "notice") {
      this.broadcast({ type: "notice", cwd: this.cwd, notice: frame });
      return;
    }

    if (frame.type === "host_tool_call" || frame.type === "host_uri_request") {
      // We publish no host tools/URIs; answer so OMP never hangs.
      this.write({ type: frame.type === "host_tool_call" ? "host_tool_result" : "host_uri_result", id: frame.id, isError: true, error: "Host tools are not available in OmpChat" });
      return;
    }
  }

  onUiRequest(frame) {
    const method = frame.method;
    if (method === "cancel") {
      if (frame.targetId) this.pendingUi.delete(frame.targetId);
      this.broadcast({ type: "ui_cancel", cwd: this.cwd, targetId: frame.targetId });
      return;
    }
    if (method === "notify") {
      this.broadcast({ type: "notice", cwd: this.cwd, message: frame.message, level: frame.notifyType });
      return;
    }
    if (method === "setTitle") {
      if (frame.title) {
        this.sessionTitle = frame.title;
        this.broadcast({ type: "session_info", cwd: this.cwd, id: this.sessionId, title: frame.title });
      }
      return;
    }
    if (method === "setStatus" || method === "setWidget" || method === "set_editor_text" || method === "open_url") {
      this.broadcast({ type: "ui_passive", cwd: this.cwd, method, frame });
      return;
    }
    // Interactive: select | confirm | input | editor | ask
    const request = {
      type: "ui_request",
      cwd: this.cwd,
      id: frame.id,
      method,
      title: frame.title,
      message: frame.message,
      options: frame.options,
      optionDetails: frame.optionDetails,
      placeholder: frame.placeholder,
      prefill: frame.prefill,
      questions: frame.questions,
      timeout: frame.timeout,
      sessionFile: this.sessionFile,
    };
    this.pendingUi.set(frame.id, request);
    this.broadcast(request);
    if (gotifyEnabledFor("approval") && (method === "select" || method === "confirm")) {
      sendGotify("Godkjenning nødvendig", `${frame.title || "OMP"}: ${frame.message || ""}`.trim(), 7, `approval:${this.cwd}:${frame.id}`).catch(() => {});
    }
    if (gotifyEnabledFor("agent_waiting") && (method === "input" || method === "editor" || method === "ask")) {
      sendGotify("Agent venter på svar", `${frame.title || "OMP"} venter på input`, 5, `waiting:${this.cwd}:${frame.id}`).catch(() => {});
    }
  }

  write(frame) {
    if (!this.child || !this.child.stdin.writable) return false;
    this.child.stdin.write(JSON.stringify(frame) + "\n");
    return true;
  }

  rpc(type, params = {}, timeoutMs = 30000) {
    const id = `b${++rpcSequence}`;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`Tidsavbrudd: ${type}`));
      }, timeoutMs);
      this.pending.set(id, { resolve, reject, timer });
      const frame = { type, id, ...params };
      if (!this.write(frame)) {
        clearTimeout(timer);
        this.pending.delete(id);
        reject(new Error("OMP er ikke tilkoblet"));
      }
    });
  }

  async refreshState() {
    if (!this.ready) return null;
    const state = await this.rpc("get_state");
    if (!state) return null;
    this.state = state;
    if (state.sessionFile) this.sessionFile = state.sessionFile;
    if (state.sessionId) this.sessionId = state.sessionId;
    if (state.sessionName) this.sessionTitle = state.sessionName;
    this.broadcast({ type: "state", cwd: this.cwd, state: publicState(state) });
    return state;
  }

  broadcast(event) {
    const payload = { ...event, ts: Date.now() };
    for (const subscriber of this.subscribers) {
      try {
        subscriber(payload);
      } catch {
        // dropped subscriber
      }
    }
    globalBroadcast(payload);
  }

  subscribe(fn) {
    this.subscribers.add(fn);
    return () => this.subscribers.delete(fn);
  }

  async stop() {
    this.subscribers.clear();
    if (!this.child) return;
    try {
      this.child.stdin.end();
    } catch {}
    const child = this.child;
    this.child = null;
    this.ready = false;
    await new Promise((resolve) => {
      const timer = setTimeout(() => {
        try {
          child.kill("SIGKILL");
        } catch {}
        resolve();
      }, 2000);
      child.once("exit", () => {
        clearTimeout(timer);
        resolve();
      });
    });
  }
}

function publicModel(model) {
  if (!model) return null;
  return {
    id: model.id,
    name: model.name || model.id,
    provider: model.provider,
    api: model.api,
    reasoning: !!model.reasoning,
    input: model.input || ["text"],
    contextWindow: model.contextWindow ?? null,
    maxTokens: model.maxTokens ?? null,
    cost: model.cost || null,
    thinking: model.thinking || null,
  };
}

function publicModels(models) {
  return (models || []).map(publicModel).filter(Boolean);
}

function publicState(state) {
  if (!state) return null;
  return {
    sessionId: state.sessionId,
    sessionFile: state.sessionFile,
    sessionName: state.sessionName || "",
    model: publicModel(state.model),
    thinkingLevel: state.thinkingLevel,
    isStreaming: !!state.isStreaming,
    isCompacting: !!state.isCompacting,
    messageCount: state.messageCount || 0,
    queuedMessageCount: state.queuedMessageCount || 0,
    isSettled: !!state.isSettled,
    todoPhases: state.todoPhases || [],
    tokensPerSecond: state.tokensPerSecond ?? null,
    contextUsage: state.contextUsage || null,
    fastModeEnabled: !!state.fastModeEnabled,
    slowModeSupported: !!state.slowModeSupported,
    steeringMode: state.steeringMode || "one-at-a-time",
    followUpMode: state.followUpMode || "one-at-a-time",
  };
}

function summariseAssistant(message) {
  if (!message) return null;
  const content = Array.isArray(message.content) ? message.content : [];
  return {
    text: content.filter((b) => b && b.type === "text").map((b) => b.text).join(""),
    thinking: content.filter((b) => b && b.type === "thinking").map((b) => b.thinking).join("\n\n"),
    model: message.model ? `${message.provider || ""}${message.provider ? "/" : ""}${message.model}` : "",
    stopReason: message.stopReason,
    error: message.errorMessage || "",
  };
}

function summariseMessage(message, messageId) {
  if (!message) return null;
  const base = {
    id: messageId || `m${Date.now()}`,
    role: message.role,
    timestamp: message.timestamp || Date.now(),
  };
  if (message.role === "user") {
    return {
      ...base,
      text: textOfContent(message.content),
      images: Array.isArray(message.content) ? message.content.filter((b) => b && b.type === "image").length : 0,
      status: "done",
    };
  }
  if (message.role === "assistant") {
    const summary = summariseAssistant(message);
    return {
      ...base,
      ...summary,
      toolCalls: (Array.isArray(message.content) ? message.content : [])
        .filter((b) => b && b.type === "toolCall")
        .map((b) => ({ id: b.id, name: b.name, args: b.arguments, state: "queued" })),
      usage: message.usage || null,
      status: message.errorMessage ? "error" : "done",
    };
  }
  if (message.role === "toolResult") {
    return {
      ...base,
      toolCallId: message.toolCallId,
      toolName: message.toolName,
      text: textOfContent(message.content),
      isError: !!message.isError,
      status: "done",
    };
  }
  return { ...base, text: textOfContent(message.content), status: "done" };
}

// ---------------------------------------------------------------------------
// Runtime pool
// ---------------------------------------------------------------------------

const runtimes = new Map();

// Siste rapporterte aktive OMP-terminalsesjon (fra en valgfri extension).
// Dette er IKKE Bridge-runtimen; det er brukerens faktiske TUI-valg.
let ompActive = null;

function runtimeKey(cwd) {
  return path.resolve(cwd);
}

async function getRuntime(cwd, { create = true } = {}) {
  const key = runtimeKey(cwd);
  let runtime = runtimes.get(key);
  if (!runtime && !create) return null;
  if (!runtime) {
    const stat = fs.statSync(key, { throwIfNoEntry: false });
    if (!stat || !stat.isDirectory()) throw new Error(`Prosjektmappen finnes ikke: ${key}`);
    runtime = new OmpRuntime(key);
    runtimes.set(key, runtime);
  }
  await runtime.start();
  runtime.lastUsed = Date.now();
  await evictIdle();
  return runtime;
}

async function evictIdle() {
  const idleMs = CONFIG.idleMinutes * 60 * 1000;
  const alive = [...runtimes.values()];
  if (alive.length <= CONFIG.maxChildren) {
    for (const runtime of alive) {
      if (alive.length > 1 && Date.now() - runtime.lastUsed > idleMs && runtime.subscribers.size === 0 && !runtime.state?.isStreaming) {
        log("info", `evicting idle runtime ${runtime.cwd}`);
        runtimes.delete(runtimeKey(runtime.cwd));
        await runtime.stop();
      }
    }
    return;
  }
  alive.sort((a, b) => a.lastUsed - b.lastUsed);
  while (runtimes.size > CONFIG.maxChildren) {
    const victim = alive.shift();
    if (!victim) break;
    if (victim.state?.isStreaming) continue;
    runtimes.delete(runtimeKey(victim.cwd));
    await victim.stop();
  }
}

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Filbehandler-hjelpere (liste/overføring/sikkerhet)
// ---------------------------------------------------------------------------

const fsJobs = new Map();

function byteTask(from, to, onProgress, cancelled) {
  return new Promise((resolve, reject) => {
    let bytes = onProgress(0, 0);
    const read = fs.createReadStream(from, { highWaterMark: 1024 * 1024 });
    const write = fs.createWriteStream(to);
    read.on("data", (chunk) => {
      write.write(chunk);
      bytes += chunk.length;
      onProgress(bytes, from);
    });
    read.on("error", reject);
    write.on("error", reject);
    write.on("finish", resolve);
    if (cancelled) {
      cancelled(() => {
        try { read.destroy(); } catch {}
        try { write.destroy(); } catch {}
      });
    }
  });
}

async function copyRecursiveFs(from, to, progress, cancelled) {
  const stat = fs.statSync(from);
  if (!stat.isDirectory()) {
    return byteTask(from, to, (bytes) => progress(0, bytes), cancelled);
  }
  fs.mkdirSync(to, { recursive: true });
  const stack = [[from, to]];
  let total = 0, done = 0;
  const sizes = [];
  while (stack.length) {
    const [src, dst] = stack.pop();
    let entries = [];
    try { entries = fs.readdirSync(src, { withFileTypes: true }); } catch { continue; }
    fs.mkdirSync(dst, { recursive: true });
    for (const entry of entries) {
      const s = path.join(src, entry.name);
      const d = path.join(dst, entry.name);
      if (entry.isDirectory()) stack.push([s, d]);
      else {
        let sz = 0;
        try { sz = fs.statSync(s).size; } catch {}
        total += sz;
        sizes.push([s, d, sz]);
      }
    }
  }
  for (const [s, d, sz] of sizes) {
    fs.mkdirSync(path.dirname(d), { recursive: true });
    await byteTask(s, d, (bytes) => progress(total, done + bytes), cancelled).then(() => {
      done += sz;
      progress(total, done);
    });
  }
  progress(total, done);
}

function runFsJob(id) {
  const job = fsJobs.get(id);
  if (!job) return;
  setImmediate(() => {
    broadcastFsJob(job, "running");
    const cancelled = () => {
      job.cancelled = false;
      return false;
    };
    copyRecursiveFs(job.from, job.to, (total, done) => {
      job.total = total; job.done = done;
      broadcastFsJob(job, "running");
    }, () => {
      if (job.cancelled) {
        broadcastFsJob(job, "cancelled");
      }
    })
      .then(() => {
        job.finished = true; job.done = job.total || job.done;
        broadcastFsJob(job, "done");
        if (fs.existsSync(path.join(job.to, path.basename(job.from))) && job.op === "move") {
          fs.rmSync(job.from, { recursive: true, force: true });
        }
      })
      .catch((error) => {
        job.error = error.message;
        broadcastFsJob(job, "error");
      });
  });
}

function broadcastFsJob(job, stage) {
  globalBroadcast({
    type: "fs_job",
    id: job.id,
    stage,
    op: job.op,
    from: job.from,
    to: job.to,
    bytes: job.done,
    total: job.total,
    error: job.error || "",
    cwd: "",
    ts: Date.now(),
  });
}

function fsMime(p) {
  const ext = p.slice(p.lastIndexOf(".") + 1).toLowerCase();
  const types = {
    jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png", gif: "image/gif",
    webp: "image/webp", svg: "image/svg+xml", bmp: "image/bmp",
    pdf: "application/pdf", txt: "text/plain", md: "text/plain", json: "application/json",
    zip: "application/zip", mp3: "audio/mpeg", wav: "audio/wav", m4a: "audio/mp4",
    mp4: "video/mp4", mkv: "video/x-matroska", apk: "application/vnd.android.package-archive",
    exe: "application/x-msdownload",
  };
  return types[ext] || "application/octet-stream";
}

/** Systemstier som (nesten) aldri skal kunne slettes fra telefonen. */
function protectedFsPath(p) {
  const n = p.toLowerCase().replace(/\//g, "\\");
  const guards = process.platform === "win32"
    ? ["\\windows", "\\program files", "\\program files (x86)", "\\programdata", "\\appdata", "\\boot", "\\$recycle.bin", "\\users\\default"]
    : ["/etc", "/bin", "/sbin", "/usr", "/lib", "/boot", "/sys", "/proc", "/dev", "/var/log"];
  for (const guard of guards) {
    if (guard === "\\appdata") {
      // beskytt ikke brukerens AppData (brukes ofte), kun ikke-brukersystem-stier
      continue;
    }
    const hits_guard = [guard, guard + "\\"];
    if (hits_guard.some((hit) => n.startsWith(hit) && (n.length === hit.length || n[hit.length] === "\\"))) {
      if (guard === "\\windows" || guard === "\\program files" || guard === "\\program files (x86)" ||
          guard === "\\programdata" || guard === "\\boot" || guard === "\\$recycle.bin" || guard === "\\users\\default" ||
          guard === "/etc" || guard === "/bin" || guard === "/sbin" || guard === "/usr" ||
          guard === "/lib" || guard === "/boot" || guard === "/sys" || guard === "/proc" || guard === "/dev" ||
          guard === "/var/log") {
        return true;
      }
    }
  }
  return false;
}

function normalizedFs(p) {
  return String(p).replace(/\\/g, "/");
}

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(payload),
    "cache-control": "no-store",
  });
  res.end(payload);
}

function readBody(req, limitBytes = 64 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > limitBytes) {
        reject(new Error("Forespørselen er for stor"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      if (!chunks.length) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch (error) {
        reject(new Error(`Ugyldig JSON: ${error.message}`));
      }
    });
    req.on("error", reject);
  });
}

function bearer(req) {
  const header = req.headers.authorization || "";
  if (header.toLowerCase().startsWith("bearer ")) return header.slice(7).trim();
  const url = new URL(req.url, "http://localhost");
  return url.searchParams.get("token") || "";
}

function authorised(req) {
  const token = bearer(req);
  return token.length === CONFIG.token.length && crypto.timingSafeEqual(Buffer.from(token), Buffer.from(CONFIG.token));
}

const rate = new Map();
function rateLimited(req) {
  const key = req.socket.remoteAddress || "unknown";
  const now = Date.now();
  const entry = rate.get(key) || { count: 0, window: now };
  if (now - entry.window > 10000) {
    entry.count = 0;
    entry.window = now;
  }
  entry.count++;
  rate.set(key, entry);
  return entry.count > 400;
}

// ---------------------------------------------------------------------------
// HTTP routes
// ---------------------------------------------------------------------------

function networkInterfaces() {
  const out = [];
  const seen = new Set();
  for (const [name, addresses] of Object.entries(os.networkInterfaces())) {
    if (!addresses) continue;
    const lowerName = name.toLowerCase();
    // Ekskluder loopback, VPN, Tailscale og virtuelle adaptere
    if (lowerName.includes("loopback") || lowerName.includes("pseudo") ||
        lowerName.includes("tailscale") || lowerName.includes("virtual") ||
        lowerName.includes("vpn") || lowerName.includes("tap") ||
        lowerName.includes("tun") || lowerName.includes("hyper-v") ||
        lowerName.includes("vmware") || lowerName.includes("virtualbox") ||
        lowerName.includes("docker") || lowerName.includes("wsl")) {
      continue;
    }
    for (const addr of addresses) {
      if (!addr || addr.family !== "IPv4" || addr.internal) continue;
      const mac = (addr.mac || "").trim().toUpperCase();
      if (!mac || mac === "00:00:00:00:00:00") continue;
      // Ekskluder multicast MAC (første byte's siste bit = 1)
      const firstByte = parseInt(mac.split(":")[0], 16);
      if (isNaN(firstByte) || (firstByte & 1) !== 0) continue;
      // Normaliser MAC
      const normalizedMac = mac.replace(/-/g, ":");
      if (seen.has(normalizedMac)) continue;
      seen.add(normalizedMac);
      const isWifi = lowerName.includes("wi-fi") || lowerName.includes("wireless") ||
                     lowerName.includes("wlan") || lowerName.includes("wifi");
      const isEthernet = lowerName.includes("ethernet") || lowerName.includes("eth") ||
                         lowerName.includes("lan") || lowerName.includes("gbe") ||
                         lowerName.includes("realtek") || lowerName.includes("intel");
      out.push({
        name: name,
        mac: normalizedMac,
        ip: addr.address,
        type: isWifi ? "wifi" : (isEthernet ? "ethernet" : "other"),
        wolCapable: isEthernet && !isWifi,
      });
    }
  }
  // Prioriter Ethernet-adaptere først
  out.sort((a, b) => {
    if (a.wolCapable && !b.wolCapable) return -1;
    if (!a.wolCapable && b.wolCapable) return 1;
    return a.name.localeCompare(b.name);
  });
  return out;
}

function lanAddresses() {
  return networkInterfaces().map((i) => i.ip).filter(Boolean);
}

function projects() {
  // Only folders that actually contain OMP sessions belong in the overview.
  // Valid but empty folders are still selectable through /api/folders; they are
  // not projects until a conversation exists in them.
  const seen = new Map();
  for (const session of listSessions({ includeArchived: true })) {
    if (!session.cwd) continue;
    const canonical = canonicalDir(session.cwd);
    const fallback = isAbsoluteDirPath(session.cwd) ? path.resolve(session.cwd) : "";
    const display = canonical || fallback;
    if (!display) continue;
    const key = IS_WINDOWS ? display.toLowerCase() : display;
    const entry = seen.get(key) || {
      path: display,
      name: path.basename(display) || display,
      sessionCount: 0,
      lastActive: 0,
      exists: !!canonical,
    };
    entry.sessionCount++;
    entry.lastActive = Math.max(entry.lastActive, session.updatedAt);
    seen.set(key, entry);
  }
  return [...seen.values()].sort((a, b) => b.lastActive - a.lastActive || a.name.localeCompare(b.name));
}


function loadProjects() {
  try {
    const raw = fs.readFileSync(PROJECTS_FILE, "utf8");
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveProjects(list) {
  try {
    fs.mkdirSync(path.dirname(PROJECTS_FILE), { recursive: true });
    fs.writeFileSync(PROJECTS_FILE, JSON.stringify(list, null, 2));
  } catch (e) {
    log("error", `saveProjects failed: ${e.message}`);
  }
}

function registeredProjects() {
  const list = loadProjects();
  // Merge with auto-discovered projects from sessions
  const auto = projects();
  const result = [...list];
  const knownPaths = new Set(list.map((p) => (IS_WINDOWS ? p.path.toLowerCase() : p.path)));
  const BLOCKED_AUTO = ["C:\\Windows", "C:\\Program Files", "C:\\Program Files (x86)", "C:\\ProgramData"];
  function isBlockedAuto(p) {
    const norm = path.resolve(p).toLowerCase();
    return BLOCKED_AUTO.some(b => {
      const blocked = b.toLowerCase();
      return norm === blocked || norm.startsWith(blocked + path.sep.toLowerCase());
    });
  }
  for (const ap of auto) {
    if (isBlockedAuto(ap.path)) continue;
    const key = IS_WINDOWS ? ap.path.toLowerCase() : ap.path;
    if (!knownPaths.has(key)) {
      result.push({
        id: `auto-${Buffer.from(ap.path).toString("base64").slice(0, 12)}`,
        name: ap.name,
        path: ap.path,
        hidden: false,
        createdAt: ap.lastActive || Date.now(),
        auto: true,
        sessionCount: ap.sessionCount,
      });
    }
  }
  return result;
}

function serveEvents(req, res) {
  const url = new URL(req.url, "http://localhost");
  const cwdParam = url.searchParams.get("cwd");
  const cwd = cwdParam ? path.resolve(cwdParam) : null;
  res.writeHead(200, {
    "content-type": "text/event-stream; charset=utf-8",
    "cache-control": "no-cache, no-transform",
    connection: "keep-alive",
    "x-accel-buffering": "no",
  });
  res.write(`: connected ${new Date().toISOString()}\n\n`);

  const write = (event) => {
    try {
      res.write(`data: ${JSON.stringify(event)}\n\n`);
    } catch {}
  };

  // Attach to everything, filtered by project; runtimes may be created later.
  const subscriber = (event) => {
    if (cwd && event.cwd && path.resolve(event.cwd) !== cwd) return;
    write(event);
  };
  globalSubscribers.add(subscriber);

  const runtime = cwd ? runtimes.get(runtimeKey(cwd)) : null;
  write({
    type: "hello",
    cwd,
    sessionFile: runtime?.sessionFile || "",
    sessionId: runtime?.sessionId || "",
    state: runtime ? publicState(runtime.state) : null,
    ts: Date.now(),
  });

  // OMP blocks until a pending approval/question is answered. If the app was
  // closed or reconnected while one was open, hand it out again instead of
  // leaving the runtime stuck forever.
  if (runtime) {
    for (const pending of runtime.pendingUi.values()) write(pending);
  }

  const heartbeat = setInterval(() => {
    try {
      res.write(`: ping ${Date.now()}\n\n`);
    } catch {}
  }, 15000);

  const cleanup = () => {
    clearInterval(heartbeat);
    globalSubscribers.delete(subscriber);
  };
  req.on("close", cleanup);
  req.on("error", cleanup);
}

// ---------------------------------------------------------------------------
// Local voice engines (installed by the Nimay OMP Windows installer)
// ---------------------------------------------------------------------------

const VOICE_DIR = path.join(HERE, "..", "voice");
const VOICE_STATUS_FILE = path.join(VOICE_DIR, "voice-status.json");
const VOICE_CONFIG_FILE = path.join(VOICE_DIR, "voice-config.json");
const VOICE_RUNTIME = path.join(VOICE_DIR, "voice_runtime.py");
const VOICE_SETUP = path.join(VOICE_DIR, "setup-voice.ps1");
const VOICE_OUT = path.join(VOICE_DIR, "out");

/** Catalogue of engines the installer can provide. Sizes are downloads. */
const VOICE_CATALOG = {
  tts: {
    "piper-talesyntese": {
      name: "Norsk talesyntese (Piper medium)",
      type: "piper",
      lang: "nb",
      downloadMB: 63,
      hardware: "CPU",
      description: "Rask, lokal norsk stemme. Fungerer uten GPU.",
    },
    "piper-nvcc": {
      name: "Norsk NVCC (Piper medium)",
      type: "piper",
      lang: "nb",
      downloadMB: 77,
      hardware: "CPU",
      description: "Alternativ norsk stemme, lett aa kjore.",
    },
    chatterbox: {
      name: "Chatterbox Multilingual",
      type: "chatterbox",
      lang: "nb",
      downloadMB: 1500,
      hardware: "GPU anbefalt (2 GB VRAM)",
      description: "Mer naturlig tale, tyngre aa kjore.",
    },
    "vibevoice-0.5b": {
      name: "VibeVoice 0.5B Streaming (rask, loepende tale)",
      type: "vibevoice",
      lang: "nb",
      downloadMB: 1100,
      hardware: "NVIDIA GPU 6 GB VRAM",
      description: "Lav latens tale i sanntid, én fast stemme.",
    },
    "vibevoice-1.5b": {
      name: "VibeVoice 1.5B (naturlig tale, lange svar)",
      type: "vibevoice",
      lang: "nb",
      downloadMB: 4200,
      hardware: "NVIDIA GPU 8 GB VRAM",
      description: "Naturlig langformssamtaler, flerstemmer (kvinnepreset inkludert).",
    },
    "vibevoice-7b": {
      name: "VibeVoice 7B (avansert tale)",
      type: "vibevoice",
      lang: "nb",
      downloadMB: 16500,
      hardware: "NVIDIA GPU 20+ GB VRAM",
      description: "Størst og mest stabil langformssamtale.",
    },
    vibevoice: {
      name: "VibeVoice (eksperimentell, gammel)",
      type: "vibevoice",
      lang: "nb",
      downloadMB: 3500,
      hardware: "NVIDIA GPU 8 GB+",
      description: "Krever sterk GPU. Installeres bare der maskinvaren stotter det.",
    },
  },
  stt: {
    "whisper-tiny": {
      name: "Whisper Tiny",
      lang: "no",
      downloadMB: 75,
      hardware: "CPU",
      description: "Rask talegjenkjenning, lavere presisjon.",
    },
    "nb-whisper-small": {
      name: "NB-Whisper Small (norsk)",
      lang: "no",
      downloadMB: 480,
      hardware: "CPU",
      description: "Norsk-trent modell, god balanse.",
    },
    "nb-whisper-medium": {
      name: "NB-Whisper Medium (norsk)",
      lang: "no",
      downloadMB: 1600,
      hardware: "GPU anbefalt",
      description: "Hoyest presisjon, tyngre.",
    },
  },
};

function readJsonFile(file) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8").replace(/^\uFEFF/, ""));
  } catch {
    return null;
  }
}

function voicePython() {
  const candidate = path.join(VOICE_DIR, "venv", "Scripts", "python.exe");
  return fs.existsSync(candidate) ? candidate : null;
}

function voiceStatus() {
  return readJsonFile(VOICE_STATUS_FILE);
}

function voiceConfig() {
  return readJsonFile(VOICE_CONFIG_FILE);
}

function voiceJob() {
  return readJsonFile(path.join(VOICE_DIR, "install-job.json"));
}

function writeVoiceJob(job) {
  try {
    fs.mkdirSync(VOICE_DIR, { recursive: true });
    fs.writeFileSync(path.join(VOICE_DIR, "install-job.json"), JSON.stringify(job));
  } catch {}
}

/** Opens voice/install.log so a background install keeps its output. */
function openVoiceLog() {
  try {
    fs.mkdirSync(VOICE_DIR, { recursive: true });
    const logPath = path.join(VOICE_DIR, "install.log");
    const fd = fs.openSync(logPath, "a");
    fs.writeSync(fd, `\n===== ${new Date().toISOString()} ny installasjon =====\n`);
    return fd;
  } catch {
    return "ignore";
  }
}

/** Set while a voice engine install runs in this bridge process. */
let voiceInstall = null;

/** Resident voice-workerklient: holder voice_runtime.py alive mellom kall saa
 *  modeller (VibeVoice/Whisper) ikke lastes paa nytt per forespoersel. */
let voiceWorker = null;
let voiceWorkerQueue = Promise.resolve();

function spawnVoiceWorker() {
  const python = voicePython();
  if (!python || !fs.existsSync(VOICE_RUNTIME)) return null;
  log("debug", `voice-worker: starter (${python})`);
  const child = spawn(python, [VOICE_RUNTIME, "serve"], {
    cwd: VOICE_DIR,
    windowsHide: true,
    stdio: ["pipe", "pipe", "pipe"],
    // Windows-python default cp1252 ødelega ae/oe/aa i svarene; tving utf-8.
    env: { ...process.env, PYTHONIOENCODING: "utf-8", PYTHONUTF8: "1" },
  });
  child.reply = null;
  child.buf = "";
  child.stdout.setEncoding("utf8");
  child.stderr.setEncoding("utf8");
  child.stderr.on("data", (chunk) => {
    // tqdm/modellogg: hold bare de siste linjene for feilsøking
    child.lastErr = (child.lastErr || "").split("\n").concat(chunk).slice(-15).join("\n");
  });
  child.on("error", (error) => log("error", `voice-worker spawn-feil: ${error.message}`));
  child.on("close", (code) => {
    log("debug", `voice-worker avsluttet (kode ${code})`);
    const reply = child.reply;
    child.reply = null;
    if (voiceWorker === child) voiceWorker = null;
    clearTimeout(reply?.timer);
    reply?.resolve({ ok: false, error: "Tale-motoren avsluttet uventet." });
  });
  child.stdout.on("data", (chunk) => {
    child.buf += chunk;
    let idx;
    while ((idx = child.buf.indexOf("\n")) >= 0) {
      const line = child.buf.slice(0, idx).trim();
      child.buf = child.buf.slice(idx + 1);
      if (!line) continue;
      log("debug", `voice-worker -> ${line.slice(0, 140)}`);
      let parsed = null;
      try { parsed = JSON.parse(line); } catch { continue; }
      if (parsed.engine === "worker" || parsed.bye) continue;
      const reply = child.reply;
      if (reply) {
        child.reply = null;
        clearTimeout(reply.timer);
        if (parsed.ok) reply.resolve({ ok: true, data: parsed });
        else reply.resolve({ ok: false, error: parsed.error || "Tale-motoren feilet." });
      }
    }
  });
  return child;
}

function runVoiceWorker(request, timeoutMs = 480000) {
  // Ett kall om gangen. Svar comes EITHER via stdout JSON or .resp-fil
  // (noen python-launchere relayer pipen forst naaar prosessen avslutter,
  // slik at filen er den troverdige kanalen).
  const run = voiceWorkerQueue.catch(() => {}).then(() => new Promise((resolve) => {
    if (!voicePython()) return resolve({ ok: false, error: "Python-miljoet for tale er ikke installert." });
    if (!fs.existsSync(VOICE_RUNTIME)) return resolve({ ok: false, error: "voice_runtime.py mangler." });
    const respFile = (((request.out || request.wav) || "") + ".resp") || "";
    if (respFile && fs.existsSync(respFile)) { try { fs.rmSync(respFile, { force: true }); } catch {} }
    if (!voiceWorker) {
      voiceWorker = spawnVoiceWorker();
      if (!voiceWorker) return resolve({ ok: false, error: "Klarte ikke starte tale-motoren." });
    }
    const worker = voiceWorker;
    const reply = { };
    reply.promise = new Promise((r) => {
      reply.resolve = r;
    });
    let settled = false;
    const finish = (result) => {
      if (settled) return;
      settled = true;
      clearInterval(pollTimer);
      clearTimeout(reply.timer);
      worker.reply = null;
      if (respFile) { try { fs.rmSync(respFile, { force: true }); } catch {} }
      resolve(result);
    };
    reply.timer = setTimeout(() => {
      log("debug", `voice-worker timeout; lastErr=${(worker.lastErr || "").slice(-400).replace(/\n/g, " | ")}`);
      finish({ ok: false, error: "Tale-motoren svarte ikke innen tidsfristen." });
      try { worker.kill(); } catch {}
      if (voiceWorker === worker) voiceWorker = null;
    }, timeoutMs);
    worker.reply = { resolve: (result) => finish(result), timer: reply.timer };
    // Fil-poller som backup naar python-pipen ikke leverer.
    const pollTimer = setInterval(() => {
      try {
        if (!respFile || !fs.existsSync(respFile)) return;
        const text = fs.readFileSync(respFile, "utf8");
        let parsed = null;
        try { parsed = JSON.parse(text); } catch { return; }
        if (parsed?.ok) finish({ ok: true, data: parsed });
        else finish({ ok: false, error: parsed?.error || "Tale-motoren feilet." });
      } catch {}
    }, 300);
    log("debug", `voice-worker <- ${JSON.stringify(request).slice(0, 140)}`);
    worker.stdin.write(`${JSON.stringify({ ...request, resp: respFile || undefined })}\n`);
  }));
  voiceWorkerQueue = run.then(() => {}, () => {});
  return run;
}

/** Runs voice_runtime.py and returns { ok, data|error }. */
function runVoiceRuntime(args, timeoutMs = 180000) {
  return new Promise((resolve) => {
    const python = voicePython();
    if (!python) return resolve({ ok: false, error: "Python-miljoet for tale er ikke installert." });
    if (!fs.existsSync(VOICE_RUNTIME)) return resolve({ ok: false, error: "voice_runtime.py mangler." });
    const child = spawn(python, [VOICE_RUNTIME, ...args], { cwd: VOICE_DIR, windowsHide: true, env: { ...process.env, PYTHONIOENCODING: "utf-8", PYTHONUTF8: "1" } });
    let stdout = "";
    let stderr = "";
    const timer = setTimeout(() => {
      try { child.kill(); } catch {}
      resolve({ ok: false, error: "Tale-motoren svarte ikke innen tidsfristen." });
    }, timeoutMs);
    child.stdout.on("data", (chunk) => { stdout += chunk.toString(); });
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", (error) => {
      clearTimeout(timer);
      resolve({ ok: false, error: error.message });
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      const line = stdout.trim().split(/\r?\n/).filter(Boolean).pop() || "";
      let parsed = null;
      try { parsed = JSON.parse(line); } catch {}
      if (code === 0 && parsed?.ok) return resolve({ ok: true, data: parsed });
      resolve({ ok: false, error: parsed?.error || stderr.trim().split(/\r?\n/).slice(-3).join(" ") || `Tale-motoren feilet (kode ${code}).` });
    });
  });
}

function voiceEngineList() {
  const status = voiceStatus() || { tts: {}, stt: {} };
  const installedTts = status.tts || {};
  const installedStt = status.stt || {};
  const config = voiceConfig() || {};

  // Ekte stemmeliste per motor:
  // - piper-nvcc er en 10-stemmers norsk motor (5 kvinner K*, 5 menn M*).
  // - vibevoice-1.5b/7b preset-wav ligger i models/vibevoice/<id>/voices.
  const nvccSpeakerMap = {
    KNN: "Kvinne NN", KSV: "Kvinne SV", MMN: "Mann MN", KON: "Kvinne ON",
    MNN: "Mann MN2", MSV: "Mann SV", MON: "Mann ON", MNV: "Mann NV",
    KMN: "Kvinne MN", KNV: "Kvinne NV",
  };
  const vibeVoices = {
    "vibevoice-1.5b": [
      { id: "en-Alice_woman", name: "Alice (kvinnelig)" },
      { id: "en-Maya_woman", name: "Maya (kvinnelig)" },
      { id: "zh-Xinran_woman", name: "Xinran (kvinnelig)" },
      { id: "en-Frank_man", name: "Frank (mann)" },
      { id: "en-Carter_man", name: "Carter (mann)" },
    ],
    "vibevoice-7b": [
      { id: "en-Alice_woman", name: "Alice (kvinnelig)" },
      { id: "en-Maya_woman", name: "Maya (kvinnelige)" },
      { id: "zh-Xinran_woman", name: "Xinran (kvinnelige)" },
      { id: "en-Frank_man", name: "Frank (mann)" },
      { id: "en-Carter_man", name: "Carter (mann)" },
    ],
    "vibevoice-0.5b": [
      { id: "en-Emma_woman", name: "Emma (kvinnelig, realtime)" },
      { id: "en-Grace_woman", name: "Grace (kvinnelig, realtime)" },
      { id: "en-Carter_man", name: "Carter (mann, realtime)" },
      { id: "en-Davis_man", name: "Davis (mann, realtime)" },
      { id: "en-Frank_man", name: "Frank (mann, realtime)" },
      { id: "en-Mike_man", name: "Mike (mann, realtime)" },
      { id: "in-Samuel_man", name: "Samuel (mann, realtime)" },
    ],
  };
  const voicesFor = (id) => {
    if (id === "piper-nvcc") {
      return Object.entries(nvccSpeakerMap).map(([k, v]) => ({ id: k, name: v }));
    }
    if (vibeVoices[id]) {
      // Kun stemmer med fil på disk kan brukes akkurat nå.
      // 0.5B realtime bruker .pt-embeddings i voices/streaming_model/,
      // lavformmodellene bruker .wav i voices/.
      const isStreaming = id === "vibevoice-0.5b";
      const target = installedTts[id]?.dir
        ? path.join(installedTts[id].dir, "voices",
            isStreaming ? "streaming_model" : "")
        : null;
      const ext = isStreaming ? ".pt" : ".wav";
      return (vibeVoices[id] || []).filter((v) => {
        if (!target) return true;
        try { return fs.existsSync(path.join(target, v.id + ext)); } catch { return true; }
      });
    }
    return null;
  };

  const tts = {};
  for (const [id, info] of Object.entries(VOICE_CATALOG.tts)) {
    const installed = Boolean(installedTts[id]?.installed);
    tts[id] = {
      ...info,
      id,
      installed,
      verified: Boolean(installedTts[id]?.verified),
      bytes: installedTts[id]?.bytes,
      dir: installedTts[id]?.dir || null,
      voice: installedTts[id]?.voice || null,
      voices: voicesFor(id),
    };
  }
  const stt = {};
  for (const [id, info] of Object.entries(VOICE_CATALOG.stt)) {
    const installed = Boolean(installedStt[id]?.installed);
    stt[id] = { ...info, id, installed, verified: Boolean(installedStt[id]?.verified), bytes: installedStt[id]?.bytes };
  }
  return {
    engines: tts,
    stt: { engine: config.stt?.id || null, engines: stt },
    active: {
      engine: config.tts?.id || null,
      stt: config.stt?.id || null,
      voice: config.tts?.voice || null,
      profile: CONFIG.voice.profile,
      settings: CONFIG.voice.settings,
    },
    installed: Boolean(config.tts) || Boolean(config.stt),
    python: Boolean(voicePython()),
    job: voiceJob(),
    test: status.test || null,
    updated: status.updated || null,
  };
}

function apkFile() {
  const candidates = [
    path.join(HERE, "..", "apk", "OmpChat.apk"),
    path.join(HERE, "..", "OmpChat.apk"),
  ];
  for (const candidate of candidates) {
    try {
      if (fs.statSync(candidate).size > 1024 * 1024) return candidate;
    } catch {}
  }
  return null;
}

function serveApkLanding(res) {
  const file = apkFile();
  const size = file ? (fs.statSync(file).size / 1024 / 1024).toFixed(1) : null;
  const body = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Nimay OMP Mobile</title>
<style>body{background:#080F1E;color:#F1F7FF;font-family:system-ui,sans-serif;margin:0;padding:32px;display:flex;flex-direction:column;gap:18px;max-width:520px}
h1{font-size:22px;margin:0}p{color:#A2B4CF;line-height:1.5;margin:0}
a{display:block;text-align:center;background:#168BFF;color:#fff;text-decoration:none;padding:16px;border-radius:16px;font-weight:600}</style>
<h1>Nimay OMP Mobile</h1>
${file ? `<p>Last ned Android-appen (${size} MB) direkte fra denne PC-en.</p><a href="/apk/OmpChat.apk" download>Last ned OmpChat.apk</a>` : "<p>APK er ikke tilgjengelig i denne installasjonen.</p>"}`;
  res.writeHead(200, { "content-type": "text/html; charset=utf-8", "content-length": Buffer.byteLength(body) });
  res.end(body);
}

async function handle(req, res) {
  const url = new URL(req.url, "http://localhost");
  const route = `${req.method} ${url.pathname}`;

  // Public APK distribution (no token: the phone has none before pairing).
  if (req.method === "GET" && (url.pathname === "/apk" || url.pathname === "/apk/")) {
    return serveApkLanding(res);
  }
  if ((req.method === "GET" || req.method === "HEAD") && url.pathname === "/apk/OmpChat.apk") {
    const file = apkFile();
    if (!file) {
      res.writeHead(404, { "content-type": "text/plain; charset=utf-8" });
      return res.end("APK mangler.");
    }
    const stat = fs.statSync(file);
    const headers = {
      "content-type": "application/vnd.android.package-archive",
      "content-length": stat.size,
      "content-disposition": 'attachment; filename="OmpChat.apk"',
      "cache-control": "no-store",
    };
    res.writeHead(200, headers);
    if (req.method === "HEAD") return res.end();
    return fs.createReadStream(file).pipe(res);
  }

  if (route === "GET /api/ping") {
    return json(res, 200, {
      ok: true,
      name: "omp-bridge",
      version: VERSION,
      protocol: 2,
      authRequired: true,
      pairing: true,
      hosts: lanAddresses(),
      port: CONFIG.port,
    });
  }

  if (!authorised(req)) {
    return json(res, 401, { error: "Ugyldig eller manglende token" });
  }
  if (rateLimited(req)) {
    return json(res, 429, { error: "For mange forespørsler" });
  }

  try {
    const audioMatch = /^\/api\/voice\/audio\/([A-Za-z0-9._-]+)\.wav$/.exec(url.pathname);
    if (req.method === "GET" && audioMatch) {
      const file = path.join(VOICE_OUT, `${audioMatch[1]}.wav`);
      if (!file.startsWith(VOICE_OUT) || !fs.existsSync(file)) {
        return json(res, 404, { error: "Lydfilen finnes ikke" });
      }
      const stat = fs.statSync(file);
      res.writeHead(200, {
        "content-type": "audio/wav",
        "content-length": stat.size,
        "cache-control": "no-store",
      });
      return fs.createReadStream(file).pipe(res);
    }

    switch (route) {
      case "GET /api/health": {
        return json(res, 200, {
          ok: true,
          version: VERSION,
          omp: OMP_BIN_EXEC,
          approvalMode: CONFIG.approvalMode,
          allowBash: CONFIG.allowBash,
          runtimes: [...runtimes.values()].map((r) => ({
            cwd: r.cwd,
            ready: r.ready,
            sessionFile: r.sessionFile,
            sessionId: r.sessionId,
            streaming: !!r.state?.isStreaming,
            lastUsed: r.lastUsed,
          })),
        });
      }

      case "GET /api/resources": {
        try {
          const resources = await getResources();
          return json(res, 200, resources);
        } catch (e) {
          return json(res, 500, { error: "Kunne ikke hente ressurser", detail: String(e?.message || e) });
        }
      }

      case "GET /api/projects":
        return json(res, 200, { projects: projects() });

      case "GET /api/projects/registry": {
        return json(res, 200, { projects: registeredProjects() });
      }

      case "POST /api/projects/create": {
        const body = await readBody(req);
        const name = String(body.name || "").trim();
        if (!name) return json(res, 400, { error: "Mangler prosjektnavn" });
        // Support both full path and root+name
        let dir;
        if (body.path) {
          dir = path.resolve(body.path);
        } else {
          const root = body.root ? path.resolve(body.root) : path.join(HOME, "Documents", "OMP", "Prosjekter");
          dir = path.join(root, name);
        }
        // Safety: allow any absolute path outside critical system directories
        if (!path.isAbsolute(dir)) return json(res, 400, { error: "Stien ma vaere absolutt" });
        if (dir.includes("..") ) return json(res, 400, { error: "Ugyldig sti (path traversal)" });
        // Resolve symlinks/junctions to prevent bypassing system dir blocks
        try { if (fs.existsSync(dir)) dir = fs.realpathSync(dir); } catch {}
        const blocked = ["C:\\Windows", "C:\\Program Files", "C:\\Program Files (x86)", "C:\\ProgramData"];
        if (blocked.some(b => dir.toLowerCase().startsWith(b.toLowerCase()))) return json(res, 400, { error: "Kan ikke bruke systemmapper som prosjektmappe" });
        // Check if already a project on this PC
        const existing = loadProjects().find(p => p.path === dir);
        if (existing) return json(res, 409, { error: "Mappen er allerede et prosjekt", project: existing });
        // Create directory if it doesn't exist (use existing if it does)
        if (!fs.existsSync(dir)) {
          try { fs.mkdirSync(dir, { recursive: true }); } catch (e) {
            return json(res, 500, { error: `Kunne ikke opprette mappe: ${e.message}` });
          }
        }
        const list = loadProjects();
        const proj = { id: `proj-${Date.now()}`, name, path: dir, hidden: false, createdAt: Date.now(), auto: false };
        list.push(proj);
        saveProjects(list);
        return json(res, 200, { ok: true, project: proj });
      }

      case "POST /api/projects/rename": {
        const body = await readBody(req);
        const id = String(body.id || "");
        const name = String(body.name || "").trim();
        if (!id || !name) return json(res, 400, { error: "Mangler id eller name" });
        const list = loadProjects();
        const proj = list.find((p) => p.id === id);
        if (!proj) return json(res, 404, { error: "Fant ikke prosjektet" });
        proj.name = name;
        saveProjects(list);
        return json(res, 200, { ok: true, project: proj });
      }

      case "POST /api/projects/hide": {
        const body = await readBody(req);
        const id = String(body.id || "");
        if (!id) return json(res, 400, { error: "Mangler id" });
        const list = loadProjects();
        const proj = list.find((p) => p.id === id);
        if (proj) { proj.hidden = true; saveProjects(list); }
        return json(res, 200, { ok: true });
      }

      case "POST /api/projects/unhide": {
        const body = await readBody(req);
        const id = String(body.id || "");
        if (!id) return json(res, 400, { error: "Mangler id" });
        const list = loadProjects();
        const proj = list.find((p) => p.id === id);
        if (proj) { proj.hidden = false; saveProjects(list); }
        return json(res, 200, { ok: true });
      }

      case "DELETE /api/projects": {
        const url2 = new URL(req.url, "http://localhost");
        const id = url2.searchParams.get("id") || "";
        const physical = url2.searchParams.get("physical") === "1";
        if (!id) return json(res, 400, { error: "Mangler id" });
        const list = loadProjects();
        const idx = list.findIndex((p) => p.id === id);
        if (idx === -1) return json(res, 404, { error: "Fant ikke prosjektet" });
        const proj = list[idx];
        if (physical && proj.path && proj.path.startsWith(HOME)) {
          // Safety: never delete system dirs
          const forbidden = ["C:\\Windows", "C:\\System32", HOME];
          // Only allow if path is under Documents/OMP or similar project root
          if (!proj.path.includes("OMP") && !proj.path.includes("Prosjekter") && !proj.path.includes("projects")) {
            return json(res, 403, { error: "Fysisk sletting ikke tillatt for denne mappen" });
          }
          try { fs.rmSync(proj.path, { recursive: true, force: true }); } catch (e) {
            return json(res, 500, { error: `Kunne ikke slette mappe: ${e.message}` });
          }
        }
        list.splice(idx, 1);
        saveProjects(list);
        return json(res, 200, { ok: true });
      }

      case "POST /api/projects/move-session": {
        const body = await readBody(req);
        const sessionFile = body.file ? path.resolve(body.file) : "";
        const projectId = String(body.projectId || "");
        if (!sessionFile || !projectId) return json(res, 400, { error: "Mangler file eller projectId" });
        if (!fs.existsSync(sessionFile)) return json(res, 404, { error: "Fant ikke samtalen" });
        const list = loadProjects();
        const proj = list.find((p) => p.id === projectId);
        if (!proj) return json(res, 404, { error: "Fant ikke prosjektet" });
        try {
          const raw = fs.readFileSync(sessionFile, "utf8");
          const lines = raw.split(/\r?\n/);
          const updated = lines.map((line) => {
            try {
              const obj = JSON.parse(line);
              if (obj.type === "session" && obj.cwd) {
                obj.cwd = proj.path;
                return JSON.stringify(obj);
              }
            } catch {}
            return line;
          });
          fs.writeFileSync(sessionFile, updated.join("\n"));
          const metaObj = metaFor(sessionFile);
          metaObj.projectId = projectId;
          saveMeta();
          return json(res, 200, { ok: true, projectId, path: proj.path });
        } catch (e) {
          return json(res, 500, { error: "Kunne ikke flytte samtale: " + e.message });
        }
      }

      case "POST /api/projects/move-session": {
        const body = await readBody(req);
        const sessionFile = body.file ? path.resolve(body.file) : "";
        const projectId = String(body.projectId || "");
        if (!sessionFile || !projectId) return json(res, 400, { error: "Mangler file eller projectId" });
        if (!fs.existsSync(sessionFile)) return json(res, 404, { error: "Fant ikke samtalen" });
        const list = loadProjects();
        const proj = list.find((p) => p.id === projectId);
        if (!proj) return json(res, 404, { error: "Fant ikke prosjektet" });
        try {
          const raw = fs.readFileSync(sessionFile, "utf8");
          const lines = raw.split(/\r?\n/);
          const updated = lines.map((line) => {
            try {
              const obj = JSON.parse(line);
              if (obj.type === "session" && obj.cwd) {
                obj.cwd = proj.path;
                return JSON.stringify(obj);
              }
            } catch {}
            return line;
          });
          fs.writeFileSync(sessionFile, updated.join("\n"));
          const metaObj = metaFor(sessionFile);
          metaObj.projectId = projectId;
          saveMeta();
          return json(res, 200, { ok: true, projectId, path: proj.path });
        } catch (e) {
          return json(res, 500, { error: "Kunne ikke flytte samtale: " + e.message });
        }
      }

      case "GET /api/folders/validate": {
        const raw = url.searchParams.get("path") || "";
        const dir = canonicalDir(raw);
        return json(res, 200, {
          valid: !!dir,
          path: dir || raw,
          name: dir ? (path.basename(dir) || dir) : "",
        });
      }

      case "GET /api/folders": {
        const raw = url.searchParams.get("path") || HOME;
        const dir = canonicalDir(raw);
        if (!dir) return json(res, 400, { error: "Mappen finnes ikke" });
        let entries = [];
        try {
          entries = fs.readdirSync(dir, { withFileTypes: true });
        } catch (error) {
          return json(res, 400, { error: `Kunne ikke lese mappen: ${error.message}` });
        }
        const dirs = entries
          .filter((entry) => entry.isDirectory() && !entry.name.startsWith(".") && !entry.name.startsWith("$"))
          .map((entry) => ({ path: path.join(dir, entry.name), name: entry.name }))
          .sort((a, b) => a.name.localeCompare(b.name));
        return json(res, 200, { path: dir, parent: path.dirname(dir), dirs });
      }

      case "POST /api/omp/active": {
        const body = await readBody(req);
        ompActive = {
          instanceId: String(body.instanceId || ""),
          sessionId: String(body.sessionId || ""),
          sessionFile: body.sessionFile ? path.resolve(body.sessionFile) : "",
          cwd: body.cwd ? path.resolve(body.cwd) : "",
          reportedAt: Date.now(),
        };
        // Let Android discover the change without aggressive polling by also
        // logging it; SSE broadcast is not available on this endpoint today.
        log("info", `omp active reported: ${ompActive.sessionId} (${ompActive.instanceId || "unknown"})`);
        return json(res, 200, { ok: true, ...ompActive });
      }

      case "GET /api/network/interfaces": {
        const interfaces = networkInterfaces();
        return json(res, 200, { interfaces });
      }
      case "GET /api/active": {
        // En nylig rapportert OMP-terminalsesjon er mer autoritativ enn
        // Bridge-runtimen. Rapporter eldre enn 2 minutter regnes som foreldet.
        if (ompActive && Date.now() - ompActive.reportedAt < 120000 && ompActive.sessionFile) {
          return json(res, 200, {
            sessionFile: ompActive.sessionFile,
            sessionId: ompActive.sessionId,
            cwd: ompActive.cwd,
            streaming: false,
            lastUsed: ompActive.reportedAt,
            source: "omp-tui",
            instanceId: ompActive.instanceId,
            reportedAt: ompActive.reportedAt,
          });
        }
        const runtime = [...runtimes.values()]
          .filter((entry) => entry.sessionFile)
          .sort((a, b) => b.lastUsed - a.lastUsed)[0];
        if (!runtime) return json(res, 404, { error: "Ingen aktiv Bridge-samtale" });
        return json(res, 200, {
          sessionFile: runtime.sessionFile,
          sessionId: runtime.sessionId,
          cwd: runtime.cwd,
          streaming: !!runtime.state?.isStreaming,
          lastUsed: runtime.lastUsed,
          source: "bridge-runtime",
          instanceId: "",
          reportedAt: runtime.lastUsed,
        });
      }

      case "GET /api/sessions": {
        const cwd = url.searchParams.get("cwd");
        const includeArchived = url.searchParams.get("archived") === "1";
        const q = (url.searchParams.get("q") || "").trim();
        const limitRaw = url.searchParams.get("limit");
        const offset = Math.max(0, Number(url.searchParams.get("offset") || 0));
        const content = url.searchParams.get("content") === "1";
        const limit = limitRaw ? Math.max(1, Math.min(200, Number(limitRaw))) : null;
        let sessions = listSessions({ cwd: cwd || undefined, includeArchived });
        if (q) {
          const low = q.toLowerCase();
          const matched = [];
          const matchedFiles = new Set();
          // 1) Billig metadata-søk (tittel, mappe).
          for (const session of sessions) {
            const haystack = `${session.title || ""} ${session.cwd || ""}`.toLowerCase();
            if (haystack.includes(low)) {
              matched.push(session);
              matchedFiles.add(session.file);
            }
          }
          // 2) Valgfritt innholdssøk, kun hvis vi trenger flere resultater og
          //    brukeren eksplisitt ber om det.
          if (content && matched.length < (offset + (limit || sessions.length))) {
            for (const session of sessions) {
              if (matchedFiles.has(session.file)) continue;
              let hit = false;
              let snippet = "";
              try {
                for (const entry of entriesFor(session.file)) {
                  if (entry.type !== "message" || !entry.message) continue;
                  const text = textOfContent(entry.message.content);
                  if (text && text.toLowerCase().includes(low)) {
                    hit = true;
                    snippet = text.replace(/\s+/g, " ").trim().slice(0, 140);
                    break;
                  }
                }
              } catch {}
              if (hit) {
                session.snippet = snippet;
                matched.push(session);
                matchedFiles.add(session.file);
              }
              if (matched.length >= offset + (limit || sessions.length) + 20) break;
            }
          }
          sessions = matched;
        }
        sessions.sort((a, b) => b.updatedAt - a.updatedAt);
        const total = sessions.length;
        const sliced = limit == null
          ? sessions.slice(offset)
          : sessions.slice(offset, offset + limit);
        return json(res, 200, { sessions: sliced, total });
      }

      case "GET /api/session/messages": {
        const file = url.searchParams.get("file");
        if (!file) return json(res, 400, { error: "Mangler file" });
        const resolved = path.resolve(file);
        if (!fs.existsSync(resolved)) return json(res, 404, { error: "Fant ikke samtalen" });
        const entries = entriesFor(resolved);
        const session = loadSession(resolved);
        return json(res, 200, { session, messages: toAppMessages(entries) });
      }

      case "POST /api/sessions/open": {
        const body = await readBody(req);
        if (!body.file) return json(res, 400, { error: "Mangler file" });
        const file = path.resolve(body.file);
        const session = loadSession(file);
        if (!session) return json(res, 404, { error: "Fant ikke samtalen" });
        const messages = toAppMessages(entriesFor(file));
        // An explicit cwd from the app is only used when the stored one is
        // unusable. The agent never runs in a folder that is missing, and a
        // different folder is never guessed: history stays readable instead.
        const recordedDir = existingDir(session.cwd);
        const requestedDir = existingDir(body.cwd);
        const cwd = recordedDir || requestedDir;
        if (!cwd) {
          return json(res, 200, {
            session,
            messages,
            readOnly: true,
            reason: session.cwd
              ? `Arbeidsmappen finnes ikke: ${session.cwd}`
              : "Samtalen mangler arbeidsmappe",
            cwd: "",
            state: null,
          });
        }
        try {
          const runtime = await getRuntime(cwd);
          const result = await runtime.rpc("switch_session", { sessionPath: file }, 60000);
          if (result?.cancelled) {
            // OMP refused because the session is bound to a working folder the
            // runtime does not match. Do not pretend the switch succeeded.
            return json(res, 200, {
              session,
              messages,
              readOnly: true,
              reason: "OMP avviste samtalen fordi den er knyttet til en annen arbeidsmappe.",
              cwd: "",
              state: null,
            });
          }
          runtime.sessionFile = result?.sessionFile || file;
          runtime.sessionId = result?.sessionId || session.id;
          const state = await runtime.refreshState();
          const entries = entriesFor(runtime.sessionFile);
          return json(res, 200, {
            session: loadSession(runtime.sessionFile) || session,
            state: publicState(state),
            messages: toAppMessages(entries),
            cwd: runtime.cwd,
            readOnly: false,
          });
        } catch (error) {
          log("warn", `open ${file} failed: ${error?.message || error}`);
          return json(res, 200, {
            session,
            messages,
            readOnly: true,
            reason: String(error?.message || error),
            cwd: "",
            state: null,
          });
        }
      }

      case "POST /api/sessions/new": {
        const body = await readBody(req);
        const cwd = existingDir(body.cwd);
        if (!cwd) {
          return json(res, 400, {
            error: "Prosjektmappen finnes ikke eller er ikke en mappe. Velg en gyldig mappe.",
          });
        }
        const runtime = await getRuntime(cwd);
        await runtime.rpc("new_session", body.parentSession ? { parentSession: body.parentSession } : {}, 60000);
        const state = await runtime.refreshState();
        const file = runtime.sessionFile;
        // Wait for session file to actually exist and be readable on disk
        if (file) {
          let attempts = 0;
          while (attempts < 10) {
            const stat = fs.statSync(path.resolve(file), { throwIfNoEntry: false });
            if (stat) {
              try { readJsonl(path.resolve(file)); break; } catch {}
            }
            await new Promise(r => setTimeout(r, 200));
            attempts++;
          }
        }
        const session = file ? loadSession(file) : null;
        // Auto-assign projectId based on cwd matching registered project paths
        if (file) {
          try {
            const list = loadProjects();
            const normCwd = IS_WINDOWS ? cwd.toLowerCase() : cwd;
            const proj = list.find((p) => (IS_WINDOWS ? p.path.toLowerCase() : p.path) === normCwd);
            if (proj && session.projectId !== proj.id) {
              const resp = await fetch("http://127.0.0.1:" + CONFIG.port + "/api/projects/move-session", {
                method: "POST",
                headers: { "Content-Type": "application/json", "Authorization": "Bearer " + CONFIG.token },
                body: JSON.stringify({ file, projectId: proj.id }),
              });
              if (resp.ok && session) {
                session.projectId = proj.id;
              }
            }
          } catch { /* best effort */ }
        }

        return json(res, 200, { session, state: publicState(state), messages: [], cwd: runtime.cwd });
      }

      case "POST /api/sessions/name": {
        const body = await readBody(req);
        const file = body.file ? path.resolve(body.file) : "";
        const name = String(body.name || "").trim();
        if (!file || !name) return json(res, 400, { error: "Mangler file eller name" });
        metaFor(file).title = name;
        saveMeta();
        const runtime = runtimes.get(runtimeKey(body.cwd || loadSession(file)?.cwd || ""));
        let renamed = false;
        if (runtime && path.resolve(runtime.sessionFile || "") === file) {
          try {
            await runtime.rpc("set_session_name", { name });
            runtime.sessionTitle = name;
            renamed = true;
          } catch (error) {
            log("debug", `set_session_name failed: ${error.message}`);
          }
        }
        if (!renamed) {
          try {
            await writeSessionTitle(file, name);
          } catch (error) {
            log("warn", `writeSessionTitle failed: ${error.message}`);
            return json(res, 200, { ok: true, renamed: false, session: loadSession(file) });
          }
        }
        const savedTitle = metaFor(file).title;
        const titleConfirmed = savedTitle === name;
        return json(res, 200, { ok: true, renamed: true, titleConfirmed, session: loadSession(file) });
      }

      case "POST /api/sessions/archive": {
        const body = await readBody(req);
        if (!body.file) return json(res, 400, { error: "Mangler file" });
        const file = path.resolve(body.file);
        const runtimeActive = [...runtimes.values()].some((r) => path.resolve(r.sessionFile || "") === file);
        const tuiActive = !!ompActive && (Date.now() - ompActive.reportedAt < 120000) &&
          path.resolve(ompActive.sessionFile || "") === file;
        if (runtimeActive || tuiActive) {
          return json(res, 409, { error: "Samtalen er aktiv og kan ikke arkiveres." });
        }
        metaFor(file).archived = body.archived !== false;
        saveMeta();
        sessionCache.delete(file);
        return json(res, 200, { ok: true, archived: metaFor(file).archived });
      }

      case "DELETE /api/session": {
        const file = url.searchParams.get("file");
        if (!file) return json(res, 400, { error: "Mangler file" });
        const resolved = path.resolve(file);
        if (!resolved.startsWith(SESSIONS_ROOT)) {
          return json(res, 400, { error: "Samtalen ligger utenfor OMPs samtalemappe" });
        }
        if (!fs.existsSync(resolved)) return json(res, 404, { error: "Fant ikke samtalen" });
        // Beskytt aktiv samtale (Bridge-runtime og nylig rapportert OMP-TUI).
        const runtimeActive = [...runtimes.values()].some((r) => path.resolve(r.sessionFile || "") === resolved);
        const tuiActive = !!ompActive && (Date.now() - ompActive.reportedAt < 120000) &&
          path.resolve(ompActive.sessionFile || "") === resolved;
        if (runtimeActive || tuiActive) {
          return json(res, 409, { error: "Samtalen er aktiv og kan ikke flyttes til papirkurven." });
        }
        const stamp = new Date().toISOString().replace(/[:.]/g, "-");
        const id = `${stamp}-${path.basename(path.dirname(resolved))}`;
        const target = path.join(TRASH_ROOT, id);
        await fsp.mkdir(target, { recursive: true });
        await fsp.rename(resolved, path.join(target, path.basename(resolved)));
        const dossier = resolved.replace(/\.jsonl$/, "");
        if (fs.existsSync(dossier) && fs.statSync(dossier).isDirectory()) {
          await fsp.rename(dossier, path.join(target, path.basename(dossier)));
        }
        // Bevar metadata for gjenoppretting.
        const session = loadSession(path.join(target, path.basename(resolved)));
        const metaObj = {
          originalFile: resolved,
          originalDir: path.dirname(resolved),
          sessionId: session?.id || "",
          cwd: session?.cwd || "",
          title: session?.title || "",
          archived: !!metaFor(resolved).archived,
          deletedAt: Date.now(),
        };
        await fsp.writeFile(path.join(target, "meta.json"), JSON.stringify(metaObj, null, 2));
        delete meta.sessions[resolved];
        saveMeta();
        sessionCache.delete(resolved);
        return json(res, 200, { ok: true, movedTo: target, trashId: id });
      }

      case "GET /api/trash": {
        const items = [];
        if (fs.existsSync(TRASH_ROOT)) {
          for (const entry of fs.readdirSync(TRASH_ROOT, { withFileTypes: true })) {
            if (!entry.isDirectory()) continue;
            const dir = path.join(TRASH_ROOT, entry.name);
            let metaObj = null;
            try { metaObj = JSON.parse(fs.readFileSync(path.join(dir, "meta.json"), "utf8")); } catch {}
            const jsonl = fs.readdirSync(dir).find((name) => name.endsWith(".jsonl"));
            if (!jsonl) continue;
            const file = path.join(dir, jsonl);
            const session = loadSession(file);
            const stat = fs.statSync(file);
            items.push({
              trashId: entry.name,
              file,
              originalFile: metaObj?.originalFile || "",
              originalDir: metaObj?.originalDir || "",
              sessionId: metaObj?.sessionId || session?.id || "",
              cwd: metaObj?.cwd || session?.cwd || "",
              title: metaObj?.title || session?.title || path.basename(file, ".jsonl"),
              archived: !!metaObj?.archived,
              deletedAt: metaObj?.deletedAt || stat.mtimeMs,
              messageCount: session?.messageCount || 0,
              size: stat.size,
            });
          }
        }
        items.sort((a, b) => b.deletedAt - a.deletedAt);
        return json(res, 200, { items });
      }

      case "POST /api/trash/restore": {
        const body = await readBody(req);
        const file = body.file ? path.resolve(body.file) : "";
        if (!file || !fs.existsSync(file)) return json(res, 404, { error: "Fant ikke samtalen i papirkurven" });
        const dir = path.dirname(file);
        let metaObj = null;
        try { metaObj = JSON.parse(fs.readFileSync(path.join(dir, "meta.json"), "utf8")); } catch {}
        const originalDir = metaObj?.originalDir || path.dirname(metaObj?.originalFile || "");
        const targetDir = originalDir && fs.existsSync(originalDir) ? originalDir : SESSIONS_ROOT;
        await fsp.mkdir(targetDir, { recursive: true });
        const base = path.basename(file);
        let target = path.join(targetDir, base);
        if (fs.existsSync(target)) {
          const stamp = new Date().toISOString().replace(/[:.]/g, "-");
          target = path.join(targetDir, `${path.basename(base, ".jsonl")}-${stamp}.jsonl`);
        }
        await fsp.rename(file, target);
        const dossier = file.replace(/\.jsonl$/, "");
        if (fs.existsSync(dossier) && fs.statSync(dossier).isDirectory()) {
          const targetDossier = target.replace(/\.jsonl$/, "");
          if (!fs.existsSync(targetDossier)) await fsp.rename(dossier, targetDossier);
        }
        // Rydd opp meta/trash-mappe hvis tom.
        try {
          const remaining = fs.readdirSync(dir);
          if (remaining.length === 0) await fsp.rmdir(dir);
          else {
            // fjern meta.json hvis den finnes
            const metaPath = path.join(dir, "meta.json");
            if (fs.existsSync(metaPath)) await fsp.unlink(metaPath);
          }
        } catch {}
        if (metaObj?.originalFile) {
          // behold meta for gjenopprettet fil hvis ønskelig (ikke nødvendig)
        }
        sessionCache.delete(file);
        return json(res, 200, { ok: true, file: target });
      }
      case "GET /api/state": {
        const cwd = existingDir(url.searchParams.get("cwd") || "");
        if (!cwd) {
          // A missing folder is not an error the app should crash on; it must
          // keep the last valid folder and show no invented project.
          return json(res, 200, { state: null, sessionFile: "", cwdMissing: true });
        }
        const runtime = await getRuntime(cwd);
        const pending = runtime.pendingUi.values().next();
        // While OMP waits for an approval/question the RPC loop is blocked, so
        // get_state would time out. Answer with the cached state and the pending
        // request instead of failing the whole call.
        let state = runtime.state;
        if (pending.done) {
          state = await runtime.refreshState();
        }
        return json(res, 200, {
          state: publicState(state),
          sessionFile: runtime.sessionFile,
          request: pending.done ? null : pending.value,
        });
      }

      case "GET /api/models": {
        const cwd = url.searchParams.get("cwd");
        if (!cwd) return json(res, 400, { error: "Mangler cwd" });
        const runtime = await getRuntime(cwd);
        // A pending approval blocks every RPC, including get_available_models;
        // answer from the cache instead of making the app wait a minute.
        if (!runtime.models.length && runtime.pendingUi.size === 0) {
          const result = await runtime.rpc("get_available_models");
          runtime.models = result?.models || [];
        }
        return json(res, 200, { models: publicModels(runtime.models) });
      }

      case "POST /api/model": {
        const body = await readBody(req);
        if (!body.cwd || !body.provider || !body.modelId) return json(res, 400, { error: "Mangler cwd/provider/modelId" });
        const runtime = await getRuntime(body.cwd);
        const model = await runtime.rpc("set_model", { provider: body.provider, modelId: body.modelId }, 60000);
        await runtime.refreshState();
        return json(res, 200, { model: publicModel(model) });
      }

      case "POST /api/prompt": {
        const body = await readBody(req);
        const cwd = existingDir(body.cwd);
        if (!cwd) return json(res, 400, { error: "Arbeidsmappen finnes ikke. Velg en gyldig mappe." });
        if (typeof body.text !== "string" || !body.text.trim()) return json(res, 400, { error: "Tom melding" });
        // Never run a prompt in a folder the selected conversation does not
        // belong to. A mismatched cwd is a client bug, not a silent fallback.
        let target = null;
        if (body.file) {
          const resolvedFile = path.resolve(body.file);
          target = loadSession(resolvedFile);
          // Race condition: new session file may not be flushed to disk yet.
          // Retry with exponential backoff.
          if (!target) {
            for (const delay of [300, 600, 1200]) {
              await new Promise(r => setTimeout(r, delay));
              target = loadSession(resolvedFile);
              if (target) break;
            }
          }
          // Safe fallback: use runtime.sessionFile only if it matches
          if (!target && runtime.sessionFile && path.resolve(runtime.sessionFile) === resolvedFile) {
            target = loadSession(resolvedFile);
          }
          if (!target) return json(res, 404, { error: "Fant ikke samtalen" });
          if (target.cwd && !samePath(target.cwd, cwd)) {
            return json(res, 409, {
              error: "Samtalen tilhører en annen arbeidsmappe. Åpne den på nytt fra listen.",
            });
          }
        }
        const runtime = await getRuntime(cwd);
        if (target && path.resolve(body.file) !== path.resolve(runtime.sessionFile || "")) {
          const switched = await runtime.rpc("switch_session", { sessionPath: path.resolve(body.file) }, 60000);
          if (switched?.cancelled) {
            return json(res, 409, {
              error: "OMP avviste samtalen fordi arbeidsmappen er forskjellig.",
            });
          }
          await runtime.refreshState();
        }
        if (Array.isArray(body.attachments) && body.attachments.length) {
          await saveAttachments(runtime.cwd, body.attachments);
        }
        const images = Array.isArray(body.images) ? body.images : [];
        const ack = await runtime.rpc("prompt", { message: body.text, ...(images.length ? { images } : {}), ...(body.streamingBehavior ? { streamingBehavior: body.streamingBehavior } : {}) }, 60000);
        runtime.lastUsed = Date.now();
        return json(res, 200, { ok: true, ack, sessionFile: runtime.sessionFile });
      }

      case "POST /api/poweroff": {
        // Krev eksplisitt bekreftelse fra klienten.
        const body = await readBody(req);
        if (body.confirm !== true) return json(res, 400, { error: "Bekreftelse mangler" });
        const cmd = IS_WINDOWS
          ? "shutdown /s /t 5 /c \"OmpChat: slår av PC-en\""
          : "shutdown -h now";
        try {
          exec(cmd, { windowsHide: true }, (error) => {
            if (error) log("warn", `poweroff feilet: ${error.message}`);
          });
          return json(res, 200, { ok: true, message: "Slår av PC-en." });
        } catch (error) {
          return json(res, 500, { error: String(error?.message || error) });
        }
      }

      case "POST /api/abort": {
        const body = await readBody(req);
        if (!body.cwd) return json(res, 400, { error: "Mangler cwd" });
        const runtime = await getRuntime(body.cwd);
        // OMP cannot process `abort` while it waits for an interactive answer,
        // so cancel anything pending first: that unblocks the RPC loop and lets
        // the abort through instead of timing out.
        for (const id of [...runtime.pendingUi.keys()]) {
          runtime.write({ type: "extension_ui_response", id, cancelled: true });
        }
        runtime.pendingUi.clear();
        runtime.broadcast({ type: "ui_cancel", cwd: runtime.cwd, targetId: "" });
        try {
          await runtime.rpc("abort", {}, 30000);
        } catch (error) {
          // Still wedged: stop the child so the next request spawns a fresh
          // OMP process instead of piling up timeouts.
          log("warn", `abort feilet for ${runtime.cwd}: ${error.message}`);
          await runtime.stop();
          return json(res, 200, { ok: true, restarted: true });
        }
        return json(res, 200, { ok: true });
      }

      case "POST /api/regenerate": {
        const body = await readBody(req);
        if (!body.cwd || !body.entryId || typeof body.text !== "string") {
          return json(res, 400, { error: "Mangler cwd/entryId/text" });
        }
        const runtime = await getRuntime(body.cwd);
        if (body.file && path.resolve(body.file) !== path.resolve(runtime.sessionFile || "")) {
          await runtime.rpc("switch_session", { sessionPath: path.resolve(body.file) }, 60000);
          await runtime.refreshState();
        }
        // `branch` restores the conversation to just before the selected entry;
        // re-prompting the same message is OMP's own way of regenerating.
        const branch = await runtime.rpc("branch", { entryId: body.entryId }, 60000);
        if (branch?.cancelled) return json(res, 409, { error: "OMP avbrøt branching" });
        const message = body.text || branch?.text || "";
        if (!message.trim()) return json(res, 400, { error: "Tom melding etter branching" });
        const ack = await runtime.rpc("prompt", { message }, 60000);
        await runtime.refreshState();
        return json(res, 200, { ok: true, ack, sessionFile: runtime.sessionFile, branched: true });
      }

      case "POST /api/ask": {
        const body = await readBody(req);
        if (!body.cwd || !body.id) return json(res, 400, { error: "Mangler cwd/id" });
        const runtime = await getRuntime(body.cwd);
        let frame;
        if (body.cancelled) {
          frame = { type: "extension_ui_response", id: body.id, cancelled: true, timedOut: !!body.timedOut };
        } else if (Array.isArray(body.answers)) {
          frame = { type: "extension_ui_response", id: body.id, answers: body.answers };
        } else if (typeof body.confirmed === "boolean") {
          frame = { type: "extension_ui_response", id: body.id, confirmed: body.confirmed };
        } else {
          frame = { type: "extension_ui_response", id: body.id, value: String(body.value ?? "") };
        }
        runtime.write(frame);
        runtime.pendingUi.delete(body.id);
        return json(res, 200, { ok: true });
      }

      case "POST /api/bash": {
        if (!CONFIG.allowBash) return json(res, 403, { error: "Terminal er deaktivert i bridge-konfigurasjonen" });
        const body = await readBody(req);
        if (!body.cwd || typeof body.command !== "string" || !body.command.trim()) {
          return json(res, 400, { error: "Mangler cwd/command" });
        }
        const runtime = await getRuntime(body.cwd);
        const result = await runtime.rpc("bash", { command: body.command }, 120000);
        return json(res, 200, { result });
      }

      case "POST /api/upload": {
        const body = await readBody(req);
        if (!body.cwd || !Array.isArray(body.files)) return json(res, 400, { error: "Mangler cwd/files" });
        const saved = await saveAttachments(body.cwd, body.files);
        return json(res, 200, { files: saved });
      }

      case "GET /api/events":
        return serveEvents(req, res);

      case "GET /api/diagnostics": {
        return json(res, 200, {
          version: VERSION,
          node: process.version,
          platform: process.platform,
          sessionsRoot: SESSIONS_ROOT,
          approvalMode: CONFIG.approvalMode,
          allowBash: CONFIG.allowBash,
          hosts: lanAddresses(),
          port: CONFIG.port,
          log: logFileTail(),
        });
      }

      case "GET /api/pairing": {
        return json(res, 200, {
          payload: `omp2://${lanAddresses()[0] || "127.0.0.1"}:${CONFIG.port}?token=${CONFIG.token}`,
          hosts: lanAddresses(),
          port: CONFIG.port,
          token: CONFIG.token,
        });
      }

      // ---------------------------------------------------------------------------
      // Godkjenningsmodus (approval mode)
      // ---------------------------------------------------------------------------

      case "POST /api/approval": {
        const body = await readBody(req);
        const raw = String(body.mode || "").trim();
        // OMP forstår bare always-ask | always-ask-cached | write | yolo.
        // Gamle app-verdier ("default"/"ask") mappes til de reelle modusene,
        // ellers falt OMP tilbake til å spørre om alt.
        const legacy = { default: "write", ask: "yolo" };
        const mode = legacy[raw] || raw;
        const valid = ["always-ask", "always-ask-cached", "write", "yolo"];
        if (!valid.includes(mode)) {
          return json(res, 400, { error: `Ugyldig modus: ${raw}. Gyldige: ${valid.join(", ")}` });
        }
        CONFIG.approvalMode = mode;
        writeConfig();
        log("info", `Godkjenningsmodus endret til: ${mode}`);
        // Kjørende runtimes fikk modus satt ved oppstart: restart de som er
        // ledig (pågående oppgaver berøres ikke; nye runder bruker ny modus).
        let restarted = 0;
        let busyLeft = 0;
        for (const [key, runtime] of runtimes) {
          const busy = Boolean(runtime.state?.isStreaming) || runtime.restarting ||
            runtime.pendingUi?.size > 0;
          if (busy) { busyLeft++; continue; }
          runtime.ready = false;
          try { runtime.child?.kill(); } catch {}
          runtimes.delete(key);
          if (runtime.pendingUi) runtime.pendingUi.clear();
          restarted++;
        }
        return json(res, 200, { ok: true, approvalMode: mode, restarted, runningTasks: busyLeft });
      }

      // ---------------------------------------------------------------------------
      // Velg aktiv tale-motor (STT/TTS)
      // ---------------------------------------------------------------------------

      case "POST /api/voice/select": {
        const body = await readBody(req);
        const kind = String(body.kind || "").trim(); // "stt" | "tts"
        const engineId = String(body.engineId || "").trim();
        const voice = typeof body.voice === "string" ? body.voice.trim() : "";
        if (!kind || !engineId) return json(res, 400, { error: "Mangler kind/engineId" });
        if (kind !== "stt" && kind !== "tts") return json(res, 400, { error: "kind må være 'stt' eller 'tts'" });
        const status = voiceStatus() || { tts: {}, stt: {} };
        const installed = kind === "tts" ? (status.tts || {}) : (status.stt || {});
        if (!installed[engineId] || !installed[engineId].installed) {
          return json(res, 404, { error: `Motor ${engineId} er ikke installert for ${kind}` });
        }
        // Bygg enginobjektet fra status-oppføringen slik at id + model/dir
        // alltid passer sammen (tidligere bug: del-felt ble blandet fra
        // forrige motor når man byttet frem og tilbake mellom motorer).
        const entry = installed[engineId];
        // 0.5B realtime bruker .pt-embeddings -> default Emma (Alice mangler der).
        const voiceFallback = engineId === "vibevoice-0.5b" ? "en-Emma_woman" : "en-Alice_woman";
        const merged = {
          ...entry,
          id: engineId,
          // Piper: 10-stemmers NVCC (5 kvinner K*, 5 menn M*). voice-can videre
          // ("KSV" osv.) eller standard stemme hvis ikke satt.
          speakerId: entry.type === "piper" && /^[KM][A-Z]{2}$/.test(voice) ? voice : undefined,
          // VibeVoice: preset navn ("en-Alice_woman"/"en-Emma_woman" osv.)
          voice: entry.type === "vibevoice" ? (voice || entry.voice || voiceFallback) : undefined,
        };
        if (merged.speakerId === undefined) delete merged.speakerId;
        if (merged.voice === undefined) delete merged.voice;
        const config = voiceConfig() || {};
        if (kind === "tts") config.tts = merged;
        else config.stt = merged;
        fs.mkdirSync(VOICE_DIR, { recursive: true });
        fs.writeFileSync(VOICE_CONFIG_FILE, JSON.stringify(config, null, 2));
        log("info", `Aktive ${kind}-motor: ${engineId}${voice ? `, stemme: ${voice}` : ""}`);
        return json(res, 200, { ok: true, kind, engineId, voice: voice || null, active: config });
      }

      // ---------------------------------------------------------------------------
      // Voice Manager (real local STT/TTS installed by the Windows installer)
      // ---------------------------------------------------------------------------

      case "GET /api/voice/engines": {
        return json(res, 200, voiceEngineList());
      }

      case "POST /api/voice/install": {
        const body = await readBody(req);
        const engineId = String(body.engine || "").trim();
        if (!engineId) return json(res, 400, { error: "Mangler engine" });
        const group = body.kind === "stt" || VOICE_CATALOG.stt[engineId] ? "stt" : "tts";
        if (!VOICE_CATALOG[group][engineId]) return json(res, 404, { error: "Ukjent motor" });
        if (!fs.existsSync(VOICE_SETUP)) {
          return json(res, 501, { error: "Installasjonsskriptet mangler. Kjør Nimay OMP-installasjonen på nytt." });
        }
        const job = voiceJob();
        if (job && job.stage && job.stage !== "done" && job.stage !== "failed") {
          return json(res, 409, { error: "En installasjon pågår allerede", job });
        }
        if (voiceInstall) {
          return json(res, 409, { error: "En installasjon pågår allerede", job: voiceJob() });
        }
        const python = voicePython() ? path.join(VOICE_DIR, "venv", "Scripts", "python.exe") : "";
        // A detached child dies silently on Windows here, so the setup script runs
        // as a normal child of the bridge (which stays alive) and its progress is
        // tracked through install-job.json.
        voiceInstall = { engine: engineId, started: Date.now(), child: null };
        writeVoiceJob({ stage: "starter", engine: engineId, updated: new Date().toISOString() });
        const logFd = openVoiceLog();
        const child = spawn("powershell.exe", [
          "-NoProfile", "-ExecutionPolicy", "Bypass",
          "-File", VOICE_SETUP,
          "-InstallDir", path.join(HERE, ".."),
          "-PythonExe", python,
          "-Engine", `${group}:${engineId}`,
          "-SkipSmokeTest",
        ], { cwd: VOICE_DIR, windowsHide: true, stdio: ["ignore", logFd, logFd] });
        voiceInstall.child = child;
        log("info", `Startet installasjon av tale-motor: ${engineId} (pid ${child.pid})`);
        child.on("error", (error) => {
          voiceInstall = null;
          writeVoiceJob({ stage: "failed", error: `Kunne ikke starte installasjonen: ${error.message}`, updated: new Date().toISOString() });
        });
        child.on("close", (code) => {
          voiceInstall = null;
          const current = voiceJob() || {};
          if (current.stage === "failed" || code !== 0) {
            writeVoiceJob({
              stage: "failed",
              error: current.error || `Installasjonen avsluttet med kode ${code}. Se voice\\install.log.`,
              updated: new Date().toISOString(),
            });
            log("error", `Installasjon av ${engineId} feilet (kode ${code})`);
          } else {
            log("info", `Installasjon av ${engineId} fullført`);
          }
        });
        return json(res, 202, {
          ok: true,
          job: voiceJob(),
          engine: engineId,
          note: "Installasjonen kjører i bakgrunnen. Følg med i /api/voice/engines.",
        });
      }

      case "POST /api/voice/uninstall": {
        const body = await readBody(req);
        const engineId = String(body.engine || "").trim();
        if (!engineId) return json(res, 400, { error: "Mangler engine" });
        const status = voiceStatus();
        if (!status) return json(res, 404, { error: "Ingen tale-motorer er installert" });
        const inTts = status.tts && status.tts[engineId];
        const inStt = status.stt && status.stt[engineId];
        if (!inTts && !inStt) return json(res, 404, { error: "Ukjent eller ikke installert motor" });
        if (inTts) {
          delete status.tts[engineId];
          const model = inTts.model;
          for (const file of [model, inTts.config]) {
            try { if (file && fs.existsSync(file)) fs.rmSync(file, { force: true }); } catch {}
          }
        }
        if (inStt) {
          delete status.stt[engineId];
          try { if (inStt.dir && fs.existsSync(inStt.dir)) fs.rmSync(inStt.dir, { recursive: true, force: true }); } catch {}
        }
        fs.writeFileSync(VOICE_STATUS_FILE, JSON.stringify(status, null, 2));
        const config = voiceConfig() || {};
        if (config.tts?.id === engineId) {
          config.tts = Object.values(status.tts || {})[0] || null;
        }
        if (config.stt?.id === engineId) {
          config.stt = Object.values(status.stt || {})[0] || null;
        }
        fs.writeFileSync(VOICE_CONFIG_FILE, JSON.stringify(config, null, 2));
        log("info", `Avinstallerte tale-motor: ${engineId}`);
        return json(res, 200, { ok: true, engine: engineId });
      }

      case "POST /api/voice/profile": {
        const body = await readBody(req);
        if (!body.profile) return json(res, 400, { error: "Mangler profile" });
        const validProfiles = ["raske", "balansert", "kvalitet", "egendefinert"];
        if (!validProfiles.includes(body.profile)) {
          return json(res, 400, { error: "Ugyldig profil" });
        }
        CONFIG.voice.profile = body.profile;
        log("info", `Satte taleprofil: ${body.profile}`);
        return json(res, 200, { ok: true, profile: body.profile });
      }

      case "POST /api/voice/settings": {
        const body = await readBody(req);
        if (!body.settings) return json(res, 400, { error: "Mangler settings" });
        CONFIG.voice.settings = { ...CONFIG.voice.settings, ...body.settings };
        log("info", "Oppdaterte taleinnstillinger");
        return json(res, 200, { ok: true, settings: CONFIG.voice.settings });
      }

      case "POST /api/voice/speak": {
        const body = await readBody(req);
        const text = String(body.text || "").trim();
        if (!text) return json(res, 400, { error: "Mangler text" });
        if (text.length > 2000) return json(res, 400, { error: "Teksten er for lang (maks 2000 tegn)" });
        fs.mkdirSync(VOICE_OUT, { recursive: true });
        const audioId = `tts-${Date.now()}-${crypto.randomBytes(4).toString("hex")}`;
        const outPath = path.join(VOICE_OUT, `${audioId}.wav`);
        const result = await runVoiceWorker({ command: "tts", text, out: outPath });
        if (!result.ok) return json(res, 503, { error: result.error, installed: Boolean(voiceConfig()?.tts) });
        const token = CONFIG.token;
        log("info", `Genererte tale (${result.data.duration}s) med ${result.data.engine}`);
        return json(res, 200, {
          ok: true,
          audioId,
          duration: result.data.duration,
          engine: result.data.engine,
          audioUrl: `/api/voice/audio/${audioId}.wav?token=${token}`,
        });
      }

      case "POST /api/voice/transcribe": {
        const body = await readBody(req, 32 * 1024 * 1024);
        const data = String(body.audio || "").replace(/^data:[^;]+;base64,/, "");
        if (!data) return json(res, 400, { error: "Mangler audio (base64 WAV)" });
        if (!voiceConfig()?.stt) {
          return json(res, 503, { error: "Ingen talegjenkjenning er installert. Kjør Nimay OMP-installasjonen." });
        }
        fs.mkdirSync(VOICE_OUT, { recursive: true });
        const wavPath = path.join(VOICE_OUT, `stt-${Date.now()}.wav`);
        fs.writeFileSync(wavPath, Buffer.from(data, "base64"));
        const result = await runVoiceWorker({ command: "stt", wav: wavPath }, 240000);
        try { fs.rmSync(wavPath, { force: true }); } catch {}
        if (!result.ok) return json(res, 500, { error: result.error });
        return json(res, 200, { ok: true, text: result.data.text, engine: result.data.engine, duration: result.data.duration });
      }

      case "POST /api/voice/start": {
        const body = await readBody(req);
        log("info", "Startet STT-strøm");
        return json(res, 200, { ok: true, engine: voiceConfig()?.stt?.id || null });
      }

      case "POST /api/voice/stop": {
        log("info", "Stoppet tale-strøm");
        return json(res, 200, { ok: true });
      }
      case "POST /api/terminal/create": {
        const body = await readBody(req);
        const cwd = body.cwd ? path.resolve(String(body.cwd)) : os.homedir();
        const cols = Number(body.cols) || 80;
        const rows = Number(body.rows) || 24;
        const session = createTerminalSession(cwd, cols, rows);
        log("info", `Terminal opprettet: ${session.id} (${cwd})`);
        return json(res, 200, { ok: true, id: session.id, cwd: session.cwd, pid: session.pty ? session.pty.pid : (session.child ? session.child.pid : null) });
      }

      case "POST /api/terminal/kill": {
        const body = await readBody(req);
        const id = String(body.id || "");
        const ok = killTerminalSession(id);
        log("info", `Terminal avsluttet: ${id} (${ok})`);
        return json(res, 200, { ok });
      }

      case "GET /api/terminal/list": {
        const list = [];
        for (const [id, s] of terminalSessions) {
          list.push({ id, cwd: s.cwd, alive: s.alive, pid: s.pty ? s.pty.pid : (s.child ? s.child.pid : null), createdAt: s.createdAt });
        }
        return json(res, 200, { ok: true, sessions: list });
      }

      // =========================================================================
﻿      // =========================================================================
      // Git + filredigering (Etappe 6)
      // =========================================================================

      case "GET /api/git/status": {
        const q = new URL(req.url, "http://localhost").searchParams;
        const cwd = clientPath(q.get("path") || "");
        if (!cwd) return json(res, 400, { error: "Kun absolutte stier" });
        const r = await runGit(cwd, ["status", "--porcelain=v2", "-b"], 30000);
        if (r.code !== 0) return json(res, 500, { error: (r.stderr || r.stdout || "git status feilet").trim() });
        const parsed = parseGitStatus(r.stdout);
        return json(res, 200, { ok: true, ...parsed });
      }

      case "GET /api/git/diff": {
        const q = new URL(req.url, "http://localhost").searchParams;
        const cwd = clientPath(q.get("path") || "");
        if (!cwd) return json(res, 400, { error: "Kun absolutte stier" });
        const file = q.get("file") || "";
        const staged = q.get("staged") === "1";
        const args = ["diff"];
        if (staged) args.push("--cached");
        if (file) args.push("--", file);
        const r = await runGit(cwd, args, 60000);
        return json(res, 200, { ok: true, diff: r.stdout, error: r.stderr.trim() });
      }

      case "GET /api/git/log": {
        const q = new URL(req.url, "http://localhost").searchParams;
        const cwd = clientPath(q.get("path") || "");
        if (!cwd) return json(res, 400, { error: "Kun absolutte stier" });
        const limit = Math.min(50, Number(q.get("limit") || 20) || 20);
        const r = await runGit(cwd, ["log", `-${limit}`, "--pretty=format:%H%x1f%an%x1f%ad%x1f%s", "--date=iso"], 30000);
        if (r.code !== 0) return json(res, 500, { error: (r.stderr || "git log feilet").trim() });
        const commits = r.stdout.split(/\r?\n/).filter(Boolean).map((line) => {
          const [hash, author, date, ...msg] = line.split("\x1f");
          return { hash, author, date, message: msg.join("\x1f") };
        });
        return json(res, 200, { ok: true, commits });
      }

      case "GET /api/git/branches": {
        const q = new URL(req.url, "http://localhost").searchParams;
        const cwd = clientPath(q.get("path") || "");
        if (!cwd) return json(res, 400, { error: "Kun absolutte stier" });
        const r = await runGit(cwd, ["branch", "-a", "--format=%(refname:short)"], 30000);
        if (r.code !== 0) return json(res, 500, { error: (r.stderr || "git branch feilet").trim() });
        const branches = r.stdout.split(/\r?\n/).map((b) => b.trim()).filter(Boolean);
        const head = await runGit(cwd, ["rev-parse", "--abbrev-ref", "HEAD"], 10000);
        return json(res, 200, { ok: true, branches, current: (head.stdout || "").trim() });
      }

      case "POST /api/git/stage": {
        const body = await readBody(req);
        const cwd = clientPath(body.cwd || "");
        if (!cwd) return json(res, 400, { error: "Kun absolutte stier" });
        const files = Array.isArray(body.files) ? body.files : [];
        const args = ["add", "--", ...files.map(String)];
        const r = await runGit(cwd, args, 60000);
        if (r.code !== 0) return json(res, 500, { error: (r.stderr || "git add feilet").trim() });
        return json(res, 200, { ok: true });
      }

      case "POST /api/git/unstage": {
        const body = await readBody(req);
        const cwd = clientPath(body.cwd || "");
        if (!cwd) return json(res, 400, { error: "Kun absolutte stier" });
        const files = Array.isArray(body.files) ? body.files : [];
        const args = ["reset", "HEAD", "--", ...files.map(String)];
        const r = await runGit(cwd, args, 60000);
        if (r.code !== 0) return json(res, 500, { error: (r.stderr || "git reset feilet").trim() });
        return json(res, 200, { ok: true });
      }

      case "POST /api/git/commit": {
        const body = await readBody(req);
        const cwd = clientPath(body.cwd || "");
        if (!cwd) return json(res, 400, { error: "Kun absolutte stier" });
        const message = String(body.message || "").trim();
        if (!message) return json(res, 400, { error: "Commit-melding mangler" });
        const r = await runGit(cwd, ["commit", "-m", message], 120000);
        if (r.code !== 0) return json(res, 500, { error: (r.stderr || r.stdout || "git commit feilet").trim() });
        return json(res, 200, { ok: true, output: r.stdout.trim() });
      }

      case "POST /api/git/checkout": {
        const body = await readBody(req);
        const cwd = clientPath(body.cwd || "");
        if (!cwd) return json(res, 400, { error: "Kun absolutte stier" });
        const branch = String(body.branch || "").trim();
        if (!branch) return json(res, 400, { error: "Branch mangler" });
        const r = await runGit(cwd, ["checkout", branch], 60000);
        if (r.code !== 0) return json(res, 500, { error: (r.stderr || "git checkout feilet").trim() });
        return json(res, 200, { ok: true, output: (r.stdout + r.stderr).trim() });
      }

      case "POST /api/git/pull": {
        const body = await readBody(req);
        const cwd = clientPath(body.cwd || "");
        if (!cwd) return json(res, 400, { error: "Kun absolutte stier" });
        const r = await runGit(cwd, ["pull"], 300000);
        return json(res, r.code === 0 ? 200 : 500, { ok: r.code === 0, output: (r.stdout + r.stderr).trim() });
      }

      case "POST /api/git/push": {
        const body = await readBody(req);
        const cwd = clientPath(body.cwd || "");
        if (!cwd) return json(res, 400, { error: "Kun absolutte stier" });
        const r = await runGit(cwd, ["push"], 300000);
        return json(res, r.code === 0 ? 200 : 500, { ok: r.code === 0, output: (r.stdout + r.stderr).trim() });
      }

      case "POST /api/git/fetch": {
        const body = await readBody(req);
        const cwd = clientPath(body.cwd || "");
        if (!cwd) return json(res, 400, { error: "Kun absolutte stier" });
        const r = await runGit(cwd, ["fetch", "--all"], 120000);
        return json(res, r.code === 0 ? 200 : 500, { ok: r.code === 0, output: (r.stdout + r.stderr).trim() });
      }

      case "GET /api/files/read": {
        const q = new URL(req.url, "http://localhost").searchParams;
        const p = clientPath(q.get("path") || "");
        if (!p) return json(res, 400, { error: "Kun absolutte stier" });
        try {
          const st = fs.statSync(p);
          if (!st.isFile()) return json(res, 400, { error: "Ikke en fil" });
          const content = fs.readFileSync(p, "utf8");
          return json(res, 200, { ok: true, path: p, content, size: st.size, mtime: Math.round(st.mtimeMs) });
        } catch (error) {
          return json(res, 500, { error: error.message });
        }
      }

      case "POST /api/files/write": {
        const body = await readBody(req);
        const p = clientPath(body.path || "");
        if (!p) return json(res, 400, { error: "Kun absolutte stier" });
        const expectedMtime = body.expectedMtime ? Number(body.expectedMtime) : null;
        try {
          if (expectedMtime !== null) {
            try {
              const st = fs.statSync(p);
              if (Math.round(st.mtimeMs) !== expectedMtime) {
                return json(res, 409, { error: "Filen er endret av en annen prosess", conflict: true, mtime: Math.round(st.mtimeMs) });
              }
            } catch {}
          }
          fs.writeFileSync(p, String(body.content ?? ""), "utf8");
          const st = fs.statSync(p);
          return json(res, 200, { ok: true, path: p, mtime: Math.round(st.mtimeMs), size: st.size });
        } catch (error) {
          return json(res, 500, { error: error.message });
        }
      }

      case "GET /api/gotify/config": {
        const masked = gotifyConfig.token ? (gotifyConfig.token.slice(0, 4) + "..." + gotifyConfig.token.slice(-4)) : "";
        return json(res, 200, { ok: true, enabled: gotifyConfig.enabled, server: gotifyConfig.server, tokenMasked: masked, hasToken: !!gotifyConfig.token, events: gotifyConfig.events || {} });
      }

      case "POST /api/gotify/config": {
        const body = await readBody(req);
        if (typeof body.enabled === "boolean") gotifyConfig.enabled = body.enabled;
        if (typeof body.server === "string") gotifyConfig.server = body.server.trim();
        if (typeof body.token === "string" && body.token.trim() && body.token !== "***") gotifyConfig.token = body.token.trim();
        if (body.events && typeof body.events === "object") gotifyConfig.events = body.events;
        saveGotifyConfig();
        log("info", `Gotify config updated: enabled=${gotifyConfig.enabled}`);
        return json(res, 200, { ok: true, enabled: gotifyConfig.enabled });
      }

      case "POST /api/gotify/test": {
        const ok = await sendGotify("Nimay OMP — test", "Gotify-tilkobling fungerer.", 5, null);
        return json(res, ok ? 200 : 500, { ok });
      }

      // Filbehandler (teleton <-> PC): liste, mapper, metadetra, streaming
      // =========================================================================

      case "GET /api/fs/list":
      case "GET /api/fs/storage":
      case "GET /api/fs/tree":
      case "GET /api/fs/search":
      case "GET /api/files/download":
      case "GET /api/files/hash":
      case "GET /api/files/upload-status": {
        const q = new URL(req.url, "http://localhost").searchParams;
        const raw = q.get("path") || "";
        const p = path.resolve(raw === "" ? "." : raw);

        // Roots/drives listing
        if (route === "GET /api/fs/list" && (raw === "" || raw === "/")) {
          const out = [];
          if (process.platform === "win32") {
            try {
              const out2 = execSync("fsutil fs drives", { timeout: 4000, encoding: "utf8" });
              const drives = String(out2).match(/[A-Z]:\\/g) || [];
              for (const drive of drives) {
                try { fs.statSync(drive); out.push({ name: drive, type: "dir", size: 0 }); } catch {}
              }
            } catch (error) {
              // fallback to C:\ if fsutil holds trouble
              out.push({ name: "C:\\", type: "dir", size: 0 });
            }
          } else {
            out.push({ name: "/", type: "dir", size: 0 });
            try {
              for (const extra of ["/home", "/media", "/mnt"]) {
                try { fs.statSync(extra); out.push({ name: extra, type: "dir", size: 0 }); } catch {}
              }
            } catch {}
          }
          const __home = os.homedir();
          const __places = [];
          const __cands = process.platform === "win32"
            ? [["Hjem", __home], ["Skrivebord", path.join(__home, "Desktop")], ["Dokumenter", path.join(__home, "Documents")], ["Nedlastinger", path.join(__home, "Downloads")], ["Bilder", path.join(__home, "Pictures")], ["Musikk", path.join(__home, "Music")], ["Videoer", path.join(__home, "Videos")]]
            : [["Hjem", __home], ["Skrivebord", path.join(__home, "Desktop")], ["Dokumenter", path.join(__home, "Documents")], ["Nedlastinger", path.join(__home, "Downloads")], ["Bilder", path.join(__home, "Pictures")], ["Musikk", path.join(__home, "Music")], ["Videoer", path.join(__home, "Videos")]];
          for (const [__label, __dir] of __cands) { try { if (fs.statSync(__dir).isDirectory()) __places.push({ label: __label, path: __dir }); } catch {} }
          return json(res, 200, { ok: true, path: raw, roots: true, entries: out, places: __places, defaultPath: __home, platform: process.platform });
        }

        // Storage info (total / free) best effort
        if (route === "GET /api/fs/storage") {
          let free = null, total = null;
          try {
            if (fs.statfsSync && typeof fs.statfsSync === "function") {
              const sf = fs.statfsSync(p);
              free = Number(sf.bfree) * Number(sf.bsize);
              total = Number(sf.blocks) * Number(sf.bsize);
            }
          } catch {}
          return json(res, 200, { ok: true, path: p, free, total });
        }

        // Recursive search within the folder (simple walk, bounded by time and hits)
        if (route === "GET /api/fs/search") {
          const query = (q.get("query") || "").toLowerCase();
          const maxHits = Math.min(200, Number(q.get("max") || 50) || 50);
          const hits = [];
          const startedAt = Date.now();
          const walk = (dir, depth) => {
            if (hits.length >= maxHits || Date.now() - startedAt > 2500) return;
            let entries = [];
            try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
            for (const entry of entries) {
              if (hits.length >= maxHits || Date.now() - startedAt > 2500) return;
              const full = path.join(dir, entry.name);
              if (entry.name.toLowerCase().includes(query) && query !== "") {
                try {
                  const st = fs.statSync(full);
                  hits.push({
                    name: entry.name,
                    type: st.isDirectory() ? "dir" : "file",
                    size: st.size,
                    mtime: Math.round(st.mtimeMs),
                    relative: full.replace(normalizedFs(p), "").replace(/^[\\/]+/, ""),
                  });
                } catch {}
              }
              if (entry.isDirectory() && depth > 0) walk(full, depth - 1);
            }
          };
          walk(p, q.get("depth") ? Number(q.get("depth")) : 5);
          return json(res, 200, { ok: true, path: p, entries: hits, truncated: Date.now() - startedAt > 2500 });
        }

        const stat = (() => { try { return fs.statSync(p); } catch { return null; } })();
        if (!stat) return json(res, 404, { error: `Fant ikke ${p}` });

        // PC -> telefon: binær nedlasting med HTTP Range (store filer, gjenopptak).
        if (route === "GET /api/files/download") {
          if (!stat.isFile()) return json(res, 400, { error: "Kan ikke laste ned en mappe" });
          const total = Number(stat.size);
          let start = 0;
          let end = total - 1;
          let status = 200;
          const rangeHeader = req.headers.range || "";
          if (rangeHeader.startsWith("bytes=")) {
            const m = rangeHeader.match(/bytes=(\d*)-(\d*)/);
            if (m) {
              if (m[1] !== "") start = parseInt(m[1], 10);
              if (m[2] !== "") end = parseInt(m[2], 10);
              status = 206;
            }
          }
          if (start >= total || end < start) return json(res, 416, { error: "Ugyldig rekkevidde", size: total });
          const headers = {
            "content-type": fsMime(p),
            "accept-ranges": "bytes",
            "cache-control": "no-store",
            "content-disposition": `attachment; filename="${path.basename(p).replace(/"/g, "")}"`,
            "x-file-sha256": "",
          };
          if (status === 200) headers["content-length"] = total;
          else headers["content-range"] = `bytes ${start}-${end}/${total}`;
          res.writeHead(status, headers);
          if (req.method === "HEAD") return res.end();
          const stream = fs.createReadStream(p, { start, end });
          stream.on("error", () => { try { res.destroy(); } catch {} });
          stream.pipe(res);
          return undefined;
        }

        // Filintegritet: beregn sha256 for sammenligning etter overføring
        if (route === "GET /api/files/hash") {
          const hash = crypto.createHash("sha256");
          await new Promise((resolve, reject) => {
            const stream = fs.createReadStream(p, { highWaterMark: 1024 * 1024 });
            stream.on("data", (chunk) => hash.update(chunk));
            stream.on("error", reject);
            stream.on("end", resolve);
          });
          return json(res, 200, { ok: true, path: p, size: stat.size, sha256: hash.digest("hex") });
        }

        // PC-side upload status (for gjenopptak): hvor langt er .tmp-filen?
        if (route === "GET /api/files/upload-status") {
          const name = path.basename(q.get("name") || "");
          if (!name || /[\\/:*?"<>|]/.test(name)) return json(res, 400, { error: "Ugyldig navn" });
          const tmp = path.join(p, name + ".ompdownload");
          let size = 0;
          try { size = fs.statSync(tmp).size; } catch {}
          return json(res, 200, { ok: true, size, sha256: "" });
        }

        // Mappeinnhold (uten totals numerisk)
        if (route === "GET /api/fs/list") {
          if (!stat.isDirectory()) return json(res, 400, { error: "Ikke en mappe" });
          const entries = fs.readdirSync(p, { withFileTypes: true }).map((entry) => {
            let size = 0;
            try { if (entry.isFile()) size = fs.statSync(path.join(p, entry.name)).size; } catch {}
            let mtime = 0;
            try { mtime = Math.round(fs.statSync(path.join(p, entry.name)).mtimeMs); } catch {}
            return { name: entry.name, type: entry.isDirectory() ? "dir" : "file", size, mtime };
          });
          return json(res, 200, { ok: true, path: p, entries });
        }

        // Fil- og mappe-informasjon for (enkel bruk) / ("GET /api/fs/tree" alias)
        if (route === "GET /api/fs/tree") {
          return json(res, 200, {
            ok: true,
            path: p,
            type: stat.isDirectory() ? "dir" : "file",
            size: stat.size,
            mtime: Math.round(stat.mtimeMs),
          });
        }
      }

      case "POST /api/fs/mkdir":
      case "POST /api/fs/rename":
      case "POST /api/fs/delete":
      case "POST /api/fs/copy":
      case "POST /api/fs/move": {
        const body = await readBody(req);
        if (route === "POST /api/fs/mkdir") {
          const dir = path.resolve(String(body.path || ""));
          if (!path.isAbsolute(dir)) return json(res, 400, { error: "Kun absolutte stier" });
          fs.mkdirSync(dir, { recursive: true });
          log("info", `Opprettet mappe ${dir}`);
          return json(res, 200, { ok: true, path: dir });
        }
        if (route === "POST /api/fs/rename") {
          const from = path.resolve(String(body.from || ""));
          const to = path.resolve(String(body.to || ""));
          if (!path.isAbsolute(from) || !path.isAbsolute(to)) return json(res, 400, { error: "Kun absolutte stier" });
          if (!fs.existsSync(from)) return json(res, 404, { error: `Fant ikke ${from}` });
          if (fs.existsSync(to)) return json(res, 409, { error: `Finnes allerede: ${to}` });
          fs.renameSync(from, to);
          return json(res, 200, { ok: true, from, to });
        }
        if (route === "POST /api/fs/delete") {
          const target = path.resolve(String(body.path || ""));
          if (!path.isAbsolute(target)) return json(res, 400, { error: "Kun absolutte stier" });
          if (protectedFsPath(target)) return json(res, 403, { error: "Stien er beskyttet (system-mappe)" });
          if (!fs.existsSync(target)) return json(res, 404, { error: `Fant ikke ${target}` });
          const root = path.parse(target).root;
          if (target === root) return json(res, 403, { error: "Kan ikke slette roten" });
          try {
            fs.rmSync(target, { recursive: true, force: false });
          } catch (error) {
            return json(res, 500, { error: error.message });
          }
          log("info", `Slettet ${target}`);
          return json(res, 200, { ok: true });
        }
        // copy / move kjører i bakgrunnen med fremskritt (SSE fs_job)
        const op = route.endsWith("/copy") ? "copy" : "move";
        const from = path.resolve(String(body.from || ""));
        const to = path.resolve(String(body.to || ""));
        if (!path.isAbsolute(from) || !path.isAbsolute(to)) return json(res, 400, { error: "Kun absolutte stier" });
        if (from === to) return json(res, 400, { error: "Kilde og mål er identisk" });
        if (!fs.existsSync(from)) return json(res, 404, { error: `Fant ikke ${from}` });
        fs.mkdirSync(to, { recursive: true });
        const id = crypto.randomBytes(6).toString("hex");
        fsJobs.set(id, { id, op, from, to, bytes: 0, total: 0, done: 0, error: "", started: Date.now() });
        runFsJob(id);
        return json(res, 200, { ok: true, id, op, from, to });
      }

      case "POST /api/fs/job": {
        const body = await readBody(req);
        const id = String(body.id || "");
        if (body.action === "cancel") {
          const job = fsJobs.get(id);
          if (job) { job.cancelled = true; }
          return json(res, 200, { ok: true });
        }
        const job = fsJobs.get(id);
        if (!job) return json(res, 404, { error: "Ukjent jobb" });
        return json(res, 200, { ok: true, job: { ...job } });
      }

      case "POST /api/files/upload": {
        // Telefon -> PC: rå byte-kropp (application/octet-stream), chunk-visning:
        // /api/files/upload?path=<target-dir>&name=<fil>.<ext>&offset=<N>&finalize=1
        const url = new URL(req.url, "http://localhost");
        const p = path.resolve(url.searchParams.get("path") || "");
        const name = path.basename(url.searchParams.get("name") || "");
        const offset = Number(url.searchParams.get("offset") || 0) || 0;
        const finalize = url.searchParams.get("finalize") === "1";
        if (!path.isAbsolute(p)) return json(res, 400, { error: "Kun absolutte stier" });
        if (!fs.existsSync(p)) return json(res, 404, { error: `Fant ikke ${p}` });
        if (!name || /[\\/:*?"<>|]/.test(name)) return json(res, 400, { error: "Ugyldig navn" });
        fs.mkdirSync(p, { recursive: true });
        const tmp = p + path.sep + name + ".ompdownload";
        if (offset === 0) {
          try { fs.rmSync(tmp, { force: true }); } catch {}
        }
        const hash = crypto.createHash("sha256");
        let written = 0;
        let missing = null;
        // Append at offset: les eksisterende del først for å beregne sha.
        if (offset > 0 && fs.existsSync(tmp)) {
          const existing = fs.statSync(tmp);
          if (existing.size > offset) {
            fs.truncateSync(tmp, offset);
          }
          if (existing.size === offset) {
            await new Promise((resolve) => {
              const stream = fs.createReadStream(tmp, { highWaterMark: 1024 * 1024 });
              stream.on("data", (chunk) => hash.update(chunk));
              stream.on("end", resolve);
              stream.on("error", () => { missing = true; resolve(); });
            });
            if (missing) { hash.reset && hash.reset(); }
          } else if (existing.size !== offset) {
            written = 0;
          }
        } else if (offset === 0) {
          // ny fil
        } else if (!fs.existsSync(tmp)) {
          return json(res, 409, { error: "Midlertidig fil mangler for gjenopptak; start på ny" });
        }
        await new Promise((resolve) => {
          const ws = fs.createWriteStream(tmp, { start: Math.min(offset, offset), flags: "r+" });
          let broken = false;
          try { fs.statSync(tmp); } catch { broken = true; }
          if (broken || offset === 0) {
            const fresh = fs.createWriteStream(tmp, { flags: "w+" });
            let seen = 0;
            req.on("data", (chunk) => { written += chunk.length; hash.update(chunk); fresh.write(chunk); });
            req.on("end", () => fresh.end(resolve));
            req.on("error", () => { try { fresh.end(); } catch {} resolve(); });
            return;
          }
          req.on("data", (chunk) => {
            written += chunk.length;
            hash.update(chunk);
            ws.write(chunk);
          });
          req.on("end", () => ws.end(resolve));
          req.on("error", () => { try { ws.end(); } catch {} resolve(); });
        });
        const size = fs.statSync(tmp).size;
        if (finalize) {
          const final = path.join(p, name);
          const overwrite = url.searchParams.get("overwrite") === "1";
          if (fs.existsSync(final) && !overwrite) {
            return json(res, 409, { error: "Finnes allerede", size, sha256: hash.digest("hex") });
          }
          fs.renameSync(tmp, final);
          log("info", `Fil mottatt: ${final} (${size} byte)`);
          return json(res, 200, { ok: true, path: final, size, sha256: hash.digest("hex"), verified: true });
        }
        return json(res, 200, { ok: true, path: tmp, size, sha256: hash.digest("hex"), offset });
      }

      default:
        return json(res, 404, { error: `Ukjent endepunkt: ${route}` });
    }
  } catch (error) {
    log("error", `${route}: ${error.message}`);
    return json(res, 500, { error: error.message });
  }
}

async function saveAttachments(cwd, files) {
  const dir = path.join(cwd, ".ompchat", "uploads");
  await fsp.mkdir(dir, { recursive: true });
  const saved = [];
  for (const file of files) {
    const name = String(file.name || "fil").replace(/[^a-zA-Z0-9._-]+/g, "_").slice(-80) || "fil";
    const unique = `${Date.now()}-${crypto.randomBytes(3).toString("hex")}-${name}`;
    const target = path.join(dir, unique);
    const data = String(file.data || "").replace(/^data:[^;]+;base64,/, "");
    await fsp.writeFile(target, Buffer.from(data, "base64"));
    saved.push({
      name,
      path: target,
      relative: path.relative(cwd, target).replace(/\\/g, "/"),
      bytes: fs.statSync(target).size,
    });
  }
  return saved;
}

// ---------------------------------------------------------------------------
// Server
// ---------------------------------------------------------------------------

// ===========================================================================
// Terminal (PTY over WebSocket) — Etappe 4
// ===========================================================================

const terminalSessions = new Map();

let nodePty = null;
try { nodePty = require("node-pty"); } catch (e) { nodePty = null; }

function terminalShellCommand(cwd) {
  if (process.platform === "win32") {
    return { command: "powershell.exe", args: ["-NoLogo", "-NoProfile"] };
  }
  // Use `script` to allocate a real PTY on Linux.
  if (fs.existsSync("/usr/bin/script")) {
    return { command: "/usr/bin/script", args: ["-q", "-c", "bash -i", "/dev/null"] };
  }
  return { command: "bash", args: ["-i"] };
}

function createTerminalSession(cwd, cols, rows) {
  const id = crypto.randomBytes(6).toString("hex");
  const c = cols || 80;
  const r = rows || 24;
  const workdir = cwd || os.homedir();
  const session = {
    id, child: null, pty: null, cwd: workdir, cols: c, rows: r,
    createdAt: Date.now(), alive: true, buffer: "",
    sockets: new Set(), usePty: false,
  };

  const push = (data) => {
    const text = Buffer.isBuffer(data) ? data.toString("utf8") : String(data);
    session.buffer += text;
    if (session.buffer.length > 200000) session.buffer = session.buffer.slice(-100000);
    for (const ws of session.sockets) {
      try { wsSendText(ws, text); } catch {}
    }
  };

  if (nodePty) {
    try {
      const shell = process.platform === "win32" ? "powershell.exe" : "bash";
      const args = process.platform === "win32" ? [] : ["-i"];
      const pty = nodePty.spawn(shell, args, {
        name: "xterm-256color",
        cols: c, rows: r,
        cwd: workdir,
        env: { ...process.env, TERM: "xterm-256color", COLORTERM: "truecolor" },
      });
      session.pty = pty;
      session.usePty = true;
      pty.onData(push);
      pty.onExit(({ exitCode }) => {
        session.alive = false;
        const msg = `\r\n[prosess avsluttet med kode ${exitCode}]\r\n`;
        session.buffer += msg;
        for (const ws of session.sockets) {
          try { wsSendText(ws, msg); wsClose(ws, 1000); } catch {}
        }
      });
      terminalSessions.set(id, session);
      return session;
    } catch (e) {
      nodePty = null;
    }
  }

  const { command, args } = terminalShellCommand(workdir);
  const child = spawn(command, args, {
    cwd: workdir,
    env: { ...process.env, TERM: "xterm-256color", COLORTERM: "truecolor" },
    stdio: ["pipe", "pipe", "pipe"],
  });
  session.child = child;
  child.stdout.on("data", push);
  child.stderr.on("data", push);
  child.on("exit", (code) => {
    session.alive = false;
    const msg = `\r\n[prosess avsluttet med kode ${code}]\r\n`;
    session.buffer += msg;
    for (const ws of session.sockets) {
      try { wsSendText(ws, msg); wsClose(ws, 1000); } catch {}
    }
  });
  child.on("error", (err) => {
    session.alive = false;
    const msg = `\r\n[feil: ${err.message}]\r\n`;
    session.buffer += msg;
    for (const ws of session.sockets) {
      try { wsSendText(ws, msg); wsClose(ws, 1011); } catch {}
    }
  });
  terminalSessions.set(id, session);
  return session;
}

function killTerminalSession(id) {
  const s = terminalSessions.get(id);
  if (!s) return false;
  try { if (s.pty) s.pty.kill(); else if (s.child) s.child.kill(); } catch {}
  s.alive = false;
  terminalSessions.delete(id);
  return true;
}

// ---- Minimal WebSocket (RFC 6455) implementation ----
function wsAcceptKey(key) {
  return crypto.createHash("sha1").update(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").digest("base64");
}

function wsParseFrame(buf) {
  if (buf.length < 2) return null;
  const first = buf[0];
  const fin = (first & 0x80) === 0x80;
  const opcode = first & 0x0f;
  const second = buf[1];
  const masked = (second & 0x80) === 0x80;
  let payloadLen = second & 0x7f;
  let offset = 2;
  if (payloadLen === 126) {
    if (buf.length < 4) return null;
    payloadLen = buf.readUInt16BE(2);
    offset = 4;
  } else if (payloadLen === 127) {
    if (buf.length < 10) return null;
    payloadLen = Number(buf.readBigUInt64BE(2));
    offset = 10;
  }
  let maskKey = null;
  if (masked) {
    if (buf.length < offset + 4) return null;
    maskKey = buf.slice(offset, offset + 4);
    offset += 4;
  }
  if (buf.length < offset + payloadLen) return null;
  const payload = Buffer.from(buf.slice(offset, offset + payloadLen));
  if (masked && maskKey) {
    for (let i = 0; i < payload.length; i++) payload[i] ^= maskKey[i % 4];
  }
  return { fin, opcode, payload, consumed: offset + payloadLen };
}

function wsSendFrame(socket, opcode, payload) {
  const buf = Buffer.isBuffer(payload) ? payload : Buffer.from(payload, "utf8");
  let header;
  if (buf.length < 126) {
    header = Buffer.alloc(2);
    header[0] = 0x80 | opcode;
    header[1] = buf.length;
  } else if (buf.length < 65536) {
    header = Buffer.alloc(4);
    header[0] = 0x80 | opcode;
    header[1] = 126;
    header.writeUInt16BE(buf.length, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x80 | opcode;
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(buf.length), 2);
  }
  socket.write(Buffer.concat([header, buf]));
}

function wsSendText(socket, text) { wsSendFrame(socket, 0x01, text); }
function wsSendBinary(socket, buf) { wsSendFrame(socket, 0x02, buf); }
function wsClose(socket, code) {
  const b = Buffer.alloc(2);
  b.writeUInt16BE(code || 1000, 0);
  try { wsSendFrame(socket, 0x08, b); } catch {}
  try { socket.end(); } catch {}
}

function handleWebSocket(req, socket) {
  const url = new URL(req.url, "http://localhost");
  if (url.pathname !== "/ws/terminal") {
    socket.write("HTTP/1.1 404 Not Found\r\n\r\n");
    socket.destroy();
    return;
  }
  const id = url.searchParams.get("id") || "";
  const session = terminalSessions.get(id);
  if (!session) {
    socket.write("HTTP/1.1 404 Not Found\r\n\r\n");
    socket.destroy();
    return;
  }
  const key = req.headers["sec-websocket-key"];
  if (!key) {
    socket.write("HTTP/1.1 400 Bad Request\r\n\r\n");
    socket.destroy();
    return;
  }
  const accept = wsAcceptKey(key);
  socket.write(
    "HTTP/1.1 101 Switching Protocols\r\n" +
    "Upgrade: websocket\r\n" +
    "Connection: Upgrade\r\n" +
    "Sec-WebSocket-Accept: " + accept + "\r\n\r\n"
  );
  socket.setNoDelay(true);

  // Send buffered output first
  if (session.buffer) {
    try { wsSendText(socket, session.buffer); } catch {}
  }

  session.sockets.add(socket);

  let inputBuffer = Buffer.alloc(0);
  socket.on("data", (chunk) => {
    inputBuffer = Buffer.concat([inputBuffer, chunk]);
    while (true) {
      const frame = wsParseFrame(inputBuffer);
      if (!frame) break;
      inputBuffer = inputBuffer.slice(frame.consumed);
      if (frame.opcode === 0x08) { // close
        try { wsClose(socket, 1000); } catch {}
        session.sockets.delete(socket);
        return;
      }
      if (frame.opcode === 0x09) { // ping
        try { wsSendFrame(socket, 0x0a, frame.payload); } catch {}
        continue;
      }
      if (frame.opcode === 0x0a) continue; // pong
      if (frame.opcode === 0x01 || frame.opcode === 0x02) {
        const text = frame.payload.toString("utf8");
        // Control messages: JSON with {type:"resize",cols,rows} or {type:"input",data}
        if (text.startsWith("{")) {
          try {
            const msg = JSON.parse(text);
            if (msg.type === "resize") {
              const cols = Number(msg.cols) || session.cols;
              const rows = Number(msg.rows) || session.rows;
              session.cols = cols;
              session.rows = rows;
              try { if (session.pty) session.pty.resize(cols, rows); } catch {}
              return;
            }
            if (msg.type === "input" && typeof msg.data === "string") {
              try { if (session.pty) session.pty.write(msg.data); else if (session.child && session.child.stdin.writable) session.child.stdin.write(msg.data); } catch {}
              return;
            }
          } catch {}
        }
        // raw input
        try {
          if (session.pty) session.pty.write(text);
          else if (session.child && session.child.stdin.writable) session.child.stdin.write(text);
        } catch {}
      }
    }
  });
  socket.on("close", () => { session.sockets.delete(socket); });
  socket.on("error", () => { session.sockets.delete(socket); });
}

// ---- HTTP routes for terminal (added into the main switch via marker) ----
// These are handled by checking pathname in the GET/POST switch below.

// ===========================================================================
// Git + file read/write (Etappe 6)
// ===========================================================================

function runGit(cwd, args, timeoutMs) {
  return new Promise((resolve) => {
    const child = spawn("git", args, { cwd, env: { ...process.env }, windowsHide: true });
    let stdout = "", stderr = "";
    const timer = setTimeout(() => { try { child.kill(); } catch {} }, timeoutMs || 60000);
    child.stdout.on("data", (d) => { stdout += d; });
    child.stderr.on("data", (d) => { stderr += d; });
    child.on("error", (e) => { clearTimeout(timer); resolve({ code: -1, stdout, stderr: e.message }); });
    child.on("close", (code) => { clearTimeout(timer); resolve({ code: code == null ? -1 : code, stdout, stderr }); });
  });
}

// Helper: parse `git status --porcelain=v2 -b` output
function parseGitStatus(stdout) {
  const lines = stdout.split(/\r?\n/).filter(Boolean);
  const result = { branch: "", upstream: "", ahead: 0, behind: 0, files: [] };
  for (const line of lines) {
    if (line.startsWith("# branch.head ")) {
      result.branch = line.slice("# branch.head ".length).trim();
    } else if (line.startsWith("# branch.upstream ")) {
      result.upstream = line.slice("# branch.upstream ".length).trim();
    } else if (line.startsWith("# branch.ab ")) {
      const m = line.match(/# branch\.ab \+(\d+) -(\d+)/);
      if (m) { result.ahead = Number(m[1]); result.behind = Number(m[2]); }
    } else if (line.startsWith("1 ") || line.startsWith("2 ")) {
      // ordinary/renamed: "1 XY origPath" or "2 XY origPath newPath"
      const parts = line.split(" ");
      // parts: type, XY, sub, mH, mI, mW, hH, hI, path (and newPath for rename)
      const xy = parts[1] || "";
      const path = parts.slice(8).join(" ").trim();
      const status = xy.trim();
      result.files.push({ path, status, staged: status[0] !== "." && status[0] !== " ", unstaged: status[1] !== "." && status[1] !== " " });
    } else if (line.startsWith("? ")) {
      result.files.push({ path: line.slice(2).trim(), status: "??", staged: false, unstaged: true });
    } else if (line.startsWith("! ")) {
      result.files.push({ path: line.slice(2).trim(), status: "!!", staged: false, unstaged: false });
    } else if (line.startsWith("u ")) {
      const parts = line.split(" ");
      const path = parts.slice(10).join(" ").trim();
      result.files.push({ path, status: "UU", staged: true, unstaged: true });
    }
  }
  return result;
}

const server = http.createServer((req, res) => {
  handle(req, res).catch((error) => {
    log("error", `unhandled: ${error.stack || error.message}`);
    try {
      json(res, 500, { error: error.message });
    } catch {}
  });
});

server.requestTimeout = 0;
server.headersTimeout = 30000;

server.on("upgrade", (req, socket, head) => {
  handleWebSocket(req, socket);
});

server.listen(CONFIG.port, CONFIG.host, () => {
  const hosts = lanAddresses();
  // PID file so the installer can stop exactly this bridge on upgrade/uninstall
  // (paths may be short 8.3 forms, which makes command-line matching unreliable).
  try {
    fs.writeFileSync(path.join(HERE, "bridge.pid"), String(process.pid));
  } catch {}
  // Discovery file for the optional OMP extension that reports the active TUI
  // session. Contains no secrets beyond the local pairing token.
  try {
    const dir = path.join(os.homedir(), ".ompchat");
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, "bridge.json"), JSON.stringify({
      url: `http://127.0.0.1:${CONFIG.port}`,
      token: CONFIG.token,
      port: CONFIG.port,
      updatedAt: Date.now(),
    }));
  } catch {}
  // Kill the resident voice worker when the bridge is shut down.
  for (const signal of ["SIGINT", "SIGTERM", "exit"]) {
    process.on(signal, () => {
      try { voiceWorker?.kill(); } catch {}
    });
  }
  log("info", `OmpChat bridge ${VERSION} listening on ${CONFIG.host}:${CONFIG.port}`);
  log("info", `session storage: ${SESSIONS_ROOT}`);
  log("info", `approval mode: ${CONFIG.approvalMode} · terminal: ${CONFIG.allowBash ? "enabled" : "disabled"}`);
  for (const host of hosts) log("info", `pairing: omp2://${host}:${CONFIG.port} (token: bridge/config.json)`);
  log("info", `QR: /ompqr i OMP, eller python tools/omp_qr.py --bridge ${hosts[0] || "127.0.0.1"}:${CONFIG.port}`);
});

async function shutdown(signal) {
  log("info", `${signal}: shutting down`);
  try {
    fs.rmSync(path.join(HERE, "bridge.pid"), { force: true });
  } catch {}
  server.close();
  await Promise.all([...runtimes.values()].map((runtime) => runtime.stop()));
  process.exit(0);
}
async function checkUpdates() {
  try {
    const res = await fetch("https://chat.nimay.net/nimayomp/update.json", { signal: AbortSignal.timeout(8000) });
    if (!res.ok) return;
    const data = await res.json();
    const v = data?.channels?.stable?.windowsBridge?.version || data?.channels?.stable?.linuxBridge?.version;
    if (v && v !== VERSION) {
      log("info", `Oppdatering tilgjengelig: bridge ${v} (nåværende ${VERSION}). Se https://chat.nimay.net/nimayomp/`);
    }
  } catch (e) {
    // offline or server unavailable — continue normally
  }
}
// run after startup
setTimeout(() => { checkUpdates().catch(() => {}); }, 5000);
// Periodic check every 6 hours
setInterval(() => { checkUpdates().catch(() => {}); }, 6 * 60 * 60 * 1000);
process.on("SIGTERM", () => shutdown("SIGTERM"));
