// Writes a QR code as an uncompressed 24-bit BMP so the Inno Setup wizard can
// display it without extra imaging libraries.
//
//   node qrgen.mjs "https://example" out.bmp [scale]
import QRCode from "qrcode";
import fs from "node:fs";

const [text, outFile, scaleArg] = process.argv.slice(2);
if (!text || !outFile) {
  console.error("usage: node qrgen.mjs <text> <out.bmp> [scale]");
  process.exit(2);
}

const scale = Math.max(2, Number(scaleArg) || 8);
const quiet = 4;
const qr = QRCode.create(text, { errorCorrectionLevel: "M" });
const modules = qr.modules.size;
const data = qr.modules.data;
const px = (modules + quiet * 2) * scale;

const rowBytes = Math.ceil((px * 3) / 4) * 4;
const pixelBytes = rowBytes * px;
const fileSize = 54 + pixelBytes;
const buffer = Buffer.alloc(fileSize, 0xff);

// BITMAPFILEHEADER
buffer.write("BM", 0, "ascii");
buffer.writeUInt32LE(fileSize, 2);
buffer.writeUInt32LE(54, 10);
// BITMAPINFOHEADER
buffer.writeUInt32LE(40, 14);
buffer.writeInt32LE(px, 18);
buffer.writeInt32LE(px, 22);
buffer.writeUInt16LE(1, 26);
buffer.writeUInt16LE(24, 28);
buffer.writeUInt32LE(0, 30);
buffer.writeUInt32LE(pixelBytes, 34);
buffer.writeInt32LE(2835, 38);
buffer.writeInt32LE(2835, 42);
buffer.writeUInt32LE(0, 46); // biClrUsed
buffer.writeUInt32LE(0, 50); // biClrImportant

for (let my = 0; my < px; my++) {
  // BMP rows are stored bottom-up.
  const moduleY = Math.floor(my / scale) - quiet;
  const rowOffset = 54 + (px - 1 - my) * rowBytes;
  for (let mx = 0; mx < px; mx++) {
    const moduleX = Math.floor(mx / scale) - quiet;
    const dark =
      moduleY >= 0 &&
      moduleY < modules &&
      moduleX >= 0 &&
      moduleX < modules &&
      data[moduleY * modules + moduleX];
    if (!dark) continue;
    const offset = rowOffset + mx * 3;
    buffer[offset] = 0x14;
    buffer[offset + 1] = 0x14;
    buffer[offset + 2] = 0x14;
  }
}

fs.writeFileSync(outFile, buffer);
console.log(JSON.stringify({ ok: true, file: outFile, pixels: px, modules }));
