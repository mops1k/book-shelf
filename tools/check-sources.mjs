#!/usr/bin/env node
// Санитарная проверка исходников, которую можно запустить БЕЗ JDK, Gradle и Android SDK.
//
// Зачем: в проекте с жёстким TDD-циклом ошибка в синтаксисе должна находиться за секунды,
// а не через десять минут сборки. Скрипт не заменяет компилятор, но ловит типовые дефекты
// (небаланс скобок, табы, CRLF, строки > 120, `TODO` без спринта, `Thread.sleep` в тестах,
// запрещённые правилами тестируемости вызовы).
//
// Использование:
//   node tools/check-sources.mjs            # проверка, код возврата 1 при ошибках
//   node tools/check-sources.mjs --warn     # показывать и предупреждения
//   node tools/check-sources.mjs --self-test  # проверить сам разбор литералов (без файлов проекта)
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, extname, join, relative } from 'node:path';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SHOW_WARNINGS = process.argv.includes('--warn');
const MAX_LINE = 120;

const SKIP_DIRS = new Set([
  '.git', '.gradle', 'build', 'dist', 'node_modules', '.idea', '.kotlin', 'schemas',
]);

/** Правила тестируемости из docs/tdd.md §5: в main-коде этих вызовов быть не должно. */
const FORBIDDEN_IN_MAIN = [
  { pattern: /System\.currentTimeMillis\(\)/, hint: 'используйте AppClock (docs/tdd.md §5)' },
  { pattern: /\bDispatchers\.(IO|Default|Main)\b/, hint: 'используйте AppDispatchers / квалификаторы Hilt' },
  { pattern: /\bLog\.[dviwe]\(/, hint: 'используйте AppLogger' },
  { pattern: /Thread\.sleep\(/, hint: 'подменяйте время (AppClock), а не спите' },
];

/**
 * Файлы — реализации самих абстракций: именно здесь системные вызовы и должны находиться.
 * Список закрытый и короткий, чтобы правило не «размывалось» исключениями по ходу дела.
 */
const ALLOWED_FILES = new Set([
  'core/common/src/main/kotlin/io/github/bookshelf/core/common/time/AppClock.kt',
  'core/common/src/main/kotlin/io/github/bookshelf/core/common/coroutine/DefaultAppDispatchers.kt',
  'core/common/src/main/kotlin/io/github/bookshelf/core/common/log/AndroidAppLogger.kt',
]);

const errors = [];
const warnings = [];

function walk(dir) {
  const result = [];
  for (const entry of readdirSync(dir)) {
    if (SKIP_DIRS.has(entry)) continue;
    const full = join(dir, entry);
    const info = statSync(full);
    if (info.isDirectory()) result.push(...walk(full));
    else if (extname(entry) === '.kt' || extname(entry) === '.kts') result.push(full);
  }
  return result;
}

/** Убирает содержимое строк, символьных литералов и комментариев, сохраняя структуру строк. */
function codeOnly(source) {
  let out = '';
  let i = 0;
  let inLineComment = false;
  let inBlockComment = false;

  while (i < source.length) {
    const two = source.slice(i, i + 2);
    const three = source.slice(i, i + 3);
    const char = source[i];

    if (inLineComment) {
      if (char === '\n') {
        inLineComment = false;
        out += '\n';
      } else {
        out += ' ';
      }
      i += 1;
      continue;
    }

    if (inBlockComment) {
      if (two === '*/') {
        inBlockComment = false;
        out += '  ';
        i += 2;
      } else {
        out += char === '\n' ? '\n' : ' ';
        i += 1;
      }
      continue;
    }

    if (three === '"""') {
      let j = i + 3;
      while (j < source.length && source.slice(j, j + 3) !== '"""') {
        out += source[j] === '\n' ? '\n' : ' ';
        j += 1;
      }
      out += '      ';
      i = j + 3;
      continue;
    }

    if (two === '//') {
      inLineComment = true;
      i += 2;
      continue;
    }
    if (two === '/*') {
      inBlockComment = true;
      i += 2;
      continue;
    }
    if (char === '"') {
      out += ' ';
      i += 1;
      while (i < source.length && source[i] !== '"') {
        if (source[i] === '\\') {
          out += '  ';
          i += 2;
          continue;
        }
        out += source[i] === '\n' ? '\n' : ' ';
        i += 1;
      }
      out += ' ';
      i += 1;
      continue;
    }
    if (char === "'") {
      // Символьный литерал: '(', '*', '\'', '\\', '\n', '\u0000'. Escape бывает длиннее
      // двух символов, и считать его как «обратный слэш + один символ» нельзя: на `'\u0000'`
      // закрывающая кавычка не находилась, следующая `)` съедалась как «литерал» и баланс
      // скобок уезжал — так возникало ложное «несбалансированные скобки» на верном файле.
      let j = i + 1;
      if (source[j] === '\\') {
        const escape = source[j + 1];
        if (escape === 'u' && source[j + 2] === '{') {
          const closingBrace = source.indexOf('}', j + 2);
          j = closingBrace === -1 ? j + 2 : closingBrace + 1;
        } else if (escape === 'u') {
          j += 6;
        } else if (escape === 'x') {
          j += 4;
        } else {
          j += 2;
        }
      } else {
        j += 1;
      }
      if (source[j] === "'") j += 1;
      out += ' '.repeat(Math.max(0, j - i));
      i = j;
      continue;
    }

    out += char;
    i += 1;
  }
  return out;
}

/** Баланс круглых скобок в коде без строк, символьных литералов и комментариев. */
function parenBalance(code) {
  let balance = 0;
  for (const char of codeOnly(code)) {
    if (char === '(') balance += 1;
    else if (char === ')') balance -= 1;
  }
  return balance;
}

/**
 * Самопроверка разбора литералов: запускается флагом `--self-test` и не требует JDK.
 *
 * Регрессия, из-за которой она появилась: escape `\uXXXX` в символьном литерале ломал подсчёт
 * скобок, и валидный файл (`charArrayOf('/', '\\', '\u0000')`) объявлялся несбалансированным.
 */
function selfTest() {
  const cases = [
    { code: String.raw`val chars = charArrayOf('/', '\\', ':', '\u0000')`, expected: 0 },
    { code: String.raw`val single = '\u0000'`, expected: 0 },
    { code: String.raw`val hex = '\x41'`, expected: 0 },
    { code: String.raw`val braced = '\u{1F600}'`, expected: 0 },
    { code: String.raw`val quote = '\''`, expected: 0 },
    { code: String.raw`val text = "скобка ( внутри строки"`, expected: 0 },
    { code: String.raw`val comment = 1 // ( в комментарии`, expected: 0 },
    { code: String.raw`val broken = call(one(2)`, expected: 1 },
  ];
  const failures = cases.filter((item) => parenBalance(item.code) !== item.expected);
  if (failures.length) {
    for (const item of failures) {
      console.error(
        `  FAIL  ${item.code}: ожидался баланс ${item.expected}, получен ${parenBalance(item.code)}`,
      );
    }
    console.error(`\nСамопроверка не прошла: ${failures.length} из ${cases.length}.`);
    process.exit(1);
  }
  console.log(`Самопроверка разбора литералов: ${cases.length} случаев, всё сходится.`);
}

function isCommentLine(line) {
  const trimmed = line.trimStart();
  return trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*');
}

function checkFile(file) {
  const rel = relative(ROOT, file);
  const raw = readFileSync(file);
  const text = raw.toString('utf8');

  if (raw[0] === 0xef && raw[1] === 0xbb && raw[2] === 0xbf) errors.push(`${rel}: BOM в начале файла`);
  if (text.includes('\r\n')) errors.push(`${rel}: CRLF вместо LF (.editorconfig требует LF)`);
  if (text.includes('\t')) errors.push(`${rel}: табуляция (нужны пробелы)`);
  if (!text.endsWith('\n')) errors.push(`${rel}: нет перевода строки в конце файла`);

  const stripped = codeOnly(text);
  const balance = { '{': 0, '(': 0, '[': 0 };
  const closing = { '}': '{', ')': '(', ']': '[' };
  for (const char of stripped) {
    if (char in balance) balance[char] += 1;
    else if (char in closing) balance[closing[char]] -= 1;
  }
  for (const [open, count] of Object.entries(balance)) {
    if (count !== 0) errors.push(`${rel}: несбалансированные скобки: '${open}' ${count > 0 ? 'не закрыто' : 'лишних'} ${Math.abs(count)}`);
  }

  const lines = text.split('\n');
  lines.forEach((line, index) => {
    const number = index + 1;
    if (line.length > MAX_LINE) {
      const where = `${rel}:${number}`;
      if (isCommentLine(line)) warnings.push(`${where}: длинная строка комментария (${line.length} > ${MAX_LINE})`);
      else errors.push(`${where}: строка кода длиннее ${MAX_LINE} символов (${line.length})`);
    }
    if (/TODO\(/.test(line)) {
      // По ADR-013 любой TODO обязан быть привязан к спринту — либо как TODO("STUB(Sprint N): …"),
      // либо с номером спринта в комментарии рядом. Смотрим и назад, и вперёд: многострочный
      // вызов TODO(…) переносит строку с маркером на следующую строку.
      const context = [
        lines[index - 2] ?? '',
        lines[index - 1] ?? '',
        line,
        lines[index + 1] ?? '',
        lines[index + 2] ?? '',
      ].join('\n');
      if (!/Sprint\s*\d+/.test(context)) {
        errors.push(`${rel}:${number}: TODO без номера спринта — см. ADR-013 (нужен STUB(Sprint N))`);
      }
    }
    if (/(^|[^A-Za-z_])@Ignore\b/.test(line)) {
      warnings.push(`${rel}:${number}: @Ignore — нужна ссылка на задачу (docs/tdd.md §2)`);
    }
  });

  const isTest = rel.includes('/src/test/') || rel.includes('/src/testFixtures/') || rel.includes('/src/androidTest/');
  if (!isTest && !ALLOWED_FILES.has(rel.replaceAll('\\', '/'))) {
    for (const rule of FORBIDDEN_IN_MAIN) {
      if (rule.pattern.test(stripped)) {
        warnings.push(`${rel}: найдено «${rule.pattern.source}» в main-коде — ${rule.hint}`);
      }
    }
  } else if (/Thread\.sleep\(/.test(stripped)) {
    errors.push(`${rel}: Thread.sleep в тесте — запрещено (docs/tdd.md §2)`);
  }
}

if (process.argv.includes('--self-test')) {
  selfTest();
  process.exit(0);
}

const files = walk(ROOT).sort();
for (const file of files) checkFile(file);

console.log(`Проверено файлов: ${files.length}`);
if (warnings.length) {
  console.log(`\nПредупреждения (${warnings.length}):`);
  for (const warning of warnings) console.log(`  warn  ${warning}`);
}
if (errors.length) {
  console.error(`\nОшибки (${errors.length}):`);
  for (const error of errors) console.error(`  FAIL  ${error}`);
  process.exit(1);
}
if (!SHOW_WARNINGS && warnings.length) {
  console.log('\nЗапустите с --warn, чтобы увидеть предупреждения.');
}
console.log('\nСинтаксическая санитария в порядке.');
