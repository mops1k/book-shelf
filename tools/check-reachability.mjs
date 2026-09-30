/**
 * Проверка достижимости экранов.
 *
 * Существует потому, что проект один раз отчитался «всё готово» при 319 зелёных тестах,
 * а пользователь на телефоне увидел девять экранов с надписью «Раздел в разработке». Причина:
 * маршруты были зарегистрированы в графе навигации, но на них никто не вёл. Классы
 * репозиториев при этом были полностью реализованы и покрыты тестами.
 *
 * Проверка «есть ли TODO() в классе» эту ошибку не ловит принципиально: ошибка была
 * в связности между двумя готовыми блоками, а не в одном из них.
 *
 * Что проверяется:
 * 1. каждый `composable(...)` в feature-модулях достижим — есть переход на его маршрут;
 * 2. ни один экран не рисует текст «Раздел в разработке» вне состояния «данных нет»;
 * 3. каждый `onOpen*` ведёт на существующий маршрут, а не в никуда;
 * 4. маршрут не достижим **только** из пустого состояния.
 *
 * Как считается переход (правило ужесточено 30.09.2026). Раньше переходом считалось любое
 * упоминание `<Маршрут>.route`, и файл самой регистрации экрана тоже подходил: `composable(route =
 * XRoute.route, …)` делал экран «достижимым» сам для себя. Из-за этого проверка пропустила два
 * разрыва, найденных на устройстве: экран сканирования открывался только с пустой полки, а экран
 * каталогов — через `CatalogsRoute.createRoute()`, который вообще не распознавался.
 * Теперь переходом считается упоминание маршрута вне аргументов `composable(…)`: сама регистрация
 * экрана переходом не является, а `navigate(…)` и `createRoute(…)` — являются.
 *
 * Запуск: node tools/check-reachability.mjs
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const ROOT = process.cwd();
const FEATURES = join(ROOT, "feature");
const APP = join(ROOT, "app/src/main/kotlin/io/github/bookshelf");

/** Все .kt под деревом, отсортированные — порядок не влияет на результат. */
function walk(dir) {
  const out = [];
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const name of entries) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) out.push(...walk(full));
    else if (name.endsWith(".kt")) out.push(full);
  }
  return out;
}

const featureFiles = walk(FEATURES);
const appFiles = walk(APP);
const read = (f) => readFileSync(f, "utf8");

/** Объекты Route: имя → значение константы. */
const routes = new Map();
for (const file of featureFiles) {
  const text = read(file);
  const obj = text.match(/object\s+(\w*Route)\s*\{/);
  if (!obj) continue;
  const route = text.match(/const\s+val\s+route\s*(?::\s*\w+)?\s*=\s*"([^"]+)"/);
  if (route) routes.set(obj[1], { value: route[1], file: relative(ROOT, file) });
}

/** Кто на кого ведёт: имя маршрута → файлы, где маршрут указан в переходе. */
const transitions = new Map();
for (const file of [...featureFiles, ...appFiles]) {
  const text = read(file);
  const rel = relative(ROOT, file);
  for (const name of routes.keys()) {
    if (transitionIndices(name, text).length === 0) continue;
    if (!transitions.has(name)) transitions.set(name, []);
    transitions.get(name).push(rel);
  }
}

/**
 * Убрать из текста строки, символьные литералы и комментарии.
 *
 * Нужно для разбора блоков `EinkEmptyState(…)`: скобки внутри подписей не должны сбивать подсчёт.
 */
function codeOnly(source) {
  let out = "";
  let i = 0;
  let line = false;
  let block = false;
  while (i < source.length) {
    const two = source.slice(i, i + 2);
    const char = source[i];
    if (line) {
      if (char === "\n") { line = false; out += "\n"; } else out += " ";
      i += 1;
      continue;
    }
    if (block) {
      if (two === "*/") { block = false; out += "  "; i += 2; } else { out += char === "\n" ? "\n" : " "; i += 1; }
      continue;
    }
    if (two === "//") { line = true; out += "  "; i += 2; continue; }
    if (two === "/*") { block = true; out += "  "; i += 2; continue; }
    if (char === '"') {
      out += " ";
      i += 1;
      while (i < source.length && source[i] !== '"') {
        if (source[i] === "\\") { out += "  "; i += 2; } else { out += " "; i += 1; }
      }
      out += " ";
      i += 1;
      continue;
    }
    out += char;
    i += 1;
  }
  // Инвариант: маска совпадает по длине с исходником, иначе индексы в ней означают не то место.
  // Ошибка в этом месте уже приводила к тихому пропуску: диапазоны регистрации уезжали влево.
  if (out.length !== source.length) {
    throw new Error(`codeOnly сместил индексы: ${out.length} вместо ${source.length}`);
  }
  return out;
}

/** Диапазоны блоков `EinkEmptyState(…)`: переход внутри них — вход только из пустого состояния. */
function emptyStateRanges(text) {
  const code = codeOnly(text);
  const ranges = [];
  let from = 0;
  while (true) {
    const start = code.indexOf("EinkEmptyState(", from);
    if (start < 0) break;
    let depth = 0;
    let i = start + "EinkEmptyState".length;
    while (i < code.length) {
      if (code[i] === "(") depth += 1;
      else if (code[i] === ")") {
        depth -= 1;
        if (depth === 0) break;
      }
      i += 1;
    }
    ranges.push([start, i]);
    from = i + 1;
  }
  return ranges;
}

/**
 * Диапазоны аргументов `composable(…)` — это регистрация экрана, а не переход.
 *
 * Именно аргументы, а не тело лямбды: внутри `composable(LibraryRoute.route) { libraryScreen(…) }`
 * лежит вся привязка экрана, и переходы оттуда настоящие.
 */
function registrationRanges(text) {
  const code = codeOnly(text);
  const ranges = [];
  let from = 0;
  while (true) {
    const start = code.indexOf("composable(", from);
    if (start < 0) break;
    let depth = 0;
    let i = start + "composable".length;
    while (i < code.length) {
      if (code[i] === "(") depth += 1;
      else if (code[i] === ")") {
        depth -= 1;
        if (depth === 0) break;
      }
      i += 1;
    }
    ranges.push([start, i]);
    from = i + 1;
  }
  return ranges;
}

/** Индексы упоминаний маршрута, которые являются переходом, а не регистрацией. */
function transitionIndices(name, text) {
  const ranges = registrationRanges(text);
  const mention = new RegExp(`${name}\\.(route|createRoute)\\b`, "g");
  const out = [];
  for (const m of text.matchAll(mention)) {
    if (ranges.some(([start, end]) => m.index > start && m.index < end)) continue;
    out.push(m.index);
  }
  return out;
}

/** Все переходы на маршрут лежат внутри пустых состояний? */
function onlyFromEmptyState(name) {
  let total = 0;
  let empty = 0;
  for (const file of [...featureFiles, ...appFiles]) {
    const text = read(file);
    const ranges = emptyStateRanges(text);
    for (const index of transitionIndices(name, text)) {
      total += 1;
      if (ranges.some(([start, end]) => index > start && index < end)) empty += 1;
    }
  }
  return total > 0 && total === empty;
}

const problems = [];

// 1. Достижимость. Стартовые маршруты перечислены явно: их показывают без перехода.
const START_ROUTES = new Set(["LibraryRoute"]);
for (const [name, info] of routes) {
  if (START_ROUTES.has(name)) continue;
  const targets = transitions.get(name) ?? [];
  if (targets.length === 0) {
    problems.push(`экран ${name} (маршрут "${info.value}") недостижим: на него нет перехода`);
    continue;
  }
  if (onlyFromEmptyState(name)) {
    problems.push(
      `экран ${name} (маршрут "${info.value}") достижим только из пустого состояния: ` +
        `при живых данных входа к нему нет`,
    );
  }
}

// 2. Надпись о незавершённости должна встречаться только в пустых состояниях.
const PLACEHOLDER = "Раздел в разработке";
for (const file of featureFiles) {
  const text = read(file);
  if (!text.includes(PLACEHOLDER)) continue;
  const lines = text.split("\n");
  lines.forEach((line, i) => {
    if (!line.includes(PLACEHOLDER)) return;
    // Допустимо только в EinkEmptyState — это честное «данных нет».
    const context = lines.slice(Math.max(0, i - 6), i).join("\n");
    if (!context.includes("EinkEmptyState") && !context.includes("EinkErrorBanner")) {
      problems.push(
        `${relative(ROOT, file)}:${i + 1} — надпись «${PLACEHOLDER}» вне пустого состояния`,
      );
    }
  });
}

// 3. Обработчики переходов не должны быть заглушками.
// Заглушкой считается ЛЮБОЙ колбэк без перехода: popBackStack — тоже переход.
// Проверяется только граф приложения: в feature-модулях `onX = { … }` — обычная привязка к
// ViewModel, а не переход, и попытка считать её переходом дала 115 ложных срабатываний, то есть
// проверка перестала бы ловить что-либо вообще. Переходы из feature-модулей ловит проверка 1 —
// по вызовам `navigate(…)` и `createRoute(…)` в любом файле.
const BACK = "popBackStack";
for (const file of appFiles) {
  const text = read(file);
  for (const m of text.matchAll(/(on[A-Z]\w*)\s*=\s*\{([^{}]*)\}/g)) {
    const [, name, body] = m;
    if (body.includes("navigate") || body.includes(BACK)) continue;
    problems.push(
      `${relative(ROOT, file)} — «${name}» никуда не ведёт: {${body.trim()}}`,
    );
  }
}

if (routes.size === 0) {
  console.error("FAIL: не найден ни один маршрут — проверка не работает, а не «всё хорошо»");
  process.exit(1);
}

if (problems.length > 0) {
  console.log(`FAIL: проблем связности ${problems.length}\n`);
  for (const p of problems) console.log(`  ${p}`);
  process.exit(1);
}

console.log(
  `OK: ${routes.size} маршрутов, все достижимы не только из пустого состояния; ` +
    `надписей о незавершённости вне пустых состояний нет`,
);
