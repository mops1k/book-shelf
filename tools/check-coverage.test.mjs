#!/usr/bin/env node
// Тесты инструмента проверки покрытия. Написаны ДО инструмента (ADR-012): красный прогон
// `node --test tools/` — доказательство, что проверка ловит именно то, ради чего написана.
//
// Запуск: node --test tools/
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

import { checkCoverage, formatReport, lineCoverageOf } from './check-coverage.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..');
const TOOL = join(HERE, 'check-coverage.mjs');
const FIXTURES = join(HERE, 'fixtures', 'coverage');
// Пути в конфигурации — от корня репозитория; для CLI-прогонов задаём их так же.
const FIXTURES_FROM_ROOT = 'tools/fixtures/coverage';

const fixture = (name) => readFileSync(join(FIXTURES, name), 'utf8');

/** Конфигурация с одним модулем: путь к отчёту — относительно tools/. */
function configWith(module) {
  return { modules: [module] };
}

function writeConfig(config, { warnOnly = false } = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'bookshelf-coverage-'));
  const path = join(dir, 'coverage.json');
  writeFileSync(path, JSON.stringify(config, null, 2));
  return { dir, path, warnOnly };
}

test('line_coverage_is_taken_from_the_report_line_counter', () => {
  const result = lineCoverageOf(fixture('report-pass.xml'));
  assert.deepEqual(result, { covered: 80, missed: 20, percent: 80 });
});

test('a_report_without_a_line_counter_has_no_data', () => {
  assert.equal(lineCoverageOf(fixture('report-no-lines.xml')), null);
});

test('an_empty_line_counter_is_treated_as_no_data', () => {
  assert.equal(lineCoverageOf(fixture('report-empty.xml')), null);
});

test('a_module_above_its_threshold_passes', () => {
  const config = configWith({ name: ':core:metadata', report: 'fixtures/coverage/report-pass.xml', minLine: 80 });
  const result = checkCoverage(config, { root: HERE });

  assert.equal(result.failed, false);
  assert.equal(result.modules[0].ok, true);
  assert.equal(result.modules[0].percent, 80);
});

test('a_module_below_its_threshold_fails', () => {
  const config = configWith({ name: ':core:data', report: 'fixtures/coverage/report-fail.xml', minLine: 75 });
  const result = checkCoverage(config, { root: HERE });

  assert.equal(result.failed, true);
  assert.equal(result.modules[0].ok, false);
  assert.equal(result.modules[0].percent, 40);
});

test('a_missing_report_is_an_error', () => {
  const config = configWith({ name: ':core:opds', report: 'fixtures/coverage/no-such-report.xml', minLine: 80 });
  const result = checkCoverage(config, { root: HERE });

  assert.equal(result.failed, true);
  assert.equal(result.modules[0].ok, false);
  assert.match(result.modules[0].reason, /нет отчёта/);
});

test('a_module_marked_optional_is_skipped_when_its_report_is_missing', () => {
  const config = configWith({
    name: ':core:testing',
    report: 'fixtures/coverage/no-such-report.xml',
    minLine: 50,
    optional: true,
  });
  const result = checkCoverage(config, { root: HERE });

  assert.equal(result.failed, false);
  assert.equal(result.modules[0].skipped, true);
  assert.equal(result.modules[0].ok, true);
});

test('a_report_without_line_data_fails_even_though_the_file_exists', () => {
  const config = configWith({ name: ':core:designsystem', report: 'fixtures/coverage/report-no-lines.xml', minLine: 60 });
  const result = checkCoverage(config, { root: HERE });

  assert.equal(result.failed, true);
  assert.equal(result.modules[0].ok, false);
  assert.match(result.modules[0].reason, /нет данных о строках/);
});

test('the_summary_lists_every_module_with_its_percentage_and_threshold', () => {
  const config = {
    modules: [
      { name: ':core:metadata', report: 'fixtures/coverage/report-pass.xml', minLine: 80 },
      { name: ':core:data', report: 'fixtures/coverage/report-fail.xml', minLine: 75 },
    ],
  };
  const text = formatReport(checkCoverage(config, { root: HERE }));

  assert.match(text, /:core:metadata/);
  assert.match(text, /80 %/);
  assert.match(text, /:core:data/);
  assert.match(text, /40 %/);
  assert.match(text, /НЕ ДОБРАНО|не добрано/i);
});

test('the_cli_exits_with_one_when_a_threshold_is_missed', () => {
  const config = configWith({
    name: ':core:data',
    report: `${FIXTURES_FROM_ROOT}/report-fail.xml`,
    minLine: 75,
  });
  const { dir, path } = writeConfig(config);

  try {
    const run = spawnSync(process.execPath, [TOOL, `--config=${path}`, '--root=' + ROOT], { encoding: 'utf8' });
    assert.equal(run.status, 1, run.stdout + run.stderr);
    assert.match(run.stdout, /:core:data/);
    // Падение обязано быть по порогу, а не потому, что отчёт не найден.
    assert.doesNotMatch(run.stdout, /нет отчёта/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('the_cli_warn_flag_reports_the_failure_but_exits_with_zero', () => {
  const config = configWith({
    name: ':core:data',
    report: `${FIXTURES_FROM_ROOT}/report-fail.xml`,
    minLine: 75,
  });
  const { dir, path } = writeConfig(config);

  try {
    const run = spawnSync(process.execPath, [TOOL, `--config=${path}`, '--root=' + ROOT, '--warn'], { encoding: 'utf8' });
    assert.equal(run.status, 0, run.stdout + run.stderr);
    assert.match(run.stdout, /предупреждение/i);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('the_cli_passes_when_every_module_meets_its_threshold', () => {
  const config = configWith({
    name: ':core:metadata',
    report: `${FIXTURES_FROM_ROOT}/report-pass.xml`,
    minLine: 80,
  });
  const { dir, path } = writeConfig(config);

  try {
    const run = spawnSync(process.execPath, [TOOL, `--config=${path}`, '--root=' + ROOT], { encoding: 'utf8' });
    assert.equal(run.status, 0, run.stdout + run.stderr);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
