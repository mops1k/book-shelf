#!/usr/bin/env node
// Порог покрытия по модулям: читает XML-отчёты Kover и сравнивает строковое покрытие с порогами
// из config/coverage.json.
//
// Зачем: Kover считает покрытие, но его правила проверки живут в DSL Gradle, а политика проекта
// («TDD» §6) задаёт пороги по модулям и правило «100 % DAO-методов» — в DSL это не выражается.
// Один отслеживаемый файл порогов плюс этот скрипт дают проверяемый гейт: он запускается в CI
// после `koverXmlReport*` и падает со списком модулей, где покрытие ниже договорённого.
// Пороги обязаны быть НИЖЕ измеренной базовой линии: порог, поставленный «на глаз», краснит CI.
//
// Использование:
//   node tools/check-coverage.mjs                     # пороги из config/coverage.json
//   node tools/check-coverage.mjs --warn              # показать недоборы, но вернуть код 0
//   node tools/check-coverage.mjs --config=path.json  # другой файл порогов
//   node tools/check-coverage.mjs --root=DIR          # корень, от которого считаются пути отчётов
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(HERE, '..');
const DEFAULT_CONFIG = join(REPO_ROOT, 'config', 'coverage.json');

/**
 * Строковое покрытие из отчёта Kover (формат JaCoCo).
 *
 * Берётся счётчик уровня ОТЧЁТА: он идёт после всех `<package>`, поэтому пакеты из текста
 * выбрасываются — иначе нашёлся бы счётчик первого класса и покрытие вышло бы случайным.
 * `null` — данных о строках нет (ни счётчика, ни строк): сравнивать порог не с чем, и молча
 * считать это «100 %» нельзя.
 */
export function lineCoverageOf(xmlText) {
  const withoutPackages = xmlText.replace(/<package\b[\s\S]*?<\/package>/g, '');
  for (const match of withoutPackages.matchAll(/<counter\b([^>]*)\/>/g)) {
    const attributes = match[1];
    if (!/type="LINE"/.test(attributes)) continue;
    const missed = Number(attributes.match(/missed="(\d+)"/)?.[1] ?? Number.NaN);
    const covered = Number(attributes.match(/covered="(\d+)"/)?.[1] ?? Number.NaN);
    if (Number.isNaN(missed) || Number.isNaN(covered)) return null;
    const total = missed + covered;
    if (total === 0) return null;
    return { covered, missed, percent: Math.round((covered / total) * 1000) / 10 };
  }
  return null;
}

/**
 * Проверка всех модулей из конфигурации.
 *
 * Модуль без отчёта — ошибка: забыть собрать отчёт значит не проверить модуль, а не «пройти».
 * Исключение — модуль с `"optional": true` (например, тестовый), его отсутствие ожидаемо.
 */
export function checkCoverage(config, options = {}) {
  const root = options.root ?? REPO_ROOT;
  const modules = (config.modules ?? []).map((module) => {
    const base = { name: module.name, minLine: module.minLine, report: module.report, skipped: false };
    const reportPath = resolve(root, module.report);
    if (!existsSync(reportPath)) {
      if (module.optional) {
        return { ...base, ok: true, skipped: true, percent: null, reason: 'отчёта нет (модуль помечен optional)' };
      }
      return { ...base, ok: false, percent: null, reason: `нет отчёта: ${module.report}` };
    }
    const coverage = lineCoverageOf(readFileSync(reportPath, 'utf8'));
    if (!coverage) {
      return { ...base, ok: false, percent: null, reason: 'нет данных о строках в отчёте' };
    }
    const ok = coverage.percent >= module.minLine;
    return {
      ...base,
      ok,
      percent: coverage.percent,
      reason: ok ? null : `НЕ ДОБРАНО: порог ${module.minLine} %`,
    };
  });

  return { modules, failed: modules.some((module) => !module.ok) };
}

/** Человекочитаемая сводка: её видно и локально, и в логе CI. */
export function formatReport(result) {
  const lines = ['Покрытие по модулям (Kover, строки):'];
  for (const module of result.modules) {
    const mark = module.skipped ? 'пропуск' : module.ok ? 'ok     ' : 'НЕДОБОР';
    const percent = module.percent === null ? '  —  ' : `${module.percent} %`;
    const threshold = `порог ${module.minLine} %`;
    const note = module.reason ? ` — ${module.reason}` : '';
    lines.push(`  ${mark} ${module.name.padEnd(22)} ${percent.padStart(7)}   ${threshold}${note}`);
  }
  const failed = result.modules.filter((module) => !module.ok).length;
  const checked = result.modules.length - result.modules.filter((module) => module.skipped).length;
  lines.push(`Итог: проверено модулей ${checked}, ниже порога ${failed}.`);
  return lines.join('\n');
}

function parseArgs(argv) {
  const value = (flag) => argv.find((arg) => arg.startsWith(`${flag}=`))?.slice(flag.length + 1);
  return {
    configPath: value('--config') ?? DEFAULT_CONFIG,
    root: value('--root') ?? REPO_ROOT,
    warn: argv.includes('--warn'),
  };
}

function main() {
  const { configPath, root, warn } = parseArgs(process.argv.slice(2));
  if (!existsSync(configPath)) {
    console.error(`Нет файла порогов покрытия: ${configPath}`);
    process.exit(1);
  }

  const config = JSON.parse(readFileSync(configPath, 'utf8'));
  const result = checkCoverage(config, { root });
  console.log(formatReport(result));

  if (!result.failed) {
    console.log('Покрытие: все пороги выполнены.');
    process.exit(0);
  }
  if (warn) {
    console.log('Покрытие: есть недоборы — это предупреждение (--warn), код возврата 0.');
    process.exit(0);
  }
  console.error('Покрытие: есть модули ниже порога — см. список выше.');
  process.exit(1);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
