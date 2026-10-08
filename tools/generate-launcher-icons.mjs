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

/** Собирает RGBA-PNG (8 бит на канал) из массива пикселей (size×size×4). */
function encodePng(size, pixels) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  // RGBA, а не grayscale: прозрачность нужна, чтобы у иконки был силуэт. Непрозрачная иконка
  // «занимает весь квадрат» — это дефект `IconLauncherShape` (был до 08.10.2026).
  ihdr[9] = 6; // color type: truecolour with alpha
  ihdr[10] = 0; // compression
  ihdr[11] = 0; // filter
  ihdr[12] = 0; // interlace

  const stride = size * 4;
  const raw = Buffer.alloc((stride + 1) * size);
  for (let y = 0; y < size; y++) {
    raw[y * (stride + 1)] = 0; // filter type: none
    for (let x = 0; x < stride; x++) raw[y * (stride + 1) + 1 + x] = pixels[y * stride + x];
  }

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/**
 * Рисует иконку: силуэт (скруглённый квадрат или круг) и чёрный корешок книги на нём.
 *
 * Силуэт принципиален: до 08.10.2026 фон закрашивал весь квадрат, поэтому у иконки не было
 * очертаний (`IconLauncherShape`), а флаг `round` не давал видимого круга — вне круга рисовался
 * такой же белый, и круглая иконка выходила побайтово равной квадратной (`IconDuplicates`).
 * Теперь вне силуэта — прозрачность, поэтому квадратная и круглая версии разные.
 *
 * Край силуэта сглаживается 4×4 подвыборками: без этого круг и скругления были бы рваными.
 */
function drawIcon(size, { round }) {
  const px = new Uint8Array(size * size * 4); // RGBA: по умолчанию всё прозрачно
  const cx = size / 2;
  const cy = size / 2;
  const radius = size * 0.47;
  const margin = size * 0.04;
  const corner = size * 0.2;
  const t = Math.max(1, Math.round(size * 0.035)); // толщина штриха

  const inRect = (x, y, x0, y0, x1, y1) =>
    x >= Math.round(x0 * size) && x <= Math.round(x1 * size) && y >= Math.round(y0 * size) && y <= Math.round(y1 * size);

  const inRoundedRect = (x, y) => {
    const left = margin;
    const top = margin;
    const right = size - margin;
    const bottom = size - margin;
    if (x < left || x > right || y < top || y > bottom) return false;
    const dx = Math.max(left + corner - x, 0, x - (right - corner));
    const dy = Math.max(top + corner - y, 0, y - (bottom - corner));
    return dx * dx + dy * dy <= corner * corner;
  };

  /** Доля силуэта в пикселе: 0 — снаружи, 1 — внутри, между — сглаженный край. */
  const coverage = (x, y) => {
    const samples = 4;
    let hit = 0;
    for (let sy = 0; sy < samples; sy++) {
      for (let sx = 0; sx < samples; sx++) {
        const sampleX = x + (sx + 0.5) / samples;
        const sampleY = y + (sy + 0.5) / samples;
        if (round) {
          const dx = sampleX - cx;
          const dy = sampleY - cy;
          if (Math.sqrt(dx * dx + dy * dy) <= radius) hit++;
        } else if (inRoundedRect(sampleX, sampleY)) {
          hit++;
        }
      }
    }
    return hit / (samples * samples);
  };

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const alpha = Math.round(coverage(x, y) * 255);
      if (alpha === 0) continue;

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

      const value = black ? 0 : 255;
      const i = (y * size + x) * 4;
      px[i] = value;
      px[i + 1] = value;
      px[i + 2] = value;
      px[i + 3] = alpha;
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
  if (buf[25] !== 6) throw new Error('ожидался RGBA-PNG (color type 6)');
  const idatStart = buf.indexOf(Buffer.from('IDAT', 'ascii'));
  const len = buf.readUInt32BE(idatStart - 4);
  const inflated = inflateSync(buf.subarray(idatStart + 4, idatStart + 4 + len));
  const stride = size * 4;
  if (inflated.length !== (stride + 1) * size) throw new Error('несжатые данные не того размера');
  // Угол обязан быть прозрачным: именно прозрачность даёт силуэт, из-за отсутствия которого lint
  // ругался `IconLauncherShape` (иконка занимала весь квадрат).
  const cornerAlpha = inflated[1 + 3];
  if (cornerAlpha !== 0) throw new Error(`угол иконки не прозрачный (alpha=${cornerAlpha})`);
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
  const dir = `${ROOT}/mipmap-${density}`;
  mkdirSync(dir, { recursive: true });
  const square = encodePng(size, drawIcon(size, { round: false }));
  const round = encodePng(size, drawIcon(size, { round: true }));
  checkPng(square, size);
  checkPng(round, size);
  // Круглая иконка обязана отличаться от квадратной. Одинаковые файлы — дефект `IconDuplicates`,
  // который и был до 08.10.2026: маска круга не давала разницы, потому что вне круга рисовался
  // такой же белый фон. Проверка стоит здесь, а не в голове: новая правка фона легко вернёт дефект.
  if (square.equals(round)) throw new Error(`иконки ${density} совпали: круглая маска не видна`);
  writeFileSync(`${dir}/ic_launcher.png`, square);
  writeFileSync(`${dir}/ic_launcher_round.png`, round);
  console.log(`mipmap-${density}`, `${size}x${size}`, `квадрат ${square.length} байт, круг ${round.length} байт`);
}
