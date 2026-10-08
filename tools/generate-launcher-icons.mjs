// Генератор launcher-иконок — единственный источник рисунка, без внешних зависимостей.
//
// Пишет два вывода из одного описания книги (`BOOK_RECTS`):
//   * растр RGBA в `mipmap-*dpi/ic_launcher{,_round}.png` — нужен для API 24–25, где адаптивных
//     иконок ещё нет (mipmap-anydpi-v26 не поддерживается);
//   * вектор `drawable/ic_launcher_foreground.xml` — передний план адаптивной иконки для API 26+.
//
// Раньше это были ДВА независимых рисунка: в векторе не было правой кромки обложки, а строки
// доходили до неё, в PNG кромка была и пропорции были другие (0.2…0.8 против 34…74 из 108).
// Александр заметил расхождение 08.10.2026: на устройстве (Android 14) показывался вектор, а PNG
// он видел в отчёте. Теперь рисунок один, и разойтись они не могут: оба вывода строятся из
// `BOOK_RECTS` ниже, а прогон проверяет, что в векторе ровно те же прямоугольники.
import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { deflateSync, inflateSync } from 'node:zlib';

// Корень вычисляется от расположения скрипта: жёсткий путь привязывал генератор
// к одной машине и ронял его в любом другом клоне репозитория.
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', 'app', 'src', 'main', 'res');

/**
 * Рисунок книги: прямоугольники `[x0, y0, x1, y1]` в системе холста 108×108 (как viewport вектора).
 *
 * Размер 34…74 из 108 (0.315…0.685) выбран по безопасной зоне адаптивной иконки: система
 * показывает центральные 66 из 108 и срезает остальное под форму лаунчера, а книга целиком лежит
 * в 21…87 — значит, её не обрежет ни на круглой маске, ни на «капле».
 */
const SPINE = [34, 30, 40, 78]; // корешок
const TOP = [34, 30, 74, 36]; // верхняя кромка обложки
const BOTTOM = [34, 78, 74, 84]; // нижняя кромка обложки
const RIGHT_EDGE = [68, 30, 74, 78]; // правая кромка — зеркало корешка, встык с кромками
// Три строки «текста»: короче правой кромки, иначе срастаются с ней в сплошной блок.
const LINES = [
  [44, 44, 66, 48],
  [44, 54, 66, 58],
  [44, 64, 60, 68],
];
const BOOK_RECTS = [SPINE, TOP, BOTTOM, RIGHT_EDGE, ...LINES];

/** Сторона холста описания: тот же viewport, что у вектора адаптивной иконки. */
const VIEWPORT = 108;

// Правая кромка обязательна (требование Александра 08.10.2026: «правая кромка нужна»). Проверка
// стоит здесь, потому что кромку легко потерять при правке: без неё книга «открыта» справа — ровно
// то расхождение, из-за которого появился этот разбор.
if (!BOOK_RECTS.includes(RIGHT_EDGE)) throw new Error('в рисунке нет правой кромки');
if (LINES.some(([, , x1]) => x1 >= RIGHT_EDGE[0])) {
  throw new Error('строка упирается в правую кромку — сольётся с ней');
}

/** Путь для вектора: прямоугольник → `M x0,y0 h w v h h -w z`. */
function rectToPathData([x0, y0, x1, y1]) {
  return `M${x0},${y0}h${x1 - x0}v${y1 - y0}h${x0 - x1}z`;
}

/** Передний план адаптивной иконки: тот же рисунок, что и в растре, только путями. */
function encodeVector() {
  const paths = BOOK_RECTS.map(
    (rect) =>
      `    <path\n        android:fillColor="#FF000000"\n        android:pathData="${rectToPathData(rect)}" />`,
  ).join('\n');
  return `<?xml version="1.0" encoding="utf-8"?>
<!--
  Сгенерировано tools/generate-launcher-icons.mjs — не правьте руками, правьте там.
  Передний план адаптивной иконки (API 26+); фон — @color/ic_launcher_background.
  Рисунок книги обязан совпадать с растром mipmap-*dpi/ic_launcher{,_round}.png для API 24–25.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
${paths}
</vector>
`;
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
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/**
 * Рисует иконку: силуэт (скруглённый квадрат или круг) и книгу `BOOK_RECTS` на нём.
 *
 * Силуэт принципиален: до 08.10.2026 фон закрашивал весь квадрат, поэтому у иконки не было
 * очертаний (`IconLauncherShape`), а флаг `round` не давал видимого круга — вне круга рисовался
 * такой же белый, и круглая иконка выходила побайтово равной квадратной (`IconDuplicates`).
 * Теперь вне силуэта — прозрачность, поэтому квадратная и круглая версии разные.
 *
 * Край силуэта сглаживается 4×4 подвыборками: без этого круг и скругления были бы рваными.
 * Сама книга берётся из `BOOK_RECTS` — того же описания, из которого собирается вектор, поэтому
 * рисунок на API 24–25 и на API 26+ один и тот же.
 */
function drawIcon(size, { round }) {
  const px = new Uint8Array(size * size * 4); // RGBA: по умолчанию всё прозрачно
  const cx = size / 2;
  const cy = size / 2;
  const radius = size * 0.47;
  const margin = size * 0.04;
  const corner = size * 0.2;

  /** Прямоугольник описания (в единицах 108×108) → попадание пикселя. Края включительно. */
  const inBookRect = (x, y, [x0, y0, x1, y1]) =>
    x >= Math.round((x0 / VIEWPORT) * size) &&
    x <= Math.round((x1 / VIEWPORT) * size) &&
    y >= Math.round((y0 / VIEWPORT) * size) &&
    y <= Math.round((y1 / VIEWPORT) * size);

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

      const black = BOOK_RECTS.some((rect) => inBookRect(x, y, rect));

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

// Вектор адаптивной иконки (API 26+) — тот же рисунок, что и в растре выше.
const vectorPath = `${ROOT}/drawable/ic_launcher_foreground.xml`;
const vector = encodeVector();

/**
 * Растр и вектор обязаны описывать один рисунок.
 *
 * Проверка не косметическая: расхождение этих двух файлов и было дефектом, который нашёл Александр
 * 08.10.2026 (в векторе не было правой кромки, в PNG была). Оба вывода строятся из `BOOK_RECTS`, но
 * проверка ловит правку вектора руками и ошибку в сборке путей.
 */
function checkVectorMatchesRaster(text) {
  const expected = BOOK_RECTS.map(rectToPathData).sort();
  const actual = [...text.matchAll(/android:pathData="([^"]+)"/g)].map((m) => m[1]).sort();
  if (actual.length !== expected.length) {
    throw new Error(`в векторе ${actual.length} путей, а в рисунке ${expected.length} прямоугольников`);
  }
  for (let i = 0; i < expected.length; i++) {
    if (actual[i] !== expected[i]) throw new Error(`путь «${actual[i]}» не совпал с рисунком «${expected[i]}»`);
  }
  if (!text.includes(`android:pathData="${rectToPathData(RIGHT_EDGE)}"`)) {
    throw new Error('в векторе нет правой кромки обложки');
  }
}

checkVectorMatchesRaster(vector);
writeFileSync(vectorPath, vector);
console.log(`drawable/ic_launcher_foreground.xml`, `${BOOK_RECTS.length} прямоугольников`, `${vector.length} байт`);
console.log("рисунок книги:", BOOK_RECTS.map((rect) => `[${rect.join(",")}]`).join(" "));
