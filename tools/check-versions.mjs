#!/usr/bin/env node
// Проверка версий из gradle/libs.versions.toml.
//
// Зачем: ошибка в версии зависимости — самая дешёвая для человека и самая дорогая для сборки
// (падение на этапе разрешения зависимостей через 10 минут после старта CI). Скрипт проверяет,
// что координата с указанной версией реально существует в репозитории, и показывает, какие
// стабильные версии вышли позже. Запускается вручную и по расписанию в CI.
//
// Использование:
//   node tools/check-versions.mjs            # проверить всё, код возврата 1 при отсутствующей версии
//   node tools/check-versions.mjs --latest   # дополнительно показать 3 последние стабильные версии
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const CATALOG = join(HERE, '..', 'gradle', 'libs.versions.toml');
const SHOW_LATEST = process.argv.includes('--latest');

const REPOS = [
  { name: 'google', url: 'https://dl.google.com/android/maven2/' },
  { name: 'central', url: 'https://repo1.maven.org/maven2/' },
  { name: 'onyx', url: 'http://repo.boox.com/repository/maven-public/' },
];
const PLUGIN_PORTAL = 'https://plugins.gradle.org/m2/';
const TIMEOUT_MS = 25000;

const isStable = (v) => !/(alpha|beta|rc|dev|preview|eap|snapshot|-M\d)/i.test(v);

function parseCatalog(text) {
  const versions = {};
  const libraries = [];
  const plugins = [];
  let section = null;

  for (const rawLine of text.split('\n')) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const sectionMatch = line.match(/^\[(.+)]$/);
    if (sectionMatch) {
      section = sectionMatch[1];
      continue;
    }
    if (section === 'versions') {
      const m = line.match(/^([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"/);
      if (m) versions[m[1]] = m[2];
      continue;
    }
    if (section === 'libraries' || section === 'plugins') {
      const m = line.match(/^([A-Za-z0-9_-]+)\s*=\s*\{(.+)}\s*$/);
      if (!m) continue;
      const alias = m[1];
      const body = m[2];
      const module = body.match(/module\s*=\s*"([^"]+)"/)?.[1];
      const id = body.match(/id\s*=\s*"([^"]+)"/)?.[1];
      const ref = body.match(/version\.ref\s*=\s*"([^"]+)"/)?.[1];
      const literal = body.match(/version\s*=\s*"([^"]+)"/)?.[1];
      const version = literal ?? (ref ? versions[ref] : undefined);
      if (section === 'libraries' && module) libraries.push({ alias, module, version, ref });
      if (section === 'plugins' && id) plugins.push({ alias, id, version, ref });
    }
  }
  return { versions, libraries, plugins };
}

async function fetchText(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(TIMEOUT_MS) });
  return response.ok ? response.text() : null;
}

async function metadataVersions(repoUrl, group, artifact) {
  const path = `${group.replace(/\./g, '/')}/${artifact}/maven-metadata.xml`;
  const text = await fetchText(repoUrl + path);
  if (!text) return null;
  return [...text.matchAll(/<version>([^<]+)<\/version>/g)].map((m) => m[1]);
}

function pluginMarkerUrl(repoUrl, id, version) {
  const path = `${id.replace(/\./g, '/')}/${id}.gradle.plugin/${version}/${id}.gradle.plugin-${version}.pom`;
  return repoUrl + path;
}

async function existsPlugin(id, version) {
  for (const repo of [PLUGIN_PORTAL, REPOS[0].url]) {
    try {
      const response = await fetch(pluginMarkerUrl(repo, id, version), {
        method: 'HEAD',
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (response.ok) return repo;
    } catch {
      // пробуем следующий репозиторий
    }
  }
  return null;
}

async function main() {
  const { libraries, plugins } = parseCatalog(readFileSync(CATALOG, 'utf8'));
  const problems = [];

  console.log(`Каталог: ${CATALOG}`);
  console.log(`Зависимостей: ${libraries.length}, плагинов: ${plugins.length}\n`);

  for (const library of libraries) {
    const [group, artifact] = library.module.split(':');
    if (!library.version) {
      console.log(`skip  ${library.alias} — версия из BOM`);
      continue;
    }
    let found = null;
    let versions = [];
    for (const repo of REPOS) {
      versions = (await metadataVersions(repo.url, group, artifact)) ?? [];
      if (versions.includes(library.version)) {
        found = repo.name;
        break;
      }
    }
    if (found) {
      const newer = versions.filter((v) => isStable(v) && v > library.version).slice(-3);
      const hint = newer.length ? `  новее: ${newer.join(', ')}` : '';
      console.log(`OK    ${library.module}:${library.version}  [${found}]${SHOW_LATEST ? hint : ''}`);
    } else {
      console.log(`FAIL  ${library.module}:${library.version} — версия не найдена ни в одном репозитории`);
      const anyRepo = REPOS[0];
      const all = (await metadataVersions(anyRepo.url, group, artifact)) ?? [];
      const stable = all.filter(isStable).slice(-5);
      if (stable.length) console.log(`      последние стабильные: ${stable.join(' ')}`);
      problems.push(`${library.module}:${library.version}`);
    }
  }

  for (const plugin of plugins) {
    if (!plugin.version) {
      console.log(`skip  plugin ${plugin.id}`);
      continue;
    }
    const repo = await existsPlugin(plugin.id, plugin.version);
    if (repo) {
      console.log(`OK    plugin ${plugin.id}:${plugin.version}  [${repo}]`);
    } else {
      console.log(`FAIL  plugin ${plugin.id}:${plugin.version} — маркер плагина не найден`);
      problems.push(`${plugin.id}:${plugin.version}`);
    }
  }

  if (problems.length) {
    console.error(`\nПроблемы (${problems.length}):`);
    for (const problem of problems) console.error(`  - ${problem}`);
    process.exit(1);
  }
  console.log('\nВсе версии существуют в репозиториях.');
}

await main();
