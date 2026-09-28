/**
 * Генератор тестовых книг для проверки парсеров на устройстве.
 *
 * Зачем: парсеры проверяются на настоящих файлах, а на тестовую библиотеку у разработчика их
 * нет. Книги делаются намеренно разными — кириллица в разных кодировках, серия, несколько
 * авторов, обложка — чтобы одна проверка охватывала все ветки разбора сразу.
 *
 * Запуск: node tools/generate-test-books.mjs [каталог]
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { deflateRawSync, crc32 } from "node:zlib";

/**
 * Перекодировка в однобайтовую кириллицу.
 *
 * Node умеет только декодировать такие кодировки, а кодировать надо вручную: русские fb2 в
 * интернете чаще всего лежат именно в windows-1251, и файл с меткой `encoding="windows-1251"`,
 * внутри которого лежит UTF-8, на устройстве даст мусор вместо заголовка.
 */
// Таблицы кодовых страниц получены из реальных кодировок, а не из памяти:
// псевдографический порядок KOI8-R легко перепутать с алфавитным.
const CODE_PAGES = {"koi8-r": {"\u0000": 0, "\u0001": 1, "\u0002": 2, "\u0003": 3, "\u0004": 4, "\u0005": 5, "\u0006": 6, "\u0007": 7, "\b": 8, "\t": 9, "\n": 10, "\u000b": 11, "\f": 12, "\r": 13, "\u000e": 14, "\u000f": 15, "\u0010": 16, "\u0011": 17, "\u0012": 18, "\u0013": 19, "\u0014": 20, "\u0015": 21, "\u0016": 22, "\u0017": 23, "\u0018": 24, "\u0019": 25, "\u001a": 26, "\u001b": 27, "\u001c": 28, "\u001d": 29, "\u001e": 30, "\u001f": 31, " ": 32, "!": 33, "\"": 34, "#": 35, "$": 36, "%": 37, "&": 38, "'": 39, "(": 40, ")": 41, "*": 42, "+": 43, ",": 44, "-": 45, ".": 46, "/": 47, "0": 48, "1": 49, "2": 50, "3": 51, "4": 52, "5": 53, "6": 54, "7": 55, "8": 56, "9": 57, ":": 58, ";": 59, "<": 60, "=": 61, ">": 62, "?": 63, "@": 64, "A": 65, "B": 66, "C": 67, "D": 68, "E": 69, "F": 70, "G": 71, "H": 72, "I": 73, "J": 74, "K": 75, "L": 76, "M": 77, "N": 78, "O": 79, "P": 80, "Q": 81, "R": 82, "S": 83, "T": 84, "U": 85, "V": 86, "W": 87, "X": 88, "Y": 89, "Z": 90, "[": 91, "\\": 92, "]": 93, "^": 94, "_": 95, "`": 96, "a": 97, "b": 98, "c": 99, "d": 100, "e": 101, "f": 102, "g": 103, "h": 104, "i": 105, "j": 106, "k": 107, "l": 108, "m": 109, "n": 110, "o": 111, "p": 112, "q": 113, "r": 114, "s": 115, "t": 116, "u": 117, "v": 118, "w": 119, "x": 120, "y": 121, "z": 122, "{": 123, "|": 124, "}": 125, "~": 126, "": 127, " ": 154, "©": 191, "°": 156, "²": 157, "·": 158, "÷": 159, "Ё": 179, "А": 225, "Б": 226, "В": 247, "Г": 231, "Д": 228, "Е": 229, "Ж": 246, "З": 250, "И": 233, "Й": 234, "К": 235, "Л": 236, "М": 237, "Н": 238, "О": 239, "П": 240, "Р": 242, "С": 243, "Т": 244, "У": 245, "Ф": 230, "Х": 232, "Ц": 227, "Ч": 254, "Ш": 251, "Щ": 253, "Ъ": 255, "Ы": 249, "Ь": 248, "Э": 252, "Ю": 224, "Я": 241, "а": 193, "б": 194, "в": 215, "г": 199, "д": 196, "е": 197, "ж": 214, "з": 218, "и": 201, "й": 202, "к": 203, "л": 204, "м": 205, "н": 206, "о": 207, "п": 208, "р": 210, "с": 211, "т": 212, "у": 213, "ф": 198, "х": 200, "ц": 195, "ч": 222, "ш": 219, "щ": 221, "ъ": 223, "ы": 217, "ь": 216, "э": 220, "ю": 192, "я": 209, "ё": 163}, "cp1251": {"\u0000": 0, "\u0001": 1, "\u0002": 2, "\u0003": 3, "\u0004": 4, "\u0005": 5, "\u0006": 6, "\u0007": 7, "\b": 8, "\t": 9, "\n": 10, "\u000b": 11, "\f": 12, "\r": 13, "\u000e": 14, "\u000f": 15, "\u0010": 16, "\u0011": 17, "\u0012": 18, "\u0013": 19, "\u0014": 20, "\u0015": 21, "\u0016": 22, "\u0017": 23, "\u0018": 24, "\u0019": 25, "\u001a": 26, "\u001b": 27, "\u001c": 28, "\u001d": 29, "\u001e": 30, "\u001f": 31, " ": 32, "!": 33, "\"": 34, "#": 35, "$": 36, "%": 37, "&": 38, "'": 39, "(": 40, ")": 41, "*": 42, "+": 43, ",": 44, "-": 45, ".": 46, "/": 47, "0": 48, "1": 49, "2": 50, "3": 51, "4": 52, "5": 53, "6": 54, "7": 55, "8": 56, "9": 57, ":": 58, ";": 59, "<": 60, "=": 61, ">": 62, "?": 63, "@": 64, "A": 65, "B": 66, "C": 67, "D": 68, "E": 69, "F": 70, "G": 71, "H": 72, "I": 73, "J": 74, "K": 75, "L": 76, "M": 77, "N": 78, "O": 79, "P": 80, "Q": 81, "R": 82, "S": 83, "T": 84, "U": 85, "V": 86, "W": 87, "X": 88, "Y": 89, "Z": 90, "[": 91, "\\": 92, "]": 93, "^": 94, "_": 95, "`": 96, "a": 97, "b": 98, "c": 99, "d": 100, "e": 101, "f": 102, "g": 103, "h": 104, "i": 105, "j": 106, "k": 107, "l": 108, "m": 109, "n": 110, "o": 111, "p": 112, "q": 113, "r": 114, "s": 115, "t": 116, "u": 117, "v": 118, "w": 119, "x": 120, "y": 121, "z": 122, "{": 123, "|": 124, "}": 125, "~": 126, "": 127, " ": 160, "¤": 164, "¦": 166, "§": 167, "©": 169, "«": 171, "¬": 172, "­": 173, "®": 174, "°": 176, "±": 177, "µ": 181, "¶": 182, "·": 183, "»": 187, "Ё": 168, "Ђ": 128, "Ѓ": 129, "Є": 170, "Ѕ": 189, "І": 178, "Ї": 175, "Ј": 163, "Љ": 138, "Њ": 140, "Ћ": 142, "Ќ": 141, "Ў": 161, "Џ": 143, "А": 192, "Б": 193, "В": 194, "Г": 195, "Д": 196, "Е": 197, "Ж": 198, "З": 199, "И": 200, "Й": 201, "К": 202, "Л": 203, "М": 204, "Н": 205, "О": 206, "П": 207, "Р": 208, "С": 209, "Т": 210, "У": 211, "Ф": 212, "Х": 213, "Ц": 214, "Ч": 215, "Ш": 216, "Щ": 217, "Ъ": 218, "Ы": 219, "Ь": 220, "Э": 221, "Ю": 222, "Я": 223, "а": 224, "б": 225, "в": 226, "г": 227, "д": 228, "е": 229, "ж": 230, "з": 231, "и": 232, "й": 233, "к": 234, "л": 235, "м": 236, "н": 237, "о": 238, "п": 239, "р": 240, "с": 241, "т": 242, "у": 243, "ф": 244, "х": 245, "ц": 246, "ч": 247, "ш": 248, "щ": 249, "ъ": 250, "ы": 251, "ь": 252, "э": 253, "ю": 254, "я": 255, "ё": 184, "ђ": 144, "ѓ": 131, "є": 186, "ѕ": 190, "і": 179, "ї": 191, "ј": 188, "љ": 154, "њ": 156, "ћ": 158, "ќ": 157, "ў": 162, "џ": 159, "Ґ": 165, "ґ": 180}};

function encodeCyrillic(text, encoding) {
  const page = CODE_PAGES[encoding];
  if (!page) throw new Error(`Неизвестная кодировка ${encoding}`);
  const out = [];
  for (const ch of text) {
    const code = page[ch];
    if (code === undefined) throw new Error(`Нет символа ${JSON.stringify(ch)} в ${encoding}`);
    out.push(code);
  }
  return Buffer.from(out);
}

const target = resolve(process.argv[2] ?? "testdata/books");
mkdirSync(target, { recursive: true });

/** Минимальный JPEG 1×1, чтобы проверить путь «обложка найдена», а не качество картинки. */
const TINY_JPEG = Buffer.from(
  "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a" +
    "HBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAA" +
    "AAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==",
  "base64",
);

/** base64 без переводов строк: FB2 пишет binary одним блоком. */
const coverBase64 = TINY_JPEG.toString("base64");

function fb2({ title, authors, series, index, year, publisher, lang = "ru", cover = true }) {
  const authorTags = authors
    .map(
      (a) =>
        `      <author><first-name>${a.first}</first-name><last-name>${a.last}</last-name></author>`,
    )
    .join("\n");
  const sequence = series
    ? `\n    <sequence name="${series}" number="${index}"/>\n  </title-info>`
    : "\n  </title-info>";
  const coverBlock = cover
    ? `\n  <body>\n    <section>\n      <title><p><image l:href="#cover.jpg"/></p></title>` +
      `\n      <p>Текст книги ${title}.</p>\n    </section>\n  </body>`
    : "\n  <body>\n    <section>\n      <p>Текст книги ${title}.</p>\n    </section>\n  </body>";
  const binary = cover
    ? `\n<binary id="cover.jpg" content-type="image/jpeg">${coverBase64}</binary>`
    : "";
  return `<?xml version="1.0" encoding="utf-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
             xmlns:l="http://www.w3.org/1999/xlink">
  <description>
    <title-info>
      <genre>classic</genre>
      <author><nickname>Лев Толстой</nickname></author>${authorTags}
      <book-title>${title}</book-title>
      <annotation>Аннотация: краткое описание книги ${title}.</annotation>
      <lang>${lang}</lang>${sequence}
    <date value="1869-01-01"/>
    <coverpage><image l:href="#cover.jpg"/></coverpage>
  </description>
  <publish-info>
    <book-name>${title}</book-name>
    <publisher>${publisher}</publisher>
    <year>${year}</year>
    <isbn>978-5-00-000000-1</isbn>
  </publish-info>${coverBlock}${binary}
</FictionBook>
`;
}

function epub({ title, authors, series, index, year, publisher, cover = true }) {
  const creatorTags = authors
    .map((a) => `<dc:creator>${a.first} ${a.last}</dc:creator>`)
    .join("\n");
  const collection = series
    ? `<meta property="belongs-to-collection" id="col">${series}</meta>` +
      `<meta property="collection-type" refines="#col">series</meta>` +
      `<meta property="group-position" refines="#col">${index}</meta>`
    : `<meta name="calibre:series" content="${series ?? ""}"/>`;
  const coverItem = cover
    ? `<item id="cover-img" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>`
    : "";
  const opf = `<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:${{
      title
    }}-${year}</dc:identifier>
    <dc:title>${title}</dc:title>
    ${creatorTags}
    <dc:language>ru</dc:language>
    <dc:publisher>${publisher}</dc:publisher>
    <dc:description>Описание книги ${title}.</dc:description>
    <dc:date>${year}-01-01</dc:date>
    ${collection}
  </metadata>
  <manifest>
    <item id="content" href="content.xhtml" media-type="application/xhtml+xml"/>
    ${coverItem}
  </manifest>
  <spine><itemref idref="content"/></spine>
</package>
`;
  const body = `<html xmlns="http://www.w3.org/1999/xhtml"><head><title>${title}</title></head>` +
    `<body><h1>${title}</h1><p>Содержимое книги ${title}.</p></body></html>`;
  return {
    "mimetype": "application/epub+zip",
    "META-INF/container.xml":
      `<?xml version="1.0"?>\n<container version="1.0" ` +
      `xmlns="urn:oasis:names:tc:opendocument:xmlns:container">` +
      `<rootfiles><rootfile full-path="OEBPS/content.opf" ` +
      `media-type="application/oebps-package+xml"/></rootfiles></container>`,
    "OEBPS/content.opf": opf,
    "OEBPS/content.xhtml": body,
    ...(cover ? { "OEBPS/images/cover.jpg": TINY_JPEG } : {}),
  };
}

/** Минимальный zip. `mimetype` обязан идти первым и без сжатия — этого требует спецификация EPUB. */
function zip(entries, { storeFirst = false } = {}) {
  const locals = [];
  const central = [];
  let offset = 0;
  for (const [name, content] of Object.entries(entries)) {
    const nameBytes = Buffer.from(name, "utf8");
    const raw = Buffer.isBuffer(content) ? content : Buffer.from(content, "utf8");
    const isFirst = storeFirst && central.length === 0;
    const body = isFirst ? raw : deflateRawSync(raw);
    const method = isFirst ? 0 : 8;
    const crc = crc32(raw);

    const local = Buffer.alloc(30 + nameBytes.length);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0, 6);
    local.writeUInt16LE(method, 8);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(body.length, 18);
    local.writeUInt32LE(raw.length, 22);
    local.writeUInt16LE(nameBytes.length, 26);
    nameBytes.copy(local, 30);
    locals.push(local, body);

    const cd = Buffer.alloc(46 + nameBytes.length);
    cd.writeUInt32LE(0x02014b50, 0);
    cd.writeUInt16LE(20, 4);
    cd.writeUInt16LE(20, 6);
    cd.writeUInt16LE(0, 8);
    cd.writeUInt16LE(method, 10);
    cd.writeUInt32LE(crc, 16);
    cd.writeUInt32LE(body.length, 20);
    cd.writeUInt32LE(raw.length, 24);
    cd.writeUInt16LE(nameBytes.length, 28);
    cd.writeUInt32LE(offset, 42);
    nameBytes.copy(cd, 46);
    central.push(cd);

    offset += local.length + body.length;
  }
  const directory = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(central.length, 8);
  end.writeUInt16LE(central.length, 10);
  end.writeUInt32LE(directory.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, directory, end]);
}

const tolstoy = { first: "Лев", last: "Толстой" };
const tolstoy2 = { first: "Лев", last: "Николаевич Толстой" };
const dostoevsky = { first: "Фёдор", last: "Достоевский" };
const tolkien = { first: "Джон", last: "Толкин" };

const written = [];

function write(name, data) {
  writeFileSync(resolve(target, name), data);
  written.push(`${name} — ${Buffer.isBuffer(data) ? data.length : Buffer.byteLength(data)} байт`);
}

// 1. FB2 в UTF-8, два автора, серия, обложка.
write(
  "Толстой — Война и мир.fb2",
  fb2({
    title: "Война и мир",
    authors: [tolstoy, tolstoy2],
    series: "Классика",
    index: 1,
    year: 1869,
    publisher: "АСТ",
  }),
);

// 2. FB2 в windows-1251 — самая частая кодировка русских fb2 в интернете.
const cp1251Fb2 = fb2({
  title: "Преступление и наказание",
  authors: [dostoevsky],
  series: "Классика",
  index: 2,
  year: 1866,
  publisher: "Эксмо",
  cover: false,
});
write(
  "Достоевский — Преступление и наказание (cp1251).fb2",
  encodeCyrillic(cp1251Fb2.replace(/encoding="utf-8"/, 'encoding="windows-1251"'), "cp1251"),
);

// 3. FB2 в KOI8-R без BOM.
write(
  "Толстой — Воскресение (koi8-r).fb2",
  encodeCyrillic(
    fb2({
      title: "Воскресение",
      authors: [tolstoy],
      series: "Классика",
      index: 3,
      year: 1899,
      publisher: "Ленинград",
      cover: false,
    }).replace(/encoding="utf-8"/, 'encoding="koi8-r"'),
    "koi8-r",
  ),
);

// 4. FB2 в ZIP.
write(
  "Толкин — Сказки Нижней Страны.fb2.zip",
  zip({ "Сказки.fb2": fb2({
    title: "Сказки Нижней Страны",
    authors: [tolkien],
    series: "Толкин",
    index: 1,
    year: 1969,
    publisher: "Азбука",
  }) }),
);

// 5. EPUB 3 с серией через belongs-to-collection и обложкой.
write(
  "Толкин — Властелин Колец.epub",
  zip(
    {
      ...epub({
        title: "Властелин Колец",
        authors: [tolkien],
        series: "Средиземье",
        index: 3,
        year: 1954,
        publisher: "Азбука",
      }),
      mimetype: "application/epub+zip",
    },
    { storeFirst: true },
  ),
);

// 6. EPUB 2 с calibre-серией, без обложки.
write(
  "Горький — Матери и дети.epub",
  zip(
    {
      ...epub({
        title: "Матери и дети",
        authors: [{ first: "Максим", last: "Горький" }],
        series: "Советская классика",
        index: 1,
        year: 1906,
        publisher: "Советский писатель",
        cover: false,
      }),
      mimetype: "application/epub+zip",
    },
    { storeFirst: true },
  ),
);

// 7. TXT в UTF-8.
write(
  "Чехов — Три сестры.txt",
  "Три сестры\n\nАнтон Павлович Чехов\n\nСодержание: текст книги начинается ниже.\n",
);

// 8. TXT в cp1251.
write(
  "Пушкин — Сказка о царе Салтане (cp1251).txt",
  encodeCyrillic(
    "Сказка о царе Салтане\n\nАлександр Сергеевич Пушкин\n\nТекст в кодировке windows-1251.\n",
    "cp1251",
  ),
);

// 9. Битая книга: приложение обязано показать метку, а не упасть.
write("Битая книга.fb2", "<?xml version=\"1.0\"?><FictionBook><description>\n");

// 10. Неподдерживаемый формат: парсера нет, книга не должна ломать очередь.
write("Картинка.jpg", TINY_JPEG);

console.log(`Записано ${written.length} файлов в ${target}\n`);
for (const line of written) console.log(`  ${line}`);
console.log(
  "\nСкопируйте эту папку на телефон в «Внутренний накопитель/Books» и добавьте её папкой в приложении.",
);
