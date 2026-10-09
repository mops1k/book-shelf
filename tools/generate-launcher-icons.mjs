// Генератор launcher-иконок — единственный источник рисунка, без внешних зависимостей.
//
// Рисунок НЕ описывается кодом: мастер лежит файлом — `assets/icon/bookshelf-icon-512.png`
// (новый рисунок Александра с рабочего стола, 09.10.2026). Из него скрипт делает все выводы:
//   * растр API 24–25: `mipmap-*dpi/ic_launcher.png` (силуэт — скруглённый квадрат) и
//     `mipmap-*dpi/ic_launcher_round.png` (силуэт — круг); вне силуэта прозрачно;
//   * передний план адаптивной иконки API 26+: `mipmap-*dpi/ic_launcher_foreground.png` (108 dp);
//     фон — `@color/ic_launcher_background`, форму даёт маска лаунчера;
//   * адаптивные иконки `mipmap-anydpi-v26/ic_launcher{,_round}.xml` — тоже генерируемые.
//
// ПОЧЕМУ РАСТР, А НЕ ВЕКТОР. Раньше передний план был вектором (`drawable/ic_launcher_foreground.xml`),
// который рисовался из описания прямоугольниками `BOOK_RECTS`. У нового рисунка векторной версии нет:
// `bookshelf_icon.svg` с рабочего стола — обёртка вокруг встроенного растрового PNG (1213×658, ни
// одного `<path>`), её рендер совпадает с PNG побайтово. Поэтому источник — мастер-PNG, а передний
// план адаптива тоже растр.
//
// ПОЧЕМУ РИСУНОК ОДИН. 08.10.2026 Александр нашёл, что иконка в лаунчере не совпадает с PNG: на
// API 26+ система берёт адаптивную иконку, а это был второй, независимый рисунок. Теперь и растр, и
// передний план — один мастер в одном масштабе (`ART_SIDE` из 108, углы вписаны в видимый круг
// 72/108). Разной остаётся только внешняя форма: на API 24–25 её задаёт силуэт в растре, на API 26+ —
// маска лаунчера (так устроена адаптивная иконка).
import { writeFileSync, mkdirSync, rmSync, existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { deflateSync, inflateSync } from 'node:zlib';
import { createHash } from 'node:crypto';

// Корень вычисляется от расположения скрипта: жёсткий путь привязывал генератор
// к одной машине и ронял его в любом другом клоне репозитория.
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const MASTER_PATH = `${ROOT}/assets/icon/bookshelf-icon-512.png`;
const RES = `${ROOT}/app/src/main/res`;
const STALE_VECTOR = `${RES}/drawable/ic_launcher_foreground.xml`;

/** Холст иконки: система координат дизайна (108×108) и он же размер переднего плана в dp. */
const VIEWPORT = 108;
/** Видимая область адаптивной иконки: система показывает центральные 72 из 108. */
const VISIBLE = 72;
/**
 * Сторона рисунка в системе 108: углы вписаны в видимый круг (72/√2 = 50.91), поэтому ни круглая
 * маска, ни «капля», ни квадрат со скруглением не срежут край рамки рисунка.
 */
const ART_SIDE = VISIBLE / Math.SQRT2;
/** Силуэт растра для API 24–25 — тот же, что был у прежнего рисунка: отступ 4 %, скругление 20 %. */
const SILHOUETTE_MARGIN = 0.04;
const SILHOUETTE_CORNER = 0.2;
/** Радиус круглого силуэта растра — как у прежнего рисунка. */
const SILHOUETTE_RADIUS = 0.47;

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

/** Читает RGBA-PNG (8 бит на канал, без чересстрочности) в плоский массив пикселей. */
function readPng(path) {
  const buf = readFileSync(path);
  PNG_SIGNATURE.forEach((byte, i) => {
    if (buf[i] !== byte) throw new Error(`${path}: битая подпись PNG`);
  });

  let offset = PNG_SIGNATURE.length;
  let width = 0;
  let height = 0;
  const idat = [];
  while (offset + 8 <= buf.length) {
    const length = buf.readUInt32BE(offset);
    const type = buf.subarray(offset + 4, offset + 8).toString('ascii');
    const data = buf.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      if (data[8] !== 8) throw new Error(`${path}: ожидалась глубина 8 бит, а не ${data[8]}`);
      if (data[9] !== 6) throw new Error(`${path}: ожидался RGBA (color type 6), а не ${data[9]}`);
      if (data[12] !== 0) throw new Error(`${path}: чересстрочный PNG не поддерживается`);
    } else if (type === 'IDAT') {
      idat.push(data);
    } else if (type === 'IEND') {
      break;
    }
    offset += 12 + length;
  }
  if (width === 0 || height === 0) throw new Error(`${path}: нет IHDR`);

  const raw = inflateSync(Buffer.concat(idat));
  const stride = width * 4;
  if (raw.length !== (stride + 1) * height) throw new Error(`${path}: сжатые данные не того размера`);

  const pixels = Buffer.alloc(stride * height);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    const out = pixels.subarray(y * stride, (y + 1) * stride);
    const prev = y > 0 ? pixels.subarray((y - 1) * stride, y * stride) : null;
    for (let x = 0; x < stride; x++) {
      const a = x >= 4 ? out[x - 4] : 0;
      const b = prev === null ? 0 : prev[x];
      const c = prev !== null && x >= 4 ? prev[x - 4] : 0;
      let value = line[x];
      if (filter === 1) {
        value += a;
      } else if (filter === 2) {
        value += b;
      } else if (filter === 3) {
        value += (a + b) >> 1;
      } else if (filter === 4) {
        const p = a + b - c;
        const pa = Math.abs(p - a);
        const pb = Math.abs(p - b);
        const pc = Math.abs(p - c);
        value += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      } else if (filter !== 0) {
        throw new Error(`${path}: неизвестный фильтр PNG ${filter}`);
      }
      out[x] = value & 0xff;
    }
  }
  return { width, height, pixels };
}

/**
 * Пиксель мастера в координатах 0…1, билинейно и с учётом прозрачности.
 *
 * Цвет возвращается домноженным на прозрачность (`r`, `g`, `b` — premultiplied): иначе сглаженный
 * край рисунка давал бы тёмную кайму. `a` — прозрачность в долях. Вне рисунка — пусто (белый):
 * вызывающий код домешивает цвет к белому фону иконки.
 */
function masterSample(master, u, v) {
  if (u < 0 || u > 1 || v < 0 || v > 1) return { r: 0, g: 0, b: 0, a: 0 };
  const { width, height, pixels } = master;
  const x = u * (width - 1);
  const y = v * (height - 1);
  const x0 = Math.floor(x);
  const y0 = Math.floor(y);
  const x1 = Math.min(x0 + 1, width - 1);
  const y1 = Math.min(y0 + 1, height - 1);
  const fx = x - x0;
  const fy = y - y0;
  const at = (px, py) => {
    const i = (py * width + px) * 4;
    const a = pixels[i + 3] / 255;
    return [pixels[i] * a, pixels[i + 1] * a, pixels[i + 2] * a, a];
  };
  const p00 = at(x0, y0);
  const p10 = at(x1, y0);
  const p01 = at(x0, y1);
  const p11 = at(x1, y1);
  const mix = (k) =>
    (p00[k] * (1 - fx) + p10[k] * fx) * (1 - fy) + (p01[k] * (1 - fx) + p11[k] * fx) * fy;
  return { r: mix(0), g: mix(1), b: mix(2), a: mix(3) };
}

/** Доля силуэта в пикселе: 0 — снаружи, 1 — внутри, между — сглаженный край (4×4 подвыборки). */
function silhouetteCoverage(x, y, size, round) {
  const samples = 4;
  const margin = size * SILHOUETTE_MARGIN;
  const corner = size * SILHOUETTE_CORNER;
  const radius = size * SILHOUETTE_RADIUS;
  let hit = 0;
  for (let sy = 0; sy < samples; sy++) {
    for (let sx = 0; sx < samples; sx++) {
      const px = x + (sx + 0.5) / samples;
      const py = y + (sy + 0.5) / samples;
      if (round) {
        if (Math.hypot(px - size / 2, py - size / 2) <= radius) hit++;
        continue;
      }
      if (px < margin || px > size - margin || py < margin || py > size - margin) continue;
      const dx = Math.max(margin + corner - px, 0, px - (size - margin - corner));
      const dy = Math.max(margin + corner - py, 0, py - (size - margin - corner));
      if (dx * dx + dy * dy <= corner * corner) hit++;
    }
  }
  return hit / (samples * samples);
}

/**
 * Растр для API 24–25: белый силуэт (скруглённый квадрат или круг), на нём — мастер-рисунок.
 *
 * Силуэт принципиален: без него иконка занимает весь квадрат (`IconLauncherShape`), а круглая
 * версия выходит побайтово равной квадратной (`IconDuplicates`) — оба дефекта были до 08.10.2026.
 */
function drawLegacy(master, size, { round }) {
  const px = new Uint8Array(size * size * 4);
  const side = (ART_SIDE / VIEWPORT) * size;
  const offset = (size - side) / 2;
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const alpha = silhouetteCoverage(x, y, size, round);
      if (alpha === 0) continue;
      const art = masterSample(master, (x + 0.5 - offset) / side, (y + 0.5 - offset) / side);
      const i = (y * size + x) * 4;
      px[i] = Math.round(art.r + 255 * (1 - art.a));
      px[i + 1] = Math.round(art.g + 255 * (1 - art.a));
      px[i + 2] = Math.round(art.b + 255 * (1 - art.a));
      px[i + 3] = Math.round(alpha * 255);
    }
  }
  return px;
}

/**
 * Передний план адаптивной иконки (API 26+): мастер-рисунок в центре холста 108×108.
 *
 * Силуэта здесь нет намеренно: форму иконки задаёт маска лаунчера, фон — `@color/ic_launcher_background`.
 * Сглаженный край рисунка домешивается к белому: фон под ним всегда белый, иначе по краю была бы кайма.
 */
function drawAdaptive(master, size) {
  const px = new Uint8Array(size * size * 4);
  const side = (ART_SIDE / VIEWPORT) * size;
  const offset = (size - side) / 2;
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const art = masterSample(master, (x + 0.5 - offset) / side, (y + 0.5 - offset) / side);
      if (art.a === 0) continue;
      const i = (y * size + x) * 4;
      px[i] = Math.round(art.r + 255 * (1 - art.a));
      px[i + 1] = Math.round(art.g + 255 * (1 - art.a));
      px[i + 2] = Math.round(art.b + 255 * (1 - art.a));
      px[i + 3] = Math.round(art.a * 255);
    }
  }
  return px;
}

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
    Buffer.from(PNG_SIGNATURE),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function checkPng(buf, size) {
  PNG_SIGNATURE.forEach((byte, i) => {
    if (buf[i] !== byte) throw new Error('битая подпись PNG');
  });
  if (buf.readUInt32BE(16) !== size || buf.readUInt32BE(20) !== size) {
    throw new Error('неверные размеры в IHDR');
  }
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
  if (buf.subarray(buf.length - 8, buf.length - 4).toString('ascii') !== 'IEND') {
    throw new Error('нет IEND');
  }
}

/**
 * Мастер обязан быть тем рисунком: квадратным, не меньше 432 px (размер переднего плана на xxxhdpi),
 * с прозрачными углами и рамкой, которая касается середины каждой стороны.
 */
function checkMaster(master) {
  if (master.width !== master.height) throw new Error('мастер-рисунок не квадратный');
  if (master.width < 432) {
    throw new Error(`мастер ${master.width}×${master.height}: меньше 432 px, передний план xxxhdpi размоется`);
  }
  const { width, height, pixels } = master;
  const at = (x, y) => {
    const i = (y * width + x) * 4;
    return [pixels[i], pixels[i + 1], pixels[i + 2], pixels[i + 3]];
  };
  if (at(0, 0)[3] !== 0) throw new Error('угол мастера не прозрачный: у иконки не будет силуэта');
  const sides = [
    [Math.floor(width / 2), 2, 'верх'],
    [Math.floor(width / 2), height - 3, 'низ'],
    [2, Math.floor(height / 2), 'лево'],
    [width - 3, Math.floor(height / 2), 'право'],
  ];
  for (const [x, y, name] of sides) {
    const [r, g, b, a] = at(x, y);
    if (a < 128 || (r + g + b) / 3 > 128) {
      throw new Error(`мастер: рамка рисунка не найдена (${name}) — рисунок не тот или обрезан`);
    }
  }
}

/**
 * Ни один непрозрачный пиксель переднего плана не должен выйти за видимый круг 72/108: иначе
 * маска лаунчера срежет край рисунка. Возвращает самый дальний пиксель в единицах холста 108.
 */
function checkAdaptiveInVisibleCircle(pixels, size) {
  const centre = size / 2;
  const limit = ((VISIBLE / 2) / VIEWPORT) * size;
  let farthest = 0;
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if (pixels[(y * size + x) * 4 + 3] === 0) continue;
      const radius = Math.hypot(x + 0.5 - centre, y + 0.5 - centre);
      if (radius > farthest) farthest = radius;
    }
  }
  if (farthest > limit + 0.5 * (size / VIEWPORT)) {
    throw new Error(
      `передний план выходит за видимый круг: ${((farthest / size) * VIEWPORT).toFixed(1)} единиц при пределе ${VISIBLE / 2}`,
    );
  }
  return (farthest / size) * VIEWPORT;
}

/** Передний план не пустой: на месте левой стенки рамки рисунка должен быть тёмный пиксель. */
function checkFramePresent(pixels, size) {
  const side = (ART_SIDE / VIEWPORT) * size;
  const offset = (size - side) / 2;
  // Левая стенка рамки мастера занимает 0…3,3 % его ширины — берём 1,5 %.
  const x = Math.round(offset + 0.015 * side);
  const y = Math.round(size / 2);
  const i = (y * size + x) * 4;
  const [r, g, b, a] = [pixels[i], pixels[i + 1], pixels[i + 2], pixels[i + 3]];
  if (a < 128 || (r + g + b) / 3 > 128) {
    throw new Error('на переднем плане нет рамки рисунка — мастер не отрисован');
  }
}

const ADAPTIVE_ICON_XML = `<?xml version="1.0" encoding="utf-8"?>
<!--
  Сгенерировано tools/generate-launcher-icons.mjs — не правьте руками, правьте там.
  Адаптивная иконка (API 26+): белый фон и передний план — тот же рисунок, что и в
  mipmap-*dpi/ic_launcher{,_round}.png для API 24–25 (мастер: assets/icon/bookshelf-icon-512.png).
  Расхождение рисунков на версиях — дефект, найденный Александром 08.10.2026, поэтому источник один.
  Внешнюю форму иконки задаёт лаунчер: так устроена адаптивная иконка.
-->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
`;

const master = readPng(MASTER_PATH);
checkMaster(master);
const masterHash = createHash('sha256').update(readFileSync(MASTER_PATH)).digest('hex');
console.log(
  'мастер:',
  MASTER_PATH.replace(`${ROOT}/`, ''),
  `${master.width}×${master.height}`,
  `sha256 ${masterHash}`,
);
console.log(
  'рисунок:',
  `сторона ${ART_SIDE.toFixed(2)} из ${VIEWPORT} (углы вписаны в видимый круг ${VISIBLE})`,
);

const densities = [
  ['mdpi', 48, 1],
  ['hdpi', 72, 1.5],
  ['xhdpi', 96, 2],
  ['xxhdpi', 144, 3],
  ['xxxhdpi', 192, 4],
];

for (const [density, size, scale] of densities) {
  const dir = `${RES}/mipmap-${density}`;
  mkdirSync(dir, { recursive: true });

  const square = encodePng(size, drawLegacy(master, size, { round: false }));
  const round = encodePng(size, drawLegacy(master, size, { round: true }));
  checkPng(square, size);
  checkPng(round, size);
  // Круглая иконка обязана отличаться от квадратной. Одинаковые файлы — дефект `IconDuplicates`,
  // который и был до 08.10.2026: маска круга не давала разницы, потому что вне круга рисовался
  // такой же белый фон. Проверка стоит здесь, а не в голове: новая правка фона легко вернёт дефект.
  if (square.equals(round)) throw new Error(`иконки ${density} совпали: круглая маска не видна`);
  writeFileSync(`${dir}/ic_launcher.png`, square);
  writeFileSync(`${dir}/ic_launcher_round.png`, round);

  const foregroundSize = Math.round(VIEWPORT * scale);
  const foregroundPixels = drawAdaptive(master, foregroundSize);
  const foreground = encodePng(foregroundSize, foregroundPixels);
  checkPng(foreground, foregroundSize);
  const farthest = checkAdaptiveInVisibleCircle(foregroundPixels, foregroundSize);
  checkFramePresent(foregroundPixels, foregroundSize);
  writeFileSync(`${dir}/ic_launcher_foreground.png`, foreground);

  console.log(
    `mipmap-${density}`,
    `силуэты ${size}×${size} (квадрат ${square.length} Б, круг ${round.length} Б)`,
    `передний план ${foregroundSize}×${foregroundSize} → ${farthest.toFixed(1)}/${VISIBLE / 2} единиц`,
  );
}

for (const name of ['ic_launcher', 'ic_launcher_round']) {
  writeFileSync(`${RES}/mipmap-anydpi-v26/${name}.xml`, ADAPTIVE_ICON_XML);
}
console.log('mipmap-anydpi-v26: ic_launcher.xml, ic_launcher_round.xml', `@mipmap/ic_launcher_foreground`);

// Вектор переднего плана больше не нужен: у рисунка нет векторной версии. Оставленный файл был бы
// мёртвым ресурсом, а `UnusedResources` в политике lint поднят до ошибки.
if (existsSync(STALE_VECTOR)) {
  rmSync(STALE_VECTOR);
  console.log('удалён устаревший вектор', STALE_VECTOR.replace(`${ROOT}/`, ''));
}
