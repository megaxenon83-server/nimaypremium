// Generates installer\assets\nimay.ico (64x64, 32bpp) without external deps.
import fs from "node:fs";
import path from "node:path";

const size = 64;
const px = new Uint8Array(size * size * 4); // RGBA

function set(x, y, [r, g, b], a = 255) {
  if (x < 0 || y < 0 || x >= size || y >= size) return;
  const i = (y * size + x) * 4;
  px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = a;
}

const bg = [13, 25, 43];
const border = [22, 139, 255];
const mark = [56, 217, 255];

const radius = 14;
for (let y = 0; y < size; y++) {
  for (let x = 0; x < size; x++) {
    const dx = Math.min(x, size - 1 - x);
    const dy = Math.min(y, size - 1 - y);
    let inside = true;
    if (dx < radius && dy < radius) {
      const cx = radius - dx;
      const cy = radius - dy;
      inside = Math.hypot(cx, cy) <= radius;
    }
    if (!inside) continue;
    const edge = dx < 3 || dy < 3;
    set(x, y, edge ? border : bg);
  }
}

// Letter "N": two vertical bars and a diagonal.
for (let y = 18; y <= 45; y++) {
  for (let x = 18; x <= 45; x++) {
    const leftBar = x >= 18 && x <= 24;
    const rightBar = x >= 39 && x <= 45;
    const t = (y - 18) / 27;
    const diagonalX = 21 + t * 21;
    const diagonal = Math.abs(x - diagonalX) <= 3.2;
    if (leftBar || rightBar || diagonal) set(x, y, mark);
  }
}

// Encode as ICO with a single 64x64 32bpp DIB.
const rowBytes = size * 4;
const maskRowBytes = Math.ceil(size / 8 / 4) * 4;
const imageSize = 40 + rowBytes * size + maskRowBytes * size;
const file = Buffer.alloc(6 + 16 + imageSize);

file.writeUInt16LE(0, 0);
file.writeUInt16LE(1, 2);
file.writeUInt16LE(1, 4);
file.writeUInt8(size, 6);
file.writeUInt8(size, 7);
file.writeUInt8(0, 8);
file.writeUInt8(0, 9);
file.writeUInt16LE(1, 10);
file.writeUInt16LE(32, 12);
file.writeUInt32LE(imageSize, 14);
file.writeUInt32LE(22, 18);

const dib = 22;
file.writeUInt32LE(40, dib);
file.writeInt32LE(size, dib + 4);
file.writeInt32LE(size * 2, dib + 8);
file.writeUInt16LE(1, dib + 12);
file.writeUInt16LE(32, dib + 14);
file.writeUInt32LE(imageSize - 40, dib + 20);

const pixels = dib + 40;
for (let y = 0; y < size; y++) {
  const srcY = size - 1 - y; // bottom-up
  for (let x = 0; x < size; x++) {
    const s = (srcY * size + x) * 4;
    const d = pixels + y * rowBytes + x * 4;
    file[d] = px[s + 2];
    file[d + 1] = px[s + 1];
    file[d + 2] = px[s];
    file[d + 3] = px[s + 3];
  }
}

const outDir = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1")), "..", "assets");
fs.mkdirSync(outDir, { recursive: true });
const out = path.join(outDir, "nimay.ico");
fs.writeFileSync(out, file);
console.log(`wrote ${out} (${file.length} bytes)`);
