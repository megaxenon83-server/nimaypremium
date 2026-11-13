// Small local HTTP server that publishes the Android APK on the LAN so the
// phone can download it straight from this PC (no cloud upload).
//
//   node apk-server.mjs [--app <installDir>] [--port 7787]
import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import QRCode from "qrcode";

function arg(name, fallback) {
  const index = process.argv.indexOf(name);
  return index > 0 ? process.argv[index + 1] : fallback;
}

const HERE = path.dirname(fileURLToPath(import.meta.url));
const appDir = path.resolve(arg("--app", path.join(HERE, "..")));
const port = Number(arg("--port", 7787)) || 7787;
const apk = path.join(appDir, "apk", "OmpChat.apk");

function primaryAddress() {
  const scored = [];
  for (const list of Object.values(os.networkInterfaces())) {
    for (const entry of list || []) {
      if (entry.family !== "IPv4" || entry.internal) continue;
      if (/^169\.254\./.test(entry.address)) continue;
      const score = /^192\.168\./.test(entry.address) || /^10\./.test(entry.address) ? 3
        : /^172\.(1[6-9]|2\d|3[01])\./.test(entry.address) ? 2
        : /^100\./.test(entry.address) ? 1 : 0;
      scored.push({ address: entry.address, score });
    }
  }
  scored.sort((a, b) => b.score - a.score);
  return (scored[0] || { address: "127.0.0.1" }).address;
}

const host = primaryAddress();

function qrSvg(text, size = 240) {
  try {
    const qr = QRCode.create(text, { errorCorrectionLevel: "M" });
    const modules = qr.modules.size;
    const data = qr.modules.data;
    let rects = "";
    for (let y = 0; y < modules; y++) {
      for (let x = 0; x < modules; x++) {
        if (data[y * modules + x]) rects += `<rect x="${x}" y="${y}" width="1" height="1"/>`;
      }
    }
    const total = modules + 8;
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${total} ${total}" shape-rendering="crispEdges">` +
      `<rect width="${total}" height="${total}" fill="#ffffff"/>` +
      `<g transform="translate(4,4)" fill="#0b1220">${rects}</g></svg>`;
  } catch {
    return "";
  }
}

function page(host, port) {
  const size = fs.existsSync(apk) ? (fs.statSync(apk).size / 1024 / 1024).toFixed(1) : "?";
  const url = `http://${host}:${port}/apk/OmpChat.apk`;
  return `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Nimay OMP Mobile</title>
<style>
body{background:#080F1E;color:#F1F7FF;font-family:system-ui,sans-serif;margin:0;padding:32px;display:flex;flex-direction:column;gap:18px;max-width:520px}
h1{font-size:22px;margin:0}p{color:#A2B4CF;line-height:1.5;margin:0}
a{display:block;text-align:center;background:#168BFF;color:#fff;text-decoration:none;padding:16px;border-radius:16px;font-weight:600;font-size:17px}
.qr{background:#fff;padding:12px;border-radius:16px;width:max-content}
code{color:#38D9FF;word-break:break-all}
small{color:#7E93B3}
</style>
<h1>Nimay OMP Mobile</h1>
<p>Skann QR-koden med telefonen for a laste ned Android-appen (${size} MB) direkte fra denne PC-en.</p>
<div class="qr">${qrSvg(url)}</div>
<p>Adresse: <code>${url}</code></p>
<a href="/apk/OmpChat.apk" download>Last ned OmpChat.apk</a>
<p><small>Telefonen ma vaere pa samme nettverk (LAN eller Tailscale). Etter nedlasting: apne filen og tillat installasjon fra denne kilden.</small></p>`;
}

const server = http.createServer((request, response) => {
  const url = new URL(request.url, `http://${host}`);
  if (url.pathname === "/health") {
    response.writeHead(200, { "content-type": "application/json" });
    response.end(JSON.stringify({ ok: true, apk: fs.existsSync(apk), bytes: fs.existsSync(apk) ? fs.statSync(apk).size : 0 }));
    return;
  }
  if (url.pathname === "/apk/OmpChat.apk") {
    if (!fs.existsSync(apk)) {
      response.writeHead(404, { "content-type": "text/plain; charset=utf-8" });
      response.end("APK mangler. Kjor installasjonen pa nytt.");
      return;
    }
    const stat = fs.statSync(apk);
    const headers = {
      "content-type": "application/vnd.android.package-archive",
      "content-length": stat.size,
      "content-disposition": 'attachment; filename="OmpChat.apk"',
      "cache-control": "no-store",
    };
    if (request.method === "HEAD") {
      response.writeHead(200, headers);
      response.end();
      return;
    }
    response.writeHead(200, headers);
    fs.createReadStream(apk).pipe(response);
    return;
  }
  if (url.pathname === "/" || url.pathname === "/index.html") {
    const body = page(host, port);
    response.writeHead(200, { "content-type": "text/html; charset=utf-8", "content-length": Buffer.byteLength(body) });
    response.end(body);
    return;
  }
  response.writeHead(404, { "content-type": "text/plain" });
  response.end("not found");
});

server.listen(port, "0.0.0.0", () => {
  try {
    fs.writeFileSync(path.join(HERE, "apk-server.pid"), String(process.pid));
  } catch (error) {
    console.error("pid file failed:", error.message, "HERE:", HERE);
  }
  console.log(`APK server: http://${host}:${port}/apk/OmpChat.apk`);
  console.log(`APK exists: ${fs.existsSync(apk)}`);
});

