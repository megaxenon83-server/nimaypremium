// Prints the LAN URLs a phone can use, without leaking the pairing token to
// stdout (the token is only embedded in the QR payload).
//
//   node apk-url.mjs [--app <installDir>] [--apk-port 7787] [--bridge-port 7777]
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

function arg(name, fallback) {
  const index = process.argv.indexOf(name);
  return index > 0 ? process.argv[index + 1] : fallback;
}

const HERE = path.dirname(fileURLToPath(import.meta.url));
const appDir = path.resolve(arg("--app", path.join(HERE, "..")));
const apkPort = Number(arg("--apk-port", 7787));
const bridgePort = Number(arg("--bridge-port", 7777));

function score(address) {
  if (/^192\.168\./.test(address)) return 3;
  if (/^10\./.test(address)) return 3;
  if (/^172\.(1[6-9]|2\d|3[01])\./.test(address)) return 2;
  if (/^100\./.test(address)) return 1; // Tailscale CGNAT
  return 0;
}

const candidates = [];
for (const [name, list] of Object.entries(os.networkInterfaces())) {
  for (const entry of list || []) {
    if (entry.family !== "IPv4" || entry.internal) continue;
    if (/^169\.254\./.test(entry.address)) continue;
    candidates.push({ name, address: entry.address, score: score(entry.address) });
  }
}
candidates.sort((a, b) => b.score - a.score);

const primary = candidates[0] || { address: "127.0.0.1", name: "loopback" };

let token = "";
for (const file of [path.join(appDir, "bridge", "config.json"), path.join(appDir, "config.json")]) {
  try {
    token = JSON.parse(fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "")).token || "";
    if (token) break;
  } catch {}
}

const port = Number(process.env.OMPCHAT_BRIDGE_PORT) || bridgePort;
const apkUrl = `http://${primary.address}:${apkPort}/apk/OmpChat.apk`;
const pairing = token ? `omp2://${primary.address}:${port}?token=${token}` : "";

if (process.argv.includes("--plain")) {
  console.log(`HOST=${primary.address}`);
  console.log(`APK_URL=${apkUrl}`);
  console.log(`PAIRING=${pairing}`);
  console.log(`APK_PORT=${apkPort}`);
  console.log(`BRIDGE_PORT=${port}`);
  console.log(`ADDRESSES=${candidates.map((c) => c.address).join(",")}`);
} else {
  console.log(JSON.stringify({
    apkUrl,
    pairing,
    host: primary.address,
    interface: primary.name,
    apkPort,
    bridgePort: port,
    addresses: candidates.map((c) => c.address),
  }, null, 2));
}
