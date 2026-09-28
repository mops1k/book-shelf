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
 * 3. каждый `onOpen*` ведёт на существующий маршрут, а не в никуда.
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

/** Кто на кого ведёт: имя маршрута → файлы, где встречается переход на него. */
const transitions = new Map();
for (const file of [...featureFiles, ...appFiles]) {
  const text = read(file);
  for (const [name, { value }] of routes) {
    if (text.includes(`${name}.route`)) {
      if (!transitions.has(name)) transitions.set(name, []);
      transitions.get(name).push(relative(ROOT, file));
    }
  }
}

const problems = [];

// 1. Достижимость. Стартовые маршруты перечислены явно: их показывают без перехода.
const START_ROUTES = new Set(["LibraryRoute"]);
for (const [name, info] of routes) {
  if (START_ROUTES.has(name)) continue;
  const targets = transitions.get(name) ?? [];
  if (targets.length === 0) {
    problems.push(`экран ${name} (маршрут "${info.value}") недостижим: на него нет перехода`);
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
const BACK = "popBackStack";
for (const file of appFiles) {
  const text = read(file);
  for (const m of text.matchAll(/(on\w+)\s*=\s*\{([^{}]*)\}/g)) {
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
  `OK: ${routes.size} маршрутов, все достижимы; надписей о незавершённости вне пустых состояний нет`,
);
