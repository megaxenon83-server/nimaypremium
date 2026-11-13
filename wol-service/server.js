const http = require("http");
const fs = require("fs");
const path = require("path");
const dgram = require("dgram");

const PORT = parseInt(process.env.WOL_PORT || "8790", 10);
const TOKEN = process.env.WOL_API_TOKEN || "";
const DATA_FILE = process.env.WOL_DATA_FILE || "/data/pcs.json";

function load() {
  try { return JSON.parse(fs.readFileSync(DATA_FILE, "utf8")); }
  catch (e) { return { pcs: [] }; }
}
function save(data) {
  fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true });
  fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2));
}

function sendWol(mac, broadcast, port) {
  console.log("sendWol called mac=", mac, "broadcast=", broadcast, "port=", port);
  const clean = String(mac).replace(/[:\-\.]/g, "").trim();
  if (clean.length !== 12 || !/^[0-9a-fA-F]+$/.test(clean)) {
    console.error("sendWol invalid mac", clean);
    return false;
  }
  const macBytes = Buffer.from(clean, "hex");
  const packet = Buffer.alloc(6 + 16 * 6);
  for (let i = 0; i < 6; i++) packet[i] = 0xFF;
  for (let i = 0; i < 16; i++) macBytes.copy(packet, 6 + i * 6);
  const bcast = broadcast || "255.255.255.255";
  const p = port || 9;
  function sendOne(target, cb) {
    const sock = dgram.createSocket("udp4");
    sock.on("error", (e) => { console.error("sock error", e && e.message); try { sock.close(); } catch (x) {} if (cb) cb(); });
    sock.bind(0, "0.0.0.0", () => {
      try { sock.setBroadcast(true); } catch (e) { console.error("setBroadcast", e && e.message); }
      sock.send(packet, p, target, (err) => {
        if (err) console.error("send err", err && err.message);
        try { sock.close(); } catch (x) {}
        if (cb) cb();
      });
    });
  }
  try {
    if (bcast !== "255.255.255.255") {
      sendOne(bcast, null);
    }
    sendOne("255.255.255.255", null);
    return true;
  } catch (e) {
    console.error("sendWol error:", e && e.message);
    return false;
  }
}

function isOnline(host, port, timeoutMs) {
  return new Promise((resolve) => {
    if (!host) return resolve(false);
    const net = require("net");
    const sock = new net.Socket();
    let done = false;
    const finish = (v) => { if (done) return; done = true; try { sock.destroy(); } catch (e) {} resolve(v); };
    sock.setTimeout(timeoutMs || 3000);
    sock.on("connect", () => finish(true));
    sock.on("timeout", () => finish(false));
    sock.on("error", () => finish(false));
    try { sock.connect(port || 7777, host); } catch (e) { finish(false); }
  });
}

function auth(req) {
  if (!TOKEN) return true;
  const h = req.headers["authorization"] || "";
  return h === "Bearer " + TOKEN;
}

function json(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(data) });
  res.end(data);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", (c) => { data += c; if (data.length > 1e6) { reject(new Error("too large")); req.destroy(); } });
    req.on("end", () => resolve(data));
    req.on("error", reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost");
  const pathname = url.pathname;

  if (pathname === "/health") {
    return json(res, 200, { ok: true, service: "nimay-wol", version: "1.0.0" });
  }

  if (!auth(req)) return json(res, 401, { error: "Unauthorized" });

  try {
    if (pathname === "/pcs" && req.method === "GET") {
      const data = load();
      // enrich with online status
      const pcs = await Promise.all(data.pcs.map(async (p) => {
        const online = await isOnline(p.ip || p.host, p.port || 7777, 3000);
        return { ...p, online };
      }));
      return json(res, 200, { pcs });
    }
    if (pathname === "/pcs" && req.method === "POST") {
      const body = JSON.parse(await readBody(req) || "{}");
      const data = load();
      const pc = {
        id: body.id || (Date.now().toString(36) + Math.random().toString(36).slice(2, 8)),
        name: body.name || "PC",
        mac: body.mac || "",
        ip: body.ip || body.host || "",
        host: body.host || body.ip || "",
        port: body.port || 7777,
        broadcast: body.broadcast || "255.255.255.255",
        wolPort: body.wolPort || 9,
      };
      data.pcs.push(pc);
      save(data);
      return json(res, 201, { ok: true, pc });
    }
    const m = pathname.match(/^\/pcs\/([^\/]+)$/);
    if (m) {
      const id = decodeURIComponent(m[1]);
      const data = load();
      const idx = data.pcs.findIndex((p) => p.id === id);
      if (idx < 0) return json(res, 404, { error: "Not found" });
      if (req.method === "GET") {
        const p = data.pcs[idx];
        const online = await isOnline(p.ip || p.host, p.port || 7777, 3000);
        return json(res, 200, { pc: { ...p, online } });
      }
      if (req.method === "PUT") {
        const body = JSON.parse(await readBody(req) || "{}");
        data.pcs[idx] = { ...data.pcs[idx], ...body, id };
        save(data);
        return json(res, 200, { ok: true, pc: data.pcs[idx] });
      }
      if (req.method === "DELETE") {
        data.pcs.splice(idx, 1);
        save(data);
        return json(res, 200, { ok: true });
      }
    }
    const w = pathname.match(/^\/pcs\/([^\/]+)\/wake$/);
    if (w && req.method === "POST") {
      const id = decodeURIComponent(w[1]);
      const data = load();
      const pc = data.pcs.find((p) => p.id === id);
      if (!pc) return json(res, 404, { error: "Not found" });
      const ok = sendWol(pc.mac, pc.broadcast, pc.wolPort);
      return json(res, 200, { ok, id, message: ok ? "WOL packet sent" : "Invalid MAC or send failed" });
    }
    return json(res, 404, { error: "Not found" });
  } catch (e) {
    return json(res, 500, { error: e.message });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log("nimay-wol listening on " + PORT);
});
