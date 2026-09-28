// Генератор launcher-иконок (PNG, 8-бит grayscale) без внешних зависимостей.
// Иконки нужны для API 24–25: адаптивные иконки (mipmap-anydpi-v26) там не поддерживаются.
import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { deflateSync, inflateSync } from 'node:zlib';

// Корень вычисляется от расположения скрипта: жёсткий путь привязывал генератор
// к одной машине и ронял его в любом другом клоне репозитория.
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', 'app', 'src', 'main', 'res');

// --- CRC32 ---
const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (const byte of buf) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crc]);
}

/** Собирает grayscale-PNG из массива яркостей (size×size). */
function encodePng(size, pixels) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 0; // color type: grayscale
  ihdr[10] = 0; // compression
  ihdr[11] = 0; // filter
  ihdr[12] = 0; // interlace

  const raw = Buffer.alloc((size + 1) * size);
  for (let y = 0; y < size; y++) {
    raw[y * (size + 1)] = 0; // filter type: none
    for (let x = 0; x < size; x++) raw[y * (size + 1) + 1 + x] = pixels[y * size + x];
  }

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/** Рисует силуэт книги: корешок + рамка обложки + три строки «текста». */
function drawIcon(size, { round }) {
  const px = new Uint8Array(size * size).fill(255); // белый фон
  const cx = size / 2;
  const cy = size / 2;
  const radius = size * 0.47;
  const t = Math.max(1, Math.round(size * 0.035)); // толщина штриха

  const inRect = (x, y, x0, y0, x1, y1) =>
    x >= Math.round(x0 * size) && x <= Math.round(x1 * size) && y >= Math.round(y0 * size) && y <= Math.round(y1 * size);

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if (round) {
        const dx = x + 0.5 - cx;
        const dy = y + 0.5 - cy;
        if (Math.sqrt(dx * dx + dy * dy) > radius) {
          px[y * size + x] = 255;
          continue;
        }
      }

      let black = false;

      // корешок
      if (inRect(x, y, 0.28, 0.2, 0.35, 0.8)) black = true;
      // верхняя и нижняя кромка обложки
      if (inRect(x, y, 0.35, 0.2, 0.76, 0.2 + t / size)) black = true;
      if (inRect(x, y, 0.35, 0.8 - t / size, 0.76, 0.8)) black = true;
      // правая кромка
      if (inRect(x, y, 0.76 - t / size, 0.2, 0.76, 0.8)) black = true;
      // строки «текста»
      if (inRect(x, y, 0.42, 0.34, 0.68, 0.34 + t / size)) black = true;
      if (inRect(x, y, 0.42, 0.46, 0.68, 0.46 + t / size)) black = true;
      if (inRect(x, y, 0.42, 0.58, 0.60, 0.58 + t / size)) black = true;

      if (black) px[y * size + x] = 0;
    }
  }
  return px;
}

function checkPng(buf, size) {
  const signature = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
  signature.forEach((b, i) => {
    if (buf[i] !== b) throw new Error('битая подпись PNG');
  });
  if (buf.readUInt32BE(16) !== size || buf.readUInt32BE(20) !== size) throw new Error('неверные размеры в IHDR');
  const idatStart = buf.indexOf(Buffer.from('IDAT', 'ascii'));
  const len = buf.readUInt32BE(idatStart - 4);
  const inflated = inflateSync(buf.subarray(idatStart + 4, idatStart + 4 + len));
  if (inflated.length !== (size + 1) * size) throw new Error('несжатые данные не того размера');
  if (buf.subarray(buf.length - 8, buf.length - 4).toString('ascii') !== 'IEND') throw new Error('нет IEND');
}

const densities = [
  ['mdpi', 48],
  ['hdpi', 72],
  ['xhdpi', 96],
  ['xxhdpi', 144],
  ['xxxhdpi', 192],
];

for (const [density, size] of densities) {
  for (const [name, round] of [['ic_launcher', false], ['ic_launcher_round', true]]) {
    const dir = `${ROOT}/mipmap-${density}`;
    mkdirSync(dir, { recursive: true });
    const png = encodePng(size, drawIcon(size, { round }));
    checkPng(png, size);
    writeFileSync(`${dir}/${name}.png`, png);
    console.log(`${dir}/${name}.png`, `${size}x${size}`, `${png.length} байт`);
  }
}
